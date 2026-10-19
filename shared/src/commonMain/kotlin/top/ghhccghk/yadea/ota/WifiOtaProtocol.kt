package top.ghhccghk.yadea.ota

/**
 * Wi-Fi OTA 的字节级协议。
 *
 * 事实来源：
 * - `e27`（`WifiOtaUtils.kt`）：9 字节 TCP 鉴权包
 * - `g17`（`WIFIOTAFileInfo.kt`）：固件头
 * - `rl1`（`FileServerFileResponder.kt`）：HTTP Range 与 `finish=1`
 */
data class WifiFirmwareHeader(
    val fileFormat: String,
    val productType: String,
    val crc32: String,
    val fileLength: String,
    val fileVersion: String,
    val manufactureCode: String,
    val fileType: String,
) {
    /** `e27` 取 `fileType` 的十六进制值作为鉴权包版本字节。 */
    val authVersionByte: Int?
        get() = fileType.toIntOrNull(16)
}

data class WifiByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long
        get() = endInclusive - start + 1L
}

object WifiOtaProtocol {
    const val TCP_PORT = 8888
    const val HTTP_PORT = 8080
    const val TCP_CONNECT_TIMEOUT_MS = 3_000
    const val CHUNK_SIZE = 8 * 1024
    const val TARGET_GROUP_OWNER_A = "192.168.7.1"
    const val TARGET_GROUP_OWNER_B = "192.168.6.1"

    private const val HEADER_SIZE = 64

    fun parseFirmwareHeader(bytes: ByteArray): WifiFirmwareHeader? {
        if (bytes.size < HEADER_SIZE) return null
        return WifiFirmwareHeader(
            fileFormat = bytes.hex(0, 3),
            productType = bytes.hex(3, 3),
            crc32 = bytes.hex(8, 4),
            fileLength = bytes.hex(12, 4),
            fileVersion = bytes.hex(16, 4),
            manufactureCode = bytes.hex(24, 2),
            fileType = bytes.hex(26, 1),
        )
    }

    fun firmwareVersionByte(bytes: ByteArray): Int? =
        parseFirmwareHeader(bytes)?.authVersionByte

    fun buildAuthPacket(firmwareVersion: Int, localIpLastOctet: Int): ByteArray {
        val version = firmwareVersion and 0xFF
        val lastOctet = localIpLastOctet and 0xFF
        val checksum = (0xF2 + version + lastOctet) and 0xFF
        return byteArrayOf(
            0x59,
            0x44,
            0xF0.toByte(),
            0x02,
            version.toByte(),
            lastOctet.toByte(),
            checksum.toByte(),
            0x4B,
            0x4A,
        )
    }

    /**
     * 仪表鉴权应答。原 App 接受以下两种 7 字节应答：
     *
     * - `59 44 F8 00 F8 4B 4A`
     * - `59 44 F9 00 F9 4B 4A`
     */
    fun isAuthAccepted(response: ByteArray): Boolean {
        if (response.size != 7) return false
        if (response[0] != 0x59.toByte() || response[1] != 0x44.toByte()) return false
        if (response[2] != 0xF8.toByte() && response[2] != 0xF9.toByte()) return false
        if (response[3] != 0x00.toByte()) return false
        if (response[4] != response[2]) return false
        return response[5] == 0x4B.toByte() && response[6] == 0x4A.toByte()
    }

    fun localIpLastOctet(address: String?): Int? {
        val normalized = address?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val octets = normalized.split('.')
        if (octets.size != 4) return null
        val values = octets.map { it.toIntOrNull() ?: return null }
        if (values.any { it !in 0..255 }) return null
        return values.last()
    }

    /**
     * Wi-Fi DVR 的 P2P 设备名：`YD` + BLE MAC 去掉分隔符后的末尾 8 位 + `DVR`。
     */
    fun dvrP2pName(bleMac: String?): String? {
        val compact = bleMac?.trim()
            .orEmpty()
            .replace(":", "")
            .replace("-", "")
            .uppercase()
        if (compact.length < 8) return null
        return "YD${compact.takeLast(8)}DVR"
    }

    /** 原 App 只接受这两个 GroupOwner 地址。 */
    fun isValidGroupOwner(address: String?): Boolean {
        val normalized = address?.trim().orEmpty()
        return normalized == TARGET_GROUP_OWNER_A || normalized == TARGET_GROUP_OWNER_B
    }

    /**
     * 解析 `Range: bytes=start-end`。
     *
     * 返回 `null` 表示该请求不是合法 Range；调用方应返回 416。
     * 未提供 Range 头时由调用方走完整文件分支。
     */
    fun parseRange(value: String?, totalBytes: Long): WifiByteRange? {
        if (totalBytes <= 0L) return null
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (!raw.startsWith("bytes=", ignoreCase = true)) return null
        val spec = raw.substringAfter('=').substringBefore(',').trim()
        val startText = spec.substringBefore('-', "").trim()
        val endText = spec.substringAfter('-', "").trim()
        val start = startText.toLongOrNull() ?: return null
        if (start < 0L || start >= totalBytes) return null
        val end = if (endText.isBlank()) {
            totalBytes - 1L
        } else {
            endText.toLongOrNull() ?: return null
        }
        if (end < start) return null
        return WifiByteRange(start, end.coerceAtMost(totalBytes - 1L))
    }

    fun queryContainsFinish(query: String?): Boolean {
        val raw = query?.trim().orEmpty()
        if (raw.isBlank()) return false
        return raw.split('&').any { pair ->
            val key = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "").trim()
            key == "finish" && value == "1"
        }
    }

    private fun ByteArray.hex(offset: Int, length: Int): String {
        val chars = CharArray(length * 2)
        var index = 0
        repeat(length) { delta ->
            val value = this[offset + delta].toInt() and 0xFF
            chars[index++] = HEX_DIGITS[value ushr 4]
            chars[index++] = HEX_DIGITS[value and 0x0F]
        }
        return chars.concatToString()
    }

    private const val HEX_DIGITS = "0123456789ABCDEF"
}
