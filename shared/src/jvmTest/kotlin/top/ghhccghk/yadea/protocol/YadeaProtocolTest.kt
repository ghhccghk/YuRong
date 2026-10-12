package top.ghhccghk.yadea.protocol

import org.junit.Assert.*
import org.junit.Test

class YadeaProtocolTest {

    // ==================== buildInnerPacket ====================

    @Test
    fun buildInnerPacket_structure() {
        val payload = byteArrayOf(1, 2, 3)
        val inner = YadeaProtocol.buildInnerPacket(0x10, payload)
        assertEquals(5 + 3, inner.size)
        assertEquals(0x10, inner[0].u())
        assertEquals(3, inner.readUInt32BE(1).toInt())
    }

    // ==================== buildFrame ====================

    @Test
    fun buildFrame_magic() {
        val frame = YadeaProtocol.buildFullFrame(0x10, byteArrayOf(1, 2, 3))
        assertEquals(0x59.toByte(), frame[0])
        assertEquals(0x44.toByte(), frame[1])
    }

    @Test
    fun buildFrame_footer() {
        val frame = YadeaProtocol.buildFullFrame(0x10, byteArrayOf(1, 2, 3))
        assertEquals(0x4B.toByte(), frame[frame.size - 2])
        assertEquals(0x4A.toByte(), frame[frame.size - 1])
    }

    @Test
    fun buildFrame_protocolId() {
        val frame = YadeaProtocol.buildFullFrame(0x10, byteArrayOf(1))
        assertEquals(0x3C.toByte(), frame[2])
    }

    @Test
    fun buildFrame_innerLength() {
        val payload = byteArrayOf(1, 2, 3)
        val frame = YadeaProtocol.buildFullFrame(0x10, payload)
        val innerLen = frame.readUInt32BE(3).toInt()
        // innerLen = innerPacket.size + 3 = (1+4+3) + 3 = 11
        assertEquals(11, innerLen)
    }

    @Test
    fun buildFrame_emptyPayload() {
        val frame = YadeaProtocol.buildFullFrame(0x13, ByteArray(0))
        assertTrue(frame.size > 0)
    }

    // ==================== parseFrame ====================

    @Test
    fun parseFrame_roundtrip() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val frame = YadeaProtocol.buildFullFrame(0x14, payload)
        val parsed = YadeaProtocol.parseFrame(frame)
        assertNotNull(parsed)
        val (type, parsedPayload, _) = parsed!!
        assertEquals(0x14, type)
        assertTrue(payload.contentEquals(parsedPayload))
    }

    @Test
    fun parseFrame_rejectsBadMagic() {
        val frame = YadeaProtocol.buildFullFrame(0x10, byteArrayOf(1))
        frame[0] = 0x00
        assertNull(YadeaProtocol.parseFrame(frame))
    }

    @Test
    fun parseFrame_rejectsTooShort() {
        assertNull(YadeaProtocol.parseFrame(ByteArray(5)))
    }

    // ==================== CRC16-IBM ====================

    @Test
    fun crc16Ibm_empty() {
        assertEquals(0xFFFF, YadeaProtocol.crc16Ibm(ByteArray(0)))
    }

    @Test
    fun crc16Ibm_knownValue() {
        val data = "123456789".encodeToByteArray()
        // base.apk/WifiHexUtils: poly=0xA001, init=0xFFFF
        assertEquals(0x4B37, YadeaProtocol.crc16Ibm(data))
    }

    // ==================== Nav Config ====================

    @Test
    fun parseNavDisplayConfig_valid() {
        val data = ByteArray(8)
        data.writeUInt16BE(0, 1024)
        data.writeUInt16BE(2, 512)
        data[4] = 0.toByte()     // format
        data[6] = 0xF6.toByte()  // full capability byte
        data[7] = 10.toByte()    // rate

        val config = YadeaProtocol.parseNavDisplayConfig(data)
        assertEquals(1024, config.width)
        assertEquals(512, config.height)
        assertEquals(0, config.imageFormat)
        assertEquals(0xF6, config.capabilities)
        assertEquals(10, config.maxFps)
    }

    @Test
    fun parseNavDisplayConfig_defaults() {
        val config = YadeaProtocol.parseNavDisplayConfig(ByteArray(4))
        assertEquals(800, config.width)
        assertEquals(480, config.height)
    }

    // ==================== NAV_START ====================

    @Test
    fun buildNavStartPayload_correct() {
        val payload = YadeaProtocol.buildNavStartPayload()
        assertEquals(8, payload.size)
        assertEquals(0x05, payload[0].u())
        assertEquals(0x9A, payload[1].u())
    }

    // ==================== NAV_END ====================

    @Test
    fun buildNavEndPayload_matchesBaseApk() {
        val payload = YadeaProtocol.buildNavEndPayload()
        assertEquals(8, payload.size)
        assertEquals(0x02, payload[0].u())
        assertEquals(0xFF, payload[1].u())
        assertEquals(0xFF, payload[7].u())
    }

    // ==================== Image Chunk ====================

    @Test
    fun buildImageFrames_singleChunk() {
        val image = ByteArray(100) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 1)
        assertEquals(1, frames.size)
    }

    @Test
    fun buildImageFrames_usesCompatibleCommand() {
        val frame = YadeaProtocol.buildImageFrames(ByteArray(10), 1).single()
        assertEquals(YadeaProtocol.CMD_IMAGE_CHUNK, frame[7].u())
        assertEquals(0x12, frame[7].u())
    }

    @Test
    fun buildImageFrames_multipleChunks() {
        val image = ByteArray(3000) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 1)
        // 3000 / 1434 = 3 chunks (1434+1434+132)
        assertEquals(3, frames.size)
    }

    // ==================== Nav Info ====================

    @Test
    fun buildNavInfoPayload_64Bytes() {
        val payload = YadeaProtocol.buildNavInfoPayload(
            iconType = 2, turningDistance = 500,
            remainingMileage = 1200, roadName = "测试路"
        )
        assertEquals(64, payload.size)
    }

    @Test
    fun buildNavInfoPayload_marker() {
        val payload = YadeaProtocol.buildNavInfoPayload()
        assertEquals(0x7F, payload[32].u())
    }

    // ==================== Heartbeat ====================

    @Test
    fun buildHeartbeatPayload_correct() {
        val hb = YadeaProtocol.buildHeartbeatPayload(2026, 10, 1, 14, 30, 0)
        assertEquals(8, hb.size)
        assertEquals(26, hb[0].u()) // year % 100
        assertEquals(10, hb[1].u())
        assertEquals(0xFF, hb[6].u())
    }
}
