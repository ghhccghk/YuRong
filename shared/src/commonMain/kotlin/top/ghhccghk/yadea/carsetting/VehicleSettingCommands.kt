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

object VehicleSettingCommands {

    fun forItem(item: CarSettingItem): VehicleSettingCommand? = when (item.id) {
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
