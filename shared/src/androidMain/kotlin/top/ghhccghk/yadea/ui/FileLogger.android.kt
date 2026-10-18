package top.ghhccghk.yadea.ui

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private var logContext: Context? = null

/** 必须在任何网关请求之前调用，使用应用上下文避免分区存储下的创建失败。 */
fun initAndroidFileLogger(context: Context) {
    logContext = context.applicationContext
}

actual fun logToFile(message: String) {
    Log.i("YadeaLog", message)
    try {
        val context = logContext ?: return
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "yadea-logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "mirror-log.txt")
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        FileWriter(file, true).use { it.write("[$ts] $message\n") }
    } catch (e: Exception) {
        Log.w("YadeaLog", "写日志失败: ${e.message}")
    }
}
