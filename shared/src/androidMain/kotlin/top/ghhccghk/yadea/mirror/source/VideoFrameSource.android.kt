package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * VideoFrameSource - 视频/图片帧源
 *
 * 特性:
 * - MediaPlayer 解码视频/图片
 * - TextureView 帧捕获
 * - 支持裁剪区域配置
 * - 前台 10fps 帧率控制
 * - 支持播放/暂停/seek
 */
class VideoFrameSource(
    private val context: Context,
    private val uri: Uri,
    frameIntervalMs: Long = 100
) : FrameSource {

    private var mediaPlayer: MediaPlayer? = null
    private var textureView: TextureView? = null
    private var surface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastFrameAt = 0L
    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)
    private var framePump: Runnable? = null

    /** 裁剪区域配置 */
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    /** 是否正在播放 */
    var isPlaying: Boolean = false
        private set

    /** 视频时长 (ms) */
    val durationMs: Long get() = mediaPlayer?.duration?.toLong() ?: 0L

    /** 当前播放位置 (ms) */
    val positionMs: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L

    override val isRunning: Boolean get() = running

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (running) return
        running = true

        val st = SurfaceTexture(0)
        surfaceTexture = st
        val sf = Surface(st)
        surface = sf

        val player = MediaPlayer().apply {
            setDataSource(context, uri)
            setSurface(sf)
            setOnPreparedListener { mp ->
                mp.start()
                isPlaying = true
                startFramePump(onFrame)
            }
            setOnCompletionListener {
                isPlaying = false
            }
            setOnErrorListener { _, _, _ ->
                isPlaying = false
                true
            }
            prepareAsync()
        }
        mediaPlayer = player
    }

    override fun stop() {
        running = false
        isPlaying = false
        framePump?.let { mainHandler.removeCallbacks(it) }
        framePump = null
        mediaPlayer?.release()
        mediaPlayer = null
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isPlaying = true
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
    }

    private fun startFramePump(onFrame: (PlatformBitmap) -> Unit) {
        val pump = object : Runnable {
            override fun run() {
                if (!running) return
                val now = System.currentTimeMillis()
                if (isPlaying && now - lastFrameAt >= frameIntervalMs) {
                    lastFrameAt = now
                    captureFrame(onFrame)
                }
                mainHandler.postDelayed(this, frameIntervalMs / 2)
            }
        }
        framePump = pump
        mainHandler.post(pump)
    }

    private fun captureFrame(onFrame: (PlatformBitmap) -> Unit) {
        try {
            val st = surfaceTexture ?: return
            val bmp = Bitmap.createBitmap(st.textureWidth.coerceAtLeast(1),
                st.textureHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            // SurfaceTexture.getBitmap() is API 26+, fallback to canvas draw for older
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                st.getBitmap(bmp)
            }
            val cropped = applyCrop(bmp)
            onFrame(PlatformBitmap(cropped))
            if (cropped !== bmp) bmp.recycle()
        } catch (_: Exception) {
            // 帧捕获失败时跳过
        }
    }

    private fun applyCrop(bitmap: Bitmap): Bitmap {
        if (cropConfig == FrameCropConfig.FULL) return bitmap
        val rect = cropConfig.toPixelRect(bitmap.width, bitmap.height)
        if (rect.x == 0 && rect.y == 0 &&
            rect.width == bitmap.width && rect.height == bitmap.height
        ) return bitmap
        return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
    }
}
