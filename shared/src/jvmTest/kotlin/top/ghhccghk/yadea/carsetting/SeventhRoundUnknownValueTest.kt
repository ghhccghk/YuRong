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
}
