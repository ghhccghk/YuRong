package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

class RemainingMileageSettingsSafetyTest {
    private class Gateway : CarSettingsGateway {
        var eligibility: suspend (String) -> RemainingMileageEligibility? = { eligible(it) }
        var reads = 0
        override suspend fun load(vin: String) = CloudCarSettings(listOf(CloudBikeConfig("42")), emptyMap())
        override suspend fun loadRemainingMileageEligibility(vin: String): RemainingMileageEligibility? {
            reads++; return eligibility(vin)
        }
    }
    private class Writer(var action: suspend () -> Boolean = { true }) : CarSettingWriter {
        val sent = mutableListOf<CarSettingItem>()
        override suspend fun write(item: CarSettingItem): Boolean { sent += item; return action() }
    }
    companion object {
        private fun eligible(vin: String = "VIN-A") = RemainingMileageSettings.parse(vin,
            """{"formula":2,"count":0,"estimateMileage":40.5,"prompt":""}""")!!
    }
    private val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
    private fun controller(g: Gateway = Gateway(), w: CarSettingWriter = Writer(), vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = w, gateway = g, vinProvider = vin, debounceMs = 0, statusPollAttempts = 0)
    private fun item(c: CarSettingsController) = c.state.value.items.single { it.id == "cloud:42" }
    private fun state(c: CarSettingsController) = c.state.value.valueState("cloud:42")
    private fun observe(c: CarSettingsController, value: Int = 0, seq: Long = 1, vin: String = "VIN-A",
                        origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
        c.updateVehicleObservation(VehicleStatusObservation(vin, unknown.copy(extended = unknown.extended.copy(
            remainingMileageDebugStatus = value)), origin, seq))
    }

    @Test fun originalFormulaBranchesRequireCompleteEvidenceAndPreserveServerPrompt() {
        for (formula in listOf(1, 2)) {
            val pending = RemainingMileageSettings.parse("VIN-A", """{"formula":$formula,"count":3}""")!!
            assertFalse(pending.allowed); assertTrue(pending.prompt.startsWith("当前还剩余3次有效骑行"))
        }
        assertFalse(RemainingMileageSettings.parse("VIN-A", """{"formula":1,"count":0,"prompt":""}""")!!.allowed)
        for (estimate in listOf(0, -1)) assertFalse(RemainingMileageSettings.parse("VIN-A",
            """{"formula":2,"count":0,"estimateMileage":$estimate,"prompt":""}""")!!.allowed)
        assertTrue(eligible().allowed)
        val notice = RemainingMileageSettings.parse("VIN-A", """{"formula":2,"count":0,"estimateMileage":1,"prompt":"稍后开启"}""")!!
        assertFalse(notice.allowed); assertEquals("稍后开启", notice.prompt)
        assertEquals("服务端提示", RemainingMileageSettings.parse("VIN-A", """{"formula":3,"prompt":"服务端提示"}""")!!.prompt)
    }

    @Test fun absentInvalidAndNonfiniteResponseFieldsNeverGrantPermission() {
        for (json in listOf("{}", "null", "[]", "bad", """{"formula":true,"prompt":""}""",
            """{"formula":2,"count":0}""", """{"formula":2,"count":0.5,"prompt":""}""",
            """{"formula":1,"count":"bad"}""", """{"formula":2,"count":0,"estimateMileage":"NaN","prompt":""}""",
            """{"formula":2,"count":0,"estimateMileage":1}""", """{"formula":3,"prompt":null}""",
            """{"formula":3,"prompt":false}""")) assertNull(json, RemainingMileageSettings.parse("VIN-A", json))
        assertNull(RemainingMileageSettings.parse("", """{"formula":3,"prompt":""}"""))
    }

    @Test fun unknownHasNoDefaultSelectionAndOnlyStrictSameVinTtpCreatesObservedValue() = runBlocking {
        val c = controller(); assertTrue(c.refresh())
        assertTrue(item(c).writable); assertNull(state(c).displayKind()); assertNull(state(c).configuredValue)
        assertFalse(c.editDraft("cloud:42") { it }); assertFalse(c.update("cloud:42") { it })
        observe(c, vin = "VIN-B"); assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        observe(c, origin = VehicleStatusOrigin.CACHED_TTP); assertNotNull(state(c).cachedValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
        observe(c, 1, 2); assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        observe(c, 0, 1); assertEquals(CarSettingKind.Toggle(true), state(c).displayKind())
        observe(c, 2, 3); assertFalse((state(c).vehicleValue as VehicleSettingValue.Known).live)
        assertEquals(SettingReadbackState.MISSING_OR_INVALID, state(c).readbackState)
    }

    @Test fun blockedUnknownAndWrongVinEligibilityNeverReachWriter() = runBlocking {
        for (permission in listOf(null, RemainingMileageSettings.parse("VIN-A", """{"formula":1,"count":0}"""), eligible("VIN-B"))) {
            val g = Gateway().apply { eligibility = { permission } }; val w = Writer(); val c = controller(g, w)
            assertTrue(c.refresh()); observe(c)
            assertFalse(c.update("cloud:42") { CarSettingKind.Toggle(true) })
            assertTrue(w.sent.isEmpty()); assertEquals(SettingWriteState.FAILED, state(c).writeState)
            assertNotNull(state(c).error); assertEquals(CarSettingKind.Toggle(false), state(c).displayKind())
        }
    }

    @Test fun eligibilityFlowsThroughActualWritersAndOnlyFreshTtpConfirms() = runBlocking {
        val g = Gateway(); val sent = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { sent += command; return true }
        })
        val c = controller(g, CloudCarSettingWriter(g, { "VIN-A" }, mapped, { true })); assertTrue(c.refresh()); observe(c)
        assertTrue(c.update("cloud:42") { CarSettingKind.Toggle(true) })
        assertEquals(listOf(0xA4), sent.map { it.commandId }); assertEquals(1, g.reads)
        assertEquals(CarSettingKind.Toggle(false), (state(c).vehicleValue as VehicleSettingValue.Known).kind)
        assertEquals(CarSettingKind.Toggle(true), state(c).submittedValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, 1, 1); assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, 1, 2); assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
        assertNull(item(c).remainingMileageEligibility)
        assertTrue(c.update("cloud:42") { CarSettingKind.Toggle(false) })
        assertEquals(0xA5, sent.last().commandId); assertEquals(1, g.reads)
    }

    @Test fun directMapperAndWriterCannotSkipEligibilityWithUnknownOrForgedItems() = runBlocking {
        val g = Gateway(); val c = controller(g); assertTrue(c.refresh()); val i = item(c)
        val calls = mutableListOf<VehicleSettingCommand>()
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean { calls += command; return true }
        })
        val w = CloudCarSettingWriter(g, { "VIN-A" }, mapped, { true })
        val on = i.copy(kind = CarSettingKind.Toggle(true))
        assertNull(VehicleSettingCommands.forConfigId("42", on.kind)); assertFalse(mapped.write(on)); assertFalse(w.write(on))
        for (bad in listOf(on.copy(remainingMileageEligibility = eligible("VIN-B")),
            on.copy(kind = CarSettingKind.Toggle(true, false), remainingMileageEligibility = eligible()),
            on.copy(id = "cloud:42:enable", remainingMileageEligibility = eligible()))) assertFalse(w.write(bad))
        assertNull(VehicleSettingCommands.forConfigId("42", CarSettingKind.Toggle(false, false)))
        assertNull(CloudCarSettingUpdateBuilder.build("42", CarSettingKind.Toggle(true), "VIN-A")); assertTrue(calls.isEmpty())
    }

    @Test fun inFlightGateDeduplicatesRefreshAndVinChangeRejectsLatePermission() = runBlocking {
        var vin = "VIN-A"; val pending = CompletableDeferred<RemainingMileageEligibility?>()
        val g = Gateway().apply { eligibility = { pending.await() } }; val w = Writer(); val c = controller(g, w) { vin }
        assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update("cloud:42") { CarSettingKind.Toggle(true) }) }
        assertFalse(c.refresh()); assertFalse(c.update("cloud:42") { CarSettingKind.Toggle(true) })
        vin = "VIN-B"; c.onVinChanged(vin); assertTrue(c.refresh())
        pending.complete(eligible()); job.join()
        assertTrue(w.sent.isEmpty()); assertNull(state(c).draftValue); assertNull(state(c).submittedValue)
        assertEquals(VehicleSettingValue.Unknown, state(c).vehicleValue)
    }

    @Test fun failureAndCancellationNeverFakeSuccessfulExecution() = runBlocking {
        for (cancel in listOf(false, true)) {
            val g = Gateway(); val w = Writer { if (cancel) throw CancellationException("fake cancel") else false }
            val c = controller(g, w); assertTrue(c.refresh()); observe(c)
            try { assertFalse(c.update("cloud:42") { CarSettingKind.Toggle(true) }); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, state(c).writeState)
            assertEquals(CarSettingKind.Toggle(false), state(c).displayKind()); assertTrue(c.state.value.pendingIds.isEmpty())
        }
        val g = Gateway().apply { eligibility = { throw CancellationException("gate cancel") } }
        val w = Writer(); val c = controller(g, w); assertTrue(c.refresh()); observe(c)
        try { c.update("cloud:42") { CarSettingKind.Toggle(true) }; fail("swallowed cancellation") }
        catch (_: CancellationException) { }
        assertTrue(w.sent.isEmpty()); assertEquals(SettingWriteState.CANCELLED, state(c).writeState)
    }

    @Test fun refreshPreservesDraftButNeverPersistsEligibility() = runBlocking {
        val c = controller(); assertTrue(c.refresh()); observe(c)
        assertTrue(c.editDraft("cloud:42") { CarSettingKind.Toggle(true) }); assertTrue(c.refresh())
        assertEquals(CarSettingKind.Toggle(true), state(c).draftValue); assertNull(item(c).remainingMileageEligibility)
        c.cancelDraft("cloud:42"); assertFalse(c.submitDraft("cloud:42"))
    }

    @Test fun observationDuringEligibilityReadCannotConfirmTheLaterWrite() = runBlocking {
        val pending = CompletableDeferred<RemainingMileageEligibility?>()
        val g = Gateway().apply { eligibility = { pending.await() } }; val c = controller(g)
        assertTrue(c.refresh()); observe(c)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { assertTrue(c.update("cloud:42") { CarSettingKind.Toggle(true) }) }
        observe(c, 1, 2); pending.complete(eligible()); job.join()
        assertEquals(SettingConfirmationState.WAITING_READBACK, state(c).confirmationState)
        observe(c, 1, 3); assertEquals(SettingConfirmationState.READBACK_MATCHED, state(c).confirmationState)
    }

    @Test fun realGatewayUsesTwoSignedGetsAndKeepsSocFallbackOnlyInRequest() = runBlocking {
        val requests = mutableListOf<HttpRequest>()
        val fake = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                requests += request
                return HttpResponse(200, if (requests.size == 1) """{"success":true,"data":{"soc1":"75"}}"""
                    else """{"success":true,"data":{"formula":2,"count":0,"estimateMileage":43,"prompt":""}}""")
            }
        }
        val g = YadeaCarSettingsGateway(fake, { "fake-token" }, timestampProvider = { 1700000000000L }, baseUrl = "https://example.invalid/")
        assertTrue(g.loadRemainingMileageEligibility("VIN-A")!!.allowed)
        assertEquals(listOf("bikeRealStatus/getVehRealStatus", "vehiclebase/estimateMileage/cal2"),
            requests.map { it.url.substringAfter("https://example.invalid/").substringBefore('?') })
        val json = requests.map { request ->
            assertEquals("GET", request.method); assertNull(request.body)
            val encoded = request.url.substringAfter("gatewaySign=").substringBefore('&')
            Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, Crypto.urlDecode(encoded))!!.substringAfter("json=")
        }
        assertEquals("""{"_gateway":"True","vin":"VIN-A"}""", json[0])
        assertEquals("""{"_gateway":"True","soc1":"75","soc2":"0","vin":"VIN-A"}""", json[1])
    }

    @Test fun failedOrMalformedCloudResponseNeverDefaultsToAllowed() = runBlocking {
        for (body in listOf("null", "{}", """{"success":false,"data":{"formula":3,"prompt":""}}""",
            """{"success":true,"data":{}}""")) {
            val g = YadeaCarSettingsGateway(object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = HttpResponse(200, body)
            }, { "fake-token" }, timestampProvider = { 0 }, baseUrl = "https://example.invalid/")
            assertNull(g.loadRemainingMileageEligibility("VIN-A"))
        }
    }

    @Test fun transportFailureMissingCredentialsAndCancellationCannotGrantPermission() = runBlocking {
        for (reply in listOf(null, HttpResponse(500, """{"success":true,"data":{"formula":3,"prompt":""}}"""))) {
            val g = YadeaCarSettingsGateway(object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = reply
            }, { "fake-token" }, timestampProvider = { 0 }, baseUrl = "https://example.invalid/")
            assertNull(g.loadRemainingMileageEligibility("VIN-A"))
        }
        var calls = 0
        val g = YadeaCarSettingsGateway(object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse? { calls++; throw CancellationException("cancel") }
        }, { "" }, timestampProvider = { 0 }, baseUrl = "https://example.invalid/")
        assertNull(g.loadRemainingMileageEligibility("VIN-A")); assertEquals(0, calls)
        val cancellable = YadeaCarSettingsGateway(object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse? = throw CancellationException("cancel")
        }, { "fake-token" }, timestampProvider = { 0 }, baseUrl = "https://example.invalid/")
        try { cancellable.loadRemainingMileageEligibility("VIN-A"); fail("swallowed cancellation") }
        catch (_: CancellationException) { }
    }
}
