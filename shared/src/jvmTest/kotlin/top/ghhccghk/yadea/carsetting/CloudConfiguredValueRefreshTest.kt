package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.ui.carSettingSummary
import top.ghhccghk.yadea.ui.settingSwitchTarget
import top.ghhccghk.yadea.ui.settingValueSourceSummary
import top.ghhccghk.yadea.ui.settingSliderValueLabel
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** Raw JSON -> real decoder -> mapper -> refresh -> UI policy. All I/O is in-memory. */
class CloudConfiguredValueRefreshTest {
    private fun response(values: String = "\"distanceOn\":1,\"distance\":200", ids: List<String> = listOf("10")) =
        """{"success":true,"data":{"centerCircleLon":120.25,"centerCircleLat":30.5,${if (values.isBlank()) "" else "$values,"}"bikeConfigVos":[${
            ids.joinToString(",") { """{"confId":"$it","parentId":"1002","izLab":1}""" }
        }]}}"""

    private class FakeTransport(var body: String?) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        var reply: suspend () -> String? = { body }
        override suspend fun execute(request: HttpRequest): HttpResponse? {
            requests += request
            return reply()?.let { HttpResponse(200, it) }
        }
    }

    private class FakeGateway(body: String?) : CarSettingsGateway {
        val transport = FakeTransport(body)
        private val decoder = YadeaCarSettingsGateway(
            transport = transport, tokenProvider = { "fake-token" }, timestampProvider = { 1_700_000_000_000L },
            baseUrl = "https://example.invalid/",
        )
        override suspend fun load(vin: String) = decoder.load(vin)
        override suspend fun checkStatus(configId: String) = true
    }

    private class FakeWriter : CarSettingWriter {
        val items = mutableListOf<CarSettingItem>()
        var result = true
        var wait: CompletableDeferred<Boolean>? = null
        override suspend fun write(item: CarSettingItem): Boolean {
            items += item
            return wait?.await() ?: result
        }
        override suspend fun writeWithResult(item: CarSettingItem) =
            CarSettingWriteResult(write(item), CarSettingWriteChannel.CLOUD)
    }

    private fun controller(gateway: FakeGateway, writer: FakeWriter = FakeWriter(), vin: () -> String = { "A" }) =
        CarSettingsController(writer = writer, gateway = gateway, vinProvider = vin,
            debounceMs = 0, statusPollAttempts = 1, statusPollIntervalMs = 0)

    private fun CarSettingsController.value(id: String = "cloud:10") = state.value.valueState(id)
    private val unknownTtp = TtpInfoParser.parse(ByteArray(256) { -1 })

    @Test fun suppliedRefreshFieldsReachFenceSwitchAndRadiusWithoutCreatingVehicleObservation() = runBlocking {
        // Non-identifying subset of the user's refreshed response, not a new live request.
        val g = FakeGateway(response())
        val c = controller(g)
        assertTrue(c.state.value.items.isEmpty())
        assertNull(c.value().displayKind())
        assertTrue(c.refresh())
        assertEquals(YadeaCarSettingsGateway.PATH, g.transport.requests.single().url.substringAfter("https://example.invalid/"))
        assertTrue((c.value().displayKind() as CarSettingKind.Toggle).checked)
        val radius = c.value("cloud:10:distance")
        assertEquals(200f, (radius.displayKind() as CarSettingKind.Slider).value)
        for (v in listOf(c.value(), radius)) {
            assertNotNull(v.configuredValue)
            assertEquals(VehicleSettingValue.Unknown, v.vehicleValue)
            assertNull(v.cachedValue)
            assertEquals(SettingConfirmationState.UNKNOWN, v.confirmationState)
            assertTrue(settingValueSourceSummary(v).contains("云端配置"))
            assertFalse(settingValueSourceSummary(v).contains("UNKNOWN"))
        }
    }

    @Test fun explicitCloudFalseIsKnownAndDirectSwitchProducesOneRequestedTarget() = runBlocking {
        val c = controller(FakeGateway(response("\"distanceOn\":0,\"distance\":200")))
        c.refresh()
        assertFalse((c.value().configuredValue as CarSettingKind.Toggle).checked)
        assertTrue(requireNotNull(settingSwitchTarget(c.value(), true)).checked)
        assertEquals(VehicleSettingValue.Unknown, c.value().vehicleValue)
    }

    @Test fun missingCloudScalarsRemainUnknownDespiteControlPlaceholders() = runBlocking {
        val c = controller(FakeGateway(response("")))
        c.refresh()
        assertEquals(2, c.state.value.items.count { it.configId == "10" })
        assertNull(c.value().displayKind())
        assertNull(c.value("cloud:10:distance").displayKind())
        assertFalse((c.state.value.items.first { it.id.endsWith(":distance") }.kind as CarSettingKind.Slider).valueKnown)
        assertNull(settingSwitchTarget(c.value(), true))
    }

    @Test fun malformedCloudBooleansNeverBecomeDefaultFalse() = runBlocking {
        for (raw in listOf("null", "2", "-1", "\"\"", "\"invalid\"", "{}", "[]")) {
            val c = controller(FakeGateway(response("\"distanceOn\":$raw")))
            c.refresh()
            assertNull(raw, c.value().configuredValue)
            assertNull(raw, c.value().displayKind())
        }
    }

    @Test fun fractionalOverflowNullAndOutOfPolicyRadiusNeverBecomeAValidDefault() = runBlocking {
        for (raw in listOf("null", "0", "199", "2001", "200.5", "2e2", "9999999999999", "\"\"", "\"NaN\"", "{}")) {
            val c = controller(FakeGateway(response("\"distance\":$raw")))
            c.refresh()
            assertNull(raw, c.value("cloud:10:distance").configuredValue)
            assertNull(raw, c.value("cloud:10:distance").displayKind())
        }
    }

    @Test fun nestedMetadataCannotImpersonateMissingRootConfigurationScalars() = runBlocking {
        val c = controller(FakeGateway(response("\"metadata\":{\"distanceOn\":1,\"distance\":200},\"distanceOn\":null")))
        c.refresh()
        assertNull(c.value().configuredValue)
        assertNull(c.value("cloud:10:distance").configuredValue)
    }

    @Test fun supportedStringAndBooleanRepresentationsRemainSourceSeparated() = runBlocking {
        val c = controller(FakeGateway(response("\"distanceOn\":true,\"distance\":\"200\"")))
        c.refresh()
        assertTrue((c.value().displayKind() as CarSettingKind.Toggle).checked)
        assertEquals(200f, (c.value("cloud:10:distance").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun absentDynamicConfigurationIsDifferentFromPresentItemWithMissingValue() = runBlocking {
        val g = FakeGateway(response(ids = emptyList()))
        val c = controller(g)
        c.refresh()
        assertFalse(c.state.value.items.any { it.configId == "10" }) // Keep the existing local key entries.
        g.transport.body = response("")
        c.refresh()
        assertEquals(2, c.state.value.items.count { it.configId == "10" })
        assertNull(c.value().configuredValue)
    }

    @Test fun successfulRefreshWithMissingValueDoesNotResurrectAnOlderCloudConfiguration() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        g.transport.body = response("")
        c.refresh()
        assertNull(c.value().displayKind())
        assertNull(c.value("cloud:10:distance").displayKind())
        assertTrue(c.state.value.loadedFromCloud)
    }

    @Test fun pendingRefreshAndFailureKeepPreviousConfigurationVisible() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        val before = c.value()
        val result = CompletableDeferred<String?>()
        g.transport.reply = { result.await() }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { c.refresh() }
        assertTrue(c.state.value.loading)
        assertEquals(before, c.value())
        result.complete(null)
        assertFalse(refresh.await())
        assertEquals(before, c.value())
        assertFalse(c.state.value.loading)
        assertNotNull(c.state.value.message)
    }

    @Test fun successfulRefreshReplacesCloudValueButCannotCreateReadbackConfirmation() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        g.transport.body = response("\"distanceOn\":0,\"distance\":300")
        c.refresh()
        assertFalse((c.value().displayKind() as CarSettingKind.Toggle).checked)
        assertEquals(300f, (c.value("cloud:10:distance").displayKind() as CarSettingKind.Slider).value)
        assertEquals(SettingConfirmationState.UNKNOWN, c.value().confirmationState)
        assertEquals(VehicleSettingValue.Unknown, c.value().vehicleValue)
    }

    @Test fun subsequentMissingTtpDoesNotClearFenceCloudConfiguration() = runBlocking {
        val c = controller(FakeGateway(response()))
        c.refresh()
        val before = c.state.value.valueStates
        c.updateVehicleStatus("A", unknownTtp)
        assertEquals(before, c.state.value.valueStates)
    }

    @Test fun cloudAndBleCapableSettingsDisplayCloudWithNoBleConnection() = runBlocking {
        val c = controller(FakeGateway(response("\"chargeWattSwitch\":1,\"chargeWattVal\":350,\"bleDistance\":2,\"bleUnlockOn\":1", listOf("47", "52"))))
        c.refresh()
        for (id in listOf("cloud:47", "cloud:47:enable", "cloud:52", "cloud:52:distance")) {
            val item = c.state.value.items.first { it.id == id }
            if (item.configId == "47") assertTrue(VehicleSettingCommands.supportsItem(item))
            assertTrue(CloudCarSettingUpdateBuilder.supports(item.configId, item.kind))
            assertNotNull(c.value(id).displayKind())
            assertEquals(VehicleSettingValue.Unknown, c.value(id).vehicleValue)
        }
    }

    @Test fun liveChargingPowerWinsWhileConflictingCloudConfigurationRemainsInspectable() = runBlocking {
        val g = FakeGateway(response("\"chargeWattVal\":350", listOf("47")))
        val c = controller(g)
        c.refresh()
        c.updateVehicleStatus("A", unknownTtp.copy(extended = unknownTtp.extended.copy(batteryChargingPowerValue = 500)))
        g.transport.body = response("\"chargeWattVal\":250", listOf("47"))
        c.refresh()
        val v = c.value("cloud:47")
        assertEquals(500f, (v.displayKind() as CarSettingKind.Slider).value)
        assertEquals(250f, (v.configuredValue as CarSettingKind.Slider).value)
        assertEquals(SettingValueSource.LIVE_TTP, (v.vehicleValue as VehicleSettingValue.Known).source)
        val text = settingValueSourceSummary(v)
        assertTrue(text.contains("500 W"))
        assertTrue(text.contains("250 W"))
        assertTrue(text.contains("非车辆回读"))
        c.updateVehicleStatus("A", unknownTtp)
        assertEquals(v.configuredValue, c.value("cloud:47").configuredValue)
        assertEquals((v.vehicleValue as VehicleSettingValue.Known).copy(live = false), c.value("cloud:47").vehicleValue)
        assertEquals(SettingReadbackState.MISSING_OR_INVALID, c.value("cloud:47").readbackState)
    }

    @Test fun numericAndChoiceCloudFieldsDoNotDependOnTtpOrMapperDefaults() = runBlocking {
        val c = controller(FakeGateway(response(
            "\"chargeWattVal\":350,\"chargeUlVal\":100,\"highestSpeedLimit\":0,\"bikeModelTopSpeedMax\":60,\"seatHeatingLevel\":2,\"capacity\":22",
            listOf("47", "48", "61", "62", "60"),
        )))
        c.refresh()
        assertEquals(350f, (c.value("cloud:47").configuredValue as CarSettingKind.Slider).value)
        assertEquals(100f, (c.value("cloud:48").configuredValue as CarSettingKind.Slider).value)
        assertEquals(0f, (c.value("cloud:61").configuredValue as CarSettingKind.Slider).value)
        // HeatedSeatsActivity radio listeners and seatHeatingLevel use raw 0/1/2.
        assertEquals(2, (c.value("cloud:62:level").configuredValue as CarSettingKind.Choice).selectedIndex)
        assertEquals(BatteryCapacityOptions.valuesAh.indexOf(22), (c.value("cloud:60:level").configuredValue as CarSettingKind.Choice).selectedIndex)
        assertNull(c.value("cloud:60:enable").configuredValue)
    }

    @Test fun invalidOrMissingNumericChoiceFieldsDoNotFallBackToPreparedItemValues() = runBlocking {
        val c = controller(FakeGateway(response("\"seatHeatingLevel\":4,\"chargeWattVal\":\"bad\",\"highestSpeedLimit\":3", listOf("47", "48", "61", "62", "60"))))
        c.refresh()
        for (id in listOf("cloud:47", "cloud:48", "cloud:61", "cloud:62:level", "cloud:60:level")) {
            assertNull(id, c.value(id).configuredValue)
            assertNull(id, c.value(id).displayKind())
        }
    }

    @Test fun radiusUsesOriginalRangeAndUnscaledCloudSubmissionWithKnownCompanions() = runBlocking {
        val w = FakeWriter()
        val c = controller(FakeGateway(response()), w)
        c.refresh()
        val slider = c.value("cloud:10:distance").displayKind() as CarSettingKind.Slider
        assertEquals(200f..2000f, slider.range)
        assertEquals(1799, slider.steps)
        assertEquals(" m", slider.unit)
        assertTrue(c.update("cloud:10:distance") { slider.copy(value = 300f) })
        val sent = w.items.single()
        val update = requireNotNull(CloudCarSettingUpdateBuilder.build("10", sent.kind, "A", sent.geofenceConfiguration))
        assertEquals("300", update.fields["distance"])
        assertEquals("1", update.fields["distanceOn"])
        assertEquals(120.25, update.fields["centerCircleLon"])
        assertEquals(CloudCarSettingUpdatePath.BIKE_SETTING, update.path)
        assertEquals(200f, (c.value("cloud:10:distance").configuredValue as CarSettingKind.Slider).value)
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:10:distance").vehicleValue)
    }

    @Test fun cloudRefreshPreservesUserDraftUntilExplicitCancel() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        assertTrue(c.editDraft("cloud:10:distance") { (it as CarSettingKind.Slider).copy(value = 400f) })
        g.transport.body = response("\"distance\":300")
        c.refresh()
        assertEquals(400f, (c.value("cloud:10:distance").displayKind() as CarSettingKind.Slider).value)
        assertEquals(300f, (c.value("cloud:10:distance").configuredValue as CarSettingKind.Slider).value)
        c.cancelDraft("cloud:10:distance")
        assertEquals(300f, (c.value("cloud:10:distance").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun fenceRefreshWaitsForTheInFlightCompositeWrite() = runBlocking {
        val g = FakeGateway(response())
        val w = FakeWriter().apply { wait = CompletableDeferred() }
        val c = controller(g, w)
        c.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.update("cloud:10") { CarSettingKind.Toggle(false) } }
        assertFalse(c.refresh())
        assertFalse((c.value().submittedValue as CarSettingKind.Toggle).checked)
        assertEquals(SettingWriteState.SUBMITTING, c.value().writeState)
        assertEquals(SettingConfirmationState.UNKNOWN, c.value().confirmationState)
        assertTrue(c.state.value.pendingIds.isNotEmpty())
        w.wait!!.complete(true)
        job.join()
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value().writeState)
        assertTrue(c.refresh())
        assertTrue(c.state.value.pendingIds.isEmpty())
    }

    @Test fun refreshRetainsCloudReceiptAndSubmittedTargetWithoutPromotingEitherToReadback() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        assertTrue(c.update("cloud:10") { CarSettingKind.Toggle(false) })
        g.transport.body = response("\"distanceOn\":1")
        c.refresh()
        assertFalse((c.value().submittedValue as CarSettingKind.Toggle).checked)
        assertTrue((c.value().configuredValue as CarSettingKind.Toggle).checked)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value().writeState)
        assertEquals(VehicleSettingValue.Unknown, c.value().vehicleValue)
    }

    @Test fun failedWriteKeepsNewVehicleObservationAndOriginalCloudConfiguration() = runBlocking {
        val w = FakeWriter().apply { wait = CompletableDeferred() }
        val c = controller(FakeGateway(response("\"chargeWattVal\":350", listOf("47"))), w)
        c.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            c.update("cloud:47") { (it as CarSettingKind.Slider).copy(value = 600f) }
        }
        c.updateVehicleStatus("A", unknownTtp.copy(extended = unknownTtp.extended.copy(batteryChargingPowerValue = 500)))
        w.wait!!.complete(false)
        job.join()
        assertEquals(500f, (c.value("cloud:47").displayKind() as CarSettingKind.Slider).value)
        assertEquals(350f, (c.value("cloud:47").configuredValue as CarSettingKind.Slider).value)
        assertNull(c.value("cloud:47").submittedValue)
    }

    @Test fun duplicateRefreshIsSerializedAndLaterRefreshCanUpdateConfiguration() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        val waiting = CompletableDeferred<String?>()
        g.transport.reply = { waiting.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { c.refresh() }
        assertFalse(c.refresh())
        assertEquals(1, g.transport.requests.size)
        waiting.complete(response())
        assertTrue(first.await())
        g.transport.reply = { response("\"distanceOn\":0") }
        assertTrue(c.refresh())
        assertFalse((c.value().displayKind() as CarSettingKind.Toggle).checked)
    }

    @Test fun vinAtoBtoARejectsFirstResponseDespiteMatchingVinText() = runBlocking {
        var vin = "A"
        val g = FakeGateway(response())
        val c = controller(g, vin = { vin })
        val waiting = CompletableDeferred<String?>()
        g.transport.reply = { waiting.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { c.refresh() }
        vin = "B"
        c.onVinChanged(vin)
        assertNull(c.value().displayKind())
        g.transport.reply = { response("\"distanceOn\":0") }
        c.refresh()
        vin = "A"
        c.onVinChanged(vin)
        c.refresh()
        val latest = c.state.value
        waiting.complete(response())
        assertFalse(first.await())
        assertEquals(latest, c.state.value)
    }

    @Test fun replacementControllerDoesNotReuseAnotherModelsCloudState() = runBlocking {
        val g = FakeGateway(response())
        val old = controller(g)
        old.refresh()
        val replacement = controller(g)
        assertNull(replacement.value().configuredValue)
        g.transport.body = response("", listOf("47"))
        replacement.refresh()
        assertFalse(replacement.state.value.items.any { it.configId == "10" })
        assertNull(replacement.value("cloud:47").displayKind())
        assertNotNull(old.value().configuredValue)
    }

    @Test fun cancelledRefreshPropagatesWithoutClearingConfigurationOrLeavingLoading() = runBlocking {
        val g = FakeGateway(response())
        val c = controller(g)
        c.refresh()
        val before = c.value()
        g.transport.reply = { CompletableDeferred<String?>().await() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.refresh() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertFalse(c.state.value.loading)
        assertEquals(before, c.value())
    }

    @Test fun listAndEditorShowCloudSourceRatherThanCallingAKnownConfigurationUnknown() = runBlocking {
        val c = controller(FakeGateway(response()))
        c.refresh()
        for (item in c.state.value.items.filter { it.configId == "10" }) {
            val summary = carSettingSummary(item, c.value(item.id), false, null)
            assertTrue(summary.contains("云端配置"))
            assertTrue(summary.contains("非车辆回读"))
            assertFalse(summary.contains("UNKNOWN"))
            assertFalse(summary.contains("云端车辆设置项"))
        }
    }

    @Test fun readOnlyMetadataDoesNotRemoveOrInvalidateKnownCloudConfiguration() = runBlocking {
        val body = """{"success":true,"data":{"distanceOn":1,"distance":200,"bikeConfigVos":[{"confId":"10","isAuth":false}]}}"""
        val c = controller(FakeGateway(body))
        c.refresh()
        val fenceItems = c.state.value.items.filter { it.configId == "10" }
        assertEquals(2, fenceItems.size)
        assertTrue(fenceItems.all { !it.writable })
        assertTrue((c.value().displayKind() as CarSettingKind.Toggle).checked)
    }

    @Test fun suppliedPasswordFlagIsNotDroppedByTheGatewayScalarWhitelist() = runBlocking {
        val c = controller(FakeGateway(response("\"passwordUnlockEnable\":1", listOf("25"))))
        c.refresh()
        assertTrue((c.value("cloud:25").configuredValue as CarSettingKind.Toggle).checked)
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:25").vehicleValue)
    }

    @Test fun unknownNumericEditorDoesNotPresentItsPlaceholderAsAKnownPosition() = runBlocking {
        val g = FakeGateway(response(""))
        val c = controller(g)
        c.refresh()
        fun label() = settingSliderValueLabel(c.value("cloud:10:distance").displayKind() as? CarSettingKind.Slider)
        assertEquals("状态未知", label())
        g.transport.body = response()
        c.refresh()
        assertEquals("200 m", label())
        c.editDraft("cloud:10:distance") { (it as CarSettingKind.Slider).copy(value = 300f) }
        assertEquals("300 m", label())
        assertEquals(200f, (c.value("cloud:10:distance").configuredValue as CarSettingKind.Slider).value)
    }

    @Test fun existingCloudBooleanMappingsDisplayBothExplicitStatesWithoutLiveTtp() = runBlocking {
        val fields = mapOf("9" to "alarmOn", "10" to "distanceOn",
            "13" to "alarmSoundEnable", "16" to "dumpAlarm",
            "24" to "emergencyContact", "25" to "passwordUnlockEnable",
            "40" to "colorEggSoundEffect", "47" to "chargeWattSwitch", "52" to "bleUnlockOn",
            "62" to "seatHeating",
            "75" to "tamperProofIotEnable", "76" to "positioningFunc", "77" to "sleepMode",
            "82" to "lowBatTelWarning", "91" to "sosEmergencyCall")
        for (raw in listOf(0, 1)) {
            val values = fields.values.distinct().joinToString(",") { "\"$it\":$raw" }
            val c = controller(FakeGateway(response(values, fields.keys.toList())))
            c.refresh()
            for (id in fields.keys) {
                val key = if (id == "47") "cloud:47:enable" else "cloud:$id"
                val checked = when (val kind = c.value(key).configuredValue) {
                    is CarSettingKind.LowPowerInfo -> kind.alarmOn
                    is CarSettingKind.LowPowerPhone -> kind.enabled
                    is CarSettingKind.Toggle -> kind.checked
                    else -> null
                }
                assertEquals(key, raw == 1, checked)
                assertEquals(VehicleSettingValue.Unknown, c.value(key).vehicleValue)
            }
            // A DTO key alone does not prove the ID7 page consumes it (329effd).
            val lock = controller(FakeGateway(response("\"bleLeavePowerOffEnable\":$raw", listOf("7"))))
            lock.refresh()
            assertNull(lock.value("cloud:7").configuredValue)
            assertNull(lock.value("cloud:7").displayKind())
            val bracket = controller(FakeGateway(response("\"bleApproachPowerOnEnable\":$raw", listOf("53"))))
            bracket.refresh()
            assertNull(bracket.value("cloud:53").configuredValue)
            assertNull(bracket.value("cloud:53").displayKind())
            assertNull(CloudCarSettingUpdateBuilder.build("53", CarSettingKind.Toggle(raw == 1), "VIN"))
            for (id in listOf("15", "59")) {
                val timer = controller(FakeGateway(response("\"vehiclePowerDownStatus\":$raw,\"shutDownTime\":3", listOf(id))))
                timer.refresh()
                assertNull(timer.value("cloud:$id").configuredValue)
                assertNull(timer.value("cloud:$id:time").configuredValue)
                assertNull(timer.value("cloud:$id").displayKind())
            }
        }
    }
}
