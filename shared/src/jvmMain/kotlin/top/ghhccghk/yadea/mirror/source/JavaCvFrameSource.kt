package top.ghhccghk.yadea.mirror.source

import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.bytedeco.javacv.OpenCVFrameGrabber
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * Camera and media-file frame source backed by JavaCV.
 *
 * Camera capture uses OpenCV while files and network streams use FFmpeg. Media
 * sources follow their own presentation timestamps through grabAtFrameRate();
 * camera capture uses the grabber's native blocking cadence. Neither source adds
 * a second fixed FPS limit: MirrorEngine performs the negotiated send schedule,
 * and FrameBus retains only the latest frame.
 */
class JavaCvFrameSource private constructor(
    private val grabber: FrameGrabber,
    private val paceByPresentationTimestamp: Boolean,
) : FrameSource {

    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val previewDispatcher = FramePreviewDispatcher()
    @Volatile private var worker: Thread? = null
    @Volatile private var cropConfig: FrameCropConfig = FrameCropConfig.FULL
    @Volatile private var failureHandler: ((String) -> Unit)? = null

    override val isRunning: Boolean
        get() = running.get()

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

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
        check(!closed.get()) { "视频帧源已释放，不能重新启动" }
        if (!running.compareAndSet(false, true)) return

        try {
            grabber.start()
            grabber.resetStartTime()
        } catch (error: Exception) {
            running.set(false)
            closeGrabber()
            throw IllegalStateException(mediaErrorMessage(error), error)
        }

        val thread = Thread({
            val converter = Java2DFrameConverter()
            try {
                while (running.get()) {
                    val frame = try {
                        if (paceByPresentationTimestamp) {
                            grabber.grabAtFrameRate()
                        } else {
                            grabber.grabFrame()
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }

                    val converted = frame?.let { convertFrame(converter, it) } ?: continue
                    val copied = Java2DFrameConverter.cloneBufferedImage(converted)
                    previewDispatcher.publishDimensions(copied.width, copied.height)
                    if (previewDispatcher.shouldPublishFrame()) {
                        val preview = Java2DFrameConverter.cloneBufferedImage(copied)
                        previewDispatcher.publishFrame(PlatformBitmap(preview))
                    }
                    val cropped = applyCrop(copied)
                    onFrame(PlatformBitmap(cropped))
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Throwable) {
                if (running.get()) {
                    val message = mediaErrorMessage(error)
                    System.err.println("Desktop media capture stopped: $message")
                    failureHandler?.invoke(message)
                }
            } finally {
                running.set(false)
                closeGrabber()
            }
        }, "desktop-media-capture").apply {
            isDaemon = true
        }
        worker = thread
        thread.start()
    }

    override fun stop() {
        if (!running.getAndSet(false)) return
        worker?.interrupt()
        worker = null
        closeGrabber()
    }

    private fun closeGrabber() {
        if (!closed.compareAndSet(false, true)) return
        try {
            grabber.stop()
        } catch (_: Exception) {
        }
        try {
            grabber.release()
        } catch (_: Exception) {
        }
    }

    private fun convertFrame(converter: Java2DFrameConverter, frame: org.bytedeco.javacv.Frame): BufferedImage {
        if (frame.imageDepth == 8 && frame.imageChannels !in setOf(1, 3, 4)) {
            return frameToBufferedImage(frame)
        }
        return converter.getBufferedImage(frame)
    }

    private fun frameToBufferedImage(frame: org.bytedeco.javacv.Frame): BufferedImage {
        val width = frame.imageWidth
        val height = frame.imageHeight
        val channels = frame.imageChannels.coerceAtLeast(1)
        val stride = if (frame.imageStride > 0) frame.imageStride else width * channels
        val source = frame.image?.firstOrNull() as? java.nio.ByteBuffer
            ?: throw IllegalStateException("JavaCV Frame 没有可读取的字节图像缓冲区")

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val row = ByteArray(minOf(stride, source.remaining().coerceAtLeast(width * channels)))
        val copy = source.duplicate()
        for (y in 0 until height) {
            copy.position(y * stride)
            val rowSize = minOf(row.size, copy.remaining())
            if (rowSize <= 0) break
            copy.get(row, 0, rowSize)
            for (x in 0 until width) {
                val offset = x * channels
                if (offset + 2 >= rowSize) continue
                val gray = row[offset].toInt() and 0xFF
                val r: Int
                val g: Int
                val b: Int
                if (channels >= 3) {
                    r = row[offset].toInt() and 0xFF
                    g = row[offset + 1].toInt() and 0xFF
                    b = row[offset + 2].toInt() and 0xFF
                } else {
                    r = gray
                    g = gray
                    b = gray
                }
                val a = if (channels >= 4 && offset + 3 < rowSize) {
                    row[offset + 3].toInt() and 0xFF
                } else {
                    0xFF
                }
                result.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    }
    private fun applyCrop(source: BufferedImage): BufferedImage {
        if (cropConfig == FrameCropConfig.FULL) return source
        val rect = cropConfig.toPixelRect(source.width, source.height)
        if (rect.x == 0 && rect.y == 0 &&
            rect.width == source.width && rect.height == source.height
        ) return source
        return source.getSubimage(rect.x, rect.y, rect.width, rect.height)
    }

    companion object {
        fun camera(cameraIndex: Int): JavaCvFrameSource =
            JavaCvFrameSource(OpenCVFrameGrabber(cameraIndex), paceByPresentationTimestamp = false)

        fun media(uri: String): JavaCvFrameSource {
            require(uri.isNotBlank()) { "请填写视频文件路径或网络流地址" }
            return JavaCvFrameSource(FFmpegFrameGrabber(uri), paceByPresentationTimestamp = true)
        }

        private fun mediaErrorMessage(error: Throwable): String {
            val detail = error.message ?: error::class.simpleName ?: "未知错误"
            return "无法读取摄像头或视频源: $detail"
        }
    }
}
