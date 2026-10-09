package top.ghhccghk.yadea.mirror

/**
 * Android 等平台的屏幕录制授权结果。
 *
 * resultData 由平台定义；Android 上是 MediaProjection Activity Result 返回的
 * Intent，只能交给 MediaProjectionManager 使用一次。
 */
data class ScreenCaptureGrant(
    val resultCode: Int,
    val resultData: Any,
)

/**
 * 发起系统屏幕录制授权并管理对应的平台资源。
 */
interface ScreenCaptureAuthorizer {
    /** 请求屏幕录制授权；只允许在用户触发后调用。 */
    fun requestScreenCapture(
        onResult: (ScreenCaptureGrant) -> Unit,
        onError: (Throwable) -> Unit,
    )

    /** 帧源释放后停止平台侧录屏服务。 */
    fun releaseScreenCapture()
}
