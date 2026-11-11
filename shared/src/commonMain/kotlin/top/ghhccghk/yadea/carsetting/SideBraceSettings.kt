package top.ghhccghk.yadea.carsetting

/** Proof that both original W0 model branches choose the independent close command. */
class SideBraceCloseAuthorization internal constructor(val vin: String)

internal object SideBraceSettings {
    fun validItem(item: CarSettingItem): Boolean = item.configId == "2" && item.id == "cloud:2" &&
        item.kind is CarSettingKind.Toggle && item.kind.valueKnown

    fun authorized(item: CarSettingItem, vin: String? = null): Boolean = validItem(item) &&
        ((item.kind as CarSettingKind.Toggle).checked || item.sideBraceCloseAuthorization?.let {
            it.vin.isNotBlank() && (vin == null || it.vin == vin)
        } == true)

    fun canClose(state: CarSettingsState, liveAutoLock: Int?): Boolean {
        if (!state.loadedFromCloud || state.vin.isBlank()) return false
        // TempleInductionActivity.w2 is the actual confId39 descriptor, not a
        // locally synthesized missing-feature row. Absence makes its AND false.
        if (state.items.none { it.configId == "39" }) return true
        val sibling = state.valueState("cloud:39")
        val shared = state.valueState("cloud:7")
        if (listOf(sibling, shared).any { it.busy || it.submittedValue != null || it.draftValue != null }) return false
        // With lock flag zero F1 is false in either W0 branch. Otherwise model
        // 1021/29 could require AC, whose frame is outside this repair's scope.
        return liveAutoLock == 0
    }
}
