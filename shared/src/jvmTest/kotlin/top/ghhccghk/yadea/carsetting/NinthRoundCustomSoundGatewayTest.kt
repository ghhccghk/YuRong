package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport

/** The real gateway is exercised exclusively through an injected in-memory transport. */
class NinthRoundCustomSoundGatewayTest {
    private class FakeTransport(val respond: suspend (HttpRequest) -> HttpResponse?) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse? {
            requests += request
            return respond(request)
        }
    }

    private fun gateway(transport: HttpTransport) = YadeaCarSettingsGateway(
        transport = transport, tokenProvider = { "fake-token" },
        timestampProvider = { 1_700_000_000_000L }, baseUrl = "https://example.invalid/api/app/",
    )

    private fun response(json: String) = HttpResponse(200, json)

    @Test fun staleFirstLoadResponseCannotStartRemainingQueries() = runBlocking {
        var valid = true
        val transport = FakeTransport { valid = false; response("""{"success":true,"data":[]}""") }
        try {
            withContext(CustomSoundRequestContext({ valid }, { _, _ -> })) {
                gateway(transport).loadCustomSoundState("fake-vin", 10)
            }
            fail("Expected stale cancellation")
        } catch (_: StaleCustomSoundRequest) {
            assertEquals(1, transport.requests.size)
            assertTrue(transport.requests.single().url.endsWith("resource/queryResourceList"))
        }
    }

    @Test fun staleSubmissionCannotStartResultPollingOrPublishReceipt() = runBlocking {
        var valid = true
        var receipts = 0
        val transport = FakeTransport {
            valid = false
            response("""{"success":true,"data":{"otaSendId":"fake-id"}}""")
        }
        try {
            withContext(CustomSoundRequestContext({ valid }, { _, _ -> receipts++ })) {
                gateway(transport).setCustomSoundEnabled("fake-vin", true)
            }
            fail("Expected stale cancellation")
        } catch (_: StaleCustomSoundRequest) {
            assertEquals(1, transport.requests.size)
            assertEquals(0, receipts)
        }
    }

    @Test fun cancellationFromTransportIsNotConvertedIntoFalse() = runBlocking {
        val cancelled = CancellationException("fake cancellation")
        val transport = FakeTransport { throw cancelled }
        try {
            gateway(transport).deleteCustomSound("fake-vin", "r1")
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
            assertEquals(1, transport.requests.size)
        }
    }

    private suspend fun pollCase(status: Int?): Triple<Boolean, Boolean?, Int?> {
        var accepted: Boolean? = null
        var serverStatus: Int? = null
        val transport = FakeTransport { request ->
            if (request.url.endsWith("resourcesBike/queryResultSetResourceBike")) {
                response(if (status == null) """{"success":true,"data":{}}""" else """{"success":true,"data":{"status":$status}}""")
            } else response("""{"success":true,"data":{"otaSendId":"fake-id"}}""")
        }
        val result = withContext(CustomSoundRequestContext({ true }, { a, s ->
            if (a != null) accepted = a
            if (s != null) serverStatus = s
        })) {
            gateway(transport).applyCustomSound(
                "fake-vin", CustomSoundState(parentConfId = "zdyyx001"),
                CustomSoundCategory("c1", "boot"), CustomSoundResource("r1", "sound"),
            )
        }
        return Triple(result, accepted, serverStatus)
    }

    @Test fun explicitServerResultIsSeparateFromRequestAcceptance() = runBlocking {
        assertEquals(Triple(true, true, 0), pollCase(0))
        assertEquals(Triple(false, true, 3), pollCase(3))
    }

    @Test fun legacyMissingStatusSuccessDoesNotInventServerResult() = runBlocking {
        assertEquals(Triple(true, true, null), pollCase(null))
    }

    @Test fun missingOtaSendIdDoesNotInventServerResult() = runBlocking {
        val receipts = mutableListOf<Pair<Boolean?, Int?>>()
        val transport = FakeTransport { response("""{"success":true,"data":{}}""") }
        val result = withContext(CustomSoundRequestContext({ true }, { accepted, status -> receipts += accepted to status })) {
            gateway(transport).setCustomSoundEnabled("fake-vin", false)
        }
        assertTrue(result)
        assertEquals(listOf(true to null), receipts)
        assertEquals(1, transport.requests.size)
    }

    @Test fun stalePollingResponseCannotPublishResultOrPollAgain() = runBlocking {
        var valid = true
        var statusReceipt: Int? = null
        val transport = FakeTransport { request ->
            if (request.url.endsWith("resourcesBike/queryResultSetResourceBike")) {
                valid = false
                response("""{"success":true,"data":{"status":0}}""")
            } else response("""{"success":true,"data":{"otaSendId":"fake-id"}}""")
        }
        try {
            withContext(CustomSoundRequestContext({ valid }, { _, status -> statusReceipt = status })) {
                gateway(transport).setCustomSoundEnabled("fake-vin", true)
            }
            fail("Expected stale cancellation")
        } catch (_: StaleCustomSoundRequest) {
            assertEquals(2, transport.requests.size)
            assertNull(statusReceipt)
        }
    }

    @Test fun rejectedLoadsDoNotManufactureCloudDefaults() = runBlocking {
        val transport = FakeTransport { response("""{"success":false,"data":[]}""") }
        assertNull(gateway(transport).loadCustomSoundState("fake-vin", 10))
    }

    @Test fun missingDataIsNotAValidEmptySelection() = runBlocking {
        val transport = FakeTransport { response("""{"success":true}""") }
        assertNull(gateway(transport).loadCustomSoundState("fake-vin", 10))
    }

    @Test fun partialSuccessfulResourceQueryDoesNotClaimKnownEnableOrSelection() = runBlocking {
        val transport = FakeTransport { request ->
            response(if (request.url.endsWith("resource/queryResourceList"))
                """{"success":true,"data":[{"id":"r1","resourceName":"sound"}]}"""
            else """{"success":false,"data":[]}""")
        }
        val loaded = gateway(transport).loadCustomSoundState("fake-vin", 10)!!
        assertEquals(setOf(CustomSoundData.RESOURCES), loaded.loadedFields)
        assertEquals("r1", loaded.resources.single().id)
    }

    @Test fun failedConfigCannotCertifyOldCategorySelections() = runBlocking {
        val transport = FakeTransport { request ->
            response(if (request.url.endsWith("resourceBike/queryResourceBikeList"))
                """{"success":true,"data":[{"resourceId":"r2","sonConfId":"c1","isSelected":true}]}"""
            else """{"success":false,"data":[]}""")
        }
        val loaded = gateway(transport).loadCustomSoundState("fake-vin", 10)!!
        assertTrue(CustomSoundData.ENABLED in loaded.loadedFields)
        assertFalse(CustomSoundData.SELECTION in loaded.loadedFields)
    }
}
