package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.vehicle.TtpInfo

internal data class RadarSettingDefinition(
    val configId: String,
    val suffix: String,
    val title: String,
    val summary: String,
    val commandType: String,
    val commandId: Int,
    val rawValue: (TtpInfo) -> Int,
) {
    val itemId: String get() = "cloud:$configId:$suffix"
}

internal object RadarSettings {
    val definitions = listOf(
        RadarSettingDefinition(
            configId = "69",
            suffix = "fcw",
            title = "前向碰撞预警 (FCW)",
            summary = "前方车辆或障碍物接近时提醒",
            commandType = "COMMAND_FRONT_COLLISION",
            commandId = 0x13A,
            rawValue = { it.extended.frontCollisionWarning },
        ),
        RadarSettingDefinition(
            configId = "78",
            suffix = "rcw",
            title = "后向碰撞预警 (RCW)",
            summary = "后方车辆接近时提醒",
            commandType = "COMMAND_RADAR_RCW",
            commandId = 0x12D,
            rawValue = { it.extended.rcwRadarSwitch },
        ),
        RadarSettingDefinition(
            configId = "70",
            suffix = "bsd",
            title = "盲区监测 (BSD)",
            summary = "侧后方盲区有车辆时提醒",
            commandType = "COMMAND_RADAR_BSD",
            commandId = 0x12E,
            rawValue = { it.extended.bsdRadar },
        ),
    )

    val configIds = definitions.mapTo(mutableSetOf()) { it.configId }

    fun definitionForConfigId(configId: String): RadarSettingDefinition? =
        definitions.firstOrNull { it.configId == configId }

    fun definitionForItem(item: CarSettingItem): RadarSettingDefinition? =
        definitionForConfigId(item.configId)?.takeIf { item.id == it.itemId }

    fun isToggleItem(item: CarSettingItem): Boolean =
        definitionForItem(item) != null && item.kind is CarSettingKind.Toggle

    fun validTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        definitionForItem(item) != null && (target as? CarSettingKind.Toggle)?.valueKnown == true

    fun observedValue(item: CarSettingItem, status: TtpInfo): Boolean? =
        definitionForItem(item)?.let { checkedFromRaw(it.rawValue(status)) }

    private fun checkedFromRaw(raw: Int): Boolean? = when (raw) {
        0, 2 -> false
        1 -> true
        else -> null
    }
}
