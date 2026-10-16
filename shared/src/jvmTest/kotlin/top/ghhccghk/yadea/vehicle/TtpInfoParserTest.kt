package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 位域读取与 TtpInfo 解析的向量对拍。
 *
 * 偏移、位宽、无效值与字节序规则见 `docs/vehicle/ttpinfo-fields.md`。
 */
class TtpInfoParserTest {

    /** 构造一条足够长的报文，默认全部填 `0xFF`（即"全部无效"）。 */
    private fun packet(size: Int = 0xB0, fill: Byte = 0xFF.toByte()) = ByteArray(size) { fill }

    // ==================== 位域原语 ====================

    @Test
    fun m_returnsInvalidOnAllOnes() {
        val d = packet(0x10)
        assertEquals(-1, Ttp.m(d, 0x04))
    }

    @Test
    fun m_returnsInvalidWhenOutOfBounds() {
        val d = ByteArray(4)
        assertEquals(-1, Ttp.m(d, 0x04))
        assertEquals(-1, Ttp.m(d, -1))
    }

    @Test
    fun bits3_readsThreeBits() {
        // 0b0000_0101 → 低 3 位 = 5
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0000_0101.toByte()
        assertEquals(5, Ttp.C(d, 0x08, 0))
    }

    @Test
    fun bits3_treatsAllOnesAsInvalid() {
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0000_0111.toByte()
        assertEquals(-1, Ttp.C(d, 0x08, 0))
    }

    @Test
    fun bits2_readsTwoBitsAtShift() {
        // 0b0010_1100 → shift=2 取 0b11 = 3 → 无效
        val d = packet(0x10, 0x00)
        d[0x08] = 0b0010_1100.toByte()
        assertEquals(-1, Ttp.G(d, 0x08, 2))

        // 0b0010_0100 → shift=2 取 0b01 = 1
        d[0x08] = 0b0010_0100.toByte()
        assertEquals(1, Ttp.G(d, 0x08, 2))
    }

    @Test
    fun d_readsFourBits() {
        // 0b1010_0101 → shift=4 取 0b1010 = 10
        val d = packet(0x10, 0x00)
        d[0x0A] = 0b1010_0101.toByte()
        assertEquals(10, Ttp.d(d, 0x0A, 4))
    }

    @Test
    fun c_readsFiveBits() {
        val d = packet(0xB0, 0x00)
        // 0b0001_0011 → 低 5 位 = 0b10011 = 19
        d[0x4E] = 0b0001_0011.toByte()
        assertEquals(19, Ttp.c(d, 0x4E, 0))
    }

    @Test
    fun c_treatsAllOnesAsInvalid() {
        val d = packet(0xB0, 0x00)
        d[0x4E] = 0b0001_1111.toByte()
        assertEquals(-1, Ttp.c(d, 0x4E, 0))
    }

    /**
     * 1 位提取器的行为很特殊：位值为 `1` 时恰好等于掩码，因此被判为无效。
     *
     * 这是原实现的**忠实行为**（`l76.l` 里 `if (v == 1) return -1`），
     * 意味着该提取器只能表达“未置位”（返回 0）与“无效”（返回 -1）两种结果。
     * 移植时保留，不要“修正”成常规布尔语义。
     */
    @Test
    fun l_treatsSetBitAsInvalid() {
        val d = packet(0x20, 0x00)
        d[0x10] = 0b0000_0010.toByte()
        // shift=1：位值为 1 → 无效
        assertEquals(-1, Ttp.l(d, 0x10, 1))
        // shift=0：位值为 0 → 有效，返回 0
        assertEquals(0, Ttp.l(d, 0x10, 0))
    }

    // ==================== 多字节字节序 ====================

    @Test
    fun littleEndian16_decodesLowByteFirst() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x34
        d[0x01] = 0x12
        assertEquals(0x1234, Ttp.H(d, 0x00, 0x01))
    }

    @Test
    fun littleEndian16_treatsAllOnesAsInvalid() {
        val d = packet(0x10, 0xFF.toByte())
        assertEquals(-1, Ttp.H(d, 0x00, 0x01))
    }

    @Test
    fun bigEndian16_decodesHighByteFirst() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x12
        d[0x01] = 0x34
        assertEquals(0x1234, Ttp.J(d, 0x00, 0x01))
    }

    @Test
    fun endianness_differOnSameBytes() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x12
        d[0x01] = 0x34
        // 同一组字节，小端与大端结果不同 —— 误用会让数值"离谱但不崩溃"
        assertEquals(0x3412, Ttp.H(d, 0x00, 0x01))
        assertEquals(0x1234, Ttp.J(d, 0x00, 0x01))
    }

    @Test
    fun e_isLittleEndian32() {
        val d = packet(0x10, 0x00)
        d[0x00] = 0x78
        d[0x01] = 0x56
        d[0x02] = 0x34
        d[0x03] = 0x12
        assertEquals(0x12345678, Ttp.e(d, 0x00, 0x01, 0x02, 0x03))
    }

    // ==================== 缩放 ====================

    @Test
    fun scaleTenths_multiplies() {
        assertEquals(25.0f, Ttp.scaleTenths(250), 0.0001f)
    }

    @Test
    fun scaleTenths_skipsInvalid() {
        // 无效值必须原样保留，不能变成 -0.1f
        assertEquals(-1.0f, Ttp.scaleTenths(Ttp.INVALID_I), 0.0001f)
    }

    // ==================== 解析器 ====================

    @Test
    fun parse_allInvalidPacket() {
        val info = TtpInfoParser.parse(packet())
        assertEquals(-1, info.parkingStatus)
        assertEquals(-1, info.onOffStatus)
        assertEquals(-1.0f, info.currentSpeedValue, 0.0001f)
        assertEquals(-1.0f, info.totalBatteryVoltage, 0.0001f)
        assertTrue(info.faultCodes.isEmpty())
    }

    @Test
    fun parse_parkingStatus_fromByteEightLowBits() {
        val d = packet(0xB0, 0x00)
        d[0x08] = 0b0000_0001.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(1, info.parkingStatus)
    }

    @Test
    fun parse_onOffStatus_fromByte4E() {
        val d = packet(0xB0, 0x00)
        d[0x4E] = 0b0000_1011.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(11, info.onOffStatus)
    }

    @Test
    fun parse_currentSpeedValue_scalesByTenth() {
        val d = packet(0xB0, 0x00)
        // 250 → 25.0 km/h，小端
        d[0x4B] = 0xFA.toByte()
        d[0x4C] = 0x00
        val info = TtpInfoParser.parse(d)
        assertEquals(25.0f, info.currentSpeedValue, 0.0001f)
    }

    @Test
    fun parse_currentSpeedValue_invalidStaysNegativeOne() {
        val d = packet(0xB0, 0xFF.toByte())
        val info = TtpInfoParser.parse(d)
        assertEquals(-1.0f, info.currentSpeedValue, 0.0001f)
    }

    @Test
    fun parse_totalBatteryElectricity_subtractsThousand() {
        val d = packet(0xB0, 0x00)
        // 原始 12000 → 1200.0 - 1000.0 = 200.0
        d[0x52] = 0xE0.toByte()
        d[0x53] = 0x2E
        val info = TtpInfoParser.parse(d)
        assertEquals(200.0f, info.totalBatteryElectricity, 0.01f)
    }

    @Test
    fun parse_soc_invalidAboveTwoHundred() {
        val d = packet(0xB0, 0x00)
        d[0x57] = 0xC9.toByte() // 201
        val info = TtpInfoParser.parse(d)
        assertEquals(-1.0f, info.pBElectricitySoc, 0.0001f)
    }

    @Test
    fun parse_soc_scalesByHalf() {
        val d = packet(0xB0, 0x00)
        d[0x57] = 0x64 // 100 → 50.0
        val info = TtpInfoParser.parse(d)
        assertEquals(50.0f, info.pBElectricitySoc, 0.0001f)
    }

    @Test
    fun parse_drivingMemory_and_cruiseControl() {
        val d = packet(0xB0, 0x00)
        d[0x8E] = 0b0000_0010.toByte()
        d[0x8F] = 0b0000_0001.toByte()
        val info = TtpInfoParser.parse(d)
        assertEquals(2, info.drivingMemory)
        assertEquals(1, info.cruiseControl)
    }

    @Test
    fun parse_faultCodes_onlyWhenProtocolF2AndAmountPositive() {
        val d = packet(0xB0, 0x00)
        d[0x5A] = 0xF2.toByte()
        d[0x59] = 0x01 // 数量 = 1
        // 故障码用大端：0x01,0x02 → 0x0102
        d[0x5B] = 0x01
        d[0x5C] = 0x02
        val info = TtpInfoParser.parse(d)
        assertEquals(0x0102, info.faultCodes.first())
        assertEquals(8, info.faultCodes.size)
    }

    @Test
    fun parse_faultCodes_emptyWhenAmountZero() {
        val d = packet(0xB0, 0x00)
        d[0x5A] = 0xF2.toByte()
        d[0x59] = 0x00
        assertTrue(TtpInfoParser.parse(d).faultCodes.isEmpty())
    }

    // ==================== 短报文 ====================

    @Test
    fun parseShort_readsItsOwnFields() {
        val d = packet(0x10, 0x00)
        d[0x04] = 0b0100_0000.toByte() // bit6-7 = 1
        d[0x05] = 0b0000_0010.toByte() // bit0-1 = 2
        val info = TtpInfoParser.parseShort(d)
        assertEquals(1, info.batteryMutualRecognition)
        assertEquals(2, info.speedLimitReleased)
    }

    @Test
    fun parseShort_invalidOnAllOnes() {
        val info = TtpInfoParser.parseShort(packet(0x10))
        assertEquals(-1, info.batteryMutualRecognition)
        assertEquals(-1, info.speedLimitReleased)
    }
}
