package top.ghhccghk.yadea.carsetting

/** ScheduledShutDown(2) and Back/DelayLight send independent switch/time commands. */
internal object TimerSettings {
    val ids = setOf("15", "59", "21", "81")
    val shutdownOptions = (0..20).map { if (it == 0) "30 秒" else "$it 分钟" }
    val homeOptions = listOf("15 秒", "30 秒", "45 秒", "60 秒")
    val delayOptions = listOf("5 秒", "10 秒", "15 秒", "30 秒")

    fun supports(id: String, kind: CarSettingKind): Boolean = id in ids && when (kind) {
        is CarSettingKind.Toggle -> true
        is CarSettingKind.Slider -> id == "15"
        is CarSettingKind.Choice -> id != "15"
        else -> false
    }

    fun validTarget(id: String, kind: CarSettingKind): Boolean = supports(id, kind) && when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown && kind.rangeKnown && kind.range == 2f..20f &&
            kind.value.isFinite() && kind.value == kind.value.toInt().toFloat() && kind.value in 2f..20f
        is CarSettingKind.Choice -> kind.valueKnown && kind.selectedIndex in kind.options.indices &&
            kind.options == when (id) { "59" -> shutdownOptions; "21" -> homeOptions; else -> delayOptions }
        else -> false
    }

    fun validItem(item: CarSettingItem): Boolean = validTarget(item.configId, item.kind) &&
        item.id == "cloud:${item.configId}${if (item.kind is CarSettingKind.Toggle) "" else ":time"}"

    fun writeKey(item: CarSettingItem): String? = if (item.configId !in ids) null else
        "${if (item.configId in setOf("15", "59")) "shutdown" else "home-light"}:" +
            if (item.kind is CarSettingKind.Toggle) "switch" else "time"

    fun flag(raw: Int): Boolean? = when (raw) { 0 -> false; 1 -> true; else -> null }
}
