package top.ghhccghk.yadea.network

import top.ghhccghk.yadea.hexByte
import top.ghhccghk.yadea.protocol.YadeaProtocol

class FrameSender(
    private val tcp: TcpTransport,
    private val udp: UdpTransport,
    private val logger: (String) -> Unit = {}
) {
    private val frameBuffer = ArrayList<ByteArray>()
    private val rawBuffer = ArrayList<Byte>()

    private fun cmdName(cmd: Int): String = when (cmd) {
        YadeaProtocol.CMD_NAV_START -> "NAV_START"
        YadeaProtocol.CMD_NAV_END -> "NAV_END"
        YadeaProtocol.CMD_NAV_INFO -> "NAV_INFO"
        YadeaProtocol.CMD_LANE_INFO -> "LANE"
        YadeaProtocol.CMD_SEGMENT_PROGRESS -> "SEGMENT"
        YadeaProtocol.CMD_IMAGE_CHUNK -> "IMG_CHUNK"
        YadeaProtocol.CMD_HEARTBEAT -> "HEARTBEAT"
        YadeaProtocol.CMD_NAV_START_RESPONSE -> "NAV_START_RESP"
        YadeaProtocol.CMD_NAV_END_RESPONSE -> "NAV_END_RESP"
        YadeaProtocol.CMD_STATUS_RESPONSE -> "STATUS"
        YadeaProtocol.CMD_RATE_RESPONSE -> "RATE"
        else -> "0x${hexByte(cmd)}"
    }

    // ==================== 发送 ====================

    suspend fun sendFrame(type: Int, payload: ByteArray) {
        val frame = YadeaProtocol.buildFullFrame(type, payload)
        logger("[TX] ${cmdName(type)} payload=${payload.size}B")
        tcp.send(frame)
    }

    suspend fun sendNavStart() {
        sendFrame(YadeaProtocol.CMD_NAV_START, YadeaProtocol.buildNavStartPayload())
    }

    suspend fun sendNavEnd() {
        sendFrame(YadeaProtocol.CMD_NAV_END, YadeaProtocol.buildNavEndPayload())
    }

    suspend fun sendHeartbeat(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        sendFrame(YadeaProtocol.CMD_HEARTBEAT, YadeaProtocol.buildHeartbeatPayload(year, month, day, hour, minute, second))
    }

    suspend fun sendNavInfo(payload: ByteArray) {
        sendFrame(YadeaProtocol.CMD_NAV_INFO, payload)
    }

    suspend fun sendStartupSequence(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
        val heartbeatFrame = YadeaProtocol.buildFullFrame(
            YadeaProtocol.CMD_HEARTBEAT,
            YadeaProtocol.buildHeartbeatPayload(year, month, day, hour, minute, second)
        )
        val navStartFrame = YadeaProtocol.buildFullFrame(
            YadeaProtocol.CMD_NAV_START,
            YadeaProtocol.buildNavStartPayload()
        )
        logger("[TX] STARTUP: HEARTBEAT + NAV_START (packetSize=${YadeaProtocol.CHUNK_SIZE})")
        tcp.send(heartbeatFrame + navStartFrame)
    }

    suspend fun sendImage(
        image: ByteArray, imageSeq: Int,
        targetHost: String, targetPort: Int,
        timestamp: Long = 0L
    ): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, timestamp)
        // 图片帧属于实时热路径。逐帧统计由 MirrorMetrics 聚合，禁止在这里同步写日志。
        for (frame in frames) {
            udp.send(frame, targetHost, targetPort)
        }
        return frames.size
    }

    suspend fun broadcastImage(
        image: ByteArray, imageSeq: Int,
        broadcastPort: Int,
        timestamp: Long = 0L
    ): Int {
        val frames = YadeaProtocol.buildImageFrames(image, imageSeq, timestamp)
        // 图片帧属于实时热路径。逐帧统计由 MirrorMetrics 聚合，禁止在这里同步写日志。
        for (frame in frames) {
            udp.broadcast(frame, broadcastPort)
        }
        return frames.size
    }

    // ==================== 接收 ====================

    suspend fun receiveFrame(timeoutMs: Long = 3000): Triple<Int, ByteArray, Int>? {
        val maxIter = (timeoutMs / 50).toInt().coerceAtLeast(1)
        var iter = 0

        while (iter < maxIter) {
            iter++
            parseBufferedFrames()
            if (frameBuffer.isNotEmpty()) return parseNextFrame()

            val raw = tcp.receive(4096, 50)
            if (raw != null && raw.isNotEmpty()) {
                logger("[RX] 收到 ${raw.size}B")
                raw.forEach { rawBuffer.add(it) }
            }
        }

        parseBufferedFrames()
        if (frameBuffer.isNotEmpty()) return parseNextFrame()
        logger("[RX] 超时 (${timeoutMs}ms), 缓冲区 ${rawBuffer.size}B")
        return null
    }

    private fun parseBufferedFrames() {
        while (rawBuffer.size >= 12) {
            // 扫描魔数 59 44
            var pos = -1
            for (i in 0..rawBuffer.size - 2) {
                if (rawBuffer[i] == 0x59.toByte() && rawBuffer[i + 1] == 0x44.toByte()) {
                    pos = i; break
                }
            }
            if (pos < 0) break
            if (pos > 0) repeat(pos) { rawBuffer.removeAt(0) }
            if (rawBuffer.size < 7) break

            // 外层: head(2)+id(1)+innerLen(4)+innerPacket+sum(1)+tail(2)
            val innerLen = (rawBuffer.subList(3, 7).toByteArray()).let {
                ((it[0].u() shl 24) or (it[1].u() shl 16) or (it[2].u() shl 8) or it[3].u())
            }
            if (innerLen < 3 || innerLen > 1_048_576) {
                rawBuffer.removeAt(0); continue
            }

            val totalFrameLen = innerLen + 7
            if (rawBuffer.size < totalFrameLen) break

            // 校验尾部
            if (rawBuffer[totalFrameLen - 2] != 0x4B.toByte() || rawBuffer[totalFrameLen - 1] != 0x4A.toByte()) {
                rawBuffer.removeAt(0); continue
            }

            val frame = rawBuffer.subList(0, totalFrameLen).toByteArray()
            repeat(totalFrameLen) { rawBuffer.removeAt(0) }
            frameBuffer.add(frame)
        }
    }

    private fun parseNextFrame(): Triple<Int, ByteArray, Int>? {
        val frame = frameBuffer.removeAt(0)
        val result = YadeaProtocol.parseFrame(frame)
        if (result != null) {
            val (cmd, payload, frameLen) = result
            logger("[RX] ${cmdName(cmd)} payload=${payload.size}B")
            return result
        }
        logger("[RX] 协议帧解析失败 (${frame.size}B)")
        return null
    }

    fun close() {
        tcp.close()
        udp.close()
    }
}

private fun Byte.u() = this.toInt() and 0xFF
