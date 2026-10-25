package top.ghhccghk.yadea.instrumentskin

internal object InstrumentCrc {

    fun crc16Modbus(bytes: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc
    }

    /**
     * 皮肤包目录使用的 CRC-CCITT（poly=0x1021，初值 0），
     * 与仪表传输报文使用的 Modbus CRC 不同。
     */
    fun crc16Ccitt(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        require(offset in 0..bytes.size && length >= 0 && offset + length <= bytes.size)
        var crc = 0
        for (index in offset until offset + length) {
            crc = crc xor ((bytes[index].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    fun sum8(bytes: ByteArray): Int {
        var sum = 0
        for (byte in bytes) {
            sum = (sum + (byte.toInt() and 0xFF)) and 0xFF
        }
        return sum
    }

    fun crc32(bytes: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (byte in bytes) {
            crc = crc xor (byte.toLong() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1L) != 0L) {
                    (crc ushr 1) xor 0xEDB88320L
                } else {
                    crc ushr 1
                }
            }
        }
        return crc xor 0xFFFFFFFFL
    }

    fun crc32Hex(bytes: ByteArray): String =
        crc32(bytes).toString(16).uppercase().padStart(8, '0')
}
