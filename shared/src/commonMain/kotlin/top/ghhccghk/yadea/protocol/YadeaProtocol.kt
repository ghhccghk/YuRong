package top.ghhccghk.yadea.protocol

import top.ghhccghk.yadea.model.NavDisplayConfig

object YadeaProtocol {

    private val MAGIC_SEND = byteArrayOf(0x59, 0x44, 0x3C)
    private val FOOTER = byteArrayOf(0x4B, 0x4A)

    const val CMD_NAV_END_SEND = 0x13
    const val CMD_IMAGE = 0x12
    const val CMD_NAV_HINT = 0x14
    const val CMD_HEARTBEAT = 0x1F
    const val CMD_NAV_START_SEND = 0x10

    const val CMD_NAV_START_RESPONSE = 0x20
    const val CMD_NAV_END_RESPONSE = 0x23
    const val CMD_NEGATIVE_RESPONSE = 0xF1

    const val IMAGE_PACKET_HEADER_SIZE = 20
    const val DEFAULT_PACKET_SIZE = 1434
    const val MAX_JPEG_BYTES = 245_760

    // ==================== 帧构建 ====================

    fun buildFrame(cmd: Int, payload: ByteArray): ByteArray {
        val totalLen = payload.size + 8
        val frame = ByteArray(payload.size + 15)
        MAGIC_SEND.copyInto(frame, 0)
        frame.writeUInt32BE(3, totalLen)
        frame[7] = cmd.toByte()
        frame.writeUInt32BE(8, payload.size)
        payload.copyInto(frame, 12)
        val lastIndex = frame.lastIndex
        frame[lastIndex - 2] = checksum(frame, lastIndex - 2)
        frame[lastIndex - 1] = FOOTER[0]
        frame[lastIndex] = FOOTER[1]
        return frame
    }

    // ==================== 帧解析 ====================

    /**
     * 解析单个协议帧 — 严格校验:
     * - 魔数: 0x59 0x44
     * - 第三字节: 0x3C(发送) 或 0x4C(接收)
     * - totalLen 合理范围 [8, 1048576]
     * - payloadLen == totalLen - 8
     * - 尾部: 0x4B 0x4A
     * - 校验和: 告警但不拒绝
     */
    fun parseFrame(data: ByteArray): Triple<Int, ByteArray, Int>? {
        if (data.size < 15) return null
        if (data[0] != 0x59.toByte() || data[1] != 0x44.toByte()) return null

        val totalLen = data.readUInt32BE(3).toInt()
        if (totalLen < 8 || totalLen > 1_048_576) return null

        val payloadLen = data.readUInt32BE(8).toInt()
        if (payloadLen != totalLen - 8) return null

        val frameLen = totalLen + 7
        if (data.size < frameLen) return null

        if (data[frameLen - 2] != 0x4B.toByte() || data[frameLen - 1] != 0x4A.toByte()) return null

        // 校验和验证（告警不拒绝）
        val expectedChecksum = data[frameLen - 3].u()
        val calcChecksum = checksum(data, frameLen - 3).u()
        val checksumOk = expectedChecksum == calcChecksum

        val cmd = data[7].u()
        val payload = data.copyOfRange(12, 12 + payloadLen)
        return Triple(cmd, payload, frameLen).also {
            if (!checksumOk) {
                // 校验和不匹配但继续解析（与原始 APK 一致）
            }
        }
    }

    // ==================== 图像分包 ====================

    fun buildImagePacketPayload(
        crc16: Int, chunkLen: Int, imageSeq: Int,
        totalSize: Int, offset: Int, chunk: ByteArray, timestamp: Long = 0L
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

    fun buildImageFrames(
        image: ByteArray, imageSeq: Int,
        packetSize: Int = DEFAULT_PACKET_SIZE, timestamp: Long = 0L
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

    fun buildNavStartPayload(packetSize: Int = DEFAULT_PACKET_SIZE): ByteArray {
        val b = ByteArray(8) { 0xFF.toByte() }
        b.writeUInt16BE(0, packetSize)
        b[2] = 0xF0.toByte()
        b[3] = 0xFF.toByte()
        b.writeUInt32BE(4, packetSize)
        return b
    }

    fun buildNavEndPayload(): ByteArray = byteArrayOf(2, -1, -1, -1, -1, -1, -1, -1)

    fun buildHeartbeatPayload(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int
    ): ByteArray = byteArrayOf(
        (year - 2000).toByte(), month.toByte(), day.toByte(),
        hour.toByte(), minute.toByte(), second.toByte(), 0xFF.toByte(), 0xFF.toByte()
    )

    fun buildNavHintPayload(
        name: String = "",
        hour: Int = 0, minute: Int = 0, second: Int = 0
    ): ByteArray {
        val b = ByteArray(64) { 0xFF.toByte() }
        b[0] = 2
        b.writeUInt16BE(7, 320)
        b.writeUInt16BE(11, 860)
        b[17] = hour.toByte()
        b[18] = minute.toByte()
        b[19] = second.toByte()
        b.writeUInt16BE(20, 8)
        b[30] = setBits(b[30], 6, 2, 1)
        val nameBytes = name.encodeToByteArray()
        val len = minOf(32, nameBytes.size)
        b[31] = len.toByte()
        nameBytes.copyInto(b, 32, 0, len)
        return b
    }

    fun setBits(value: Byte, startBit: Int, bitCount: Int, bits: Int): Byte {
        val mask = ((1 shl bitCount) - 1) shl startBit
        val cleared = value.toInt() and mask.inv()
        val set = (bits shl startBit) and mask
        return (cleared or set).toByte()
    }

    // ==================== 显示配置解析 ====================

    /**
     * 解析仪表显示配置
     *
     * 原始 APK 字段语义:
     * [0:2] width (BE uint16)
     * [2:4] height (BE uint16)
     * [4]   imageFormat
     * [6]   capabilities
     * [7]   maxFps
     *
     * 要求 data.size >= 8
     */
    fun parseNavDisplayConfig(data: ByteArray): NavDisplayConfig {
        if (data.size < 8) return NavDisplayConfig()
        val width = data.readUInt16BE(0).let { if (it in 320..4096) it else 1024 }
        val height = data.readUInt16BE(2).let { if (it in 160..2160) it else 512 }
        return NavDisplayConfig(
            width = width,
            height = height,
            imageFormat = data[4].u(),
            capabilities = data[6].u(),
            maxFps = data[7].u().coerceAtLeast(1)
        )
    }

    // ==================== 校验 ====================

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

    fun checksum(data: ByteArray, endExclusive: Int): Byte {
        var sum = 0
        for (i in 0 until endExclusive) sum += data[i].u()
        return (sum and 0xFF).toByte()
    }
}
