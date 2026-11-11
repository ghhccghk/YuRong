package top.ghhccghk.yadea.carsetting

/** Original k2/D2 switch echo only; neither a persistent setting nor an operation receipt. */
data class TimeSyncStatusReport(
    val enabledEcho: Boolean,
    val vin: String,
    val sequence: Long,
    val source: SettingValueSource,
    val live: Boolean,
)

internal object TimeSyncSettings {
    fun validTarget(kind: CarSettingKind): Boolean = kind is CarSettingKind.Toggle && kind.valueKnown
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:51" && validTarget(item.kind)
}
