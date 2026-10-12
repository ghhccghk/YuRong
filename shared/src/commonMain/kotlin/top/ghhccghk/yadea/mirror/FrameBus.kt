package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

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
        var old: PlatformBitmap? = null
        _latest.update { current ->
            old = current
            bitmap
        }
        old?.recycle()
    }

    /**
     * Take the current frame and clear the slot.
     * Caller is responsible for recycling.
     * @return The latest frame, or null if no frame available.
     */
    fun take(): PlatformBitmap? {
        var old: PlatformBitmap? = null
        _latest.update { current ->
            old = current
            null
        }
        return old
    }

    /**
     * Wait for the next frame without polling. The returned frame is owned by the caller.
     */
    suspend fun takeNext(): PlatformBitmap? {
        while (true) {
            take()?.let { return it }
            _latest.first { it != null }
        }
    }

    /**
     * Clear all state and recycle any held frame.
     */
    fun clear() {
        _frameCount.value = 0
        take()?.recycle()
    }

    /** Whether a frame is currently held. */
    fun hasFrame(): Boolean = _latest.value != null
}
