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
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * ScreenCaptureFrameSource - 基于 MediaProjection 的屏幕录制帧源。
 *
 * ImageReader 只保留最新帧，像素转换与裁剪在专用 HandlerThread 完成，主线程仅
 * 用于注册系统 MediaProjection 回调。源层不重复执行固定 FPS 限速；发送节奏由
 * MirrorEngine 统一控制。
 */
class ScreenCaptureFrameSource(
    private val context: Context,
    private val mediaProjection: MediaProjection,
) : FrameSource {

    private val lifecycleLock = Any()
    private val started = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val intentionalProjectionStop = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)
    private val previewDispatcher = FramePreviewDispatcher()

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var callbackRegistered = false
    private var packedRgbaBuffer: ByteBuffer? = null
    private var argbPixelBuffer: IntArray? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            val unexpected = !intentionalProjectionStop.get()
            releaseCaptureResources(stopProjection = false)
            if (unexpected) {
                reportFailure("屏幕录制授权已结束")
            }
        }
    }

    /** 裁剪区域配置 */
    @Volatile
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    /** 最大输出宽度（高度按比例） */
    @Volatile
    var maxWidth: Int = 960

    override val isRunning: Boolean get() = running.get()

    override fun onFailure(handler: (String) -> Unit) {
        failureHandler = handler
    }

    override fun onSourceDimensions(handler: ((FrameSourceDimensions) -> Unit)?) {
        previewDispatcher.onSourceDimensions(handler)
    }

    override fun onPreviewFrame(handler: ((PlatformBitmap) -> Unit)?) {
        previewDispatcher.onPreviewFrame(handler)
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        check(!released.get()) { "屏幕帧源已释放，不能重新启动" }
        // A MediaProjection grant can only be consumed once. A stopped source instance
        // is intentionally one-shot; callers must create a new source for a new grant.
        if (!started.compareAndSet(false, true)) return

        val metrics = getDisplayMetrics()
        val scale = minOf(1f, maxWidth.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels))
        val outW = maxOf(2, (metrics.widthPixels * scale).toInt())
        val outH = maxOf(2, (metrics.heightPixels * scale).toInt())

        val thread = HandlerThread("android-screen-frame-source").apply { start() }
        val handler = Handler(thread.looper)
        val reader = try {
            ImageReader.newInstance(outW, outH, PixelFormat.RGBA_8888, 2)
        } catch (error: Throwable) {
            thread.quitSafely()
            throw error
        }
        reader.setOnImageAvailableListener({ source ->
            processFrame(source, onFrame)
        }, handler)

        // Publish the one-shot source and the system projection atomically. A stop()
        // before this block prevents registration; a stop() racing with it waits until
        // every captured resource has been published and can then release it.
        synchronized(lifecycleLock) {
            if (released.get() || stopRequested.get()) {
                reader.setOnImageAvailableListener(null, null)
                reader.close()
                thread.quitSafely()
                return
            }

            captureThread = thread
            captureHandler = handler
            imageReader = reader
            running.set(true)

            try {
                mediaProjection.registerCallback(projectionCallback, mainHandler)
                callbackRegistered = true

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenMirror",
                    outW,
                    outH,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    null,
                ) ?: throw IllegalStateException("createVirtualDisplay 返回 null")
            } catch (error: Throwable) {
                intentionalProjectionStop.set(true)
                releaseCaptureResources(stopProjection = true)
                throw error
            }
        }
    }

    override fun stop() {
        stopRequested.set(true)
        intentionalProjectionStop.set(true)
        releaseCaptureResources(stopProjection = true)
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
    }

    private fun releaseCaptureResources(stopProjection: Boolean) {
        synchronized(lifecycleLock) {
            running.set(false)

            imageReader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null
            packedRgbaBuffer = null
            argbPixelBuffer = null

            if (callbackRegistered) {
                callbackRegistered = false
                runCatching { mediaProjection.unregisterCallback(projectionCallback) }
            }

            if (stopProjection && !intentionalProjectionStop.get()) {
                intentionalProjectionStop.set(true)
            }
            if (stopProjection) {
                runCatching { mediaProjection.stop() }
            }

            captureHandler = null
            captureThread?.quitSafely()
            captureThread = null
        }
    }

    private fun processFrame(reader: ImageReader, onFrame: (PlatformBitmap) -> Unit) {
        if (!running.get()) return
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            if (!running.get()) return

            val bitmap = image.toFrameBitmap()
            if (!running.get()) {
                bitmap.recycle()
                return
            }

            previewDispatcher.publishDimensions(bitmap.width, bitmap.height)
            if (previewDispatcher.shouldPublishFrame()) {
                val preview = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (preview != null) {
                    previewDispatcher.publishFrame(PlatformBitmap(preview))
                }
            }
            val cropped = applyCrop(bitmap)
            var delivered = false
            try {
                if (!running.get()) return
                onFrame(PlatformBitmap(cropped))
                delivered = true
            } finally {
                if (cropped !== bitmap) bitmap.recycle()
                if (!delivered) cropped.recycle()
            }
        } catch (error: Throwable) {
            val detail = error.message ?: error::class.simpleName ?: "未知错误"
            intentionalProjectionStop.set(true)
            releaseCaptureResources(stopProjection = true)
            reportFailure("屏幕帧处理失败: $detail")
        } finally {
            image.close()
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

    /**
     * RGBA_8888 ImageReader 常返回带行填充的缓冲区。无填充时直接零拷贝交给
     * Bitmap；有填充时复用紧凑 ByteBuffer 去掉 row padding，避免每帧创建大数组。
     */
    private fun Image.toFrameBitmap(): Bitmap {
        val plane = planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val frameWidth = width
        val frameHeight = height
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val source = plane.buffer
        source.rewind()

        val tightRowBytes = frameWidth * pixelStride
        if (pixelStride == 4 && rowStride == tightRowBytes) {
            bitmap.copyPixelsFromBuffer(source)
            return bitmap
        }

        if (pixelStride == 4) {
            val requiredBytes = tightRowBytes * frameHeight
            val packed = packedRgbaBuffer
                ?.takeIf { it.capacity() >= requiredBytes }
                ?: ByteBuffer.allocate(requiredBytes).also { packedRgbaBuffer = it }
            packed.clear()
            for (y in 0 until frameHeight) {
                val rowStart = y * rowStride
                val available = (source.limit() - rowStart).coerceAtLeast(0)
                val bytesToCopy = minOf(tightRowBytes, available)
                if (bytesToCopy <= 0) break
                val row = source.duplicate()
                row.position(rowStart)
                row.limit(rowStart + bytesToCopy)
                packed.put(row)
            }
            packed.flip()
            bitmap.copyPixelsFromBuffer(packed)
            return bitmap
        }

        val requiredPixels = frameWidth * frameHeight
        val pixels = argbPixelBuffer
            ?.takeIf { it.size >= requiredPixels }
            ?: IntArray(requiredPixels).also { argbPixelBuffer = it }
        for (y in 0 until frameHeight) {
            for (x in 0 until frameWidth) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 3 >= source.limit()) continue
                val r = source.get(offset).toInt() and 0xFF
                val g = source.get(offset + 1).toInt() and 0xFF
                val b = source.get(offset + 2).toInt() and 0xFF
                val a = source.get(offset + 3).toInt() and 0xFF
                pixels[y * frameWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, frameWidth, 0, 0, frameWidth, frameHeight)
        return bitmap
    }

    private fun reportFailure(message: String) {
        if (failureReported.compareAndSet(false, true)) {
            failureHandler?.invoke(message)
        }
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
