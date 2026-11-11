package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** Every transport, writer and status source in this suite is in memory. */
class TrackerSettingsSafetyTest {
    private class Gateway : CarSettingsGateway {
        var cloud = CloudCarSettings(listOf(CloudBikeConfig("75"), CloudBikeConfig("76")),
            mapOf("tamperProofIotEnable" to "1", "positioningFunc" to "0"))
        var authorize: suspend (String) -> LocationSwitchAuthorization? = { LocationSwitchAuthorization(it) }
        var receipt: suspend () -> LocationSwitchWriteReceipt = { LocationSwitchWriteReceipt(true, "write-A") }
        var status: Boolean? = true
        var check: (suspend () -> Boolean?)? = null
        val checks = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, CloudCarSettingUpdate>>()
        override suspend fun load(vin: String) = cloud
        override suspend fun loadLocationSwitchAuthorization(vin: String) = authorize(vin)
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            writes += vin to update; return true
        }
        override suspend fun updateLocationSwitch(vin: String, update: CloudCarSettingUpdate,
                                                 authorization: LocationSwitchAuthorization): LocationSwitchWriteReceipt {
            writes += vin to update; return receipt()
        }
        override suspend fun checkStatus(configId: String): Boolean? { checks += configId; return check?.invoke() ?: status }
    }
    private fun controller(g: Gateway = Gateway(), vin: () -> String = { "VIN-A" }, writer: CarSettingWriter? = null) =
        CarSettingsController(writer = writer ?: CloudCarSettingWriter(g, vin), gateway = g, vinProvider = vin,
            debounceMs = 0, statusPollAttempts = 2, statusPollIntervalMs = 0)
    private fun item(c: CarSettingsController, id: String) = c.state.value.items.single { it.id == "cloud:$id" }
    private fun state(c: CarSettingsController, id: String) = c.state.value.valueState("cloud:$id")
    private fun observe(c: CarSettingsController, raw: Int, sequence: Long = 1,
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP, vin: String = "VIN-A") {
        val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
        c.updateVehicleObservation(VehicleStatusObservation(vin, empty.copy(extended = empty.extended.copy(iotRemoveAlarm = raw)), origin, sequence))
    }

    @Test fun onlyTheBoundCloudFieldCreatesConfigurationAndUnknownNeverCreatesASelection() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertNull(state(c, "75").configuredValue); assertNull(state(c, "75").displayKind())
        assertNull(settingSwitchTarget(state(c, "75"), true))
        assertEquals(CarSettingKind.Toggle(false), state(c, "76").configuredValue)
        for (id in TrackerSettings.ids) assertEquals(VehicleSettingValue.Unknown, state(c, id).vehicleValue)
        for (raw in listOf("", "2", "-1", "0.5", "bad")) {
            g.cloud = g.cloud.copy(values = mapOf("positioningFunc" to raw)); assertTrue(c.refresh())
            assertNull(state(c, "76").configuredValue); assertNull(settingSwitchTarget(state(c, "76"), true))
        }
    }

    @Test fun tamperTtpAndCacheStaySeparateAndInvalidFieldsRevokeLiveAuthority() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        observe(c, 1, origin = VehicleStatusOrigin.CACHED_TTP)
        assertEquals(VehicleSettingValue.Unknown, state(c, "75").vehicleValue)
        assertFalse(state(c, "75").cachedValue!!.live)
        assertEquals(SettingValueSource.CACHED_TTP, state(c, "75").cachedValue!!.source)
        observe(c, 0, 2)
        assertEquals(CarSettingKind.Toggle(false), (state(c, "75").vehicleValue as VehicleSettingValue.Known).kind)
        observe(c, 1, 3, vin = "VIN-B"); observe(c, 1, 1)
        assertEquals(CarSettingKind.Toggle(false), (state(c, "75").vehicleValue as VehicleSettingValue.Known).kind)
        for ((n, raw) in listOf(-1, 2, 255).withIndex()) {
            observe(c, raw, 4L + n); assertFalse((state(c, "75").vehicleValue as VehicleSettingValue.Known).live)
        }
        assertEquals(VehicleSettingValue.Unknown, state(c, "76").vehicleValue)
    }

    @Test fun unprovenTamperCloudCloseUnknownAndForgedItemsAreRejectedAtEveryBoundary() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); observe(c, 1)
        val w = CloudCarSettingWriter(g, { "VIN-A" })
        assertFalse(c.editDraft("cloud:75") { CarSettingKind.Toggle(false) })
        assertFalse(c.update("cloud:75") { CarSettingKind.Toggle(false) })
        assertNull(CloudCarSettingUpdateBuilder.build("75", CarSettingKind.Toggle(false), "VIN-A"))
        for (id in TrackerSettings.ids) {
            assertFalse(w.write(item(c, id).copy(kind = CarSettingKind.Toggle(true, false))))
            assertFalse(w.write(item(c, id).copy(id = "cloud:wrong", kind = CarSettingKind.Toggle(true))))
            assertNull(CloudCarSettingUpdateBuilder.build(id, CarSettingKind.Toggle(true, false), "VIN-A"))
        }
        assertFalse(w.write(item(c, "75").copy(kind = CarSettingKind.Toggle(false))))
        assertTrue(g.writes.isEmpty())
    }

    @Test fun tamperEnableOnlyWritesTheProvenIntegerFieldAndNeedsFreshReadback() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); observe(c, 0)
        assertTrue(c.update("cloud:75") { CarSettingKind.Toggle(true) })
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "tamperProofIotEnable" to 1), g.writes.single().second.fields)
        assertEquals(CarSettingKind.Toggle(false), (state(c, "75").vehicleValue as VehicleSettingValue.Known).kind)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "75").writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c, "75").confirmationState)
        assertTrue(g.checks.isEmpty())
        observe(c, 1, 2); assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c, "75").confirmationState)
    }

    @Test fun locationCloseRequiresConfirmationIncludingDirectControllerAndWriterCalls() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertFalse(c.update("cloud:76") { CarSettingKind.Toggle(false) })
        assertFalse(c.updateLocationSwitch("cloud:76", false))
        val i = item(c, "76").copy(kind = CarSettingKind.Toggle(false), locationSwitchAuthorization = LocationSwitchAuthorization("VIN-A"))
        assertFalse(CloudCarSettingWriter(g, { "VIN-A" }).write(i))
        assertFalse(c.updateLocationSwitch("cloud:75", false, true)); assertTrue(g.writes.isEmpty())
        assertTrue(c.updateLocationSwitch("cloud:76", false, true))
        assertEquals(0, g.writes.single().second.fields["value"])
        assertNull(item(c, "76").locationSwitchAuthorization)
    }

    @Test fun offlineUnknownOrWrongVinOnlineChecksNeverSendOrUseBleAsOnlineProof() = runBlocking {
        for (proof in listOf(null, LocationSwitchAuthorization("VIN-B"))) {
            val g = Gateway().apply { authorize = { proof } }; val c = controller(g); assertTrue(c.refresh())
            assertFalse(c.updateLocationSwitch("cloud:76", true)); assertTrue(g.writes.isEmpty())
            assertEquals(SettingWriteState.FAILED, state(c, "76").writeState)
        }
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); var bleWrites = 0
        val ble = object : CarSettingWriter { override suspend fun write(item: CarSettingItem): Boolean { bleWrites++; return true } }
        val w = CloudCarSettingWriter(g, { "VIN-A" }, ble, { true })
        assertFalse(w.write(item(c, "76").copy(kind = CarSettingKind.Toggle(true))))
        assertTrue(w.write(item(c, "76").copy(kind = CarSettingKind.Toggle(true), locationSwitchAuthorization = LocationSwitchAuthorization("VIN-A"))))
        assertEquals(0, bleWrites)
    }

    @Test fun locationUsesThisWriteResultIdNeverConfIdAndNeverCreatesAVehicleValue() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertTrue(c.updateLocationSwitch("cloud:76", true))
        assertEquals(listOf("write-A"), g.checks)
        assertEquals("POSITIONING_FUNC", g.writes.single().second.fields["id"])
        assertEquals("[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":1}]", g.writes.single().second.fields["valueJson"])
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "76").writeState)
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, state(c, "76").confirmationState)
        assertEquals(VehicleSettingValue.Unknown, state(c, "76").vehicleValue)
        assertEquals(CarSettingKind.Toggle(false), state(c, "76").configuredValue)
    }

    @Test fun missingReceiptIdCannotFallBackTo76OrClaimResultConfirmation() = runBlocking {
        val g = Gateway().apply { receipt = { LocationSwitchWriteReceipt(true) } }; val c = controller(g); assertTrue(c.refresh())
        assertFalse(c.updateLocationSwitch("cloud:76", true)); assertTrue(g.checks.isEmpty())
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "76").writeState)
        assertEquals(SettingConfirmationState.UNKNOWN, state(c, "76").confirmationState)
        assertNull(state(c, "76").submittedValue); assertEquals(VehicleSettingValue.Unknown, state(c, "76").vehicleValue)
    }

    @Test fun failedPendingAndCancelledLocationOperationsRetainHonestState() = runBlocking {
        for (cancel in listOf(false, true)) {
            val g = Gateway().apply { receipt = { if (cancel) throw CancellationException("fake") else LocationSwitchWriteReceipt(false) } }
            val c = controller(g); assertTrue(c.refresh())
            try { assertFalse(c.updateLocationSwitch("cloud:76", true)); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c, "76").writeState)
            assertNull(state(c, "76").submittedValue); assertEquals(CarSettingKind.Toggle(false), state(c, "76").configuredValue)
        }
        for (status in listOf(false, null)) {
            val g = Gateway().apply { this.status = status }; val c = controller(g); assertTrue(c.refresh())
            assertFalse(c.updateLocationSwitch("cloud:76", true))
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "76").writeState)
            assertEquals(if (status == false) SettingConfirmationState.FAILED else SettingConfirmationState.TIMED_OUT, state(c, "76").confirmationState)
        }
    }

    @Test fun draftSurvivesRefreshAndCancelledConfirmationDoesNotSend() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertTrue(c.editDraft("cloud:76") { CarSettingKind.Toggle(true) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(true), state(c, "76").draftValue)
        c.cancelDraft("cloud:76"); assertFalse(c.submitDraft("cloud:76")); assertTrue(g.writes.isEmpty())
    }

    @Test fun lateOnlineProofCannotCrossVinAndGateDeduplicatesRefreshAndWrites() = runBlocking {
        var vin = "VIN-A"; val deferred = CompletableDeferred<LocationSwitchAuthorization?>()
        val g = Gateway().apply { authorize = { deferred.await() } }; val c = controller(g, { vin }); assertTrue(c.refresh())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.updateLocationSwitch("cloud:76", true)) }
        assertFalse(c.refresh()); assertFalse(c.updateLocationSwitch("cloud:76", true))
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        deferred.complete(LocationSwitchAuthorization("VIN-A")); job.join(); assertTrue(g.writes.isEmpty())
        assertNull(state(c, "76").draftValue); assertNull(state(c, "76").submittedValue)
    }

    @Test fun lateWriteReceiptCannotConfirmNewVin() = runBlocking {
        var vin = "VIN-A"; val deferred = CompletableDeferred<LocationSwitchWriteReceipt>()
        val g = Gateway().apply { receipt = { deferred.await() } }; val c = controller(g, { vin }); assertTrue(c.refresh())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.updateLocationSwitch("cloud:76", true)) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        deferred.complete(LocationSwitchWriteReceipt(true, "old-A")); job.join()
        assertTrue(g.checks.isEmpty()); assertEquals(SettingConfirmationState.UNKNOWN, state(c, "76").confirmationState)
    }

    @Test fun onlineCancellationPropagatesWithoutSendingOrInventingReceipt() = runBlocking {
        val g = Gateway().apply { authorize = { throw CancellationException("fake online cancellation") } }
        val c = controller(g); assertTrue(c.refresh())
        try { c.updateLocationSwitch("cloud:76", true); fail("cancellation swallowed") }
        catch (_: CancellationException) { }
        assertTrue(g.writes.isEmpty()); assertEquals(SettingWriteState.CANCELLED, state(c, "76").writeState)
        assertTrue(c.state.value.pendingIds.isEmpty())
    }

    @Test fun cancelledOrOldVinPollCannotTurnAcceptanceIntoVehicleConfirmation() = runBlocking {
        for (changeVin in listOf(false, true)) {
            var vin = "VIN-A"; val deferred = CompletableDeferred<Boolean?>()
            val g = Gateway().apply { check = { deferred.await() } }; val c = controller(g, { vin }); assertTrue(c.refresh())
            val job = launch(start = CoroutineStart.UNDISPATCHED) { c.updateLocationSwitch("cloud:76", true) }
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "76").writeState)
            if (changeVin) {
                vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); deferred.complete(true); job.join()
            } else job.cancelAndJoin()
            assertEquals(VehicleSettingValue.Unknown, state(c, "76").vehicleValue)
            assertEquals(SettingConfirmationState.UNKNOWN, state(c, "76").confirmationState)
            if (!changeVin) assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c, "76").writeState)
        }
    }

    private class Transport(var body: String) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse { requests += request; return HttpResponse(200, body) }
    }
    private fun gateway(t: Transport) = YadeaCarSettingsGateway(t, { "fake-token" }, timestampProvider = { 1_700_000_000_000L }, baseUrl = "https://example.invalid/")

    @Test fun realGatewayRejectsTamperCloseAndDoesNotAcceptMissingSuccess() = runBlocking {
        val good = requireNotNull(CloudCarSettingUpdateBuilder.build("75", CarSettingKind.Toggle(true), "VIN-A"))
        for (body in listOf("{}", """{"data":true}""", """{"success":false}""", """{"success":true,"data":false}""")) {
            assertFalse(gateway(Transport(body)).update("VIN-A", good))
        }
        val t = Transport("""{"success":true,"data":true}"""); val g = gateway(t)
        assertFalse(g.update("VIN-A", good.copy(fields = good.fields + ("tamperProofIotEnable" to 0))))
        assertTrue(t.requests.isEmpty()); assertTrue(g.update("VIN-A", good))
    }

    @Test fun realGatewayOnlyAuthorizesExplicitCloudOnlineAndKeepsItOutOfTtp() = runBlocking {
        for (raw in listOf("true", "false", "null", "1", "2", "{}")) {
            val t = Transport("""{"success":true,"data":{"onlineState":$raw}}""")
            assertEquals(raw == "true", gateway(t).loadLocationSwitchAuthorization("VIN-A") != null)
            assertEquals("GET", t.requests.single().method)
            assertTrue(t.requests.single().url.contains("bikeRealStatus/getVehRealStatus?"))
        }
        for (body in listOf("{}", """{"success":false,"data":{"onlineState":true}}""", """{"success":true,"data":{"nested":{"onlineState":true}}}""")) {
            assertNull(gateway(Transport(body)).loadLocationSwitchAuthorization("VIN-A"))
        }
    }

    @Test fun realGatewayRetainsScalarTransactionIdsAndRejectsLegacyOrForgedRequests() = runBlocking {
        val update = requireNotNull(CloudCarSettingUpdateBuilder.build("76", CarSettingKind.Toggle(true), "VIN-A"))
        for (data in listOf("\"transaction-A\"", "123", "null", "true", "{}")) {
            val t = Transport("""{"success":true,"data":$data}"""); val g = gateway(t)
            val result = g.updateLocationSwitch("VIN-A", update, LocationSwitchAuthorization("VIN-A"))
            assertTrue(result.accepted)
            assertEquals(when (data) { "\"transaction-A\"" -> "transaction-A"; "123" -> "123"; else -> null }, result.resultId)
            assertEquals("POST", t.requests.single().method)
            assertTrue(t.requests.single().url.endsWith(YadeaCarSettingsGateway.PARAM_UPDATE_PATH))
        }
        val t = Transport("""{"success":false,"data":"wrong"}"""); val g = gateway(t)
        assertFalse(g.updateLocationSwitch("VIN-A", update, LocationSwitchAuthorization("VIN-A")).accepted)
        val count = t.requests.size
        assertFalse(g.update("VIN-A", update))
        assertFalse(g.updateLocationSwitch("VIN-A", update, LocationSwitchAuthorization("VIN-B")).accepted)
        assertFalse(g.updateLocationSwitch("VIN-A", update.copy(fields = update.fields + ("value" to 2)), LocationSwitchAuthorization("VIN-A")).accepted)
        val off = requireNotNull(CloudCarSettingUpdateBuilder.build("76", CarSettingKind.Toggle(false), "VIN-A"))
        assertFalse(g.updateLocationSwitch("VIN-A", off, LocationSwitchAuthorization("VIN-A")).accepted)
        assertEquals(count, t.requests.size)
    }
}
