package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

class GeofenceSettingsSafetyTest {
    private val fields = mapOf("distanceOn" to "1", "distance" to "200", "centerCircleLon" to "120.25", "centerCircleLat" to "30.5")
    private class Gateway(var values: Map<String, String>) : CarSettingsGateway {
        val updates = mutableListOf<CloudCarSettingUpdate>()
        var action: suspend () -> Boolean = { true }
        override suspend fun load(vin: String) = CloudCarSettings(configs = listOf(CloudBikeConfig("10")), values = values)
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean { updates += update; return action() }
        override suspend fun checkStatus(configId: String) = true
    }
    private fun controller(g: Gateway, vin: () -> String = { "VIN-A" }, fallback: CarSettingWriter? = null) =
        CarSettingsController(writer = CloudCarSettingWriter(g, vin, fallback, { true }), gateway = g,
            vinProvider = vin, debounceMs = 0, statusPollAttempts = 1, statusPollIntervalMs = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == id }
    private fun radius(c: CarSettingsController, value: Float) =
        (item(c, "cloud:10:distance").kind as CarSettingKind.Slider).copy(value = value, valueKnown = true)
    private fun off() = CarSettingKind.Toggle(false, valueKnown = true)

    @Test fun realDecoderPreservesTheFourFieldsAsCloudConfigurationOnly() = runBlocking {
        val decoder = YadeaCarSettingsGateway(object : HttpTransport {
            override suspend fun execute(request: HttpRequest) = HttpResponse(200,
                """{"success":true,"data":{"distanceOn":1,"distance":2000,"centerCircleLon":120.25,"centerCircleLat":30.5,"bikeConfigVos":[{"confId":"10"}]}}""")
        }, { "fake" }, timestampProvider = { 1L }, baseUrl = "https://example.invalid/")
        val loaded = requireNotNull(decoder.load("VIN-A"))
        val i = CloudCarSettingMapper.toItems(loaded, "VIN-A").single { it.id == "cloud:10:distance" }
        assertEquals(2000f, (i.kind as CarSettingKind.Slider).value)
        assertEquals(GeofenceConfiguration("VIN-A", true, 2000, 120.25, 30.5), i.geofenceConfiguration)
        val c = controller(Gateway(fields)); assertTrue(c.refresh())
        for (id in listOf("cloud:10", "cloud:10:distance")) {
            val s = c.state.value.valueState(id)
            assertNotNull(s.configuredValue); assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }

    @Test fun switchAndRadiusRequestsCarryOnlyTheirOwnEditAndAllKnownCompanions() = runBlocking {
        for (editSwitch in listOf(true, false)) {
            val g = Gateway(fields); val c = controller(g); assertTrue(c.refresh())
            assertTrue(if (editSwitch) c.update("cloud:10") { off() } else c.update("cloud:10:distance") { radius(c, 1201f) })
            val u = g.updates.single()
            assertEquals(CloudCarSettingUpdatePath.BIKE_SETTING, u.path)
            assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "distanceOn" to if (editSwitch) "0" else "1",
                "distance" to if (editSwitch) "200" else "1201", "centerCircleLon" to 120.25, "centerCircleLat" to 30.5), u.fields)
            val s = c.state.value.valueState(if (editSwitch) "cloud:10" else "cloud:10:distance")
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, s.writeState)
            assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, s.confirmationState)
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }

    @Test fun everyUnknownCompanionBlocksBothWritesWithoutInventingDefaults() = runBlocking {
        for (missing in fields.keys) {
            val g = Gateway(fields - missing); val c = controller(g); assertTrue(c.refresh())
            assertFalse(c.update("cloud:10") { off() })
            assertFalse(c.update("cloud:10:distance") { radius(c, 400f) })
            assertTrue(g.updates.isEmpty())
            assertFalse(item(c, "cloud:10").geofenceConfiguration!!.complete)
        }
        assertNull(CloudCarSettingUpdateBuilder.build("10", off(), "VIN-A"))
    }

    @Test fun invalidCoordinatesOrRadiusNeverFormACompleteSnapshot() = runBlocking {
        for ((key, invalid) in mapOf(
            "distanceOn" to listOf("2", "invalid"), "distance" to listOf("199", "2001", "200.5", "NaN"),
            "centerCircleLon" to listOf("NaN", "Infinity", "bad"), "centerCircleLat" to listOf("NaN", "-Infinity", ""),
        )) for (bad in invalid) {
            val g = Gateway(fields + (key to bad)); val c = controller(g); assertTrue(c.refresh())
            assertFalse(c.update("cloud:10") { off() }); assertTrue(g.updates.isEmpty())
        }
        val c = controller(Gateway(fields + mapOf("centerCircleLon" to "0", "centerCircleLat" to "0")))
        assertTrue(c.refresh()); assertTrue(item(c, "cloud:10").geofenceConfiguration!!.complete) // explicit zero is not missing
    }

    @Test fun fractionalOutOfRangeAndUnknownTargetsAreRejectedWithoutTruncation() = runBlocking {
        val g = Gateway(fields); val c = controller(g); assertTrue(c.refresh())
        for (v in listOf(0f, 199f, 2001f, 200.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val k = radius(c, v)
            assertFalse(c.editDraft("cloud:10:distance") { k }); assertFalse(c.update("cloud:10:distance") { k })
            assertNull(CloudCarSettingUpdateBuilder.build("10", k, "VIN-A", item(c, "cloud:10").geofenceConfiguration))
        }
        assertFalse(c.update("cloud:10") { off().copy(valueKnown = false) })
        assertTrue(g.updates.isEmpty())
    }

    @Test fun allOriginalIntegerBoundariesAreAcceptedUnscaled() = runBlocking {
        for (v in listOf(200, 201, 1001, 1999, 2000)) {
            val g = Gateway(fields); val c = controller(g); assertTrue(c.refresh())
            assertTrue(c.update("cloud:10:distance") { radius(c, v.toFloat()) })
            assertEquals(v.toString(), g.updates.single().fields["distance"])
        }
    }

    @Test fun directWritersRejectMissingWrongVinOrForgedIdAndNeverUseBle() = runBlocking {
        val g = Gateway(fields); val c = controller(g); assertTrue(c.refresh()); var bleCalls = 0
        val fallback = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean { bleCalls++; return true }
        }
        val w = CloudCarSettingWriter(g, { "VIN-A" }, fallback, { true })
        val i = item(c, "cloud:10").copy(kind = off())
        for (bad in listOf(i.copy(geofenceConfiguration = null), i.copy(id = "cloud:002:enable"),
            i.copy(geofenceConfiguration = i.geofenceConfiguration!!.copy(vin = "VIN-B")))) assertFalse(w.write(bad))
        assertNull(VehicleSettingCommands.forItem(i.copy(id = "cloud:002:enable")))
        assertTrue(w.write(i)); assertEquals(0, bleCalls); assertEquals(1, g.updates.size)
    }

    @Test fun siblingWriteWaitsForFreshConfigurationThatContainsTheEarlierEdit() = runBlocking {
        val g = Gateway(fields); val c = controller(g); assertTrue(c.refresh())
        assertTrue(c.update("cloud:10") { off() })
        assertTrue(c.editDraft("cloud:10:distance") { radius(c, 400f) })
        assertFalse(c.update("cloud:10:distance") { radius(c, 400f) })
        assertNotNull(c.state.value.valueState("cloud:10:distance").draftValue)
        assertTrue(c.state.value.message!!.contains("刷新"))
        assertTrue(c.refresh()); assertFalse(c.update("cloud:10:distance") { radius(c, 400f) })
        g.values = fields + ("distanceOn" to "0"); assertTrue(c.refresh())
        assertTrue(c.update("cloud:10:distance") { radius(c, 400f) })
        assertEquals("0", g.updates.last().fields["distanceOn"])
    }

    @Test fun draftSurvivesRefreshAndIncompleteConfigurationBlocksOnlySubmission() = runBlocking {
        val g = Gateway(fields - "centerCircleLat"); val c = controller(g); assertTrue(c.refresh())
        assertTrue(c.editDraft("cloud:10:distance") { radius(c, 400f) })
        assertFalse(c.submitDraft("cloud:10:distance")); assertNotNull(c.state.value.valueState("cloud:10:distance").draftValue)
        g.values = fields; assertTrue(c.refresh())
        assertEquals(400f, (c.state.value.valueState("cloud:10:distance").draftValue as CarSettingKind.Slider).value)
        assertTrue(c.submitDraft("cloud:10:distance"))
    }

    @Test fun failuresAndCancellationKeepConfigurationAndNeverCreateVehicleConfirmation() = runBlocking {
        for (cancel in listOf(true, false)) {
            val g = Gateway(fields); g.action = { if (cancel) throw CancellationException("cancel") else false }
            val c = controller(g); assertTrue(c.refresh())
            try { assertFalse(c.update("cloud:10") { off() }); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            val s = c.state.value.valueState("cloud:10")
            assertTrue((s.configuredValue as CarSettingKind.Toggle).checked); assertNull(s.submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, s.writeState)
            assertEquals(VehicleSettingValue.Unknown, s.vehicleValue)
        }
    }

    @Test fun inFlightCompositeBlocksRefreshAndVinSwitchRejectsItsLateReceipt() = runBlocking {
        var vin = "VIN-A"; val result = CompletableDeferred<Boolean>(); val g = Gateway(fields); g.action = { result.await() }
        val c = controller(g, { vin }); assertTrue(c.refresh())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:10") { off() }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:10:distance") { radius(c, 400f) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); result.complete(true); job.join()
        assertNull(c.state.value.valueState("cloud:10").submittedValue)
        assertEquals("VIN-B", item(c, "cloud:10").geofenceConfiguration!!.vin)
    }
}
