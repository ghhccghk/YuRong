package top.ghhccghk.yadea.carsetting

/** One online check authorizes one confirmed immediate-sleep operation, never a value. */
class HibernateAuthorization internal constructor(val vin: String, val enterConfirmed: Boolean = false)

data class HibernateWriteReceipt(val accepted: Boolean, val resultId: String? = null)

internal object HibernateSettings {
    fun validTarget(kind: CarSettingKind): Boolean =
        kind is CarSettingKind.Toggle && kind.valueKnown && kind.checked

    fun validItem(item: CarSettingItem): Boolean =
        item.id == "cloud:77" && item.configId == "77" && validTarget(item.kind)

    fun authorized(item: CarSettingItem, vin: String): Boolean = validItem(item) &&
        item.hibernateAuthorization?.let { it.vin == vin && it.enterConfirmed } == true
}
