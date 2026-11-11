package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

class SimpleChoiceSettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val cloud = CloudCarSettings(values = emptyMap(), configs = SimpleChoiceSettings.ids.map { CloudBikeConfig(it) })
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
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.configId == id }
    private fun choice(c: CarSettingsController, id: String, index: Int) =
        (item(c, id).kind as CarSettingKind.Choice).copy(selectedIndex = index, valueKnown = true)
    private fun observe(c: CarSettingsController, raw: Int, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        val s = unknown.copy(extended = unknown.extended.copy(offModeStatus = raw, metricSystemStatus = raw,
            panelThemeCurrent = raw, voltageSettingStatus = raw + 1))
        c.updateVehicleObservation(VehicleStatusObservation(vin, s, origin, seq))
    }

    @Test fun unknownChoicesHaveNoSelectionDraftOrWritableCommand() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (id in SimpleChoiceSettings.ids) {
            val i = item(c, id); val k = i.kind as CarSettingKind.Choice
            assertFalse(k.valueKnown); assertEquals(-1, k.selectedIndex)
            assertNull(c.state.value.valueState(i.id).displayKind())
            assertNull(c.state.value.valueState(i.id).configuredValue)
            assertNull(c.state.value.valueState(i.id).draftValue)
            assertTrue(VehicleSettingCommands.supportsItem(i))
            assertNull(VehicleSettingCommands.forItem(i)); assertNull(VehicleSettingCommands.forConfigId(id, k))
            assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun everyLegalChoiceReachesTheActualMappedWriterWithExactParameter() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for ((id, options) in SimpleChoiceSettings.options) for (index in options.indices) {
            val target = item(c, id).copy(kind = choice(c, id, index))
            assertTrue(w.write(target)); val cmd = sent.last()
            assertEquals(when (id) { "27" -> 0x8D + index; "31" -> 0x89 + index; "50" -> 0x3F; else -> 0xC6 }, cmd.commandId)
            assertEquals(when (id) { "50" -> index; "54" -> index + 1; else -> -1 }, cmd.parameter)
            assertNotNull(VehicleSettingFrameEncoder.encode(cmd))
            assertNull(CloudCarSettingUpdateBuilder.build(id, target.kind, "VIN-A"))
        }
        assertEquals(9, sent.size)
    }

    @Test fun invalidUnknownAndForgedChoicesAreRejectedAtAllEntrypoints() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (id in SimpleChoiceSettings.ids) {
            val i = item(c, id); val legal = choice(c, id, 0)
            for (k in listOf(legal.copy(valueKnown = false), legal.copy(selectedIndex = -1),
                legal.copy(selectedIndex = legal.options.size), legal.copy(options = listOf("invented")))) {
                assertFalse(c.editDraft(i.id) { k }); assertFalse(c.update(i.id) { k })
                assertNull(VehicleSettingCommands.forConfigId(id, k))
                assertNull(VehicleSettingCommands.forItem(i.copy(kind = k)))
            }
            assertNull(VehicleSettingCommands.forItem(i.copy(id = "cloud:002:level", kind = legal)))
            val cloudWriter = CloudCarSettingWriter(Gateway { cloud }, { "VIN-A" }, w, { true })
            assertFalse(cloudWriter.write(i.copy(id = "cloud:002:level", kind = legal)))
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun directRawConstructorsRejectValuesOutsideProtocolDomain() {
        for ((id, bad) in mapOf(0x3F to listOf(-1, 3, 255), 0xC6 to listOf(-1, 7, 255))) for (v in bad) {
            try { VehicleSettingCommand("direct", id, v); fail("accepted $v") }
            catch (_: IllegalArgumentException) { }
        }
        // Page options are narrower than the established raw protocol.
        assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("direct", 0x3F, 2)))
        assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("direct", 0xC6, 6)))
    }

    @Test fun ttpMapsZeroBasedChoicesAndOneBasedVoltageWithoutGuessing() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (raw in 0..1) {
            observe(c, raw, raw + 1L)
            for (id in SimpleChoiceSettings.ids) {
                val s = c.state.value.valueState("cloud:$id")
                assertEquals(raw, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
                assertNull(s.configuredValue)
            }
        }
        observe(c, 2, 3)
        assertEquals(2, ((c.state.value.valueState("cloud:54").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
        for (id in setOf("27", "31", "50")) assertFalse((c.state.value.valueState("cloud:$id").vehicleValue as VehicleSettingValue.Known).live)
        observe(c, 99, 4)
        for (id in SimpleChoiceSettings.ids) assertFalse((c.state.value.valueState("cloud:$id").vehicleValue as VehicleSettingValue.Known).live)
    }

    @Test fun cachedWrongVinAndOlderObservationsDoNotBecomeNewLiveValues() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, 1, 1, origin = VehicleStatusOrigin.CACHED_TTP)
        for (id in SimpleChoiceSettings.ids) {
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertNotNull(s.cachedValue)
        }
        observe(c, 0, 10); val before = c.state.value.valueStates
        observe(c, 1, 9); observe(c, 1, 11, vin = "VIN-B")
        assertEquals(before, c.state.value.valueStates)
    }

    @Test fun receiptDoesNotMutateObservationAndOnlyFreshReadbackConfirms() = runBlocking {
        for (id in SimpleChoiceSettings.ids) {
            val c = controller(); assertTrue(c.refresh()); observe(c, 0, 10)
            assertTrue(c.update("cloud:$id") { choice(c, id, 1) })
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(0, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
            assertEquals(SettingConfirmationState.WAITING_READBACK, s.confirmationState)
            observe(c, 1, 9); assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:$id").confirmationState)
            observe(c, 1, 11); assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:$id").confirmationState)
        }
    }

    @Test fun refreshPreservesDraftAndFailureOrCancellationNeverCreatesSuccess() = runBlocking {
        for (cancel in listOf(false, true)) for (id in SimpleChoiceSettings.ids) {
            val c = controller(Writer { if (cancel) throw CancellationException("cancel") else false })
            assertTrue(c.refresh()); observe(c, 0)
            assertTrue(c.editDraft("cloud:$id") { choice(c, id, 1) }); assertTrue(c.refresh())
            assertNotNull(c.state.value.valueState("cloud:$id").draftValue)
            try { assertFalse(c.submitDraft("cloud:$id")); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:$id")
            assertNull(s.submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, s.writeState)
            assertEquals(0, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
        }
    }

    @Test fun inFlightWritesBlockRefreshAndOldVinReceiptsCannotPolluteNewVehicle() = runBlocking {
        var vin = "VIN-A"; val receipt = CompletableDeferred<Boolean>(); val w = Writer { receipt.await() }
        val c = controller(w, { vin }); assertTrue(c.refresh()); observe(c, 0)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:27") { choice(c, "27", 1) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:27") { choice(c, "27", 1) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); receipt.complete(true); job.join()
        assertNull(c.state.value.valueState("cloud:27").submittedValue)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:27").vehicleValue)
        assertEquals(1, w.sent.size)
    }
}
