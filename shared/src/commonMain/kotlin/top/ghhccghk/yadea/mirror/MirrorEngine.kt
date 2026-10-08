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

/**
 * 心跳时间参数
 */
data class DateTimeParts(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int, val second: Int
)

/**
 * 投屏引擎状态
 */
enum class MirrorState {
    /** 空闲 */
    Idle,
    /** 协商中 */
    Negotiating,
    /** 运行中 */
    Running,
    /** 已停止 */
    Stopped,
    /** 失败 */
    Failed
}

/**
 * MirrorEngine - 核心投屏循环
 *
 * 完整投屏流程:
 * 1. TCP 发送 NAV_START 指令，协商画布尺寸
 * 2. 读取仪表回复，解析 NavDisplayConfig（宽高/分包倍率）
 * 3. 启动帧发送循环: FrameBus.take() -> JPEG 编码 -> UDP 分包发送
 * 4. 启动心跳循环: 每秒发送当前时间（TCP）
 * 5. 停止时发送 NAV_END 并清理资源
 *
 * @param frameSender 协议发送器（TCP 控制 + UDP 数据）
 * @param frameBus 帧总线（生产者提交帧，引擎消费帧）
 * @param onStatus 状态回调（用于 UI 展示）
 */
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

    /** 当前引擎状态 */
    val currentState: MirrorState get() = state

    /** 仪表协商的显示配置 */
    val currentConfig: NavDisplayConfig get() = navConfig

    /** 是否正在投屏 */
    val isRunning: Boolean get() = state == MirrorState.Running

    /** 已发送帧数 */
    val sentFrameCount: Int get() = framesSent

    /** 已发送字节数 */
    val sentByteCount: Int get() = bytesSent

    /**
     * 启动投屏
     *
     * @param targetHost UDP 目标主机（仪表 IP）
     * @param targetPort UDP 目标端口
     * @param broadcastPort UDP 广播端口（null 则使用 targetHost 单播）
     * @param scope 协程作用域
     * @param currentTimeProvider 当前时间提供器（用于心跳）
     * @param frameIntervalMs 帧间隔毫秒（默认 100ms ≈ 10fps）
     * @return 是否成功启动
     */
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

        // ---- 第 1 步: 发送导航开始指令（TCP） ----
        try {
            frameSender.sendNavStart()
            onStatus("已发送导航开始指令，等待仪表回复...")
        } catch (e: Exception) {
            state = MirrorState.Failed
            onStatus("导航开始指令发送失败: ${e.message}")
            return false
        }

        // ---- 第 2 步: 读取仪表回复，协商画布配置 ----
        try {
            val response = frameSender.receiveFrame(timeoutMs = 3000)
            if (response != null) {
                val (_, payload, _) = response
                navConfig = YadeaProtocol.parseNavDisplayConfig(payload)
                onStatus("协商成功: ${navConfig.width}x${navConfig.height}, 分包倍率=${navConfig.packetMultiplier}")
            } else {
                onStatus("未收到回复，使用默认 ${navConfig.width}x${navConfig.height}")
            }
        } catch (e: Exception) {
            onStatus("读取配置异常: ${e.message}，使用默认")
        }

        // ---- 第 3 步: 启动帧发送循环 ----
        state = MirrorState.Running
        onStatus("投屏已启动")

        sendJob = scope.launch(Dispatchers.Default) {
            while (!stopRequested && isActive) {
                val bitmap = frameBus.take()
                if (bitmap != null) {
                    try {
                        // JPEG 编码（自适应质量）
                        val jpeg = JpegEncoder.encode(bitmap, navConfig.width, navConfig.height)
                        // 分包发送
                        val packetSize = YadeaProtocol.DEFAULT_PACKET_SIZE * navConfig.packetMultiplier
                        val timestamp = top.ghhccghk.yadea.currentMonotonicMillis() and 0xFFFFFFFFL
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

        // ---- 第 4 步: 启动心跳循环（每秒） ----
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

    /**
     * 停止投屏
     *
     * 发送 NAV_END 指令，取消帧发送和心跳协程，清空帧总线。
     */
    suspend fun stop() {
        if (state == MirrorState.Idle || state == MirrorState.Stopped) return

        stopRequested = true
        sendJob?.cancel()
        heartbeatJob?.cancel()
        sendJob = null
        heartbeatJob = null

        try {
            frameSender.sendNavEnd()
        } catch (_: Exception) {
            // NAV_END 发送失败可忽略（连接可能已断开）
        }

        frameBus.clear()
        state = MirrorState.Stopped
        onStatus("投屏已停止，共${framesSent}帧/${bytesSent}B")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopRequested = true
        sendJob?.cancel()
        heartbeatJob?.cancel()
        frameBus.clear()
    }
}
