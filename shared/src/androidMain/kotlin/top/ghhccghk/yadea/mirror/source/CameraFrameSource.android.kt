package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * CameraFrameSource - 基于 CameraX 的摄像头帧源。
 *
 * CameraX owns capture cadence and conflation. The mirror scheduler in
 * commonMain applies the negotiated vehicle FPS, so this source must not add a
 * second wall-clock FPS limit.
 */
class CameraFrameSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val useFrontCamera: Boolean = false,
) : FrameSource {

    private val lifecycleLock = Any()
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "android-camera-frame-source").apply { isDaemon = true }
    }
    private val startGeneration = AtomicLong(0L)
    private val released = AtomicBoolean(false)
    private val failureReported = AtomicBoolean(false)

    private val running = AtomicBoolean(false)

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    /** 裁剪区域配置。 */
    @Volatile
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    /** CameraX 分析目标分辨率；最终输出分辨率仍由 MirrorEngine 协商结果决定。 */
    var targetResolution: Size = Size(960, 368)

    override val isRunning: Boolean get() = running.get()

    override fun onFailure(handler: (String) -> Unit) {
        failureHandler = handler
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        check(!released.get()) { "摄像头帧源已释放，不能重新启动" }
        if (!running.compareAndSet(false, true)) return

        failureReported.set(false)
        val generation = startGeneration.incrementAndGet()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!isCurrentStart(generation)) return@addListener
            try {
                val provider = future.get()
                synchronized(lifecycleLock) {
                    if (!isCurrentStart(generation)) return@synchronized

                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(targetResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        processFrame(generation, imageProxy, onFrame)
                    }

                    cameraProvider = provider
                    imageAnalysis = analysis
                    provider.unbind(analysis)
                    val selector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    provider.bindToLifecycle(lifecycleOwner, selector, analysis)
                }
            } catch (error: Exception) {
                failStart(
                    generation,
                    "摄像头启动失败: ${error.message ?: error::class.simpleName ?: "未知错误"}",
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        if (!running.getAndSet(false)) return
        startGeneration.incrementAndGet()

        val (provider, analysis) = synchronized(lifecycleLock) {
            val currentProvider = cameraProvider
            val currentAnalysis = imageAnalysis
            cameraProvider = null
            imageAnalysis = null
            currentProvider to currentAnalysis
        }
        try {
            analysis?.clearAnalyzer()
            if (provider != null && analysis != null) {
                provider.unbind(analysis)
            }
        } catch (_: Exception) {
            // CameraX may already have unbound during lifecycle shutdown.
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
        analyzerExecutor.shutdownNow()
    }

    private fun isCurrentStart(generation: Long): Boolean =
        running.get() && !released.get() && startGeneration.get() == generation

    private fun failStart(generation: Long, message: String) {
        if (!isCurrentStart(generation)) return
        stop()
        reportFailure(message)
    }

    private fun reportFailure(message: String) {
        if (failureReported.compareAndSet(false, true)) {
            failureHandler?.invoke(message)
        }
    }

    private fun processFrame(
        generation: Long,
        imageProxy: ImageProxy,
        onFrame: (PlatformBitmap) -> Unit,
    ) {
        try {
            if (!isCurrentStart(generation)) return

            val bitmap = imageProxy.toRgbaBitmap()
            var cropped: Bitmap? = null
            var delivered = false
            try {
                if (!isCurrentStart(generation)) return
                cropped = applyCrop(bitmap)
                if (!isCurrentStart(generation)) return
                onFrame(PlatformBitmap(cropped))
                delivered = true
            } finally {
                if (cropped !== bitmap) bitmap.recycle()
                if (!delivered) cropped?.recycle()
            }
        } catch (error: Exception) {
            if (isCurrentStart(generation)) {
                failStart(
                    generation,
                    "摄像头帧处理失败: ${error.message ?: error::class.simpleName ?: "未知错误"}",
                )
            }
        } finally {
            imageProxy.close()
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
