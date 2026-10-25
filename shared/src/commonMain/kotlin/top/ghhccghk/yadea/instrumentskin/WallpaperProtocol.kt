package top.ghhccghk.yadea.instrumentskin

import top.ghhccghk.yadea.network.TcpTransport

object WallpaperProtocol {
    const val REQUEST_PROTOCOL_ID = 60
    const val RESPONSE_PROTOCOL_ID = 76
    const val HEADER_0 = 0x59
    const val HEADER_1 = 0x44
    const val TAIL_0 = 0x4B
    const val TAIL_1 = 0x4A

    const val CMD_START_REQUEST = 0x10
    const val CMD_PARAM_REQUEST = 0x11
    const val CMD_DATA_REQUEST = 0x12
    const val CMD_END_REQUEST = 0x13
    const val CMD_HEARTBEAT = 0x1F

    const val CMD_START_RESPONSE = 0x20
    const val CMD_PARAM_RESPONSE = 0x21
    const val CMD_DATA_RESPONSE = 0x24
    const val CMD_END_RESPONSE = 0x23
    const val CMD_NEGATIVE_RESPONSE = 0xF1

    data class InnerFrame(
        val command: Int,
        val data: ByteArray,
    )

    fun command(cmd: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val result = ByteArray(5 + data.size)
        result[0] = cmd.toByte()
        val length = data.size
        result[1] = (length ushr 24).toByte()
        result[2] = (length ushr 16).toByte()
        result[3] = (length ushr 8).toByte()
        result[4] = length.toByte()
        data.copyInto(result, 5)
        return result
    }

    fun outerFrame(
        inner: ByteArray,
        protocolId: Int = REQUEST_PROTOCOL_ID,
    ): ByteArray {
        val rawLength = inner.size + 3
        val result = ByteArray(11 + inner.size)
        var index = 0
        result[index++] = HEADER_0.toByte()
        result[index++] = HEADER_1.toByte()
        result[index++] = protocolId.toByte()
        result[index++] = (rawLength ushr 24).toByte()
        result[index++] = (rawLength ushr 16).toByte()
        result[index++] = (rawLength ushr 8).toByte()
        result[index++] = rawLength.toByte()
        inner.copyInto(result, index)
        index += inner.size
        val checkBytes = ByteArray(5 + inner.size)
        checkBytes[0] = protocolId.toByte()
        checkBytes[1] = (rawLength ushr 24).toByte()
        checkBytes[2] = (rawLength ushr 16).toByte()
        checkBytes[3] = (rawLength ushr 8).toByte()
        checkBytes[4] = rawLength.toByte()
        inner.copyInto(checkBytes, 5)
        result[index++] = InstrumentCrc.sum8(checkBytes).toByte()
        result[index++] = TAIL_0.toByte()
        result[index] = TAIL_1.toByte()
        return result
    }

    fun parseInner(inner: ByteArray): InnerFrame {
        require(inner.size >= 5) { "壁纸命令帧过短" }
        val dataLength = readInt32(inner, 1)
        require(dataLength >= 0 && inner.size == dataLength + 5) {
            "壁纸命令长度不匹配"
        }
        return InnerFrame(
            command = inner[0].toInt() and 0xFF,
            data = inner.copyOfRange(5, inner.size),
        )
    }

    suspend fun readOuterFrame(tcp: TcpTransport): InnerFrame {
        var previous = -1
        while (true) {
            val chunk = tcp.receive(1, timeoutMs = 15_000)
                ?: error("壁纸连接已关闭")
            val current = chunk[0].toInt() and 0xFF
            if (previous == HEADER_0 && current == HEADER_1) {
                break
            }
            previous = current
        }

        val protocol = tcp.readExact(1, timeoutMs = 15_000)
            ?: error("壁纸响应协议 ID 读取失败")
        val protocolId = protocol[0].toInt() and 0xFF
        require(protocolId == RESPONSE_PROTOCOL_ID) {
            "壁纸响应协议 ID 错误：期望 0x${RESPONSE_PROTOCOL_ID.toString(16)}，" +
                "收到 0x${protocolId.toString(16)}"
        }

        val lengthBytes = tcp.readExact(4, timeoutMs = 15_000)
            ?: error("壁纸帧长度读取失败")
        val frameLength = readInt32(lengthBytes, 0)
        require(frameLength in 3 until 2_097_156) { "壁纸帧长度无效" }

        val innerHeader = tcp.readExact(5, timeoutMs = 15_000)
            ?: error("壁纸命令头读取失败")
        val dataLength = readInt32(innerHeader, 1)
        require(dataLength in 0 until 2_097_153) { "壁纸命令数据长度无效" }

        val inner = ByteArray(5 + dataLength)
        innerHeader.copyInto(inner, 0)
        val data = tcp.readExact(dataLength, timeoutMs = 15_000)
            ?: error("壁纸命令数据读取失败")
        data.copyInto(inner, 5)

        val tail = tcp.readExact(3, timeoutMs = 15_000)
            ?: error("壁纸帧尾读取失败")
        require(
            (tail[1].toInt() and 0xFF) == TAIL_0 &&
                (tail[2].toInt() and 0xFF) == TAIL_1,
        ) { "壁纸帧尾错误" }
        val actualFrameLength = inner.size + 3
        val legacyParamLength = (innerHeader[0].toInt() and 0xFF) == CMD_PARAM_RESPONSE &&
            dataLength == 8 &&
            frameLength == 12
        require(frameLength == actualFrameLength || legacyParamLength) {
            "壁纸外层长度不匹配：声明=$frameLength，实际=$actualFrameLength"
        }
        return parseInner(inner)
    }

    fun int16(value: Int): ByteArray = byteArrayOf(
        (value ushr 8).toByte(),
        value.toByte(),
    )

    fun int32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    fun readInt16(bytes: ByteArray, offset: Int): Int {
        require(offset in 0..bytes.size - 2) { "读取 16 位整数越界" }
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    fun readInt32(bytes: ByteArray, offset: Int): Int {
        require(offset in 0..bytes.size - 4) { "读取 32 位整数越界" }
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }
}
