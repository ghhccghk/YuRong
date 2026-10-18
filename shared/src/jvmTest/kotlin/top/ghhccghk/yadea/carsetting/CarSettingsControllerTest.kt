package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarSettingsControllerTest {
    private fun items() = CarSettingsRegistry.defaultItems()

    private class FakeWriter(
        private val result: Boolean,
    ) : CarSettingWriter {
        var last: CarSettingItem? = null
        override suspend fun write(item: CarSettingItem): Boolean {
            last = item
            return result
        }
    }

    @Test
    fun success_updatesOptimisticallyAndClearsPending() = runBlocking {
        val writer = FakeWriter(true)
        val controller = CarSettingsController(items(), writer)

        val ok = controller.update("security.auto_lock") { kind ->
            (kind as CarSettingKind.Toggle).copy(checked = false)
        }

        assertTrue(ok)
        val item = controller.state.value.items.first { it.id == "security.auto_lock" }
        assertEquals(false, (item.kind as CarSettingKind.Toggle).checked)
        assertTrue(controller.state.value.pendingIds.isEmpty())
        assertNotNull(writer.last)
    }

    @Test
    fun failure_rollsBackAndReportsMessage() = runBlocking {
        val controller = CarSettingsController(items(), FakeWriter(false))
        val before = controller.state.value.items.first { it.id == "ride.cruise" }

        val ok = controller.update("ride.cruise") { kind ->
            (kind as CarSettingKind.Toggle).copy(checked = true)
        }

        assertFalse(ok)
        val after = controller.state.value.items.first { it.id == "ride.cruise" }
        assertEquals(before.kind, after.kind)
        assertNotNull(controller.state.value.message)
        assertTrue(controller.state.value.pendingIds.isEmpty())
    }

    @Test
    fun unavailableWriter_rejectsWithoutChangingValue() = runBlocking {
        val controller = CarSettingsController(items(), writer = null)
        val before = controller.state.value.items.first { it.id == "battery.charge_limit" }

        val ok = controller.update("battery.charge_limit") { kind ->
            (kind as CarSettingKind.Slider).copy(value = 80f)
        }

        assertFalse(ok)
        val after = controller.state.value.items.first { it.id == "battery.charge_limit" }
        assertEquals(before.kind, after.kind)
        assertEquals("车辆设置通道未接入", controller.state.value.message)
    }

    @Test
    fun smaliCommandMapping_usesConfirmedIdsAndParameters() {
        val items = items().associateBy { it.id }
        assertEquals(
            VehicleSettingCommand("LEAVE_THE_CAR_AUTO_LOCK_ON", 0x53),
            VehicleSettingCommands.forItem(items.getValue("security.auto_lock")),
        )
        assertEquals(
            VehicleSettingCommand("WARING_SOUND_CLOSE", 0x63),
            VehicleSettingCommands.forItem(
                items.getValue("light.find_sound").copy(
                    kind = CarSettingKind.Toggle(false),
                ),
            ),
        )
        assertEquals(
            VehicleSettingCommand("BRAKE_RECOVER_LEVEL", 0x3D, parameter = 3),
            VehicleSettingCommands.forItem(
                items.getValue("ride.recover").copy(
                    kind = CarSettingKind.Choice(2, listOf("弱", "中", "强")),
                ),
            ),
        )
        assertEquals(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_VALUE", 0x134, parameter = 80),
            VehicleSettingCommands.forItem(items.getValue("meter.brightness")),
        )
        assertEquals(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_SWITCH", 0x135, parameter = 1),
            VehicleSettingCommands.forItem(items.getValue("meter.auto_brightness")),
        )
    }

    @Test
    fun frameEncoder_buildsVehicleFramesForMappedCommands() {
        val encoded = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand("LEAVE_THE_CAR_AUTO_LOCK_ON", 0x53),
        )
        assertNotNull(encoded)
        val hex = encoded!!.joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
        }
        assertTrue(hex.startsWith("59443E"))
        assertTrue(hex.endsWith("4B4A"))
        assertTrue(
            hex.contains(
                "01000101F8" +
                    "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFF",
            ),
        )

        val brightness = VehicleSettingFrameEncoder.encode(
            VehicleSettingCommand("BRIGHTNESS_ADJUST_VALUE", 0x134, parameter = 80),
        )
        assertNotNull(brightness)
    }
}
