package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.protocol.YadeaProtocol
import top.ghhccghk.yadea.protocol.readUInt32BE
import top.ghhccghk.yadea.protocol.u

class FrameSender(
    private val tcp: TcpTransport,
    private val udp: UdpTransport,
    private val logger: (String) -> Unit = {}
) {
    private val frameBuffer = ArrayList<ByteArray>()
    private val rawBuffer = ArrayList<Byte>()

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
        YadeaProtocol.CMD_NAV_END_SEND -> "NAV_END_REQ"
        YadeaProtocol.CMD_NAV_END_RESPONSE -> "NAV_END_RESP"
        YadeaProtocol.CMD_IMAGE -> "IMAGE"
        YadeaProtocol.CMD_NAV_HINT -> "NAV_HINT"
        YadeaProtocol.CMD_HEARTBEAT -> "HEARTBEAT"
        YadeaProtocol.CMD_NAV_START_SEND -> "NAV_START_REQ"
        YadeaProtocol.CMD_NAV_START_RESPONSE -> "NAV_START_RESP"
        YadeaProtocol.CMD_NEGATIVE_RESPONSE -> "NACK"
        else -> "0x${String.format("%02X", cmd)}"
    }

    // ==================== 发送 ====================

    suspend fun sendFrame(cmd: Int, payload: ByteArray) {
        val frame = YadeaProtocol.buildFrame(cmd, payload)
        logger("[TX] ${cmdName(cmd)} ${payload.size}B: ${hex(payload)}")
        tcp.send(frame)
    }

    suspend fun sendNavStart(packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE) {
        sendFrame(YadeaProtocol.CMD_NAV_START_SEND, YadeaProtocol.buildNavStartPayload(packetSize))
    }

    suspend fun sendNavEnd() {
        sendFrame(YadeaProtocol.CMD_NAV_END_SEND, YadeaProtocol.buildNavEndPayload())
    }

    suspend fun sendHeartbeat(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        sendFrame(YadeaProtocol.CMD_HEARTBEAT, YadeaProtocol.buildHeartbeatPayload(year, month, day, hour, minute, second))
    }

    suspend fun sendNavHint(payload: ByteArray) {
        sendFrame(YadeaProtocol.CMD_NAV_HINT, payload)
    }

    suspend fun sendStartupSequence(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        val heartbeatFrame = YadeaProtocol.buildFrame(
            YadeaProtocol.CMD_HEARTBEAT,
            YadeaProtocol.buildHeartbeatPayload(year, month, day, hour, minute, second)
        )
        val navStartFrame = YadeaProtocol.buildFrame(
            YadeaProtocol.CMD_NAV_START_SEND,
            YadeaProtocol.buildNavStartPayload(YadeaProtocol.DEFAULT_PACKET_SIZE)
        )
        logger("[TX] STARTUP: HEARTBEAT + NAV_START (packetSize=${YadeaProtocol.DEFAULT_PACKET_SIZE})")
        tcp.send(heartbeatFrame + navStartFrame)
    }

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
        logger("[TX-UDP] BROADCAST seq=$imageSeq packets=${frames.size}")
        for (frame in frames) {
            udp.broadcast(frame, broadcastPort)
        }
        return frames.size
    }

    // ==================== 接收（持续读取直到完整帧） ====================

    suspend fun receiveFrame(timeoutMs: Long = 3000): Triple<Int, ByteArray, Int>? {
        // 每次 receive 调用 50ms 超时，循环直到 timeoutMs
        val maxIter = (timeoutMs / 50).toInt().coerceAtLeast(1)
        var iter = 0

        while (iter < maxIter) {
            iter++
            parseBufferedFrames()
            if (frameBuffer.isNotEmpty()) return parseNextFrame()

            val raw = tcp.receive(4096, 50)
            if (raw != null && raw.isNotEmpty()) {
                logger("[RX] 收到 ${raw.size}B: ${hex(raw)}")
                raw.forEach { rawBuffer.add(it) }
            }
        }

        parseBufferedFrames()
        if (frameBuffer.isNotEmpty()) return parseNextFrame()

        logger("[RX] 超时 (${timeoutMs}ms), 缓冲区 ${rawBuffer.size}B")
        return null
    }

    private fun parseBufferedFrames() {
        while (rawBuffer.size >= 15) {
            var pos = -1
            for (i in 0..rawBuffer.size - 2) {
                if (rawBuffer[i] == 0x59.toByte() && rawBuffer[i + 1] == 0x44.toByte()) {
                    pos = i; break
                }
            }
            if (pos < 0) break
            if (pos > 0) repeat(pos) { rawBuffer.removeAt(0) }
            if (rawBuffer.size < 12) break

            val headerArr = rawBuffer.subList(0, 12).toByteArray()
            val totalLen = headerArr.readUInt32BE(3).toInt()
            if (totalLen < 8 || totalLen > 1_048_576) {
                rawBuffer.removeAt(0)
                continue
            }

            val payloadLen = headerArr.readUInt32BE(8).toInt()
            if (payloadLen != totalLen - 8) {
                rawBuffer.removeAt(0)
                continue
            }

            val frameEnd = totalLen + 7
            if (rawBuffer.size < frameEnd) break

            if (rawBuffer[frameEnd - 2] != 0x4B.toByte() || rawBuffer[frameEnd - 1] != 0x4A.toByte()) {
                rawBuffer.removeAt(0)
                continue
            }

            val frame = rawBuffer.subList(0, frameEnd).toByteArray()
            repeat(frameEnd) { rawBuffer.removeAt(0) }
            frameBuffer.add(frame)
        }
    }

    private fun parseNextFrame(): Triple<Int, ByteArray, Int>? {
        val frame = frameBuffer.removeAt(0)
        val payloadLen = frame.readUInt32BE(8).toInt()
        val cmd = frame[7].u()
        val payload = frame.copyOfRange(12, 12 + payloadLen)
        logger("[RX] ${cmdName(cmd)} payload=${payload.size}B: ${hex(payload)}")
        return Triple(cmd, payload, frame.size)
    }

    fun close() {
        tcp.close()
        udp.close()
    }
}
