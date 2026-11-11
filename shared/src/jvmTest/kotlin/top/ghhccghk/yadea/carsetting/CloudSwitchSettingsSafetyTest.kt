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

class CloudSwitchSettingsSafetyTest {
    private val ids = listOf("13", "16", "40")
    private fun cloud(values: Map<String, String> = emptyMap()) =
        CloudCarSettings(ids.map { CloudBikeConfig(it) }, values)
    private class Gateway : CarSettingsGateway {
        var data = CloudCarSettings(listOf(CloudBikeConfig("13"), CloudBikeConfig("16"), CloudBikeConfig("40")),
            mapOf("alarmSoundEnable" to "1", "dumpAlarm" to "0", "colorEggSoundEffect" to "0"))
        var loadAction: (suspend (String) -> CloudCarSettings)? = null
        var updateAction: suspend () -> Boolean = { true }
        var status: Boolean? = true
        val sent = mutableListOf<Pair<String, CloudCarSettingUpdate>>()
        override suspend fun load(vin: String) = loadAction?.invoke(vin) ?: data
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            sent += vin to update
            return updateAction()
        }
        override suspend fun checkStatus(configId: String): Boolean? = status
    }
    private class Writer(var action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    private fun controller(g: CarSettingsGateway = Gateway(), w: CarSettingWriter = Writer(),
                           vin: () -> String = { "VIN-A" }) = CarSettingsController(
        writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 1, statusPollIntervalMs = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == "cloud:$id" }

    @Test fun onlyBoundCloudFieldsCreateConfiguredValuesAndNeverVehicleObservations() = runBlocking {
        for (on in listOf(false, true)) {
            val g = Gateway().apply { data = cloud(mapOf("dumpAlarm" to if (on) "1" else "0",
                "colorEggSoundEffect" to if (on) "1" else "0", "alarmSoundEnable" to "1")) }
            val c = controller(g); assertTrue(c.refresh())
            for (id in ids) {
                val s = c.state.value.valueState("cloud:$id")
                assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertNull(s.cachedValue)
                assertNull(s.draftValue); assertNull(s.submittedValue)
                if (id == "13") { assertNull(s.configuredValue); assertNull(s.displayKind()) }
                else assertEquals(CarSettingKind.Toggle(on), s.configuredValue)
            }
        }
    }

    @Test fun missingMalformedAndOutOfDomainCloudValuesStayUnknownWithoutDefaults() = runBlocking {
        for (value in listOf(null, "", "2", "-1", "0.5", "bad", "null")) {
            val g = Gateway().apply { data = cloud(value?.let { mapOf("dumpAlarm" to it, "colorEggSoundEffect" to it) }.orEmpty()) }
            val w = Writer(); val c = controller(g, w); assertTrue(c.refresh())
            for (id in ids) {
                val i = item(c, id); assertTrue(i.writable)
                assertNull(c.state.value.valueState(i.id).displayKind())
                assertFalse(c.editDraft(i.id) { it }); assertFalse(c.update(i.id) { it })
            }
            assertTrue(w.sent.isEmpty())
        }
    }

    @Test fun vehicleAlarmHasOnlyEstablishedBleCommandsAndNoGuessedCloudWrite() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); val sent = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        for (on in listOf(false, true)) {
            val target = CarSettingKind.Toggle(on)
            assertTrue(mapped.write(item(c, "13").copy(kind = target)))
            assertEquals(if (on) 0x62 else 0x63, sent.last().commandId); assertEquals(-1, sent.last().parameter)
            assertNotNull(VehicleSettingFrameEncoder.encode(sent.last()))
            assertNull(CloudCarSettingUpdateBuilder.build("13", target, "VIN-A"))
        }
        assertNull(VehicleSettingCommands.forConfigId("13", CarSettingKind.Toggle(false, false)))
    }

    @Test fun independentCloudRequestsPreserveOriginalClearSoundEffectSeven() {
        for (on in listOf(false, true)) {
            val dump = requireNotNull(CloudCarSettingUpdateBuilder.build("16", CarSettingKind.Toggle(on), "VIN-A"))
            assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "dumpAlarm" to if (on) "1" else "0"), dump.fields)
            assertEquals(CloudCarSettingUpdatePath.BIKE_SETTING, dump.path)
        }
        val on = requireNotNull(CloudCarSettingUpdateBuilder.build("40", CarSettingKind.Toggle(true), "VIN-A"))
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "colorEggSoundEffect" to "1"), on.fields)
        val off = requireNotNull(CloudCarSettingUpdateBuilder.build("40", CarSettingKind.Toggle(false), "VIN-A"))
        assertEquals(CloudCarSettingUpdatePath.PARAM_SETTING, off.path)
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "oper" to "SET_PARAM", "id" to "CLEAR_SOUND_EFFECT",
            "value" to 7, "valueJson" to "[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":7}]"), off.fields)
        for (id in CloudSwitchSettings.ids) assertNull(VehicleSettingCommands.forConfigId(id, CarSettingKind.Toggle(true)))
    }

    @Test fun unknownAndForgedTargetsFailAtControllerBuilderAndWriterBoundaries() = runBlocking {
        val g = Gateway(); val w = Writer(); val c = controller(g, w); assertTrue(c.refresh())
        val cloudWriter = CloudCarSettingWriter(g, { "VIN-A" }, w, { true })
        for (id in ids) {
            val i = item(c, id); val unknown = CarSettingKind.Toggle(true, false)
            assertFalse(c.editDraft(i.id) { unknown }); assertFalse(c.update(i.id) { unknown })
            assertFalse(cloudWriter.write(i.copy(kind = unknown)))
            assertNull(CloudCarSettingUpdateBuilder.build(id, unknown, "VIN-A"))
            assertFalse(cloudWriter.write(i.copy(id = "cloud:002:level", kind = CarSettingKind.Toggle(true))))
        }
        for (id in CloudSwitchSettings.ids) assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Choice(0, listOf("a")), "VIN-A"))
        assertTrue(w.sent.isEmpty()); assertTrue(g.sent.isEmpty())
    }

    @Test fun actualWriterUsesBleOnlyForAlarmAndCloudForDumpAndEgg() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); val commands = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { commands += command; return true }
        })
        val w = CloudCarSettingWriter(g, { "VIN-A" }, mapped, { true })
        for (id in ids) assertTrue(w.write(item(c, id).copy(kind = CarSettingKind.Toggle(true))))
        assertEquals(listOf(0x62), commands.map { it.commandId })
        assertEquals(listOf("16", "40"), g.sent.map { it.second.configId })
        assertFalse(CloudCarSettingWriter(g, { "VIN-A" }).write(item(c, "13").copy(kind = CarSettingKind.Toggle(true))))
    }

    @Test fun serverAcceptanceResultAndRefreshedConfigurationNeverBecomeVehicleConfirmation() = runBlocking {
        val g = Gateway(); val w = CloudCarSettingWriter(g, { "VIN-A" }); val c = controller(g, w)
        assertTrue(c.refresh())
        for (id in CloudSwitchSettings.ids) {
            assertTrue(c.update("cloud:$id") { CarSettingKind.Toggle(true) })
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, s.writeState)
            assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, s.confirmationState)
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
            assertEquals(CarSettingKind.Toggle(false), s.configuredValue)
        }
        g.data = cloud(mapOf("dumpAlarm" to "1", "colorEggSoundEffect" to "1")); assertTrue(c.refresh())
        for (id in CloudSwitchSettings.ids) {
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(CarSettingKind.Toggle(true), s.configuredValue)
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }

    @Test fun uncorrelatedTtpCannotConfirmTheseCloudOrMissingParserValues() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        for (id in ids) assertTrue(c.update("cloud:$id") { CarSettingKind.Toggle(true) })
        val ttp = TtpInfoParser.parse(ByteArray(256))
        for (origin in listOf(VehicleStatusOrigin.CACHED_TTP, VehicleStatusOrigin.LIVE_TTP)) {
            c.updateVehicleObservation(VehicleStatusObservation("VIN-A", ttp, origin, if (origin == VehicleStatusOrigin.LIVE_TTP) 2 else 1))
            for (id in ids) {
                val s = c.state.value.valueState("cloud:$id")
                assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertNull(s.cachedValue)
                assertEquals(SettingConfirmationState.WAITING_READBACK, s.confirmationState)
            }
        }
    }

    @Test fun failedWriteAndCancellationNeverInventSuccessfulState() = runBlocking {
        for (id in ids) for (cancel in listOf(false, true)) {
            val g = Gateway().apply { updateAction = { if (cancel) throw CancellationException("cancel") else false } }
            val ble = Writer { if (cancel) throw CancellationException("cancel") else false }
            val w = CloudCarSettingWriter(g, { "VIN-A" }, ble, { true }); val c = controller(g, w); assertTrue(c.refresh())
            val old = c.state.value.valueState("cloud:$id").configuredValue
            assertTrue(c.editDraft("cloud:$id") { CarSettingKind.Toggle(true) })
            try { assertFalse(c.submitDraft("cloud:$id")); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:$id")
            assertEquals(old, s.configuredValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
            assertNull(s.submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, s.writeState)
        }
    }

    @Test fun draftSurvivesRefreshAndInvalidResultKeepsConfigurationAndVehicleSeparate() = runBlocking {
        val g = Gateway().apply { status = false }; val c = controller(g, CloudCarSettingWriter(g, { "VIN-A" }))
        assertTrue(c.refresh()); assertTrue(c.editDraft("cloud:16") { CarSettingKind.Toggle(true) })
        assertTrue(c.refresh()); assertEquals(CarSettingKind.Toggle(true), c.state.value.valueState("cloud:16").draftValue)
        assertFalse(c.submitDraft("cloud:16"))
        val s = c.state.value.valueState("cloud:16")
        assertEquals(SettingConfirmationState.FAILED, s.confirmationState)
        assertEquals(CarSettingKind.Toggle(false), s.configuredValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, s.writeState)
    }

    @Test fun activeWriteBlocksDuplicateAndRefreshAndOldVinReceiptCannotContaminateNewVehicle() = runBlocking {
        for (id in ids) {
            var vin = "VIN-A"; val receipt = CompletableDeferred<Boolean>(); val w = Writer { receipt.await() }
            val c = controller(w = w, vin = { vin }); assertTrue(c.refresh())
            assertTrue(c.editDraft("cloud:$id") { CarSettingKind.Toggle(true) })
            val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.submitDraft("cloud:$id")) }
            assertFalse(c.refresh()); assertFalse(c.update("cloud:$id") { CarSettingKind.Toggle(false) })
            vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); receipt.complete(true); job.join()
            val s = c.state.value.valueState("cloud:$id")
            assertNull(s.draftValue); assertNull(s.submittedValue); assertFalse(c.submitDraft("cloud:$id"))
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue); assertEquals(1, w.sent.size)
        }
    }

    @Test fun lateRefreshFromOlderGenerationOrVinCannotOverwriteNewCloudConfiguration() = runBlocking {
        val stale = CompletableDeferred<CloudCarSettings>(); val g = Gateway(); var calls = 0; var vin = "VIN-A"
        g.loadAction = { if (++calls == 1) stale.await() else cloud(mapOf("dumpAlarm" to "1", "colorEggSoundEffect" to "1")) }
        val c = controller(g, vin = { vin })
        val first = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.refresh()) }
        assertFalse(c.refresh()); assertEquals(1, calls)
        vin = "VIN-B"; c.onVinChanged(vin)
        vin = "VIN-A"; c.onVinChanged(vin)
        assertTrue(c.refresh()); stale.complete(cloud(mapOf("dumpAlarm" to "0", "colorEggSoundEffect" to "0"))); first.join()
        assertEquals(CarSettingKind.Toggle(true), c.state.value.valueState("cloud:16").configuredValue)
        assertTrue(c.editDraft("cloud:16") { CarSettingKind.Toggle(false) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); assertFalse(c.submitDraft("cloud:16"))
        assertEquals("VIN-B", c.state.value.vin)
    }
}
