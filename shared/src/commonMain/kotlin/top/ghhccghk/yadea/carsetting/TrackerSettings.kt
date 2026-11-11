package top.ghhccghk.yadea.carsetting

/** A fresh online check authorizes one ID76 submission; it is never a setting value. */
class LocationSwitchAuthorization internal constructor(val vin: String, val closeConfirmed: Boolean = false)

internal object TrackerSettings {
    val ids = setOf("75", "76")
    fun supports(id: String, kind: CarSettingKind): Boolean = id in ids && kind is CarSettingKind.Toggle
    fun validTarget(id: String, kind: CarSettingKind): Boolean =
        supports(id, kind) && (kind as CarSettingKind.Toggle).valueKnown &&
            // P4/s20 ignore the Boolean and write 1. A cloud value of 0 is not established.
            (id != "75" || kind.checked)
    fun validItem(item: CarSettingItem): Boolean =
        item.id == "cloud:${item.configId}" && validTarget(item.configId, item.kind)
    fun authorized(item: CarSettingItem, vin: String): Boolean =
        item.configId != "76" || (item.locationSwitchAuthorization?.vin == vin &&
            ((item.kind as? CarSettingKind.Toggle)?.checked == true || item.locationSwitchAuthorization.closeConfirmed))
}

/** The result endpoint takes the identifier returned by this write, never confId 76. */
data class LocationSwitchWriteReceipt(val accepted: Boolean, val resultId: String? = null)
