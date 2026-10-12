package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

private const val MAX_CAPTURE_EDGE = 1280

/**
 * Android 视频/网络流帧源。
 *
 * 使用 MediaMetadataRetriever 从本地 content URI、文件路径或受支持的网络地址
 * 提取视频帧，不依赖尚未挂载到 Window 的 TextureView。
 */
class VideoFrameSource(
    private val context: Context,
    private val uri: Uri,
    frameIntervalMs: Long = 100,
) : FrameSource {

    private val retriever = MediaMetadataRetriever()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "android-video-frame-source").apply { isDaemon = true }
    }
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var playing = true

    @Volatile
    private var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    @Volatile
    private var durationUs = 0L

    @Volatile
    private var positionUs = 0L

    @Volatile
    private var playbackOriginUs = 0L

    @Volatile
    private var playbackOriginElapsedMs = 0L

    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)

    val isPlaying: Boolean
        get() = playing

    val durationMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(durationUs)

    val positionMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(currentPositionUs())

    override val isRunning: Boolean
        get() = running

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

    override fun onFailure(handler: (String) -> Unit) {
        failureHandler = handler
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (!started.compareAndSet(false, true)) return
        if (uri.toString().isBlank()) {
            started.set(false)
            throw IllegalArgumentException("请先选择视频文件或填写视频地址")
        }

        running = true
        positionUs = 0L
        playbackOriginUs = 0L
        playbackOriginElapsedMs = SystemClock.elapsedRealtime()
        playing = true
        executor.execute { decodeFrames(onFrame) }
    }

    override fun stop() {
        running = false
        playing = false
        executor.shutdownNow()
    }

    fun play() {
        if (playing) return
        playbackOriginUs = positionUs
        playbackOriginElapsedMs = SystemClock.elapsedRealtime()
        playing = true
    }

    fun pause() {
        if (!playing) return
        positionUs = currentPositionUs()
        playing = false
    }

    fun seekTo(positionMs: Long) {
        positionUs = TimeUnit.MILLISECONDS.toMicros(positionMs.coerceAtLeast(0L))
        playbackOriginUs = positionUs
        playbackOriginElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun currentPositionUs(): Long {
        if (!playing) return positionUs
        val elapsedUs = TimeUnit.MILLISECONDS.toMicros(
            (SystemClock.elapsedRealtime() - playbackOriginElapsedMs).coerceAtLeast(0L)
        )
        return (playbackOriginUs + elapsedUs).coerceAtLeast(0L)
    }

    private fun decodeFrames(onFrame: (PlatformBitmap) -> Unit) {
        try {
            retriever.setDataSource(context, uri)
            durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let(TimeUnit.MILLISECONDS::toMicros)
                ?: 0L
            val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val (scaledWidth, scaledHeight) = scaledSize(sourceWidth, sourceHeight)

            playbackOriginUs = positionUs
            playbackOriginElapsedMs = SystemClock.elapsedRealtime()

            while (running) {
                if (!playing) {
                    Thread.sleep(50)
                    continue
                }

                val targetUs = currentPositionUs()
                positionUs = targetUs
                val frame = frameAt(targetUs, scaledWidth, scaledHeight)
                if (frame != null) {
                    deliverFrame(frame, onFrame)
                }

                if (durationUs > 0L && positionUs >= durationUs) {
                    playbackOriginUs = 0L
                    playbackOriginElapsedMs = SystemClock.elapsedRealtime()
                    positionUs = 0L
                }
                Thread.sleep(frameIntervalMs)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (running) {
                val detail = error.message ?: error::class.simpleName ?: "未知错误"
                failureHandler?.invoke("无法读取视频源: $detail")
            }
        } finally {
            running = false
            playing = false
            releaseRetriever()
        }
    }

    private fun frameAt(positionUs: Long, width: Int, height: Int): Bitmap? {
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

    private fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val scale = minOf(1f, MAX_CAPTURE_EDGE.toFloat() / maxOf(width, height))
        return (width * scale).roundToInt().coerceAtLeast(2) to
            (height * scale).roundToInt().coerceAtLeast(2)
    }

    private fun deliverFrame(frame: Bitmap, onFrame: (PlatformBitmap) -> Unit) {
        val cropped = applyCrop(frame)
        onFrame(PlatformBitmap(cropped))
        if (cropped !== frame) {
            frame.recycle()
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

    private fun releaseRetriever() {
        if (!closed.compareAndSet(false, true)) return
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}
