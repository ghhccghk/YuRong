package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

/** In-memory transport/gateway only; the sync action never calls a vehicle Writer. */
class InstrumentInfoSyncTest {
    private class FakeTransport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var response = HttpResponse(200, """{"success":true,"data":true}""")
        var failure: Throwable? = null
        override suspend fun execute(request: HttpRequest): HttpResponse {
            failure?.let { throw it }
            requests += request
            return response
        }
    }

    private class FakeGateway : CarSettingsGateway {
        var cloud = CloudCarSettings(listOf(CloudBikeConfig("71")), mapOf("bikeInfoSyncName" to "1"))
        val vins = mutableListOf<String>()
        var reply: suspend () -> Boolean = { true }
        override suspend fun load(vin: String) = cloud
        override suspend fun refreshInstrumentInfo(vin: String): Boolean {
            vins += vin
            return reply()
        }
    }

    private fun controller(gateway: FakeGateway, vin: () -> String = { "VIN-A" }) =
        CarSettingsController(writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean = error("Sync must not write a setting")
        }, gateway = gateway, vinProvider = vin)

    @Test
    fun gatewayUsesOriginalPostVinQueryAndSignedBody() = runBlocking {
        val transport = FakeTransport()
        val gateway = YadeaCarSettingsGateway(transport, { "fake-token" }, timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/")
        assertTrue(gateway.refreshInstrumentInfo(" VIN-A "))
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://example.invalid/protocol-lowfreq/bike-info-sync-name/refresh?vin=VIN-A", request.url)
        val sign = requireNotNull(request.body).substringAfter("gatewaySign=").substringBefore('&')
        val signed = requireNotNull(Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, Crypto.urlDecode(sign)))
        assertEquals("{\"_gateway\":\"True\",\"vin\":\"VIN-A\"}", signed.substringAfter("json="))
        assertFalse(gateway.refreshInstrumentInfo(" "))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun gatewayRequiresExplicitAcceptanceAndPreservesCancellation() = runBlocking {
        val transport = FakeTransport()
        val gateway = YadeaCarSettingsGateway(transport, { "fake-token" }, timestampProvider = { 1L }, baseUrl = "https://example.invalid/")
        for (body in listOf("{}", "{\"success\":true}", "{\"success\":false,\"data\":true}",
            "{\"success\":true,\"data\":false}", "{\"data\":{\"success\":true,\"data\":true}}")) {
            transport.response = HttpResponse(200, body)
            assertFalse(gateway.refreshInstrumentInfo("VIN-A"))
        }
        transport.response = HttpResponse(500, "{\"success\":true,\"data\":true}")
        assertFalse(gateway.refreshInstrumentInfo("VIN-A"))
        val cancellation = CancellationException("fake cancellation")
        transport.failure = cancellation
        try { gateway.refreshInstrumentInfo("VIN-A"); fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }

    @Test
    fun unknownDisabledUnauthorizedOrMissingConfigurationCannotSend() = runBlocking {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        for (raw in listOf(null, "0", "2", "garbage")) {
            gateway.cloud = CloudCarSettings(listOf(CloudBikeConfig("71")),
                raw?.let { mapOf("bikeInfoSyncName" to it) } ?: emptyMap())
            controller.refresh()
            assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        }
        gateway.cloud = CloudCarSettings(listOf(CloudBikeConfig("71", isAuth = false)), mapOf("bikeInfoSyncName" to "1"))
        controller.refresh()
        assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        gateway.cloud = CloudCarSettings(emptyList(), mapOf("bikeInfoSyncName" to "1"))
        controller.refresh()
        assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        assertTrue(gateway.vins.isEmpty())
    }

    @Test
    fun acceptanceIsIndependentOfSettingValuesAndVehicleConfirmation() = runBlocking {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        assertTrue(controller.refresh())
        val before = controller.state.value.valueStates
        assertTrue(controller.refreshInstrumentInfo("VIN-A"))
        assertEquals(listOf("VIN-A"), gateway.vins)
        assertEquals(InstrumentInfoSyncPhase.CLOUD_ACCEPTED, controller.state.value.instrumentInfoSync?.phase)
        assertEquals(before, controller.state.value.valueStates)
        assertEquals(VehicleSettingValue.Unknown, controller.state.value.valueState("cloud:71").vehicleValue)
        assertEquals(SettingConfirmationState.UNKNOWN, controller.state.value.valueState("cloud:71").confirmationState)
    }

    @Test
    fun draftAndDuplicateSubmissionCannotBypassGuard() = runBlocking {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        controller.refresh()
        controller.editDraft("cloud:71") { CarSettingKind.Toggle(false) }
        val draft = controller.state.value.valueState("cloud:71").draftValue
        assertNotNull(draft)
        assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        assertEquals(draft, controller.state.value.valueState("cloud:71").draftValue)

        val fresh = controller(gateway)
        fresh.refresh()
        val deferred = CompletableDeferred<Boolean>()
        gateway.reply = { deferred.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { fresh.refreshInstrumentInfo("VIN-A") }
        assertEquals(InstrumentInfoSyncPhase.SUBMITTING, fresh.state.value.instrumentInfoSync?.phase)
        assertFalse(fresh.refreshInstrumentInfo("VIN-A"))
        assertEquals(1, gateway.vins.size)
        deferred.complete(true)
        assertTrue(pending.await())
    }

    @Test
    fun oldVinOrRefreshGenerationCannotPublishLateAcceptance() = runBlocking {
        val gateway = FakeGateway()
        var vin = "VIN-A"
        val controller = controller(gateway) { vin }
        controller.refresh()
        var deferred = CompletableDeferred<Boolean>()
        gateway.reply = { deferred.await() }
        val old = async(start = CoroutineStart.UNDISPATCHED) { controller.refreshInstrumentInfo(vin) }
        vin = "VIN-B"
        controller.refresh()
        deferred.complete(true)
        assertFalse(old.await())
        assertNull(controller.state.value.instrumentInfoSync)
        assertEquals("VIN-B", controller.state.value.vin)

        deferred = CompletableDeferred()
        val previous = async(start = CoroutineStart.UNDISPATCHED) { controller.refreshInstrumentInfo(vin) }
        controller.refresh()
        deferred.complete(true)
        assertFalse(previous.await())
        assertNull(controller.state.value.instrumentInfoSync)
    }

    @Test
    fun failureAndCancellationNeverCreateSuccessOrChangeSettingValues() = runBlocking {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        controller.refresh()
        val before = controller.state.value.valueStates
        gateway.reply = { error("fake failure") }
        assertFalse(controller.refreshInstrumentInfo("VIN-A"))
        assertEquals(InstrumentInfoSyncPhase.FAILED, controller.state.value.instrumentInfoSync?.phase)
        assertEquals(before, controller.state.value.valueStates)
        val cancellation = CancellationException("fake cancellation")
        gateway.reply = { throw cancellation }
        try { controller.refreshInstrumentInfo("VIN-A"); fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(cancellation, actual) }
        assertEquals(InstrumentInfoSyncPhase.CANCELLED, controller.state.value.instrumentInfoSync?.phase)
        assertEquals(before, controller.state.value.valueStates)
        gateway.reply = { true }
        assertTrue(controller.refreshInstrumentInfo("VIN-A"))
    }
}
