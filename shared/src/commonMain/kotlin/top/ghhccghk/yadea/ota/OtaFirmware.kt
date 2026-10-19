package top.ghhccghk.yadea.ota

/** smali `OTAFileInfo.buildFile(file, "01")` 解析出的固件头。 */
data class OtaFirmwareInfo(
    val fileType: String,
    val rawHardwareType: String,
    val hardwareType: String,
    val productType: String,
    val vendorCode: String,
    val hardwareVendorCode: String,
    val crc32Hex: String,
    val fileLengthHex: String,
    val sofaVersionHex: String,
    val hardwareVersionHex: String,
)

/**
 * 固件 OTA 命令编码，按 `BleBicycleFirmwareOtaCommandManager` 转录。
 *
 * 这是高风险兼容实现：帧结构经过 smali 对照，但真车 bootloader 的 ACK
 * 时序仍可能因固件版本不同而变化。
 */
object OtaFirmware {
    const val OTA_SERVICE = "f000ffc0-0451-4000-b000-000000000000"
    const val OTA_CHARACTERISTIC = "f000ffc2-0451-4000-b000-000000000000"
    const val CHUNK_SIZE = 64

    fun parse(data: ByteArray): OtaFirmwareInfo? {
        if (data.size <= 0x18) return null
        val fileType = chars(data, 0, 3)
        val rawHardwareType = chars(data, 3, 3).uppercase()
        val hardwareType = bootIdOf(rawHardwareType)
        if (hardwareType.isBlank()) return null
        return OtaFirmwareInfo(
            fileType = fileType,
            rawHardwareType = rawHardwareType,
            hardwareType = hardwareType,
            productType = bytesHex(data, intArrayOf(5, 4, 3)),
            vendorCode = oneByteHex(data[6]),
            hardwareVendorCode = oneByteHex(data[7]),
            crc32Hex = bytesHex(data, intArrayOf(8, 9, 10, 11)),
            fileLengthHex = bytesHex(data, intArrayOf(12, 13, 14, 15)),
            sofaVersionHex = bytesHex(data, intArrayOf(16, 17, 18, 19)),
            hardwareVersionHex = bytesHex(data, intArrayOf(20, 21, 22, 23)),
        )
    }

    fun bootJump(info: OtaFirmwareInfo): ByteArray =
        command(
            info.hardwareType,
            "0B",
            "10",
            "02${info.sofaVersionHex}${info.productType}" +
                "${info.vendorCode}${info.hardwareVendorCode}",
        )

    fun start(info: OtaFirmwareInfo): ByteArray =
        command(info.hardwareType, "0B", "34", "0044FFFFFFFF${info.fileLengthHex}")

    fun erase(info: OtaFirmwareInfo): ByteArray =
        command(info.hardwareType, "0C", "31", "01FF00FFFFFFFF${info.fileLengthHex}")

    fun data(info: OtaFirmwareInfo, firmware: ByteArray, index: Int): ByteArray {
        val offset = index * CHUNK_SIZE
        val block = ByteArray(CHUNK_SIZE) { i ->
            val source = offset + i
            if (source < firmware.size) firmware[source] else 0xFF.toByte()
        }
        val indexHex = if (index == 0) {
            "FFFFFFFF"
        } else {
            ((index - 1) * CHUNK_SIZE).toString(16).uppercase().padStart(8, '0')
        }
        return command(info.hardwareType, "45", "36", indexHex + block.toHex())
    }

    fun end(info: OtaFirmwareInfo): ByteArray =
        command(info.hardwareType, "01", "37", "")

    fun integrity(info: OtaFirmwareInfo): ByteArray =
        command(info.hardwareType, "08", "32", "010202${info.crc32Hex}")

    fun reset(info: OtaFirmwareInfo): ByteArray =
        command(info.hardwareType, "02", "11", "01")

    fun packetCount(firmwareSize: Int): Int =
        (firmwareSize + CHUNK_SIZE - 1) / CHUNK_SIZE

    private fun command(
        hardwareType: String,
        commandId: String,
        subCommand: String,
        payloadHex: String,
    ): ByteArray {
        val body = hardwareType + commandId + subCommand + payloadHex
        val bodyBytes = body.hexToBytesOrNull() ?: return byteArrayOf()
        val check = bodyBytes.sumOf { it.toInt() and 0xFF } and 0xFF
        val frame = "5944$body${check.toString(16).uppercase().padStart(2, '0')}4B4A"
        return frame.hexToBytesOrNull() ?: byteArrayOf()
    }

    private fun bootIdOf(raw: String): String = when (raw) {
        "MCU" -> "27"
        "BMS" -> "17"
        "CHG" -> "57"
        "IOT" -> "07"
        "NFC" -> "E7"
        "ICM" -> "47"
        "PCU" -> "67"
        "BLE" -> "97"
        "ALM" -> "37"
        "OBD", "ETL" -> "87"
        else -> ""
    }

    private fun chars(data: ByteArray, offset: Int, length: Int): String =
        buildString(length) {
            for (i in offset until (offset + length).coerceAtMost(data.size)) {
                append((data[i].toInt() and 0xFF).toChar())
            }
        }

    private fun bytesHex(data: ByteArray, indices: IntArray): String =
        indices.joinToString("") { oneByteHex(data[it]) }

    private fun oneByteHex(value: Byte) =
        (value.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')

    private fun ByteArray.toHex(): String =
        joinToString("") { oneByteHex(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        val output = ByteArray(length / 2)
        for (i in output.indices) {
            output[i] = substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte()
                ?: return null
        }
        return output
    }
}
