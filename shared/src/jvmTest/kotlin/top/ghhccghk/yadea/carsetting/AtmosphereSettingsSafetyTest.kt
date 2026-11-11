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

class AtmosphereSettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val cloud = CloudCarSettings(configs = listOf(CloudBikeConfig("73")), values = emptyMap())
    private val full = CarSettingKind.Atmosphere(showMode = 3, runMode = 5, singleColor = false,
        red1 = 255, green1 = 3, blue1 = 4, red2 = 5, green2 = 6, blue2 = 255, valueKnown = true)
    private class Gateway(val action: suspend () -> CloudCarSettings) : CarSettingsGateway {
        override suspend fun load(vin: String) = action()
    }
    private class Writer(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(writer: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = writer, gateway = Gateway { cloud }, vinProvider = vin, debounceMs = 0)
    private fun item(c: CarSettingsController) = c.state.value.items.single { it.id == "cloud:73:config" }
    private fun state(c: CarSettingsController) = c.state.value.valueState("cloud:73:config")
    private fun observe(c: CarSettingsController, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP,
                        raw: CarSettingKind.Atmosphere = full.copy(red1 = 254, blue2 = 254)) {
        val info = unknown.copy(extended = unknown.extended.copy(ambientStatus = 1,
            atmosphereShowMode = raw.showMode, atmosphereOperationMode = raw.runMode,
            atmosphereColorMode = if (raw.singleColor) 0 else 1,
            atmosphereRgb1r = raw.red1, atmosphereRgb1g = raw.green1, atmosphereRgb1b = raw.blue1,
            atmosphereRgb2r = raw.red2, atmosphereRgb2g = raw.green2, atmosphereRgb2b = raw.blue2))
        c.updateVehicleObservation(VehicleStatusObservation(vin, info, origin, seq))
    }

    @Test fun unknownDoesNotGenerateDefaultsOrAllowControllerOrWriterBypass() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        assertEquals(-1, (item(c).kind as CarSettingKind.Atmosphere).red1)
        assertNull(state(c).displayKind()); assertNull(state(c).draftValue)
        assertTrue(VehicleSettingCommands.supportsItem(item(c)))
        assertFalse(c.editDraft(item(c).id) { full }); assertFalse(c.update(item(c).id) { full })
        assertNull(VehicleSettingCommands.forItem(item(c)))
        val cloudWriter = CloudCarSettingWriter(Gateway { cloud }, { "VIN-A" }, w, { true })
        assertFalse(cloudWriter.write(item(c))); assertTrue(w.sent.isEmpty())
    }

    @Test fun modeDependentPayloadMatchesOriginalAndRgb255IsNormalizedOnlyOnWrite() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (mode in 0..3) {
            val k = full.copy(showMode = mode)
            val command = requireNotNull(VehicleSettingCommands.forItem(item(c).copy(kind = k)))
            assertEquals(0xF7, command.commandId)
            val fixed = mode == 0 || mode == 2
            assertEquals(listOf(mode, if (fixed) 0 else 1, 254, 3, 4,
                if (fixed) 255 else 5, if (fixed) 255 else 6, if (fixed) 255 else 254,
                if (mode == 3) 5 else -1), command.parameters)
            assertNotNull(VehicleSettingFrameEncoder.encode(command))
            assertNull(CloudCarSettingUpdateBuilder.build("73", k, "VIN-A"))
        }
        assertEquals(255, full.red1)
    }

    @Test fun invalidMissingAndUnknownFieldsCannotReachAnyWriter() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh()); observe(c)
        for (bad in listOf(full.copy(valueKnown = false), full.copy(showMode = -1), full.copy(showMode = 4),
            full.copy(runMode = -1), full.copy(runMode = 6), full.copy(red1 = -1), full.copy(green1 = 256),
            full.copy(blue1 = -1), full.copy(red2 = -1), full.copy(green2 = 256), full.copy(blue2 = -1))) {
            assertFalse(c.editDraft(item(c).id) { bad }); assertFalse(c.update(item(c).id) { bad })
            assertNull(VehicleSettingCommands.forItem(item(c).copy(kind = bad)))
        }
        assertNull(VehicleSettingCommands.forItem(item(c).copy(id = "cloud:002:level", kind = full)))
        assertTrue(w.sent.isEmpty())
    }

    @Test fun finalCommandConstructorRejectsMutableOrForgedCompositePayloads() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        val valid = requireNotNull(VehicleSettingCommands.forItem(item(c).copy(kind = full)))
        for ((id, type) in listOf(0xF7 to "ATMOSPHERE_CONFIG", 0xF7 to "other", 2 to "ATMOSPHERE_CONFIG")) {
            try { VehicleSettingCommand(type, id, parameters = valid.parameters.toMutableList()); fail("raw list accepted") }
            catch (_: IllegalArgumentException) { }
        }
        assertFalse(valid.parameters is MutableList<*>)
        assertNotNull(VehicleSettingFrameEncoder.encode(valid.copy()))
    }

    @Test fun ttpCompletenessAndSourcesAreIndependentOfSwitchAndCloud() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        assertTrue(state(c).vehicleValue is VehicleSettingValue.Unknown)
        assertFalse(requireNotNull(state(c).cachedValue).live); assertNull(state(c).configuredValue)
        observe(c, seq = 2)
        assertTrue((state(c).vehicleValue as VehicleSettingValue.Known).live)
        assertEquals(3, ((state(c).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Atmosphere).showMode)
        observe(c, seq = 3, raw = full.copy(red1 = -1))
        assertFalse((state(c).vehicleValue as VehicleSettingValue.Known).live)
        assertEquals(SettingReadbackState.MISSING_OR_INVALID, state(c).readbackState)
        assertTrue((c.state.value.valueState("cloud:73").vehicleValue as VehicleSettingValue.Known).live)
    }

    @Test fun wrongVinAndOldSequenceCannotOverwriteFreshObservation() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, 10)
        val before = state(c).vehicleValue
        observe(c, 11, "VIN-B", raw = full.copy(showMode = 0)); observe(c, 9, raw = full.copy(showMode = 2))
        assertEquals(before, state(c).vehicleValue)
    }

    @Test fun oneFieldEditPreservesSiblingsAndRefreshPreservesDraft() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        val before = (state(c).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Atmosphere
        assertTrue(c.editDraft(item(c).id) { (it as CarSettingKind.Atmosphere).copy(runMode = 1) })
        assertTrue(c.refresh())
        assertEquals(before.copy(runMode = 1), state(c).draftValue)
    }

    @Test fun receiptWaitsForNewMatchingVehicleReadback() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        val before = state(c).vehicleValue
        assertTrue(c.update(item(c).id) { full.copy(runMode = 1) })
        assertEquals(before, state(c).vehicleValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, 2, raw = full.copy(runMode = 1, red1 = 254, blue2 = 254))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
    }

    @Test fun failureAndCancellationNeverCreateVehicleSuccess() = runBlocking {
        for (cancel in listOf(false, true)) {
            val c = controller(Writer { if (cancel) throw CancellationException("fake") else false })
            assertTrue(c.refresh()); observe(c); val before = state(c).vehicleValue
            try { assertFalse(c.update(item(c).id) { full }) } catch (e: CancellationException) { assertTrue(cancel) }
            assertEquals(before, state(c).vehicleValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c).writeState)
            assertNotEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
        }
    }

    @Test fun vinSwitchInvalidatesOldDraftAndInflightReceipt() = runBlocking {
        var vin = "VIN-A"; val gate = CompletableDeferred<Boolean>(); val c = controller(Writer { gate.await() }, { vin })
        assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft(item(c).id) { full })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.submitDraft("cloud:73:config")) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); gate.complete(true); job.join()
        assertNull(state(c).draftValue); assertNull(state(c).submittedValue)
        assertTrue(state(c).vehicleValue is VehicleSettingValue.Unknown)
    }

    @Test fun refreshAndDuplicateSubmitBlockedWhileWriterIsActive() = runBlocking {
        val gate = CompletableDeferred<Boolean>(); val w = Writer { gate.await() }; val c = controller(w)
        assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(c.update(item(c).id) { full }) }
        assertFalse(c.refresh()); assertFalse(c.update(item(c).id) { full })
        gate.complete(true); job.join(); assertEquals(1, w.sent.size)
    }
}
