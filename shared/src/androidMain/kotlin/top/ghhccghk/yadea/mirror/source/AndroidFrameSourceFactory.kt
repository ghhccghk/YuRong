package top.ghhccghk.yadea.mirror.source

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceFactory

class AndroidFrameSourceFactory(
    context: Context,
    private val lifecycleOwner: LifecycleOwner
) : FrameSourceFactory {

    private val appContext = context.applicationContext

    override fun createCameraSource(): FrameSource =
        CameraFrameSource(appContext, lifecycleOwner)

    override fun createScreenSource(): FrameSource =
        throw IllegalStateException(
            "Android 屏幕录制必须先通过 Activity Result 授权，并将 resultCode/resultData 传给 createScreenSource"
        )

    override fun createScreenSource(resultCode: Int, resultData: Any): FrameSource {
        if (resultCode != Activity.RESULT_OK) {
            throw IllegalStateException(
                "屏幕录制授权失败：resultCode=$resultCode，必须为 Activity.RESULT_OK"
            )
        }
        val data = resultData as? Intent
            ?: throw IllegalArgumentException("resultData 必须是 Intent")

        // 调用前必须已有 mediaProjection 类型的前台服务调用过 startForeground。
        // 授权返回的 Intent 在 Android 14+ 只能使用一次，只能在这里交给 getMediaProjection，不能缓存后复用。
        val manager = appContext.getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("系统未提供 MediaProjectionManager")
        val projection = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("无法获取 MediaProjection，请确认录屏授权成功且 mediaProjection 前台服务正在运行")

        return ScreenCaptureFrameSource(appContext, projection)
    }

    override fun createVideoSource(uri: String): FrameSource =
        VideoFrameSource(appContext, Uri.parse(uri))
}
