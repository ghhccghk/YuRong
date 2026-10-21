package top.ghhccghk.yadea.ui

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val LOG_QUEUE_CAPACITY = 256
private const val DROP_REPORT_INTERVAL_MS = 5_000L

@Volatile private var asyncLogger: AsyncAndroidFileLogger? = null

/** 必须在任何网关请求之前调用，使用应用上下文避免分区存储下的创建失败。 */
@Synchronized
fun initAndroidFileLogger(context: Context) {
    if (asyncLogger == null) {
        asyncLogger = AsyncAndroidFileLogger(context.applicationContext)
    }
}

actual fun logToFile(message: String) {
    val logger = asyncLogger
    if (logger == null) {
        Log.i("YadeaLog", message)
        return
    }
    logger.enqueue(message)
}

/**
 * 热路径只尝试入队，不执行文件 I/O。队列满时丢弃最旧日志并累计计数，
 * 由后台线程低频输出丢弃摘要，保证视频帧永远不会等待日志写入。
 */
private class AsyncAndroidFileLogger(context: Context) {
    private val directory = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "yadea-logs",
    )
    private val queue = ArrayBlockingQueue<String>(LOG_QUEUE_CAPACITY)
    private val dropped = AtomicLong()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yadea-file-logger").apply { isDaemon = true }
    }

    init {
        executor.execute(::drainLoop)
    }

    fun enqueue(message: String) {
        Log.i("YadeaLog", message)
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
                Log.w("YadeaLog", "写日志失败: ${e.message}")
            }
        }
    }

    private fun write(line: String) {
        if (!directory.exists()) directory.mkdirs()
        FileWriter(File(directory, "mirror-log.txt"), true).use { it.write(line + "\n") }
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
}
