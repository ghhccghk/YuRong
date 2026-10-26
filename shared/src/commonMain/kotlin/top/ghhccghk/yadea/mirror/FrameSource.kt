package top.ghhccghk.yadea.mirror

import kotlin.concurrent.Volatile
import top.ghhccghk.yadea.currentMonotonicMillis

data class FrameSourceDimensions(
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 16f / 9f
}

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
     * Receive the oriented source dimensions. Preview containers use this ratio,
     * independently from the projection target negotiated with the vehicle.
     */
    fun onSourceDimensions(handler: ((FrameSourceDimensions) -> Unit)?) {}

    /**
     * Receive a low-rate, uncropped preview frame. The callback takes ownership
     * and must recycle the bitmap after encoding or displaying it.
     */
    fun onPreviewFrame(handler: ((PlatformBitmap) -> Unit)?) {}

    /**
     * Update the source capture cadence for sources that can cheaply gate new
     * frames before conversion. Native camera/media timestamp pacing may ignore
     * this hint; the final protocol send cadence always remains in MirrorEngine.
     */
    fun updateTargetFps(fps: Int) {}
}

/**
 * Keeps high-rate frame sources from allocating copies for the UI on every frame.
 * Handlers are updated from the UI thread while publishing happens on source
 * worker threads.
 */
class FramePreviewDispatcher(
    private val minIntervalMs: Long = 250L,
) {
    @Volatile
    private var dimensionsHandler: ((FrameSourceDimensions) -> Unit)? = null

    @Volatile
    private var frameHandler: ((PlatformBitmap) -> Unit)? = null

    @Volatile
    private var lastPublishedDimensions: FrameSourceDimensions? = null

    @Volatile
    private var lastFrameAtMillis = 0L

    fun onSourceDimensions(handler: ((FrameSourceDimensions) -> Unit)?) {
        dimensionsHandler = handler
        if (handler == null) {
            lastPublishedDimensions = null
        } else {
            lastPublishedDimensions?.let(handler)
        }
    }

    fun onPreviewFrame(handler: ((PlatformBitmap) -> Unit)?) {
        frameHandler = handler
    }

    fun publishDimensions(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val dimensions = FrameSourceDimensions(width, height)
        if (dimensions == lastPublishedDimensions) return false
        lastPublishedDimensions = dimensions
        dimensionsHandler?.invoke(dimensions)
        return true
    }

    fun shouldPublishFrame(nowMillis: Long = currentMonotonicMillis()): Boolean {
        if (frameHandler == null) return false
        if (lastFrameAtMillis != 0L && nowMillis - lastFrameAtMillis < minIntervalMs) {
            return false
        }
        lastFrameAtMillis = nowMillis
        return true
    }

    fun publishFrame(frame: PlatformBitmap) {
        val handler = frameHandler
        if (handler == null) {
            frame.recycle()
        } else {
            handler(frame)
        }
    }
}
