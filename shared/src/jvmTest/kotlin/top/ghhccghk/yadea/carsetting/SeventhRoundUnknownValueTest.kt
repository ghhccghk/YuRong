package top.ghhccghk.yadea.carsetting

import org.junit.Assert.assertFalse
import org.junit.Test

class SeventhRoundUnknownValueTest {
    @Test
    fun alarmSensitivityAndVehicleVolumeStartUnknownUntilTtpArrives() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "14", confName = "车辆报警灵敏度"),
                CloudBikeConfig(confId = "20", confName = "车辆音量"),
            ),
            values = emptyMap(),
        )
        val items = CloudCarSettingMapper.toItems(cloud).associateBy { it.configId }

        listOf("14", "20").forEach { configId ->
            val slider = items.getValue(configId).kind as CarSettingKind.Slider
            assertFalse("confId $configId must not expose 0 as a known vehicle value", slider.valueKnown)
        }
    }

    @Test
    fun delayedLightTimesStartUnknownUntilTtpArrives() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "21", confName = "伴我回家灯"),
                CloudBikeConfig(confId = "81", confName = "大灯延时"),
            ),
            values = emptyMap(),
        )
        val items = CloudCarSettingMapper.toItems(cloud).associateBy { it.id }

        listOf("cloud:21:time", "cloud:81:time").forEach { itemId ->
            val choice = items.getValue(itemId).kind as CarSettingKind.Choice
            assertFalse("$itemId must not expose option 0 as a known vehicle value", choice.valueKnown)
        }
    }

    @Test
    fun readbackBackedChoicesStartUnknownUntilTtpArrives() {
        val cloud = CloudCarSettings(
            configs = listOf(
                CloudBikeConfig(confId = "27", confName = "设防模式"),
                CloudBikeConfig(confId = "31", confName = "速度单位"),
                CloudBikeConfig(confId = "54", confName = "电压设置"),
            ),
            values = emptyMap(),
        )
        val items = CloudCarSettingMapper.toItems(cloud).associateBy { it.configId }

        listOf("27", "31", "54").forEach { configId ->
            val choice = items.getValue(configId).kind as CarSettingKind.Choice
            assertFalse("confId $configId must not expose option 0 as a known vehicle value", choice.valueKnown)
        }
    }
}
