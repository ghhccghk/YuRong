package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import top.ghhccghk.yadea.currentMonotonicMillis

/** A single-slot frame plus monotonic timestamps used by the local pipeline metrics. */
data class BufferedFrame(
    val bitmap: PlatformBitmap,
    val capturedAtMillis: Long,
    val submittedAtMillis: Long,
)

/**
 * FrameBus - thread-safe single-slot frame buffer.
 *
 * Producers submit frames and MirrorEngine takes only the newest one. Submitting
 * a newer frame replaces and recycles the previous frame; frames are never queued.
 */
class FrameBus {

    private val _latest = MutableStateFlow<BufferedFrame?>(null)
    private val _capturedFrameCount = MutableStateFlow(0L)
    private val _overwrittenFrameCount = MutableStateFlow(0L)

    /** Observable latest frame metadata. */
    val latest: StateFlow<BufferedFrame?> = _latest.asStateFlow()

    /** Frames submitted to this bus since the last clear. */
    val capturedFrameCount: StateFlow<Long> = _capturedFrameCount.asStateFlow()

    /** Frames replaced by a newer frame before MirrorEngine could take them. */
    val overwrittenFrameCount: StateFlow<Long> = _overwrittenFrameCount.asStateFlow()

    /**
     * Submit a frame. [capturedAtMillis] is the source callback completion time,
     * not the camera sensor or media presentation timestamp.
     */
    fun submit(
        bitmap: PlatformBitmap,
        capturedAtMillis: Long = currentMonotonicMillis(),
    ): BufferedFrame {
        val frame = BufferedFrame(
            bitmap = bitmap,
            capturedAtMillis = capturedAtMillis,
            submittedAtMillis = currentMonotonicMillis(),
        )
        var replaced: BufferedFrame? = null
        while (true) {
            val current = _latest.value
            if (_latest.compareAndSet(current, frame)) {
                replaced = current
                break
            }
        }
        _capturedFrameCount.update { it + 1 }
        replaced?.let {
            _overwrittenFrameCount.update { count -> count + 1 }
            it.bitmap.recycle()
        }
        return frame
    }

    /** Take the current frame metadata and clear the slot. Caller owns the bitmap. */
    fun takeFrame(): BufferedFrame? {
        while (true) {
            val current = _latest.value ?: return null
            if (_latest.compareAndSet(current, null)) return current
        }
    }

    /** Compatibility helper for tests and simple callers. */
    fun take(): PlatformBitmap? = takeFrame()?.bitmap

    /** Wait for the next latest frame without polling. Caller owns the bitmap. */
    suspend fun takeNextFrame(): BufferedFrame? {
        while (true) {
            takeFrame()?.let { return it }
            _latest.first { it != null }
        }
    }

    suspend fun takeNext(): PlatformBitmap? = takeNextFrame()?.bitmap

    /**
     * Remove and recycle only [frame] if it is still the current latest frame.
     * This prevents a late callback from discarding a newer source's frame.
     */
    fun discardIf(frame: BufferedFrame): Boolean {
        while (true) {
            val current = _latest.value ?: return false
            if (current !== frame) return false
            if (_latest.compareAndSet(current, null)) {
                frame.bitmap.recycle()
                return true
            }
        }
    }

    /**
     * Remove and recycle the held frame without resetting cumulative counters.
     * Used when stopping a session so its final metrics remain observable.
     */
    fun discardLatestFrame() {
        takeFrame()?.bitmap?.recycle()
    }

    /** Clear the slot and counters, recycling any held frame. */
    fun clear() {
        _capturedFrameCount.value = 0
        _overwrittenFrameCount.value = 0
        discardLatestFrame()
    }

    /** Whether a frame is currently held. */
    fun hasFrame(): Boolean = _latest.value != null
}
