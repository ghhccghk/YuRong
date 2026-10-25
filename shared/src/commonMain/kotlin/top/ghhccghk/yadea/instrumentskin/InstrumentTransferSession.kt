package top.ghhccghk.yadea.instrumentskin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.mirror.DateTimeParts

/**
 * 仪表传输会话。壁纸与皮肤共用协议 60（0x3C）与 TCP 8880。
 */
class InstrumentTransferSession(
    private val tcp: TcpTransport,
    private val scope: CoroutineScope,
    private val timestampMillis: () -> Long,
    private val currentTime: () -> DateTimeParts,
    private val onStatus: (String) -> Unit = {},
    private val onProgress: (Float?) -> Unit = {},
    private val cancelled: () -> Boolean = { false },
) {
    private var heartbeatJob: Job? = null
    private var heartbeatFailure: Throwable? = null
    private val sendMutex = Mutex()

    data class DisplayConfig(
        val width: Int,
        val height: Int,
        val format: Int,
    )

    suspend fun uploadWallpaper(
        convert: suspend (DisplayConfig) -> ByteArray,
    ) {
        checkActive()
        // 壁纸 START 载荷固定，响应后再启动每秒心跳。
        send(WallpaperProtocol.CMD_START_REQUEST, WALLPAPER_START_PAYLOAD)
        startHeartbeat(initialDelayMs = 0L)

        try {
            onStatus("正在读取仪表壁纸参数…")
            val start = await(WallpaperProtocol.CMD_START_RESPONSE)
            val display = parseDisplay(start)
            onStatus("仪表 ${display.width}x${display.height} 格式=${display.format}")
            onProgress(0f)

            val image = convert(display)
            require(image.isNotEmpty()) { "壁纸图片转换失败" }

            send(
                WallpaperProtocol.CMD_PARAM_REQUEST,
                WallpaperProtocol.int32(image.size) + PARAM_RESERVED,
            )
            awaitParamAck(image.size)

            val crc16 = InstrumentCrc.crc16Modbus(image)
            var offset = 0
            while (offset < image.size) {
                checkActive()
                val size = minOf(WALLPAPER_CHUNK_SIZE, image.size - offset)
                val header = crc16.toLe16() +
                    WallpaperProtocol.int16(size) +
                    WallpaperProtocol.int32(0) +
                    WallpaperProtocol.int32(image.size) +
                    WallpaperProtocol.int32(offset) +
                    WallpaperProtocol.int32(timestampMillis().toInt())
                send(WallpaperProtocol.CMD_DATA_REQUEST, header + image.copyOfRange(offset, offset + size))
                offset += size
                onStatus("正在上传壁纸 ${(offset * 100) / image.size}%")
                onProgress((offset.toFloat() / image.size.toFloat()).coerceIn(0f, 1f))
            }

            // 壁纸全部数据发完后只等待一次 0x24，data[0]==0 接受。
            onStatus("等待仪表确认壁纸数据…")
            val dataAck = await(WallpaperProtocol.CMD_DATA_RESPONSE)
            require(dataAck.isNotEmpty() && (dataAck[0].toInt() and 0xFF) == 0) {
                "仪表未接受壁纸数据"
            }

            send(WallpaperProtocol.CMD_END_REQUEST, END_PAYLOAD)
            val end = await(WallpaperProtocol.CMD_END_RESPONSE)
            require(end.size >= 1 && (end[0].toInt() and 3) != 0) {
                "仪表未确认壁纸生效"
            }
            onProgress(1f)
            onStatus("仪表壁纸设置成功")
        } finally {
            stopHeartbeat()
        }
    }

    suspend fun uploadSkin(pack: ByteArray) {
        checkActive()
        InstrumentSkinPack.parse(pack)

        send(WallpaperProtocol.CMD_HEARTBEAT, heartbeatPayload())
        send(WallpaperProtocol.CMD_START_REQUEST, SKIN_START_PAYLOAD)
        startHeartbeat(initialDelayMs = 1_000L)

        try {
            onStatus("正在读取皮肤参数…")
            val start = await(WallpaperProtocol.CMD_START_RESPONSE)
            require(start.size == 8) { "皮肤确认帧必须为 8 字节" }
            require(WallpaperProtocol.readInt16(start, 0) == 800) { "当前皮肤仅适配 800×480 仪表" }
            require(WallpaperProtocol.readInt16(start, 2) == 480) { "当前皮肤仅适配 800×480 仪表" }
            require((start[4].toInt() and 0xFF) == 0) { "当前皮肤仅适配格式 0" }

            send(
                WallpaperProtocol.CMD_PARAM_REQUEST,
                WallpaperProtocol.int32(pack.size) + PARAM_RESERVED,
            )
            awaitParamAck(pack.size)
            onProgress(0f)

            val crc16 = InstrumentCrc.crc16Modbus(pack)
            var offset = 0
            while (offset < pack.size) {
                checkActive()
                val size = minOf(SKIN_CHUNK_SIZE, pack.size - offset)
                val header = crc16.toLe16() +
                    WallpaperProtocol.int16(size) +
                    WallpaperProtocol.int32(0) +
                    WallpaperProtocol.int32(pack.size) +
                    WallpaperProtocol.int32(offset) +
                    WallpaperProtocol.int32(timestampMillis().toInt())
                send(WallpaperProtocol.CMD_DATA_REQUEST, header + pack.copyOfRange(offset, offset + size))

                // 皮肤每个包都等待 0x24。
                val ack = await(WallpaperProtocol.CMD_DATA_RESPONSE)
                val expected = if (offset + size == pack.size) 0 else 4
                require(ack.size == 1 && (ack[0].toInt() and 0xFF) == expected) {
                    "皮肤分包确认异常：offset=$offset"
                }
                offset += size
                onStatus("正在上传皮肤 ${(offset * 100) / pack.size}%")
                onProgress((offset.toFloat() / pack.size.toFloat()).coerceIn(0f, 1f))
            }

            send(WallpaperProtocol.CMD_END_REQUEST, END_PAYLOAD)
            val end = await(WallpaperProtocol.CMD_END_RESPONSE)
            require(end.size == 8 && (end[0].toInt() and 3) == 1) {
                "仪表未确认皮肤生效"
            }
            onProgress(1f)
            onStatus("仪表已确认皮肤上传成功")
        } finally {
            stopHeartbeat()
        }
    }

    private suspend fun await(expected: Int): ByteArray {
        while (true) {
            checkActive()
            val frame = WallpaperProtocol.readOuterFrame(tcp)
            if (frame.command == WallpaperProtocol.CMD_NEGATIVE_RESPONSE) {
                throw IllegalStateException("仪表拒绝（0xF1）")
            }
            if (frame.command == expected) return frame.data
            // 忽略其他响应，等待目标命令。
        }
    }

    private suspend fun awaitParamAck(expectedLength: Int) {
        val ack = await(WallpaperProtocol.CMD_PARAM_RESPONSE)
        require(ack.size == 8 && WallpaperProtocol.readInt32(ack, 0) == expectedLength) {
            "仪表确认的长度不一致"
        }
    }

    private fun parseDisplay(data: ByteArray): DisplayConfig {
        require(data.size >= 8) { "0x20 响应过短" }
        val width = WallpaperProtocol.readInt16(data, 0)
        val height = WallpaperProtocol.readInt16(data, 2)
        val format = data[4].toInt() and 0xFF
        require(width in 1..4096 && height in 1..4096) { "仪表尺寸无效" }
        return DisplayConfig(width, height, if (format in 0..3) format else 0)
    }

    private suspend fun send(cmd: Int, data: ByteArray) {
        checkActive()
        // 心跳与业务帧共享同一 TCP 连接，写入必须串行，避免两个 IO 协程交错。
        sendMutex.withLock {
            tcp.send(WallpaperProtocol.outerFrame(WallpaperProtocol.command(cmd, data)))
        }
    }

    private fun checkActive() {
        if (cancelled()) throw IllegalStateException("仪表任务已停止")
        heartbeatFailure?.let { throw it }
    }

    private fun startHeartbeat(initialDelayMs: Long) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            delay(initialDelayMs)
            while (isActive) {
                try {
                    send(WallpaperProtocol.CMD_HEARTBEAT, heartbeatPayload())
                } catch (t: Throwable) {
                    heartbeatFailure = t
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun heartbeatPayload(): ByteArray =
        currentTime().let { now ->
            byteArrayOf(
                (now.year % 100).toByte(),
                now.month.toByte(),
                now.day.toByte(),
                now.hour.toByte(),
                now.minute.toByte(),
                now.second.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            )
        }

    private fun Int.toLe16(): ByteArray = byteArrayOf(
        toByte(),
        (ushr(8)).toByte(),
    )

    private companion object {
        const val WALLPAPER_CHUNK_SIZE = 1434
        const val SKIN_CHUNK_SIZE = 32768

        val WALLPAPER_START_PAYLOAD = byteArrayOf(0x05.toByte(), 0x9A.toByte(), 0xF3.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(), 0x9A.toByte())
        val SKIN_START_PAYLOAD = byteArrayOf(0x80.toByte(), 0x00.toByte(), 0xF4.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x05.toByte(), 0x9A.toByte())
        val PARAM_RESERVED = byteArrayOf(0xF6.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val END_PAYLOAD = byteArrayOf(0xFD.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    }
}
