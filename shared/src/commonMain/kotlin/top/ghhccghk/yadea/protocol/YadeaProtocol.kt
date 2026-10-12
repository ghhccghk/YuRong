package top.ghhccghk.yadea.protocol

import top.ghhccghk.yadea.model.NavDisplayConfig

/**
 * 雅迪投屏协议 — 严格按 YadeaHook/docs/p2p-data-protocol.md 实现
 *
 * 两层帧结构:
 * ```
 * 外层: 59 44 | 3C | INNER_LEN(BE32) | INNER_PACKET | SUM8 | 4B 4A
 * 内层: TYPE(1B) | PAYLOAD_LEN(BE32) | PAYLOAD
 * ```
 *
 * 校验和 = SUM8(3C + INNER_LEN + INNER_PACKET)，不覆盖 5944 和 4B4A
 */
object YadeaProtocol {

    // ==================== 内层命令字 ====================
    const val CMD_NAV_START = 0x10        // 导航启动请求
    const val CMD_NAV_END = 0x13          // 导航结束请求 (payload 为空!)
    const val CMD_NAV_INFO = 0x14         // 导航信息 (64字节二进制)
    const val CMD_LANE_INFO = 0x15        // 车道信息
    const val CMD_SEGMENT_PROGRESS = 0x16 // 路段进度
    const val CMD_IMAGE_CHUNK = 0x1A      // 导航图片块 ← 不是 0x12!
    const val CMD_HEARTBEAT = 0x1F        // 时间同步/心跳

    const val CMD_NAV_START_RESPONSE = 0x20
    const val CMD_NAV_END_RESPONSE = 0x23
    const val CMD_STATUS_RESPONSE = 0x25
    const val CMD_RATE_RESPONSE = 0x26

    // ==================== 参数 ====================
    const val CHUNK_SIZE = 1434           // 0x59A, 图片分片大小
    const val IMAGE_TARGET_MIN = 30_720   // 0x7800, 目标最小 30KB
    const val IMAGE_TARGET_MAX = 51_200   // 0xC800, 目标最大 50KB
    const val IMAGE_HARD_LIMIT = 81_920   // 0x14000, 硬上限 80KB

    // 外层帧常量
    private val FRAME_HEAD = byteArrayOf(0x59, 0x44) // "YD"
    private val PROTOCOL_ID: Byte = 0x3C
    private val FRAME_TAIL = byteArrayOf(0x4B, 0x4A) // "KJ"

    // ==================== 外层帧构建 ====================

    /**
     * 构建完整外层帧: 5944 + 3C + INNER_LEN(BE32) + INNER_PACKET + SUM8 + 4B4A
     *
     * INNER_LEN = innerPacket.size + 3
     * SUM8 覆盖: 3C + INNER_LEN + INNER_PACKET
     */
    fun buildFrame(innerPacket: ByteArray): ByteArray {
        val innerLen = innerPacket.size + 3
        // 外层总长 = 2(head) + 1(protoId) + 4(innerLen) + innerPacket + 1(checksum) + 2(tail)
        val frame = ByteArray(innerPacket.size + 10)
        var pos = 0

        // 帧头
        FRAME_HEAD.copyInto(frame, pos); pos += 2
        // 协议ID
        frame[pos] = PROTOCOL_ID; pos += 1
        // 内层长度 (BE32)
        frame.writeUInt32BE(pos, innerLen); pos += 4
        // 内层包
        innerPacket.copyInto(frame, pos); pos += innerPacket.size

        // 校验和: SUM8(protocolId + innerLen + innerPacket) = 从 offset 2 到 offset pos-1
        var sum = 0
        for (i in 2 until pos) sum += frame[i].u()
        frame[pos] = (sum and 0xFF).toByte(); pos += 1

        // 帧尾
        FRAME_TAIL.copyInto(frame, pos)

        return frame
    }

    /**
     * 构建内层包: TYPE(1B) + PAYLOAD_LEN(BE32) + PAYLOAD
     */
    fun buildInnerPacket(type: Int, payload: ByteArray): ByteArray {
        val inner = ByteArray(5 + payload.size)
        inner[0] = type.toByte()
        inner.writeUInt32BE(1, payload.size)
        payload.copyInto(inner, 5)
        return inner
    }

    /**
     * 构建完整帧（便捷方法）: 内层 + 外层
     */
    fun buildFullFrame(type: Int, payload: ByteArray): ByteArray {
        return buildFrame(buildInnerPacket(type, payload))
    }

    // ==================== 帧解析 ====================

    /**
     * 解析外层帧 → 返回 (innerPacket, frameLength)
     */
    fun parseOuterFrame(data: ByteArray): Pair<ByteArray, Int>? {
        if (data.size < 12) return null
        if (data[0] != 0x59.toByte() || data[1] != 0x44.toByte()) return null

        val innerLen = data.readUInt32BE(3).toInt()
        if (innerLen < 3 || innerLen > 1_048_576) return null

        val frameEnd = 2 + 1 + 4 + innerLen - 3 + 1 + 2  // head+id+len+inner+sum+tail
        // 实际: head(2) + id(1) + len(4) + innerPacket(innerLen-3) + sum(1) + tail(2) = innerLen + 7
        val totalFrameLen = innerLen + 7

        if (data.size < totalFrameLen) return null

        // 校验帧尾
        if (data[totalFrameLen - 2] != 0x4B.toByte() || data[totalFrameLen - 1] != 0x4A.toByte()) return null

        // 校验和验证 (不拒绝，仅校验)
        val checksumStart = 2 // 从 protocolId 开始
        val checksumEnd = totalFrameLen - 3 // 到 innerPacket 结束
        var sum = 0
        for (i in checksumStart until checksumEnd) sum += data[i].u()
        val expected = data[totalFrameLen - 3].u()

        val innerPacket = data.copyOfRange(7, totalFrameLen - 3)
        return Pair(innerPacket, totalFrameLen)
    }

    /**
     * 解析内层包 → Triple(type, payload, innerPacketLength)
     */
    fun parseInnerPacket(inner: ByteArray): Triple<Int, ByteArray, Int>? {
        if (inner.size < 5) return null
        val type = inner[0].u()
        val payloadLen = inner.readUInt32BE(1).toInt()
        if (payloadLen < 0 || 5 + payloadLen > inner.size) return null
        val payload = inner.copyOfRange(5, 5 + payloadLen)
        return Triple(type, payload, 5 + payloadLen)
    }

    /**
     * 完整解析: 外层 → 内层
     * @return Triple(type, payload, frameLength)
     */
    fun parseFrame(data: ByteArray): Triple<Int, ByteArray, Int>? {
        val (inner, frameLen) = parseOuterFrame(data) ?: return null
        val (type, payload, _) = parseInnerPacket(inner) ?: return null
        return Triple(type, payload, frameLen)
    }

    // ==================== 导航控制 payload ====================

    /**
     * 0x10 导航启动请求 payload: 8 字节固定
     * `05 9A F0 FF 00 00 05 9A` (packetSize=1434)
     */
    fun buildNavStartPayload(packetSize: Int = CHUNK_SIZE): ByteArray {
        return byteArrayOf(
            0x05, 0x9A.toByte(), 0xF0.toByte(), 0xFF.toByte(),
            0x00, 0x00, 0x05, 0x9A.toByte()
        )
    }

    /**
     * 0x13 导航结束请求 payload: 空 (长度 0)
     */
    fun buildNavEndPayload(): ByteArray = ByteArray(0)

    /**
     * 0x1F 心跳 payload: 8 字节
     * [year%100, month, day, hour, minute, second, FF, FF]
     */
    fun buildHeartbeatPayload(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int
    ): ByteArray = byteArrayOf(
        (year % 100).toByte(), month.toByte(), day.toByte(),
        hour.toByte(), minute.toByte(), second.toByte(),
        0xFF.toByte(), 0xFF.toByte()
    )

    /**
     * 0x14 导航信息 payload: 64 字节固定二进制布局
     *
     * [0]    iconType
     * [1:5]  FF FF FF FF
     * [5:7]  turningDistanceA (BE16, <1000 直接, >=1000 /100)
     * [7:9]  turningDistanceB (BE16, <1000 重复, >=1000 FFFF)
     * [9:11] remainingMileageA (BE16)
     * [11:13] remainingMileageB (BE16)
     * [13:19] FF x6
     * [19]   arrivalHour
     * [20]   arrivalMin
     * [21]   arrivalSec
     * [22:24] arrivalTime (BE16)
     * [24:32] FF x8
     * [32]   marker 0x7F
     * [33]   roadName length
     * [34..] roadName UTF-8
     * 补 0xFF 到 64 字节
     */
    fun buildNavInfoPayload(
        iconType: Int = 0,
        turningDistance: Int = 0,
        remainingMileage: Int = 0,
        arrivalHour: Int = 0,
        arrivalMin: Int = 0,
        arrivalSec: Int = 0,
        arrivalTime: Int = 0,
        roadName: String = ""
    ): ByteArray {
        val b = ByteArray(64) { 0xFF.toByte() }
        b[0] = (iconType and 0xFF).toByte()
        // [1:5] reserved FF FF FF FF (already filled)

        // turningDistance A
        if (turningDistance < 1000) {
            b.writeUInt16BE(5, turningDistance)
        } else {
            b.writeUInt16BE(5, turningDistance / 100)
        }
        // turningDistance B
        if (turningDistance < 1000) {
            b.writeUInt16BE(7, turningDistance)
        } else {
            b.writeUInt16BE(7, 0xFFFF)
        }

        // remainingMileage A
        if (remainingMileage < 1000) {
            b.writeUInt16BE(9, remainingMileage)
        } else {
            b.writeUInt16BE(9, remainingMileage / 100)
        }
        // remainingMileage B
        if (remainingMileage < 1000) {
            b.writeUInt16BE(11, remainingMileage)
        } else {
            b.writeUInt16BE(11, 0xFFFF)
        }

        // [13:19] reserved FF x6 (already filled)
        b[19] = (arrivalHour and 0xFF).toByte()
        b[20] = (arrivalMin and 0xFF).toByte()
        b[21] = (arrivalSec and 0xFF).toByte()
        b.writeUInt16BE(22, arrivalTime)

        // [24:32] reserved FF x8 (already filled)
        b[32] = 0x7F.toByte() // marker

        val nameBytes = roadName.encodeToByteArray()
        val len = minOf(29, nameBytes.size) // 34 + len <= 63, 即 len <= 29
        b[33] = (len and 0xFF).toByte()
        nameBytes.copyInto(b, 34, 0, len)

        return b
    }

    // ==================== 图片分块 ====================

    /**
     * 0x1A 图片块 payload:
     * [0:2]   CRC16-IBM/ARC (低字节在前)
     * [2:4]   chunkLen (BE16)
     * [4:8]   imageSeq (BE32)
     * [8:12]  totalSize (BE32)
     * [12:16] offset = chunkIndex * 1434 (BE32)
     * [16:20] timestamp (BE32)
     * [20:]   chunk bytes
     */
    fun buildImageChunkPayload(
        crc16: Int, chunkLen: Int, imageSeq: Int,
        totalSize: Int, chunkIndex: Int, chunk: ByteArray,
        timestamp: Long = 0L
    ): ByteArray {
        val payload = ByteArray(20 + chunk.size)
        // CRC16 低字节在前
        payload[0] = (crc16 and 0xFF).toByte()
        payload[1] = ((crc16 shr 8) and 0xFF).toByte()
        payload.writeUInt16BE(2, chunkLen)
        payload.writeUInt32BE(4, imageSeq)
        payload.writeUInt32BE(8, totalSize)
        payload.writeUInt32BE(12, chunkIndex * CHUNK_SIZE) // offset = chunkIndex * 1434
        payload.writeUInt32BE(16, (timestamp and 0xFFFFFFFFL).toInt())
        chunk.copyInto(payload, 20)
        return payload
    }

    /**
     * 将图片分块为完整帧列表
     */
    fun buildImageFrames(
        image: ByteArray, imageSeq: Int,
        timestamp: Long = 0L
    ): List<ByteArray> {
        val crc = crc16Ibm(image)
        val frames = mutableListOf<ByteArray>()
        var chunkIndex = 0
        var offset = 0

        while (offset < image.size) {
            val len = minOf(CHUNK_SIZE, image.size - offset)
            val chunk = image.copyOfRange(offset, offset + len)
            val payload = buildImageChunkPayload(crc, len, imageSeq, image.size, chunkIndex, chunk, timestamp)
            frames.add(buildFullFrame(CMD_IMAGE_CHUNK, payload))
            offset += len
            chunkIndex++
        }
        return frames
    }

    // ==================== CRC16-IBM/ARC ====================

    /**
     * CRC16-IBM/ARC (用于图片整图校验)
     * poly=0xA001, init=0x0000
     */
    fun crc16Ibm(data: ByteArray): Int {
        var crc = 0x0000
        for (byte in data) {
            crc = crc xor byte.u()
            repeat(8) {
                crc = if (crc and 1 != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        return crc and 0xFFFF
    }

    // ==================== 显示配置解析 ====================

    /**
     * 0x20 启动响应 payload:
     * [0:2] displayWidth (BE16)
     * [2:4] displayHeight (BE16)
     * [4]   formatCode (>2 重置为 0)
     * [5]   未读取
     * [6]   screenType (低4位)
     * [7]   rate (1..30, 否则 10)
     *
     * 默认: 800x480, format 0, rate 10
     */
    fun parseNavDisplayConfig(data: ByteArray): NavDisplayConfig {
        if (data.size < 8) return NavDisplayConfig() // 默认 800x480
        val width = data.readUInt16BE(0).let { if (it in 320..4096) it else 800 }
        val height = data.readUInt16BE(2).let { if (it in 160..2160) it else 480 }
        val format = data[4].u().let { if (it > 2) 0 else it }
        val screenType = data[6].u() and 0x0F
        val rate = data[7].u().let { if (it in 1..30) it else 10 }

        return NavDisplayConfig(
            width = width,
            height = height,
            imageFormat = format,
            capabilities = screenType,
            maxFps = rate
        )
    }

    // ==================== 字节工具 ====================
}
