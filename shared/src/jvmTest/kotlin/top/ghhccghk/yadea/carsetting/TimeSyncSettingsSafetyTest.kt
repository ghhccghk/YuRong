package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.carSettingSummary
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.ui.timeSyncStatusSummary
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** Result-only reports and commands are exercised with in-memory Fakes only. */
class TimeSyncSettingsSafetyTest {
    private class Gateway(var present: Boolean = true) : CarSettingsGateway {
        var writes = 0
        override suspend fun load(vin: String) = CloudCarSettings(
            if (present) listOf(CloudBikeConfig("51")) else emptyList(), emptyMap())
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { writes++; return true }
    }
    private class Writer(var action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun controller(g: Gateway = Gateway(), w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun state(c: CarSettingsController) = c.state.value.valueState("cloud:51")
    private fun item(c: CarSettingsController) = c.state.value.items.single { it.id == "cloud:51" }
    private fun observe(c: CarSettingsController, raw: Int = 1, result: Int = 1, seq: Long = 1,
                        vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, empty.copy(extended = empty.extended.copy(
            syncTimeResultStatus = raw, syncTimeResult = result)), origin, seq))
    }

    @Test fun originalEchoIsReadableButNeverManufacturesASettingOrDefaultChoice() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        assertNull(c.state.value.timeSyncReport); assertNull(state(c).displayKind())
        assertFalse(c.update("cloud:51") { it })
        for ((n, raw) in listOf(0, 1).withIndex()) {
            observe(c, raw, seq = 1L + n)
            assertEquals(raw == 1, c.state.value.timeSyncReport!!.enabledEcho)
            assertTrue(c.state.value.timeSyncReport!!.live)
            assertEquals(SettingValueSource.LIVE_TTP, c.state.value.timeSyncReport!!.source)
            assertNull(state(c).configuredValue); assertNull(state(c).cachedValue); assertNull(state(c).displayKind())
            assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
            assertNull(settingSwitchTarget(state(c), true))
            assertFalse((item(c).kind as CarSettingKind.Toggle).valueKnown)
        }
    }

    @Test fun missingInvalidAndOtherResultFieldsCannotInventAnEcho() = runBlocking {
        for (raw in listOf(-1, 2, 255)) {
            val c = controller(); assertTrue(c.refresh()); observe(c, raw)
            assertNull(c.state.value.timeSyncReport); assertNull(state(c).displayKind())
        }
        val c = controller(); assertTrue(c.refresh()); observe(c); observe(c, -1, seq = 2)
        assertNull(c.state.value.timeSyncReport)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
    }

    @Test fun cachedReportsAndDisconnectedReportsAreExplicitHistoryAndCannotConfirm() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        assertFalse(c.state.value.timeSyncReport!!.live)
        assertEquals(SettingValueSource.CACHED_TTP, c.state.value.timeSyncReport!!.source)
        assertTrue(timeSyncStatusSummary(c.state.value.timeSyncReport).contains("历史上报"))
        observe(c, seq = 2); c.updateVehicleObservation(null)
        assertFalse(c.state.value.timeSyncReport!!.live)
        assertEquals(SettingConfirmationState.UNKNOWN, state(c).confirmationState)
        assertNull(state(c).cachedValue)
    }

    @Test fun wrongVinAndOldSequencesDoNotReplaceNewReport() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, raw = 1, seq = 2)
        observe(c, raw = 0, seq = 1); observe(c, raw = 0, seq = 3, vin = "VIN-B")
        assertTrue(c.state.value.timeSyncReport!!.enabledEcho)
        assertEquals("VIN-A", c.state.value.timeSyncReport!!.vin)
    }

    @Test fun directoryAbsenceAndVinSwitchCannotLeakReports() = runBlocking {
        val absent = controller(Gateway(false)); assertTrue(absent.refresh()); observe(absent)
        assertNull(absent.state.value.timeSyncReport)
        var vin = "VIN-A"; val c = controller(vin = { vin }); assertTrue(c.refresh()); observe(c)
        vin = "VIN-B"; c.onVinChanged(vin); assertNull(c.state.value.timeSyncReport); assertTrue(c.refresh())
        observe(c, vin = "VIN-A", seq = 2); assertNull(c.state.value.timeSyncReport)
    }

    @Test fun reportedEchoNeverConfirmsEitherAnOldOrNewWrite() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        assertTrue(c.update("cloud:51") { CarSettingKind.Toggle(true) })
        observe(c, result = 0, seq = 2); observe(c, result = 1, seq = 3)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        assertEquals(CarSettingKind.Toggle(true), state(c).submittedValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        assertEquals(SettingWriteState.LOCAL_UPDATED, state(c).writeState)
    }

    @Test fun finalMapperAndActualMappedWriterRequireKnownBooleanAndStableId() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val commands = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        for (kind in listOf(CarSettingKind.Toggle(true, false), CarSettingKind.Choice(0, listOf("0")))) {
            assertNull(VehicleSettingCommands.forConfigId("51", kind))
            assertFalse(w.write(item(c).copy(kind = kind))); assertFalse(c.update("cloud:51") { kind })
        }
        val known = item(c).copy(kind = CarSettingKind.Toggle(true))
        assertFalse(w.write(known.copy(id = "cloud:wrong")))
        assertTrue(w.write(known)); assertTrue(w.write(known.copy(kind = CarSettingKind.Toggle(false))))
        assertEquals(listOf(1, 0), commands.map { it.parameter }); assertTrue(commands.all { it.commandId == 0xC1 })
        assertNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("COMMAND_CONST_TIME_SYNC", 0xC0, 123456)))
    }

    @Test fun actualCloudWriterCannotTurnUnknownOrOfflineOperationIntoCloudSuccess() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); val ble = Writer()
        val w = CloudCarSettingWriter(g, { "VIN-A" }, ble, { true })
        assertFalse(w.write(item(c))); assertTrue(ble.sent.isEmpty())
        assertTrue(w.write(item(c).copy(kind = CarSettingKind.Toggle(true))))
        assertEquals(1, ble.sent.size); assertEquals(0, g.writes)
        assertFalse(CloudCarSettingWriter(g, { "VIN-A" }).write(item(c).copy(kind = CarSettingKind.Toggle(true))))
        assertEquals(0, g.writes)
    }

    @Test fun draftSurvivesRefreshAndCannotBeConfirmedByReporting() = runBlocking {
        val w = Writer(); val c = controller(w = w); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:51") { CarSettingKind.Toggle(false) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(false), state(c).draftValue); assertNotNull(c.state.value.timeSyncReport)
        observe(c, raw = 0, seq = 2); assertEquals(CarSettingKind.Toggle(false), state(c).draftValue)
        c.cancelDraft("cloud:51"); assertFalse(c.submitDraft("cloud:51")); assertTrue(w.sent.isEmpty())
    }

    @Test fun failuresAndCancellationRemainSeparateFromReports() = runBlocking {
        for (cancel in listOf(false, true)) {
            val w = Writer { if (cancel) throw CancellationException("fake") else false }
            val c = controller(w = w); assertTrue(c.refresh()); observe(c)
            try { assertFalse(c.update("cloud:51") { CarSettingKind.Toggle(true) }); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c).writeState)
            assertNull(state(c).submittedValue); assertNull(state(c).displayKind()); assertNotNull(c.state.value.timeSyncReport)
        }
    }

    @Test fun lateWriteCannotConfirmNewVinAndInFlightRefreshIsRejected() = runBlocking {
        var vin = "VIN-A"; val result = CompletableDeferred<Boolean>(); val w = Writer { result.await() }
        val c = controller(w = w, vin = { vin }); assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:51") { CarSettingKind.Toggle(true) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:51") { CarSettingKind.Toggle(false) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); result.complete(true); job.join()
        assertNull(c.state.value.timeSyncReport); assertNull(state(c).submittedValue)
        assertEquals(SettingConfirmationState.UNKNOWN, state(c).confirmationState)
    }

    @Test fun uiNamesTheEchoAndItsConfirmationLimitWithoutClaimingARealValue() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        val summary = carSettingSummary(item(c), state(c), false, null, c.state.value.timeSyncReport)
        assertTrue(summary.contains("仪表同步上报：开启")); assertTrue(summary.contains("不代表本次校时成功"))
        assertTrue(summary.contains("UNKNOWN")); assertFalse(summary.contains("回读一致"))
    }
}
