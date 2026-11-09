package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

class FourthRoundVehicleSettingsTest {

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

    private fun frame(command: VehicleSettingCommand): String =
        hex(requireNotNull(VehicleSettingFrameEncoder.encode(command)))

    @Test
    fun oneKeyAndPasswordGoldenFramesMatchSmali() {
        assertEquals(
            "59443E1E0100010148FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF8E4B4A",
            frame(VehicleSettingCommand("NEAR_UNLOCK_OPEN", 0x0D)),
        )
        assertEquals(
            "59443E1E0100010108FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF4E4B4A",
            frame(VehicleSettingCommand("NEAR_UNLOCK_CLOSE", 0x0E)),
        )
        assertEquals(
            "59443E1E01000101C8FAFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF094B4A",
            frame(VehicleSettingCommand("NEARBY_BLE_UNLOCK", 0x37, parameter = 2)),
        )
        assertEquals(
            "59443E1E01000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF7FFFFFFF8E4B4A",
            frame(VehicleSettingCommand("COMMAND_PASSWORD_UNLOCK_OPEN", 0x80)),
        )
    }

    @Test
    fun atmosphereGoldenFrameMatchesMultiBleCommandLayout() {
        val command = VehicleSettingCommand(
            commandType = "ATMOSPHERE_CONFIG",
            commandId = 0xF7,
            parameters = listOf(3, 1, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 5),
        )
        assertEquals(
            "59443E4601000101C8FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF112233FF445566EB674B4A",
            frame(command),
        )
    }

    @Test
    fun fourthRoundMappingsUseStableIdsAndConfirmedCommands() {
        val enable = CarSettingItem(
            id = "cloud:1:enable",
            configId = "1",
            title = "",
            summary = "",
            classification = CarSettingClassification.POWER,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        )
        val distance = enable.copy(
            id = "cloud:1:distance",
            kind = CarSettingKind.Slider(7f, 0f..7f, steps = 6),
        )
        val password = enable.copy(id = "cloud:25", configId = "25", kind = CarSettingKind.Toggle(false))
        val atmosphere = enable.copy(
            id = "cloud:73:config",
            configId = "73",
            kind = CarSettingKind.Atmosphere(showMode = 3, runMode = 5, singleColor = false),
        )

        assertTrue(CarSettingItemAccess.canWrite(enable))
        assertTrue(CarSettingItemAccess.canWrite(distance))
        assertTrue(CarSettingItemAccess.canWrite(password))
        assertTrue(CarSettingItemAccess.canWrite(atmosphere))
        assertEquals(0x0D, VehicleSettingCommands.forItem(enable)?.commandId)
        assertEquals(0x37, VehicleSettingCommands.forItem(distance)?.commandId)
        assertEquals(7, VehicleSettingCommands.forItem(distance)?.parameter)
        assertEquals(0x81, VehicleSettingCommands.forItem(password)?.commandId)
        assertEquals(0xF7, VehicleSettingCommands.forItem(atmosphere)?.commandId)
    }

    @Test
    fun atmosphereCommandClampsInvalidRgbMarkerAndKeepsRunMode() {
        val item = CarSettingItem(
            id = "cloud:73:config",
            configId = "73",
            title = "",
            summary = "",
            classification = CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Atmosphere(
                showMode = 3,
                runMode = 5,
                singleColor = false,
                red1 = 255,
                green1 = 255,
                blue1 = 255,
                red2 = 255,
                green2 = 255,
                blue2 = 255,
            ),
        )
        val command = requireNotNull(VehicleSettingCommands.forItem(item))
        assertEquals(listOf(3, 1, 254, 254, 254, 254, 254, 254, 5), command.parameters)
    }

    @Test
    fun fourthRoundTtpFieldsMatchSmaliBitRanges() {
        val bytes = ByteArray(0xB0) { 0xFF.toByte() }
        bytes[0x15] = 0x01
        bytes[0x16] = 0x05
        bytes[0x31] = 0x14
        val status = TtpInfoParser.parse(bytes).extended
        assertEquals(1, status.bleKeyStatus)
        assertEquals(5, status.bleKeyDistanceLevel)
        assertEquals(1, status.passwordUnlockStatus)
        assertEquals(1, status.passwordUnlockSettingResult)
    }

    @Test
    fun cloudMapperSplitsOneKeyAndAddsAtmosphereConfig() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "1", confName = "一键开机"),
                CloudBikeConfig(confId = "25", confName = "密码解锁"),
                CloudBikeConfig(confId = "73", confName = "氛围灯"),
            ),
            values = mapOf("passwordUnlockEnable" to "1"),
        )
        val items = CloudCarSettingMapper.toItems(cloud)
        assertFalse(items.any { it.id == "cloud:1" })
        assertNotNull(items.firstOrNull { it.id == "cloud:1:enable" })
        assertNotNull(items.firstOrNull { it.id == "cloud:1:distance" })
        assertNotNull(items.firstOrNull { it.id == "cloud:25" })
        assertNotNull(items.firstOrNull { it.id == "cloud:73:config" })
    }
}
