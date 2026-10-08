package top.ghhccghk.yadea.mirror.source

import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceCapability
import top.ghhccghk.yadea.mirror.FrameSourceFactory
import top.ghhccghk.yadea.mirror.FrameSourceRequest
import top.ghhccghk.yadea.mirror.FrameSourceType

/**
 * Desktop frame source factory. Screen capture is supported through
 * [RobotScreenCaptureFrameSource]; camera and video require an additional
 * native media backend and are reported as unsupported for now.
 */
class JvmFrameSourceFactory : FrameSourceFactory {
    override val capabilities: List<FrameSourceCapability> = run {
        val screenSupport = desktopScreenCaptureSupport()
        listOf(
            FrameSourceCapability(
                FrameSourceType.Screen,
                supported = screenSupport.first,
                reason = screenSupport.second,
            ),
        FrameSourceCapability(
            FrameSourceType.Camera,
            supported = false,
            reason = "桌面版暂不支持摄像头",
        ),
        FrameSourceCapability(
            FrameSourceType.Video,
            supported = false,
            reason = "桌面版暂不支持视频文件",
        ),
            FrameSourceCapability(
                FrameSourceType.Navigation,
                supported = false,
                reason = "桌面版暂不支持导航源",
            ),
        )
    }

    override fun createCameraSource(): FrameSource =
        unsupported(FrameSourceType.Camera)

    override fun createScreenSource(resultCode: Int, resultData: Any): FrameSource =
        RobotScreenCaptureFrameSource()

    override fun createScreenSource(): FrameSource =
        RobotScreenCaptureFrameSource()

    override fun createVideoSource(uri: String): FrameSource =
        unsupported(FrameSourceType.Video)

    override fun createSource(request: FrameSourceRequest): FrameSource = when (request) {
        is FrameSourceRequest.Screen ->
            RobotScreenCaptureFrameSource(displayIndex = request.displayIndex).also {
                it.updateCropConfig(request.cropConfig)
            }

        is FrameSourceRequest.Camera -> unsupported(FrameSourceType.Camera)
        is FrameSourceRequest.Video -> unsupported(FrameSourceType.Video)
        is FrameSourceRequest.Navigation -> unsupported(FrameSourceType.Navigation)
    }

    private fun unsupported(type: FrameSourceType): FrameSource {
        val capability = capabilities.first { it.type == type }
        throw UnsupportedOperationException(capability.reason ?: "桌面版不支持该投屏源")
    }
}
