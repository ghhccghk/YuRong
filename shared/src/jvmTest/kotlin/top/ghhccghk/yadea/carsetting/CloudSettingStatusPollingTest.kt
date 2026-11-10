package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

class CloudSettingStatusPollingTest {

    private class FakeTransport(
        private val response: HttpResponse?,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse? {
            requests += request
            return response
        }
    }

    private class SequenceGateway(
        private val values: List<Boolean?>,
    ) : CarSettingsGateway {
        var calls = 0

        override suspend fun load(vin: String): CloudCarSettings? = null

        override suspend fun checkStatus(configId: String): Boolean? {
            calls += 1
            return values.getOrNull(calls - 1)
        }
    }

    private class CloudWriter : CarSettingWriter {
        override suspend fun write(item: CarSettingItem): Boolean =
            writeWithResult(item).success

        override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult =
            CarSettingWriteResult(success = true, channel = CarSettingWriteChannel.CLOUD)
    }

    private val item = CarSettingItem(
        id = "cloud:10",
        configId = "10",
        title = "电子围栏",
        summary = "",
        classification = CarSettingClassification.SECURITY,
        channel = CarSettingChannel.IOT,
        kind = CarSettingKind.Toggle(false),
    )

    private fun gateway(transport: HttpTransport): YadeaCarSettingsGateway =
        YadeaCarSettingsGateway(
            transport = transport,
            tokenProvider = { "token" },
            timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/api/app/",
        )

    private fun queryBusinessJson(request: HttpRequest): String {
        val query = request.url.substringAfter("?")
        val encodedSign = query.substringAfter("gatewaySign=").substringBefore("&")
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            Crypto.urlDecode(encodedSign),
        ) ?: error("gatewaySign did not decrypt")
        return plain.substringAfter("json=")
    }

    private suspend fun checkStatus(
        responseBody: String?,
        status: Int = 200,
    ): Triple<Boolean?, List<HttpRequest>, String?> {
        val transport = FakeTransport(responseBody?.let { HttpResponse(status, it) })
        val result = gateway(transport).checkStatus("10")
        return Triple(result, transport.requests, transport.requests.firstOrNull()?.let(::queryBusinessJson))
    }

    @Test
    fun resultZeroIsConfirmedAndRequestContainsGatewayFlag() = runBlocking {
        val (result, requests, json) = checkStatus("""{"success":true,"data":{"result":"0"}}""")

        assertEquals(true, result)
        assertEquals(1, requests.size)
        assertEquals("GET", requests.single().method)
        assertTrue(requests.single().url.startsWith("https://example.invalid/api/app/common-cmd/low-freq/info/param?"))
        assertEquals("""{"_gateway":"True","id":"10"}""", json)
    }

    @Test
    fun nonZeroAndUnparseableResultsAreTerminalFailure() = runBlocking {
        assertEquals(false, checkStatus("""{"success":true,"data":{"result":"1"}}""").first)
        assertEquals(false, checkStatus("""{"success":true,"data":{"result":"unknown"}}""").first)
    }

    @Test
    fun missingDataOrResultRemainsPending() = runBlocking {
        assertNull(checkStatus("""{"success":true}""").first)
        assertNull(checkStatus("""{"success":true,"data":{}}""").first)
    }

    @Test
    fun requestAndHttpErrorsAreTerminalFailure() = runBlocking {
        assertEquals(false, checkStatus(null).first)
        assertEquals(false, checkStatus("""{"success":true,"data":{"result":"0"}}""", status = 503).first)
    }

    @Test
    fun pollingStopsOnConfirmedOrFailureAndTimesOutOnlyOnPending() = runBlocking {
        val confirmedGateway = SequenceGateway(listOf(null, null, true))
        val confirmedController = CarSettingsController(
            initialItems = listOf(item),
            writer = CloudWriter(),
            gateway = confirmedGateway,
            vinProvider = { "VIN" },
            debounceMs = 0L,
            statusPollAttempts = 3,
            statusPollIntervalMs = 0L,
        )
        assertTrue(confirmedController.update("cloud:10") { CarSettingKind.Toggle(true) })
        assertEquals(3, confirmedGateway.calls)
        assertEquals(
            CarSettingConfirmationStatus.CLOUD_ACCEPTED,
            confirmedController.state.value.confirmationStatuses["cloud:10"],
        )
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT,
            confirmedController.state.value.valueState("cloud:10").confirmationState)
        assertEquals(VehicleSettingValue.Unknown,
            confirmedController.state.value.valueState("cloud:10").vehicleValue)

        val failedGateway = SequenceGateway(listOf(false))
        val failedController = CarSettingsController(
            initialItems = listOf(item),
            writer = CloudWriter(),
            gateway = failedGateway,
            vinProvider = { "VIN" },
            debounceMs = 0L,
            statusPollAttempts = 3,
            statusPollIntervalMs = 0L,
        )
        assertFalse(failedController.update("cloud:10") { CarSettingKind.Toggle(true) })
        assertEquals(1, failedGateway.calls)
        assertEquals(
            CarSettingConfirmationStatus.CLOUD_ACCEPTED,
            failedController.state.value.confirmationStatuses["cloud:10"],
        )
        assertEquals(SettingConfirmationState.FAILED,
            failedController.state.value.valueState("cloud:10").confirmationState)

        val pendingGateway = SequenceGateway(listOf(null, null, null))
        val pendingController = CarSettingsController(
            initialItems = listOf(item),
            writer = CloudWriter(),
            gateway = pendingGateway,
            vinProvider = { "VIN" },
            debounceMs = 0L,
            statusPollAttempts = 3,
            statusPollIntervalMs = 0L,
        )
        assertFalse(pendingController.update("cloud:10") { CarSettingKind.Toggle(true) })
        assertEquals(3, pendingGateway.calls)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED,
            pendingController.state.value.confirmationStatuses["cloud:10"])
        assertEquals(SettingConfirmationState.TIMED_OUT,
            pendingController.state.value.valueState("cloud:10").confirmationState)
        assertEquals("设置确认超时，车辆状态仍以回读为准", pendingController.state.value.message)
    }

    @Test
    fun thrownStatusErrorTerminatesPollingAsFailure() = runBlocking {
        val gateway = object : CarSettingsGateway {
            var calls = 0
            override suspend fun load(vin: String): CloudCarSettings? = null
            override suspend fun checkStatus(configId: String): Boolean? {
                calls += 1
                error("offline")
            }
        }
        val controller = CarSettingsController(
            initialItems = listOf(item),
            writer = CloudWriter(),
            gateway = gateway,
            vinProvider = { "VIN" },
            debounceMs = 0L,
            statusPollAttempts = 3,
            statusPollIntervalMs = 0L,
        )

        assertFalse(controller.update("cloud:10") { CarSettingKind.Toggle(true) })
        assertEquals(1, gateway.calls)
    }
}
