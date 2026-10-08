package top.ghhccghk.yadea.mirror.source

import java.awt.AWTException
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.image.BufferedImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.FrameSource
import top.ghhccghk.yadea.mirror.PlatformBitmap

/**
 * Desktop screen frame source backed by java.awt.Robot.
 *
 * The source captures one physical display on a dedicated worker thread and
 * emits a fresh BufferedImage for every frame. It is intentionally limited to
 * the JVM target so other platforms keep their native capture implementations.
 */
class RobotScreenCaptureFrameSource(
    private val displayIndex: Int = 0,
    private val frameIntervalMs: Long = 100,
) : FrameSource {

    private val running = AtomicBoolean(false)
    private var worker: ExecutorService? = null
    private var cropConfig: FrameCropConfig = FrameCropConfig.FULL

    override val isRunning: Boolean
        get() = running.get()

    override fun updateCropConfig(config: FrameCropConfig) {
        if (config.isValid) cropConfig = config
    }

    override suspend fun start(onFrame: (PlatformBitmap) -> Unit) {
        if (!running.compareAndSet(false, true)) return
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
            worker = executor
            executor.execute {
                try {
                    while (running.get()) {
                        val captured = robot.createScreenCapture(deviceBounds)
                        val cropped = applyCrop(captured)
                        onFrame(PlatformBitmap(cropped))
                        Thread.sleep(frameIntervalMs.coerceAtLeast(1L))
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    System.err.println("Desktop screen capture stopped: ${desktopCaptureErrorMessage(error)}")
                } finally {
                    running.set(false)
                }
            }
        } catch (error: Exception) {
            running.set(false)
            worker?.shutdownNow()
            worker = null
            throw error
        }
    }
    override fun stop() {
        if (!running.getAndSet(false)) return
        worker?.shutdownNow()
        worker = null
    }

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
