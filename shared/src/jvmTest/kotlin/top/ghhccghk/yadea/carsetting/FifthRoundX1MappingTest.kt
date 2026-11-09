package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.hexToBytes

class FifthRoundX1MappingTest {

    @Test
    fun x1ConfigIdsRemainDistinctAndSplitStably() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "001", confName = "X1 距离单位"),
                CloudBikeConfig(confId = "002", confName = "X1 感应自动开关机"),
                CloudBikeConfig(confId = "003", confName = "X1 制动能量回收"),
            ),
            values = emptyMap(),
        )
        val items = CloudCarSettingMapper.toItems(cloud)
        assertTrue(items.any { it.id == "cloud:001" && it.kind is CarSettingKind.Choice })
        assertFalse(items.any { it.id == "cloud:002" })
        assertFalse(items.any { it.id == "cloud:003" })
        assertTrue(items.any { it.id == "cloud:002:enable" })
        assertTrue(items.any { it.id == "cloud:002:level" })
        assertTrue(items.any { it.id == "cloud:003:enable" })
        assertTrue(items.any { it.id == "cloud:003:level" })
    }

    @Test
    fun x1CommandsMatchX1VehicleSettingViewModel() {
        val items = listOf(
            CarSettingItem(
                id = "cloud:001",
                configId = "001",
                title = "",
                summary = "",
                classification = CarSettingClassification.METER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(1, listOf("km/h", "mph")),
            ),
            CarSettingItem(
                id = "cloud:002:enable",
                configId = "002",
                title = "",
                summary = "",
                classification = CarSettingClassification.POWER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(true),
            ),
            CarSettingItem(
                id = "cloud:002:level",
                configId = "002",
                title = "",
                summary = "",
                classification = CarSettingClassification.POWER,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(2f, 0f..2f, steps = 1),
            ),
            CarSettingItem(
                id = "cloud:003:enable",
                configId = "003",
                title = "",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(false),
            ),
            CarSettingItem(
                id = "cloud:003:level",
                configId = "003",
                title = "",
                summary = "",
                classification = CarSettingClassification.RIDE_CONTROL,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(3f, 1f..3f, steps = 1),
            ),
        )
        val commands = items.map { requireNotNull(VehicleSettingCommands.forItem(it)) }
        assertEquals(listOf(0x8A, 0x5C, 0x5E, 0x16, 0x3D), commands.map { it.commandId })
        assertEquals(2, commands[2].parameter)
        assertEquals(3, commands[4].parameter)
        commands.forEach { assertNotNull(VehicleSettingFrameEncoder.encode(it)) }
    }

    @Test
    fun x1TtpFieldsMatchSmaliBitRanges() {
        val bytes = ByteArray(0xB0) { 0xFF.toByte() }
        bytes[0x1A] = ((bytes[0x1A].toInt() and 0x3F) or (1 shl 6)).toByte()
        bytes[0x1B] = ((bytes[0x1B].toInt() and 0xF8) or 2).toByte()
        val status = TtpInfoParser.parse(bytes).extended
        assertEquals(1, status.bleAutoTurnOnOffStatus)
        assertEquals(2, status.bleAutoTurnOnOffGearStatus)
    }
}
