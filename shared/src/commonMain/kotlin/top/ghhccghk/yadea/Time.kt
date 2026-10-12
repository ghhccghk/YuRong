package top.ghhccghk.yadea

import kotlin.time.TimeSource
import top.ghhccghk.yadea.mirror.DateTimeParts

private val appTimeStart = TimeSource.Monotonic.markNow()

internal fun currentMonotonicMillis(): Long =
    appTimeStart.elapsedNow().inWholeMilliseconds


internal fun DateTimeParts.toEpochMillis(): Long {
    val adjustedYear = if (month <= 2) year - 1 else year
    val era = adjustedYear / 400
    val yearOfEra = adjustedYear - era * 400
    val shiftedMonth = if (month > 2) month - 3 else month + 9
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    val days = era * 146097 + dayOfEra - 719468
    val seconds = days * 86_400L + hour * 3_600L + minute * 60L + second
    return seconds * 1_000L
}
