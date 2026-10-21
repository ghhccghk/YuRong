package top.ghhccghk.yadea.ui

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val LOG_QUEUE_CAPACITY = 256
private const val DROP_REPORT_INTERVAL_MS = 5_000L

actual fun logToFile(message: String) {
    AsyncJvmFileLogger.enqueue(message)
}

/**
 * JVM 日志同样只做非阻塞入队。队列满时优先保留新日志并累计丢弃数量，
 * 不允许阻塞背压进入视频发送热路径。
 */
private object AsyncJvmFileLogger {
    private val queue = ArrayBlockingQueue<String>(LOG_QUEUE_CAPACITY)
    private val dropped = AtomicLong()
    private val file = File("yadea-mirror-log.txt")
    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    init {
        Thread(::drainLoop, "yadea-file-logger").apply {
            isDaemon = true
            start()
        }
    }

    fun enqueue(message: String) {
        val line = "[${timestamp()}] $message"
        if (!queue.offer(line)) {
            // Preserve the newest diagnostics (especially stop/failure events)
            // instead of allowing an old backlog to win the race with them.
            dropped.incrementAndGet()
            queue.poll()
            if (!queue.offer(line)) dropped.incrementAndGet()
        }
    }

    private fun drainLoop() {
        var lastDropReportAt = 0L
        while (true) {
            try {
                val line = queue.poll(1, TimeUnit.SECONDS)
                if (line != null) write(line)
                val now = System.currentTimeMillis()
                if (now - lastDropReportAt >= DROP_REPORT_INTERVAL_MS) {
                    val count = dropped.getAndSet(0)
                    if (count > 0) write("[${timestamp()}] 日志队列已满，丢弃 $count 条")
                    lastDropReportAt = now
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (e: Exception) {
                System.err.println("写日志失败: ${e.message}")
            }
        }
    }

    private fun write(line: String) {
        FileWriter(file, true).use { it.write(line + "\n") }
    }

    private fun timestamp(): String = timestampFormat.get().format(Date())
}
