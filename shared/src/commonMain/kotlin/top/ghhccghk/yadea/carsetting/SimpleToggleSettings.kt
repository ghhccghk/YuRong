package top.ghhccghk.yadea.carsetting

/** Independently audited single-switch pages; capability does not imply a known current value. */
internal object SimpleToggleSettings {
    val ids = setOf("19", "26", "36", "53")
    fun flag(raw: Int): Boolean? = when (raw) { 0 -> false; 1 -> true; else -> null }
    fun valid(kind: CarSettingKind): Boolean = kind is CarSettingKind.Toggle && kind.valueKnown
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:${item.configId}" && valid(item.kind)
}
