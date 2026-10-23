package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 车辆帧与十六进制工具的向量对拍。
 *
 * 期望值来自 `BleBicycleCommandManager.smali` 的逐行核对，
 * 具体规则见 `docs/vehicle/protocol.md`。
 */
class VehicleFrameTest {

    // ==================== decInt2hexString ====================

    @Test
    fun decHex_padsOddLengthAndUppercases() {
        // 奇数长度补一个 0
        assertEquals("01", 1.toDecHex())
        assertEquals("0F", 15.toDecHex())
        assertEquals("03", 3.toDecHex())
        // 偶数长度原样，只转大写
        assertEquals("10", 16.toDecHex())
        assertEquals("1E", 0x1E.toDecHex())
        assertEquals("FF", 255.toDecHex())
        // 三位奇数长度只补一个 0
        assertEquals("0100", 256.toDecHex())
    }

    @Test
    fun checkHex_padsOnlySingleDigitAndKeepsCase() {
        assertEquals("05", 5.toCheckHex())
        assertEquals("0a", 0x0A.toCheckHex())
        // 两位以上原样，不转大写
        assertEquals("2a", 0x2A.toCheckHex())
        assertEquals("100", 256.toCheckHex())
    }

    // ==================== 字节工具 ====================

    @Test
    fun hexRoundTrip() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x59, 0x44, 0xFF.toByte())
        assertEquals("000F5944FF", bytes.toHex())
        assertEquals(bytes.toList(), "000F5944FF".hexToBytes()?.toList())
    }

    @Test
    fun hexToBytes_rejectsOddLength() {
        assertNull("ABC".hexToBytes())
    }

    @Test
    fun hexToBytes_rejectsInvalidChars() {
        assertNull("ZZ".hexToBytes())
    }

    @Test
    fun hexToBytes_acceptsLowercase() {
        assertEquals(listOf<Byte>(0x59, 0x44), "5944".lowercase().hexToBytes()?.toList())
    }

    // ==================== binaryString2hexString ====================

    @Test
    fun binaryToHex_requiresMultipleOfEight() {
        assertNull("0000".binaryToHex())
        assertNull("0000000".binaryToHex())
        assertNull("".binaryToHex())
    }

    @Test
    fun binaryToHex_producesOneCharPerNibble() {
        // 每 4 位二进制产生 1 个 hex 字符，因此 8 位 → 2 个字符
        assertEquals("0f", "00001111".binaryToHex())
        assertEquals("3f", "00111111".binaryToHex())
        assertEquals("00", "00000000".binaryToHex())
        // 不做大小写转换
        assertEquals("0f", "00001111".binaryToHex())
    }

    @Test
    fun binaryToHex_rejectsNonBinaryChars() {
        assertNull("0000111x".binaryToHex())
    }

    // ==================== 校验 ====================

    @Test
    fun sum8_matchesHandCalculation() {
        val body = byteArrayOf(0x3E, 0x03, 0x07, 0x01, 0x01)
        assertEquals(0x4A, Checksums.sum8(body))
    }

    @Test
    fun sum8_wrapsAt256() {
        assertEquals(0x00, Checksums.sum8(byteArrayOf(0xFF.toByte(), 0x01)))
    }

    @Test
    fun crc16Xmodem_knownVector() {
        // 标准 XMODEM 对拍向量
        assertEquals(0x31C3, Checksums.crc16Xmodem("123456789".encodeToByteArray()))
    }

    @Test
    fun crc16XmodemHex_padsToTwoThenFour() {
        // 结果不足 2 位时补 0，恰好 2 位时再前补 "00"
        val hex = Checksums.crc16XmodemHex("123456789".encodeToByteArray())
        assertEquals("31c3", hex)
    }

    @Test
    fun crc16XmodemHex_padsEmptyInputToFour() {
        // 空输入 → CRC 为 0 → toHexString 得 "0"，补到 "00"，长度 2 时再前补 "00"
        assertEquals("0000", Checksums.crc16XmodemHex(ByteArray(0)))
    }

    @Test
    fun scooterEncrypt_xorWithIndexPlusTen() {
        val frame = byteArrayOf(0x5A, 0x23, 0x02)
        val out = Checksums.scooterEncrypt(frame)
        assertEquals((0x5A xor 10).toByte(), out[0])
        assertEquals((0x23 xor 11).toByte(), out[1])
        assertEquals((0x02 xor 12).toByte(), out[2])
    }

    @Test
    fun xorFold_foldsOnlyGivenLength() {
        val data = ByteArray(32) { 0x01 }
        // 前 31 个 0x01 异或 → 0x01
        assertEquals(0x01.toByte(), Checksums.xorFold(data, 31))
        // 前 2 个 0x01 异或 → 0x00
        assertEquals(0x00.toByte(), Checksums.xorFold(data, 2))
    }

    // ==================== 组帧 ====================

    @Test
    fun build_lockFrame() {
        // payload "070101" → 3 字节 → 长度字段 "03"
        // 校验 = (0x3E + 0x03 + 0x07 + 0x01 + 0x01) & 0xFF = 0x4A
        val frame = VehicleFrame.build(VehicleFrame.ORDER_ID, "070101")
        assertEquals("59443E030701014A4B4A", frame)
    }

    @Test
    fun build_rejectsOddLengthPayload() {
        assertNull(VehicleFrame.build(VehicleFrame.ORDER_ID, "07010"))
    }

    @Test
    fun build_rejectsNonHexPayload() {
        assertNull(VehicleFrame.build(VehicleFrame.ORDER_ID, "ZZZZ"))
    }

    @Test
    fun build_byteArrayOverloadMatchesHexOverload() {
        val fromHex = VehicleFrame.build(VehicleFrame.ORDER_ID, "070101")
        val fromBytes = VehicleFrame.build(VehicleFrame.ORDER_ID, ClassicCommands.LOCK)
        assertEquals(fromHex, fromBytes?.toHex())
    }

    @Test
    fun build_checksumCoversOrderIdAndLength() {
        val body = "3E" + 3.toDecHex() + "070101"
        // 校验字段不转大写，由组帧时统一处理
        assertEquals("4a", VehicleFrame.checksum(body))
    }

    // ==================== 3B 专用帧 ====================

    @Test
    fun reverseHexPairs_reversesByTwoChars() {
        assertEquals("DDCCBBAA", VehicleFrame.reverseHexPairs("AABBCCDD"))
        assertEquals("4433", VehicleFrame.reverseHexPairs("3344"))
    }

    @Test
    fun reverseHexPairs_rejectsOddLength() {
        assertNull(VehicleFrame.reverseHexPairs("AABBCCD"))
    }

    @Test
    fun build3B_rejectsOtherCommandTypes() {
        assertNull(VehicleFrame.build3B(0x13A, "AABBCCDD", 0))
    }

    @Test
    fun build3B_producesFrameWithMarkerAndPadding() {
        val frame = VehicleFrame.build3B(0x13B, "AABBCCDD", 2)
        // 载荷 = "DDCCBBAA"(4B) + "FFFFFFFFFF"(5B) + "FFFF"(2B) = 11 字节 → 长度 "0B"
        val expected = "59443B" + "0B" + "DDCCBBAAFFFFFFFFFF" + "FFFF" + "4D" + "4B4A"
        assertEquals(expected, frame?.toHex())
    }

    @Test
    fun increaseBytesHex_clampsToTen() {
        assertEquals("", VehicleFrame.increaseBytesHex(0))
        assertEquals("FFFFFF", VehicleFrame.increaseBytesHex(3))
        assertEquals("FF".repeat(10), VehicleFrame.increaseBytesHex(20))
        assertEquals("", VehicleFrame.increaseBytesHex(-5))
    }

    // ==================== 经典命令 ====================

    @Test
    fun classicCommands_byteValues() {
        assertEquals("070104", ClassicCommands.ON.toHex())
        assertEquals("07010E", ClassicCommands.OFF.toHex())
        assertEquals("070101", ClassicCommands.LOCK.toHex())
        assertEquals("070102", ClassicCommands.UNLOCK.toHex())
        assertEquals("070103", ClassicCommands.WHISTLE.toHex())
        assertEquals("07010D", ClassicCommands.DETECT.toHex())
        assertEquals("07010A", ClassicCommands.ODO_RESET.toHex())
        assertEquals("070109", ClassicCommands.CUSHION.toHex())
    }

    @Test
    fun classicCommands_dispatchById() {
        assertEquals(ClassicCommands.ON.toList(), ClassicCommands.byCommandId(0x08)?.toList())
        assertEquals(ClassicCommands.OFF.toList(), ClassicCommands.byCommandId(0x07)?.toList())
        assertEquals(ClassicCommands.LOCK.toList(), ClassicCommands.byCommandId(0x06)?.toList())
        assertEquals(ClassicCommands.UNLOCK.toList(), ClassicCommands.byCommandId(0x04)?.toList())
        assertEquals(ClassicCommands.WHISTLE.toList(), ClassicCommands.byCommandId(0x03)?.toList())
        assertEquals(ClassicCommands.DETECT.toList(), ClassicCommands.byCommandId(0x05)?.toList())
        assertEquals(ClassicCommands.ODO_RESET.toList(), ClassicCommands.byCommandId(0x31)?.toList())
        assertEquals(ClassicCommands.CUSHION.toList(), ClassicCommands.byCommandId(0x09)?.toList())
        // 氛围灯走独立载荷构造，分派表里不返回裸载荷
        assertNull(ClassicCommands.byCommandId(0x4C))
        assertNull(ClassicCommands.byCommandId(0x00))
    }

    @Test
    fun vehicleControlProtocol_matchesBicycleControlHelperCommands() {
        fun commandByte(command: VehicleControlCommand, address: String): String =
            VehicleControlProtocol.encode(
                command = command,
                protocolVersion = 1,
                bikeBondTypeHex = address,
            ).toHex().substring(22, 24)

        assertEquals("00", commandByte(VehicleControlCommand.POWER_ON, "C8"))
        assertEquals("03", commandByte(VehicleControlCommand.POWER_OFF, "C8"))
        assertEquals("01", commandByte(VehicleControlCommand.UNLOCK, "C8"))
        assertEquals("04", commandByte(VehicleControlCommand.LOCK, "C8"))
        assertEquals("FD", commandByte(VehicleControlCommand.FIND, "C8"))
        assertEquals("07", commandByte(VehicleControlCommand.CUSHION, "C8"))
    }
}
