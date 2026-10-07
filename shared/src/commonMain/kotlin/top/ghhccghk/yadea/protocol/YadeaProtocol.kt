package top.ghhccghk.yadea.protocol

import top.ghhccghk.yadea.model.NavDisplayConfig

/**
 * 雅迪仪表投屏通信协议
 *
 * 帧结构 (buildFrame):
 * `
 * 偏移  长度  内容
 * 0     3     魔数: 0x59 0x44 0x3C ("YD<")
 * 3     4     总长度 (Big-Endian, = payload.length + 8)
 * 7     1     命令字
 * 8     4     payload 长度 (Big-Endian)
 * 12    N     payload
 * 12+N  1     校验和
 * 13+N  2     尾部: 0x4B 0x4A ("KJ")
 * `
 */
object YadeaProtocol {

    // ---- 帧头/帧尾 ----
    private val MAGIC = byteArrayOf(0x59, 0x44, 0x3C)  // "YD<"
    private val FOOTER = byteArrayOf(0x4B, 0x4A)       // "KJ"

    // ---- 命令字 ----
    const val CMD_NAV_END = 0x02
    const val CMD_IMAGE = 0x12
    const val CMD_NAV_HINT = 0x14
    const val CMD_HEARTBEAT = 0x1F
    const val CMD_NAV_START = 0x20

    // ---- 图像分包头部长度 ----
    const val IMAGE_PACKET_HEADER_SIZE = 20

    // ---- 默认参数 ----
    const val DEFAULT_PACKET_SIZE = 1024
    const val MAX_JPEG_BYTES = 245_760  // 240KB

    // ==================== 帧构建 ====================

    /**
     * 构建完整的协议帧
     * @param cmd 命令字
     * @param payload 数据负载
     * @return 完整帧字节数组
     */
    fun buildFrame(cmd: Int, payload: ByteArray): ByteArray {
        val totalLen = payload.size + 8
        val frame = ByteArray(payload.size + 15)

        // 魔数
        MAGIC.copyInto(frame, 0)
        // 总长度 (不含魔数和自身，= cmd(1) + len(4) + payload + checksum(1) + footer(2) - 2)
        frame.writeUInt32BE(3, totalLen)
        // 命令字
        frame[7] = cmd.toByte()
        // payload 长度
        frame.writeUInt32BE(8, payload.size)
        // payload
        payload.copyInto(frame, 12)

        val lastIndex = frame.lastIndex
        // 校验和 (倒数第3字节)
        frame[lastIndex - 2] = checksum(frame, lastIndex - 2)
        // 尾部
        frame[lastIndex - 1] = FOOTER[0]
        frame[lastIndex] = FOOTER[1]

        return frame
    }

    /**
     * 从字节数组中解析帧
     * @return Triple(命令字, payload, 帧总长度) 或 null
     */
    fun parseFrame(data: ByteArray): Triple<Int, ByteArray, Int>? {
        if (data.size < 15) return null
        // 校验魔数
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1] || data[2] != MAGIC[2]) return null

        val payloadLen = data.readUInt32BE(8).toInt()
        val frameLen = payloadLen + 15
        if (data.size < frameLen) return null

        // 校验尾部
        val lastIdx = frameLen - 1
        if (data[lastIdx - 1] != FOOTER[0] || data[lastIdx] != FOOTER[1]) return null

        val cmd = data[7].u()
        val payload = data.copyOfRange(12, 12 + payloadLen)
        return Triple(cmd, payload, frameLen)
    }

    // ==================== 图像分包 ====================

    /**
     * 构建图像数据包 payload (20B头 + 数据分片)
     */
    fun buildImagePacketPayload(
        crc16: Int,
        chunkLen: Int,
        imageSeq: Int,
        totalSize: Int,
        offset: Int,
        chunk: ByteArray,
        timestamp: Long = 0L
    ): ByteArray {
        val payload = ByteArray(IMAGE_PACKET_HEADER_SIZE + chunk.size)
        payload.writeUInt16LE(0, crc16)
        payload.writeUInt16BE(2, chunkLen)
        payload.writeUInt32BE(4, imageSeq)
        payload.writeUInt32BE(8, totalSize)
        payload.writeUInt32BE(12, offset)
        payload.writeUInt32BE(16, (timestamp and 0xFFFFFFFFL).toInt())
        chunk.copyInto(payload, IMAGE_PACKET_HEADER_SIZE)
        return payload
    }

    /**
     * 将图像数据分包为多个协议帧
     */
    fun buildImageFrames(
        image: ByteArray,
        imageSeq: Int,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        timestamp: Long = 0L
    ): List<ByteArray> {
        val crc = crc16Modbus(image)
        val frames = mutableListOf<ByteArray>()
        var offset = 0

        while (offset < image.size) {
            val len = minOf(packetSize, image.size - offset)
            val chunk = image.copyOfRange(offset, offset + len)
            val payload = buildImagePacketPayload(crc, len, imageSeq, image.size, offset, chunk, timestamp)
            frames.add(buildFrame(CMD_IMAGE, payload))
            offset += len
        }
        return frames
    }

    // ==================== 导航控制 ====================

    /**
     * 导航开始 payload: 协商分包大小
     */
    fun buildNavStartPayload(packetSize: Int = DEFAULT_PACKET_SIZE): ByteArray {
        val b = ByteArray(8) { 0xFF.toByte() }
        b.writeUInt16BE(0, packetSize)
        b[2] = 0xF0.toByte()
        b[3] = 0xFF.toByte()
        b.writeUInt32BE(4, packetSize)
        return b
    }

    /**
     * 导航结束 payload
     */
    fun buildNavEndPayload(): ByteArray = byteArrayOf(2, -1, -1, -1, -1, -1, -1, -1)

    /**
     * 心跳 payload: 当前时间
     * @param year 年 (如 2026)
     * @param month 月 (1-12)
     * @param day 日 (1-31)
     * @param hour 时 (0-23)
     * @param minute 分 (0-59)
     * @param second 秒 (0-59)
     */
    fun buildHeartbeatPayload(
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int, second: Int
    ): ByteArray = byteArrayOf(
        (year - 2000).toByte(),
        month.toByte(),
        day.toByte(),
        hour.toByte(),
        minute.toByte(),
        second.toByte(),
        0xFF.toByte(),
        0xFF.toByte()
    )

    /**
     * 解析仪表返回的显示配置
     */
    fun parseNavDisplayConfig(data: ByteArray): NavDisplayConfig {
        if (data.size < 8) return NavDisplayConfig()

        val rawWidth = data.readUInt16BE(0)
        val rawHeight = data.readUInt16BE(2)

        val width = if (rawWidth in 320..4096) rawWidth else 960
        val height = if (rawHeight in 160..2160) rawHeight else 368

        return NavDisplayConfig(
            width = width,
            height = height,
            rotation = data[4].u(),
            offsetX = data[6].u(),
            packetMultiplier = data[7].u().coerceAtLeast(1)
        )
    }

    // ==================== 校验 ====================

    /**
     * CRC16-Modbus
     */
    fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor byte.u()
            repeat(8) {
                crc = if (crc and 1 != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }

    /**
     * 累加校验和 (取低8位)
     */
    fun checksum(data: ByteArray, endExclusive: Int): Byte {
        var sum = 0
        for (i in 0 until endExclusive) {
            sum += data[i].u()
        }
        return (sum and 0xFF).toByte()
    }
}
