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
import top.ghhccghk.yadea.vehicle.TtpInfoParser
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

/** No real transport, sink, account or vehicle is used. */
class HibernateLinkedWriteTest {
    private class Fixture {
        var vin = "VIN-A"
        val stages = mutableListOf<String>()
        var send: suspend () -> Boolean = { true }
        var metadata: suspend () -> Boolean = { true }
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(listOf(CloudBikeConfig("77")), mapOf("sleepMode" to "0"))
            override suspend fun loadHibernateAuthorization(vin: String) = HibernateAuthorization(vin)
            override suspend fun updateHibernateMetadata(vin: String, authorization: HibernateAuthorization): Boolean {
                assertEquals("VIN-A", vin); assertEquals(vin, authorization.vin); assertTrue(authorization.enterConfirmed)
                stages += "metadata"; return metadata()
            }
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = error("generic update bypass")
            override suspend fun updateHibernate(vin: String, update: CloudCarSettingUpdate, authorization: HibernateAuthorization): HibernateWriteReceipt = error("cloud command must not repeat BLE")
            override suspend fun checkStatus(configId: String): Boolean? = error("no guessed transaction polling")
        }
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean {
                assertEquals(VehicleSettingCommand("SLEEP_MODE_SWITCH", 0x133, 1), command)
                stages += "BLE"; return send()
            }
        })
        val writer = CloudCarSettingWriter(gateway, { vin }, mapped, { true })
        val controller = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 0)
        fun state() = controller.state.value.valueState("cloud:77")
        fun observe(raw: Int, seq: Long = 1) {
            val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
            controller.updateVehicleObservation(VehicleStatusObservation(vin,
                empty.copy(extended = empty.extended.copy(sleepModeSwitch = raw)), VehicleStatusOrigin.LIVE_TTP, seq))
        }
    }

    @Test fun linkedWriteUsesTheOriginalOrderAndNeverCallsTheCloudCommandOrPollsConfId() = runBlocking {
        val f = Fixture(); assertTrue(f.controller.refresh())
        assertTrue(f.controller.enterHibernate("cloud:77", true))
        assertEquals(listOf("BLE", "metadata"), f.stages)
        assertEquals(SettingWriteState.LOCAL_UPDATED, f.state().writeState)
        assertEquals(HibernateMetadataState.ACCEPTED, f.state().hibernateMetadata)
        assertEquals(CarSettingKind.Toggle(false), f.state().configuredValue)
        assertEquals(VehicleSettingValue.Unknown, f.state().vehicleValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, f.state().confirmationState)
        assertFalse(f.controller.enterHibernate("cloud:77", true)); assertEquals(2, f.stages.size)
        f.observe(1)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, f.state().confirmationState)
    }

    @Test fun metadataFailureOrExceptionRetainsBleReceiptAndDoesNotRetry() = runBlocking {
        for (throwing in listOf(false, true)) {
            val f = Fixture(); f.metadata = { if (throwing) error("fake metadata failure") else false }
            assertTrue(f.controller.refresh()); assertFalse(f.controller.enterHibernate("cloud:77", true))
            assertEquals(listOf("BLE", "metadata"), f.stages)
            assertEquals(SettingWriteState.LOCAL_UPDATED, f.state().writeState)
            assertEquals(HibernateMetadataState.FAILED, f.state().hibernateMetadata)
            assertNotNull(f.state().error); assertNotNull(f.state().submittedValue)
            assertEquals(SettingConfirmationState.WAITING_READBACK, f.state().confirmationState)
            assertFalse(f.controller.enterHibernate("cloud:77", true)); assertEquals(2, f.stages.size)
            f.observe(0)
            assertEquals(SettingConfirmationState.READBACK_MISMATCH, f.state().confirmationState)
            assertEquals(HibernateMetadataState.FAILED, f.state().hibernateMetadata)
            assertNull(f.state().submittedValue)
        }
    }

    @Test fun metadataCancellationPropagatesTheOriginalExceptionAndKeepsTheBleFact() = runBlocking {
        val f = Fixture(); val cancellation = CancellationException("fake second stage")
        f.metadata = { throw cancellation }; assertTrue(f.controller.refresh())
        try { f.controller.enterHibernate("cloud:77", true); fail("cancel swallowed") }
        catch (e: CancellationException) { assertSame(cancellation, e) }
        assertEquals(HibernateMetadataState.CANCELLED, f.state().hibernateMetadata)
        assertEquals(SettingWriteState.LOCAL_UPDATED, f.state().writeState)
        assertNotNull(f.state().error); assertNotNull(f.state().submittedValue)
        assertTrue(f.controller.state.value.pendingIds.isEmpty())
        f.observe(1); assertEquals(SettingConfirmationState.READBACK_MATCHED, f.state().confirmationState)
    }

    @Test fun failedOrCancelledBleStageNeverUpdatesMetadata() = runBlocking {
        for (cancel in listOf(false, true)) {
            val f = Fixture(); f.send = { if (cancel) throw CancellationException("fake first stage") else false }
            assertTrue(f.controller.refresh())
            try { assertFalse(f.controller.enterHibernate("cloud:77", true)); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(listOf("BLE"), f.stages)
            assertNull(f.state().hibernateMetadata); assertNull(f.state().submittedValue)
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, f.state().writeState)
        }
    }

    @Test fun vinChangeBetweenStagesCannotUpdateOrConfirmTheNewVehicle() = runBlocking {
        val f = Fixture(); val send = CompletableDeferred<Boolean>(); f.send = { send.await() }
        assertTrue(f.controller.refresh())
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.controller.enterHibernate("cloud:77", true) }
        f.vin = "VIN-B"; f.controller.onVinChanged(f.vin); assertTrue(f.controller.refresh())
        send.complete(true); job.join()
        assertEquals(listOf("BLE"), f.stages)
        assertNull(f.state().submittedValue); assertNull(f.state().hibernateMetadata)
        assertEquals(SettingWriteState.IDLE, f.state().writeState)
    }

    @Test fun refreshPreservesTheDraftAndCannotInterruptTheLinkedOperation() = runBlocking {
        val f = Fixture(); val metadata = CompletableDeferred<Boolean>(); f.metadata = { metadata.await() }
        assertTrue(f.controller.refresh())
        assertTrue(f.controller.editDraft("cloud:77") { CarSettingKind.Toggle(true) })
        assertTrue(f.controller.refresh()); assertEquals(CarSettingKind.Toggle(true), f.state().draftValue)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.controller.enterHibernate("cloud:77", true) }
        assertFalse(f.controller.refresh())
        assertFalse(f.controller.editDraft("cloud:77") { CarSettingKind.Toggle(true) })
        assertFalse(f.controller.enterHibernate("cloud:77", true))
        metadata.complete(true); job.join()
        assertNull(f.state().draftValue)
        assertEquals(CarSettingKind.Toggle(true), f.state().submittedValue)
        assertEquals(HibernateMetadataState.ACCEPTED, f.state().hibernateMetadata)
    }

    @Test fun ordinaryMappedEntryAndGenericFallbackCannotSendABareSleepCommand() = runBlocking {
        val f = Fixture(); assertTrue(f.controller.refresh())
        val item = f.controller.state.value.items.single { it.id == "cloud:77" }.copy(kind = CarSettingKind.Toggle(true),
            hibernateAuthorization = HibernateAuthorization("VIN-A", true))
        assertFalse(f.mapped.write(item)); assertNull(VehicleSettingCommands.forItem(item))
        var fallbackCalls = 0
        val generic = object : CarSettingWriter { override suspend fun write(item: CarSettingItem): Boolean { fallbackCalls++; return true } }
        assertFalse(CloudCarSettingWriter(f.gateway, { f.vin }, generic, { true }).write(item))
        assertEquals(0, fallbackCalls); assertTrue(f.stages.isEmpty())
    }

    @Test fun booleanWriterDoesNotCallPartialAcceptanceACompleteSuccess() = runBlocking {
        val f = Fixture(); assertTrue(f.controller.refresh()); f.metadata = { false }
        val item = f.controller.state.value.items.single { it.id == "cloud:77" }.copy(kind = CarSettingKind.Toggle(true),
            hibernateAuthorization = HibernateAuthorization("VIN-A", true))
        assertFalse(f.writer.write(item))
        assertEquals(listOf("BLE", "metadata"), f.stages)
    }

    @Test fun existingFrameMatchesOriginalUjC6Value6Through7() {
        // generateBleCommand:1845 -> uj.c(6,1,6,7): FF + 7F + 24 FF bytes.
        val encoded = requireNotNull(VehicleSettingFrameEncoder.encode(VehicleSettingCommand("SLEEP_MODE_SWITCH", 0x133, 1)))
        val expectedPayload = "01000101C8FF7F" + "FF".repeat(24)
        val expected = top.ghhccghk.yadea.vehicle.VehicleFrame.build(top.ghhccghk.yadea.vehicle.VehicleFrame.ORDER_ID, expectedPayload)!!
        assertEquals(expected.uppercase(), encoded.joinToString("") { "%02X".format(it) })
        for (raw in listOf(-1, 0, 2, 255)) {
            try { VehicleSettingCommand("SLEEP_MODE_SWITCH", 0x133, raw); fail("invalid raw parameter accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun realGatewayUsesBikeSettingMetadataOnlyAndStrictResponseWithNoRealTransport() = runBlocking {
        val requests = mutableListOf<HttpRequest>(); var body = """{"success":true,"data":true}"""
        val g = YadeaCarSettingsGateway(object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse { requests += request; return HttpResponse(200, body) }
        }, { "fake-token" }, timestampProvider = { 1_700_000_000_000L }, baseUrl = "https://example.invalid/")
        assertFalse(g.updateHibernateMetadata("VIN-A", HibernateAuthorization("VIN-B", true)))
        assertFalse(g.updateHibernateMetadata("VIN-A", HibernateAuthorization("VIN-A")))
        assertTrue(requests.isEmpty())
        assertTrue(g.updateHibernateMetadata("VIN-A", HibernateAuthorization("VIN-A", true)))
        assertTrue(requests.single().url.endsWith(YadeaCarSettingsGateway.UPDATE_PATH))
        assertEquals(mapOf("_gateway" to "True", "vin" to "VIN-A", "sleepMode" to 1), HibernateSettings.metadataUpdate("VIN-A").fields)
        for (invalid in listOf("{}", """{"success":false}""", """{"success":true,"data":false}""")) {
            body = invalid; assertFalse(g.updateHibernateMetadata("VIN-A", HibernateAuthorization("VIN-A", true)))
        }
    }
}
