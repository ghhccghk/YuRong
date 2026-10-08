package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.content.Intent
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceFactory

class AndroidFrameSourceFactory(private val context: Context) : FrameSourceFactory {

    override fun createCameraSource(): FrameSource {
        return CameraFrameSource(context, context as androidx.lifecycle.LifecycleOwner)
    }

    override fun createScreenSource(resultCode: Int, resultData: Any): FrameSource {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData as Intent)
        return ScreenCaptureFrameSource(context, projection!!)
    }

    override fun createVideoSource(uri: String): FrameSource {
        return VideoFrameSource(context, android.net.Uri.parse(uri))
    }
}
