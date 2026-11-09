package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

class FifthRoundDistanceRegressionTest {

    private class LocalWriter : CarSettingWriter {
        override suspend fun write(item: CarSettingItem): Boolean = true
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult =
            CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.BLE,
                confirmationStatus = CarSettingConfirmationStatus.LOCAL_UPDATED,
            )
    }

    private fun distanceStatus(level: Int) =
        TtpInfoParser.parse(ByteArray(0xB0) { 0xFF.toByte() }.also {
            it[0x16] = level.toByte()
        })

    @Test
    fun distanceLevelReadbackIsVinScopedAndKeepsUnknownUntilReported() = runBlocking {
        val item = CarSettingItem(
            id = "cloud:1:distance",
            configId = "1",
            title = "开机距离档位",
            summary = "",
            classification = CarSettingClassification.POWER,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Slider(0f, 0f..7f, steps = 6, unit = " 档", valueKnown = false),
        )
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = LocalWriter(),
            vinProvider = { "VIN-A" },
            debounceMs = 0L,
        )
        controller.updateVehicleStatus("VIN-B", distanceStatus(7))
        assertEquals(0f, (controller.state.value.items.single().kind as CarSettingKind.Slider).value)
        assertFalse((controller.state.value.items.single().kind as CarSettingKind.Slider).valueKnown)

        controller.updateVehicleStatus("VIN-A", distanceStatus(5))
        val readback = controller.state.value.items.single().kind as CarSettingKind.Slider
        assertEquals(5f, readback.value)
        assertTrue(readback.valueKnown)
    }
}
