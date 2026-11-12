package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser

class FifthRoundDistanceRegressionTest {

    private class LocalWriter : CarSettingWriter {
        val writes = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean = true
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            writes += item
            return CarSettingWriteResult(
                success = true,
                channel = CarSettingWriteChannel.BLE,
                confirmationStatus = CarSettingConfirmationStatus.LOCAL_UPDATED,
            )
        }
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

        controller.updateVehicleStatus("VIN-A", distanceStatus(7))
        assertFalse((controller.state.value.items.single().kind as CarSettingKind.Slider).valueKnown)

        controller.updateVehicleStatus("VIN-A", distanceStatus(5))
        val readback = controller.state.value.items.single().kind as CarSettingKind.Slider
        assertEquals(5f, readback.value)
        assertTrue(readback.valueKnown)
    }

    @Test
    fun unknownDistanceCannotBeEditedOrSubmittedButLiveKnownDistanceCan() = runBlocking {
        val item = distanceItem(valueKnown = false)
        val writer = LocalWriter()
        val controller = CarSettingsController(
            initialItems = listOf(item), writer = writer, vinProvider = { "VIN-A" }, debounceMs = 0L,
        )

        assertFalse(controller.editDraft(item.id) {
            (it as CarSettingKind.Slider).copy(value = 0f, valueKnown = true)
        })
        assertFalse(controller.update(item.id) {
            (it as CarSettingKind.Slider).copy(value = 0f, valueKnown = true)
        })
        assertTrue(writer.writes.isEmpty())

        val knownWriter = LocalWriter()
        val knownController = CarSettingsController(
            initialItems = listOf(item), writer = knownWriter, vinProvider = { "VIN-A" }, debounceMs = 0L,
        )
        knownController.updateVehicleStatus("VIN-A", distanceStatus(5))
        assertTrue((knownController.state.value.items.single().kind as CarSettingKind.Slider).valueKnown)
        assertTrue(knownController.state.value.valueState(item.id).vehicleValue is VehicleSettingValue.Known)
        assertTrue(CarSettingItemAccess.canWrite(knownController.state.value.items.single()))
        assertTrue(VehicleSettingCommands.forItem(knownController.state.value.items.single()) != null)
        assertTrue(knownController.editDraft(item.id) {
            (it as CarSettingKind.Slider).copy(value = 0f)
        })
        assertTrue(knownController.submitDraft(item.id))
        assertEquals(0f, (knownWriter.writes.single().kind as CarSettingKind.Slider).value)
        assertEquals(SettingWriteState.LOCAL_UPDATED, knownController.state.value.valueState(item.id).writeState)
        assertFalse(knownController.state.value.valueState(item.id).confirmationState == SettingConfirmationState.READBACK_MATCHED)
    }

    @Test
    fun distanceCommandRejectsUnknownFractionalAndOutOfRangeTargets() {
        val item = distanceItem(valueKnown = true)
        val slider = item.kind as CarSettingKind.Slider
        for (value in listOf(-1f, 0.5f, 8f)) {
            assertNull(VehicleSettingCommands.forItem(item.copy(
                kind = slider.copy(value = value),
            )))
        }
        assertNull(VehicleSettingCommands.forItem(item.copy(
            kind = slider.copy(valueKnown = false),
        )))
        for (value in listOf(0, 7)) {
            val command = VehicleSettingCommands.forItem(item.copy(
                kind = slider.copy(value = value.toFloat()),
            ))
            assertEquals(value, command?.parameter)
            assertEquals(0x37, command?.commandId)
            assertTrue(VehicleSettingFrameEncoder.encode(requireNotNull(command)) != null)
        }
    }

    @Test
    fun directDistanceCommandConstructionRejectsOutOfRangeParameters() {
        for (parameter in listOf(-1, 8)) {
            try {
                VehicleSettingCommand("NEARBY_BLE_UNLOCK", 0x37, parameter)
                throw AssertionError("expected invalid 0x37 parameter to be rejected")
            } catch (_: IllegalArgumentException) {
                // The final command boundary must reject instead of relying on UI validation.
            }
        }
    }

    private fun distanceItem(valueKnown: Boolean) = CarSettingItem(
        id = "cloud:1:distance",
        configId = "1",
        title = "开机距离档位",
        summary = "",
        classification = CarSettingClassification.POWER,
        channel = CarSettingChannel.BLE,
        kind = CarSettingKind.Slider(0f, 0f..7f, steps = 6, unit = " 档", valueKnown = valueKnown),
    )
}
