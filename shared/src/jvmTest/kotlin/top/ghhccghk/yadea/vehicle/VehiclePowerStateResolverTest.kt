package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehiclePowerStateResolverTest {

    private fun packet(fill: Byte = 0x00) = ByteArray(0xC0) { fill }

    @Test
    fun ttpPowerAndMechanicalKeyStatesAreResolved() {
        val onPacket = packet().also {
            it[0x4E] = 0x01 // onOffStatus = 已开机
            it[0x4D] = 0x01 // realKeyStatus = 机械钥匙已开机
        }
        val onState = VehiclePowerStateResolver.resolve(
            status = TtpInfoParser.parse(onPacket),
            cloudPowerState = 0,
            cloudLockStatus = 0,
        )
        assertEquals(true, onState.powerOn)
        assertEquals(true, onState.keyOn)

        val offPacket = packet().also {
            it[0x4E] = 0x08 // onOffStatus = 已关机
            it[0x4D] = 0x00
        }
        val offState = VehiclePowerStateResolver.resolve(
            status = TtpInfoParser.parse(offPacket),
        )
        assertEquals(false, offState.powerOn)
        assertEquals(false, offState.keyOn)
    }

    @Test
    fun cloudStateIsUsedWhenTtpIsMissing() {
        val state = VehiclePowerStateResolver.resolve(
            status = null,
            cloudPowerState = 1,
            cloudLockStatus = 1,
        )
        assertEquals(true, state.powerOn)
        assertEquals(true, state.keyOn)
    }

    @Test
    fun unknownStatesRemainNull() {
        val state = VehiclePowerStateResolver.resolve(status = null)
        assertEquals(null, state.powerOn)
        assertEquals(null, state.keyOn)
    }
}
