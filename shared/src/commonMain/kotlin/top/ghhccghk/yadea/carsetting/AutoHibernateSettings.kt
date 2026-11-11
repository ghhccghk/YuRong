package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.network.JsonSupport

data class AutoHibernateSession(val vin: String, val loadGeneration: Long) {
    fun matches(state: CarSettingsState): Boolean = vin == state.vin && loadGeneration == state.loadGeneration
}

internal object AutoHibernateSettings {
    const val ID = "cloud:77:auto"
    const val GET_PATH = "vehicle-base/bike-sleep-appointment/get"
    const val UPDATE_PATH = "vehicle-base/bike-sleep-appointment/update"

    fun parse(json: String) = CarSettingKind.AutoHibernate(
        enabled = when (JsonSupport.topLevelScalarText(json, "autoSleepSwitch")) { "0" -> false; "1" -> true; else -> null },
        dayCount = JsonSupport.topLevelScalarText(json, "dayCount")?.toIntOrNull()?.takeIf { it in 1..30 },
    )

    fun validTarget(kind: CarSettingKind.AutoHibernate): Boolean = kind.edit != null && when (kind.edit) {
        CarSettingKind.AutoHibernateEdit.DISABLE -> kind.enabled == false && kind.dayCount == 1
        else -> kind.enabled == true && kind.dayCount?.let { it in 1..30 } == true
    }

    fun validItem(item: CarSettingItem): Boolean = item.id == ID && item.configId == "77" &&
        (item.kind as? CarSettingKind.AutoHibernate)?.let(::validTarget) == true

    fun available(state: CarSettingsState): Boolean {
        // Immediate mode blocks auto scheduling on the original page. Drafts cannot lift that mask.
        val value = state.valueState("cloud:77")
        if ((value.submittedValue as? CarSettingKind.Toggle)?.checked == true) return false
        val observed = (value.vehicleValue as? VehicleSettingValue.Known)?.takeIf { it.live && it.vin == state.vin }?.kind
        val immediate = (observed ?: value.configuredValue) as? CarSettingKind.Toggle
        return immediate?.let { it.valueKnown && !it.checked } == true
    }

    fun canSubmit(state: CarSettingsState, target: CarSettingKind.AutoHibernate): Boolean {
        if (!validTarget(target) || !available(state) || state.valueState(ID).submittedValue != null) return false
        val current = state.valueState(ID).configuredValue as? CarSettingKind.AutoHibernate ?: return false
        return when (target.edit) {
            CarSettingKind.AutoHibernateEdit.ENABLE -> current.enabled == false
            CarSettingKind.AutoHibernateEdit.DISABLE, CarSettingKind.AutoHibernateEdit.DAYS -> current.enabled == true
            null -> false
        }
    }
}

suspend fun CarSettingsController.updateAutoHibernate(session: AutoHibernateSession, target: CarSettingKind.AutoHibernate): Boolean {
    if (!session.matches(state.value)) return false
    return update(AutoHibernateSettings.ID) { target }
}
