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

class EnergyRecoverySettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun cloud(switch: Boolean = true) = CloudCarSettings(configs =
        (if (switch) listOf(CloudBikeConfig("17")) else emptyList()) + CloudBikeConfig("18"),
        values = mapOf("resistanceOn" to "1"))
    private class Gateway(val action: suspend (String) -> CloudCarSettings) : CarSettingsGateway {
        override suspend fun load(vin: String) = action(vin)
    }
    private class Writer(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" },
                           g: CarSettingsGateway = Gateway { cloud() }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == "cloud:$id" }
    private fun level(c: CarSettingsController, value: Float) =
        (item(c, "18").kind as CarSettingKind.Slider).copy(value = value, valueKnown = true)
    private fun observe(c: CarSettingsController, switch: Int = 1, level: Int = 1, seq: Long = 1,
                        vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, unknown.copy(extended = unknown.extended.copy(
            slideModePowerRecoverStatus = switch, slideModePowerRecoverCurrent = level)), origin, seq))
    }

    @Test fun dtoKeyCannotCreateKnownConfigurationDefaultDraftOrCloudWrite() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (id in EnergyRecoverySettings.ids) {
            val i = item(c, id); val s = c.state.value.valueState(i.id)
            assertNull(s.configuredValue); assertNull(s.displayKind()); assertNull(s.draftValue)
            assertTrue(VehicleSettingCommands.supportsItem(i)); assertNull(VehicleSettingCommands.forItem(i))
            assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
        }
        assertNull(CloudCarSettingUpdateBuilder.build("17", CarSettingKind.Toggle(true), "VIN-A"))
        assertNull(CloudCarSettingUpdateBuilder.build("18", level(c, 2f), "VIN-A"))
        assertTrue(w.sent.isEmpty())
    }

    @Test fun independentSwitchAndThreeLevelsReachMappedWriterExactly() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for (on in listOf(false, true)) {
            assertTrue(w.write(item(c, "17").copy(kind = CarSettingKind.Toggle(on))))
            assertEquals(if (on) 0x13 else 0x14, sent.last().commandId); assertEquals(-1, sent.last().parameter)
        }
        for (v in 1..3) {
            assertTrue(w.write(item(c, "18").copy(kind = level(c, v.toFloat()))))
            assertEquals(0x3C, sent.last().commandId); assertEquals(v, sent.last().parameter)
            assertNotNull(VehicleSettingFrameEncoder.encode(sent.last()))
        }
        assertEquals(5, sent.size)
    }

    @Test fun unknownFractionalOutOfRangeAndForgedTargetsCannotReachWriter() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh()); observe(c)
        val i = item(c, "18"); val good = level(c, 2f)
        val writer = CloudCarSettingWriter(Gateway { cloud() }, { "VIN-A" }, w, { true })
        for (bad in listOf(0f, 4f, -1f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY).map { good.copy(value = it) } +
            listOf(good.copy(valueKnown = false), good.copy(rangeKnown = false), good.copy(range = 0f..7f))) {
            assertFalse(c.editDraft(i.id) { bad }); assertFalse(c.update(i.id) { bad })
            assertNull(VehicleSettingCommands.forConfigId("18", bad)); assertNull(VehicleSettingCommands.forItem(i.copy(kind = bad)))
            assertFalse(writer.write(i.copy(kind = bad)))
        }
        for (id in EnergyRecoverySettings.ids) {
            val legal = if (id == "17") CarSettingKind.Toggle(true) else good
            val forged = item(c, id).copy(id = "cloud:002:level", kind = legal)
            assertNull(VehicleSettingCommands.forItem(forged)); assertFalse(writer.write(forged))
        }
        assertNull(VehicleSettingCommands.forConfigId("17", CarSettingKind.Toggle(true, valueKnown = false)))
        assertTrue(w.sent.isEmpty())
    }

    @Test fun rawCommandBoundaryKeepsEstablishedDomainButNeverClampsInvalidValues() {
        for (v in listOf(-1, 8, 255)) {
            try { VehicleSettingCommand("ENERGY_RECOVER_LEVEL", 0x3C, v); fail("accepted $v") }
            catch (_: IllegalArgumentException) { }
        }
        assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("ENERGY_RECOVER_LEVEL", 0x3C, 0)))
        assertNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("ENERGY_RECOVER_LEVEL", 0x3C, 7)))
    }

    @Test fun ttpReadsIndependentValuesAndInvalidObservationsLoseLiveStatus() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (v in 1..3) {
            observe(c, switch = 0, level = v, seq = v.toLong())
            assertEquals(CarSettingKind.Toggle(false), (c.state.value.valueState("cloud:17").vehicleValue as VehicleSettingValue.Known).kind)
            assertEquals(v.toFloat(), ((c.state.value.valueState("cloud:18").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        }
        for ((index, bad) in listOf(-1, 0, 4, 255).withIndex()) {
            observe(c, switch = if (bad == 0) 2 else bad, level = bad, seq = 4L + index)
            for (id in EnergyRecoverySettings.ids) assertFalse((c.state.value.valueState("cloud:$id").vehicleValue as VehicleSettingValue.Known).live)
        }
    }

    @Test fun levelOnlyAndCombinedModesHaveDifferentObservedSwitchRequirements() = runBlocking {
        val only = controller(g = Gateway { cloud(switch = false) }); assertTrue(only.refresh())
        assertTrue(only.editDraft("cloud:18") { level(only, 2f) }); assertTrue(only.submitDraft("cloud:18"))
        val c = controller(); assertTrue(c.refresh())
        assertFalse(c.editDraft("cloud:18") { level(c, 2f) })
        observe(c, origin = VehicleStatusOrigin.CACHED_TTP); assertFalse(c.editDraft("cloud:18") { level(c, 2f) })
        observe(c, switch = 0, seq = 2)
        assertTrue(c.editDraft("cloud:17") { CarSettingKind.Toggle(true) })
        assertFalse(c.editDraft("cloud:18") { level(c, 2f) })
        assertTrue(c.submitDraft("cloud:17")); assertFalse(c.editDraft("cloud:18") { level(c, 2f) })
        observe(c, seq = 3); assertTrue(c.editDraft("cloud:18") { level(c, 2f) })
        assertTrue(c.submitDraft("cloud:18"))
    }

    @Test fun highLevelRequiresExplicitConfirmationAndCancellationSendsNothing() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh()); observe(c)
        assertEquals(3f, settingSliderDraftValue(item(c, "18"), 2.6f))
        assertTrue(c.editDraft("cloud:18") { level(c, 3f) })
        assertFalse(c.submitDraft("cloud:18")); assertFalse(c.update("cloud:18") { level(c, 3f) })
        assertTrue(w.sent.isEmpty()); assertNotNull(c.state.value.valueState("cloud:18").draftValue)
        c.cancelDraft("cloud:18"); assertFalse(c.submitDraft("cloud:18", confirmEnergyRecoveryHigh = true))
        assertTrue(w.sent.isEmpty())
        assertTrue(c.editDraft("cloud:18") { level(c, 3f) })
        assertTrue(c.submitDraft("cloud:18", confirmEnergyRecoveryHigh = true))
        assertEquals(1, w.sent.size)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:18").confirmationState)
        observe(c, level = 3, seq = 2)
        assertTrue(c.update("cloud:18") { level(c, 3f) })
        observe(c, level = 3, seq = 3, origin = VehicleStatusOrigin.CACHED_TTP)
        assertFalse(c.update("cloud:18") { level(c, 3f) })
    }

    @Test fun lowerLevelsDoNotNeedHighAcknowledgementAndReceiptsDoNotChangeObservedValues() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, seq = 10)
        assertTrue(c.editDraft("cloud:18") { level(c, 2f) }); assertTrue(c.refresh())
        assertNotNull(c.state.value.valueState("cloud:18").draftValue); assertTrue(c.submitDraft("cloud:18"))
        val s = c.state.value.valueState("cloud:18")
        assertEquals(1f, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(SettingWriteState.LOCAL_UPDATED, s.writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, s.confirmationState)
        observe(c, level = 2, seq = 9); assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:18").confirmationState)
        observe(c, level = 2, seq = 11); assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:18").confirmationState)
    }

    @Test fun cachedWrongVinAndOlderTtpNeverBecomeCurrentLiveObservations() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        for (id in EnergyRecoverySettings.ids) {
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertNotNull(s.cachedValue)
        }
        observe(c, seq = 10); val old = c.state.value.valueStates
        observe(c, level = 3, seq = 9); observe(c, level = 3, seq = 11, vin = "VIN-B")
        assertEquals(old, c.state.value.valueStates)
    }

    @Test fun failureAndCancellationRollbackWithoutInventingVehicleSuccess() = runBlocking {
        for (cancel in listOf(false, true)) for (id in EnergyRecoverySettings.ids) {
            val c = controller(Writer { if (cancel) throw CancellationException("cancel") else false })
            assertTrue(c.refresh()); observe(c)
            val old = c.state.value.valueState("cloud:$id").vehicleValue
            assertTrue(c.editDraft("cloud:$id") { if (id == "17") CarSettingKind.Toggle(false) else level(c, 2f) })
            try { assertFalse(c.submitDraft("cloud:$id")); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(old, s.vehicleValue); assertNull(s.submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, s.writeState)
        }
    }

    @Test fun vinSwitchInvalidatesDraftAndInFlightReceiptAndBlocksRefreshRace() = runBlocking {
        var vin = "VIN-A"; val receipt = CompletableDeferred<Boolean>(); val w = Writer { receipt.await() }
        val c = controller(w, { vin }); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:18") { level(c, 3f) })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:17") { CarSettingKind.Toggle(false) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:17") { CarSettingKind.Toggle(true) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); receipt.complete(true); job.join()
        assertFalse(c.submitDraft("cloud:18", confirmEnergyRecoveryHigh = true))
        for (id in EnergyRecoverySettings.ids) {
            val s = c.state.value.valueState("cloud:$id")
            assertNull(s.draftValue); assertNull(s.submittedValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }
}
