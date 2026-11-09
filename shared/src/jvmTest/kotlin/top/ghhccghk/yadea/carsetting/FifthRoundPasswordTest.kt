package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.isValidPassword

class FifthRoundPasswordTest {

    @Test
    fun passwordRulesMatchPwdUnlockHelper() {
        assertTrue(isValidPassword("1357"))
        assertFalse(isValidPassword("1234"))
        assertFalse(isValidPassword("4321"))
        assertFalse(isValidPassword("1111"))
        assertFalse(isValidPassword("123"))
        assertFalse(isValidPassword("12a4"))
    }

    @Test
    fun passwordSettingGoldenFrameMatchesSmali() {
        val frame = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand(
                commandType = "COMMAND_PASSWORD_UNLOCK_SETTING",
                commandId = 0x82,
                parameter = 1234,
            ),
        )
        assertEquals(
            "59443E2401000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF04D2E64B4A",
            requireNotNull(frame).joinToString("") {
                (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
            },
        )
    }
}
