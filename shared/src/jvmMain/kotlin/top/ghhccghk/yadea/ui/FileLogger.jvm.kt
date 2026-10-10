package top.ghhccghk.yadea.ui
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
actual fun logToFile(message: String) {
    try {
        val file = java.io.File("yadea-mirror-log.txt")
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        FileWriter(file, true).use { it.write("[$ts] $message\n") }
    } catch (_: Exception) {}
}
