package top.ghhccghk.yadea.mirror.source

import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceCapability
import top.ghhccghk.yadea.mirror.FrameSourceFactory
import top.ghhccghk.yadea.mirror.FrameSourceRequest
import top.ghhccghk.yadea.mirror.FrameSourceType

/**
 * Desktop frame source factory.
 *
 * Screen capture uses java.awt.Robot; camera, local media and network streams
 * use JavaCV. Unsupported platform conditions are reported through
 * [capabilities] instead of silently falling back to another source.
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
            FrameSourceCapability(FrameSourceType.Camera, supported = true),
            FrameSourceCapability(
                FrameSourceType.Video,
                supported = true,
                reason = "支持本地视频路径和网络流地址",
            ),
            FrameSourceCapability(
                FrameSourceType.Navigation,
                supported = true,
                reason = "支持导航图片流或视频流地址",
            ),
        )
    }

    override fun createCameraSource(): FrameSource =
        JavaCvFrameSource.camera(cameraIndex = 0)

    override fun createScreenSource(resultCode: Int, resultData: Any): FrameSource =
        RobotScreenCaptureFrameSource()

    override fun createScreenSource(): FrameSource =
        RobotScreenCaptureFrameSource()

    override fun createVideoSource(uri: String): FrameSource =
        JavaCvFrameSource.media(uri)

    override fun createSource(request: FrameSourceRequest): FrameSource = when (request) {
        is FrameSourceRequest.Screen ->
            RobotScreenCaptureFrameSource(displayIndex = request.displayIndex).also {
                it.updateCropConfig(request.cropConfig)
            }

        is FrameSourceRequest.Camera ->
            JavaCvFrameSource.camera(request.cameraIndex).also {
                it.updateCropConfig(request.cropConfig)
            }

        is FrameSourceRequest.Video ->
            JavaCvFrameSource.media(request.uri).also {
                it.updateCropConfig(request.cropConfig)
            }

        is FrameSourceRequest.Navigation ->
            JavaCvFrameSource.media(request.uri).also {
                it.updateCropConfig(request.cropConfig)
            }
    }
}
