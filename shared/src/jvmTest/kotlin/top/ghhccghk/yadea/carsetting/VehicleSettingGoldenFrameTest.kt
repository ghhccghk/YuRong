package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Golden frames transcribed from `BleBicycleCommandManager.generateBleCommand`.
 *
 * Evidence boundaries:
 * - frame wrapper/checksum: `BleBicycleCommandManager.smali:125-189,5147-5208`
 * - fixed suffixes: `:4974-4985`, `:3884-3895`, `:3655-3665`
 * - parameter byte 0x36: `:4630-4660`
 * - `uj.c` bitfields used by 0xD2/0xD3: `BleBicycleCommandManager.smali:2456-2484`,
 *   `uj.smali:152-264`
 */
class VehicleSettingGoldenFrameTest {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

    private fun assertFrame(
        commandId: Int,
        parameter: Int = -1,
        expected: String,
    ) {
        val frame = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand(
                commandType = "GOLDEN",
                commandId = commandId,
                parameter = parameter,
            ),
        )
        assertNotNull("missing encoder for 0x${commandId.toString(16)}", frame)
        assertEquals(expected, hex(requireNotNull(frame)))
    }

    @Test
    fun fixedCommandSuffixesMatchOriginalFrames() {
        assertFrame(
            0x0F,
            expected = "59443E1E01000101C8FFFFFFFFFFDFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEE4B4A",
        )
        assertFrame(
            0x10,
            expected = "59443E1E01000101C8FFFFFFFFFFCFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDE4B4A",
        )
        assertFrame(
            0x11,
            expected = "59443E1E01000101C8FFFFFFFFFF7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF8E4B4A",
        )
        assertFrame(
            0x12,
            expected = "59443E1E01000101C8FFFFFFFFFF3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF4E4B4A",
        )
        assertFrame(
            0x13,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFDFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF0C4B4A",
        )
        assertFrame(
            0x14,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFCFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF0B4B4A",
        )
        assertFrame(
            0x53,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFFEE4B4A",
        )
        assertFrame(
            0x54,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFCFFFFFFFFFFFFFFFDE4B4A",
        )
        assertFrame(
            0x62,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFFFF7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF8E4B4A",
        )
        assertFrame(
            0x63,
            expected = "59443E1E01000101C8FFFFFFFFFFFFFFFF3FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF4E4B4A",
        )
    }

    @Test
    fun parameterCommandGoldenFramesMatchOriginalFrames() {
        assertFrame(
            0x36,
            parameter = 80,
            expected = "59443E1E01000101C8FFFFFFFF50FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5F4B4A",
        )
        assertFrame(
            0xD2,
            parameter = 1,
            expected = "59443E3601000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFEE4B4A",
        )
        assertFrame(
            0xD2,
            parameter = 0,
            expected = "59443E3601000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFCFDE4B4A",
        )
        assertFrame(
            0xD3,
            parameter = 2,
            expected = "59443E3601000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFBFCE4B4A",
        )
    }
}
