package top.ghhccghk.yadea.ble

/**
 * 从广播包解析车辆绑定需要的字段。
 *
 * 对应 `BleDevice.parseAdvertisementData`：
 * - `09FF5900` 后 12 个 hex 字符等于本机 MAC → 踏板车
 * - `1AFF` 的字节偏移 +12/+13/+25 分别包含 OTA 版本、BLE 协议版本和驻车状态
 */
object BleAdvertisementParser {

    fun parse(mac: String, data: ByteArray?): BleAdvertisementInfo {
        if (data == null || data.isEmpty()) return BleAdvertisementInfo()
        val hex = data.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val upper = hex.uppercase()
        val normalizedMac = mac.filter { it.isLetterOrDigit() }.uppercase()

        val scooter = run {
            val marker = upper.indexOf("09FF5900")
            if (marker < 0) return@run false
            val start = marker + 8
            val end = start + 12
            end <= upper.length && upper.substring(start, end) == normalizedMac
        }

        val markerIndex = upper.indexOf("1AFF")
        val advertised = markerIndex >= 0 && markerIndex % 2 == 0
        if (!advertised) {
            return BleAdvertisementInfo(
                isScooter = scooter,
                supportsToken = upper.contains("920B81E7"),
            )
        }

        val offset = markerIndex / 2
        if (data.size < offset + 0x1A + 1) {
            return BleAdvertisementInfo(
                isScooter = scooter,
                supportsToken = upper.contains("920B81E7"),
            )
        }
        val flags = data[offset + 0x0D].toInt() and 0xFF
        return BleAdvertisementInfo(
            isScooter = scooter,
            supportsToken = upper.contains("920B81E7"),
            bleProtocolVersion = flags and 0x07,
            parkingStatus = data[offset + 0x19].toInt() and 0xFF,
            supportsBleKey = (flags ushr 3) and 0x01,
            otaProtocolVersion = (data[offset + 0x0C].toInt() ushr 4) and 0x0F,
            softwareArchitecture = data[offset + 0x0C].toInt() and 0x0F,
        )
    }
}

data class BleAdvertisementInfo(
    val isScooter: Boolean = false,
    val isBox: Boolean = false,
    val supportsToken: Boolean = false,
    val bleProtocolVersion: Int = 0,
    val parkingStatus: Int = -1,
    val supportsBleKey: Int = 0,
    val otaProtocolVersion: Int = 0,
    val softwareArchitecture: Int = 0,
)
