package top.ghhccghk.yadea.ui

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun logToFile(message: String) {
    try {
        val dir = File("/storage/emulated/0/Download/yadea-logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "mirror-log.txt")
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        FileWriter(file, true).use { it.write("[$ts] $message\n") }
    } catch (e: Exception) {
        Log.w("FileLogger", "写日志失败: ${e.message}")
    }
}
