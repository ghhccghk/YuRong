package top.ghhccghk.yadea.ui
import top.ghhccghk.yadea.mirror.DateTimeParts
import kotlin.js.Date

actual fun getCurrentTime(): DateTimeParts {
    val d = Date()
    return DateTimeParts(d.getFullYear().toInt(), d.getMonth().toInt()+1, d.getDate().toInt(), d.getHours().toInt(), d.getMinutes().toInt(), d.getSeconds().toInt())
}
