package top.ghhccghk.yadea.mirror.source

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap

private const val PREVIEW_MAX_EDGE = 1024
private const val DEFAULT_TARGET_WIDTH = 800
private const val DEFAULT_TARGET_HEIGHT = 480

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
    private val previewDispatcher = FramePreviewDispatcher()

    private val running = AtomicBoolean(false)

    @Volatile
    private var failureHandler: ((String) -> Unit)? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var targetWidth = DEFAULT_TARGET_WIDTH

    @Volatile
    private var targetHeight = DEFAULT_TARGET_HEIGHT

    // ImageAnalysis is single-threaded, so this scratch buffer can be reused.
    private var argbPixelBuffer: IntArray? = null

    /** 裁剪区域配置。 */
    @Volatile
    var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

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

    override fun updateTargetSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            targetWidth = width
            targetHeight = height
        }
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
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY,
                                )
                                .build(),
                        )
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

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val orientedWidth: Int
            val orientedHeight: Int
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                orientedWidth = imageProxy.height
                orientedHeight = imageProxy.width
            } else {
                orientedWidth = imageProxy.width
                orientedHeight = imageProxy.height
            }
            previewDispatcher.publishDimensions(orientedWidth, orientedHeight)

            if (previewDispatcher.shouldPublishFrame()) {
                val previewScale = minOf(
                    1f,
                    PREVIEW_MAX_EDGE.toFloat() /
                        maxOf(orientedWidth, orientedHeight).coerceAtLeast(1),
                )
                val preview = imageProxy.toArgbBitmap(
                    outputWidth = (orientedWidth * previewScale)
                        .roundToInt()
                        .coerceAtLeast(1),
                    outputHeight = (orientedHeight * previewScale)
                        .roundToInt()
                        .coerceAtLeast(1),
                    rotationDegrees = rotationDegrees,
                    crop = FrameCropConfig.FULL,
                )
                previewDispatcher.publishFrame(PlatformBitmap(preview))
            }

            val frame = imageProxy.toArgbBitmap(
                outputWidth = targetWidth,
                outputHeight = targetHeight,
                rotationDegrees = rotationDegrees,
                crop = cropConfig,
            )
            var delivered = false
            try {
                if (!isCurrentStart(generation)) return
                onFrame(PlatformBitmap(frame))
                delivered = true
            } finally {
                if (!delivered) frame.recycle()
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

    /**
     * Samples CameraX's RGBA plane directly into the requested output size.
     *
     * The analysis stream now uses the camera's highest available resolution,
     * so allocating and rotating a full-resolution ARGB bitmap first would
     * create an unnecessary 4K/8K allocation peak. Crop coordinates remain in
     * oriented source pixels even though the output is the vehicle resolution.
     */
    private fun ImageProxy.toArgbBitmap(
        outputWidth: Int,
        outputHeight: Int,
        rotationDegrees: Int,
        crop: FrameCropConfig,
    ): Bitmap {
        require(outputWidth > 0 && outputHeight > 0) {
            "摄像头输出尺寸无效: ${outputWidth}x$outputHeight"
        }
        require(crop.isValid) { "摄像头裁切区域无效" }
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        if (pixelStride < 4) {
            throw IllegalStateException("摄像头 RGBA 像素步长无效: $pixelStride")
        }

        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val orientedWidth = if (normalizedRotation == 90 || normalizedRotation == 270) {
            height
        } else {
            width
        }
        val orientedHeight = if (normalizedRotation == 90 || normalizedRotation == 270) {
            width
        } else {
            height
        }
        val selected = crop.toPixelRect(orientedWidth, orientedHeight)

        // Preserve the previous center-crop-to-output-ratio behavior while
        // sampling the source, without creating an intermediate full-size copy.
        val targetAspect = outputWidth.toFloat() / outputHeight
        val selectedAspect = selected.width.toFloat() / selected.height
        val effectiveWidth: Int
        val effectiveHeight: Int
        if (selectedAspect > targetAspect) {
            effectiveHeight = selected.height
            effectiveWidth = (effectiveHeight * targetAspect)
                .roundToInt()
                .coerceIn(1, selected.width)
        } else {
            effectiveWidth = selected.width
            effectiveHeight = (effectiveWidth / targetAspect)
                .roundToInt()
                .coerceIn(1, selected.height)
        }
        val effectiveLeft = selected.x + (selected.width - effectiveWidth) / 2
        val effectiveTop = selected.y + (selected.height - effectiveHeight) / 2

        val source = plane.buffer
        val baseOffset = source.position()
        val lastByteIndex = baseOffset +
            (height - 1) * plane.rowStride +
            (width - 1) * pixelStride +
            3
        if (lastByteIndex >= source.limit()) {
            throw IllegalStateException(
                "摄像头 RGBA 缓冲区不足: last=$lastByteIndex limit=${source.limit()}",
            )
        }

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            val requiredPixels = outputWidth * outputHeight
            val pixels = argbPixelBuffer
                ?.takeIf { it.size >= requiredPixels }
                ?: IntArray(requiredPixels).also { argbPixelBuffer = it }
            for (y in 0 until outputHeight) {
                val orientedY = (
                    effectiveTop +
                        (y.toLong() * effectiveHeight / outputHeight).toInt()
                    ).coerceIn(effectiveTop, effectiveTop + effectiveHeight - 1)
                for (x in 0 until outputWidth) {
                    val orientedX = (
                        effectiveLeft +
                            (x.toLong() * effectiveWidth / outputWidth).toInt()
                        ).coerceIn(effectiveLeft, effectiveLeft + effectiveWidth - 1)
                    val rawX: Int
                    val rawY: Int
                    when (normalizedRotation) {
                        90 -> {
                            rawX = orientedY
                            rawY = height - 1 - orientedX
                        }

                        180 -> {
                            rawX = width - 1 - orientedX
                            rawY = height - 1 - orientedY
                        }

                        270 -> {
                            rawX = width - 1 - orientedY
                            rawY = orientedX
                        }

                        else -> {
                            rawX = orientedX
                            rawY = orientedY
                        }
                    }
                    val sourceX = rawX.coerceIn(0, width - 1)
                    val sourceY = rawY.coerceIn(0, height - 1)
                    val pixelOffset = baseOffset +
                        sourceY * plane.rowStride +
                        sourceX * pixelStride
                    val r = source.get(pixelOffset).toInt() and 0xFF
                    val g = source.get(pixelOffset + 1).toInt() and 0xFF
                    val b = source.get(pixelOffset + 2).toInt() and 0xFF
                    pixels[y * outputWidth + x] =
                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(
                pixels,
                0,
                outputWidth,
                0,
                0,
                outputWidth,
                outputHeight,
            )
            return bitmap
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
        }
    }
}
