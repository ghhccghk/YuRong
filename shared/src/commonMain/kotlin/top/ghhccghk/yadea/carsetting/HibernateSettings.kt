package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException

/** One online check authorizes one confirmed immediate-sleep operation, never a value. */
class HibernateAuthorization internal constructor(val vin: String, val enterConfirmed: Boolean = false)

data class HibernateWriteReceipt(val accepted: Boolean, val resultId: String? = null)

/** Separate receipt for the cloud marker after a successful BLE send; never a vehicle value. */
enum class HibernateMetadataState { ACCEPTED, FAILED, CANCELLED }

internal class HibernateMetadataCancellation(val original: CancellationException) : CancellationException(original.message)

internal object HibernateSettings {
    // HibernateModeActivity.sendCommand -> VM.d9/P4 -> jp6.o, without a PARAM command.
    fun metadataUpdate(vin: String) = CloudCarSettingUpdate("77",
        linkedMapOf("_gateway" to "True", "vin" to vin, "sleepMode" to 1))

    fun validTarget(kind: CarSettingKind): Boolean =
        kind is CarSettingKind.Toggle && kind.valueKnown && kind.checked

    fun validItem(item: CarSettingItem): Boolean =
        item.id == "cloud:77" && item.configId == "77" && validTarget(item.kind)

    fun authorized(item: CarSettingItem, vin: String): Boolean = validItem(item) &&
        item.hibernateAuthorization?.let { it.vin == vin && it.enterConfirmed } == true
}
