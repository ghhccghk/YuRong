package top.ghhccghk.yadea.carsetting

/** DumpNotice/EggSound use independent cloud switches, with no established TTP readback. */
internal object CloudSwitchSettings {
    val ids = setOf("16", "40")
    fun supports(id: String, kind: CarSettingKind): Boolean = id in ids && kind is CarSettingKind.Toggle
    fun validTarget(kind: CarSettingKind): Boolean = kind is CarSettingKind.Toggle && kind.valueKnown
    fun validItem(item: CarSettingItem): Boolean =
        item.id == "cloud:${item.configId}" && supports(item.configId, item.kind) && validTarget(item.kind)
}
