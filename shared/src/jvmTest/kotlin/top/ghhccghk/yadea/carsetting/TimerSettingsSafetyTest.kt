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

class TimerSettingsSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val cloud = CloudCarSettings(values = emptyMap(), configs = TimerSettings.ids.map { CloudBikeConfig(it) })
    private val keys = TimerSettings.ids.flatMap { listOf("cloud:$it", "cloud:$it:time") }
    private class FakeGateway(var cloud: CloudCarSettings) : CarSettingsGateway {
        override suspend fun load(vin: String) = cloud
    }
    private class FakeWriter(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(w: CarSettingWriter = FakeWriter(), g: CarSettingsGateway = FakeGateway(cloud),
                           vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }
    private fun status(time: Int = -1, light: Int = -1, enabled: Int = -1) = unknown.copy(extended = unknown.extended.copy(
        vehiclePowerDownTimeStatus = time, vehiclePowerDownStatus = enabled,
        withGoHomeTimeStatus = light, withGoHomeStatus = enabled,
    ))
    private fun observe(c: CarSettingsController, time: Int, light: Int, seq: Long = 1, enabled: Int = 1,
                        vin: String = "VIN-A", origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) =
        c.updateVehicleObservation(VehicleStatusObservation(vin, status(time, light, enabled), origin, seq))

    @Test fun missingAndDtoOnlyCloudValuesNeverCreateDefaultsOrDrafts() = runBlocking {
        for (fields in listOf(emptyMap(), mapOf("shutDownTime" to "3", "vehiclePowerDownStatus" to "true"))) {
            val c = controller(g = FakeGateway(cloud.copy(values = fields))); assertTrue(c.refresh())
            for (id in keys) {
                val s = c.state.value.valueState(id)
                assertNull(s.configuredValue); assertNull(s.displayKind()); assertNull(s.draftValue)
                assertFalse(c.update(id) { it }); assertNull(VehicleSettingCommands.forItem(item(c, id)))
                assertTrue(VehicleSettingCommands.supportsItem(item(c, id)))
            }
        }
    }

    @Test fun ttpFillsSwitchesAndEveryAllowedTimePosition() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (time in 0..20) {
            observe(c, time, time % 4, time + 1L, time % 2)
            for (id in TimerSettings.ids) {
                val s = c.state.value.valueState("cloud:$id")
                assertEquals(time % 2 == 1, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked)
            }
            assertEquals(time, ((c.state.value.valueState("cloud:59:time").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
            if (time >= 2) assertEquals(time.toFloat(),
                ((c.state.value.valueState("cloud:15:time").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        }
    }

    @Test fun unknownAndOutOfRangeTtpDoesNotBecomeLive() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c, 3, 1)
        observe(c, 21, 4, 2, enabled = 2)
        for (id in keys) {
            val s = c.state.value.valueState(id)
            assertFalse((s.vehicleValue as VehicleSettingValue.Known).live)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, s.readbackState)
        }
        val clean = controller(); assertTrue(clean.refresh()); observe(clean, -1, -1, enabled = -1)
        for (id in keys) assertEquals(VehicleSettingValue.Unknown, clean.state.value.valueState(id).vehicleValue)
    }

    @Test fun legacyZeroAndOneAreNotGuessedAsTwoMinutesForId15() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, 0, 0); observe(c, 1, 1, 2)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:15:time").vehicleValue)
        assertEquals(1, ((c.state.value.valueState("cloud:59:time").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
        assertEquals("30 秒", TimerSettings.shutdownOptions[0])
    }

    @Test fun bothLightPagesKeepTheirOwnLabelsButSendTheSameRawIndex() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        assertEquals(listOf("15 秒", "30 秒", "45 秒", "60 秒"), (item(c, "cloud:21:time").kind as CarSettingKind.Choice).options)
        assertEquals(listOf("5 秒", "10 秒", "15 秒", "30 秒"), (item(c, "cloud:81:time").kind as CarSettingKind.Choice).options)
        for (i in 0..3) for (id in listOf("21", "81")) {
            val k = (item(c, "cloud:$id:time").kind as CarSettingKind.Choice).copy(selectedIndex = i, valueKnown = true)
            assertEquals(i, VehicleSettingCommands.forConfigId(id, k)!!.parameter)
        }
    }

    @Test fun invalidChoicesAndSlidersCannotBypassControllerOrMapper() = runBlocking {
        val w = FakeWriter(); val c = controller(w); assertTrue(c.refresh())
        for (id in keys.filter { it.endsWith(":time") }) {
            val original = item(c, id)
            val invalid = when (val k = original.kind) {
                is CarSettingKind.Choice -> listOf(k, k.copy(selectedIndex = -1, valueKnown = true),
                    k.copy(selectedIndex = 99, valueKnown = true),
                    k.copy(selectedIndex = 0, options = listOf("invented"), valueKnown = true))
                is CarSettingKind.Slider -> listOf(k) + listOf(-1f, 0f, 1f, 2.5f, 21f, Float.NaN).map { k.copy(value = it, valueKnown = true) }
                else -> error("kind")
            }
            for (k in invalid) {
                assertFalse(c.editDraft(id) { k }); assertFalse(c.update(id) { k })
                assertNull(VehicleSettingCommands.forConfigId(original.configId, k))
                assertNull(VehicleSettingCommands.forItem(original.copy(kind = k)))
            }
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun mappedWriterSendsOnlyTheEditedDimensionAndRejectsMismatchedSplitIds() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for (id in keys) {
            val i = item(c, id); val k = when (val kind = i.kind) {
                is CarSettingKind.Toggle -> kind.copy(checked = true, valueKnown = true)
                is CarSettingKind.Choice -> kind.copy(selectedIndex = 0, valueKnown = true)
                is CarSettingKind.Slider -> kind.copy(value = 3f, valueKnown = true)
                else -> error("kind")
            }
            assertTrue(w.write(i.copy(kind = k)))
            assertFalse(w.write(i.copy(id = "cloud:002:level", kind = k)))
            assertNull(CloudCarSettingUpdateBuilder.build(i.configId, k, "VIN-A"))
            assertTrue(sent.last().parameters.isEmpty())
        }
        assertEquals(8, sent.size)
    }

    @Test fun directCommandRejectsOutOfDomainBeforeEncoding() {
        for ((id, values) in mapOf(0x7D to listOf(-1, 21, 255), 0x85 to listOf(-1, 4, 255))) {
            for (v in values) {
                try { VehicleSettingCommand("direct", id, v); fail("accepted $v") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun concurrentAliasAndRefreshCannotInvalidateTheInFlightWrite() = runBlocking {
        for ((first, alias) in listOf("15" to "59", "21" to "81")) {
            val receipt = CompletableDeferred<Boolean>(); val w = FakeWriter { receipt.await() }
            val c = controller(w); assertTrue(c.refresh()); observe(c, 3, 1)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(c.update("cloud:$first:time") { it }) }
            assertFalse(c.refresh()); assertFalse(c.update("cloud:$alias:time") { it })
            assertFalse(c.editDraft("cloud:$alias:time") { it }); assertEquals(1, w.sent.size)
            receipt.complete(true); job.join()
        }
    }

    @Test fun vinChangeAndOldObservationsCannotCrossStateBoundaries() = runBlocking {
        var vin = "VIN-A"; val c = controller(vin = { vin }); assertTrue(c.refresh())
        observe(c, 3, 1, 10); val before = c.state.value.valueStates
        observe(c, 4, 2, 9); observe(c, 4, 2, 11, vin = "VIN-B")
        assertEquals(before, c.state.value.valueStates)
        assertTrue(c.editDraft("cloud:15:time") { (it as CarSettingKind.Slider).copy(value = 4f) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        assertFalse(c.submitDraft("cloud:15:time")); assertNull(c.state.value.valueState("cloud:15:time").draftValue)
        for (id in keys) assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
    }

    @Test fun cachedAndAcceptedValuesDoNotConfirmAVehicleWrite() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, 3, 1, 1, origin = VehicleStatusOrigin.CACHED_TTP)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:59:time").vehicleValue)
        assertTrue(c.update("cloud:59:time") { (it as CarSettingKind.Choice).copy(selectedIndex = 4, valueKnown = true) })
        assertEquals(SettingWriteState.LOCAL_UPDATED, c.state.value.valueState("cloud:59:time").writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:59:time").confirmationState)
        observe(c, 4, 1, 2)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState("cloud:59:time").confirmationState)
    }

    @Test fun failedOrCancelledWritePreservesTheObservationAndRefreshPreservesDraft() = runBlocking {
        for (cancel in listOf(false, true)) {
            val c = controller(FakeWriter { if (cancel) throw CancellationException("cancel") else false })
            assertTrue(c.refresh()); observe(c, 3, 1)
            assertTrue(c.editDraft("cloud:59:time") { (it as CarSettingKind.Choice).copy(selectedIndex = 4) })
            assertTrue(c.refresh()); assertNotNull(c.state.value.valueState("cloud:59:time").draftValue)
            try { assertFalse(c.submitDraft("cloud:59:time")); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:59:time")
            assertNull(s.submittedValue)
            assertEquals(3, ((s.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
            assertNotEquals(SettingConfirmationState.READBACK_MATCHED, s.confirmationState)
        }
    }
}
