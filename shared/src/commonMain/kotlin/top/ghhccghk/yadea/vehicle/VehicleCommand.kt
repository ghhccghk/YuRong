package top.ghhccghk.yadea.vehicle

/**
 * 车辆命令载体。
 *
 * `commandType` 是**自由字符串**，不是枚举 —— 原实现里它有约 180 个取值，
 * 且部分取值需要先经 [mapScooterCommand] 转换。
 */
data class VehicleCommand(
    val commandType: String,
    /** 单整数参数，默认 `-1` 表示不带参。 */
    val parameter: Int = -1,
    val clientId: Int = 1,
    /** 忙碌时是否允许并发，`true` 时绕过命令锁。 */
    val isPlural: Boolean = false,
    val isVoice: Boolean = false,
    /** 命令来源是否为桌面小组件。 */
    val isFromWidget: Boolean = false,
    /** 是否走多参数协议分支。 */
    val isMultiOrder: Boolean = false,
    val orderId: String? = null,
    /** 多参数载荷，默认空。 */
    val parameters: Map<String, Int> = emptyMap(),
    /** 字符串参数。 */
    val parameterText: String = "",
)

/** 已知的数值型命令 ID。 */
internal object CommandIds {
    const val DRIVING_MODE_SWITCH = 0x8F
    const val BRAKE_RECOVER_LEVEL = 0x3D
    const val CRUISE_CONTROL_CLOSE = 0x93
    const val FORTIFY = 3
}

/**
 * `t()` 命令映射。
 *
 * 仅对 X1 / EiBike 车型生效，其余车型直接透传。
 * 原实现是**原地修改同一个对象**，这里保持"输入不变、返回新对象"的等价语义。
 */
internal fun VehicleCommand.mapScooterCommand(): VehicleCommand = when (commandType) {
    "SCOOTER_GEAR_1" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 0)
    "SCOOTER_GEAR_2" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 1)
    "SCOOTER_GEAR_3" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 2)
    "SCOOTER_GEAR_4" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 3)
    "SCOOTER_GEAR_5" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 4)
    "SCOOTER_GEAR_6" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 5)

    // 档位 X 与档位 4 映射到同一个参数。
    "SCOOTER_GEAR_X" -> copy(commandType = "DRIVING_MODE_SWITCH", parameter = 3)

    "SCOOTER_ENERGY_RECOVERY_WEAK" ->
        copy(commandType = "BRAKE_RECOVER_LEVEL", parameter = 1)
    "SCOOTER_ENERGY_RECOVERY_MEDIUM" ->
        copy(commandType = "BRAKE_RECOVER_LEVEL", parameter = 2)
    "SCOOTER_ENERGY_RECOVERY_STRONG" ->
        copy(commandType = "BRAKE_RECOVER_LEVEL", parameter = 3)

    // 以下四项只换命令名，参数保持原值。
    "SCOOTER_CLOSE_CRUISE_CONTROL" -> copy(commandType = "CRUISE_CONTROL_CLOSE")
    "SCOOTER_OPEN_CRUISE_CONTROL" -> copy(commandType = "CRUISE_CONTROL_OPEN")
    "SCOOTER_UNLOCK" -> copy(commandType = "RELEASE_FORTIFY")
    "SCOOTER_LOCK" -> copy(commandType = "FORTIFY")

    else -> this
}
