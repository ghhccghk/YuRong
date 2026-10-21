package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import top.ghhccghk.yadea.currentMonotonicMillis
import top.ghhccghk.yadea.hexByte
import top.ghhccghk.yadea.model.NavDisplayConfig
import top.ghhccghk.yadea.network.FrameSender
import top.ghhccghk.yadea.protocol.YadeaProtocol
import top.ghhccghk.yadea.toEpochMillis

data class DateTimeParts(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int, val second: Int
)

enum class MirrorState {
    Idle, Negotiating, Running, Stopped, Failed
}

class MirrorEngine(
    private val frameSender: FrameSender,
    private val frameBus: FrameBus,
    private val onStatus: (String) -> Unit = {}
) {
    private val lifecycleMutex = Mutex()
    private val metricsTracker = MirrorMetricsTracker()

    private var navConfig = NavDisplayConfig()
    @Volatile private var state = MirrorState.Idle
    private var sendJob: Job? = null
    private var heartbeatJob: Job? = null
    private var imageSequence = 1
    @Volatile private var resolvedFps = FrameRatePolicy.SAFE_DEFAULT_FPS
    @Volatile private var stopRequested = false
    @Volatile private var releaseRequested = false
    @Volatile private var framesSent = 0
    @Volatile private var bytesSent = 0L

    val currentState: MirrorState get() = state
    val currentConfig: NavDisplayConfig get() = navConfig
    val isRunning: Boolean get() = state == MirrorState.Running
    val sentFrameCount: Int get() = framesSent
    val sentByteCount: Long get() = bytesSent
    val effectiveFps: Int get() = resolvedFps
    val metrics: StateFlow<MirrorMetricsSnapshot> = metricsTracker.snapshot

    suspend fun start(
        targetHost: String,
        targetPort: Int,
        broadcastPort: Int? = null,
        scope: CoroutineScope,
        currentTimeProvider: () -> DateTimeParts,
        userPreferredFps: Int? = null,
    ): Boolean = lifecycleMutex.withLock {
        if (state == MirrorState.Running || state == MirrorState.Negotiating) return@withLock true
        if (releaseRequested) return@withLock false

        state = MirrorState.Negotiating
        stopRequested = false
        imageSequence = 1
        framesSent = 0
        bytesSent = 0L
        navConfig = NavDisplayConfig()
        metricsTracker.reset()
        frameBus.clear()

        try {
            val time = currentTimeProvider()
            frameSender.sendStartupSequence(time.year, time.month, time.day, time.hour, time.minute, time.second)
            onStatus("已发送 HEARTBEAT + NAV_START")
        } catch (cancellation: CancellationException) {
            state = MirrorState.Stopped
            throw cancellation
        } catch (e: Exception) {
            state = MirrorState.Failed
            onStatus("启动命令失败: ${e.message}")
            return@withLock false
        }

        try {
            var waitCount = 0
            var gotConfig = false
            while (!gotConfig && waitCount <= 5 && !releaseRequested) {
                val response = frameSender.receiveFrame(timeoutMs = 8_000) ?: break
                val (cmd, payload, _) = response
                when (cmd) {
                    YadeaProtocol.CMD_NAV_START_RESPONSE -> {
                        if (payload.size >= 8) {
                            navConfig = YadeaProtocol.parseNavDisplayConfig(payload)
                            gotConfig = true
                            onStatus(
                                "协商成功: ${navConfig.width}x${navConfig.height}, " +
                                    "format=0x${hexByte(navConfig.imageFormat)}, " +
                                    "capabilities=0x${hexByte(navConfig.capabilities)}, rate=${navConfig.maxFps}"
                            )
                        } else {
                            onStatus("0x20 响应不足8字节 (${payload.size}B)")
                        }
                    }
                    else -> onStatus("收到 ${cmdName(cmd)} (等待0x20)")
                }
                waitCount++
            }
            if (!gotConfig) {
                navConfig = NavDisplayConfig()
                onStatus("未收到0x20配置，使用默认 ${navConfig.width}x${navConfig.height}, rate=${navConfig.maxFps}")
            }
        } catch (cancellation: CancellationException) {
            state = MirrorState.Stopped
            throw cancellation
        } catch (e: Exception) {
            onStatus("读取配置异常: ${e.message}")
        }

        if (releaseRequested) {
            state = MirrorState.Stopped
            return@withLock false
        }

        val fps = FrameRatePolicy.resolveEffectiveFps(navConfig.maxFps, userPreferredFps)
        val frameIntervalMs = FrameRatePolicy.intervalMillis(fps)
        resolvedFps = fps
        state = MirrorState.Running
        onStatus("投屏已启动 (车辆上限=${navConfig.maxFps}fps, 实际调度=${fps}fps, 间隔=${frameIntervalMs}ms)")

        sendJob = scope.launch(Dispatchers.Default) {
            runFrameLoop(targetHost, targetPort, broadcastPort, currentTimeProvider, frameIntervalMs)
        }
        heartbeatJob = scope.launch(Dispatchers.Default) {
            runHeartbeatLoop(currentTimeProvider)
        }

        // release() is intentionally non-suspending. Re-check after publishing the
        // jobs so a cleanup racing with the end of start() cannot leave Running state.
        if (releaseRequested) {
            stopRequested = true
            sendJob?.cancel()
            heartbeatJob?.cancel()
            sendJob = null
            heartbeatJob = null
            state = MirrorState.Stopped
            return@withLock false
        }
        true
    }

    private suspend fun runFrameLoop(
        targetHost: String,
        targetPort: Int,
        broadcastPort: Int?,
        currentTimeProvider: () -> DateTimeParts,
        frameIntervalMs: Long,
    ) {
        var nextFrameAt = currentMonotonicMillis()
        var lastFailureReportAt = 0L
        var suppressedFailures = 0L

        while (!stopRequested && kotlinx.coroutines.currentCoroutineContext().isActive) {
            val waitMs = nextFrameAt - currentMonotonicMillis()
            if (waitMs > 0) delay(waitMs)

            // Wait for a fresh frame only after the next send slot. This avoids holding
            // an old frame while sleeping and preserves FrameBus latest-only semantics.
            val buffered = frameBus.takeNextFrame() ?: continue
            val takenAt = currentMonotonicMillis()
            metricsTracker.onFrameTaken(buffered, takenAt)

            try {
                val jpeg = JpegEncoder.encode(
                    buffered.bitmap,
                    navConfig.width,
                    navConfig.height,
                )
                val encodedAt = currentMonotonicMillis()
                metricsTracker.onFrameEncoded(
                    encodedAtMillis = encodedAt,
                    outputWidth = navConfig.width,
                    outputHeight = navConfig.height,
                    jpegBytes = jpeg.size,
                )

                // Encoding is synchronous on the send loop. Cancellation cannot reach it
                // while inside the platform JPEG call, so re-check before publishing a frame.
                currentCoroutineContext().ensureActive()
                if (stopRequested) throw CancellationException("Mirror session stopped")

                val timestamp = currentTimeProvider().toEpochMillis() and 0xFFFFFFFFL
                val sendStartedAt = currentMonotonicMillis()
                val chunks = if (broadcastPort != null) {
                    frameSender.broadcastImage(jpeg, imageSequence, broadcastPort, timestamp)
                } else {
                    frameSender.sendImage(jpeg, imageSequence, targetHost, targetPort, timestamp)
                }
                val sentAt = currentMonotonicMillis()
                framesSent++
                bytesSent += jpeg.size.toLong()
                metricsTracker.onFrameSent(sendStartedAt, sentAt, chunks)
                imageSequence++
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                metricsTracker.onFrameFailed()
                val now = currentMonotonicMillis()
                if (lastFailureReportAt == 0L || now - lastFailureReportAt >= FAILURE_REPORT_INTERVAL_MS) {
                    val suffix = if (suppressedFailures > 0) " (另有${suppressedFailures}次未重复记录)" else ""
                    onStatus("帧发送失败: ${e.message}$suffix")
                    lastFailureReportAt = now
                    suppressedFailures = 0
                } else {
                    suppressedFailures++
                }
            } finally {
                buffered.bitmap.recycle()
            }

            metricsTracker.publish(frameBus)
            nextFrameAt += frameIntervalMs

            // If processing overran one or more slots, skip them instead of accumulating delay.
            val now = currentMonotonicMillis()
            if (nextFrameAt < now) {
                val missedSlots =
                    (now - nextFrameAt + frameIntervalMs - 1L) / frameIntervalMs
                nextFrameAt += missedSlots * frameIntervalMs
            }
        }
        metricsTracker.publish(frameBus, force = true)
    }

    private suspend fun runHeartbeatLoop(currentTimeProvider: () -> DateTimeParts) {
        var lastFailureReportAt = 0L
        var suppressedFailures = 0L
        while (!stopRequested && kotlinx.coroutines.currentCoroutineContext().isActive) {
            try {
                val time = currentTimeProvider()
                frameSender.sendHeartbeat(time.year, time.month, time.day, time.hour, time.minute, time.second)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                val now = currentMonotonicMillis()
                if (lastFailureReportAt == 0L || now - lastFailureReportAt >= FAILURE_REPORT_INTERVAL_MS) {
                    val suffix = if (suppressedFailures > 0) " (另有${suppressedFailures}次未重复记录)" else ""
                    onStatus("心跳失败: ${e.message}$suffix")
                    lastFailureReportAt = now
                    suppressedFailures = 0
                } else {
                    suppressedFailures++
                }
            }
            delay(1_000)
        }
    }

    suspend fun stop() = lifecycleMutex.withLock {
        if (state == MirrorState.Idle || state == MirrorState.Stopped) return@withLock

        stopRequested = true
        sendJob?.cancelAndJoin()
        heartbeatJob?.cancelAndJoin()
        sendJob = null
        heartbeatJob = null
        metricsTracker.publish(frameBus, force = true)
        frameBus.discardLatestFrame()

        // Preserve the existing NAV_END handshake and its timeout. release() is the
        // non-blocking path used when the UI/process is being destroyed.
        try {
            frameSender.sendNavEnd()
            onStatus("已发送 NAV_END，等待 0x23 响应")
            val response = frameSender.receiveFrame(timeoutMs = 5_000)
            if (response != null && response.first == YadeaProtocol.CMD_NAV_END_RESPONSE) {
                onStatus("收到 NAV_END 响应")
            } else {
                onStatus("NAV_END 响应超时或命令不匹配")
            }
        } catch (e: Exception) {
            onStatus("NAV_END 发送/响应失败: ${e.message}")
        }

        state = MirrorState.Stopped
        onStatus("投屏已停止，共${framesSent}帧/${bytesSent}B")
    }

    /** Immediate, non-suspending cleanup; it deliberately does not wait for NAV_END. */
    fun release() {
        releaseRequested = true
        stopRequested = true
        sendJob?.cancel()
        heartbeatJob?.cancel()
        sendJob = null
        heartbeatJob = null
        frameBus.discardLatestFrame()
        if (state != MirrorState.Failed) state = MirrorState.Stopped
    }

    private fun cmdName(cmd: Int): String = when (cmd) {
        YadeaProtocol.CMD_NAV_START -> "NAV_START"
        YadeaProtocol.CMD_NAV_END -> "NAV_END"
        YadeaProtocol.CMD_NAV_INFO -> "NAV_INFO"
        YadeaProtocol.CMD_IMAGE_CHUNK -> "IMG_CHUNK"
        YadeaProtocol.CMD_HEARTBEAT -> "HEARTBEAT"
        YadeaProtocol.CMD_NAV_START_RESPONSE -> "NAV_START_RESP"
        YadeaProtocol.CMD_NAV_END_RESPONSE -> "NAV_END_RESP"
        YadeaProtocol.CMD_STATUS_RESPONSE -> "STATUS"
        else -> "0x${hexByte(cmd)}"
    }

    private companion object {
        const val FAILURE_REPORT_INTERVAL_MS = 5_000L
    }
}
