package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.vehicle.VehicleFrame
import top.ghhccghk.yadea.vehicle.hexToBytes

/**
 * 车辆设置命令的 BLE 帧编码。
 *
 * 移植自 `BleBicycleCommandManager.generateBleCommand(commandId, param, 1)`
 * 中本项目已映射的命令分支。版本前缀使用原类构造函数默认值：
 * `bleCommSmartVersion=01`、`bleCommBigVersion=00`、
 * `appSmartVersion=01`、`appBigVersion=01`、`bikeBondType=111`、`appType=000`。
 *
 * 若后续读取到车辆协商版本，可把 [versionPrefix] 参数扩展为动态值。
 */
object VehicleSettingFrameEncoder {

    private const val DEFAULT_VERSION_PREFIX = "01000101F8"

    fun encode(
        command: VehicleSettingCommand,
        versionPrefix: String = DEFAULT_VERSION_PREFIX,
    ): ByteArray? {
        val suffix = suffixOf(command) ?: return null
        val payload = versionPrefix + suffix
        return VehicleFrame.build(VehicleFrame.ORDER_ID, payload)?.hexToBytes()
    }

    private fun suffixOf(command: VehicleSettingCommand): String? = when (command.commandId) {
        0x53 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFF"
        0x54 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFCFFFFFFFFFFFFFFF"
        0x62 -> "FFFFFFFFFFFFFFFF7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        0x63 -> "FFFFFFFFFFFFFFFF3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        0x92 -> "FFFFFFFFFFFFFFFFFFFFFFF7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        0x93 -> "FFFFFFFFFFFFFFFFFFFFFFF3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        // BodyStabilityActivity -> TCS_BODY_STABILITY，参数 0..7 映射到 F8..FF。
        0x9D -> "FF".repeat(35) + hex2(0xF8 + command.parameter.coerceIn(0, 7))
        // AntiLockActivity -> ABS_ANTI_LOCK_OPEN / CLOSE。
        0x99 -> "FF".repeat(27) + "7F"
        0x9A -> "FF".repeat(27) + "3F"
        // SimulatedSoundActivity -> SIMULATED_SOUND_WAVE_OPEN / CLOSE。
        0x95 -> "FF".repeat(27) + "FD"
        0x96 -> "FF".repeat(27) + "FC"
        // AutomaticLightActivity。
        0x25 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFF7FFFFFFFFFFFFFFFFFFFF"
        0x26 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFF3FFFFFFFFFFFFFFFFFFFF"
        // TurnSignalActivity。
        0x23 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFFFFFFF"
        0x24 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFCFFFFFFFFFFFFFFFFFFFF"
        // BrakePowerOffActivity。
        0x9B -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF7"
        0x9C -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF3"
        // SlopeDescentActivity。
        0xAE -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF7F"
        0xAF -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF3F"
        // RampStopsActivity。
        0xB0 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDF"
        0xB1 -> "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFCF"
        0x3D -> "FFFFFFFFFFFFFF" +
            hex2((command.parameter.coerceIn(1, 3) + 0x38)) +
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        0x134 -> "FF".repeat(0x46 - 5) + hex2(command.parameter.coerceIn(0, 100))
        0x135 -> {
            val bit = if (command.parameter == 1) 0xEF else 0xFF
            "FF".repeat(0x1B - 5) + hex2(bit) + "FF".repeat(0x1E - 0x1B)
        }
        else -> null
    }

    private fun hex2(value: Int): String =
        (value and 0xFF).toString(16).padStart(2, '0').uppercase()
}
