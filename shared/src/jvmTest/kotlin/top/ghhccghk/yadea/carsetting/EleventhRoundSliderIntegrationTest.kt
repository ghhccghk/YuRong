package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.adaptivePushUiDisabledReason
import top.ghhccghk.yadea.ui.isAuditedThreeTickSlider
import top.ghhccghk.yadea.ui.settingSliderDraftValue
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Pure callback policy -> draft -> fake writer tests; no Compose runtime or I/O. */
class EleventhRoundSliderIntegrationTest {
    private fun items() = CloudCarSettingMapper.toItems(CloudCarSettings(
        listOf("14", "20", "64", "89").map { CloudBikeConfig(confId = it) }, emptyMap(),
    ))
    private val ids = listOf("cloud:14", "cloud:64:level", "cloud:89:level")
    private fun status(alarm: Int = 7, level: Int = 7, switch: Int = 3) = TtpInfoParser.parse(
        ByteArray(256) { -1 }.also {
            it[0x1C] = ((0xFF and (7 shl 2).inv()) or (alarm shl 2)).toByte()
            it[0x33] = ((4 shl 3) or 6).toByte()
            it[0x6E] = 0x7F
            it[0x76] = ((switch shl 6) or 0x3F).toByte()
            it[0x79] = (0xF8 or level).toByte()
        },
    )

    private class FakeWriter : CarSettingWriter {
        val submitted = mutableListOf<CarSettingItem>()
        var channel = CarSettingWriteChannel.BLE
        var response: CompletableDeferred<Boolean>? = null
        override suspend fun write(item: CarSettingItem) = true
        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
            submitted += item
            return CarSettingWriteResult(response?.await() ?: true, channel)
        }
    }

    @Test fun provenThreeUiPositionsReachUnchangedCommandParametersWithoutOffsets() = runBlocking {
        for (id in ids) for (tick in 0..2) {
            val writer = FakeWriter()
            val c = CarSettingsController(items(), writer, debounceMs = 0)
            val item = c.state.value.items.single { it.id == id }
            val slider = item.kind as CarSettingKind.Slider
            val progress = if (id == "cloud:14") tick * 50f else tick.toFloat()
            val next = settingSliderDraftValue(item, progress)!!
            assertEquals(progress, next)
            assertTrue(c.editDraft(id) { slider.copy(value = next, valueKnown = true) })
            assertTrue(c.submitDraft(id))
            val command = VehicleSettingCommands.forItem(writer.submitted.single())!!
            assertEquals(tick, command.parameter)
            assertEquals(if (id == "cloud:14") 0x5F else 0xD1, command.commandId)
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
        }
    }

    @Test fun fractionalAccessibilityEventsSnapOnlyToTheThreeProvenUiPositions() {
        for (id in ids) {
            val item = items().single { it.id == id }
            val scale = if (id == "cloud:14") 50f else 1f
            assertEquals(0f, settingSliderDraftValue(item, 0.49f * scale))
            assertEquals(scale, settingSliderDraftValue(item, 0.5f * scale))
            assertEquals(scale, settingSliderDraftValue(item, 1.49f * scale))
            assertEquals(2f * scale, settingSliderDraftValue(item, 1.5f * scale))
            for (step in 0..100) {
                val value = settingSliderDraftValue(item, step / 50f * scale)!!
                assertTrue(value in listOf(0f, scale, 2f * scale))
                assertNotNull(VehicleSettingCommands.forItem(item.copy(kind = (item.kind as CarSettingKind.Slider).copy(value = value))))
            }
        }
    }

    @Test fun illegalUiEventsAndUnexpectedSliderShapesAreRejectedNotClamped() {
        for (id in ids) {
            val item = items().single { it.id == id }
            val slider = item.kind as CarSettingKind.Slider
            for (value in listOf(-1f, slider.range.endInclusive + 1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertNull(settingSliderDraftValue(item, value))
            }
            assertNull(settingSliderDraftValue(item.copy(kind = slider.copy(steps = 6)), 0f))
            assertNull(settingSliderDraftValue(item.copy(kind = slider.copy(range = 1f..3f)), 2f))
        }
    }

    @Test fun vehicleVolumeKeepsEightPositionsAndIsNeverAlarmQuantized() {
        val item = items().single { it.id == "cloud:20" }
        val slider = item.kind as CarSettingKind.Slider
        assertFalse(isAuditedThreeTickSlider(item))
        assertEquals(0f..7f, slider.range)
        assertEquals(6, slider.steps)
        assertEquals(3.25f, settingSliderDraftValue(item, 3.25f))
        for (tick in 0..7) {
            assertEquals(tick, VehicleSettingCommands.forItem(item.copy(kind = slider.copy(value = tick.toFloat())))!!.parameter)
        }
    }

    @Test fun explicitSaveOfUnknownFallbackCreatesIntentionalTargetNotVehicleObservation() = runBlocking {
        for (id in ids) {
            val writer = FakeWriter()
            val c = CarSettingsController(items(), writer, debounceMs = 0)
            val item = c.state.value.items.single { it.id == id }
            val slider = item.kind as CarSettingKind.Slider
            assertFalse(slider.valueKnown)
            assertFalse(c.submitDraft(id))
            assertTrue(writer.submitted.isEmpty())
            val next = settingSliderDraftValue(item, slider.value)!!
            assertTrue(c.editDraft(id) { slider.copy(value = next, valueKnown = true) })
            assertTrue(c.submitDraft(id))
            val value = c.state.value.valueState(id)
            assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
            assertEquals(SettingWriteState.LOCAL_UPDATED, value.writeState)
            assertEquals(SettingConfirmationState.WAITING_READBACK, value.confirmationState)
            assertNotNull(value.submittedValue)
        }
    }

    @Test fun incomingVehicleAlarmDoesNotReplaceDraftOrVehicleVolumeState() {
        val c = CarSettingsController(items(), writer = FakeWriter())
        c.updateVehicleStatus("VIN-A", status(alarm = 0))
        val volume = c.state.value.valueState("cloud:20").vehicleValue
        assertTrue(c.editDraft("cloud:14") { (it as CarSettingKind.Slider).copy(value = 100f) })
        c.updateVehicleStatus("VIN-A", status(alarm = 1))
        val value = c.state.value.valueState("cloud:14")
        assertEquals(100f, (value.draftValue as CarSettingKind.Slider).value)
        assertEquals(50f, ((value.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals((volume as VehicleSettingValue.Known).kind,
            (c.state.value.valueState("cloud:20").vehicleValue as VehicleSettingValue.Known).kind)
        c.cancelDraft("cloud:14")
        assertEquals(50f, (c.state.value.valueState("cloud:14").editableKind(c.state.value.items.single { it.id == "cloud:14" }.kind) as CarSettingKind.Slider).value)
    }

    @Test fun failureRemovesOnlySubmittedTargetAndRetainsReadbackReceivedDuringWrite() = runBlocking {
        for (id in ids) {
            val writer = FakeWriter().also { it.response = CompletableDeferred() }
            val c = CarSettingsController(items(), writer, debounceMs = 0)
            c.updateVehicleStatus("VIN-A", status(alarm = 0, level = 0))
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                assertFalse(c.update(id) { (it as CarSettingKind.Slider).copy(value = it.range.endInclusive) })
            }
            c.updateVehicleStatus("VIN-A", status(alarm = 1, level = 1))
            val newest = c.state.value.valueState(id).vehicleValue
            writer.response!!.complete(false)
            job.join()
            val value = c.state.value.valueState(id)
            assertEquals(newest, value.vehicleValue)
            assertNull(value.draftValue)
            assertNull(value.submittedValue)
            assertEquals(SettingWriteState.FAILED, value.writeState)
            assertNotNull(value.error)
            assertTrue(c.state.value.pendingIds.isEmpty())
        }
    }

    @Test fun unknownAdaptiveCanBeEditedWithLiveMainAndReceiptDoesNotConfirmTarget() = runBlocking {
        for ((id, parameter) in listOf("64" to 1, "89" to 2)) {
            val writer = FakeWriter()
            val gateway = object : CarSettingsGateway {
                override suspend fun load(vin: String) = CloudCarSettings(
                    listOf(CloudBikeConfig(confId = id)), emptyMap(),
                )
            }
            val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0)
            c.refresh()
            c.updateVehicleStatus("VIN-A", status())
            val item = c.state.value.items.single { it.id == "cloud:$id:adaptive" }
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(item.id).vehicleValue)
            assertNull(adaptivePushUiDisabledReason(item, c.state.value, true, true))
            assertTrue(c.update(item.id) { CarSettingKind.Toggle(true) })
            assertEquals(parameter, VehicleSettingCommands.forItem(writer.submitted.single())!!.parameter)
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(item.id).vehicleValue)
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(item.id).confirmationState)
        }
    }

    @Test fun duplicateSubmissionCannotReplaceActiveSliderTarget() = runBlocking {
        val writer = FakeWriter().also { it.response = CompletableDeferred() }
        val c = CarSettingsController(items(), writer, debounceMs = 0)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            c.update("cloud:14") { (it as CarSettingKind.Slider).copy(value = 100f) }
        }
        assertFalse(c.editDraft("cloud:14") { (it as CarSettingKind.Slider).copy(value = 50f) })
        assertFalse(c.update("cloud:14") { (it as CarSettingKind.Slider).copy(value = 0f) })
        assertEquals(1, writer.submitted.size)
        assertEquals(100f, (c.state.value.valueState("cloud:14").submittedValue as CarSettingKind.Slider).value)
        writer.response!!.complete(true)
        job.join()
    }

    @Test fun cloudReceiptIsNotVehicleReadbackForAuditedSettings() = runBlocking {
        for (id in ids) {
            val writer = FakeWriter().also { it.channel = CarSettingWriteChannel.CLOUD }
            val c = CarSettingsController(items(), writer, debounceMs = 0)
            assertTrue(c.update(id) { (it as CarSettingKind.Slider).copy(value = it.range.endInclusive) })
            val value = c.state.value.valueState(id)
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, value.writeState)
            assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
            assertEquals(SettingConfirmationState.WAITING_READBACK, value.confirmationState)
        }
    }
}
