package top.ghhccghk.yadea.protocol

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class YadeaProtocolTest {

    // ==================== buildFrame ====================

    @Test
    fun buildFrame_producesCorrectMagic() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1, 2, 3))
        assertEquals(0x59.toByte(), frame[0]) // 'Y'
        assertEquals(0x44.toByte(), frame[1]) // 'D'
        assertEquals(0x3C.toByte(), frame[2]) // '<'
    }

    @Test
    fun buildFrame_producesCorrectFooter() {
        val frame = YadeaProtocol.buildFrame(0x12, byteArrayOf(1, 2, 3))
        assertEquals(0x4B.toByte(), frame[frame.size - 2]) // 'K'
        assertEquals(0x4A.toByte(), frame[frame.size - 1]) // 'J'
    }

    @Test
    fun buildFrame_frameLengthIsCorrect() {
        val payload = byteArrayOf(1, 2, 3)
        val frame = YadeaProtocol.buildFrame(0x12, payload)
        // frame total = 15 + payload.size
        assertEquals(15 + payload.size, frame.size)
        // totalLen field = payload.size + 8
        val totalLen = frame.readUInt32BE(3)
        assertEquals((payload.size + 8).toLong(), totalLen)
    }

    @Test
    fun buildFrame_cmdIsCorrect() {
        val frame = YadeaProtocol.buildFrame(0x20, byteArrayOf())
        assertEquals(0x20, frame[7].u())
    }

    @Test
    fun buildFrame_payloadLengthIsCorrect() {
        val payload = byteArrayOf(10, 20, 30, 40, 50)
        val frame = YadeaProtocol.buildFrame(0x1F, payload)
        val payloadLen = frame.readUInt32BE(8)
        assertEquals(payload.size.toLong(), payloadLen)
    }

    @Test
    fun buildFrame_payloadIsCopied() {
        val payload = byteArrayOf(0x0A, 0x0B, 0x0C)
        val frame = YadeaProtocol.buildFrame(0x12, payload)
        for (i in payload.indices) {
            assertEquals(payload[i], frame[12 + i])
        }
    }

    @Test
    fun buildFrame_emptyPayload() {
        val frame = YadeaProtocol.buildFrame(0x02, byteArrayOf())
        assertEquals(15, frame.size)
    }

    // ==================== parseFrame ====================

    @Test
    fun parseFrame_roundtrip() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val frame = YadeaProtocol.buildFrame(0x12, payload)
        val parsed = YadeaProtocol.parseFrame(frame)
        assertNotNull(parsed)
        val cmd = parsed!!.first
        val parsedPayload = parsed.second
        val frameLen = parsed.third
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
        // CRC16-Modbus("123456789") = 0x4B37
        val data = "123456789".encodeToByteArray()
        assertEquals(0x4B37, YadeaProtocol.crc16Modbus(data))
    }

    @Test
    fun crc16Modbus_singleByte() {
        val result = YadeaProtocol.crc16Modbus(byteArrayOf(0x01))
        assertTrue(result in 0..0xFFFF)
    }

    // ==================== checksum ====================

    @Test
    fun checksum_simple() {
        val data = byteArrayOf(1, 2, 3, 0, 0) // last 2 reserved
        val sum = YadeaProtocol.checksum(data, 3)
        assertEquals(6.toByte(), sum) // 1+2+3 = 6
    }

    @Test
    fun checksum_overflow() {
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0, 0)
        val sum = YadeaProtocol.checksum(data, 2)
        // (255+255) & 0xFF = 0xFE
        assertEquals(0xFE.toByte(), sum)
    }

    // ==================== Image Packet ====================

    @Test
    fun buildImagePacketPayload_headerFields() {
        val chunk = byteArrayOf(10, 20, 30)
        val payload = YadeaProtocol.buildImagePacketPayload(
            crc16 = 0xABCD,
            chunkLen = 3,
            imageSeq = 7,
            totalSize = 100,
            offset = 50,
            chunk = chunk,
            timestamp = 0x12345678L
        )
        assertEquals(20 + 3, payload.size)
        assertEquals(0xABCD, payload.readUInt16LE(0))
        assertEquals(3, payload.readUInt16BE(2))
        assertEquals(7L, payload.readUInt32BE(4))
        assertEquals(100L, payload.readUInt32BE(8))
        assertEquals(50L, payload.readUInt32BE(12))
    }

    @Test
    fun buildImageFrames_singlePacket() {
        val image = ByteArray(100) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 0, packetSize = 200)
        assertEquals(1, frames.size)
    }

    @Test
    fun buildImageFrames_multiplePackets() {
        val image = ByteArray(2500) { it.toByte() }
        val frames = YadeaProtocol.buildImageFrames(image, 0, packetSize = 1024)
        assertEquals(3, frames.size) // 1024 + 1024 + 452
    }

    // ==================== Nav Config ====================

    @Test
    fun parseNavDisplayConfig_valid() {
        val data = ByteArray(8)
        data.writeUInt16BE(0, 1024) // width
        data.writeUInt16BE(2, 480)  // height
        data[4] = 1                  // rotation
        data[6] = 5                  // offsetX
        data[7] = 2                  // packetMultiplier

        val config = YadeaProtocol.parseNavDisplayConfig(data)
        assertEquals(1024, config.width)
        assertEquals(480, config.height)
        assertEquals(1, config.rotation)
        assertEquals(5, config.offsetX)
        assertEquals(2, config.packetMultiplier)
    }

    @Test
    fun parseNavDisplayConfig_defaults() {
        val config = YadeaProtocol.parseNavDisplayConfig(ByteArray(4))
        assertEquals(960, config.width)
        assertEquals(368, config.height)
        assertEquals(1, config.packetMultiplier)
    }

    @Test
    fun parseNavDisplayConfig_clampsOutOfRange() {
        val data = ByteArray(8)
        data.writeUInt16BE(0, 100) // too small
        data.writeUInt16BE(2, 5000) // too large
        val config = YadeaProtocol.parseNavDisplayConfig(data)
        assertEquals(960, config.width)   // fallback
        assertEquals(368, config.height)   // fallback
    }

    // ==================== Heartbeat ====================

    @Test
    fun buildHeartbeatPayload_fields() {
        val hb = YadeaProtocol.buildHeartbeatPayload(2026, 10, 1, 14, 30, 0)
        assertEquals(8, hb.size)
        assertEquals(26, hb[0].u()) // year - 2000
        assertEquals(10, hb[1].u())
        assertEquals(1, hb[2].u())
        assertEquals(14, hb[3].u())
        assertEquals(30, hb[4].u())
        assertEquals(0, hb[5].u())
        assertEquals(0xFF, hb[6].u())
        assertEquals(0xFF, hb[7].u())
    }

    // ==================== Byte Extensions ====================

    @Test
    fun uint16BE_roundtrip() {
        val buf = ByteArray(2)
        buf.writeUInt16BE(0, 0x1234)
        assertEquals(0x1234, buf.readUInt16BE(0))
    }

    @Test
    fun uint16LE_roundtrip() {
        val buf = ByteArray(2)
        buf.writeUInt16LE(0, 0x1234)
        assertEquals(0x1234, buf.readUInt16LE(0))
    }

    @Test
    fun uint32BE_roundtrip() {
        val buf = ByteArray(4)
        buf.writeUInt32BE(0, 0x12345678)
        assertEquals(0x12345678L, buf.readUInt32BE(0))
    }

    @Test
    fun uint32LE_roundtrip() {
        val buf = ByteArray(4)
        buf.writeUInt32LE(0, 0x12345678)
        assertEquals(0x12345678L, buf.readUInt32LE(0))
    }

    @Test
    fun byteUnsigned() {
        assertEquals(255, 0xFF.toByte().u())
        assertEquals(128, 0x80.toByte().u())
        assertEquals(0, 0x00.toByte().u())
    }
}


