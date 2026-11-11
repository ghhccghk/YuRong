package top.ghhccghk.yadea.carsetting

/** Mappings verified against the four original setting pages, not cloud field names. */
internal object SecondPhaseSettings {
    val ids = setOf("5", "7", "11", "39")
    val soundOptions = listOf("主题 1", "主题 2")
    val timeOptions = listOf("3 秒", "5 秒", "10 秒", "15 秒")
    val distanceOptions = listOf("近", "中", "远")

    fun flag(raw: Int): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    /**
     * The original ViewModel checks VehicleDataManager.W0 (1021/29) before
     * combining the shared lock flag with sideBraceStatus. That model gate is not
     * available here: report only outcomes common to both branches, never guess it.
     */
    fun autoLockEnabled(raw: Int, sideBrace: Int): Boolean? = when {
        raw == 0 -> false
        raw == 1 && sideBrace == 1 -> true
        else -> null
    }

    fun validTarget(configId: String, kind: CarSettingKind): Boolean = when (configId) {
        "5" -> kind is CarSettingKind.Choice && kind.valueKnown &&
            kind.options == soundOptions && kind.selectedIndex in 0..1
        "7", "39" -> when (kind) {
            is CarSettingKind.Toggle -> kind.valueKnown
            is CarSettingKind.Choice -> kind.valueKnown &&
                kind.options == timeOptions && kind.selectedIndex in 0..3
            else -> false
        }
        "11" -> when (kind) {
            is CarSettingKind.Toggle -> kind.valueKnown
            is CarSettingKind.Choice -> kind.valueKnown &&
                kind.options == distanceOptions && kind.selectedIndex in 0..2
            else -> false
        }
        else -> true
    }

    /** The original time/distance listeners submit only while their switch is on. */
    fun canEditChoice(item: CarSettingItem, state: CarSettingsState): Boolean {
        if (item.configId !in setOf("7", "11", "39") || item.kind !is CarSettingKind.Choice) return true
        val parentId = "cloud:${item.configId}"
        val parent = state.valueState(parentId)
        val enabled = parent.displayKind() as? CarSettingKind.Toggle
        return enabled?.valueKnown == true && enabled.checked && !parent.busy
    }

    /** 7 and 39 expose the same BLE flag and time command, not separate settings. */
    fun writeKey(item: CarSettingItem): String? = when (item.configId) {
        "7", "39" -> if (item.kind is CarSettingKind.Choice) "auto-lock-time" else "auto-lock-switch"
        else -> null
    }
}
