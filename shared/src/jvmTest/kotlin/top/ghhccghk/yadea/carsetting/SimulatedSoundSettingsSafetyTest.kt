package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** In-memory directory, writer and observations; no real transport or vehicle. */
class SimulatedSoundSettingsSafetyTest {
    private class Gateway(var present: Boolean = true, var auth: Boolean? = true) : CarSettingsGateway {
        var writes = 0
        override suspend fun load(vin: String) = CloudCarSettings(
            if (present) listOf(CloudBikeConfig("33", isAuth = auth)) else emptyList(), emptyMap())
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { writes++; return true }
    }
    private class Writer(var action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun controller(g: Gateway = Gateway(), w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun state(c: CarSettingsController, suffix: String = "") = c.state.value.valueState("cloud:33$suffix")
    private fun item(c: CarSettingsController, suffix: String = "") = c.state.value.items.single { it.id == "cloud:33$suffix" }
    private fun observe(c: CarSettingsController, switch: Int = 0, mode: Int = 0, volume: Int = 1,
                        online: Int = 0, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, empty.copy(extended = empty.extended.copy(
            simulatedSoundWaveStatus = switch, simulatedSoundWaveModeStatus = mode,
            simulatedSoundWaveVolumeStatus = volume, simulatedSoundWaveOnlineStatus = online)), origin, seq))
    }

    @Test fun directoryCreatesOnlyProvenChildrenAndUnknownDoesNotSelectDefaults() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (suffix in listOf("", ":mode", ":volume")) {
            assertNull(state(c, suffix).displayKind()); assertNull(state(c, suffix).configuredValue)
            assertEquals(VehicleSettingValue.Unknown, state(c, suffix).vehicleValue)
        }
        assertNull(settingSwitchTarget(state(c), true)); assertFalse(c.simulatedSoundAvailable)
        assertFalse(item(c, ":mode").writable); assertFalse(item(c, ":volume").writable)
        assertFalse(c.editDraft("cloud:33:mode") { CarSettingKind.Choice(1, SimulatedSoundSettings.modes) })
        assertFalse(c.editDraft("cloud:33:volume") { CarSettingKind.Slider(16f, 1f..16f) })
        assertFalse(c.update("cloud:33") { it })
        val absent = controller(Gateway(false)); assertTrue(absent.refresh())
        assertTrue(absent.state.value.items.none { it.configId == "33" })
    }

    @Test fun cloudReadOnlyPermissionCannotBeBypassedByTheController() = runBlocking {
        val w = Writer(); val c = controller(Gateway(auth = false), w); assertTrue(c.refresh()); observe(c)
        assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) }); assertTrue(w.sent.isEmpty())
    }

    @Test fun strictIndependentReadbackPreservesModeAndVolumeInsteadOfSwitchDefaults() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, switch = 1, mode = 1, volume = 16)
        assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        assertEquals(1, (state(c, ":mode").displayKind() as CarSettingKind.Choice).selectedIndex)
        assertEquals(16f, (state(c, ":volume").displayKind() as CarSettingKind.Slider).value)
        observe(c, switch = 0, mode = 0, volume = 1, seq = 2)
        assertEquals(CarSettingKind.Toggle(false), state(c).displayKind())
        assertEquals(0, (state(c, ":mode").displayKind() as CarSettingKind.Choice).selectedIndex)
        assertEquals(1f, (state(c, ":volume").displayKind() as CarSettingKind.Slider).value)
        for (suffix in listOf("", ":mode", ":volume")) assertNull(state(c, suffix).configuredValue)
    }

    @Test fun missingAndOutOfRangeFieldsDoNotAcquireAuthority() = runBlocking {
        for (raw in listOf(-1, 2, 255)) {
            val c = controller(); assertTrue(c.refresh()); observe(c, switch = raw, mode = raw, volume = -1)
            assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
            assertEquals(VehicleSettingValue.Unknown, state(c, ":mode").vehicleValue)
            assertEquals(VehicleSettingValue.Unknown, state(c, ":volume").vehicleValue)
        }
        for (raw in listOf(0, 17, 31, 255)) {
            val c = controller(); assertTrue(c.refresh()); observe(c, volume = raw)
            assertEquals(VehicleSettingValue.Unknown, state(c, ":volume").vehicleValue)
        }
        val c = controller(); assertTrue(c.refresh()); observe(c)
        observe(c, switch = 2, mode = 2, volume = 17, seq = 2)
        for (suffix in listOf("", ":mode", ":volume")) {
            assertFalse((state(c, suffix).vehicleValue as VehicleSettingValue.Known).live)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, state(c, suffix).readbackState)
        }
    }

    @Test fun cachedWrongVinAndOlderObservationsNeverAuthorizeOrReplaceLiveValues() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        assertFalse(c.simulatedSoundAvailable)
        for (suffix in listOf("", ":mode", ":volume")) {
            assertEquals(VehicleSettingValue.Unknown, state(c, suffix).vehicleValue)
            assertEquals(SettingValueSource.CACHED_TTP, state(c, suffix).cachedValue!!.source)
            assertFalse(state(c, suffix).cachedValue!!.live)
        }
        observe(c, switch = 1, mode = 1, volume = 16, seq = 2)
        observe(c, online = 1, seq = 1); observe(c, online = 1, seq = 3, vin = "VIN-B")
        assertTrue(c.simulatedSoundAvailable); assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        c.updateVehicleObservation(null); assertFalse(c.simulatedSoundAvailable)
        assertFalse((state(c).vehicleValue as VehicleSettingValue.Known).live)
    }

    @Test fun onlyFreshRawZeroAllowsTheOriginalSwitchAndNothingWritesToCloud() = runBlocking {
        for (raw in listOf(-1, 1, 2, 255)) {
            val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c, online = raw)
            assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) }); assertTrue(w.sent.isEmpty())
            assertEquals(SettingWriteState.FAILED, state(c).writeState); assertNotNull(state(c).error)
        }
        val g = Gateway(); val commands = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        val c = controller(g, CloudCarSettingWriter(g, { "VIN-A" }, mapped, { true })); assertTrue(c.refresh()); observe(c)
        assertTrue(c.update("cloud:33") { CarSettingKind.Toggle(true) })
        assertTrue(c.update("cloud:33") { CarSettingKind.Toggle(false) })
        assertEquals(listOf(0x95, 0x96), commands.map { it.commandId }); assertEquals(0, g.writes)
        assertNull(item(c).simulatedSoundAuthorization)
    }

    @Test fun bareMapperAndDirectWriterRejectMissingWrongVinUnknownAndForgedProof() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        val w = CloudCarSettingWriter(g, { "VIN-A" }, Writer(), { true })
        val target = item(c).copy(kind = CarSettingKind.Toggle(true))
        assertNull(VehicleSettingCommands.forConfigId("33", target.kind)); assertNull(VehicleSettingCommands.forItem(target))
        assertFalse(w.write(target)); assertFalse(w.write(target.copy(simulatedSoundAuthorization = SimulatedSoundAuthorization("VIN-B"))))
        val authorized = target.copy(simulatedSoundAuthorization = SimulatedSoundAuthorization("VIN-A"))
        for (bad in listOf(authorized.copy(id = "cloud:wrong"), authorized.copy(kind = CarSettingKind.Toggle(true, false)),
            authorized.copy(id = "cloud:33:mode", kind = CarSettingKind.Choice(0, SimulatedSoundSettings.modes)),
            authorized.copy(id = "cloud:33:volume", kind = CarSettingKind.Slider(1f, 1f..16f)))) {
            assertNull(VehicleSettingCommands.forItem(bad)); assertFalse(w.write(bad))
        }
        assertTrue(g.writes == 0)
    }

    @Test fun unsupportedModeAndVolumeRemainReadOnlyEvenAfterReadback() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c)
        for (suffix in listOf(":mode", ":volume")) {
            assertFalse(c.update("cloud:33$suffix") { it }); assertNull(VehicleSettingCommands.forItem(item(c, suffix)))
            assertFalse(CloudCarSettingUpdateBuilder.supports("33", item(c, suffix).kind))
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun writeAcceptanceNeedsANewerMatchingReadbackAndDoesNotChangeOtherFields() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c, mode = 1, volume = 16)
        assertTrue(c.update("cloud:33") { CarSettingKind.Toggle(true) })
        assertEquals(CarSettingKind.Toggle(false), (state(c).vehicleValue as VehicleSettingValue.Known).kind)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, switch = 1, mode = 1, volume = 16, seq = 1)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, switch = 1, mode = 1, volume = 16, seq = 2)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
        assertEquals(1, (state(c, ":mode").displayKind() as CarSettingKind.Choice).selectedIndex)
        assertEquals(16f, (state(c, ":volume").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun refreshPreservesDraftAndCancellingDraftDoesNotSend() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:33") { CarSettingKind.Toggle(true) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(true), state(c).draftValue)
        c.cancelDraft("cloud:33"); assertFalse(c.submitDraft("cloud:33")); assertTrue(w.sent.isEmpty())
    }

    @Test fun failedAndCancelledWritesDoNotPretendSuccess() = runBlocking {
        for (cancel in listOf(false, true)) {
            val w = Writer { if (cancel) throw CancellationException("fake cancel") else false }
            val c = controller(w = w); assertTrue(c.refresh()); observe(c)
            try { assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) }); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c).writeState)
            assertNull(state(c).submittedValue); assertEquals(CarSettingKind.Toggle(false), state(c).displayKind())
        }
    }

    @Test fun lateWriteCannotContaminateNewVinAndActiveWriteBlocksRefreshAndDuplicates() = runBlocking {
        var vin = "VIN-A"; val result = CompletableDeferred<Boolean>(); val w = Writer { result.await() }
        val c = controller(w = w, vin = { vin }); assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); result.complete(true); job.join()
        assertFalse(c.simulatedSoundAvailable); assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        assertNull(state(c).submittedValue); assertNull(state(c).draftValue)
        assertFalse(c.update("cloud:33") { CarSettingKind.Toggle(true) }); assertEquals(1, w.sent.size)
    }
}
