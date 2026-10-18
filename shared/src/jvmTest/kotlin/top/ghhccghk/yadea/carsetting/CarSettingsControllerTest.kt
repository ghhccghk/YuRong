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
}
