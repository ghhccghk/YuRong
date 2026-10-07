package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * ScreenCaptureFrameSource - 基于 MediaProjection 的屏幕录制帧源
 *
 * 特性:
 * - 系统屏幕捕获
 * - 支持裁剪区域配置
 * - 10fps 帧率控制
 * - 自动缩放到适合仪表的分辨率
 */
class ScreenCaptureFrameSource(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    frameIntervalMs: Long = 100
) : FrameSource {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var running = false
    private var lastFrameAt = 0L
    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)

    /** 裁剪区域配置 */
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    /** 最大输出宽度（高度按比例） */
    var maxWidth: Int = 960

    override val isRunning: Boolean get() = running

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (running) return
        running = true

        val metrics = getDisplayMetrics()
        val scale = minOf(1f, maxWidth.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels))
        val outW = maxOf(2, (metrics.widthPixels * scale).toInt())
        val outH = maxOf(2, (metrics.heightPixels * scale).toInt())

        val reader = ImageReader.newInstance(outW, outH, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            processFrame(r, onFrame)
        }, null)
        imageReader = reader

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenMirror",
            outW, outH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )
    }

    override fun stop() {
        running = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    private fun processFrame(reader: ImageReader, onFrame: (PlatformBitmap) -> Unit) {
        if (!running) return
        val image = reader.acquireLatestImage() ?: return
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameAt < frameIntervalMs) return
            lastFrameAt = now

            val bitmap = image.toBitmap()
            val cropped = applyCrop(bitmap)
            onFrame(PlatformBitmap(cropped))
            if (cropped !== bitmap) bitmap.recycle()
        } catch (_: Exception) {
            // 帧处理失败时跳过
        } finally {
            image.close()
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

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = this.width
        val height = this.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = plane.buffer
        buffer.rewind()

        if (pixelStride == 4 && rowStride == width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val pixels = IntArray(width * height)
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(row, 0, minOf(rowStride, buffer.remaining()))
                for (x in 0 until width) {
                    val offset = x * pixelStride
                    if (offset + 3 >= row.size) continue
                    val r = row[offset].toInt() and 0xFF
                    val g = row[offset + 1].toInt() and 0xFF
                    val b = row[offset + 2].toInt() and 0xFF
                    val a = row[offset + 3].toInt() and 0xFF
                    pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return bitmap
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().also { wm.defaultDisplay.getMetrics(it) }
    }

    companion object {
        /**
         * 创建 MediaProjection 的 Intent
         */
        fun createProjectionIntent(context: Context): Intent {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return manager.createScreenCaptureIntent()
        }
    }
}
