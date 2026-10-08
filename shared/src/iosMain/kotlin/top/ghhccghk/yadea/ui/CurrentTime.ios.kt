package top.ghhccghk.yadea.ui
import top.ghhccghk.yadea.mirror.DateTimeParts
actual fun getCurrentTime(): DateTimeParts {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    return DateTimeParts(calendar.component(NSCalendarUnitYear, fromDate=now).toInt(), calendar.component(NSCalendarUnitMonth, fromDate=now).toInt(), calendar.component(NSCalendarUnitDay, fromDate=now).toInt(), calendar.component(NSCalendarUnitHour, fromDate=now).toInt(), calendar.component(NSCalendarUnitMinute, fromDate=now).toInt(), calendar.component(NSCalendarUnitSecond, fromDate=now).toInt())
}
