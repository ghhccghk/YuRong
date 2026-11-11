package top.ghhccghk.yadea.carsetting

/** EnergyRecoveryActivity resolves switch-only, level-only and combined modes from authorized IDs. */
internal object EnergyRecoverySettings {
    val ids = setOf("17", "18")
    fun supports(id: String, kind: CarSettingKind): Boolean =
        (id == "17" && kind is CarSettingKind.Toggle) || (id == "18" && kind is CarSettingKind.Slider)

    fun validTarget(id: String, kind: CarSettingKind): Boolean = supports(id, kind) && when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown && kind.rangeKnown && kind.range == 1f..3f &&
            kind.value.isFinite() && kind.value in 1f..3f && kind.value == kind.value.toInt().toFloat()
        else -> false
    }
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:${item.configId}" && validTarget(item.configId, item.kind)

    fun canEdit(item: CarSettingItem, state: CarSettingsState): Boolean {
        if (item.configId != "18" || state.items.none { it.configId == "17" && it.writable }) return true
        val parent = state.valueState("cloud:17")
        val observed = parent.vehicleValue as? VehicleSettingValue.Known ?: return false
        val switch = observed.kind as? CarSettingKind.Toggle ?: return false
        return observed.vin == state.vin && observed.live && observed.source == SettingValueSource.LIVE_TTP &&
            parent.readbackState == SettingReadbackState.VALID && switch.valueKnown && switch.checked &&
            !parent.busy && parent.submittedValue == null
    }

    fun needsHighConfirmation(target: CarSettingKind, state: CarSettingsState): Boolean {
        if (target !is CarSettingKind.Slider || target.value != 3f) return false
        val observed = state.valueState("cloud:18").vehicleValue as? VehicleSettingValue.Known
        return observed == null || observed.vin != state.vin || !observed.live ||
            (observed.kind as? CarSettingKind.Slider)?.value != 3f
    }
}
