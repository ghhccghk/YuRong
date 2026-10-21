package top.ghhccghk.yadea.mirror

/**
 * FrameSource - abstract frame producer
 *
 * Implementations: CameraFrameSource, VideoFrameSource, ScreenCaptureFrameSource.
 * Produces frames and submits them to a [FrameBus].
 */
interface FrameSource {
    /** Start producing frames. Each frame is passed to [onFrame]. */
    suspend fun start(onFrame: (PlatformBitmap) -> Unit)

    /** Stop producing frames. */
    fun stop()

    /** Release resources and make the source safe for repeated stop calls. */
    fun release() {
        stop()
    }

    /** Whether this source is currently active. */
    val isRunning: Boolean

    /**
     * Update the normalized crop area. Implementations that cannot crop at
     * runtime may keep the value and apply it to the next frame.
     */
    fun updateCropConfig(config: FrameCropConfig) {}

    /** Register a callback for asynchronous source failures. */
    fun onFailure(handler: (String) -> Unit) {}

    /**
     * Update the source capture cadence for sources that can cheaply gate new
     * frames before conversion. Native camera/media timestamp pacing may ignore
     * this hint; the final protocol send cadence always remains in MirrorEngine.
     */
    fun updateTargetFps(fps: Int) {}
}
