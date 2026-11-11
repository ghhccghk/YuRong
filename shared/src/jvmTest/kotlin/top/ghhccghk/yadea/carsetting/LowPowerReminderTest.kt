package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.ui.AppTimeOfDay
import top.ghhccghk.yadea.ui.lowPowerReminderVisibleItems
import top.ghhccghk.yadea.ui.settingValueSourceSummary
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Raw JSON decoding, refresh, editing, cloud map construction and receipts; no external I/O. */
class LowPowerReminderTest {
    private fun response(
        values: String = """"alarmOn":1,"alarmPower":20,"lowBatTelWarning":0,"lowBatWarningStartTime":"07:00","lowBatWarningEndTime":"21:00"""",
        ids: List<String> = listOf("9", "82"),
        auth: Boolean? = null,
    ): String = """{"success":true,"data":{${if (values.isBlank()) "" else "$values,"}"bikeConfigVos":[${
        ids.joinToString(",") { """{"confId":"$it","parentId":"1002","izLab":1${auth?.let { ",\"isAuth\":$it" }.orEmpty()}}""" }
    }]}}"""

    private class FakeTransport(var body: String?) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun execute(request: HttpRequest): HttpResponse? {
            requests += request
            return body?.let { HttpResponse(200, it) }
        }
    }

    private class FakeGateway(body: String) : CarSettingsGateway {
        val transport = FakeTransport(body)
        private val decoder = YadeaCarSettingsGateway(
            transport, tokenProvider = { "fake-token" }, timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/",
        )
        val updates = mutableListOf<Pair<String, CloudCarSettingUpdate>>()
        var updateResult = true
        var updateReply: suspend () -> Boolean = { updateResult }
        var statusReply: suspend () -> Boolean? = { true }
        override suspend fun load(vin: String): CloudCarSettings? = decoder.load(vin)
        override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
            updates += vin to update
            return updateReply()
        }
        override suspend fun checkStatus(configId: String): Boolean? = statusReply()
    }

    private class Fixture(body: String) {
        var vin = "VIN-A"
        val gateway = FakeGateway(body)
        // The real cloud writer is surrounded by in-memory fakes; no BLE fallback exists.
        val controller = CarSettingsController(
            writer = CloudCarSettingWriter(gateway, { vin }), gateway = gateway, vinProvider = { vin },
            debounceMs = 0, statusPollAttempts = 1, statusPollIntervalMs = 0,
        )
        val session get() = LowPowerReminderSession(controller.state.value.vin, controller.state.value.loadGeneration)
        fun value(id: String) = controller.state.value.valueState(id)
        fun info() = value("cloud:9").displayKind() as CarSettingKind.LowPowerInfo
        fun phone() = value("cloud:82").displayKind() as CarSettingKind.LowPowerPhone
        fun lastFields() = gateway.updates.last().second.fields
        suspend fun refresh() { assertTrue(controller.refresh()) }
        suspend fun infoSwitch(checked: Boolean) = controller.updateLowPowerReminder("cloud:9", session) {
            (it as CarSettingKind.LowPowerInfo).copy(alarmOn = checked, edit = CarSettingKind.LowPowerInfoEdit.ALARM_SWITCH)
        }
        suspend fun phoneSwitch(checked: Boolean) = controller.updateLowPowerReminder("cloud:82", session) {
            (it as CarSettingKind.LowPowerPhone).copy(enabled = checked, edit = CarSettingKind.LowPowerPhoneEdit.SWITCH)
        }
        fun threshold(value: Int) = controller.editDraft("cloud:9") {
            (it as CarSettingKind.LowPowerInfo).copy(alarmPower = value, edit = CarSettingKind.LowPowerInfoEdit.THRESHOLD)
        }
        suspend fun submit(id: String) = controller.submitLowPowerDraft(id, session)
    }

    @Test fun refreshPopulatesIndependentFieldsWithoutLiveTtp() = runBlocking {
        val f = Fixture(response())
        assertNull(f.value("cloud:9").displayKind())
        f.refresh()
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), f.info())
        assertEquals(CarSettingKind.LowPowerPhone(false, "07:00", "21:00"), f.phone())
        for (id in listOf("cloud:9", "cloud:82")) {
            assertEquals(VehicleSettingValue.Unknown, f.value(id).vehicleValue)
            assertNull(f.value(id).cachedValue)
            assertEquals(SettingConfirmationState.UNKNOWN, f.value(id).confirmationState)
            assertTrue(settingValueSourceSummary(f.value(id)).contains("非车辆回读"))
        }
        assertEquals(1, f.gateway.transport.requests.size)
        assertEquals(YadeaCarSettingsGateway.PATH, f.gateway.transport.requests.single().url.substringAfter("https://example.invalid/"))
    }

    @Test fun changingThresholdSendsBothInfoSiblingsAndNoPhoneFields() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.threshold(37))
        assertEquals(20, (f.value("cloud:9").configuredValue as CarSettingKind.LowPowerInfo).alarmPower)
        assertTrue(f.submit("cloud:9"))
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "alarmOn" to "1", "alarmPower" to "37"), f.lastFields())
        assertEquals(CarSettingKind.LowPowerPhone(false, "07:00", "21:00"), f.phone())
        assertEquals("9", f.gateway.updates.single().second.configId)
    }

    @Test fun oneSwitchClickSendsOneInfoUpdateAndPreservesThreshold() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.infoSwitch(false))
        assertEquals(1, f.gateway.updates.size)
        assertEquals("0", f.lastFields()["alarmOn"])
        assertEquals("20", f.lastFields()["alarmPower"])
        assertEquals(20, f.info().alarmPower)
        assertEquals(true, (f.value("cloud:9").configuredValue as CarSettingKind.LowPowerInfo).alarmOn)
        assertEquals(false, f.phone().enabled)
    }

    @Test fun phoneSwitchPreservesBothTimesAndDoesNotChangeInfo() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.phoneSwitch(true))
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "lowBatTelWarning" to 1,
            "lowBatWarningStartTime" to "07:00", "lowBatWarningEndTime" to "21:00"), f.lastFields())
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), f.info())
    }

    @Test fun editingStartTimeKeepsEndAndSendsPairWithoutSwitch() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.controller.editLowPowerTimes("cloud:82", f.session, startTime = "08:10"))
        assertEquals("21:00", f.phone().endTime)
        assertEquals("07:00", (f.value("cloud:82").configuredValue as CarSettingKind.LowPowerPhone).startTime)
        assertTrue(f.submit("cloud:82"))
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A",
            "lowBatWarningStartTime" to "08:10", "lowBatWarningEndTime" to "21:00"), f.lastFields())
        assertFalse(f.phone().enabled!!)
    }

    @Test fun editingEndTimeKeepsStartAndSendsPairWithoutSwitch() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "22:30"))
        assertEquals("07:00", f.phone().startTime)
        assertTrue(f.submit("cloud:82"))
        assertEquals("22:30", f.lastFields()["lowBatWarningEndTime"])
        assertEquals("07:00", f.lastFields()["lowBatWarningStartTime"])
        assertFalse(f.lastFields().containsKey("lowBatTelWarning"))
        assertFalse(f.lastFields().containsKey("alarmOn"))
    }

    @Test fun missingFieldsRemainUnknownAndCannotSupplySiblingDefaults() = runBlocking {
        val f = Fixture(response("")); f.refresh()
        assertNull(f.value("cloud:9").configuredValue)
        assertNull(f.value("cloud:82").configuredValue)
        assertFalse(f.infoSwitch(true))
        assertFalse(f.phoneSwitch(true))
        assertTrue(f.gateway.updates.isEmpty())
        assertNull(AppTimeOfDay.parse(null))
    }

    @Test fun independentlyMissingInfoFieldsCannotWriteAnInventedSibling() = runBlocking {
        val onOnly = Fixture(response(""""alarmOn":1""")); onOnly.refresh()
        assertNull(onOnly.info().alarmPower)
        assertFalse(onOnly.infoSwitch(false))
        val powerOnly = Fixture(response(""""alarmPower":20""")); powerOnly.refresh()
        assertNull(powerOnly.info().alarmOn)
        assertTrue(powerOnly.threshold(30))
        assertFalse(powerOnly.submit("cloud:9"))
        assertTrue(onOnly.gateway.updates.isEmpty())
        assertTrue(powerOnly.gateway.updates.isEmpty())
    }

    @Test fun unknownTimesCanBeExplicitlySelectedWithoutInventingPhoneSwitch() = runBlocking {
        val f = Fixture(response("")); f.refresh()
        assertTrue(f.controller.editLowPowerTimes("cloud:82", f.session, startTime = "06:20"))
        assertNull(f.phone().endTime)
        assertFalse(f.submit("cloud:82"))
        assertTrue(f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "20:40"))
        assertTrue(f.submit("cloud:82"))
        assertNull(f.phone().enabled)
        assertEquals("06:20", f.lastFields()["lowBatWarningStartTime"])
        assertEquals("20:40", f.lastFields()["lowBatWarningEndTime"])
        assertFalse(f.lastFields().containsKey("lowBatTelWarning"))
    }

    @Test fun unknownTimeIsNotReplacedWhenOnlyOtherEndIsEdited() = runBlocking {
        val f = Fixture(response("\"lowBatTelWarning\":1,\"lowBatWarningEndTime\":\"21:00\"")); f.refresh()
        assertNull(f.phone().startTime)
        assertTrue(f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "22:00"))
        assertNull(f.phone().startTime)
        assertFalse(f.submit("cloud:82"))
        assertFalse(f.phoneSwitch(false))
        assertTrue(f.gateway.updates.isEmpty())
    }

    @Test fun invalidThresholdsNeverCreateDefaultConfigurations() = runBlocking {
        for (raw in listOf("null", "-1", "101", "20.5", "2e1", "\"\"", "\"NaN\"", "999999999999")) {
            val f = Fixture(response("\"alarmOn\":1,\"alarmPower\":$raw")); f.refresh()
            assertNull(raw, f.info().alarmPower)
            assertFalse(f.infoSwitch(false))
            assertTrue(f.gateway.updates.isEmpty())
        }
    }

    @Test fun thresholdBoundariesSubmitUnscaledPercentIntegers() = runBlocking {
        val f = Fixture(response()); f.refresh()
        for (percent in listOf(0, 1, 20, 99, 100)) {
            assertTrue(f.threshold(percent))
            assertTrue(f.submit("cloud:9"))
            assertEquals(percent.toString(), f.lastFields()["alarmPower"])
        }
        for (invalid in listOf(-1, 101)) assertFalse(f.threshold(invalid))
    }

    @Test fun timeParsingDoesNotClampOrInventDefaults() = runBlocking {
        for (raw in listOf("", "7:00", "24:00", "07:60", "-1:00", "07:+0", "x", "07:00:00")) {
            val f = Fixture(response("\"lowBatWarningStartTime\":\"$raw\",\"lowBatWarningEndTime\":\"21:00\"")); f.refresh()
            assertNull(raw, f.phone().startTime)
            assertNull(AppTimeOfDay.parse(raw))
        }
        assertEquals("00:00", AppTimeOfDay(0, 0).format())
        assertEquals("23:50", AppTimeOfDay(23, 50).format())
        assertEquals(AppTimeOfDay(7, 5), AppTimeOfDay.parse("07:05"))
    }

    @Test fun overlapSelectionAdjustsByTenMinutesAtBothDayBoundaries() {
        assertEquals("08:00" to "08:10", adjustedLowPowerTimes("08:00", "08:00"))
        assertEquals("22:00" to "22:10", adjustedLowPowerTimes("22:00", "07:00"))
        assertEquals("23:40" to "23:50", adjustedLowPowerTimes("23:50", "00:00"))
        assertEquals("00:00" to "00:10", adjustedLowPowerTimes("00:00", "00:00"))
        assertEquals("07:00" to "21:00", adjustedLowPowerTimes("07:00", "21:00"))
        assertEquals(null to "21:00", adjustedLowPowerTimes(null, "21:00"))
    }

    @Test fun directInvalidOrCrossMidnightTargetsAreRejectedBeforeWriter() = runBlocking {
        val f = Fixture(response()); f.refresh()
        for ((start, end) in listOf("22:00" to "07:00", "07:00" to "07:00", "23:50" to "24:00", "07:05" to "21:00")) {
            assertFalse(f.controller.updateLowPowerReminder("cloud:82", f.session) {
                (it as CarSettingKind.LowPowerPhone).copy(startTime = start, endTime = end, edit = CarSettingKind.LowPowerPhoneEdit.TIMES)
            })
        }
        assertTrue(f.gateway.updates.isEmpty())
    }

    @Test fun readClockMinutesArePreservedButPickerRequiresTenMinuteStep() = runBlocking {
        val f = Fixture(response("\"lowBatTelWarning\":0,\"lowBatWarningStartTime\":\"07:05\",\"lowBatWarningEndTime\":\"21:09\"")); f.refresh()
        assertEquals("07:05", f.phone().startTime)
        assertEquals("21:09", f.phone().endTime)
        assertTrue(f.phoneSwitch(true))
        assertEquals("07:05", f.lastFields()["lowBatWarningStartTime"])
        assertFalse(f.controller.editLowPowerTimes("cloud:82", f.session, startTime = "08:05"))
    }

    @Test fun infoFailureRestoresConfigurationAndKeepsPhoneDraft() = runBlocking {
        val f = Fixture(response()); f.refresh()
        f.controller.editLowPowerTimes("cloud:82", f.session, startTime = "08:00")
        val phoneDraft = f.value("cloud:82").draftValue
        f.threshold(40)
        f.gateway.updateResult = false
        assertFalse(f.submit("cloud:9"))
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), f.info())
        assertNull(f.value("cloud:9").submittedValue)
        assertNull(f.value("cloud:9").draftValue)
        assertEquals(SettingWriteState.FAILED, f.value("cloud:9").writeState)
        assertNotNull(f.value("cloud:9").error)
        assertEquals(phoneDraft, f.value("cloud:82").draftValue)
    }

    @Test fun phoneFailureRestoresTimesAndSwitchAndKeepsInfoDraft() = runBlocking {
        val f = Fixture(response()); f.refresh()
        f.threshold(30)
        val infoDraft = f.value("cloud:9").draftValue
        f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "22:00")
        f.gateway.updateResult = false
        assertFalse(f.submit("cloud:82"))
        assertEquals(CarSettingKind.LowPowerPhone(false, "07:00", "21:00"), f.phone())
        assertEquals(infoDraft, f.value("cloud:9").draftValue)
        assertEquals(SettingWriteState.FAILED, f.value("cloud:82").writeState)
    }

    @Test fun cancelledWritePropagatesAndIsNeverSuccess() = runBlocking {
        val f = Fixture(response()); f.refresh()
        val gate = CompletableDeferred<Boolean>()
        f.gateway.updateReply = { gate.await() }
        val write = async(start = CoroutineStart.UNDISPATCHED) { f.infoSwitch(false) }
        assertTrue(f.value("cloud:9").busy)
        write.cancel()
        try { write.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(SettingWriteState.CANCELLED, f.value("cloud:9").writeState)
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), f.info())
        assertEquals(SettingConfirmationState.UNKNOWN, f.value("cloud:9").confirmationState)
    }

    @Test fun cancellingResultWaitRetainsCloudReceiptWithoutClaimingVehicleConfirmation() = runBlocking {
        val f = Fixture(response()); f.refresh()
        val gate = CompletableDeferred<Boolean?>()
        f.gateway.statusReply = { gate.await() }
        val write = async(start = CoroutineStart.UNDISPATCHED) { f.phoneSwitch(true) }
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.value("cloud:82").writeState)
        write.cancel()
        try { write.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.value("cloud:82").writeState)
        assertEquals(SettingConfirmationState.UNKNOWN, f.value("cloud:82").confirmationState)
        assertEquals(VehicleSettingValue.Unknown, f.value("cloud:82").vehicleValue)
    }

    @Test fun cloudAcceptedAndTrueResultNeverBecomeLiveVehicleValues() = runBlocking {
        val f = Fixture(response()); f.refresh()
        assertTrue(f.infoSwitch(false))
        val value = f.value("cloud:9")
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, value.writeState)
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, value.confirmationState)
        assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), value.configuredValue)
    }

    @Test fun vinSwitchAndReturnRejectOldDraftAndOldSheetCallback() = runBlocking {
        val f = Fixture(response()); f.refresh()
        val aSession = f.session
        f.threshold(45)
        f.controller.editLowPowerTimes("cloud:82", aSession, startTime = "08:00")
        f.vin = "VIN-B"; f.controller.onVinChanged(f.vin); f.refresh()
        assertNull(f.value("cloud:9").draftValue)
        assertNull(f.value("cloud:82").draftValue)
        assertFalse(f.controller.submitLowPowerDraft("cloud:9", aSession))
        assertFalse(f.controller.editLowPowerTimes("cloud:82", aSession, startTime = "09:00"))
        f.vin = "VIN-A"; f.controller.onVinChanged(f.vin); f.refresh()
        assertFalse(f.controller.updateLowPowerReminder("cloud:9", aSession) {
            (it as CarSettingKind.LowPowerInfo).copy(alarmOn = false, edit = CarSettingKind.LowPowerInfoEdit.ALARM_SWITCH)
        })
        assertTrue(f.gateway.updates.isEmpty())
    }

    @Test fun oldRequestFinallyCannotClearNewVinOperation() = runBlocking {
        val f = Fixture(response()); f.refresh()
        val oldGate = CompletableDeferred<Boolean>()
        f.gateway.updateReply = { oldGate.await() }
        val oldWrite = async(start = CoroutineStart.UNDISPATCHED) { f.infoSwitch(false) }
        f.vin = "VIN-B"; f.controller.onVinChanged(f.vin); f.refresh()
        val newGate = CompletableDeferred<Boolean>()
        f.gateway.updateReply = { newGate.await() }
        val newWrite = async(start = CoroutineStart.UNDISPATCHED) { f.infoSwitch(false) }
        val newOperation = f.value("cloud:9").operationId
        oldGate.complete(true)
        assertFalse(oldWrite.await())
        assertEquals(newOperation, f.value("cloud:9").operationId)
        assertTrue(f.value("cloud:9").busy)
        assertTrue("cloud:9" in f.controller.state.value.pendingIds)
        newGate.complete(true)
        assertTrue(newWrite.await())
        assertEquals(listOf("VIN-A", "VIN-B"), f.gateway.updates.map { it.first })
    }

    @Test fun cancelDraftAndReopenRestoreConfiguredValuesWithoutWriting() = runBlocking {
        val f = Fixture(response()); f.refresh()
        f.threshold(40)
        f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "22:00")
        f.controller.cancelDraft("cloud:9"); f.controller.cancelDraft("cloud:82")
        assertEquals(CarSettingKind.LowPowerInfo(true, 20), f.info())
        assertEquals(CarSettingKind.LowPowerPhone(false, "07:00", "21:00"), f.phone())
        assertFalse(f.submit("cloud:82"))
        assertTrue(f.gateway.updates.isEmpty())
    }

    @Test fun missingTtpDoesNotReplaceCloudOrDraftValues() = runBlocking {
        val f = Fixture(response()); f.refresh()
        f.threshold(50)
        f.controller.editLowPowerTimes("cloud:82", f.session, startTime = "08:00")
        val info = f.value("cloud:9"); val phone = f.value("cloud:82")
        f.controller.updateVehicleStatus("VIN-A", TtpInfoParser.parse(ByteArray(256) { -1 }))
        assertEquals(info, f.value("cloud:9"))
        assertEquals(phone, f.value("cloud:82"))
    }

    @Test fun refreshFailureRetainsValidLowPowerConfiguration() = runBlocking {
        val f = Fixture(response()); f.refresh()
        val info = f.value("cloud:9"); val phone = f.value("cloud:82")
        f.gateway.transport.body = null
        assertFalse(f.controller.refresh())
        assertEquals(info, f.value("cloud:9"))
        assertEquals(phone, f.value("cloud:82"))
    }

    @Test fun oneVisibleEntryRetainsBothIssuedConfigurationsAndUnrelatedItems() = runBlocking {
        for (ids in listOf(listOf("9", "82", "10"), listOf("82", "10"), listOf("9", "10"), listOf("10"))) {
            val f = Fixture(response(ids = ids)); f.refresh()
            val items = f.controller.state.value.items
            val visible = lowPowerReminderVisibleItems(items)
            assertEquals(if ("9" in ids || "82" in ids) 1 else 0, visible.count { it.configId in setOf("9", "82") })
            assertEquals(ids.count { it in setOf("9", "82") }, items.count { it.configId in setOf("9", "82") })
            assertEquals(items.filter { it.configId == "10" }, visible.filter { it.configId == "10" })
        }
    }

    @Test fun permissionDenialDisablesEachCloudUpdateWithoutDroppingRows() = runBlocking {
        val f = Fixture(response(auth = false)); f.refresh()
        assertEquals(2, f.controller.state.value.items.count { it.configId in setOf("9", "82") })
        assertFalse(f.infoSwitch(false))
        assertFalse(f.phoneSwitch(true))
        assertFalse(f.controller.editLowPowerTimes("cloud:82", f.session, endTime = "22:00"))
        assertTrue(f.gateway.updates.isEmpty())
    }

    @Test fun legacyToggleCannotBypassInfoSiblingRequirements() {
        assertNull(CloudCarSettingUpdateBuilder.build("9", CarSettingKind.Toggle(true), "VIN-A"))
        assertNull(CloudCarSettingUpdateBuilder.build("9", CarSettingKind.LowPowerInfo(true, null, CarSettingKind.LowPowerInfoEdit.ALARM_SWITCH), "VIN-A"))
    }
}
