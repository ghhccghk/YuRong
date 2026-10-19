package top.ghhccghk.yadea.ota

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOtaProtocolTest {

    @Test
    fun authPacketMatchesSmaliFormula() {
        assertArrayEquals(
            byteArrayOf(
                0x59,
                0x44,
                0xF0.toByte(),
                0x02,
                0x1A,
                0x64,
                0x70,
                0x4B,
                0x4A,
            ),
            WifiOtaProtocol.buildAuthPacket(firmwareVersion = 0x1A, localIpLastOctet = 0x64),
        )
    }

    @Test
    fun firmwareHeaderUsesFileTypeAsAuthVersion() {
        val firmware = ByteArray(64)
        val header = byteArrayOf(
            0x59, 0x44, 0x46,
            0x01, 0x02, 0x03,
            0x00, 0x00,
            0x12, 0x34, 0x56, 0x78,
            0x00, 0x00, 0x10, 0x00,
            0x01, 0x02, 0x03, 0x04,
            0x59, 0x44, 0x00, 0x00,
            0x38, 0x38,
            0x1A,
        )
        header.copyInto(firmware)
        val parsed = requireNotNull(WifiOtaProtocol.parseFirmwareHeader(firmware))
        assertEquals("YDF", parsed.fileFormat)
        assertEquals("010203", parsed.productType)
        assertEquals("12345678", parsed.crc32)
        assertEquals("00001000", parsed.fileLength)
        assertEquals("01020304", parsed.fileVersion)
        assertEquals("3838", parsed.manufactureCode)
        assertEquals("1A", parsed.fileType)
        assertEquals(0x1A, parsed.authVersionByte)
    }

    @Test
    fun shortFirmwareHeaderIsRejected() {
        assertNull(WifiOtaProtocol.parseFirmwareHeader(ByteArray(63)))
    }

    @Test
    fun rangeSupportsOpenEndAndClamps() {
        assertEquals(
            WifiByteRange(100L, 8191L),
            WifiOtaProtocol.parseRange("bytes=100-8191", 10_000L),
        )
        assertEquals(
            WifiByteRange(100L, 9_999L),
            WifiOtaProtocol.parseRange("bytes=100-", 10_000L),
        )
        assertEquals(
            WifiByteRange(100L, 9_999L),
            WifiOtaProtocol.parseRange("bytes=100-20000", 10_000L),
        )
        assertNull(WifiOtaProtocol.parseRange("bytes=10000-", 10_000L))
        assertNull(WifiOtaProtocol.parseRange("bytes=20-10", 10_000L))
        assertNull(WifiOtaProtocol.parseRange("items=0-1", 10_000L))
    }

    @Test
    fun finishQueryRequiresExactOne() {
        assertTrue(WifiOtaProtocol.queryContainsFinish("x=1&finish=1"))
        assertFalse(WifiOtaProtocol.queryContainsFinish("finish=0"))
        assertFalse(WifiOtaProtocol.queryContainsFinish("finish=10"))
        assertFalse(WifiOtaProtocol.queryContainsFinish(null))
    }

    @Test
    fun authResponseAcceptsBothSmaliConstants() {
        assertTrue(
            WifiOtaProtocol.isAuthAccepted(
                byteArrayOf(0x59, 0x44, 0xF8.toByte(), 0x00, 0xF8.toByte(), 0x4B, 0x4A),
            ),
        )
        assertTrue(
            WifiOtaProtocol.isAuthAccepted(
                byteArrayOf(0x59, 0x44, 0xF9.toByte(), 0x00, 0xF9.toByte(), 0x4B, 0x4A),
            ),
        )
        assertFalse(
            WifiOtaProtocol.isAuthAccepted(
                byteArrayOf(0x59, 0x44, 0xF8.toByte(), 0x00, 0xF0.toByte(), 0x4B, 0x4A),
            ),
        )
    }

    @Test
    fun dvrNameUsesLastEightMacDigits() {
        assertEquals(
            "YD43123456DVR",
            WifiOtaProtocol.dvrP2pName("A4:C1:43:12:34:56"),
        )
    }
}
