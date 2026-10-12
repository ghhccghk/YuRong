package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.model.NavDisplayConfig
import top.ghhccghk.yadea.network.FrameSender
import top.ghhccghk.yadea.protocol.YadeaProtocol

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
    private var navConfig = NavDisplayConfig()
    private var state = MirrorState.Idle
    private var sendJob: Job? = null
    private var heartbeatJob: Job? = null
    private var imageSequence = 1
    private var stopRequested = false
    private var framesSent = 0
    private var bytesSent = 0

    val currentState: MirrorState get() = state
    val currentConfig: NavDisplayConfig get() = navConfig
    val isRunning: Boolean get() = state == MirrorState.Running
    val sentFrameCount: Int get() = framesSent
    val sentByteCount: Int get() = bytesSent

    suspend fun start(
        targetHost: String,
        targetPort: Int,
        broadcastPort: Int? = null,
        scope: CoroutineScope,
        currentTimeProvider: () -> DateTimeParts,
        isLiveSource: Boolean = false
    ): Boolean {
        if (state == MirrorState.Running) return true

        state = MirrorState.Negotiating
        stopRequested = false
        imageSequence = 1
        framesSent = 0
        bytesSent = 0

        val frameIntervalMs = if (isLiveSource) 1000L else 100L

        // 1. 启动序列: HEARTBEAT + NAV_START
        try {
            val time = currentTimeProvider()
            frameSender.sendStartupSequence(time.year, time.month, time.day, time.hour, time.minute, time.second)
            onStatus("已发送 HEARTBEAT + NAV_START")
        } catch (e: Exception) {
            state = MirrorState.Failed
            onStatus("启动命令失败: ${e.message}")
            return false
        }

        // 2. 等待 0x20 响应
        try {
            var waitCount = 0
            var gotConfig = false
            while (!gotConfig && waitCount <= 5) {
                val response = frameSender.receiveFrame(timeoutMs = 2000) ?: break
                val (cmd, payload, _) = response
                when (cmd) {
                    YadeaProtocol.CMD_NAV_START_RESPONSE -> {
                        if (payload.size >= 8) {
                            navConfig = YadeaProtocol.parseNavDisplayConfig(payload)
                            gotConfig = true
                            onStatus("协商成功: ${navConfig.width}x${navConfig.height}, format=${navConfig.imageFormat}, screenType=${navConfig.capabilities}, rate=${navConfig.maxFps}")
                        } else {
                            onStatus("0x20 响应不足8字节 (${payload.size}B)")
                        }
                    }
                    else -> onStatus("收到 ${cmdName(cmd)} (等待0x20)")
                }
                waitCount++
            }
            if (!gotConfig) onStatus("未收到0x20配置，使用默认 ${navConfig.width}x${navConfig.height}")
        } catch (e: Exception) {
            onStatus("读取配置异常: ${e.message}")
        }

        // 3. 帧发送循环
        state = MirrorState.Running
        onStatus("投屏已启动 (帧间隔=${frameIntervalMs}ms)")

        sendJob = scope.launch(Dispatchers.Default) {
            while (!stopRequested && isActive) {
                val bitmap = frameBus.take()
                if (bitmap != null) {
                    try {
                        val jpeg = JpegEncoder.encode(bitmap, navConfig.width, navConfig.height)
                        val timestamp = System.currentTimeMillis() and 0xFFFFFFFFL
                        val packets = if (broadcastPort != null) {
                            frameSender.broadcastImage(jpeg, imageSequence, broadcastPort, timestamp)
                        } else {
                            frameSender.sendImage(jpeg, imageSequence, targetHost, targetPort, timestamp)
                        }
                        framesSent++
                        bytesSent += jpeg.size
                        onStatus("第${imageSequence}帧: ${packets}片, ${jpeg.size}B")
                        imageSequence++
                    } catch (e: Exception) {
                        onStatus("帧发送失败: ${e.message}")
                    } finally {
                        bitmap.recycle()
                    }
                }
                delay(frameIntervalMs)
            }
        }

        // 4. 心跳
        heartbeatJob = scope.launch(Dispatchers.Default) {
            while (!stopRequested && isActive) {
                try {
                    val time = currentTimeProvider()
                    frameSender.sendHeartbeat(time.year, time.month, time.day, time.hour, time.minute, time.second)
                } catch (e: Exception) {
                    onStatus("心跳失败: ${e.message}")
                }
                delay(1000)
            }
        }

        return true
    }

    suspend fun stop() {
        if (state == MirrorState.Idle || state == MirrorState.Stopped) return
        stopRequested = true
        sendJob?.cancel()
        heartbeatJob?.cancel()
        sendJob = null
        heartbeatJob = null

        try {
            frameSender.sendNavEnd()
            onStatus("已发送 NAV_END")
        } catch (_: Exception) {}

        frameBus.clear()
        state = MirrorState.Stopped
        onStatus("投屏已停止，共${framesSent}帧/${bytesSent}B")
    }

    fun release() {
        stopRequested = true
        sendJob?.cancel()
        heartbeatJob?.cancel()
        frameBus.clear()
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
        else -> "0x${String.format("%02X", cmd)}"
    }
}
