package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.settingSliderDraftValue
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** All gateways and writers below are in-memory; no transport or platform adapter is constructed. */
class VolumeBrightnessSettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
    private val cloud = CloudCarSettings(configs = listOf(CloudBikeConfig("20"), CloudBikeConfig("86")),
        values = mapOf("brightnessAdjustValue" to "80", "brightnessAdjustSwitch" to "1", "alarmWarningVolumeLevel" to "2"))
    private class Gateway(val loadAction: suspend (String) -> CloudCarSettings) : CarSettingsGateway {
        override suspend fun load(vin: String) = loadAction(vin)
    }
    private class Writer(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" },
                           g: CarSettingsGateway = Gateway { cloud }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }
    private fun target(c: CarSettingsController, id: String, value: Int): CarSettingKind = when (val k = item(c, id).kind) {
        is CarSettingKind.Toggle -> k.copy(checked = value == 1, valueKnown = true)
        is CarSettingKind.Slider -> k.copy(value = value.toFloat(), valueKnown = true)
        else -> error("unexpected kind")
    }
    private fun observe(c: CarSettingsController, volume: Int = 0, switch: Int = 1, brightness: Int = 50,
                        seq: Long = 1, vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, unknown.copy(extended = unknown.extended.copy(
            alarmWarningVolumeLevel = volume, brightnessAdjustSwitch = switch, brightnessAdjustValue = brightness)), origin, seq))
    }

    @Test fun unknownAndDtoOnlyKeysDoNotEstablishConfigurationOrInitialDraft() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (i in c.state.value.items.filter { it.configId in VolumeBrightnessSettings.ids }) {
            val state = c.state.value.valueState(i.id)
            assertNull(state.configuredValue); assertNull(state.displayKind()); assertNull(state.draftValue)
            assertEquals(VehicleSettingValue.Unknown, state.vehicleValue)
            assertTrue(VehicleSettingCommands.supportsItem(i))
            assertNull(VehicleSettingCommands.forItem(i))
            assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun pageRangesAndExplicitUiQuantizationDoNotChangeRawObservations() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for ((id, max) in listOf("cloud:20" to 2, "cloud:86:value" to 100)) {
            val i = item(c, id); val k = i.kind as CarSettingKind.Slider
            assertEquals(0f..max.toFloat(), k.range)
            assertEquals(if (max == 2) 1 else 99, k.steps)
            assertEquals(1f, settingSliderDraftValue(i, 0.6f))
            for (bad in listOf(-1f, max + 1f, Float.NaN, Float.POSITIVE_INFINITY)) assertNull(settingSliderDraftValue(i, bad))
            assertNull(c.state.value.valueState(id).draftValue)
        }
    }

    @Test fun eachLegalTargetReachesMappedWriterWithExactIndependentParameter() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for ((id, values) in listOf("cloud:20" to (0..2), "cloud:86" to (0..1), "cloud:86:value" to (0..100))) {
            for (v in values) {
                val i = item(c, id).copy(kind = target(c, id, v))
                assertTrue(w.write(i)); val cmd = sent.last()
                assertEquals(when (id) { "cloud:20" -> 0x46; "cloud:86" -> 0x135; else -> 0x134 }, cmd.commandId)
                assertEquals(v, cmd.parameter); assertNotNull(VehicleSettingFrameEncoder.encode(cmd))
                assertNull(CloudCarSettingUpdateBuilder.build(i.configId, i.kind, "VIN-A"))
            }
        }
        assertEquals(106, sent.size)
    }

    @Test fun illegalUnknownAndForgedTargetsCannotReachAnyWriter() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh()); observe(c)
        val cloudWriter = CloudCarSettingWriter(Gateway { cloud }, { "VIN-A" }, w, { true })
        for (id in listOf("cloud:20", "cloud:86:value")) {
            val i = item(c, id); val k = target(c, id, 1) as CarSettingKind.Slider
            val bad = listOf(k.copy(value = -1f), k.copy(value = k.range.endInclusive + 1),
                k.copy(value = 0.5f), k.copy(value = Float.NaN), k.copy(value = Float.POSITIVE_INFINITY),
                k.copy(valueKnown = false), k.copy(rangeKnown = false), k.copy(range = 0f..255f))
            for (b in bad) {
                assertFalse(c.editDraft(id) { b }); assertFalse(c.update(id) { b })
                assertNull(VehicleSettingCommands.forConfigId(i.configId, b))
                assertNull(VehicleSettingCommands.forItem(i.copy(kind = b)))
                assertFalse(cloudWriter.write(i.copy(kind = b)))
            }
            val forged = i.copy(id = "cloud:001", kind = k)
            assertNull(VehicleSettingCommands.forItem(forged)); assertFalse(cloudWriter.write(forged))
        }
        val i = item(c, "cloud:86").copy(kind = CarSettingKind.Toggle(true, valueKnown = false))
        assertNull(VehicleSettingCommands.forItem(i)); assertFalse(cloudWriter.write(i))
        assertTrue(w.sent.isEmpty())
    }

    @Test fun finalRawBoundaryRejectsOutOfDomainWithoutNarrowingProvenVolumeProtocol() {
        for ((id, values) in mapOf(0x46 to listOf(-1, 8, 255), 0x134 to listOf(-1, 101, 255), 0x135 to listOf(-1, 2, 255))) {
            for (v in values) {
                try { VehicleSettingCommand("direct", id, v); fail("accepted $id/$v") }
                catch (_: IllegalArgumentException) { }
            }
        }
        assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("VOLUME", 0x46, 7)))
    }

    @Test fun readbackFieldsRemainIndependentAndInvalidFieldsInvalidateOnlyTheirObservation() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, volume = 2, switch = 0, brightness = 80)
        assertEquals(2f, ((c.state.value.valueState("cloud:20").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(CarSettingKind.Toggle(false), (c.state.value.valueState("cloud:86").vehicleValue as VehicleSettingValue.Known).kind)
        assertEquals(80f, ((c.state.value.valueState("cloud:86:value").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        observe(c, volume = 3, switch = 2, brightness = 101, seq = 2)
        for (i in c.state.value.items.filter { it.configId in VolumeBrightnessSettings.ids })
            assertFalse((c.state.value.valueState(i.id).vehicleValue as VehicleSettingValue.Known).live)
        observe(c, volume = 0, switch = -1, brightness = -1, seq = 3)
        assertTrue((c.state.value.valueState("cloud:20").vehicleValue as VehicleSettingValue.Known).live)
        assertFalse((c.state.value.valueState("cloud:86").vehicleValue as VehicleSettingValue.Known).live)
    }

    @Test fun brightnessRequiresObservedOnSwitchAndDoesNotUseDraftCacheOrReceiptAsEnable() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        fun allowed() = VolumeBrightnessSettings.canEdit(item(c, "cloud:86:value"), c.state.value)
        assertFalse(allowed()); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        assertFalse(allowed()); assertFalse(c.editDraft("cloud:86:value") { target(c, "cloud:86:value", 70) })
        observe(c, switch = 0, seq = 2); assertTrue(c.editDraft("cloud:86") { target(c, "cloud:86", 1) })
        assertFalse(allowed()); assertTrue(c.submitDraft("cloud:86")); assertFalse(allowed())
        assertFalse(c.update("cloud:86:value") { target(c, "cloud:86:value", 70) })
        observe(c, switch = 1, seq = 3); assertTrue(allowed())
        assertTrue(c.update("cloud:86:value") { target(c, "cloud:86:value", 70) })
        assertEquals(2, w.sent.size)
    }

    @Test fun cachedWrongVinAndOldObservationsNeverReplaceCurrentLiveValue() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        for (i in c.state.value.items.filter { it.configId in VolumeBrightnessSettings.ids }) {
            val s = c.state.value.valueState(i.id)
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertNotNull(s.cachedValue)
        }
        observe(c, seq = 10); val before = c.state.value.valueStates
        observe(c, volume = 2, seq = 9); observe(c, volume = 2, seq = 11, vin = "VIN-B")
        assertEquals(before, c.state.value.valueStates)
    }

    @Test fun receiptWaitsForFreshFieldSpecificReadbackAndRefreshPreservesDraft() = runBlocking {
        for (id in listOf("cloud:20", "cloud:86:value")) {
            val c = controller(); assertTrue(c.refresh()); observe(c, seq = 10)
            val old = c.state.value.valueState(id).vehicleValue
            assertTrue(c.editDraft(id) { target(c, id, 2) }); assertTrue(c.refresh())
            assertNotNull(c.state.value.valueState(id).draftValue); assertTrue(c.submitDraft(id))
            assertEquals(old, c.state.value.valueState(id).vehicleValue)
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(id).confirmationState)
            observe(c, volume = 2, brightness = 2, seq = 9)
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(id).confirmationState)
            observe(c, volume = 2, brightness = 2, seq = 11)
            assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState(id).confirmationState)
        }
    }

    @Test fun failureAndCancellationDoNotReplaceObservationsOrCreateSuccess() = runBlocking {
        for (cancel in listOf(false, true)) for (id in listOf("cloud:20", "cloud:86", "cloud:86:value")) {
            val c = controller(Writer { if (cancel) throw CancellationException("cancel") else false })
            assertTrue(c.refresh()); observe(c)
            val old = c.state.value.valueState(id).vehicleValue
            assertTrue(c.editDraft(id) { target(c, id, if (id == "cloud:86") 0 else 2) })
            try { assertFalse(c.submitDraft(id)); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState(id)
            assertEquals(old, s.vehicleValue); assertNull(s.submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, s.writeState)
        }
    }

    @Test fun inFlightRefreshAndVinChangesCannotReuseOldWritesOrDrafts() = runBlocking {
        var vin = "VIN-A"; val receipt = CompletableDeferred<Boolean>(); val w = Writer { receipt.await() }
        val c = controller(w, { vin }); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:86:value") { target(c, "cloud:86:value", 80) })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:20") { target(c, "cloud:20", 2) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:20") { target(c, "cloud:20", 1) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); receipt.complete(true); job.join()
        assertFalse(c.submitDraft("cloud:86:value")); assertEquals(1, w.sent.size)
        for (i in c.state.value.items) {
            val s = c.state.value.valueState(i.id)
            assertNull(s.submittedValue); assertNull(s.draftValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }

    @Test fun staleLoadsCannotReplaceNewVinSettings() = runBlocking {
        var vin = "VIN-A"; val old = CompletableDeferred<CloudCarSettings>()
        val c = controller(vin = { vin }, g = Gateway { if (it == "VIN-A") old.await() else cloud })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.refresh()) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        observe(c, volume = 2, seq = 10, vin = "VIN-B"); val before = c.state.value
        old.complete(cloud); job.join(); assertEquals(before, c.state.value)
    }
}
