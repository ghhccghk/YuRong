package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

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

    private var lastFrameAt = 0L
    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)

    val isPlaying: Boolean
        get() = playing

    val durationMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(durationUs)

    val positionMs: Long
        get() = TimeUnit.MICROSECONDS.toMillis(positionUs)

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
        playing = true
        executor.execute { decodeFrames(onFrame) }
    }

    override fun stop() {
        running = false
        playing = false
        executor.shutdownNow()
    }

    fun play() {
        playing = true
    }

    fun pause() {
        playing = false
    }

    fun seekTo(positionMs: Long) {
        positionUs = TimeUnit.MILLISECONDS.toMicros(positionMs.coerceAtLeast(0L))
    }

    private fun decodeFrames(onFrame: (PlatformBitmap) -> Unit) {
        try {
            retriever.setDataSource(context, uri)
            durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let(TimeUnit.MILLISECONDS::toMicros)
                ?: 0L

            while (running) {
                if (!playing) {
                    Thread.sleep(50)
                    continue
                }

                val now = System.currentTimeMillis()
                if (now - lastFrameAt < frameIntervalMs) {
                    Thread.sleep(frameIntervalMs - (now - lastFrameAt))
                    continue
                }
                lastFrameAt = now

                val frame = if (durationUs > 0L) {
                    retriever.getFrameAtTime(
                        positionUs.coerceIn(0L, durationUs),
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )
                } else {
                    retriever.frameAtTime
                }

                if (frame != null) {
                    deliverFrame(frame, onFrame)
                }

                positionUs += TimeUnit.MILLISECONDS.toMicros(frameIntervalMs)
                if (durationUs > 0L && positionUs >= durationUs) {
                    positionUs = 0L
                }
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
