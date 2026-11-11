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
) {
    init {
        // These parameter domains are established by ch.d; never let its encoder
        // fallback/clamping turn an invalid direct command into a valid write.
        when (commandId) {
            0x55 -> require(parameter in 0..3)
            0x5E -> require(parameter in 0..2)
            0x2E -> require(parameter in 0..2) // ch.d also defines THEME_2; ID5 exposes only 0/1.
            0xC9, 0xD2, 0xD4 -> require(parameter in 0..1)
            0xCA -> require(parameter in 200..1000 && parameter % 50 == 0)
            0xCB -> require(parameter in 70..100 && parameter % 5 == 0)
            0xCD -> require(parameter in BatteryCapacityOptions.valuesAh)
            0xD3 -> require(parameter in 0..2)
            0x7D -> require(parameter in 0..20)
            0x85 -> require(parameter in 0..3)
            0x3F -> require(parameter in 0..2) // Raw protocol includes 2; ID50 exposes 0/1.
            0xC6 -> require(parameter in 0..6) // Raw voltage domain; ID54 exposes 1/2/3.
            0xC7 -> require(parameter in 0..1)
            0x9D -> require(parameter in 0..7) // BleBicycleCommandManager:2741–2749, not a guessed boolean domain.
            0xCF, 0xD9, 0xDD, 0xFB1 -> require(parameter in 0..1)
            0x132, 0x136, 0x13F, 0x144 -> require(parameter in 0..1)
            0x46 -> require(parameter in 0..7) // Raw command domain; the volume page exposes only 0/1/2.
            0x134 -> require(parameter in 0..100)
            0x135 -> require(parameter in 0..1)
            0x3C -> require(parameter in 0..7) // Raw protocol; EnergyRecoveryActivity exposes 1/2/3.
        }
        // Validate even callers that construct a composite command without the mapper.
        if (commandId == 0xC8 || commandType == "CUSTOMIZE_SETTING") {
            require(commandId == 0xC8 && commandType == "CUSTOMIZE_SETTING" && parameters is CustomizeCommandParameters) {
                "CUSTOMIZE_SETTING requires four explicit, valid fields"
            }
        }
        if (commandId == 0xF7 || commandType == "ATMOSPHERE_CONFIG") {
            require(commandId == 0xF7 && commandType == "ATMOSPHERE_CONFIG" && parameters is AtmosphereCommandParameters) {
                "ATMOSPHERE_CONFIG requires an explicit valid mode-dependent configuration"
            }
        }
    }
}

/** Fixed-size immutable payload; direct callers cannot substitute or mutate an unvalidated list. */
private class AtmosphereCommandParameters(kind: CarSettingKind.Atmosphere) : AbstractList<Int>() {
    private val values = requireNotNull(AtmosphereSettings.payload(kind)).toIntArray()
    override val size: Int get() = values.size
    override fun get(index: Int): Int = values[index]
}

/** No mutable List alias can change a validated composite between mapping and encoding. */
private class CustomizeCommandParameters(
    private val throttle: Int,
    private val energy: Int,
    private val energyState: Int,
    private val tcs: Int,
) : AbstractList<Int>() {
    init {
        require(throttle in 0..2 && energy in 0..2 && energyState in 0..1 && tcs in 0..1)
    }
    override val size: Int get() = 4
    override fun get(index: Int): Int = when (index) {
        0 -> throttle
        1 -> energy
        2 -> energyState
        3 -> tcs
        else -> throw IndexOutOfBoundsException(index.toString())
    }
}

/** 平台侧负责把 [VehicleSettingCommand] 编码成整车 BLE 命令并写入协议命令特征。 */
interface VehicleSettingCommandSink {
    suspend fun send(command: VehicleSettingCommand): Boolean
}

class MappedCarSettingWriter(
    private val sink: VehicleSettingCommandSink,
) : CarSettingWriter {
    override suspend fun write(item: CarSettingItem): Boolean =
        writeWithResult(item).success

    override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
        if (!CarSettingItemAccess.canWrite(item)) return CarSettingWriteResult(false)
        val command = VehicleSettingCommands.forItem(item)
            ?: return CarSettingWriteResult(false)
        val sent = sink.send(command)
        return CarSettingWriteResult(
            success = sent,
            channel = CarSettingWriteChannel.BLE,
            confirmationStatus = if (sent) {
                CarSettingConfirmationStatus.LOCAL_UPDATED
            } else {
                CarSettingConfirmationStatus.FAILED
            },
        )
    }
}

/**
 * 原 App 的分流：蓝牙已连接且存在 BLE 映射时优先 BLE，否则走云端字段。
 * BLE 写入失败时允许再尝试云端，云端失败则不伪造成功。
 *
 * 云端清单决定项目是否存在与是否授权；写入通道仍按各 configId 的原实现分流。
 */
class CloudCarSettingWriter(
    private val gateway: CarSettingsGateway,
    private val vinProvider: () -> String,
    private val bleFallback: CarSettingWriter? = null,
    private val bleAvailableProvider: () -> Boolean = { false },
) : CarSettingWriter {
    override suspend fun write(item: CarSettingItem): Boolean =
        writeWithResult(item).success

    override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
        if (!CarSettingItemAccess.canWrite(item)) return CarSettingWriteResult(false)
        if (item.configId in BatterySettings.ids && !BatterySettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in TimerSettings.ids && !TimerSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in SimpleChoiceSettings.ids && !SimpleChoiceSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId == "10" && !GeofenceSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId == "73" && !AtmosphereSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in SimpleToggleSettings.ids && !SimpleToggleSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in VolumeBrightnessSettings.ids && !VolumeBrightnessSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in EnergyRecoverySettings.ids && !EnergyRecoverySettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in CloudSwitchSettings.ids && !CloudSwitchSettings.validItem(item)) return CarSettingWriteResult(false)
        if (item.configId in TrackerSettings.ids && !TrackerSettings.validItem(item)) return CarSettingWriteResult(false)
        if ((item.configId in setOf("4", "58", "61", "55", "92") || item.configId in SecondPhaseSettings.ids) &&
            VehicleSettingCommands.forItem(item) == null
        ) return CarSettingWriteResult(false)
        val vin = vinProvider().trim()
        if (vin.isBlank()) return CarSettingWriteResult(false)
        if (item.configId == "2" && !SideBraceSettings.authorized(item, vin)) return CarSettingWriteResult(false)
        if (item.configId == "42" && !RemainingMileageSettings.authorized(item, vin)) return CarSettingWriteResult(false)
        if (!TrackerSettings.authorized(item, vin)) return CarSettingWriteResult(false)
        if (item.configId == "33" && !SimulatedSoundSettings.authorized(item, vin)) return CarSettingWriteResult(false)
        if (item.configId == "51" && !TimeSyncSettings.validItem(item)) return CarSettingWriteResult(false)
        val update = CloudCarSettingUpdateBuilder.build(
            configId = item.configId,
            kind = item.kind,
            vin = vin,
            geofence = item.geofenceConfiguration,
        )
        if (item.configId == "10" && update == null) return CarSettingWriteResult(false)
        if (item.configId == "76") {
            val authorization = item.locationSwitchAuthorization ?: return CarSettingWriteResult(false)
            if (update == null) return CarSettingWriteResult(false)
            val receipt = gateway.updateLocationSwitch(vin, update, authorization)
            if (vinProvider().trim() != vin) return CarSettingWriteResult(false)
            return CarSettingWriteResult(receipt.accepted, CarSettingWriteChannel.CLOUD, resultId = receipt.resultId)
        }
        val bleAvailable = bleAvailableProvider()
        if (bleAvailable && item.configId != "10") {
            val fallback = bleFallback?.writeWithResult(item)
            if (vinProvider().trim() != vin) return CarSettingWriteResult(false)
            if (fallback?.success == true) return fallback
        }
        if (vinProvider().trim() != vin) return CarSettingWriteResult(false)
        if (update != null && gateway.update(vin, update)) {
            if (vinProvider().trim() != vin) return CarSettingWriteResult(false)
            return CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.CLOUD,
                confirmationStatus = CarSettingConfirmationStatus.CLOUD_ACCEPTED,
            )
        }
        return CarSettingWriteResult(false)
    }
}

object VehicleSettingCommands {

    fun supports(configId: String, kind: CarSettingKind): Boolean =
        // Capability must survive unknown initial values; forItem/forConfigId validate writes.
        SimpleChoiceSettings.supports(configId, kind) || TimerSettings.supports(configId, kind) || BatterySettings.supports(configId, kind) ||
            VolumeBrightnessSettings.supports(configId, kind) ||
            EnergyRecoverySettings.supports(configId, kind) ||
            (configId in SimpleToggleSettings.ids && kind is CarSettingKind.Toggle) ||
            (configId == "73" && (kind is CarSettingKind.Atmosphere || kind is CarSettingKind.Toggle)) ||
            (configId in setOf("55", "92") && kind is CarSettingKind.Customize) ||
            (configId in setOf("4", "58", "61") && kind is CarSettingKind.Slider) ||
            (configId in SecondPhaseSettings.ids && when (configId) {
                "5" -> kind is CarSettingKind.Choice
                else -> kind is CarSettingKind.Toggle || kind is CarSettingKind.Choice
            }) ||
            (configId in setOf("2", "42", "33", "51") && kind is CarSettingKind.Toggle) ||
            forConfigId(configId, kind)?.let { VehicleSettingFrameEncoder.encode(it) != null } == true

    fun supportsItem(item: CarSettingItem): Boolean =
        if (item.configId in setOf("4", "58", "61", "55", "92") || item.configId in SecondPhaseSettings.ids ||
            item.configId in BatterySettings.ids || item.configId in TimerSettings.ids ||
            item.configId in SimpleChoiceSettings.ids || item.configId in SimpleToggleSettings.ids ||
            item.configId in VolumeBrightnessSettings.ids || item.configId in EnergyRecoverySettings.ids || item.configId in setOf("73", "42", "2", "33", "51")) supports(item.configId, item.kind)
        else forItem(item)?.let { VehicleSettingFrameEncoder.encode(it) != null } == true

    /**
     * 云端 `BikeConfig.confId` 对应的已确认命令。
     *
     * 只有能生成完整 `5944...4B4A` 帧的项才标为可写；其余云端项仍会展示，
     * 但保持只读，避免向车辆发送猜测出来的命令。
     */
    fun forConfigId(configId: String, kind: CarSettingKind): VehicleSettingCommand? =
        if (configId in SimpleToggleSettings.ids && !SimpleToggleSettings.valid(kind)) null else when (configId) {
            // TempleInductionActivity：边撑感应。
            "2" -> if (kind !is CarSettingKind.Toggle || !kind.valueKnown || !kind.checked) null else toggle(
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
            // EnergyRecoveryActivity：能量回收开关。
            "17" -> if (!EnergyRecoverySettings.validTarget(configId, kind)) null else toggle(
                kind = kind,
                onType = "ENERGY_RECOVER_OPEN",
                offType = "ENERGY_RECOVER_CLOSE",
                onId = 0x13,
                offId = 0x14,
            )
            // BracketPoweredActivity：边撑开机。
            "53" -> toggle(
                kind = kind,
                onType = "COMMAND_CONST_TEMPLE_SETTING",
                offType = "COMMAND_CONST_TEMPLE_SETTING",
                onId = 0xC7,
                offId = 0xC7,
                onParameter = 1,
                offParameter = 0,
            )
            "32" -> toggle(
                kind = kind,
                onType = "TILT_WARNING_OPEN",
                offType = "TILT_WARNING_CLOSE",
                onId = 0xA0,
                offId = 0xA1,
            )
            "63" -> toggle(
                kind = kind,
                onType = "COMMAND_CONST_T_POWER_SETTING",
                offType = "COMMAND_CONST_T_POWER_SETTING",
                onId = 0xCF,
                offId = 0xCF,
                onParameter = 1,
                offParameter = 0,
            )
            // HeatedSeatsActivity：座椅加热开关和档位。
            "62" -> if (!BatterySettings.validTarget(configId, kind)) null else when (kind) {
                is CarSettingKind.Toggle -> toggle(
                    kind = kind,
                    onType = "SEAT_HEAT_SWITCH",
                    offType = "SEAT_HEAT_SWITCH",
                    onId = 0xD2,
                    offId = 0xD2,
                    onParameter = 1,
                    offParameter = 0,
                )
                is CarSettingKind.Choice -> VehicleSettingCommand(
                    commandType = "SEAT_HEAT_LEVEL",
                    commandId = 0xD3,
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
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
                onId = 0x13F,
                offId = 0x13F,
                onParameter = 1,
                offParameter = 0,
            )
            // EnduranceModeActivity.refreshCheckBox / setListener$lambda$2：0=长续航，1=狂暴。
            "72" -> toggle(
                kind = kind,
                onType = "LONG_ENDURANCE_SETTING",
                offType = "LONG_ENDURANCE_SETTING",
                onId = 0xDD,
                offId = 0xDD,
                onParameter = 0,
                offParameter = 1,
            )
            // HibernateModeActivity：休眠模式。
            "77" -> toggle(
                kind = kind,
                onType = "SLEEP_MODE_SWITCH",
                offType = "SLEEP_MODE_SWITCH",
                onId = 0x133,
                offId = 0x133,
                onParameter = 1,
                offParameter = 0,
            )
            // OTDActivity.onSwClick：旧开关取反，1=开、0=关。
            "80" -> toggle(
                kind = kind,
                onType = "COMMAND_OTD_SWITCH",
                offType = "COMMAND_OTD_SWITCH",
                onId = 0x132,
                offId = 0x132,
                onParameter = 1,
                offParameter = 0,
            )
            "45" -> toggle(
                kind = kind,
                onType = "CONST_SPEED_OPEN",
                offType = "CONST_SPEED_CLOSE",
                onId = 0xAA,
                offId = 0xAB,
            )
            "27" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (!SimpleChoiceSettings.validTarget(configId, choice)) return null
                if (choice.selectedIndex == 0) {
                    VehicleSettingCommand("FORTIFY_MODE_NO", 0x8D)
                } else {
                    VehicleSettingCommand("FORTIFY_MODE_AUTO", 0x8E)
                }
            }
            "31" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (!SimpleChoiceSettings.validTarget(configId, choice)) return null
                if (choice.selectedIndex == 0) {
                    VehicleSettingCommand("IMPERIAL_UNIT_KM", 0x89)
                } else {
                    VehicleSettingCommand("IMPERIAL_UNIT_MILES", 0x8A)
                }
            }
            // TopSpeedActivity / MaxSpeedSettingActivity：最高车速。
            "4" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                val speed = slider.value.toInt()
                if (!slider.rangeKnown || !slider.valueKnown || !slider.value.isFinite() || slider.value != speed.toFloat() ||
                    speed !in 15..120 || slider.value !in slider.range
                ) return null
                VehicleSettingCommand(
                    commandType = "MAX_SPEED_SETTING",
                    commandId = 0x36,
                    parameter = speed,
                )
            }
            "58" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                val presets = slider.speedPresets
                val speed = slider.value.toInt()
                if (!slider.rangeKnown || !slider.valueKnown || !slider.value.isFinite() || slider.value != speed.toFloat() ||
                    presets.size != 3 || presets[0] !in 15..120 || presets[1] !in 15..120 ||
                    presets[0] >= presets[1] || presets[2] != 0 || speed !in presets ||
                    slider.value !in slider.range
                ) return null
                VehicleSettingCommand("MAX_SPEED_SETTING", 0x36, parameter = speed)
            }
            // MaxSpeedLimitActivity：0 表示不限速，非零最低 15 km/h。
            "61" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                val speed = slider.value.toInt()
                if (!slider.rangeKnown || !slider.valueKnown || !slider.value.isFinite() || slider.value != speed.toFloat() || speed.toFloat() !in slider.range ||
                    (speed != 0 && (speed < (slider.minimumNonZero?.toInt() ?: 15) || speed !in 15..120))
                ) return null
                VehicleSettingCommand(
                    commandType = "MAX_SPEED_SETTING",
                    commandId = 0x36,
                    parameter = speed,
                )
            }
            // AutoShutDownActivity：离车自动锁与 3/5/10/15 秒时间。
            "7" -> if (!SecondPhaseSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
            // AutoOnOffActivity：自动开关机。
            "11" -> if (!SecondPhaseSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
            // GaugeStyleHandOffActivity：仪表主题。
            "50" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (!SimpleChoiceSettings.validTarget(configId, choice)) return null
                VehicleSettingCommand(
                    commandType = "COMMAND_BIKE_PANEL_THEME_TYPE",
                    commandId = 0x3F,
                    parameter = choice.selectedIndex,
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
                        listOf(1, 2)
                    } else {
                        listOf(0, 1)
                    },
                )
            }
            // ScheduledShutDown2Activity：定时关机。
            "59" -> if (!TimerSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
            "54" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (!SimpleChoiceSettings.validTarget(configId, choice)) return null
                VehicleSettingCommand(
                    commandType = "COMMAND_CONST_VOLTAGE_SETTING",
                    commandId = 0xC6,
                    parameter = choice.selectedIndex + 1,
                )
            }
            // OldVehicleSoundEffectsActivity：ALARM_THEME_0/1。
            "5" -> {
                val choice = kind as? CarSettingKind.Choice ?: return null
                if (!SecondPhaseSettings.validTarget(configId, choice)) return null
                VehicleSettingCommand(
                    commandType = "ALARM_THEME",
                    commandId = 0x2E,
                    parameter = choice.selectedIndex,
                )
            }
            "15" -> if (!TimerSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.value.toInt(),
                )
                else -> null
            }
            "42" -> if (kind !is CarSettingKind.Toggle || !kind.valueKnown || kind.checked) null else toggle(
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
                    parameter = kind.value.takeIf { it in 0f..2f && it % 1f == 0f }?.toInt() ?: return null,
                )
                else -> null
            }
            // VehicleCustomizeActivity：CUSTOMIZE_SETTING 一次下发四项参数。
            "55", "92" -> {
                val customize = kind as? CarSettingKind.Customize ?: return null
                val throttle = customize.throttleLevel?.takeIf { it in 0..2 } ?: return null
                val energyLevel = customize.energyLevel?.takeIf { it in 0..2 } ?: return null
                val energyState = customize.energyState ?: return null
                val tcs = customize.tcs ?: return null
                VehicleSettingCommand(
                    commandType = "CUSTOMIZE_SETTING",
                    commandId = 0xC8,
                    parameters = CustomizeCommandParameters(
                        throttle,
                        energyLevel,
                        if (energyState) 1 else 0,
                        if (tcs) 1 else 0,
                    ),
                )
            }
            // BraceAutoShutDownActivity：边撑自动关机，依赖边撑感应（confId 2）。
            "39" -> if (!SecondPhaseSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
            // CustomizeKeyActivity：默认按短按命令判断可写性，具体条目在 forItem 区分。
            "1005" -> keyFunctionCommand(kind, short = true)
            "21", "81" -> if (!TimerSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.selectedIndex,
                )
                else -> null
            }
            "73" -> if (kind !is CarSettingKind.Toggle || !kind.valueKnown) null else toggle(
                kind = kind,
                onType = "COMMAND_FEEL_LIGHT_SETTING_STATE",
                offType = "COMMAND_FEEL_LIGHT_SETTING_STATE",
                onId = 0xC3,
                offId = 0xC3,
                onParameter = 0,
                offParameter = 1,
            )
            "51" -> if (!TimeSyncSettings.validTarget(kind)) null else toggle(
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
            // The original switch requires D5/R availability; a bare confId carries no proof.
            "33" -> null
            // BrightnessAdjustmentActivity：开关走 0x135，手动亮度走 0x134。
            "86" -> if (!VolumeBrightnessSettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = kind.value.toInt(),
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
            // SuddenBrakingActivity.onSwClick：旧开关取反，1=开、0=关。
            "68" -> toggle(
                kind = kind,
                onType = "COMMAND_QUICK_BRAKE_LIGHT_SWITCH",
                offType = "COMMAND_QUICK_BRAKE_LIGHT_SWITCH",
                onId = 0xD9,
                offId = 0xD9,
                onParameter = 1,
                offParameter = 0,
            )
            // GearMemoryActivity.onSwClick：旧开关取反，1=开、0=关。
            "94" -> toggle(
                kind = kind,
                onType = "COMMAND_GEAR_MEMORY",
                offType = "COMMAND_GEAR_MEMORY",
                onId = 0x144,
                offId = 0x144,
                onParameter = 1,
                offParameter = 0,
            )
            // AssistDriveLightActivity.onSwClick：旧开关取反，1=开、0=关。
            "84" -> toggle(
                kind = kind,
                onType = "BLE_CONFIG_ASSIST_DRIVE_LIGHT",
                offType = "BLE_CONFIG_ASSIST_DRIVE_LIGHT",
                onId = 0x136,
                offId = 0x136,
                onParameter = 1,
                offParameter = 0,
            )
            // VehicleVolumeActivity: low/middle/high, thumbPosition 0/1/2.
            "20" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                if (!VolumeBrightnessSettings.validTarget(configId, slider)) return null
                VehicleSettingCommand(
                    commandType = "VOLUME",
                    commandId = 0x46,
                    parameter = slider.value.toInt(),
                )
            }
            // VehicleAlarmSensitivityActivity sends thumbPosition, not 0/50/100 progress.
            "14" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                VehicleSettingCommand(
                    commandType = "ALARM_SENSITIVITY_GEAR",
                    commandId = 0x5F,
                    parameter = AlarmSensitivityMapping.tickFromProgress(slider.value) ?: return null,
                )
            }
            // EnergyRecoveryActivity：1..3 档，命令值直接是档位。
            "18" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                if (!EnergyRecoverySettings.validTarget(configId, slider)) return null
                VehicleSettingCommand(
                    commandType = "ENERGY_RECOVER_LEVEL",
                    commandId = 0x3C,
                    parameter = slider.value.toInt(),
                )
            }
            // ChargeCapActivity：70..100%，5% 步进。
            "48" -> {
                val slider = kind as? CarSettingKind.Slider ?: return null
                if (!BatterySettings.validTarget(configId, slider) || slider.value.toInt() % 5 != 0) return null
                val percent = slider.value.toInt()
                VehicleSettingCommand(
                    commandType = "COMMAND_CONST_CHARGING_LIMIT_VALUE",
                    commandId = 0xCB,
                    parameter = percent,
                )
            }
            // CapacitySettingActivity：电池容量开关与固定 13/15/22/25/32/38Ah 档位。
            "60" -> if (!BatterySettings.validTarget(configId, kind)) null else when (kind) {
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
                    parameter = BatteryCapacityOptions.valuesAh[kind.selectedIndex],
                )
                else -> null
            }
            // ChargingPowerActivity：开关走 0xC9，瓦数滑杆走 0xCA。
            "47" -> if (!BatterySettings.validTarget(configId, kind)) null else when (kind) {
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
                    val watt = kind.value.toInt()
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
        if (item.configId == "51") {
            if (!TimeSyncSettings.validItem(item)) return null
            return forConfigId("51", item.kind)
        }
        if (item.configId == "33") {
            if (!SimulatedSoundSettings.authorized(item)) return null
            return if ((item.kind as CarSettingKind.Toggle).checked)
                VehicleSettingCommand("SIMULATED_SOUND_WAVE_OPEN", 0x95)
            else VehicleSettingCommand("SIMULATED_SOUND_WAVE_CLOSE", 0x96)
        }
        if (item.configId == "2") {
            if (!SideBraceSettings.authorized(item)) return null
            return if ((item.kind as CarSettingKind.Toggle).checked) forConfigId("2", item.kind)
            else VehicleSettingCommand("SLIDE_BRACE_OFF", 0x10)
        }
        if (item.configId == "42") {
            if (!RemainingMileageSettings.authorized(item)) return null
            return if ((item.kind as CarSettingKind.Toggle).checked)
                VehicleSettingCommand("DISPLAY_REMAINING_MILEAGE_OPEN", 0xA4)
            else forConfigId("42", item.kind)
        }
        if (item.configId in EnergyRecoverySettings.ids) {
            if (!EnergyRecoverySettings.validItem(item)) return null
            return forConfigId(item.configId, item.kind)
        }
        if (item.configId in VolumeBrightnessSettings.ids) {
            if (!VolumeBrightnessSettings.validItem(item)) return null
            return forConfigId(item.configId, item.kind)
        }
        if (item.configId in SimpleToggleSettings.ids && !SimpleToggleSettings.validItem(item)) return null
        if (item.configId == "73" && !AtmosphereSettings.validItem(item)) return null
        // The original fence flow is cloud-only; split IDs must not turn it into a BLE write.
        if (item.configId == "10") return null
        if (item.configId in SimpleChoiceSettings.ids) {
            if (!SimpleChoiceSettings.validItem(item)) return null
            return forConfigId(item.configId, item.kind)
        }
        if (item.configId in TimerSettings.ids) {
            if (!TimerSettings.validItem(item)) return null
            return forConfigId(item.configId, item.kind)
        }
        if (item.configId in BatterySettings.ids) {
            if (!BatterySettings.validItem(item)) return null
            return forConfigId(item.configId, item.kind)
        }
        if (item.configId in SecondPhaseSettings.ids) {
            val expectedId = when (item.kind) {
                is CarSettingKind.Toggle -> "cloud:${item.configId}"
                is CarSettingKind.Choice -> if (item.configId == "5") "cloud:5"
                    else "cloud:${item.configId}:${if (item.configId == "11") "distance" else "time"}"
                else -> return null
            }
            if (item.id != expectedId) return null
            return forConfigId(item.configId, item.kind)
        }
        // These whole-item settings must not enter an unrelated split-id shortcut.
        if (item.configId in setOf("4", "58", "61", "55", "92")) {
            return forConfigId(item.configId, item.kind)
        }
        when (item.id) {
            "cloud:001" -> {
                val choice = item.kind as? CarSettingKind.Choice ?: return null
                return VehicleSettingCommand(
                    commandType = if (choice.selectedIndex == 0) {
                        "METRIC_SYSTEM_SETTING"
                    } else {
                        "BRITISH_SYSTEM_SETTING"
                    },
                    commandId = if (choice.selectedIndex == 0) 0x89 else 0x8A,
                )
            }
            "cloud:002:enable" -> return toggle(
                kind = item.kind,
                onType = "AUTO_TURN_ON_OFF_OPEN",
                offType = "AUTO_TURN_ON_OFF_CLOSE",
                onId = 0x5C,
                offId = 0x5D,
            )
            "cloud:002:level" -> {
                val slider = item.kind as? CarSettingKind.Slider ?: return null
                return VehicleSettingCommand(
                    commandType = "AUTO_TURN_ON_OFF",
                    commandId = 0x5E,
                    parameter = slider.value.toInt().coerceIn(0, 2),
                )
            }
            "cloud:003:enable" -> return toggle(
                kind = item.kind,
                onType = "BRAKE_RECOVER_OPEN",
                offType = "BRAKE_RECOVER_CLOSE",
                onId = 0x15,
                offId = 0x16,
            )
            "cloud:003:level" -> {
                val slider = item.kind as? CarSettingKind.Slider ?: return null
                return VehicleSettingCommand(
                    commandType = "BRAKE_RECOVER_LEVEL",
                    commandId = 0x3D,
                    parameter = slider.value.toInt().coerceIn(1, 3),
                )
            }
            "cloud:1:enable" -> return toggle(
                kind = item.kind,
                onType = "NEAR_UNLOCK_OPEN",
                offType = "NEAR_UNLOCK_CLOSE",
                onId = 0x0D,
                offId = 0x0E,
            )
            "cloud:1:distance" -> {
                val slider = item.kind as? CarSettingKind.Slider ?: return null
                return VehicleSettingCommand(
                    commandType = "NEARBY_BLE_UNLOCK",
                    commandId = 0x37,
                    parameter = slider.value.toInt().coerceIn(0, 7),
                )
            }
            "cloud:25" -> return toggle(
                kind = item.kind,
                onType = "COMMAND_PASSWORD_UNLOCK_OPEN",
                offType = "BLE_CONFIG_COMMAND_PASSWORD_UNLOCK",
                onId = 0x80,
                offId = 0x81,
            )
            "cloud:73:config" -> {
                val config = item.kind as? CarSettingKind.Atmosphere ?: return null
                if (item.configId != "73" || !AtmosphereSettings.valid(config)) return null
                return VehicleSettingCommand(
                    commandType = "ATMOSPHERE_CONFIG",
                    commandId = 0xF7,
                    parameters = AtmosphereCommandParameters(config),
                )
            }
        }
        when (item.id) {
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
                // Parent click reads the old checkbox; command parameters represent the target.
                onParameter = when (item.configId) {
                    "64" -> AdaptivePush64Mapping.parameterForTarget(true)
                    else -> AdaptivePush89Mapping.parameterForTarget(true)
                },
                offParameter = when (item.configId) {
                    "64" -> AdaptivePush64Mapping.parameterForTarget(false)
                    else -> AdaptivePush89Mapping.parameterForTarget(false)
                },
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
