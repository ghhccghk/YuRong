package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSettingsConfIdMappingTest {

    @Test
    fun reversingAndStrollerModesAreToggles() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "43", confName = "倒车模式"),
                CloudBikeConfig(confId = "44", confName = "婴儿车模式"),
            ),
            values = emptyMap(),
        )

        val items = CloudCarSettingMapper.toItems(cloud).associateBy { it.configId }

        assertTrue(items.getValue("43").kind is CarSettingKind.Toggle)
        assertTrue(items.getValue("44").kind is CarSettingKind.Toggle)
        assertTrue(
            VehicleSettingCommands.forConfigId(
                "43",
                CarSettingKind.Toggle(false),
            ) != null,
        )
        assertTrue(
            VehicleSettingCommands.forConfigId(
                "44",
                CarSettingKind.Toggle(false),
            ) != null,
        )
    }

    @Test
    fun backHomeLightSupportsSwitchAndTimeChoice() {
        val cloud = CloudCarSettings(
            configs = listOf(CloudBikeConfig(confId = "21", confName = "伴我回家灯")),
            values = emptyMap(),
        )

        val items = CloudCarSettingMapper.toItems(cloud).associateBy { it.id }

        assertTrue(items.getValue("cloud:21").kind is CarSettingKind.Toggle)
        val time = items.getValue("cloud:21:time").kind as CarSettingKind.Choice
        assertEquals(listOf("15 秒", "30 秒", "45 秒", "60 秒"), time.options)
        assertTrue(VehicleSettingCommands.forConfigId("21", CarSettingKind.Toggle(true)) != null)
        assertTrue(VehicleSettingCommands.forConfigId("21", CarSettingKind.Choice(0, time.options)) != null)
    }
}