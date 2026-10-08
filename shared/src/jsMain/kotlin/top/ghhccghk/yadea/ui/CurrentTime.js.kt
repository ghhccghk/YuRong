package top.ghhccghk.yadea.ui
import top.ghhccghk.yadea.mirror.DateTimeParts
actual fun getCurrentTime(): DateTimeParts {
    val d = kotlinx.js.Date()
    return DateTimeParts(d.getFullYear().toInt(), d.getMonth().toInt()+1, d.getDate().toInt(), d.getHours().toInt(), d.getMinutes().toInt(), d.getSeconds().toInt())
}
