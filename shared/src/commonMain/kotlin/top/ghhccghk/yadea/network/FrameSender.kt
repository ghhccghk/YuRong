package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.protocol.YadeaProtocol
import top.ghhccghk.yadea.protocol.readUInt32BE
import top.ghhccghk.yadea.protocol.u

/**
 * FrameSender - 协议帧收发器
 *
 * @param logger 日志回调，格式: [方向] 描述 + hex
 */
class FrameSender(
    private val tcp: TcpTransport,
    private val udp: UdpTransport,
    private val logger: (String) -> Unit = {}
) {
    private fun hex(data: ByteArray, maxLen: Int = 48): String {
        val sb = StringBuilder()
        for (i in 0 until minOf(data.size, maxLen)) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", data[i].toInt() and 0xFF))
        }
        if (data.size > maxLen) sb.append(" ...(${data.size}B)")
        return sb.toString()
    }

    private fun cmdName(cmd: Int): String = when (cmd) {
        YadeaProtocol.CMD_NAV_END -> "NAV_END"
        YadeaProtocol.CMD_IMAGE -> "IMAGE"
        YadeaProtocol.CMD_NAV_HINT -> "NAV_HINT"
        YadeaProtocol.CMD_HEARTBEAT -> "HEARTBEAT"
        YadeaProtocol.CMD_NAV_START -> "NAV_START"
        else -> "0x${String.format("%02X", cmd)}"
    }

    // ==================== TCP 发送 ====================

    suspend fun sendFrame(cmd: Int, payload: ByteArray) {
        val frame = YadeaProtocol.buildFrame(cmd, payload)
        logger("[TX] ${cmdName(cmd)} ${payload.size}B payload: ${hex(payload)}")
        logger("[TX] frame ${frame.size}B: ${hex(frame)}")
        tcp.send(frame)
    }

    suspend fun sendNavStart(packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE) {
        sendFrame(YadeaProtocol.CMD_NAV_START, YadeaProtocol.buildNavStartPayload(packetSize))
    }

    suspend fun sendNavEnd() {
        sendFrame(YadeaProtocol.CMD_NAV_END, YadeaProtocol.buildNavEndPayload())
    }

    suspend fun sendHeartbeat(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        sendFrame(YadeaProtocol.CMD_HEARTBEAT, YadeaProtocol.buildHeartbeatPayload(year, month, day, hour, minute, second))
    }

    suspend fun sendNavHint(payload: ByteArray) {
        sendFrame(YadeaProtocol.CMD_NAV_HINT, payload)
    }

    // ==================== UDP 发送 ====================

    suspend fun sendImage(
        image: ByteArray, imageSeq: Int,
        targetHost: String, targetPort: Int,
        packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE,
        timestamp: Long = 0L
    ): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, packetSize, timestamp)
        logger("[TX-UDP] IMAGE seq=$imageSeq size=${image.size}B packets=${frames.size} -> $targetHost:$targetPort")
        for (frame in frames) {
            udp.send(frame, targetHost, targetPort)
        }
        return frames.size
    }

    suspend fun broadcastImage(
        image: ByteArray, imageSeq: Int,
        broadcastPort: Int,
        packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE,
        timestamp: Long = 0L
    ): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, packetSize, timestamp)
        logger("[TX-UDP] BROADCAST seq=$imageSeq size=${image.size}B packets=${frames.size} port=$broadcastPort")
        for (frame in frames) {
            udp.broadcast(frame, broadcastPort)
        }
        return frames.size
    }

    // ==================== TCP 接收 ====================

    suspend fun receiveFrame(timeoutMs: Long = 3000): Triple<Int, ByteArray, Int>? {
        // 一次性读取所有可用数据
        val raw = tcp.receive(4096, timeoutMs) ?: run {
            logger("[RX] 超时/无数据 (${timeoutMs}ms)")
            return null
        }
        logger("[RX] 收到 ${raw.size}B: ${hex(raw)}")

        // 扫描帧魔数 0x59 0x44
        var pos = 0
        while (pos + 12 <= raw.size) {
            // 寻找 "YD" 魔数
            if (raw[pos] != 0x59.toByte() || raw[pos + 1] != 0x44.toByte()) {
                pos++
                continue
            }

            val totalLen = raw.readUInt32BE(pos + 3).toInt()
            if (totalLen < 8 || totalLen > 1_048_576) {
                pos++
                continue
            }

            val frameEnd = pos + totalLen + 7
            if (frameEnd > raw.size) {
                logger("[RX] 帧不完整: 需要 ${frameEnd}B, 只有 ${raw.size}B")
                return null
            }

            // 检查尾部 0x4B 0x4A
            if (raw[frameEnd - 2] != 0x4B.toByte() || raw[frameEnd - 1] != 0x4A.toByte()) {
                pos++
                continue
            }

            val payloadLen = raw.readUInt32BE(pos + 8).toInt()
            val cmd = raw[pos + 7].u()
            val payload = raw.copyOfRange(pos + 12, pos + 12 + payloadLen)

            logger("[RX] 帧解析成功: ${cmdName(cmd)} payload=${payload.size}B: ${hex(payload)}")
            return Triple(cmd, payload, frameEnd - pos)
        }

        logger("[RX] 未找到有效帧! raw=${hex(raw)}")
        return null
    }

    fun close() {
        tcp.close()
        udp.close()
    }
}

