package top.ghhccghk.yadea.carsetting

/** Parameter domains from ChargingPower/ChargeCap/CapacityChoose/HeatedSeats and s20. */
internal object BatterySettings {
    val ids = setOf("47", "48", "60", "62")
    val heatOptions = listOf("1 档", "2 档", "3 档")

    fun supports(id: String, kind: CarSettingKind): Boolean = when (id) {
        "47" -> kind is CarSettingKind.Toggle || kind is CarSettingKind.Slider
        "48" -> kind is CarSettingKind.Slider
        "60", "62" -> kind is CarSettingKind.Toggle || kind is CarSettingKind.Choice
        else -> false
    }

    fun validTarget(id: String, kind: CarSettingKind): Boolean = when (kind) {
        is CarSettingKind.Toggle -> id in setOf("47", "60", "62") && kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown && kind.rangeKnown && kind.value.isFinite() &&
            kind.value == kind.value.toInt().toFloat() && when (id) {
                "47" -> kind.range == 200f..1000f && kind.value in 200f..1000f && kind.value.toInt() % 50 == 0
                "48" -> kind.range == 75f..100f && kind.value in 75f..100f
                else -> false
            }
        is CarSettingKind.Choice -> kind.valueKnown && kind.selectedIndex in kind.options.indices && when (id) {
            "60" -> kind.options == BatteryCapacityOptions.labels
            "62" -> kind.options == heatOptions
            else -> false
        }
        else -> false
    }

    fun validItem(item: CarSettingItem): Boolean = validTarget(item.configId, item.kind) &&
        item.id == when (item.configId) {
            "47" -> if (item.kind is CarSettingKind.Toggle) "cloud:47:enable" else "cloud:47"
            "48" -> "cloud:48"
            "60" -> if (item.kind is CarSettingKind.Toggle) "cloud:60:enable" else "cloud:60:level"
            "62" -> if (item.kind is CarSettingKind.Toggle) "cloud:62" else "cloud:62:level"
            else -> ""
        }

    fun flag(raw: Int): Boolean? = when (raw) { 0 -> false; 1 -> true; else -> null }

    // VehicleSettingViewModel: raw 0/1 both display 75; 2..6 display 80..100.
    fun chargingLimit(raw: Int): Float? = when (raw) {
        0, 1 -> 75f
        in 2..6 -> (70 + raw * 5).toFloat()
        else -> null
    }
}
