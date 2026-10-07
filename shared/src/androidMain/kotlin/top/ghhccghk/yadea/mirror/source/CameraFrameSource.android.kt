package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * CameraFrameSource - 基于 CameraX 的摄像头帧源
 *
 * 特性:
 * - 支持前置/后置摄像头
 * - 支持裁剪区域配置
 * - 10fps 帧率控制
 * - 自动旋转校正
 */
class CameraFrameSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val useFrontCamera: Boolean = false,
    frameIntervalMs: Long = 100
) : FrameSource {

    private var cameraProvider: ProcessCameraProvider? = null
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var running = false
    private var lastFrameAt = 0L
    private val frameIntervalMs = frameIntervalMs.coerceAtLeast(50)

    /** 裁剪区域配置 */
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    /** 目标分辨率 */
    var targetResolution: Size = Size(960, 368)

    override val isRunning: Boolean get() = running

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (running) return
        running = true

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) return@addListener
            try {
                val provider = future.get()
                cameraProvider = provider

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(targetResolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    processFrame(imageProxy, onFrame)
                }

                provider.unbindAll()
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
                provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            } catch (e: Exception) {
                running = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        running = false
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    fun close() {
        stop()
        analyzerExecutor.shutdownNow()
    }

    private fun processFrame(imageProxy: ImageProxy, onFrame: (PlatformBitmap) -> Unit) {
        try {
            val now = System.currentTimeMillis()
            if (!running || now - lastFrameAt < frameIntervalMs) return
            lastFrameAt = now

            val bitmap = imageProxy.toRgbaBitmap()
            val cropped = applyCrop(bitmap)
            onFrame(PlatformBitmap(cropped))
            if (cropped !== bitmap) bitmap.recycle()
        } catch (_: Exception) {
            // 帧处理失败时跳过
        } finally {
            imageProxy.close()
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

    private fun ImageProxy.toRgbaBitmap(): Bitmap {
        val plane = planes[0]
        val rowStride = plane.rowStride / plane.pixelStride
        var bitmap = Bitmap.createBitmap(rowStride, height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        bitmap.copyPixelsFromBuffer(plane.buffer)

        if (rowStride != width) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            bitmap = cropped
        }

        val rotation = imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }
}
