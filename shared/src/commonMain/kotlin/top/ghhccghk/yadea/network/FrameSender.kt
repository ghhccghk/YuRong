package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.protocol.YadeaProtocol

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
    private fun hex(data: ByteArray, maxLen: Int = 32): String {
        val sb = StringBuilder()
        for (i in 0 until minOf(data.size, maxLen)) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02X", data[i].toInt() and 0xFF))
        }
        if (data.size > maxLen) sb.append(" ...(${data.size}B)")
        return sb.toString()
    }

    private fun cmdName(cmd: Int): String = when (cmd) {
        YadeaProtocol.CMD_NAV_END -> "NAV_END(0x02)"
        YadeaProtocol.CMD_IMAGE -> "IMAGE(0x12)"
        YadeaProtocol.CMD_NAV_HINT -> "NAV_HINT(0x14)"
        YadeaProtocol.CMD_HEARTBEAT -> "HEARTBEAT(0x1F)"
        YadeaProtocol.CMD_NAV_START -> "NAV_START(0x20)"
        else -> "CMD=0x${String.format("%02X", cmd)}"
    }

    // ==================== TCP 发送 ====================

    suspend fun sendFrame(cmd: Int, payload: ByteArray) {
        val frame = YadeaProtocol.buildFrame(cmd, payload)
        logger("[TX] ${cmdName(cmd)} payload=${payload.size}B frame=${frame.size}B hex=${hex(frame)}")
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
        logger("[TX-UDP] IMAGE seq=$imageSeq total=${image.size}B packets=${frames.size} → $targetHost:$targetPort")
        if (frames.isNotEmpty()) {
            logger("[TX-UDP] 首包 hex=${hex(frames[0])}")
        }
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
        logger("[TX-UDP] BROADCAST seq=$imageSeq total=${image.size}B packets=${frames.size} port=$broadcastPort")
        for (frame in frames) {
            udp.broadcast(frame, broadcastPort)
        }
        return frames.size
    }

    // ==================== TCP 接收 ====================

    suspend fun receiveFrame(timeoutMs: Long = 3000): Triple<Int, ByteArray, Int>? {
        val header = tcp.readExact(12, timeoutMs) ?: run {
            logger("[RX] 超时/无数据 (等待 ${timeoutMs}ms)")
            return null
        }
        logger("[RX] 帧头 12B hex=${hex(header)}")

        val payloadLen = header.copyOfRange(8, 12).let { bytes ->
            ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
        }
        logger("[RX] payloadLen=$payloadLen")

        val remaining = tcp.readExact(payloadLen + 3, timeoutMs) ?: run {
            logger("[RX] 读取剩余 ${payloadLen + 3}B 失败")
            return null
        }
        logger("[RX] 剩余 ${remaining.size}B hex=${hex(remaining)}")

        val fullFrame = header + remaining
        val result = YadeaProtocol.parseFrame(fullFrame)
        if (result != null) {
            val (cmd, payload, frameLen) = result
            logger("[RX] 解析成功: ${cmdName(cmd)} payload=${payload.size}B frameLen=$frameLen hex=${hex(payload)}")
        } else {
            logger("[RX] 解析失败! fullFrame=${fullFrame.size}B hex=${hex(fullFrame)}")
        }
        return result
    }

    fun close() {
        tcp.close()
        udp.close()
    }
}
