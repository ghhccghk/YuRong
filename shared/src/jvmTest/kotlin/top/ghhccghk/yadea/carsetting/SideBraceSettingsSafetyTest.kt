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

class SideBraceSettingsSafetyTest {
    private class Gateway(var dependent: Boolean = true) : CarSettingsGateway {
        override suspend fun load(vin: String) = CloudCarSettings(
            listOf(CloudBikeConfig("2"), CloudBikeConfig("3"), CloudBikeConfig("7")) +
                if (dependent) listOf(CloudBikeConfig("39")) else emptyList(), emptyMap())
    }
    private class Writer(var action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun controller(g: Gateway = Gateway(), w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun state(c: CarSettingsController, id: String = "2") = c.state.value.valueState("cloud:$id")
    private fun item(c: CarSettingsController, id: String = "2") = c.state.value.items.single { it.id == "cloud:$id" }
    private fun observe(c: CarSettingsController, brace: Int = 1, lock: Int = 0, seq: Long = 1,
                        vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, unknown.copy(extended = unknown.extended.copy(
            sideBraceStatus = brace, leaveVehicleAutoLockStatus = lock)), origin, seq))
    }

    @Test fun dynamicEntriesRemainUnknownWithoutGuessedDefaultsOrReadFields() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh())
        for (id in listOf("2", "3")) {
            assertTrue(item(c, id).writable); assertNull(state(c, id).displayKind()); assertNull(state(c, id).configuredValue)
            assertFalse(c.editDraft("cloud:$id") { it }); assertFalse(c.update("cloud:$id") { it })
            assertNull(VehicleSettingCommands.forItem(item(c, id)))
            assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Toggle(true), "VIN-A"))
        }
        observe(c)
        assertNull(state(c, "3").displayKind()); assertEquals(VehicleSettingValue.Unknown, state(c, "3").vehicleValue)
        assertTrue(w.sent.isEmpty())
    }

    @Test fun sideBraceOnlyAcceptsStrictSameVinNewerObservationsAndInvalidatesBadValues() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, vin = "VIN-B"); assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        observe(c, origin = VehicleStatusOrigin.CACHED_TTP); assertNotNull(state(c).cachedValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        observe(c, seq = 2); assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        observe(c, brace = 0, seq = 1); assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        observe(c, brace = 2, seq = 3); assertFalse((state(c).vehicleValue as VehicleSettingValue.Known).live)
        assertEquals(SettingReadbackState.MISSING_OR_INVALID, state(c).readbackState)
    }

    @Test fun closingWithoutDependentCloudDescriptorUsesOriginalIndependentCommand() = runBlocking {
        val g = Gateway(false); val commands = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        val c = controller(g, CloudCarSettingWriter(g, { "VIN-A" }, w, { true })); assertTrue(c.refresh()); observe(c, lock = -1)
        assertTrue(c.state.value.items.none { it.configId == "39" })
        assertTrue(c.state.value.missingCloudItems.any { it.configId == "39" })
        assertTrue(c.update("cloud:2") { CarSettingKind.Toggle(false) })
        assertEquals(listOf(0x10), commands.map { it.commandId })
        assertNull(item(c).sideBraceCloseAuthorization)
    }

    @Test fun disabledLiveLockMakesIndependentCloseSafeInBothModelBranches() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c, lock = 0)
        assertTrue(c.update("cloud:2") { CarSettingKind.Toggle(false) })
        assertNotNull(w.sent.single().sideBraceCloseAuthorization)
        assertEquals(0x10, VehicleSettingCommands.forItem(w.sent.single())!!.commandId)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        assertEquals(CarSettingKind.Toggle(true), (state(c).vehicleValue as VehicleSettingValue.Known).kind)
        observe(c, brace = 0, seq = 2); assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
    }

    @Test fun enabledUnknownInvalidAndCachedLockCannotChooseAnUnprovenModelCommand() = runBlocking {
        for (lock in listOf(1, -1, 2)) {
            val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c, lock = lock)
            assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) })
            assertEquals(SettingWriteState.FAILED, state(c).writeState); assertNotNull(state(c).error); assertTrue(w.sent.isEmpty())
        }
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh())
        observe(c, lock = 0, origin = VehicleStatusOrigin.CACHED_TTP)
        assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) }); assertTrue(w.sent.isEmpty())
    }

    @Test fun turningOnNeedsNoModelDependentCloseEvidence() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c, brace = 0, lock = 1)
        assertTrue(c.update("cloud:2") { CarSettingKind.Toggle(true) })
        assertEquals(0x0F, VehicleSettingCommands.forItem(w.sent.single())!!.commandId)
        assertNull(w.sent.single().sideBraceCloseAuthorization)
    }

    @Test fun directMapperAndWritersRejectMissingProofWrongVinUnknownAndForgedEntries() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); val i = item(c)
        val sent = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        val w = CloudCarSettingWriter(g, { "VIN-A" }, mapped, { true })
        val off = i.copy(kind = CarSettingKind.Toggle(false))
        assertNull(VehicleSettingCommands.forConfigId("2", off.kind)); assertFalse(mapped.write(off)); assertFalse(w.write(off))
        for (bad in listOf(off.copy(sideBraceCloseAuthorization = SideBraceCloseAuthorization("VIN-B")),
            off.copy(id = "cloud:2:enable", sideBraceCloseAuthorization = SideBraceCloseAuthorization("VIN-A")))) assertFalse(w.write(bad))
        for (id in listOf("2", "3")) {
            val unknown = item(c, id).copy(kind = CarSettingKind.Toggle(true, false))
            assertNull(VehicleSettingCommands.forConfigId(id, unknown.kind)); assertFalse(mapped.write(unknown)); assertFalse(w.write(unknown))
            assertFalse(c.editDraft("cloud:$id") { unknown.kind }); assertFalse(c.update("cloud:$id") { unknown.kind })
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun carryOutHasEstablishedIndependentWritesButNoFabricatedReadback() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for (on in listOf(false, true)) {
            assertTrue(w.write(item(c, "3").copy(kind = CarSettingKind.Toggle(on))))
            assertEquals(if (on) 0x11 else 0x12, sent.last().commandId); assertEquals(-1, sent.last().parameter)
            assertNotNull(VehicleSettingFrameEncoder.encode(sent.last()))
        }
        assertTrue(c.update("cloud:3") { CarSettingKind.Toggle(true) }); observe(c, seq = 2)
        assertEquals(VehicleSettingValue.Unknown, state(c, "3").vehicleValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c, "3").confirmationState)
    }

    @Test fun sharedLockSwitchAndBraceWritesExcludeEachOther() = runBlocking {
        for (first in listOf("2", "7", "39")) {
            val pending = CompletableDeferred<Boolean>(); val w = Writer { pending.await() }; val c = controller(w = w)
            assertTrue(c.refresh()); observe(c, lock = 0)
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                assertTrue(c.update("cloud:$first") { CarSettingKind.Toggle(true) })
            }
            for (other in listOf("2", "7", "39").filter { it != first })
                assertFalse(c.update("cloud:$other") { CarSettingKind.Toggle(true) })
            assertFalse(c.refresh()); pending.complete(true); job.join(); assertEquals(1, w.sent.size)
        }
    }

    @Test fun acceptedLockTargetDoesNotOverrideTheActualLockSnapshotForClose() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, lock = 0)
        assertTrue(c.update("cloud:39") { CarSettingKind.Toggle(true) })
        assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) })
        observe(c, lock = 1, seq = 2)
        assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) })
        assertEquals(CarSettingKind.Toggle(true), (state(c).vehicleValue as VehicleSettingValue.Known).kind)
    }

    @Test fun failedCancelledAndOldVinReceiptsNeverConfirmVehicleState() = runBlocking {
        for (cancel in listOf(false, true)) {
            val c = controller(w = Writer { if (cancel) throw CancellationException("cancel") else false })
            assertTrue(c.refresh()); observe(c)
            try { assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) }); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c).writeState)
            assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        }
        var vin = "VIN-A"; val pending = CompletableDeferred<Boolean>(); val c = controller(w = Writer { pending.await() }) { vin }
        assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:2") { CarSettingKind.Toggle(false) }) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); pending.complete(true); job.join()
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue); assertNull(state(c).submittedValue)
        assertNull(item(c).sideBraceCloseAuthorization)
    }

    @Test fun refreshKeepsDraftWithoutCreatingCloseAuthorization() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:2") { CarSettingKind.Toggle(false) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(false), state(c).draftValue); assertNull(item(c).sideBraceCloseAuthorization)
        c.cancelDraft("cloud:2"); assertFalse(c.submitDraft("cloud:2"))
    }
}
