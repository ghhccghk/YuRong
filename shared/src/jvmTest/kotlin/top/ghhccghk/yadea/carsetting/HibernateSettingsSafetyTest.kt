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

/** Fake-only immediate-sleep flow. Automatic sleep is a separate appointment API. */
class HibernateSettingsSafetyTest {
    private class Gateway : CarSettingsGateway {
        var cloud = CloudCarSettings(listOf(CloudBikeConfig("77")), mapOf("sleepMode" to "0"))
        var authorize: suspend (String) -> HibernateAuthorization? = { HibernateAuthorization(it) }
        var receipt: suspend () -> HibernateWriteReceipt = { HibernateWriteReceipt(true, "sleep-write-A") }
        var poll: suspend () -> Boolean? = { true }
        var load: (suspend (String) -> CloudCarSettings)? = null
        val writes = mutableListOf<Pair<String, CloudCarSettingUpdate>>()
        val checks = mutableListOf<String>()
        override suspend fun load(vin: String) = load?.invoke(vin) ?: cloud
        override suspend fun loadHibernateAuthorization(vin: String) = authorize(vin)
        override suspend fun updateHibernate(vin: String, update: CloudCarSettingUpdate, authorization: HibernateAuthorization): HibernateWriteReceipt {
            assertEquals(vin, authorization.vin); assertTrue(authorization.enterConfirmed)
            writes += vin to update; return receipt()
        }
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = error("legacy update must not be used")
        override suspend fun checkStatus(configId: String): Boolean? { checks += configId; return poll() }
    }
    private fun controller(g: Gateway, vin: () -> String = { "VIN-A" }) = CarSettingsController(
        writer = CloudCarSettingWriter(g, vin), gateway = g, vinProvider = vin,
        debounceMs = 0, statusPollIntervalMs = 0, statusPollAttempts = 1)
    private fun state(c: CarSettingsController) = c.state.value.valueState("cloud:77")
    private fun item(c: CarSettingsController) = c.state.value.items.single { it.id == "cloud:77" }
    private val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun observe(c: CarSettingsController, raw: Int, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) =
        c.updateVehicleObservation(VehicleStatusObservation(vin,
            empty.copy(extended = empty.extended.copy(sleepModeSwitch = raw)), origin, seq))

    @Test fun configurationAndUnknownAreNeverVehicleStateOrAnInitialChoice() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(false), state(c).configuredValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        for (raw in listOf("", "2", "-1", "0.5", "bad")) {
            g.cloud = g.cloud.copy(values = mapOf("sleepMode" to raw)); assertTrue(c.refresh())
            assertNull(state(c).configuredValue); assertNull(settingSwitchTarget(state(c), true))
            assertFalse(c.enterHibernate("cloud:77", true)); assertTrue(g.writes.isEmpty())
        }
    }

    @Test fun cacheVinSequenceAndInvalidObservationKeepTheirAuthority() = runBlocking {
        val c = controller(Gateway()); assertTrue(c.refresh())
        observe(c, 1, origin = VehicleStatusOrigin.CACHED_TTP)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        assertFalse(state(c).cachedValue!!.live); assertEquals(SettingValueSource.CACHED_TTP, state(c).cachedValue!!.source)
        observe(c, 0, 2); observe(c, 1, 1); observe(c, 1, 3, "VIN-B")
        assertEquals(CarSettingKind.Toggle(false), (state(c).vehicleValue as VehicleSettingValue.Known).kind)
        for ((n, raw) in listOf(-1, 2, 255).withIndex()) {
            observe(c, raw, 4L + n); assertFalse((state(c).vehicleValue as VehicleSettingValue.Known).live)
        }
        assertNull(state(c).draftValue)
    }

    @Test fun builderUsesTargetOneWithoutInvertingItOrMixingBleMetadata() {
        val update = requireNotNull(CloudCarSettingUpdateBuilder.build("77", CarSettingKind.Toggle(true), "VIN-A"))
        assertEquals(CloudCarSettingUpdatePath.PARAM_SETTING, update.path)
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "oper" to "SET_PARAM", "id" to "SLEEP_MODE_SWITCH",
            "value" to 1, "valueJson" to "[{\"byteCount\":1,\"dataType\":1,\"sort\":1,\"value\":1}]"), update.fields)
        for (kind in listOf(CarSettingKind.Toggle(false), CarSettingKind.Toggle(true, false), CarSettingKind.Choice(0, listOf("a")))) {
            assertNull(CloudCarSettingUpdateBuilder.build("77", kind, "VIN-A"))
            assertNull(VehicleSettingCommands.forConfigId("77", kind))
        }
    }

    @Test fun directControllerAndDraftCannotSkipConfirmationOrSendAnExitCommand() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertFalse(c.update("cloud:77") { CarSettingKind.Toggle(true) })
        assertFalse(c.enterHibernate("cloud:77")); assertFalse(c.enterHibernate("cloud:76", true))
        assertTrue(c.editDraft("cloud:77") { CarSettingKind.Toggle(true) })
        assertFalse(c.submitDraft("cloud:77")); assertTrue(g.writes.isEmpty())
        assertTrue(c.enterHibernate("cloud:77", true)); assertNull(item(c).hibernateAuthorization)
        assertFalse(c.update("cloud:77") { CarSettingKind.Toggle(false) })
        assertFalse(c.enterHibernate("cloud:77", true)); assertEquals(1, g.writes.size)
        g.cloud = g.cloud.copy(values = mapOf("sleepMode" to "1")); assertTrue(c.refresh())
        assertFalse(c.enterHibernate("cloud:77", true))
    }

    @Test fun actualWritersRejectUnknownAliasesMissingProofAndTheIncompleteBleBranch() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); var bleCalls = 0
        val ble = object : CarSettingWriter { override suspend fun write(item: CarSettingItem): Boolean { bleCalls++; return true } }
        var sends = 0
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sends++; return true }
        })
        for (connected in listOf(false, true)) {
            val w = CloudCarSettingWriter(g, { "VIN-A" }, ble, { connected })
            for (kind in listOf(CarSettingKind.Toggle(false), CarSettingKind.Toggle(true, false), CarSettingKind.Toggle(true))) {
                val target = item(c).copy(kind = kind)
                assertFalse(w.write(target)); assertFalse(mapped.write(target)); assertNull(VehicleSettingCommands.forItem(target))
                assertFalse(w.write(target.copy(hibernateAuthorization = HibernateAuthorization("VIN-B", true))))
                assertFalse(w.write(target.copy(hibernateAuthorization = HibernateAuthorization("VIN-A"))))
                assertFalse(w.write(target.copy(id = "cloud:002:enable", hibernateAuthorization = HibernateAuthorization("VIN-A", true))))
            }
            if (connected) assertFalse(w.write(item(c).copy(kind = CarSettingKind.Toggle(true),
                hibernateAuthorization = HibernateAuthorization("VIN-A", true))))
        }
        assertEquals(0, bleCalls); assertEquals(0, sends); assertTrue(g.writes.isEmpty())
    }

    @Test fun onlineProofIsMandatoryAndFailureOrCancellationNeverSends() = runBlocking {
        for (proof in listOf(null, HibernateAuthorization("VIN-B"))) {
            val g = Gateway().apply { authorize = { proof } }; val c = controller(g); assertTrue(c.refresh())
            assertFalse(c.enterHibernate("cloud:77", true)); assertTrue(g.writes.isEmpty())
            assertEquals(SettingWriteState.FAILED, state(c).writeState)
        }
        val g = Gateway().apply { authorize = { throw CancellationException("fake") } }; val c = controller(g); assertTrue(c.refresh())
        try { c.enterHibernate("cloud:77", true); fail("cancel swallowed") } catch (_: CancellationException) { }
        assertEquals(SettingWriteState.CANCELLED, state(c).writeState); assertTrue(g.writes.isEmpty())
    }

    @Test fun thisWritesReceiptNeverConfIdIsPolledAndAcceptanceDoesNotChangeTheVehicle() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh())
        assertTrue(c.enterHibernate("cloud:77", true)); assertEquals(listOf("sleep-write-A"), g.checks)
        assertEquals(CarSettingKind.Toggle(false), state(c).configuredValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c).writeState)
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, state(c).confirmationState)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED, state(c).confirmationStatus)
    }

    @Test fun missingReceiptFailedAndCancelledWritesKeepHonestState() = runBlocking {
        val missing = Gateway().apply { receipt = { HibernateWriteReceipt(true) } }; val c = controller(missing); assertTrue(c.refresh())
        assertFalse(c.enterHibernate("cloud:77", true)); assertTrue(missing.checks.isEmpty())
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c).writeState); assertEquals(SettingConfirmationState.UNKNOWN, state(c).confirmationState)
        for (cancel in listOf(false, true)) {
            val g = Gateway().apply { receipt = { if (cancel) throw CancellationException("fake") else HibernateWriteReceipt(false) } }
            val f = controller(g); assertTrue(f.refresh())
            try { assertFalse(f.enterHibernate("cloud:77", true)); assertFalse(cancel) } catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(f).writeState)
            assertNull(state(f).submittedValue); assertEquals(CarSettingKind.Toggle(false), state(f).configuredValue)
        }
    }

    @Test fun aFreshReadbackAfterResultPollingStillReconcilesAndClearsTheOptimisticTarget() = runBlocking {
        for (raw in listOf(0, 1)) {
            val c = controller(Gateway()); assertTrue(c.refresh()); assertTrue(c.enterHibernate("cloud:77", true))
            observe(c, raw)
            assertNull(state(c).submittedValue)
            assertEquals(CarSettingKind.Toggle(raw == 1), state(c).displayKind())
            assertEquals(if (raw == 1) SettingConfirmationState.READBACK_MATCHED else SettingConfirmationState.READBACK_MISMATCH,
                state(c).confirmationState)
        }
    }

    @Test fun aNewCloudReadReplacesOnlyTheOptimisticTargetNeverTheVehicleSource() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); assertTrue(c.enterHibernate("cloud:77", true))
        g.cloud = g.cloud.copy(values = mapOf("sleepMode" to "0")); assertTrue(c.refresh())
        assertNull(state(c).submittedValue); assertEquals(CarSettingKind.Toggle(false), state(c).displayKind())
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, state(c).confirmationState)
    }

    @Test fun authorizationWaitCannotTurnEarlierTtpIntoPostWriteReadback() = runBlocking {
        val proof = CompletableDeferred<HibernateAuthorization?>(); val poll = CompletableDeferred<Boolean?>()
        val g = Gateway().apply { authorize = { proof.await() }; this.poll = { poll.await() } }
        val c = controller(g); assertTrue(c.refresh())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.enterHibernate("cloud:77", true) }
        observe(c, 1); assertFalse(c.refresh()); proof.complete(HibernateAuthorization("VIN-A"))
        kotlinx.coroutines.yield()
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, 1, 2); poll.complete(null); job.join()
        assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
    }

    @Test fun vinChangeOrCancelledPollCannotConfirmANewVehicle() = runBlocking {
        for (changeVin in listOf(false, true)) {
            var vin = "VIN-A"; val poll = CompletableDeferred<Boolean?>()
            val g = Gateway().apply { this.poll = { poll.await() } }; val c = controller(g) { vin }; assertTrue(c.refresh())
            val job = launch(start = CoroutineStart.UNDISPATCHED) { c.enterHibernate("cloud:77", true) }
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c).writeState)
            if (changeVin) { vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); poll.complete(true); job.join() }
            else job.cancelAndJoin()
            assertEquals(SettingConfirmationState.UNKNOWN, state(c).confirmationState)
            assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
            if (!changeVin) assertEquals(SettingWriteState.CLOUD_ACCEPTED, state(c).writeState)
        }
    }

    @Test fun oldAuthorizationCannotWriteToNewVinAndRefreshKeepsDrafts() = runBlocking {
        var vin = "VIN-A"; val proof = CompletableDeferred<HibernateAuthorization?>()
        val g = Gateway().apply { authorize = { proof.await() } }; val c = controller(g) { vin }; assertTrue(c.refresh())
        assertTrue(c.editDraft("cloud:77") { CarSettingKind.Toggle(true) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(true), state(c).draftValue)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.enterHibernate("cloud:77", true) }
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh()); proof.complete(HibernateAuthorization("VIN-A")); job.join()
        assertTrue(g.writes.isEmpty()); assertNull(state(c).draftValue); assertNull(state(c).submittedValue)
    }

    private class Transport(var body: String) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse { requests += request; return HttpResponse(200, body) }
    }
    private fun gateway(t: Transport) = YadeaCarSettingsGateway(t, { "fake-token" }, timestampProvider = { 1_700_000_000_000L }, baseUrl = "https://example.invalid/")

    @Test fun realGatewayUsesOnlyExplicitOnlineAndNeverAGuessedDefault() = runBlocking {
        for (raw in listOf("true", "false", "null", "1", "{}")) {
            val t = Transport("""{"success":true,"data":{"onlineState":$raw}}""")
            assertEquals(raw == "true", gateway(t).loadHibernateAuthorization("VIN-A") != null)
            assertEquals("GET", t.requests.single().method); assertTrue(t.requests.single().url.contains("bikeRealStatus/getVehRealStatus?"))
        }
        for (body in listOf("{}", """{"success":false,"data":{"onlineState":true}}""", """{"success":true,"data":{"nested":{"onlineState":true}}}"""))
            assertNull(gateway(Transport(body)).loadHibernateAuthorization("VIN-A"))
    }

    @Test fun realGatewayKeepsTheReturnedIdAndRejectsLegacyOrMutatedRequests() = runBlocking {
        val update = requireNotNull(CloudCarSettingUpdateBuilder.build("77", CarSettingKind.Toggle(true), "VIN-A"))
        val proof = HibernateAuthorization("VIN-A", true)
        for (data in listOf("\"write-A\"", "123", "null", "true", "{}")) {
            val t = Transport("""{"success":true,"data":$data}""")
            val receipt = gateway(t).updateHibernate("VIN-A", update, proof)
            assertTrue(receipt.accepted); assertEquals(when (data) { "\"write-A\"" -> "write-A"; "123" -> "123"; else -> null }, receipt.resultId)
            assertTrue(t.requests.single().url.endsWith(YadeaCarSettingsGateway.PARAM_UPDATE_PATH))
        }
        val t = Transport("""{"success":true,"data":"write-A"}"""); val g = gateway(t)
        assertFalse(g.update("VIN-A", update)); assertFalse(g.updateHibernate("VIN-A", update, HibernateAuthorization("VIN-B", true)).accepted)
        assertFalse(g.updateHibernate("VIN-A", update, HibernateAuthorization("VIN-A")).accepted)
        for (fields in listOf(update.fields + ("value" to 0), update.fields + ("sleepMode" to 1), update.fields + ("vin" to "VIN-B")))
            assertFalse(g.updateHibernate("VIN-A", update.copy(fields = fields), proof).accepted)
        assertTrue(t.requests.isEmpty())
        for (body in listOf("{}", """{"data":"write-A"}""", """{"success":false}""", """{"success":true,"data":false}""")) {
            t.body = body; assertFalse(g.updateHibernate("VIN-A", update, proof).accepted)
        }
    }
}
