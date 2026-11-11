package top.ghhccghk.yadea.carsetting

/** VehicleVolumeActivity sends a three-tick index; BrightnessAdjustment sends integer progress. */
internal object VolumeBrightnessSettings {
    val ids = setOf("20", "86")

    fun supports(id: String, kind: CarSettingKind): Boolean =
        (id == "20" && kind is CarSettingKind.Slider) ||
            (id == "86" && (kind is CarSettingKind.Toggle || kind is CarSettingKind.Slider))

    fun validTarget(id: String, kind: CarSettingKind): Boolean = supports(id, kind) && when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Slider -> {
            val range = if (id == "20") 0f..2f else 0f..100f
            kind.valueKnown && kind.rangeKnown && kind.range == range && kind.value.isFinite() &&
                kind.value in range && kind.value == kind.value.toInt().toFloat()
        }
        else -> false
    }

    fun validItem(item: CarSettingItem): Boolean = validTarget(item.configId, item.kind) &&
        item.id == "cloud:${item.configId}${if (item.configId == "86" && item.kind is CarSettingKind.Slider) ":value" else ""}"

    /** The original brightness slider is enabled only while its observed switch is on. */
    fun canEdit(item: CarSettingItem, state: CarSettingsState): Boolean {
        if (item.configId != "86" || item.kind !is CarSettingKind.Slider) return true
        val parent = state.valueState("cloud:86")
        val observed = parent.vehicleValue as? VehicleSettingValue.Known ?: return false
        val toggle = observed.kind as? CarSettingKind.Toggle ?: return false
        return observed.vin == state.vin && observed.live && observed.source == SettingValueSource.LIVE_TTP &&
            parent.readbackState == SettingReadbackState.VALID && toggle.valueKnown && toggle.checked &&
            !parent.busy && parent.submittedValue == null
    }
}
