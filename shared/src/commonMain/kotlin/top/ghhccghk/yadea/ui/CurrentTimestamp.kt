package top.ghhccghk.yadea.ui

/** OTA 日志沿用原 App 的 `yyyy-MM-dd HH:mm:ss` 本地时间格式。 */
internal fun currentTimestampText(): String {
    val now = getCurrentTime()
    fun Int.twoDigits(): String = toString().padStart(2, '0')
    return "${now.year}-${now.month.twoDigits()}-${now.day.twoDigits()} " +
        "${now.hour.twoDigits()}:${now.minute.twoDigits()}:${now.second.twoDigits()}"
}
