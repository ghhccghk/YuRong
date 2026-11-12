package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.network.JsonSupport

/** Independent schedule records returned by vehicle-base/seat-heating-job/list. */
data class HeatedSeatTask(
    val id: String,
    val vin: String,
    val workTime: String,
    val repeat: Int?,
    val weekdayPositions: Set<Int>?,
    val status: Int?,
)

data class HeatedSeatTaskDraft(
    val workTime: String,
    val repeat: Int,
    /** UI order: Sunday=0, Monday=1, ... Saturday=6. */
    val weekdayPositions: Set<Int>,
)

internal object HeatedSeatTaskRules {
    fun enabledFromStatus(status: Int?): Boolean? = when (status) {
        0 -> true
        1 -> false
        else -> null
    }

    fun statusForEnabled(enabled: Boolean): Int = if (enabled) 0 else 1

    fun validTime(value: String): Boolean = Regex("[0-9]{2}:[0-9]{2}").matches(value) && run {
        val hour = value.substring(0, 2).toIntOrNull()
        val minute = value.substring(3, 5).toIntOrNull()
        hour != null && minute != null && hour in 0..23 && minute in 0..59
    }

    fun valid(draft: HeatedSeatTaskDraft): Boolean = validTime(draft.workTime) && when (draft.repeat) {
        1 -> draft.weekdayPositions.isEmpty()
        0 -> draft.weekdayPositions.isNotEmpty() && draft.weekdayPositions.all { it in 0..6 }
        else -> false
    }

    fun apiWeekdays(positions: Set<Int>): List<Int>? = positions.takeIf {
        it.isNotEmpty() && it.all { day -> day in 0..6 }
    }?.distinct()?.sorted()

    fun parse(json: String): HeatedSeatTask? {
        val id = JsonSupport.string(json, "id")?.takeIf(String::isNotBlank) ?: return null
        val vin = JsonSupport.string(json, "vin")?.takeIf(String::isNotBlank) ?: return null
        val time = JsonSupport.string(json, "workTime")?.takeIf(::validTime) ?: return null
        val repeat = (JsonSupport.string(json, "izRepeat") ?: JsonSupport.numberText(json, "izRepeat"))
            ?.toIntOrNull()?.takeIf { it == 0 || it == 1 } ?: return null
        val rawWeekdays = JsonSupport.string(json, "weekNumbers")
        val weekdays = if (repeat == 1) emptySet() else rawWeekdays
            ?.split(',')?.mapNotNull(String::toIntOrNull)
            ?.takeIf { values -> values.isNotEmpty() && values.all { it in 0..6 } }
            ?.toSet() ?: return null
        val status = (JsonSupport.string(json, "status") ?: JsonSupport.numberText(json, "status"))
            ?.toIntOrNull()
        return HeatedSeatTask(id, vin, time, repeat, weekdays, status)
    }
}
