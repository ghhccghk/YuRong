package top.ghhccghk.yadea.vehicle

/** 车辆通知分派结果。 */
sealed interface VehicleNotify {
    data class Ttp(val info: TtpInfo, val isShort: Boolean = false) : VehicleNotify
    data class LegacyPanel(val info: PanelInfo) : VehicleNotify
    data class LegacyFault(val info: FaultInfo) : VehicleNotify
    data class Unknown(val raw: ByteArray) : VehicleNotify
}

data class PanelInfo(
    val speed: Int,
    val voltage: Float,
    val raw: ByteArray,
)

data class FaultInfo(
    val faults: List<Int>,
    val raw: ByteArray,
)

/**
 * `NotifyDispatcher` 的等价实现。
 *
 * 协议版本判定顺序来自 `connection.md` / smali：
 * - 14 字节且 `[9]=0x41, [10]=0x05` → 0xF1
 * - `[9]`、`[10]` 都不是 0xFF → 1
 * - 其余 → 0xF1
 */
object VehicleNotifyDispatcher {

    fun protocolVersion(data: ByteArray): Int {
        if (data.size <= 10) return 0xF1
        if (data.size == 14 && data[9] == 0x41.toByte() && data[10] == 0x05.toByte()) {
            return 0xF1
        }
        val b9 = data[9].toInt() and 0xFF
        val b10 = data[10].toInt() and 0xFF
        return if (b9 < 0xFF && b10 < 0xFF) 1 else 0xF1
    }

    fun dispatch(data: ByteArray): VehicleNotify {
        if (data.size <= 2) return VehicleNotify.Unknown(data)
        if (data[2].toInt() and 0xFF == 0x29) {
            // 短报文只含两个独立状态位，不覆盖完整状态快照；UI 由完整报文驱动。
            return VehicleNotify.Ttp(TtpInfoParser.parse(data), isShort = true)
        }
        if (protocolVersion(data) != 1) {
            return legacyDispatch(data)
        }
        return when (data[2].toInt() and 0xFF) {
            0x2A, 0x7A -> VehicleNotify.Ttp(TtpInfoParser.parse(data))
            else -> VehicleNotify.Unknown(data)
        }
    }

    private fun legacyDispatch(data: ByteArray): VehicleNotify {
        val tokens = data.toHex().chunked(2)
        if (tokens.size < 2 || tokens[0] != "04") return VehicleNotify.Unknown(data)
        return when (tokens[1]) {
            "01" -> VehicleNotify.LegacyPanel(
                PanelInfo(
                    speed = tokens.getOrNull(2)?.toIntOrNull(16) ?: -1,
                    voltage = tokens.getOrNull(3)?.toIntOrNull(16)?.toFloat() ?: -1f,
                    raw = data,
                ),
            )
            "02" -> VehicleNotify.LegacyFault(
                FaultInfo(
                    faults = tokens.drop(2).mapNotNull { it.toIntOrNull(16) },
                    raw = data,
                ),
            )
            else -> VehicleNotify.Unknown(data)
        }
    }
}
