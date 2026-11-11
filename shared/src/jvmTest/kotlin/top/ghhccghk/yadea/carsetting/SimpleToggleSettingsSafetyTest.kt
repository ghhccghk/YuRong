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

class SimpleToggleSettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val cloud = CloudCarSettings(configs = SimpleToggleSettings.ids.map { CloudBikeConfig(it) },
        values = mapOf("bleApproachPowerOnEnable" to "1"))
    private class Gateway(val action: suspend () -> CloudCarSettings) : CarSettingsGateway {
        override suspend fun load(vin: String) = action()
    }
    private class Writer(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = Gateway { cloud }, vinProvider = vin, debounceMs = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.configId == id }
    private fun observe(c: CarSettingsController, raw: Int, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        val info = unknown.copy(extended = unknown.extended.copy(automaticHeadlightFunctionStatus = raw,
            turnSignalReturnsAutomaticallyStatus = raw, brakePowerDownFunctionStatus = raw, templeSettingStatus = raw,
            tcsWorkMode = if (raw in 0..1) 1 - raw else raw, absFunctionStatus = raw,
            steepSlopeState = raw, rampSlopeState = raw, tPowerStatus = raw, quickBrakeLightStatus = raw,
            longEnduranceStatus = if (raw in 0..1) 1 - raw else raw, seatPowerStatus = raw))
        c.updateVehicleObservation(VehicleStatusObservation(vin, info, origin, seq))
    }

    @Test fun unknownIsNotFalseAndCannotBeSubmittedThroughDirectEntrypoints() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (id in SimpleToggleSettings.ids) {
            val i = item(c, id)
            assertFalse((i.kind as CarSettingKind.Toggle).valueKnown)
            assertNull(c.state.value.valueState(i.id).displayKind())
            assertNull(c.state.value.valueState(i.id).configuredValue)
            assertTrue(VehicleSettingCommands.supportsItem(i))
            assertNull(VehicleSettingCommands.forItem(i)); assertNull(VehicleSettingCommands.forConfigId(id, i.kind))
            assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
            assertFalse(CloudCarSettingWriter(Gateway { cloud }, { "VIN-A" }, w, { true }).write(i))
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun bothExplicitTargetsReachMappedWriterWithExactExistingCommand() = runBlocking {
        val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        val c = controller(); assertTrue(c.refresh())
        for (id in SimpleToggleSettings.ids) for (on in listOf(false, true)) {
            assertTrue(w.write(item(c, id).copy(kind = CarSettingKind.Toggle(on))))
            val command = sent.last()
            assertEquals(when (id) {
                "19" -> if (on) 0x25 else 0x26
                "26" -> if (on) 0x23 else 0x24
                "36" -> if (on) 0x9B else 0x9C
                "37" -> 0x9D
                "38" -> if (on) 0x99 else 0x9A
                "46" -> if (on) 0xAE else 0xAF
                "49" -> if (on) 0xB0 else 0xB1
                "63" -> 0xCF
                "68" -> 0xD9
                "72" -> 0xDD
                "79" -> 0xFB1
                else -> 0xC7
            }, command.commandId)
            assertEquals(when (id) {
                "53", "63", "68", "79" -> if (on) 1 else 0
                "37", "72" -> if (on) 0 else 1
                else -> -1
            }, command.parameter)
            assertNotNull(VehicleSettingFrameEncoder.encode(command))
            assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Toggle(on), "VIN-A"))
        }
        assertEquals(24, sent.size)
    }

    @Test fun forgedStableIdAndWrongKindCannotBypassValidation() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (id in SimpleToggleSettings.ids) {
            val forged = item(c, id).copy(id = "cloud:002:level", kind = CarSettingKind.Toggle(true))
            assertNull(VehicleSettingCommands.forItem(forged))
            assertFalse(CloudCarSettingWriter(Gateway { cloud }, { "VIN-A" }, w, { true }).write(forged))
            assertFalse(c.update(item(c, id).id) { CarSettingKind.Slider(1f, 0f..1f) })
        }
        assertTrue(w.sent.isEmpty())
        for (raw in listOf(-1, 2, 255)) {
            try { VehicleSettingCommand("COMMAND_CONST_TEMPLE_SETTING", 0xC7, raw); fail("invalid parameter") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rawBooleanOnlyZeroAndOneCreateLiveVehicleValue() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (raw in 0..1) {
            observe(c, raw, raw + 1L)
            for (id in SimpleToggleSettings.ids) {
                val value = c.state.value.valueState("cloud:$id")
                val live = value.vehicleValue as VehicleSettingValue.Known
                assertTrue(live.live); assertEquals(raw == 1, (live.kind as CarSettingKind.Toggle).checked)
                assertNull(value.configuredValue)
            }
        }
        observe(c, 99, 3)
        for (id in SimpleToggleSettings.ids) {
            val value = c.state.value.valueState("cloud:$id")
            assertFalse((value.vehicleValue as VehicleSettingValue.Known).live)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, value.readbackState)
        }
    }

    @Test fun cacheWrongVinAndOldSequenceCannotClaimFreshReadback() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, 1, origin = VehicleStatusOrigin.CACHED_TTP)
        for (id in SimpleToggleSettings.ids) {
            val value = c.state.value.valueState("cloud:$id")
            assertTrue(value.vehicleValue is VehicleSettingValue.Unknown)
            assertFalse(requireNotNull(value.cachedValue).live)
        }
        observe(c, 0, 10)
        val before = c.state.value.valueStates
        observe(c, 1, 11, "VIN-B"); observe(c, 1, 9)
        assertEquals(before, c.state.value.valueStates)
    }

    @Test fun tcsPreservesOriginalZeroOnAndOneThroughSevenOffWithoutClampingInvalidRaw() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (raw in 0..7) {
            val info = unknown.copy(extended = unknown.extended.copy(tcsWorkMode = raw))
            c.updateVehicleObservation(VehicleStatusObservation("VIN-A", info, VehicleStatusOrigin.LIVE_TTP, raw + 1L))
            val value = c.state.value.valueState("cloud:37").vehicleValue as VehicleSettingValue.Known
            assertEquals(raw == 0, (value.kind as CarSettingKind.Toggle).checked)
            assertTrue(value.live)
            assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("TCS_BODY_STABILITY", 0x9D, raw)))
        }
        for (raw in listOf(-1, 8, 255)) {
            try { VehicleSettingCommand("TCS_BODY_STABILITY", 0x9D, raw); fail("invalid TCS parameter") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun enduranceRawZeroIsOnWhileOtherThreeReportOneAsOnAndCommandPolarityMatchesPage() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (raw in 0..1) {
            val info = unknown.copy(extended = unknown.extended.copy(tPowerStatus = raw,
                quickBrakeLightStatus = raw, longEnduranceStatus = raw, seatPowerStatus = raw))
            c.updateVehicleObservation(VehicleStatusObservation("VIN-A", info, VehicleStatusOrigin.LIVE_TTP, raw + 1L))
            for (id in listOf("63", "68", "72", "79")) {
                val kind = (c.state.value.valueState("cloud:$id").vehicleValue as VehicleSettingValue.Known).kind
                assertEquals(id, raw == if (id == "72") 0 else 1, (kind as CarSettingKind.Toggle).checked)
                assertEquals(raw, requireNotNull(VehicleSettingCommands.forConfigId(id, kind)).parameter)
            }
        }
        for ((type, id) in listOf("COMMAND_CONST_T_POWER_SETTING" to 0xCF,
            "COMMAND_QUICK_BRAKE_LIGHT_SWITCH" to 0xD9, "LONG_ENDURANCE_SETTING" to 0xDD,
            "COMMAND_SEAT_POWER_UP" to 0xFB1)) for (raw in listOf(-1, 2, 255)) {
            try { VehicleSettingCommand(type, id, raw); fail("invalid direct switch parameter") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun refreshKeepsDraftAndReceiptWaitsForPostWriteObservation() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, 0)
        for (id in SimpleToggleSettings.ids) {
            assertTrue(c.editDraft("cloud:$id") { CarSettingKind.Toggle(true) })
        }
        assertTrue(c.refresh())
        for (id in SimpleToggleSettings.ids) {
            assertEquals(CarSettingKind.Toggle(true), c.state.value.valueState("cloud:$id").draftValue)
            assertTrue(c.submitDraft("cloud:$id"))
            assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:$id").confirmationState)
            assertFalse(((c.state.value.valueState("cloud:$id").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked)
        }
        observe(c, 1, 2)
        for (id in SimpleToggleSettings.ids) assertEquals(SettingConfirmationState.READBACK_MATCHED,
            c.state.value.valueState("cloud:$id").confirmationState)
    }

    @Test fun failuresAndCancellationPreserveVehicleObservation() = runBlocking {
        for (cancel in listOf(false, true)) {
            val c = controller(Writer { if (cancel) throw CancellationException("fake") else false })
            assertTrue(c.refresh()); observe(c, 0)
            for (id in SimpleToggleSettings.ids) {
                try { assertFalse(c.update("cloud:$id") { CarSettingKind.Toggle(true) }) }
                catch (e: CancellationException) { assertTrue(cancel) }
                val value = c.state.value.valueState("cloud:$id")
                assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, value.writeState)
                assertFalse(((value.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked)
            }
        }
    }

    @Test fun vinChangeDropsOldDraftAndOldInflightReceipt() = runBlocking {
        var vin = "VIN-A"; val gate = CompletableDeferred<Boolean>(); val c = controller(Writer { gate.await() }, { vin })
        assertTrue(c.refresh()); observe(c, 0)
        assertTrue(c.editDraft("cloud:53") { CarSettingKind.Toggle(true) })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.submitDraft("cloud:53")) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); gate.complete(true); job.join()
        val value = c.state.value.valueState("cloud:53")
        assertNull(value.submittedValue); assertNull(value.draftValue); assertTrue(value.vehicleValue is VehicleSettingValue.Unknown)
    }

    @Test fun refreshAndDuplicateWriteBlockedDuringSubmission() = runBlocking {
        for (id in SimpleToggleSettings.ids) {
            val gate = CompletableDeferred<Boolean>(); val w = Writer { gate.await() }; val c = controller(w)
            assertTrue(c.refresh()); observe(c, 0)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(c.update("cloud:$id") { CarSettingKind.Toggle(true) }) }
            assertFalse(c.refresh()); assertFalse(c.update("cloud:$id") { CarSettingKind.Toggle(true) })
            gate.complete(true); job.join(); assertEquals(1, w.sent.size)
        }
    }
}
