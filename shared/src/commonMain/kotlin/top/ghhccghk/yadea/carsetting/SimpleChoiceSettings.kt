package top.ghhccghk.yadea.carsetting

/** Radio choices from ArmedMode/SpeedUnit/GaugeStyleHandOff/VoltagePlatform. */
internal object SimpleChoiceSettings {
    val options = mapOf(
        "27" to listOf("关机不设防", "关机自动设防"),
        "31" to listOf("km/h", "mph"),
        "50" to listOf("风格 1", "风格 2"),
        "54" to listOf("48V", "60V", "70V"),
    )
    val ids = options.keys

    fun supports(id: String, kind: CarSettingKind): Boolean = id in ids && kind is CarSettingKind.Choice

    fun validTarget(id: String, kind: CarSettingKind): Boolean =
        kind is CarSettingKind.Choice && kind.valueKnown && kind.options == options[id] &&
            kind.selectedIndex in kind.options.indices

    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:${item.configId}" &&
        validTarget(item.configId, item.kind)
}
