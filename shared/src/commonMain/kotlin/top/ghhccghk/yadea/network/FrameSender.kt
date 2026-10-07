package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.protocol.YadeaProtocol

/**
 * FrameSender - high-level frame sending API
 *
 * Wraps protocol and transport layers:
 * - sendFrame: single protocol frame via TCP
 * - sendImage: image data via UDP (chunked)
 * - sendNavStart/End: navigation control commands
 * - sendHeartbeat: heartbeat
 */
class FrameSender(
    private val tcp: TcpTransport,
    private val udp: UdpTransport
) {
    suspend fun sendFrame(cmd: Int, payload: ByteArray) {
        val frame = YadeaProtocol.buildFrame(cmd, payload)
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

    suspend fun sendImage(image: ByteArray, imageSeq: Int, targetHost: String, targetPort: Int, packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, packetSize)
        for (frame in frames) {
            udp.send(frame, targetHost, targetPort)
        }
        return frames.size
    }

    suspend fun broadcastImage(image: ByteArray, imageSeq: Int, broadcastPort: Int, packetSize: Int = YadeaProtocol.DEFAULT_PACKET_SIZE): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, packetSize)
        for (frame in frames) {
            udp.broadcast(frame, broadcastPort)
        }
        return frames.size
    }

    suspend fun receiveFrame(timeoutMs: Long = 3000): Triple<Int, ByteArray, Int>? {
        val header = tcp.readExact(12, timeoutMs) ?: return null
        val payloadLen = header.copyOfRange(8, 12).let { bytes ->
            ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
        }
        val remaining = tcp.readExact(payloadLen + 3, timeoutMs) ?: return null
        val fullFrame = header + remaining
        return YadeaProtocol.parseFrame(fullFrame)
    }

    fun close() {
        tcp.close()
        udp.close()
    }
}
