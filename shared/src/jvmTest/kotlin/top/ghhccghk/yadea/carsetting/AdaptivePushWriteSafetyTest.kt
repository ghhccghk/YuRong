package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

class AdaptivePushWriteSafetyTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
    private val cloud = CloudCarSettings(listOf(CloudBikeConfig("64"), CloudBikeConfig("89")), emptyMap())
    private val gateway = object : CarSettingsGateway { override suspend fun load(vin: String) = cloud }
    private class Writer(val action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(writer: CarSettingWriter = Writer()) = CarSettingsController(
        writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0, statusPollAttempts = 0)
    private fun observe(c: CarSettingsController, seq: Long = 1, origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP,
                        main: Int = 1, adaptive: Int = 1, level: Int = 1) {
        c.updateVehicleObservation(VehicleStatusObservation("VIN-A", unknown.copy(extended = unknown.extended.copy(
            helpCarState = main, customDriveSwitchStatus = adaptive, customDriveLevelStatus = level)), origin, seq))
    }
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }

    @Test fun unknownControlsRemainCapabilityOnlyAndCannotSubmitAnUneditedPlaceholder() = runBlocking {
        val w = Writer(); val c = controller(w); assertTrue(c.refresh())
        for (i in c.state.value.items.filter { it.configId in AdaptivePushSettings.ids }) {
            assertTrue(VehicleSettingCommands.supportsItem(i))
            assertNull(c.state.value.valueState(i.id).displayKind())
            assertNull(VehicleSettingCommands.forItem(i))
            assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun explicitIndependentTargetsReachSinkWithoutAnySiblingFields() = runBlocking {
        val c = controller(); c.refresh()
        val sent = mutableListOf<VehicleSettingCommand>()
        val w = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for (conf in AdaptivePushSettings.ids) {
            for (on in listOf(false, true)) {
                assertTrue(w.write(item(c, "cloud:$conf").copy(kind = CarSettingKind.Toggle(on))))
                assertEquals(if (on) 0xA8 else 0xA9, sent.last().commandId)
                assertTrue(w.write(item(c, "cloud:$conf:adaptive").copy(kind = CarSettingKind.Toggle(on))))
                assertEquals(0xD0, sent.last().commandId)
                assertEquals(if (!on) 0 else if (conf == "64") 1 else 2, sent.last().parameter)
            }
            val i = item(c, "cloud:$conf:level"); val slider = i.kind as CarSettingKind.Slider
            for (v in 0..2) {
                assertTrue(w.write(i.copy(kind = slider.copy(value = v.toFloat(), valueKnown = true))))
                assertEquals(0xD1, sent.last().commandId); assertEquals(v, sent.last().parameter)
            }
        }
        assertTrue(sent.all { it.parameters.isEmpty() && VehicleSettingFrameEncoder.encode(it) != null })
    }

    @Test fun unknownMalformedAndForgedTargetsCannotBypassControllerMapperOrActualWriter() = runBlocking {
        val w = Writer(); val c = controller(w); c.refresh(); observe(c)
        val actual = CloudCarSettingWriter(gateway, { "VIN-A" }, w, { true })
        for (conf in AdaptivePushSettings.ids) {
            val i = item(c, "cloud:$conf:level"); val s = i.kind as CarSettingKind.Slider
            val invalid = listOf(-1f, 3f, 0.5f, Float.NaN, Float.POSITIVE_INFINITY).map { s.copy(value = it) } +
                listOf(s.copy(valueKnown = false), s.copy(rangeKnown = false), s.copy(range = 0f..7f), s.copy(steps = 6))
            for (bad in invalid) {
                assertFalse(c.editDraft(i.id) { bad }); assertFalse(c.update(i.id) { bad })
                assertNull(VehicleSettingCommands.forConfigId(conf, bad)); assertNull(VehicleSettingCommands.forItem(i.copy(kind = bad)))
                assertFalse(actual.write(i.copy(kind = bad)))
            }
            for (suffix in listOf("", ":adaptive")) {
                val toggle = item(c, "cloud:$conf$suffix").copy(kind = CarSettingKind.Toggle(true, false))
                assertNull(VehicleSettingCommands.forItem(toggle)); assertFalse(actual.write(toggle))
            }
            for (forged in listOf(i.copy(id = "cloud:$conf"), i.copy(id = "forged:adaptive"), i.copy(id = "cloud:69:rcw"))) {
                assertNull(VehicleSettingCommands.forItem(forged)); assertFalse(actual.write(forged))
            }
            assertNull(CloudCarSettingUpdateBuilder.build(conf, s, "VIN-A"))
        }
        assertTrue(w.sent.isEmpty())
    }

    @Test fun directCommandCannotClampIllegalParameterIntoARideSetting() {
        for (id in listOf(0xD0, 0xD1)) {
            for (v in listOf(-1, 3, 7, 255)) {
                try { VehicleSettingCommand("direct", id, v); fail("accepted $id/$v") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun invalidNewObservationDemotesOldLiveValuesInsteadOfCreatingFalseOrDefaultLevel() = runBlocking {
        val c = controller(); c.refresh(); observe(c)
        observe(c, seq = 2, main = 2, adaptive = 3, level = 7)
        for (i in c.state.value.items.filter { it.configId in AdaptivePushSettings.ids }) {
            val s = c.state.value.valueState(i.id)
            assertEquals(SettingReadbackState.MISSING_OR_INVALID, s.readbackState)
            assertFalse((s.vehicleValue as VehicleSettingValue.Known).live)
        }
    }

    @Test fun cacheAndOldSequenceNeverCreateLiveValuesOrOverwriteNewerDraft() = runBlocking {
        val c = controller(); c.refresh(); observe(c, origin = VehicleStatusOrigin.CACHED_TTP)
        val id = "cloud:89:level"
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
        assertFalse(c.state.value.valueState(id).cachedValue!!.live)
        observe(c, seq = 4, level = 2)
        assertTrue(c.editDraft(id) { (it as CarSettingKind.Slider).copy(value = 0f) })
        observe(c, seq = 3, level = 1)
        assertEquals(2f, ((c.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(0f, (c.state.value.valueState(id).draftValue as CarSettingKind.Slider).value)
    }

    @Test fun failureDoesNotCreateAnObservationAndCancellationPropagates() = runBlocking {
        val c = controller(Writer { false }); c.refresh(); observe(c)
        val id = "cloud:64:adaptive"
        assertFalse(c.update(id) { CarSettingKind.Toggle(false) })
        assertEquals(SettingWriteState.FAILED, c.state.value.valueState(id).writeState)
        assertTrue(((c.state.value.valueState(id).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Toggle).checked)
        val cancelled = CancellationException("fake cancellation")
        val d = controller(Writer { throw cancelled }); d.refresh(); observe(d)
        try { d.update(id) { CarSettingKind.Toggle(false) }; fail("swallowed cancellation") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertNotEquals(SettingConfirmationState.READBACK_MATCHED, d.state.value.valueState(id).confirmationState)
    }
}
