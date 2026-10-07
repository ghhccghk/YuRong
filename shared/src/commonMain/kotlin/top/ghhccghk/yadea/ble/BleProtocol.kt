package top.ghhccghk.yadea.ble

/**
 * 雅迪 BLE 通信协议常量
 *
 * UUID 来自原始 APK 的 BleTileClient
 */
object BleProtocol {
    // ---- 服务/特征 UUID ----
    const val SERVICE_UUID = "7e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val CONTROL_UUID = "7e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val DATA_UUID = "7e400003-b5a3-f393-e0a9-e50e24dcca9e"
    const val STATUS_UUID = "7e400004-b5a3-f393-e0a9-e50e24dcca9e"
    const val RESULT_UUID = "7e400005-b5a3-f393-e0a9-e50e24dcca9e"

    // ---- 标准 CCCD ----
    const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    // ---- 传输参数 ----
    const val REQUESTED_MTU = 517
    const val TRANSPORT_HEADER_SIZE = 10
    const val SCAN_TIMEOUT_MS = 10_000L
    const val DATA_PACKET_PACING_MS = 18L
    const val DATA_WRITE_RETRY_MS = 40L
    const val DATA_WRITE_STALL_MS = 1500L
    const val RECONNECT_DELAY_MS = 1000L
    const val MAX_DATA_WRITE_RETRIES = 20

    // ---- 传输头字段偏移 ----
    const val HEADER_OFFSET_SEQUENCE = 0      // UInt16 LE
    const val HEADER_OFFSET_OFFSET = 2        // UInt32 LE
    const val HEADER_OFFSET_PAYLOAD_SIZE = 6  // UInt32 LE

    /**
     * 构建 BLE 数据传输头
     *
     * 头部 10 字节:
     * [0:2]  包序列号 (UInt16 LE)
     * [2:6]  数据偏移量 (UInt32 LE)
     * [6:10] 负载大小 (UInt32 LE)
     */
    fun buildTransportHeader(sequence: Int, offset: Int, payloadSize: Int): ByteArray {
        val header = ByteArray(TRANSPORT_HEADER_SIZE)
        writeUInt16LE(header, HEADER_OFFSET_SEQUENCE, sequence)
        writeUInt32LE(header, HEADER_OFFSET_OFFSET, offset)
        writeUInt32LE(header, HEADER_OFFSET_PAYLOAD_SIZE, payloadSize)
        return header
    }

    /**
     * 解析 BLE 数据传输头
     * @return Triple(sequence, offset, payloadSize)
     */
    fun parseTransportHeader(data: ByteArray): Triple<Int, Int, Int> {
        if (data.size < TRANSPORT_HEADER_SIZE) return Triple(0, 0, 0)
        val seq = readUInt16LE(data, HEADER_OFFSET_SEQUENCE)
        val offset = readUInt32LE(data, HEADER_OFFSET_OFFSET)
        val payloadSize = readUInt32LE(data, HEADER_OFFSET_PAYLOAD_SIZE)
        return Triple(seq, offset, payloadSize)
    }

    /**
     * 构建完整的 BLE 数据包 (头 + 负载)
     */
    fun buildDataPacket(sequence: Int, offset: Int, payload: ByteArray): ByteArray {
        val header = buildTransportHeader(sequence, offset, payload.size)
        return header + payload
    }

    // ---- 字节工具 ----
    fun writeUInt16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = value.toByte()
        buf[offset + 1] = (value shr 8).toByte()
    }

    fun writeUInt32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = value.toByte()
        buf[offset + 1] = (value shr 8).toByte()
        buf[offset + 2] = (value shr 16).toByte()
        buf[offset + 3] = (value shr 24).toByte()
    }

    fun readUInt16LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun readUInt32LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }
}
