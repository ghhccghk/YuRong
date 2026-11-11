package top.ghhccghk.yadea.carsetting

/** Independently audited single-switch pages; capability does not imply a known current value. */
internal object SimpleToggleSettings {
    val ids = setOf("19", "26", "36", "53", "37", "38", "46", "49", "63", "68", "72", "79",
        "80", "84", "90", "94", "32", "43", "44", "45")
    fun flag(raw: Int): Boolean? = when (raw) { 0 -> false; 1 -> true; else -> null }
    fun invertedFlag(raw: Int): Boolean? = flag(raw)?.not()
    fun tcs(raw: Int): Boolean? = raw.takeIf { it in 0..7 }?.let { it == 0 }
    fun valid(kind: CarSettingKind): Boolean = kind is CarSettingKind.Toggle && kind.valueKnown
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:${item.configId}" && valid(item.kind)
}
