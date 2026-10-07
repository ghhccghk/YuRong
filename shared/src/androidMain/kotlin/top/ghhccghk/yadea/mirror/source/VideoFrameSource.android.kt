package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * VideoFrameSource - 视频/图片帧源
 *
 * 使用 MediaPlayer + TextureView 渲染视频，
 * 通过 PixelCopy (API 24+) 或 TextureView.getBitmap() 捕获帧。
 */
class VideoFrameSource(
    private val context: Context,
    private val uri: Uri,
    frameIntervalMs: Long = 100
) : FrameSource {

    /** 供调用方添加到视图层级的 TextureView */
    val textureView: TextureView = TextureView(context)

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastFrameAt = 0L
    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)
    private var framePump: Runnable? = null

    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    var isPlaying: Boolean = false
        private set

    val durationMs: Long get() = mediaPlayer?.duration?.toLong() ?: 0L
    val positionMs: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L

    override val isRunning: Boolean get() = running

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (running) return
        running = true

        if (textureView.isAvailable) {
            setupPlayer(onFrame)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    setupPlayer(onFrame)
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    private fun setupPlayer(onFrame: (PlatformBitmap) -> Unit) {
        val st = textureView.surfaceTexture ?: return
        val sf = Surface(st)
        surface = sf

        val self = this
        val player = MediaPlayer().apply {
            setDataSource(context, uri)
            setSurface(sf)
            setOnPreparedListener { mp ->
                mp.start()
                self.isPlaying = true
                startFramePump(onFrame)
            }
            setOnCompletionListener {
                self.isPlaying = false
            }
            setOnErrorListener { _, _, _ ->
                self.isPlaying = false
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
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                this.isPlaying = true
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                this.isPlaying = false
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
            val bmp = Bitmap.createBitmap(
                textureView.width.coerceAtLeast(1),
                textureView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val s = surface ?: return
                PixelCopy.request(s, bmp, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        onFrameDeliver(bmp, onFrame)
                    } else {
                        bmp.recycle()
                    }
                }, mainHandler)
            } else {
                val tvBmp = textureView.getBitmap(bmp)
                if (tvBmp != null) {
                    onFrameDeliver(tvBmp, onFrame)
                } else {
                    bmp.recycle()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun onFrameDeliver(bmp: Bitmap, onFrame: (PlatformBitmap) -> Unit) {
        val cropped = applyCrop(bmp)
        onFrame(PlatformBitmap(cropped))
        if (cropped !== bmp) bmp.recycle()
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
