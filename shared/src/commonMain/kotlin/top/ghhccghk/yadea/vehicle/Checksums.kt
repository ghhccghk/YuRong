package top.ghhccghk.yadea.vehicle

/**
 * 车辆协议涉及的校验与编码算法。
 *
 * 这些算法都是纯字节运算，不依赖任何平台 API，因此放在 `commonMain`。
 */
internal object Checksums {

    /** 8 位算术和。车辆控制帧的 SUM8。 */
    fun sum8(bytes: ByteArray): Int {
        var sum = 0
        for (b in bytes) sum += b.toInt() and 0xFF
        return sum and 0xFF
    }

    /**
     * CRC16-XMODEM：poly `0x1021`，init `0x0000`，输入输出不反转，final XOR `0x0000`。
     *
     * 滑板车状态/控制命令使用。对拍向量：`"123456789"` → `0x31C3`。
     */
    fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /**
     * CRC16-XMODEM 的十六进制串。
     *
     * 后处理：`toHexString` 后**奇数长度补 `0`**；若结果长度恰好为 2，再前补 `"00"`。
     */
    fun crc16XmodemHex(data: ByteArray): String {
        var s = crc16Xmodem(data).toString(16)
        if (s.length % 2 == 1) s = "0$s"
        if (s.length == 2) s = "00$s"
        return s
    }

    /**
     * CRC32-MPEG-2：poly `0x04C11DB7`，init `0xFFFFFFFF`，MSB-first，final XOR `0`。
     *
     * OTA / 升级链路使用。返回无符号 32 位的 `Long`。
     */
    fun crc32Mpeg2(data: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF).toLong() shl 24)
            repeat(8) {
                crc = if (crc and 0x80000000L != 0L) {
                    ((crc shl 1) xor 0x04C11DB7L) and 0xFFFFFFFFL
                } else {
                    (crc shl 1) and 0xFFFFFFFFL
                }
            }
        }
        return crc and 0xFFFFFFFFL
    }

    /**
     * 滑板车整帧编码：`out[i] = frame[i] xor ((i + 10) and 0xFF)`。
     *
     * **CRC 字节也参与 XOR**，所以必须在拼好含 CRC 的完整帧之后再调用。
     * 查询与 OTA 命令走 `getDataNoDencry` 分支，**不做**这一步。
     */
    fun scooterEncrypt(frame: ByteArray): ByteArray =
        ByteArray(frame.size) { i ->
            (frame[i].toInt() xor ((i + 10) and 0xFF)).toByte()
        }

    /**
     * 逐字节 XOR 折叠，握手帧的最后一个字节用它。
     *
     * @param len 参与折叠的字节数（握手帧固定为 31）
     */
    fun xorFold(data: ByteArray, len: Int): Byte {
        var acc = 0
        val end = len.coerceAtMost(data.size)
        for (i in 0 until end) acc = acc xor (data[i].toInt() and 0xFF)
        return acc.toByte()
    }
}
