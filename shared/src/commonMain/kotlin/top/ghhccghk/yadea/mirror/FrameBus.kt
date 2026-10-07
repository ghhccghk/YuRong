package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FrameBus - thread-safe single-slot frame buffer
 *
 * Producers (camera, video, screen capture) submit frames.
 * The mirror engine takes frames for encoding and sending.
 * Only the latest frame is kept - older frames are dropped (conflated).
 */
class FrameBus {

    private val _latest = MutableStateFlow<PlatformBitmap?>(null)
    private val _frameCount = MutableStateFlow(0L)

    /** Observable latest frame (for debugging/UI). */
    val latest = _latest.asStateFlow()

    /** Total frames submitted since last clear(). */
    val frameCount = _frameCount.asStateFlow()

    /**
     * Submit a new frame. Replaces and recycles the previous frame.
     */
    fun submit(bitmap: PlatformBitmap) {
        _frameCount.value++
        val old = _latest.value
        _latest.value = bitmap
        old?.recycle()
    }

    /**
     * Take the current frame and clear the slot.
     * Caller is responsible for recycling.
     * @return The latest frame, or null if no frame available.
     */
    fun take(): PlatformBitmap? {
        val current = _latest.value
        _latest.value = null
        return current
    }

    /**
     * Clear all state and recycle any held frame.
     */
    fun clear() {
        val old = _latest.value
        _latest.value = null
        _frameCount.value = 0
        old?.recycle()
    }

    /** Whether a frame is currently held. */
    fun hasFrame(): Boolean = _latest.value != null
}
