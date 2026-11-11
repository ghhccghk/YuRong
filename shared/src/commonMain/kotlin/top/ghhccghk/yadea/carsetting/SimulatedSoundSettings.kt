package top.ghhccghk.yadea.carsetting

/** Per-submission evidence of the original D5/R gate; never persisted as configuration. */
class SimulatedSoundAuthorization internal constructor(val vin: String)

internal object SimulatedSoundSettings {
    // Original rb_1/rb_2 submit 0/1 independently of the switch and volume.
    val modes = listOf("音效一", "音效二")
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:33" &&
        item.kind is CarSettingKind.Toggle && item.kind.valueKnown
    fun authorized(item: CarSettingItem, vin: String? = null): Boolean = validItem(item) &&
        item.simulatedSoundAuthorization?.vin?.let { it.isNotBlank() && (vin == null || it == vin) } == true
}
