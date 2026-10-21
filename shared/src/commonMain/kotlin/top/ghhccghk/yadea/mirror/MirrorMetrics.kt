package top.ghhccghk.yadea.mirror

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ghhccghk.yadea.currentMonotonicMillis

/**
 * Fixed-size mirror pipeline metrics. Only cumulative counters and the latest
 * frame timings are retained; no per-frame history or trace objects are kept.
 *
 * All timestamps are local monotonic-clock timestamps. localPipelineDurationMs
 * ends when the local UDP send calls complete and is not an end-to-end latency.
 */
data class MirrorMetricsSnapshot(
    val capturedFrames: Long = 0,
    val overwrittenFrames: Long = 0,
    val processedFrames: Long = 0,
    val encodedFrames: Long = 0,
    val sentFrames: Long = 0,
    val failedFrames: Long = 0,
    val captureFps: Double = 0.0,
    val processFps: Double = 0.0,
    val encodeFps: Double = 0.0,
    val sendFps: Double = 0.0,
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    val jpegBytes: Int = 0,
    val udpChunks: Int = 0,
    val lastCapturedAtMillis: Long = 0,
    val lastSubmittedAtMillis: Long = 0,
    val lastTakenAtMillis: Long = 0,
    val lastEncodedAtMillis: Long = 0,
    val lastSentAtMillis: Long = 0,
    val captureToSubmitMs: Long = 0,
    val queueAgeMs: Long = 0,
    val encodeDurationMs: Long = 0,
    val udpSendDurationMs: Long = 0,
    val localPipelineDurationMs: Long = 0,
)

internal class MirrorMetricsTracker {
    private val _snapshot = MutableStateFlow(MirrorMetricsSnapshot())
    val snapshot: StateFlow<MirrorMetricsSnapshot> = _snapshot.asStateFlow()

    private var processedFrames = 0L
    private var encodedFrames = 0L
    private var sentFrames = 0L
    private var failedFrames = 0L

    private var lastCapturedAt = 0L
    private var lastSubmittedAt = 0L
    private var lastTakenAt = 0L
    private var lastEncodedAt = 0L
    private var lastSentAt = 0L

    private var lastInputWidth = 0
    private var lastInputHeight = 0
    private var lastOutputWidth = 0
    private var lastOutputHeight = 0
    private var lastJpegBytes = 0
    private var lastUdpChunks = 0

    private var lastCaptureToSubmitMs = 0L
    private var lastQueueAgeMs = 0L
    private var lastEncodeDurationMs = 0L
    private var lastUdpSendDurationMs = 0L
    private var lastLocalPipelineDurationMs = 0L

    private var windowStartedAt = -1L
    private var windowCapturedFrames = 0L
    private var windowProcessedFrames = 0L
    private var windowEncodedFrames = 0L
    private var windowSentFrames = 0L

    fun onFrameTaken(frame: BufferedFrame, takenAtMillis: Long = currentMonotonicMillis()) {
        processedFrames++
        lastCapturedAt = frame.capturedAtMillis
        lastSubmittedAt = frame.submittedAtMillis
        lastTakenAt = takenAtMillis
        lastInputWidth = frame.bitmap.width
        lastInputHeight = frame.bitmap.height
        lastCaptureToSubmitMs = (lastSubmittedAt - lastCapturedAt).coerceAtLeast(0)
        lastQueueAgeMs = (lastTakenAt - lastSubmittedAt).coerceAtLeast(0)
    }

    fun onFrameEncoded(
        encodedAtMillis: Long,
        outputWidth: Int,
        outputHeight: Int,
        jpegBytes: Int,
    ) {
        encodedFrames++
        lastEncodedAt = encodedAtMillis
        lastOutputWidth = outputWidth
        lastOutputHeight = outputHeight
        lastJpegBytes = jpegBytes
        lastEncodeDurationMs = (encodedAtMillis - lastTakenAt).coerceAtLeast(0)
    }

    fun onFrameSent(sendStartedAtMillis: Long, sentAtMillis: Long, udpChunks: Int) {
        sentFrames++
        lastSentAt = sentAtMillis
        lastUdpChunks = udpChunks
        lastUdpSendDurationMs = (sentAtMillis - sendStartedAtMillis).coerceAtLeast(0)
        lastLocalPipelineDurationMs = (sentAtMillis - lastCapturedAt).coerceAtLeast(0)
    }

    fun onFrameFailed() {
        failedFrames++
    }

    fun reset() {
        _snapshot.value = MirrorMetricsSnapshot()
        processedFrames = 0L
        encodedFrames = 0L
        sentFrames = 0L
        failedFrames = 0L
        lastCapturedAt = 0L
        lastSubmittedAt = 0L
        lastTakenAt = 0L
        lastEncodedAt = 0L
        lastSentAt = 0L
        lastInputWidth = 0
        lastInputHeight = 0
        lastOutputWidth = 0
        lastOutputHeight = 0
        lastJpegBytes = 0
        lastUdpChunks = 0
        lastCaptureToSubmitMs = 0L
        lastQueueAgeMs = 0L
        lastEncodeDurationMs = 0L
        lastUdpSendDurationMs = 0L
        lastLocalPipelineDurationMs = 0L
        windowStartedAt = -1L
        windowCapturedFrames = 0L
        windowProcessedFrames = 0L
        windowEncodedFrames = 0L
        windowSentFrames = 0L
    }

    fun publish(frameBus: FrameBus, force: Boolean = false) {
        val now = currentMonotonicMillis()
        val captured = frameBus.capturedFrameCount.value
        val overwritten = frameBus.overwrittenFrameCount.value
        if (windowStartedAt < 0) {
            resetWindow(now, captured)
        }

        val elapsed = now - windowStartedAt
        if (!force && elapsed < METRICS_PUBLISH_INTERVAL_MS) return

        val seconds = elapsed.coerceAtLeast(1L) / 1_000.0
        _snapshot.value = MirrorMetricsSnapshot(
            capturedFrames = captured,
            overwrittenFrames = overwritten,
            processedFrames = processedFrames,
            encodedFrames = encodedFrames,
            sentFrames = sentFrames,
            failedFrames = failedFrames,
            captureFps = (captured - windowCapturedFrames).coerceAtLeast(0) / seconds,
            processFps = (processedFrames - windowProcessedFrames).coerceAtLeast(0) / seconds,
            encodeFps = (encodedFrames - windowEncodedFrames).coerceAtLeast(0) / seconds,
            sendFps = (sentFrames - windowSentFrames).coerceAtLeast(0) / seconds,
            inputWidth = lastInputWidth,
            inputHeight = lastInputHeight,
            outputWidth = lastOutputWidth,
            outputHeight = lastOutputHeight,
            jpegBytes = lastJpegBytes,
            udpChunks = lastUdpChunks,
            lastCapturedAtMillis = lastCapturedAt,
            lastSubmittedAtMillis = lastSubmittedAt,
            lastTakenAtMillis = lastTakenAt,
            lastEncodedAtMillis = lastEncodedAt,
            lastSentAtMillis = lastSentAt,
            captureToSubmitMs = lastCaptureToSubmitMs,
            queueAgeMs = lastQueueAgeMs,
            encodeDurationMs = lastEncodeDurationMs,
            udpSendDurationMs = lastUdpSendDurationMs,
            localPipelineDurationMs = lastLocalPipelineDurationMs,
        )
        resetWindow(now, captured)
    }

    private fun resetWindow(now: Long, captured: Long) {
        windowStartedAt = now
        windowCapturedFrames = captured
        windowProcessedFrames = processedFrames
        windowEncodedFrames = encodedFrames
        windowSentFrames = sentFrames
    }

    private companion object {
        const val METRICS_PUBLISH_INTERVAL_MS = 500L
    }
}
