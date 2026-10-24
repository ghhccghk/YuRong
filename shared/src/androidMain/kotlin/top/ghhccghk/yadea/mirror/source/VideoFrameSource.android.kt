package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlin.math.max
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

private const val MAX_CAPTURE_EDGE = 1280
private const val PAUSE_POLL_INTERVAL_MS = 50L
private const val UNKNOWN_SOURCE_FRAME_INTERVAL_MS = 100L
private const val MIN_SOURCE_FRAME_INTERVAL_MS = 1L
private const val MAX_SOURCE_FRAME_INTERVAL_MS = 1_000L
private const val DEFAULT_TARGET_FPS = 30

/**
 * Android 视频/网络流帧源。
 *
 * 每次 start 都创建独立的 MediaMetadataRetriever 与工作线程。播放节奏优先使用
 * 媒体帧时间戳，不在源层套用固定的 10/30/60 FPS 限速；最终发送节奏由
 * MirrorEngine 根据车辆协商结果统一调度，FrameBus 只保留最新一帧。
 */
class VideoFrameSource(
    private val context: Context,
    private val uri: Uri,
) : FrameSource {

    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var playing = true

    @Volatile
    private var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    @Volatile
    private var targetFps: Int = DEFAULT_TARGET_FPS

    @Volatile
    private var durationUs = 0L

    @Volatile
    private var positionUs = 0L

    @Volatile
    private var playbackOriginUs = 0L

    @Volatile
    private var playbackOriginElapsedMs = 0L

    val isPlaying: Boolean
        get() = playing

    val durationMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(durationUs)

    val positionMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(currentPositionUs())

    override val isRunning: Boolean
        get() = running.get()

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

    override fun onFailure(handler: (String) -> Unit) {
        failureHandler = handler
    }

    override fun updateTargetFps(fps: Int) {
        if (fps > 0) targetFps = fps.coerceAtMost(60)
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        check(!released.get()) { "视频帧源已释放，不能重新启动" }
        if (uri.toString().isBlank()) {
            throw IllegalArgumentException("请先选择视频文件或填写视频地址")
        }

        val startGeneration: Long
        synchronized(lifecycleLock) {
            if (running.get()) return
            startGeneration = generation.incrementAndGet()
            durationUs = 0L
            positionUs = 0L
            playbackOriginUs = 0L
            playbackOriginElapsedMs = SystemClock.elapsedRealtime()
            playing = true
            running.set(true)

            worker = Thread(
                {
                    decodeFrames(
                        generation = startGeneration,
                        retriever = MediaMetadataRetriever(),
                        onFrame = onFrame,
                    )
                },
                "android-video-frame-source",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    override fun stop() {
        val thread = synchronized(lifecycleLock) {
            if (!running.getAndSet(false)) return
            generation.incrementAndGet()
            playing = false
            worker.also { worker = null }
        }
        thread?.interrupt()
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
    }

    fun play() {
        synchronized(lifecycleLock) {
            if (playing || !running.get()) return
            playbackOriginUs = positionUs
            playbackOriginElapsedMs = SystemClock.elapsedRealtime()
            playing = true
        }
    }

    fun pause() {
        synchronized(lifecycleLock) {
            if (!playing) return
            positionUs = currentPositionUsLocked()
            playing = false
        }
    }

    fun seekTo(positionMs: Long) {
        synchronized(lifecycleLock) {
            val requestedUs = TimeUnit.MILLISECONDS.toMicros(positionMs.coerceAtLeast(0L))
            positionUs = if (durationUs > 0L) requestedUs.coerceAtMost(durationUs) else requestedUs
            playbackOriginUs = positionUs
            playbackOriginElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    private fun currentPositionUs(): Long =
        synchronized(lifecycleLock) { currentPositionUsLocked() }

    private fun currentPositionUsLocked(): Long {
        if (!playing) return positionUs
        val elapsedUs = TimeUnit.MILLISECONDS.toMicros(
            (SystemClock.elapsedRealtime() - playbackOriginElapsedMs).coerceAtLeast(0L)
        )
        return (playbackOriginUs + elapsedUs).coerceAtLeast(0L)
    }

    private fun decodeFrames(
        generation: Long,
        retriever: MediaMetadataRetriever,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        try {
            setDataSource(retriever)
            val sourceDurationUs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let(TimeUnit.MILLISECONDS::toMicros)
                ?: 0L
            val sourceWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val sourceHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val (scaledWidth, scaledHeight) = scaledSize(sourceWidth, sourceHeight)
            val sourceFrameIntervalUs = resolveSourceFrameIntervalUs(retriever, sourceDurationUs)

            synchronized(lifecycleLock) {
                if (!isCurrent(generation)) return
                durationUs = sourceDurationUs
                positionUs = 0L
                playbackOriginUs = 0L
                playbackOriginElapsedMs = SystemClock.elapsedRealtime()
            }

            var nextRetrievalAtMs = SystemClock.elapsedRealtime()

            while (isCurrent(generation)) {
                if (!playing) {
                    Thread.sleep(PAUSE_POLL_INTERVAL_MS)
                    nextRetrievalAtMs = SystemClock.elapsedRealtime()
                    continue
                }

                waitUntil(nextRetrievalAtMs)
                if (!isCurrent(generation)) break

                val targetUs = currentPositionUs()
                synchronized(lifecycleLock) {
                    if (isCurrent(generation)) positionUs = targetUs
                }

                val frame = frameAt(retriever, targetUs, scaledWidth, scaledHeight)
                if (!isCurrent(generation)) {
                    frame?.recycle()
                    break
                }

                if (frame != null) {
                    deliverFrame(generation, frame, onFrame)
                }

                if (sourceDurationUs > 0L &&
                    currentPositionUs() >= sourceDurationUs
                ) {
                    synchronized(lifecycleLock) {
                        if (!isCurrent(generation)) break
                        positionUs = 0L
                        playbackOriginUs = 0L
                        playbackOriginElapsedMs = SystemClock.elapsedRealtime()
                    }
                }

                val intervalMs = effectiveFrameIntervalMs(sourceFrameIntervalUs)
                nextRetrievalAtMs += intervalMs

                // MediaMetadataRetriever may decode slower than the requested cadence.
                // Skip missed playback slots instead of issuing a burst of old frames.
                val now = SystemClock.elapsedRealtime()
                if (nextRetrievalAtMs < now) {
                    val missedSlots = (now - nextRetrievalAtMs + intervalMs - 1L) / intervalMs
                    nextRetrievalAtMs += missedSlots * intervalMs
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (isCurrent(generation)) {
                val detail = error.message ?: error::class.simpleName ?: "未知错误"
                failureHandler?.invoke("无法读取视频源: $detail")
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            synchronized(lifecycleLock) {
                if (generation == this.generation.get()) {
                    running.set(false)
                    playing = false
                    worker = null
                }
            }
        }
    }

    private fun setDataSource(retriever: MediaMetadataRetriever) {
        when (uri.scheme?.lowercase()) {
            "http", "https", "rtsp" ->
                retriever.setDataSource(uri.toString(), emptyMap())
            "content", "file", "android.resource" ->
                retriever.setDataSource(context, uri)
            null ->
                retriever.setDataSource(uri.toString())
            else ->
                retriever.setDataSource(context, uri)
        }
    }

    private fun effectiveFrameIntervalMs(sourceFrameIntervalUs: Long): Long {
        val sourceIntervalMs = TimeUnit.MICROSECONDS.toMillis(
            sourceFrameIntervalUs.coerceAtLeast(1L),
        )
        val targetIntervalMs = (1_000L / targetFps.coerceAtLeast(1)).coerceAtLeast(1L)
        return max(sourceIntervalMs, targetIntervalMs)
            .coerceIn(MIN_SOURCE_FRAME_INTERVAL_MS, MAX_SOURCE_FRAME_INTERVAL_MS)
    }

    private fun frameAt(
        retriever: MediaMetadataRetriever,
        positionUs: Long,
        width: Int,
        height: Int,
    ): Bitmap? {
        val safePositionUs = positionUs.coerceAtLeast(0L)
        return if (Build.VERSION.SDK_INT >= 27 && width > 0 && height > 0) {
            retriever.getScaledFrameAtTime(
                safePositionUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                width,
                height,
            )
        } else {
            val frame = retriever.getFrameAtTime(
                safePositionUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
            ) ?: return null
            if (width <= 0 || height <= 0 || (frame.width == width && frame.height == height)) {
                frame
            } else {
                Bitmap.createScaledBitmap(frame, width, height, true).also { frame.recycle() }
            }
        }
    }

    private fun resolveSourceFrameIntervalUs(
        retriever: MediaMetadataRetriever,
        durationUs: Long,
    ): Long {
        val captureRate = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it > 0f }
        if (captureRate != null) {
            return (1_000_000f / captureRate).roundToInt().toLong()
        }

        if (Build.VERSION.SDK_INT >= 28 && durationUs > 0L) {
            val frameCount = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            if (frameCount != null) {
                return durationUs / frameCount
            }
        }

        // Only used when the media exposes neither capture rate nor frame count.
        // The engine's negotiated FPS can lower this cadence further.
        return TimeUnit.MILLISECONDS.toMicros(UNKNOWN_SOURCE_FRAME_INTERVAL_MS)
    }

    private fun waitUntil(targetElapsedMs: Long) {
        val waitMs = targetElapsedMs - SystemClock.elapsedRealtime()
        if (waitMs > 0L) Thread.sleep(waitMs)
    }

    private fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val scale = minOf(1f, MAX_CAPTURE_EDGE.toFloat() / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(2) to
            (height * scale).roundToInt().coerceAtLeast(2)
    }

    private fun deliverFrame(
        generation: Long,
        frame: Bitmap,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        val cropped = applyCrop(frame)
        var delivered = false
        try {
            if (!isCurrent(generation)) return
            onFrame(PlatformBitmap(cropped))
            delivered = true
        } finally {
            if (!delivered) cropped.recycle()
            if (cropped !== frame) frame.recycle()
        }
    }

    private fun applyCrop(bitmap: Bitmap): Bitmap {
        if (cropConfig == FrameCropConfig.FULL) return bitmap
        val rect = cropConfig.toPixelRect(bitmap.width, bitmap.height)
        if (rect.x == 0 && rect.y == 0 &&
            rect.width == bitmap.width && rect.height == bitmap.height
        ) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
    }

    private fun isCurrent(expectedGeneration: Long): Boolean =
        running.get() && !released.get() && generation.get() == expectedGeneration
}
