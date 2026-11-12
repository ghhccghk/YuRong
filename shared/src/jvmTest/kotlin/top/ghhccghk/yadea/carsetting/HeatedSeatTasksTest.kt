package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
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

class HeatedSeatTasksTest {
    private class FakeTransport(var responseBody: String = "{\"success\":true,\"data\":true}") : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var failure: Throwable? = null

        override suspend fun execute(request: HttpRequest): HttpResponse? {
            failure?.let { throw it }
            requests += request
            return HttpResponse(200, responseBody)
        }
    }

    private fun gateway(transport: HttpTransport) = YadeaCarSettingsGateway(
        transport = transport,
        tokenProvider = { "fake-token" },
        timestampProvider = { 1_700_000_000_000L },
        baseUrl = "https://example.invalid/api/app/",
    )

    private fun businessJson(form: String): String {
        val encoded = form.substringAfter("gatewaySign=").substringBefore("&")
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, Crypto.urlDecode(encoded))
            ?: error("signed fake request did not decode")
        return plain.substringAfter("json=")
    }

    @Test
    fun scheduleRulesKeepUnknownAndUseSmaliWeekNumberMapping() {
        assertTrue(HeatedSeatTaskRules.validTime("00:00"))
        assertTrue(HeatedSeatTaskRules.validTime("23:59"))
        assertFalse(HeatedSeatTaskRules.validTime("24:00"))
        assertFalse(HeatedSeatTaskRules.validTime("09:60"))
        assertFalse(HeatedSeatTaskRules.validTime("9:00"))
        assertEquals(true, HeatedSeatTaskRules.enabledFromStatus(0))
        assertEquals(false, HeatedSeatTaskRules.enabledFromStatus(1))
        assertNull(HeatedSeatTaskRules.enabledFromStatus(2))
        assertEquals(0, HeatedSeatTaskRules.statusForEnabled(true))
        assertEquals(1, HeatedSeatTaskRules.statusForEnabled(false))
        assertEquals(listOf(0, 1, 6), HeatedSeatTaskRules.apiWeekdays(setOf(0, 1, 6)))
        assertEquals(setOf(0, 1, 6), HeatedSeatTaskRules.parse(
            """{"id":"t1","vin":"VIN","workTime":"06:30","izRepeat":0,"weekNumbers":"0,1,6","status":1}""",
        )?.weekdayPositions)
        assertNull(HeatedSeatTaskRules.parse(
            """{"id":"t1","vin":"VIN","workTime":"06:30","izRepeat":0,"weekNumbers":"0,7","status":1}""",
        ))
        assertNull(HeatedSeatTaskRules.parse(
            """{"id":"t1","vin":"VIN","workTime":"06:30","izRepeat":2,"weekNumbers":"1"}""",
        ))
        assertTrue(HeatedSeatTaskRules.valid(HeatedSeatTaskDraft("06:30", 1, emptySet())))
        assertFalse(HeatedSeatTaskRules.valid(HeatedSeatTaskDraft("06:30", 0, emptySet())))
    }

    @Test
    fun loadUsesVinAndPreservesValidatedCloudTaskFields() = runBlocking {
        val transport = FakeTransport(
            """{"success":true,"data":[{"id":"task-1","vin":"VIN-A","workTime":"07:05","izRepeat":0,"weekNumbers":"0,2","status":1}]}""",
        )
        val tasks = gateway(transport).loadHeatedSeatTasks("VIN-A")
        assertEquals("task-1", tasks?.single()?.id)
        assertEquals("07:05", tasks?.single()?.workTime)
        assertEquals(setOf(0, 2), tasks?.single()?.weekdayPositions)
        assertEquals("GET", transport.requests.single().method)
        assertTrue(transport.requests.single().url.contains("vehicle-base/seat-heating-job/list?"))

        transport.responseBody = """{"success":true,"data":[{"id":"task-1","vin":"VIN-B","workTime":"07:05","izRepeat":1,"status":1}]}"""
        assertNull(gateway(transport).loadHeatedSeatTasks("VIN-A"))

        transport.responseBody = """{"success":true}"""
        assertNull(gateway(transport).loadHeatedSeatTasks("VIN-A"))
        transport.responseBody = """{"success":true,"data":[]}"""
        assertEquals(emptyList<HeatedSeatTask>(), gateway(transport).loadHeatedSeatTasks("VIN-A"))
    }

    @Test
    fun insertAndUpdateSendOnlySmaliConfirmedFields() = runBlocking {
        val transport = FakeTransport()
        val gateway = gateway(transport)

        assertTrue(gateway.saveHeatedSeatTask("VIN-A", HeatedSeatTaskDraft("08:15", 0, setOf(0, 2))))
        val insert = transport.requests.last()
        assertEquals("POST", insert.method)
        assertTrue(insert.url.endsWith("vehicle-base/seat-heating-job/insert"))
        val insertJson = businessJson(requireNotNull(insert.body))
        assertTrue(insertJson.contains("\"weekNumberList\":[0,2]"))
        assertTrue(insertJson.contains("\"workTime\":\"08:15\""))
        assertTrue(insertJson.contains("\"izRepeat\":0"))

        assertTrue(gateway.saveHeatedSeatTask("VIN-A", HeatedSeatTaskDraft("08:15", 1, emptySet()), "task-1"))
        val update = transport.requests.last()
        assertTrue(update.url.endsWith("vehicle-base/seat-heating-job/update"))
        val updateJson = businessJson(requireNotNull(update.body))
        assertTrue(updateJson.contains("\"id\":\"task-1\""))
        assertFalse(updateJson.contains("weekNumberList"))
        assertFalse(gateway.saveHeatedSeatTask("VIN-A", HeatedSeatTaskDraft("08:15", 1, emptySet()), "  "))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun mutationsUseConfirmedGetEndpointsAndRequireExplicitSuccess() = runBlocking {
        val transport = FakeTransport()
        val gateway = gateway(transport)
        assertTrue(gateway.setHeatedSeatTaskStatus("VIN-A", "task-1", 0))
        assertEquals("GET", transport.requests.last().method)
        assertTrue(transport.requests.last().url.contains("vehicle-base/seat-heating-job/status?"))
        assertTrue(businessJson(transport.requests.last().url.substringAfter('?')).contains("\"status\":\"0\""))
        assertTrue(gateway.setHeatedSeatTaskStatus("VIN-A", "task-1", 1))
        assertTrue(businessJson(transport.requests.last().url.substringAfter('?')).contains("\"status\":\"1\""))
        assertTrue(gateway.deleteHeatedSeatTask("VIN-A", "task-1"))
        assertTrue(transport.requests.last().url.contains("vehicle-base/seat-heating-job/del?"))
        assertFalse(gateway.setHeatedSeatTaskStatus("VIN-A", "task-1", 2))

        transport.responseBody = """{"data":true}"""
        assertFalse(gateway.deleteHeatedSeatTask("VIN-A", "task-1"))
    }

    @Test
    fun cancellationPropagatesWithoutReportingAcceptedWrite() = runBlocking {
        val transport = FakeTransport().apply { failure = CancellationException("cancelled") }
        var cancelled = false
        try {
            gateway(transport).saveHeatedSeatTask("VIN-A", HeatedSeatTaskDraft("08:15", 1, emptySet()))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun controllerRejectsScheduleAccessAfterVinSwitch() = runBlocking {
        var currentVin = "VIN-A"
        var reads = 0
        var writes = 0
        val fake = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                configs = listOf(CloudBikeConfig(confId = "62", confName = "座椅加热")),
                values = emptyMap(),
            )

            override suspend fun loadHeatedSeatTasks(vin: String): List<HeatedSeatTask> {
                reads++
                return emptyList()
            }

            override suspend fun saveHeatedSeatTask(vin: String, draft: HeatedSeatTaskDraft, taskId: String?): Boolean {
                writes++
                return true
            }
        }
        val controller = CarSettingsController(
            writer = null,
            gateway = fake,
            vinProvider = { currentVin },
            debounceMs = 0,
        )

        assertTrue(controller.refresh())
        assertEquals(emptyList<HeatedSeatTask>(), controller.loadHeatedSeatTasks("VIN-A"))
        assertEquals(1, reads)
        currentVin = "VIN-B"
        assertNull(controller.loadHeatedSeatTasks("VIN-A"))
        assertFalse(controller.saveHeatedSeatTask("VIN-A", HeatedSeatTaskDraft("08:00", 1, emptySet())))
        assertEquals(1, reads)
        assertEquals(0, writes)
    }
}
