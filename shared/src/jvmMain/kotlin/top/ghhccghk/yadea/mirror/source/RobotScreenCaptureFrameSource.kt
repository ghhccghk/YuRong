package top.ghhccghk.yadea.mirror.source

import java.awt.AWTException
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.image.BufferedImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FramePreviewDispatcher
import top.ghhccghk.yadea.mirror.FrameRatePolicy
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.FrameSourceDimensions
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * Desktop screen frame source backed by java.awt.Robot.
 *
 * The source captures one physical display on a dedicated worker thread. The
 * target FPS is a capture-cost hint that prevents a busy loop; MirrorEngine
 * remains the sole owner of the protocol send schedule and FrameBus conflates
 * frames to the latest one.
 */
class RobotScreenCaptureFrameSource(
    private val displayIndex: Int = 0,
) : FrameSource {

    private val lifecycleLock = Any()
    private val startGeneration = AtomicLong(0L)
    private val running = AtomicBoolean(false)
    private val previewDispatcher = FramePreviewDispatcher()
    private var worker: ExecutorService? = null
    @Volatile
    private var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    @Volatile
    private var targetFrameIntervalNanos = TimeUnit.MILLISECONDS.toNanos(100L)

    @Volatile private var failureHandler: ((String) -> Unit)? = null

    override val isRunning: Boolean
        get() = running.get()

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

    override fun updateTargetFps(fps: Int) {
        targetFrameIntervalNanos = TimeUnit.MILLISECONDS.toNanos(FrameRatePolicy.intervalMillis(fps))
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
        val generation: Long
        synchronized(lifecycleLock) {
            if (running.get()) return
            generation = startGeneration.incrementAndGet()
            running.set(true)
        }

        try {
            if (GraphicsEnvironment.isHeadless()) {
                throw IllegalStateException("桌面环境处于 headless 模式，无法捕获屏幕")
            }

            val robot = try {
                Robot()
            } catch (error: AWTException) {
                throw IllegalStateException(desktopCaptureErrorMessage(error), error)
            }

            val deviceBounds = screenBounds(displayIndex)
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "desktop-screen-capture").apply { isDaemon = true }
            }
            synchronized(lifecycleLock) {
                if (!isCurrentLocked(generation)) {
                    executor.shutdownNow()
                    return
                }
                worker = executor
            }
            executor.execute {
                try {
                    var nextCaptureAtNanos = System.nanoTime()
                    while (isCurrent(generation)) {
                        val waitNanos = nextCaptureAtNanos - System.nanoTime()
                        if (waitNanos > 0L) TimeUnit.NANOSECONDS.sleep(waitNanos)
                        if (!isCurrent(generation)) break

                        val captured = robot.createScreenCapture(deviceBounds)
                        previewDispatcher.publishDimensions(captured.width, captured.height)
                        if (previewDispatcher.shouldPublishFrame()) {
                            val preview = BufferedImage(
                                captured.width,
                                captured.height,
                                BufferedImage.TYPE_INT_ARGB,
                            )
                            val graphics = preview.createGraphics()
                            try {
                                graphics.drawImage(captured, 0, 0, null)
                            } finally {
                                graphics.dispose()
                            }
                            previewDispatcher.publishFrame(PlatformBitmap(preview))
                        }
                        val cropped = applyCrop(captured)
                        onFrame(PlatformBitmap(cropped))

                        val intervalNanos = targetFrameIntervalNanos.coerceAtLeast(1L)
                        nextCaptureAtNanos += intervalNanos
                        val now = System.nanoTime()
                        if (nextCaptureAtNanos < now) {
                            val missedSlots =
                                (now - nextCaptureAtNanos + intervalNanos - 1L) / intervalNanos
                            nextCaptureAtNanos += missedSlots * intervalNanos
                        }
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    if (isCurrent(generation)) {
                        val message = desktopCaptureErrorMessage(error)
                        System.err.println("Desktop screen capture stopped: $message")
                        failureHandler?.invoke(message)
                    }
                } finally {
                    synchronized(lifecycleLock) {
                        if (startGeneration.get() == generation) {
                            running.set(false)
                            if (worker === executor) worker = null
                        }
                    }
                }
            }
        } catch (error: Exception) {
            stopIfCurrent(generation)
            throw error
        }
    }

    override fun stop() {
        val executor = synchronized(lifecycleLock) {
            if (!running.getAndSet(false)) return
            startGeneration.incrementAndGet()
            worker.also { worker = null }
        }
        executor?.shutdownNow()
    }

    private fun stopIfCurrent(generation: Long) {
        val executor = synchronized(lifecycleLock) {
            if (startGeneration.get() != generation) return
            running.set(false)
            worker.also { worker = null }
        }
        executor?.shutdownNow()
    }

    private fun isCurrent(generation: Long): Boolean =
        running.get() && startGeneration.get() == generation

    private fun isCurrentLocked(generation: Long): Boolean =
        running.get() && startGeneration.get() == generation

    private fun screenBounds(index: Int): java.awt.Rectangle {
        val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val device = devices.getOrNull(index) ?: devices.firstOrNull()
            ?: throw IllegalStateException("桌面环境没有可用显示器")
        return java.awt.Rectangle(device.defaultConfiguration.bounds)
    }

    private fun applyCrop(source: BufferedImage): BufferedImage {
        if (cropConfig == FrameCropConfig.FULL) return source
        val rect = cropConfig.toPixelRect(source.width, source.height)
        if (rect.x == 0 && rect.y == 0 &&
            rect.width == source.width && rect.height == source.height
        ) return source
        return source.getSubimage(rect.x, rect.y, rect.width, rect.height)
    }
}
