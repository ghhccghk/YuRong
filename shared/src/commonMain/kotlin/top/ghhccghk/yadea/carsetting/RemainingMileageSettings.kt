package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.network.JsonSupport

/** A per-request eligibility result, never a configured or observed switch value. */
class RemainingMileageEligibility internal constructor(val vin: String, val prompt: String) {
    val allowed: Boolean get() = prompt.isBlank()
}

internal object RemainingMileageSettings {
    fun validItem(item: CarSettingItem): Boolean = item.id == "cloud:42" && item.configId == "42" &&
        item.kind is CarSettingKind.Toggle && item.kind.valueKnown

    fun authorized(item: CarSettingItem, vin: String? = null): Boolean = validItem(item) &&
        (!(item.kind as CarSettingKind.Toggle).checked || item.remainingMileageEligibility?.let {
            it.vin.isNotBlank() && it.allowed && (vin == null || it.vin == vin)
        } == true)

    // VehicleSettingViewModel.x(RemainingMileageData):31877–32030. Missing fields
    // remain unknown instead of borrowing the original DTO's primitive defaults.
    fun parse(vin: String, data: String): RemainingMileageEligibility? {
        if (vin.isBlank()) return null
        if (!data.trim().endsWith('}')) return null
        fun scalar(key: String) = JsonSupport.topLevelScalarText(data, key)
        val formula = scalar("formula")?.toIntOrNull() ?: return null
        fun countPrompt(count: Int) = "当前还剩余${count}次有效骑行，预估剩余里程是根据10次有效骑行数据后进行AI算法拟合计算，有效期骑行10次后，次日凌晨6点后即可进行开启，车满仪表会同时展示剩余续航里程数据"
        val waiting = "预估剩余里程是根据10次有效骑行数据后进行AI算法拟合计算,有效期骑行10次后,两日后凌晨6点后即可开启,车端仪表会同时展示剩余续航里程数据"
        fun serverPrompt(): String? = scalar("prompt")?.takeIf { JsonSupport.string(data, "prompt") == it }
        val prompt = when (formula) {
            1 -> (scalar("count")?.toIntOrNull() ?: return null).let { if (it > 0) countPrompt(it) else waiting }
            2 -> {
                val count = scalar("count")?.toIntOrNull() ?: return null
                if (count > 0) countPrompt(count) else {
                    val estimate = scalar("estimateMileage")?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
                    if (estimate <= 0) waiting else serverPrompt() ?: return null
                }
            }
            else -> serverPrompt() ?: return null
        }
        return RemainingMileageEligibility(vin.trim(), prompt)
    }
}
