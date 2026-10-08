package top.ghhccghk.yadea.ui
import top.ghhccghk.yadea.mirror.DateTimeParts
actual fun getCurrentTime(): DateTimeParts {
    val now = java.util.Calendar.getInstance()
    return DateTimeParts(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH)+1, now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), now.get(java.util.Calendar.SECOND))
}
