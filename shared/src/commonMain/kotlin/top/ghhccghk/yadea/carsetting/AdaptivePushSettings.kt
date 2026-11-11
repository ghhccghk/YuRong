package top.ghhccghk.yadea.carsetting

/** Both variants send each control independently; no unknown sibling is needed or supplied. */
internal object AdaptivePushSettings {
    val ids = setOf("64", "89")
    fun supports(kind: CarSettingKind): Boolean = kind is CarSettingKind.Toggle || kind is CarSettingKind.Slider
    fun valid(kind: CarSettingKind): Boolean = when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown && kind.rangeKnown && kind.range == 0f..2f &&
            kind.steps == 1 && kind.value.isFinite() && kind.value in 0f..2f && kind.value % 1f == 0f
        else -> false
    }
    fun validItem(item: CarSettingItem): Boolean = item.configId in ids && valid(item.kind) && when (item.kind) {
        is CarSettingKind.Toggle -> item.id == "cloud:${item.configId}" || item.id == "cloud:${item.configId}:adaptive"
        is CarSettingKind.Slider -> item.id == "cloud:${item.configId}:level"
        else -> false
    }
}
