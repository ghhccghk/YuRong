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

    /** Whether this source is currently active. */
    val isRunning: Boolean

    /**
     * Update the normalized crop area. Implementations that cannot crop at
     * runtime may keep the value and apply it to the next frame.
     */
    fun updateCropConfig(config: FrameCropConfig) {}
}
