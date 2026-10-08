package top.ghhccghk.yadea.mirror

/**
 * 帧源工厂接口 - 各平台实现创建帧源
 */
interface FrameSourceFactory {
    /** 创建摄像头帧源 */
    fun createCameraSource(): FrameSource

    /** 创建屏幕录制帧源 (需要 MediaProjection resultData) */
    fun createScreenSource(resultCode: Int, resultData: Any): FrameSource

    /** 创建视频帧源 */
    fun createVideoSource(uri: String): FrameSource
}
