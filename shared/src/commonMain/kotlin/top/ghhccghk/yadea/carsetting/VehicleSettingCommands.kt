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
        forConfigId(configId, kind) != null

    /**
     * 云端 `BikeConfig.confId` 对应的已确认命令。
     *
     * 只有能生成完整 `5944...4B4A` 帧的项才标为可写；其余云端项仍会展示，
     * 但保持只读，避免向车辆发送猜测出来的命令。
     */
    fun forConfigId(configId: String, kind: CarSettingKind): VehicleSettingCommand? =
        when (configId) {
            "45" -> toggle(
                kind = kind,
                onType = "CONST_SPEED_OPEN",
                offType = "CONST_SPEED_CLOSE",
                onId = 0x92,
                offId = 0x93,
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
            // BrightnessAdjustmentActivity：同一个命令 ID，用参数区分开关。
            "86" -> toggle(
                kind = kind,
                onType = "BRIGHTNESS_ADJUST_SWITCH",
                offType = "BRIGHTNESS_ADJUST_SWITCH",
                onId = 0x135,
                offId = 0x135,
                onParameter = 1,
                offParameter = 0,
            )
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
            else -> null
        }

    fun forItem(item: CarSettingItem): VehicleSettingCommand? {
        forConfigId(item.configId, item.kind)?.let { return it }
        return forLegacyItem(item)
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
