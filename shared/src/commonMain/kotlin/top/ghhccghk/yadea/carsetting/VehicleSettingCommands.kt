package top.ghhccghk.yadea.carsetting

/**
 * 设置项到车辆命令的映射。
 *
 * 命令 ID 来自 smali：
 * - `ch.d`：0x53/0x54 离车自动锁、0x62/0x63 提示音、0x92/0x93 定速巡航、0x3D 能量回收
 * - `qg.e`：0x9D TCS、0x134 仪表亮度值、0x135 自动亮度
 */
data class VehicleSettingCommand(
    val commandType: String,
    val commandId: Int,
    val parameter: Int = -1,
    /** 多参数命令的附加字段，顺序与 [commandType] 的原实现一致。 */
    val parameters: List<Int> = emptyList(),
)

/** 平台侧负责把 [VehicleSettingCommand] 编码成整车 BLE 命令并写入主特征。 */
interface VehicleSettingCommandSink {
    suspend fun send(command: VehicleSettingCommand): Boolean
}

class MappedCarSettingWriter(
    private val sink: VehicleSettingCommandSink,
) : CarSettingWriter {
    override suspend fun write(item: CarSettingItem): Boolean {
        val command = VehicleSettingCommands.forItem(item) ?: return false
        return sink.send(command)
    }
}

/**
 * 云端设置写入优先，未确认云端字段的设置再回退到 BLE 映射。
 *
 * 云端清单决定项目是否存在与是否授权；写入通道仍按各 configId 的原实现分流。
 */
class CloudCarSettingWriter(
    private val gateway: CarSettingsGateway,
    private val vinProvider: () -> String,
    private val bleFallback: CarSettingWriter? = null,
) : CarSettingWriter {
    override suspend fun write(item: CarSettingItem): Boolean {
        val vin = vinProvider().trim()
        if (vin.isBlank()) return false
        val update = CloudCarSettingUpdateBuilder.build(
            configId = item.configId,
            kind = item.kind,
            vin = vin,
        )
        if (update != null) {
            return gateway.update(vin, update)
        }
        return bleFallback?.write(item) ?: false
    }
}

object VehicleSettingCommands {

    fun supports(configId: String, kind: CarSettingKind): Boolean =
        forConfigId(configId, kind)?.let { VehicleSettingFrameEncoder.encode(it) != null } == true

    /**
     * 云端 `BikeConfig.confId` 对应的已确认命令。
     *
     * 只有能生成完整 `5944...4B4A` 帧的项才标为可写；其余云端项仍会展示，
     * 但保持只读，避免向车辆发送猜测出来的命令。
     */
    fun forConfigId(configId: String, kind: CarSettingKind): VehicleSettingCommand? =
        when (configId) {
            // TempleInductionActivity：边撑感应。
            "2" -> toggle(
                kind = kind,
                onType = "SLIDE_BRACE_ON",
                offType = "SLIDE_BRACE_OFF",
                onId = 0x0F,
                offId = 0x10,
            )
            // SeatCushionSensorActivity：携出助力。
            "3" -> toggle(
                kind = kind,
                onType = "CARRY_OUT_OPEN",
                offType = "CARRY_OUT_CLOSE",
                onId = 0x11,
                offId = 0x12,
            )
            "32" -> toggle(
                kind = kind,
                onType = "TILT_WARNING_OPEN",
                offType = "TILT_WARNING_CLOSE",
                onId = 0x12D,
                offId = 0x12D,
                onParameter = 1,
                offParameter = 0,
            )
            "63" -> toggle(
                kind = kind,
                onType = "COMMAND_CONST_T_POWER_SETTING",
                offType = "COMMAND_CONST_T_POWER_SETTING",
                onId = 0x6A,
                offId = 0x69,
                onParameter = 1,
                offParameter = 0,
            )
            "79" -> toggle(
                kind = kind,
                onType = "COMMAND_SEAT_POWER_UP",
                offType = "COMMAND_SEAT_POWER_UP",
                onId = 0xFB1,
                offId = 0xFB1,
                onParameter = 1,
                offParameter = 0,
            )
            "90" -> toggle(
                kind = kind,
                onType = "TRACK_MODE",
                offType = "TRACK_MODE",
                onId = 0x90,
                offId = 0x91,
                onParameter = 1,
                offParameter = 0,
            )
            "45" -> toggle(
                kind = kind,
                onType = "CONST_SPEED_OPEN",
                offType = "CONST_SPEED_CLOSE",
                onId = 0x92,
                offId = 0x93,
            )
            "27" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (choice.selectedIndex == 0) {
                    VehicleSettingCommand("FORTIFY_MODE_NO", 0x8D)
                } else {
                    VehicleSettingCommand("FORTIFY_MODE_AUTO", 0x8E)
                }
            }
            "31" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (choice.selectedIndex == 0) {
                    VehicleSettingCommand("IMPERIAL_UNIT_KM", 0x89)
                } else {
                    VehicleSettingCommand("IMPERIAL_UNIT_MILES", 0x8A)
                }
            }
            // TopSpeedActivity / MaxSpeedSettingActivity：最高车速。
            "4", "58" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                VehicleSettingCommand(
                    commandType = "MAX_SPEED_SETTING",
                    commandId = 0x36,
                    parameter = slider.value.toInt().coerceIn(15, 120),
                )
            }
            // AutoShutDownActivity：离车自动锁与 3/5/10/15 秒时间。
            "7" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "LEAVE_THE_CAR_AUTO_LOCK_ON",
                    offType = "LEAVE_THE_CAR_AUTO_LOCK_OFF",
                    onId = 0x53,
                    offId = 0x54,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "LEAVE_THE_CAR_AUTO_LOCK_TIME",
                    commandId = 0x55,
                    parameter = kind.selectedIndex.coerceIn(0, 3),
                )
                else -> null
            }
            // AutoOnOffActivity：自动开关机。
            "11" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "AUTO_TURN_ON_OFF_OPEN",
                    offType = "AUTO_TURN_ON_OFF_CLOSE",
                    onId = 0x5C,
                    offId = 0x5D,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "AUTO_TURN_ON_OFF",
                    commandId = 0x5E,
                    parameter = kind.selectedIndex.coerceIn(0, 2),
                )
                else -> null
            }
            // GaugeStyleHandOffActivity：仪表主题。
            "50" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                VehicleSettingCommand(
                    commandType = "COMMAND_BIKE_PANEL_THEME_TYPE",
                    commandId = 0x3F,
                    parameter = choice.selectedIndex.coerceIn(0, 2),
                )
            }
            // RadarActivity：具体分项由 forItem 按条目 id 区分。
            "69" -> toggle(
                kind = kind,
                onType = "COMMAND_FRONT_COLLISION_OPEN",
                offType = "COMMAND_FRONT_COLLISION_CLOSE",
                onId = 0x13A,
                offId = 0x13A,
                onParameter = 1,
                offParameter = 0,
            )
            // QuietModeActivity：新旧两个超速提示字段一次下发。
            "93" -> {
                val toggle = kind as? CarSettingKind.Toggle ?: return null
                VehicleSettingCommand(
                    commandType = "COMMAND_DISABLE_OVERSPEED_ALERT",
                    commandId = 0x141,
                    parameters = if (toggle.checked) {
                        listOf(0, 2)
                    } else {
                        listOf(1, 1)
                    },
                )
            }
            // ScheduledShutDown2Activity：定时关机。
            "59" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "VEHICLE_MANDATORY_POWER_DOWN_OPEN",
                    offType = "VEHICLE_MANDATORY_POWER_DOWN_CLOSE",
                    onId = 0x7B,
                    offId = 0x7C,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "VEHICLE_MANDATORY_POWER_DOWN_TIME",
                    commandId = 0x7D,
                    parameter = kind.selectedIndex.coerceIn(0, 20),
                )
                else -> null
            }
            "54" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                VehicleSettingCommand(
                    commandType = "COMMAND_CONST_VOLTAGE_SETTING",
                    commandId = 0xC6,
                    parameter = (choice.selectedIndex + 1).coerceIn(1, 3),
                )
            }
            // OldVehicleSoundEffectsActivity：ALARM_THEME_0/1。
            "5" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                VehicleSettingCommand(
                    commandType = "ALARM_THEME",
                    commandId = 0x2E,
                    parameter = choice.selectedIndex.coerceIn(0, 1),
                )
            }
            "15" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "VEHICLE_MANDATORY_POWER_DOWN_OPEN",
                    offType = "VEHICLE_MANDATORY_POWER_DOWN_CLOSE",
                    onId = 0x7B,
                    offId = 0x7C,
                )
                is CarSettingKind.Slider -> VehicleSettingCommand(
                    commandType = "VEHICLE_MANDATORY_POWER_DOWN_TIME",
                    commandId = 0x7D,
                    parameter = kind.value.toInt().coerceIn(0, 20),
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "VEHICLE_MANDATORY_POWER_DOWN_TIME",
                    commandId = 0x7D,
                    parameter = kind.selectedIndex.coerceIn(0, 20),
                )
                else -> null
            }
            "42" -> toggle(
                kind = kind,
                onType = "DISPLAY_REMAINING_MILEAGE_OPEN",
                offType = "DISPLAY_REMAINING_MILEAGE_CLOSE",
                onId = 0xA4,
                offId = 0xA5,
            )
            // ReversingFunctionActivity：倒车模式。
            "43" -> toggle(
                kind = kind,
                onType = "REVERSING_OPEN",
                offType = "REVERSING_CLOSE",
                onId = 0xA6,
                offId = 0xA7,
            )
            // HelpPromoteActivity：婴儿车模式。
            "44" -> toggle(
                kind = kind,
                onType = "STROLLER_OPEN",
                offType = "STROLLER_CLOSE",
                onId = 0xA8,
                offId = 0xA9,
            )
            // HelpPromotePlusActivity：辅助推行开关与推行档位。
            "64", "89" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "STROLLER_OPEN",
                    offType = "STROLLER_CLOSE",
                    onId = 0xA8,
                    offId = 0xA9,
                )
                is CarSettingKind.Slider -> VehicleSettingCommand(
                    commandType = "COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING",
                    commandId = 0xD1,
                    parameter = kind.value.toInt().coerceIn(1, 3),
                )
                else -> null
            }
            // VehicleCustomizeActivity：CUSTOMIZE_SETTING 一次下发四项参数。
            "55", "92" -> {
                val customize = kind as? CarSettingKind.Customize ?: return null
                VehicleSettingCommand(
                    commandType = "CUSTOMIZE_SETTING",
                    commandId = 0xC8,
                    parameters = listOf(
                        customize.throttleLevel.coerceIn(0, 2),
                        customize.energyLevel.coerceIn(0, 2),
                        if (customize.energyState) 1 else 0,
                        if (customize.tcs) 1 else 0,
                    ),
                )
            }
            // BraceAutoShutDownActivity：边撑自动关机，依赖边撑感应（confId 2）。
            "39" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "LEAVE_THE_CAR_AUTO_LOCK_ON",
                    offType = "LEAVE_THE_CAR_AUTO_LOCK_OFF",
                    onId = 0x53,
                    offId = 0x54,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "LEAVE_THE_CAR_AUTO_LOCK_TIME",
                    commandId = 0x55,
                    parameter = kind.selectedIndex.coerceIn(0, 3),
                )
                else -> null
            }
            // CustomizeKeyActivity：默认按短按命令判断可写性，具体条目在 forItem 区分。
            "1005" -> keyFunctionCommand(kind, short = true)
            "81" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "COMMAND_WITH_GO_HOME_OPEN",
                    offType = "COMMAND_WITH_GO_HOME_CLOSE",
                    onId = 0x83,
                    offId = 0x84,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "COMMAND_WITH_GO_HOME_TIME_SETTING",
                    commandId = 0x85,
                    parameter = kind.selectedIndex.coerceIn(0, 3),
                )
                else -> null
            }
            "73" -> toggle(
                kind = kind,
                onType = "COMMAND_FEEL_LIGHT_SETTING_STATE",
                offType = "COMMAND_FEEL_LIGHT_SETTING_STATE",
                onId = 0xC3,
                offId = 0xC3,
                onParameter = 0,
                offParameter = 1,
            )
            "51" -> toggle(
                kind = kind,
                onType = "COMMAND_CONST_TIME_SYNC_OPEN",
                offType = "COMMAND_CONST_TIME_SYNC_CLOSE",
                onId = 0xC1,
                offId = 0xC1,
                onParameter = 1,
                offParameter = 0,
            )
            "13" -> toggle(
                kind = kind,
                onType = "WARING_SOUND_OPEN",
                offType = "WARING_SOUND_CLOSE",
                onId = 0x62,
                offId = 0x63,
            )
            // BodyStabilityActivity：value 0=开启，1=关闭。
            "37" -> toggle(
                kind = kind,
                onType = "TCS_BODY_STABILITY",
                offType = "TCS_BODY_STABILITY",
                onId = 0x9D,
                offId = 0x9D,
                onParameter = 0,
                offParameter = 1,
            )
            // AntiLockActivity：0x99 开启，0x9A 关闭。
            "38" -> toggle(
                kind = kind,
                onType = "ABS_ANTI_LOCK_OPEN",
                offType = "ABS_ANTI_LOCK_CLOSE",
                onId = 0x99,
                offId = 0x9A,
            )
            // SimulatedSoundActivity：0x95 开启，0x96 关闭。
            "33" -> toggle(
                kind = kind,
                onType = "SIMULATED_SOUND_WAVE_OPEN",
                offType = "SIMULATED_SOUND_WAVE_CLOSE",
                onId = 0x95,
                offId = 0x96,
            )
            // BrightnessAdjustmentActivity：开关走 0x135，手动亮度走 0x134。
            "86" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "BRIGHTNESS_ADJUST_SWITCH",
                    offType = "BRIGHTNESS_ADJUST_SWITCH",
                    onId = 0x135,
                    offId = 0x135,
                    onParameter = 1,
                    offParameter = 0,
                )
                is CarSettingKind.Slider -> VehicleSettingCommand(
                    commandType = "BRIGHTNESS_ADJUST_VALUE",
                    commandId = 0x134,
                    parameter = kind.value.toInt().coerceIn(0, 100),
                )
                else -> null
            }
            "19" -> toggle(
                kind = kind,
                onType = "AUTOMATIC_HEADLIGHT_OPEN",
                offType = "AUTOMATIC_HEADLIGHT_CLOSE",
                onId = 0x25,
                offId = 0x26,
            )
            "26" -> toggle(
                kind = kind,
                onType = "COMMAND_DIRECTION_LIGHT_RESET_ON",
                offType = "COMMAND_DIRECTION_LIGHT_RESET_OFF",
                onId = 0x23,
                offId = 0x24,
            )
            "36" -> toggle(
                kind = kind,
                onType = "BRAKE_OUTAGE_OPEN",
                offType = "BRAKE_OUTAGE_CLOSE",
                onId = 0x9B,
                offId = 0x9C,
            )
            "46" -> toggle(
                kind = kind,
                onType = "BLE_SLOP_DESCENT_OPEN",
                offType = "BLE_SLOP_DESCENT_CLOSE",
                onId = 0xAE,
                offId = 0xAF,
            )
            "49" -> toggle(
                kind = kind,
                onType = "BLE_RAMP_SLOP_OPEN",
                offType = "BLE_RAMP_SLOP_CLOSE",
                onId = 0xB0,
                offId = 0xB1,
            )
            // SuddenBrakingActivity：同一个命令 ID，value 0=开、1=关。
            "68" -> toggle(
                kind = kind,
                onType = "COMMAND_QUICK_BRAKE_LIGHT_SWITCH",
                offType = "COMMAND_QUICK_BRAKE_LIGHT_SWITCH",
                onId = 0xD9,
                offId = 0xD9,
                onParameter = 0,
                offParameter = 1,
            )
            // GearMemoryActivity：value 0=开、1=关。
            "94" -> toggle(
                kind = kind,
                onType = "COMMAND_GEAR_MEMORY",
                offType = "COMMAND_GEAR_MEMORY",
                onId = 0x144,
                offId = 0x144,
                onParameter = 0,
                offParameter = 1,
            )
            // AssistDriveLightActivity：value 0=开、1=关。
            "84" -> toggle(
                kind = kind,
                onType = "BLE_CONFIG_ASSIST_DRIVE_LIGHT",
                offType = "BLE_CONFIG_ASSIST_DRIVE_LIGHT",
                onId = 0x136,
                offId = 0x136,
                onParameter = 0,
                offParameter = 1,
            )
            // VehicleVolumeActivity：0..7 档。
            "20" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                VehicleSettingCommand(
                    commandType = "VOLUME",
                    commandId = 0x46,
                    parameter = slider.value.toInt().coerceIn(0, 7),
                )
            }
            // VehicleAlarmSensitivityActivity：0..7 档。
            "14" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                VehicleSettingCommand(
                    commandType = "ALARM_SENSITIVITY_GEAR",
                    commandId = 0x5F,
                    parameter = slider.value.toInt().coerceIn(0, 7),
                )
            }
            // EnergyRecoveryActivity：1..3 档，命令值直接是档位。
            "18" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                VehicleSettingCommand(
                    commandType = "ENERGY_RECOVER_LEVEL",
                    commandId = 0x3C,
                    parameter = slider.value.toInt().coerceIn(1, 3),
                )
            }
            // ChargeCapActivity：70..100%，5% 步进。
            "48" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                val percent = (slider.value.toInt() / 5 * 5).coerceIn(70, 100)
                VehicleSettingCommand(
                    commandType = "COMMAND_CONST_CHARGING_LIMIT_VALUE",
                    commandId = 0xCB,
                    parameter = percent,
                )
            }
            // CapacitySettingActivity：电池容量开关与固定 13/15/22/25/32/38Ah 档位。
            "60" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "COMMAND_CONST_CAPACITY_SWITCH_SETTING",
                    offType = "COMMAND_CONST_CAPACITY_SWITCH_SETTING",
                    onId = 0xD4,
                    offId = 0xD4,
                    onParameter = 1,
                    offParameter = 0,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "COMMAND_CONST_CAPACITY_SETTING",
                    commandId = 0xCD,
                    parameter = BatteryCapacityOptions.valuesAh
                        .getOrNull(kind.selectedIndex)
                        ?: BatteryCapacityOptions.valuesAh.last(),
                )
                else -> null
            }
            // ChargingPowerActivity：开关走 0xC9，瓦数滑杆走 0xCA。
            "47" -> when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "COMMAND_CONST_CHARGING_POWER_STATE",
                    offType = "COMMAND_CONST_CHARGING_POWER_STATE",
                    onId = 0xC9,
                    offId = 0xC9,
                    onParameter = 0,
                    offParameter = 1,
                )
                is CarSettingKind.Slider -> {
                    val watt = (kind.value.toInt() / 50 * 50).coerceIn(200, 1000)
                    VehicleSettingCommand(
                        commandType = "COMMAND_CONST_CHARGING_POWER_VALUE",
                        commandId = 0xCA,
                        parameter = watt,
                    )
                }
                else -> null
            }
            else -> null
        }

    fun forItem(item: CarSettingItem): VehicleSettingCommand? {
        when (item.id) {
            "cloud:60:enable" -> return toggle(
                kind = item.kind,
                onType = "COMMAND_CONST_CAPACITY_SWITCH_SETTING",
                offType = "COMMAND_CONST_CAPACITY_SWITCH_SETTING",
                onId = 0xD4,
                offId = 0xD4,
                onParameter = 1,
                offParameter = 0,
            )
            "cloud:60:level" -> {
                val choice = item.kind as? CarSettingKind.Choice ?: return null
                return VehicleSettingCommand(
                    commandType = "COMMAND_CONST_CAPACITY_SETTING",
                    commandId = 0xCD,
                    parameter = BatteryCapacityOptions.valuesAh
                        .getOrNull(choice.selectedIndex)
                        ?: BatteryCapacityOptions.valuesAh.last(),
                )
            }
            "cloud:1005:short" -> return keyFunctionCommand(item.kind, short = true)
            "cloud:1005:long" -> return keyFunctionCommand(item.kind, short = false)
            "cloud:69:fcw" -> return toggle(
                kind = item.kind,
                onType = "COMMAND_FRONT_COLLISION_OPEN",
                offType = "COMMAND_FRONT_COLLISION_CLOSE",
                onId = 0x13A,
                offId = 0x13A,
                onParameter = 1,
                offParameter = 0,
            )
            "cloud:69:rcw" -> return toggle(
                kind = item.kind,
                onType = "COMMAND_RADAR_RCW",
                offType = "COMMAND_RADAR_RCW",
                onId = 0x12D,
                offId = 0x12D,
                onParameter = 1,
                offParameter = 0,
            )
            "cloud:69:bsd" -> return toggle(
                kind = item.kind,
                onType = "COMMAND_RADAR_BSD",
                offType = "COMMAND_RADAR_BSD",
                onId = 0x12E,
                offId = 0x12E,
                onParameter = 1,
                offParameter = 0,
            )
        }
        // 自适应推行与总开关共用 confId 64，需要按条目 id 区分。
        if (item.id.endsWith(":adaptive") && item.configId in setOf("64", "89")) {
            return toggle(
                kind = item.kind,
                onType = "COMMAND_CONST_CUSTOM_DRIVE_SWITCH_SETTING",
                offType = "COMMAND_CONST_CUSTOM_DRIVE_SWITCH_SETTING",
                onId = 0xD0,
                offId = 0xD0,
                // 原通过 CheckBox 反值下发：勾选 = 0，未勾选 = 1。
                onParameter = 0,
                offParameter = 1,
            )
        }
        forConfigId(item.configId, item.kind)?.let { return it }
        return forLegacyItem(item)
    }

    private fun keyFunctionCommand(
        kind: CarSettingKind,
        short: Boolean,
    ): VehicleSettingCommand? {
        val choice = kind as? CarSettingKind.Choice ?: return null
        val functionId = if (choice.selectedIndex == 9) 14 else choice.selectedIndex
        return VehicleSettingCommand(
            commandType = if (short) {
                "CUSTOMIZE_KEYS_CONFIG_SHORT"
            } else {
                "CUSTOMIZE_KEYS_CONFIG_LONG"
            },
            commandId = if (short) 0x131 else 0x130,
            parameter = functionId.coerceIn(0, 15),
        )
    }

    private fun forLegacyItem(item: CarSettingItem): VehicleSettingCommand? = when (item.id) {
        "security.auto_lock" -> toggle(
            item = item,
            onType = "LEAVE_THE_CAR_AUTO_LOCK_ON",
            offType = "LEAVE_THE_CAR_AUTO_LOCK_OFF",
            onId = 0x53,
            offId = 0x54,
        )
        "light.find_sound" -> toggle(
            item = item,
            onType = "WARING_SOUND_OPEN",
            offType = "WARING_SOUND_CLOSE",
            onId = 0x62,
            offId = 0x63,
        )
        "ride.cruise" -> toggle(
            item = item,
            onType = "CRUISE_CONTROL_OPEN",
            offType = "CRUISE_CONTROL_CLOSE",
            onId = 0x92,
            offId = 0x93,
        )
        "ride.recover" -> {
            val choice = item.kind as? CarSettingKind.Choice ?: return null
            VehicleSettingCommand(
                commandType = "BRAKE_RECOVER_LEVEL",
                commandId = 0x3D,
                parameter = (choice.selectedIndex + 1).coerceIn(1, 3),
            )
        }
        "meter.brightness" -> {
            val slider = item.kind as? CarSettingKind.Slider ?: return null
            VehicleSettingCommand(
                commandType = "BRIGHTNESS_ADJUST_VALUE",
                commandId = 0x134,
                parameter = slider.value.toInt().coerceIn(0, 100),
            )
        }
        "meter.auto_brightness" -> toggle(
            item = item,
            onType = "BRIGHTNESS_ADJUST_SWITCH",
            offType = "BRIGHTNESS_ADJUST_SWITCH",
            onId = 0x135,
            offId = 0x135,
            onParameter = 1,
            offParameter = 0,
        )
        else -> null
    }

    private fun toggle(
        kind: CarSettingKind,
        onType: String,
        offType: String,
        onId: Int,
        offId: Int,
        onParameter: Int = -1,
        offParameter: Int = -1,
    ): VehicleSettingCommand? {
        val toggle = kind as? CarSettingKind.Toggle ?: return null
        return if (toggle.checked) {
            VehicleSettingCommand(onType, onId, onParameter)
        } else {
            VehicleSettingCommand(offType, offId, offParameter)
        }
    }

    private fun toggle(
        item: CarSettingItem,
        onType: String,
        offType: String,
        onId: Int,
        offId: Int,
        onParameter: Int = -1,
        offParameter: Int = -1,
    ): VehicleSettingCommand? {
        val toggle = item.kind as? CarSettingKind.Toggle ?: return null
        return if (toggle.checked) {
            VehicleSettingCommand(onType, onId, onParameter)
        } else {
            VehicleSettingCommand(offType, offId, offParameter)
        }
    }
}
