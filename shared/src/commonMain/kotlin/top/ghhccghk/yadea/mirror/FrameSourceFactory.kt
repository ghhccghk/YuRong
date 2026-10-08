package top.ghhccghk.yadea.mirror

/**
 * 帧源工厂接口 - 各平台实现创建帧源。
 *
 * [createSource] 是跨平台业务入口；旧的 Android Activity Result 参数仅保留给
 * Android 专用调用路径，桌面实现不应接触这些类型。
 */
interface FrameSourceFactory {
    val capabilities: List<FrameSourceCapability>
        get() = FrameSourceType.entries.map { FrameSourceCapability(it, supported = true) }

    /** 创建摄像头帧源 */
    fun createCameraSource(): FrameSource

    /** 创建屏幕录制帧源 (需要 MediaProjection resultData) */
    fun createScreenSource(resultCode: Int, resultData: Any): FrameSource

    /** 创建屏幕录制帧源的跨平台入口。 */
    fun createScreenSource(): FrameSource = createScreenSource(0, Unit)

    /** 创建视频帧源 */
    fun createVideoSource(uri: String): FrameSource

    /** 按业务请求创建帧源。 */
    fun createSource(request: FrameSourceRequest): FrameSource = when (request) {
        is FrameSourceRequest.Camera -> createCameraSource()
        is FrameSourceRequest.Screen -> createScreenSource()
        is FrameSourceRequest.Video -> createVideoSource(request.uri)
        is FrameSourceRequest.Navigation -> createVideoSource(request.uri)
    }
}

/** 投屏源类型，使用业务语义而不是 UI 索引。 */
enum class FrameSourceType {
    Camera,
    Screen,
    Video,
    Navigation,
}

/** 某个平台对投屏源的支持情况。 */
data class FrameSourceCapability(
    val type: FrameSourceType,
    val supported: Boolean,
    val reason: String? = null,
)

/** 跨平台帧源请求。 */
sealed interface FrameSourceRequest {
    data class Camera(
        val cameraIndex: Int = 0,
        val cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    ) : FrameSourceRequest

    data class Screen(
        val displayIndex: Int = 0,
        val cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    ) : FrameSourceRequest

    data class Video(
        val uri: String,
        val cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    ) : FrameSourceRequest

    data class Navigation(
        val uri: String = "",
        val cropConfig: FrameCropConfig = FrameCropConfig.FULL,
    ) : FrameSourceRequest
}
