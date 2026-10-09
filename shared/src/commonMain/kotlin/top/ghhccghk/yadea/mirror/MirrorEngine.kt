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
    private var imageSequence = 0
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
        frameIntervalMs: Long = 100
    ): Boolean {
        if (state == MirrorState.Running) return true

        state = MirrorState.Negotiating
        stopRequested = false
        imageSequence = 0
        framesSent = 0
        bytesSent = 0

        // 1. 发送 NAV_START (TCP)
        try {
            frameSender.sendNavStart()
            onStatus("已发送 NAV_START，等待仪表回复...")
        } catch (e: Exception) {
            state = MirrorState.Failed
            onStatus("NAV_START 发送失败: ${e.message}")
            return false
        }

        // 2. 读取仪表回复，解析显示配置
        try {
            val response = frameSender.receiveFrame(timeoutMs = 3000)
            if (response != null) {
                val (_, payload, _) = response
                navConfig = YadeaProtocol.parseNavDisplayConfig(payload)
                onStatus("协商成功: ${navConfig.width}x${navConfig.height}, 能力=${navConfig.rotation}, FPS=${navConfig.offsetX}")
            } else {
                onStatus("未收到回复，使用默认 ${navConfig.width}x${navConfig.height}")
            }
        } catch (e: Exception) {
            onStatus("读取配置异常: ${e.message}，使用默认")
        }

        // 3. 发送 NAV_HINT (导航提示)
        try {
            val time = currentTimeProvider()
            val hint = YadeaProtocol.buildNavHintPayload(
                name = "投屏导航",
                hour = time.hour, minute = time.minute, second = time.second
            )
            frameSender.sendNavHint(hint)
            onStatus("已发送导航提示")
        } catch (e: Exception) {
            onStatus("导航提示发送失败: ${e.message}")
        }

        // 4. 帧发送循环
        state = MirrorState.Running
        onStatus("投屏已启动")

        sendJob = scope.launch(Dispatchers.Default) {
            while (!stopRequested && isActive) {
                val bitmap = frameBus.take()
                if (bitmap != null) {
                    try {
                        val jpeg = JpegEncoder.encode(bitmap, navConfig.width, navConfig.height)
                        val packetSize = YadeaProtocol.DEFAULT_PACKET_SIZE * navConfig.packetMultiplier
                        val timestamp = System.currentTimeMillis() and 0xFFFFFFFFL
                        val packets = if (broadcastPort != null) {
                            frameSender.broadcastImage(jpeg, imageSequence, broadcastPort, packetSize, timestamp)
                        } else {
                            frameSender.sendImage(jpeg, imageSequence, targetHost, targetPort, packetSize, timestamp)
                        }
                        framesSent++
                        bytesSent += jpeg.size
                        onStatus("第${imageSequence + 1}帧: ${packets}包, ${jpeg.size}B")
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

        // 5. 心跳循环 (每秒)
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
}
