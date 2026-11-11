package top.ghhccghk.yadea.carsetting

/** A sheet callback belongs to the VIN and load generation in which it was created. */
data class LowPowerReminderSession(val vin: String, val loadGeneration: Long) {
    fun matches(state: CarSettingsState): Boolean = vin == state.vin && loadGeneration == state.loadGeneration
}

suspend fun CarSettingsController.updateLowPowerReminder(
    id: String,
    session: LowPowerReminderSession,
    transform: (CarSettingKind) -> CarSettingKind,
): Boolean {
    if (!session.matches(state.value) || id !in setOf("cloud:9", "cloud:82")) return false
    return update(id, transform)
}

suspend fun CarSettingsController.submitLowPowerDraft(id: String, session: LowPowerReminderSession): Boolean {
    val draft = state.value.valueState(id).draftValue ?: return false
    return updateLowPowerReminder(id, session) { draft }
}

/** LowPowerRemindChangePop.e(): overlapping selections are moved apart by ten minutes. */
internal fun adjustedLowPowerTimes(start: String?, end: String?): Pair<String?, String?> {
    if (!selectableLowPowerTime(start) || !selectableLowPowerTime(end) || start!! < end!!) return start to end
    val startMinutes = start.substring(0, 2).toInt() * 60 + start.substring(3).toInt()
    fun format(minutes: Int): String =
        (minutes / 60).toString().padStart(2, '0') + ":" + (minutes % 60).toString().padStart(2, '0')
    return if (startMinutes >= 23 * 60 + 50) "23:40" to "23:50"
    else start to format(startMinutes + 10)
}

fun CarSettingsController.editLowPowerTimes(
    id: String,
    session: LowPowerReminderSession,
    startTime: String? = null,
    endTime: String? = null,
): Boolean {
    if (id != "cloud:82" || !session.matches(state.value)) return false
    if ((startTime != null && !selectableLowPowerTime(startTime)) ||
        (endTime != null && !selectableLowPowerTime(endTime))
    ) return false
    return editDraft(id) { current ->
        if (current !is CarSettingKind.LowPowerPhone) return@editDraft current
        val (start, end) = adjustedLowPowerTimes(startTime ?: current.startTime, endTime ?: current.endTime)
        current.copy(startTime = start, endTime = end, edit = CarSettingKind.LowPowerPhoneEdit.TIMES)
    }
}
