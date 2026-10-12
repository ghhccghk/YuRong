package top.ghhccghk.yadea.protocol

import org.junit.Assert.*
import org.junit.Test

class YadeaProtocolTest {

    // ==================== buildFrame ====================

    @Test
    fun buildFrame_producesCorrectMagic() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1, 2, 3))
        assertEquals(0x59.toByte(), frame[0])
        assertEquals(0x44.toByte(), frame[1])
        assertEquals(0x3C.toByte(), frame[2])
    }

    @Test
    fun buildFrame_producesCorrectFooter() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1, 2, 3))
        assertEquals(0x4B.toByte(), frame[frame.size - 2])
        assertEquals(0x4A.toByte(), frame[frame.size - 1])
    }

    @Test
    fun buildFrame_frameLengthIsCorrect() {
        val payload = byteArrayOf(1, 2, 3)
        val frame = YadeaProtocol.buildFrame(0x12, payload)
        assertEquals(15 + payload.size, frame.size)
        val totalLen = frame.readUInt32BE(3)
        assertEquals((payload.size + 8).toLong(), totalLen)
    }

    @Test
    fun buildFrame_cmdIsCorrect() {
        val frame = YadeaProtocol.buildFrame(0x10, byteArrayOf())
        assertEquals(0x10, frame[7].u())
    }

    @Test
    fun buildFrame_payloadLengthIsCorrect() {
        val payload = byteArrayOf(10, 20, 30, 40, 50)
        val frame = YadeaProtocol.buildFrame(0x1F, payload)
        val payloadLen = frame.readUInt32BE(8)
        assertEquals(payload.size.toLong(), payloadLen)
    }

    @Test
    fun buildFrame_emptyPayload() {
        val frame = YadeaProtocol.buildFrame(0x13, byteArrayOf())
        assertEquals(15, frame.size)
    }

    // ==================== parseFrame ====================

    @Test
    fun parseFrame_roundtrip() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val frame = YadeaProtocol.buildFrame(0x12, payload)
        val parsed = YadeaProtocol.parseFrame(frame)
        assertNotNull(parsed)
        val (cmd, parsedPayload, frameLen) = parsed!!
        assertEquals(0x12, cmd)
        assertTrue(payload.contentEquals(parsedPayload))
        assertEquals(frame.size, frameLen)
    }

    @Test
    fun parseFrame_rejectsBadMagic() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1))
        frame[0] = 0x00
        assertNull(YadeaProtocol.parseFrame(frame))
    }

    @Test
    fun parseFrame_rejectsTooShort() {
        assertNull(YadeaProtocol.parseFrame(ByteArray(10)))
    }

    @Test
    fun parseFrame_rejectsBadFooter() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1))
        frame[frame.size - 1] = 0x00
        assertNull(YadeaProtocol.parseFrame(frame))
    }

    // ==================== CRC16-Modbus ====================

    @Test
    fun crc16Modbus_empty() {
        assertEquals(0xFFFF, YadeaProtocol.crc16Modbus(ByteArray(0)))
    }

    @Test
    fun crc16Modbus_knownValue() {
        val data = "123456789".encodeToByteArray()
        assertEquals(0x4B37, YadeaProtocol.crc16Modbus(data))
    }

    // ==================== checksum ====================

    @Test
    fun checksum_simple() {
        val data = byteArrayOf(1, 2, 3, 0, 0)
        val sum = YadeaProtocol.checksum(data, 3)
        assertEquals(6.toByte(), sum)
    }

    // ==================== Image Packet ====================

    @Test
    fun buildImageFrames_singlePacket() {
        val image = ByteArray(100) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 1, packetSize = 200)
        assertEquals(1, frames.size)
    }

    @Test
    fun buildImageFrames_multiplePackets() {
        val image = ByteArray(2500) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 1, packetSize = 1024)
        assertEquals(3, frames.size)
    }

    // ==================== Nav Config ====================

    @Test
    fun parseNavDisplayConfig_valid() {
        val data = ByteArray(8)
        data.writeUInt16BE(0, 1024) // width
        data.writeUInt16BE(2, 512)  // height
        data[4] = 1.toByte()         // imageFormat
        data[6] = 0xF6.toByte()      // capabilities
        data[7] = 10.toByte()        // maxFps

        val config = YadeaProtocol.parseNavDisplayConfig(data)
        assertEquals(1024, config.width)
        assertEquals(512, config.height)
        assertEquals(1, config.imageFormat)
        assertEquals(0xF6, config.capabilities)
        assertEquals(10, config.maxFps)
    }

    @Test
    fun parseNavDisplayConfig_defaults() {
        val config = YadeaProtocol.parseNavDisplayConfig(ByteArray(4))
        assertEquals(1024, config.width)
        assertEquals(512, config.height)
    }

    @Test
    fun parseNavDisplayConfig_requiresMin8Bytes() {
        val config = YadeaProtocol.parseNavDisplayConfig(ByteArray(7))
        assertEquals(1024, config.width) // fallback
    }

    // ==================== Heartbeat ====================

    @Test
    fun buildHeartbeatPayload_fields() {
        val hb = YadeaProtocol.buildHeartbeatPayload(2026, 10, 1, 14, 30, 0)
        assertEquals(8, hb.size)
        assertEquals(26, hb[0].u())
        assertEquals(10, hb[1].u())
        assertEquals(1, hb[2].u())
    }

    // ==================== Byte Extensions ====================

    @Test
    fun uint16BE_roundtrip() {
        val buf = ByteArray(2)
        buf.writeUInt16BE(0, 0x1234)
        assertEquals(0x1234, buf.readUInt16BE(0))
    }

    @Test
    fun uint32BE_roundtrip() {
        val buf = ByteArray(4)
        buf.writeUInt32BE(0, 0x12345678)
        assertEquals(0x12345678L, buf.readUInt32BE(0))
    }

    @Test
    fun byteUnsigned() {
        assertEquals(255, 0xFF.toByte().u())
    }
}

