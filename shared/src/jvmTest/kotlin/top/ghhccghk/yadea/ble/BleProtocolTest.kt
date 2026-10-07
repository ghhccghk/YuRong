package top.ghhccghk.yadea.ble

import org.junit.Assert.*
import org.junit.Test

class BleProtocolTest {

    // ==================== UUID 常量 ====================
    @Test
    fun uuidConstants_areCorrect() {
        assertEquals("7e400001-b5a3-f393-e0a9-e50e24dcca9e", BleProtocol.SERVICE_UUID)
        assertEquals("7e400002-b5a3-f393-e0a9-e50e24dcca9e", BleProtocol.CONTROL_UUID)
        assertEquals("7e400003-b5a3-f393-e0a9-e50e24dcca9e", BleProtocol.DATA_UUID)
        assertEquals("7e400004-b5a3-f393-e0a9-e50e24dcca9e", BleProtocol.STATUS_UUID)
        assertEquals("7e400005-b5a3-f393-e0a9-e50e24dcca9e", BleProtocol.RESULT_UUID)
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", BleProtocol.CLIENT_CONFIG_UUID)
    }

    @Test
    fun protocolConstants_areCorrect() {
        assertEquals(517, BleProtocol.REQUESTED_MTU)
        assertEquals(10, BleProtocol.TRANSPORT_HEADER_SIZE)
        assertEquals(10_000L, BleProtocol.SCAN_TIMEOUT_MS)
        assertEquals(18L, BleProtocol.DATA_PACKET_PACING_MS)
        assertEquals(1500L, BleProtocol.DATA_WRITE_STALL_MS)
        assertEquals(20, BleProtocol.MAX_DATA_WRITE_RETRIES)
    }

    // ==================== 传输头构建 ====================
    @Test
    fun buildTransportHeader_length() {
        val header = BleProtocol.buildTransportHeader(1, 0, 100)
        assertEquals(10, header.size)
    }

    @Test
    fun buildTransportHeader_sequence() {
        val header = BleProtocol.buildTransportHeader(0x1234, 0, 0)
        assertEquals(0x34, header[0].toInt() and 0xFF)
        assertEquals(0x12, header[1].toInt() and 0xFF)
    }

    @Test
    fun buildTransportHeader_offset() {
        val header = BleProtocol.buildTransportHeader(0, 0x01020304, 0)
        assertEquals(0x04, header[2].toInt() and 0xFF)
        assertEquals(0x03, header[3].toInt() and 0xFF)
        assertEquals(0x02, header[4].toInt() and 0xFF)
        assertEquals(0x01, header[5].toInt() and 0xFF)
    }

    @Test
    fun buildTransportHeader_payloadSize() {
        val header = BleProtocol.buildTransportHeader(0, 0, 0x0A0B0C0D)
        assertEquals(0x0D, header[6].toInt() and 0xFF)
        assertEquals(0x0C, header[7].toInt() and 0xFF)
        assertEquals(0x0B, header[8].toInt() and 0xFF)
        assertEquals(0x0A, header[9].toInt() and 0xFF)
    }

    // ==================== 传输头解析 ====================
    @Test
    fun parseTransportHeader_roundtrip() {
        val header = BleProtocol.buildTransportHeader(42, 1024, 256)
        val (seq, offset, size) = BleProtocol.parseTransportHeader(header)
        assertEquals(42, seq)
        assertEquals(1024, offset)
        assertEquals(256, size)
    }

    @Test
    fun parseTransportHeader_tooShort() {
        val (seq, offset, size) = BleProtocol.parseTransportHeader(ByteArray(5))
        assertEquals(0, seq)
        assertEquals(0, offset)
        assertEquals(0, size)
    }

    @Test
    fun parseTransportHeader_zeroValues() {
        val header = BleProtocol.buildTransportHeader(0, 0, 0)
        val (seq, offset, size) = BleProtocol.parseTransportHeader(header)
        assertEquals(0, seq)
        assertEquals(0, offset)
        assertEquals(0, size)
    }

    @Test
    fun parseTransportHeader_maxValues() {
        val header = BleProtocol.buildTransportHeader(0xFFFF, 0x7FFFFFFF, 0x7FFFFFFF)
        val (seq, offset, size) = BleProtocol.parseTransportHeader(header)
        assertEquals(0xFFFF, seq)
        assertEquals(0x7FFFFFFF, offset)
        assertEquals(0x7FFFFFFF, size)
    }

    // ==================== 数据包构建 ====================
    @Test
    fun buildDataPacket_headerPlusPayload() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = BleProtocol.buildDataPacket(1, 0, payload)
        assertEquals(10 + 5, packet.size)
    }

    @Test
    fun buildDataPacket_payloadPreserved() {
        val payload = byteArrayOf(0x0A, 0x0B, 0x0C)
        val packet = BleProtocol.buildDataPacket(0, 0, payload)
        for (i in payload.indices) {
            assertEquals(payload[i], packet[10 + i])
        }
    }

    @Test
    fun buildDataPacket_parseRoundtrip() {
        val payload = byteArrayOf(10, 20, 30)
        val packet = BleProtocol.buildDataPacket(7, 500, payload)
        val (seq, offset, size) = BleProtocol.parseTransportHeader(packet)
        assertEquals(7, seq)
        assertEquals(500, offset)
        assertEquals(3, size)
    }

    // ==================== 字节工具 ====================
    @Test
    fun uint16LE_roundtrip() {
        val buf = ByteArray(2)
        BleProtocol.writeUInt16LE(buf, 0, 0x1234)
        assertEquals(0x1234, BleProtocol.readUInt16LE(buf, 0))
    }

    @Test
    fun uint32LE_roundtrip() {
        val buf = ByteArray(4)
        BleProtocol.writeUInt32LE(buf, 0, 0x12345678)
        assertEquals(0x12345678, BleProtocol.readUInt32LE(buf, 0))
    }

    @Test
    fun uint16LE_zeroAndMax() {
        val buf = ByteArray(2)
        BleProtocol.writeUInt16LE(buf, 0, 0)
        assertEquals(0, BleProtocol.readUInt16LE(buf, 0))
        BleProtocol.writeUInt16LE(buf, 0, 0xFFFF)
        assertEquals(0xFFFF, BleProtocol.readUInt16LE(buf, 0))
    }
}
