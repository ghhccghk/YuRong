package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SixthRoundActivityBatchTest {

    @Test
    fun scheduledShutdownTimeMatchesSeekBarSemantics() {
        val cloud = CloudCarSettings(
            configs = listOf(CloudBikeConfig(confId = "15", confName = "离车定时关机")),
            values = mapOf("shutDownTime" to "7"),
        )
        val item = CloudCarSettingMapper.toItems(cloud).first { it.id == "cloud:15:time" }
        val slider = item.kind as CarSettingKind.Slider
        assertEquals(7f, slider.value)
        assertEquals(0f, slider.range.start)
        assertEquals(20f, slider.range.endInclusive)

        val command = requireNotNull(
            VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = 12f))),
        )
        assertEquals(0x7D, command.commandId)
        assertEquals(12, command.parameter)
    }

    @Test
    fun secondBatchCommandMappingsRemainStable() {
        val cases = listOf(
            Triple("cloud:3", CarSettingKind.Toggle(true), 0x11),
            Triple("cloud:4", CarSettingKind.Slider(80f, 15f..120f, steps = 105), 0x36),
            Triple("cloud:20", CarSettingKind.Slider(5f, 0f..7f, steps = 7), 0x46),
            Triple("cloud:36", CarSettingKind.Toggle(false), 0x9C),
            Triple("cloud:53", CarSettingKind.Toggle(true), 0xC7),
        )
        cases.forEach { (id, kind, commandId) ->
            val item = CarSettingItem(
                id = id,
                configId = id.removePrefix("cloud:"),
                title = "",
                summary = "",
                classification = CarSettingClassification.OTHER,
                channel = CarSettingChannel.BLE,
                kind = kind,
            )
            val command = requireNotNull(VehicleSettingCommands.forItem(item))
            assertEquals(commandId, command.commandId)
            assertTrue(CarSettingItemAccess.canWrite(item))
        }
    }
}
