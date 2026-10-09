package top.ghhccghk.yadea

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.mirror.ScreenCaptureGrant
import top.ghhccghk.yadea.mirror.source.ScreenCaptureFrameSource

/**
 * Android 屏幕录制授权实现。
 *
 * resultData 只保存在一次 Activity Result 的调用栈中；前台服务进入前台后，
 * 它会一次性交给 AndroidFrameSourceFactory，不写入字段或 Intent extra。
 */
class AndroidScreenCaptureAuthorizer(
    private val activity: ComponentActivity,
) : ScreenCaptureAuthorizer {

    private data class PendingRequest(
        val onResult: (ScreenCaptureGrant) -> Unit,
        val onError: (Throwable) -> Unit,
    )

    private var pendingRequest: PendingRequest? = null

    private val screenCaptureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingRequest ?: return@registerForActivityResult
        pendingRequest = null

        val resultCode = result.resultCode
        val resultData = result.data
        if (resultCode != Activity.RESULT_OK) {
            pending.onError(
                IllegalStateException(
                    "屏幕录制授权失败：resultCode=$resultCode，必须为 Activity.RESULT_OK"
                )
            )
            return@registerForActivityResult
        }
        if (resultData == null) {
            pending.onError(IllegalArgumentException("屏幕录制授权未返回 resultData Intent"))
            return@registerForActivityResult
        }

        try {
            CaptureService.start(activity, resultCode, resultData) {
                pending.onResult(ScreenCaptureGrant(resultCode, resultData))
            }
        } catch (error: Throwable) {
            CaptureService.stop(activity)
            pending.onError(error)
        }
    }

    override fun requestScreenCapture(
        onResult: (ScreenCaptureGrant) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (pendingRequest != null) {
            onError(IllegalStateException("已有屏幕录制授权请求正在进行"))
            return
        }
        pendingRequest = PendingRequest(onResult, onError)
        try {
            screenCaptureLauncher.launch(
                ScreenCaptureFrameSource.createProjectionIntent(activity)
            )
        } catch (error: Throwable) {
            pendingRequest = null
            onError(error)
        }
    }

    override fun releaseScreenCapture() {
        CaptureService.stop(activity)
    }
}
