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

/** All responses, clocks, VINs, transport and Writer instances are fake/in-memory. */
class AutoHibernateSettingsTest {
    private val id = AutoHibernateSettings.ID
    private val enable = CarSettingKind.AutoHibernate(true, 7, CarSettingKind.AutoHibernateEdit.ENABLE)
    private class Fixture {
        var vin = "VIN-A"
        var cloud = CloudCarSettings(listOf(CloudBikeConfig("77")), mapOf("sleepMode" to "0"))
        var configured: CarSettingKind.AutoHibernate? = CarSettingKind.AutoHibernate(false, 3)
        var load: (suspend () -> CarSettingKind.AutoHibernate?)? = null
        var authorize: suspend (String) -> HibernateAuthorization? = { HibernateAuthorization(it) }
        var save: suspend (CarSettingKind.AutoHibernate) -> Boolean = { configured = it.copy(edit = null); true }
        val writes = mutableListOf<CarSettingKind.AutoHibernate>()
        var loads = 0
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud
            override suspend fun loadAutoHibernate(vin: String): CarSettingKind.AutoHibernate? {
                loads++; val active = load; return if (active != null) active() else configured
            }
            override suspend fun loadHibernateAuthorization(vin: String) = authorize(vin)
            override suspend fun updateAutoHibernate(vin: String, target: CarSettingKind.AutoHibernate, authorization: HibernateAuthorization): Boolean {
                assertEquals("VIN-A", vin); assertEquals(vin, authorization.vin)
                writes += target; return save(target)
            }
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = error("no generic update")
            override suspend fun checkStatus(configId: String): Boolean? = error("no confId polling for appointments")
        }
        val writer = CloudCarSettingWriter(gateway, { vin }, bleAvailableProvider = { true })
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 0)
        fun state() = c.state.value.valueState(AutoHibernateSettings.ID)
        fun session() = AutoHibernateSession(c.state.value.vin, c.state.value.loadGeneration)
        fun observe(raw: Int, origin: VehicleStatusOrigin = VehicleStatusOrigin.LIVE_TTP) {
            val empty = TtpInfoParser.parse(ByteArray(256) { -1 })
            c.updateVehicleObservation(VehicleStatusObservation(vin, empty.copy(extended = empty.extended.copy(sleepModeSwitch = raw)), origin, 1))
        }
    }

    @Test fun configurationIsIndependentOfImmediateModeAndTtpNeverConfirmsAnAppointment() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh())
        assertEquals(CarSettingKind.Toggle(false), f.c.state.value.valueState("cloud:77").configuredValue)
        assertEquals(CarSettingKind.AutoHibernate(false, 3), f.state().configuredValue)
        assertEquals(VehicleSettingValue.Unknown, f.state().vehicleValue)
        f.observe(1)
        assertEquals(VehicleSettingValue.Unknown, f.state().vehicleValue); assertNull(f.state().cachedValue)
        assertEquals(CarSettingKind.AutoHibernate(false, 3), f.state().configuredValue)
    }

    @Test fun mapperDoesNotCreateAChildWithoutTheIssuedParentAndInheritsPermission() = runBlocking {
        val f = Fixture(); f.cloud = f.cloud.copy(configs = emptyList()); assertTrue(f.c.refresh())
        assertFalse(f.c.state.value.items.any { it.id == id }); assertEquals(0, f.loads)
        f.cloud = f.cloud.copy(configs = listOf(CloudBikeConfig("77", isAuth = false)))
        assertTrue(f.c.refresh()); assertFalse(f.c.state.value.items.single { it.id == id }.writable)
        assertFalse(f.c.updateAutoHibernate(f.session(), enable)); assertTrue(f.writes.isEmpty())
    }

    @Test fun rawMissingIllegalAndOutOfRangeValuesStayUnknownIndependently() {
        for (raw in listOf("null", "2", "-1", "0.5", "true", "{}", "\"bad\"")) {
            assertNull(AutoHibernateSettings.parse("""{"autoSleepSwitch":$raw,"dayCount":5}""").enabled)
        }
        for (raw in listOf("null", "0", "31", "-1", "1.5", "{}", "true")) {
            val parsed = AutoHibernateSettings.parse("""{"autoSleepSwitch":1,"dayCount":$raw}""")
            assertEquals(true, parsed.enabled); assertNull(parsed.dayCount)
        }
        assertEquals(CarSettingKind.AutoHibernate(), AutoHibernateSettings.parse("{}"))
        assertEquals(CarSettingKind.AutoHibernate(), AutoHibernateSettings.parse("""{"nested":{"autoSleepSwitch":1,"dayCount":5}}"""))
    }

    @Test fun enablingRequiresExplicitDaysAndRefreshesOnlyCloudConfiguration() = runBlocking {
        val f = Fixture(); f.configured = CarSettingKind.AutoHibernate(false, null); assertTrue(f.c.refresh())
        assertFalse(f.c.updateAutoHibernate(f.session(), enable.copy(dayCount = null)))
        assertTrue(f.c.updateAutoHibernate(f.session(), enable)); assertEquals(listOf(enable), f.writes)
        assertEquals(2, f.loads); assertEquals(enable.copy(edit = null), f.state().configuredValue)
        assertNull(f.state().submittedValue); assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.state().writeState)
        assertEquals(SettingConfirmationState.UNKNOWN, f.state().confirmationState)
        assertEquals(VehicleSettingValue.Unknown, f.state().vehicleValue)
    }

    @Test fun disablingUsesTheOriginalExplicitDayOneAndDoesNotSendAnUnknownSibling() = runBlocking {
        val f = Fixture(); f.configured = CarSettingKind.AutoHibernate(true, null); assertTrue(f.c.refresh())
        val close = CarSettingKind.AutoHibernate(false, 1, CarSettingKind.AutoHibernateEdit.DISABLE)
        assertFalse(f.c.updateAutoHibernate(f.session(), close.copy(dayCount = null)))
        assertFalse(f.c.updateAutoHibernate(f.session(), close.copy(dayCount = 8)))
        assertTrue(f.c.updateAutoHibernate(f.session(), close)); assertEquals(listOf(close), f.writes)
    }

    @Test fun modifyingDaysRequiresEnabledConfigurationAndKeepsTheSwitchTrue() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh())
        val target = CarSettingKind.AutoHibernate(true, 30, CarSettingKind.AutoHibernateEdit.DAYS)
        assertFalse(f.c.updateAutoHibernate(f.session(), target))
        f.configured = CarSettingKind.AutoHibernate(true, 5); assertTrue(f.c.refresh())
        assertTrue(f.c.updateAutoHibernate(f.session(), target)); assertEquals(listOf(target), f.writes)
    }

    @Test fun unknownCurrentSwitchAndImmediateStateCannotBeOverwritten() = runBlocking {
        val f = Fixture(); f.configured = CarSettingKind.AutoHibernate(null, 8); assertTrue(f.c.refresh())
        assertFalse(f.c.updateAutoHibernate(f.session(), enable))
        f.configured = CarSettingKind.AutoHibernate(false, 8)
        for (raw in listOf("1", "2", "")) {
            f.cloud = f.cloud.copy(values = mapOf("sleepMode" to raw)); assertTrue(f.c.refresh())
            assertFalse(f.c.updateAutoHibernate(f.session(), enable))
        }
        f.cloud = f.cloud.copy(values = emptyMap()); assertTrue(f.c.refresh()); f.observe(0, VehicleStatusOrigin.CACHED_TTP)
        assertFalse(f.c.updateAutoHibernate(f.session(), enable)); assertTrue(f.writes.isEmpty())
    }

    @Test fun illegalTargetsCannotPassControllerBuilderMappedWriterOrActualCloudWriter() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh())
        val base = f.c.state.value.items.single { it.id == id }
        val mapped = MappedCarSettingWriter(object : VehicleSettingCommandSink {
            override suspend fun send(command: VehicleSettingCommand): Boolean = error("no BLE schedule command")
        })
        for (target in listOf(enable.copy(dayCount = 0), enable.copy(dayCount = 31), enable.copy(dayCount = null),
            enable.copy(edit = null), enable.copy(enabled = null))) {
            assertFalse(f.c.updateAutoHibernate(f.session(), target))
            assertNull(CloudCarSettingUpdateBuilder.build("77", target, "VIN-A"))
            val item = base.copy(kind = target, hibernateAuthorization = HibernateAuthorization("VIN-A"))
            assertFalse(f.writer.write(item)); assertFalse(mapped.write(item))
        }
        assertFalse(f.writer.write(base.copy(kind = enable)))
        assertFalse(f.writer.write(base.copy(id = "cloud:77", kind = enable, hibernateAuthorization = HibernateAuthorization("VIN-A"))))
        assertTrue(f.writes.isEmpty())
    }

    @Test fun failureAndCancellationDoNotBecomeCloudAcceptanceOrEraseTheConfiguration() = runBlocking {
        for (cancel in listOf(false, true)) {
            val f = Fixture(); assertTrue(f.c.refresh())
            f.save = { if (cancel) throw CancellationException("fake save") else false }
            try { assertFalse(f.c.updateAutoHibernate(f.session(), enable)); assertFalse(cancel) }
            catch (_: CancellationException) { assertTrue(cancel) }
            assertEquals(if (cancel) SettingWriteState.CANCELLED else SettingWriteState.FAILED, f.state().writeState)
            assertEquals(CarSettingKind.AutoHibernate(false, 3), f.state().configuredValue); assertNull(f.state().submittedValue)
            assertEquals(VehicleSettingValue.Unknown, f.state().vehicleValue)
        }
    }

    @Test fun failedOrMismatchedRefreshKeepsAcceptanceSeparateAndNeverConfirmsTheVehicle() = runBlocking {
        for (missing in listOf(false, true)) {
            val f = Fixture(); assertTrue(f.c.refresh()); f.save = { true }
            if (missing) f.configured = null
            assertFalse(f.c.updateAutoHibernate(f.session(), enable))
            assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.state().writeState)
            assertNotNull(f.state().error); assertEquals(SettingConfirmationState.UNKNOWN, f.state().confirmationState)
            assertEquals(missing, f.state().submittedValue != null)
        }
    }

    @Test fun cancellingConfigurationRereadKeepsAcceptedReceiptAndPropagates() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh()); f.save = { f.load = { throw CancellationException("fake reread") }; true }
        try { f.c.updateAutoHibernate(f.session(), enable); fail("cancel swallowed") } catch (_: CancellationException) { }
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.state().writeState)
        assertNotNull(f.state().submittedValue); assertEquals(SettingConfirmationState.UNKNOWN, f.state().confirmationState)
    }

    @Test fun staleSessionsVinChangesAndOldLoadsCannotWriteOrOverwriteTheNewVehicle() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh()); val old = f.session(); assertTrue(f.c.refresh())
        assertFalse(f.c.updateAutoHibernate(old, enable))
        val result = CompletableDeferred<CarSettingKind.AutoHibernate?>(); f.load = { result.await() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.c.refresh() }
        f.vin = "VIN-B"; f.c.onVinChanged(f.vin); f.load = null; f.configured = CarSettingKind.AutoHibernate(true, 10)
        assertTrue(f.c.refresh()); result.complete(CarSettingKind.AutoHibernate(false, 2)); job.join()
        assertEquals(CarSettingKind.AutoHibernate(true, 10), f.state().configuredValue)
        assertFalse(f.c.updateAutoHibernate(old, enable)); assertTrue(f.writes.isEmpty())
    }

    @Test fun authorizationWaitRechecksVinAndImmediateMask() = runBlocking {
        for (changeVin in listOf(false, true)) {
            val f = Fixture(); assertTrue(f.c.refresh()); val proof = CompletableDeferred<HibernateAuthorization?>(); f.authorize = { proof.await() }
            val job = launch(start = CoroutineStart.UNDISPATCHED) { f.c.updateAutoHibernate(f.session(), enable) }
            if (changeVin) { f.vin = "VIN-B"; f.c.onVinChanged(f.vin); assertTrue(f.c.refresh()) }
            else f.observe(1)
            proof.complete(HibernateAuthorization("VIN-A")); job.join()
            assertTrue(f.writes.isEmpty()); assertNull(f.state().submittedValue)
        }
    }

    @Test fun draftSurvivesRefreshButBusyMutationsAndImmediateEntryAreRejected() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh()); assertTrue(f.c.editDraft(id) { enable }); assertTrue(f.c.refresh())
        assertEquals(enable, f.state().draftValue)
        val save = CompletableDeferred<Boolean>(); f.save = { save.await() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.c.updateAutoHibernate(f.session(), enable) }
        assertFalse(f.c.refresh()); assertFalse(f.c.editDraft(id) { enable }); assertFalse(f.c.enterHibernate("cloud:77", true))
        save.complete(false); job.join()
        assertEquals(CarSettingKind.AutoHibernate(false, 3), f.state().configuredValue)
    }

    @Test fun actualGatewayUsesTheOriginalSeparateEndpointsAndExactIntegerPair() = runBlocking {
        val requests = mutableListOf<HttpRequest>(); var body = """{"success":true,"data":{"autoSleepSwitch":0,"dayCount":12}}"""
        val gateway = YadeaCarSettingsGateway(object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse { requests += request; return HttpResponse(200, body) }
        }, { "fake-token" }, timestampProvider = { 1_700_000_000_000L }, baseUrl = "https://example.invalid/")
        assertEquals(CarSettingKind.AutoHibernate(false, 12), gateway.loadAutoHibernate("VIN-A"))
        assertEquals("GET", requests.single().method); assertTrue(requests.single().url.contains(AutoHibernateSettings.GET_PATH + "?"))
        assertFalse(gateway.updateAutoHibernate("VIN-A", enable, HibernateAuthorization("VIN-B"))); assertEquals(1, requests.size)
        body = """{"success":true,"data":"accepted"}"""
        assertTrue(gateway.updateAutoHibernate("VIN-A", enable, HibernateAuthorization("VIN-A")))
        val request = requests.last(); assertTrue(request.url.endsWith(AutoHibernateSettings.UPDATE_PATH)); assertEquals("POST", request.method)
        val data = request.body!!.split('&').single { it.startsWith("gatewaySign=") }.substringAfter('=')
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, Crypto.urlDecode(data))!!.substringAfter("&json=")
        assertEquals("{\"_gateway\":\"True\",\"autoSleepSwitch\":1,\"dayCount\":7,\"vin\":\"VIN-A\"}", plain)
        for (invalid in listOf("{}", """{"success":false}""", """{"success":true,"data":false}""")) {
            body = invalid; assertFalse(gateway.updateAutoHibernate("VIN-A", enable, HibernateAuthorization("VIN-A")))
        }
    }

    @Test fun configurationReadFailureDoesNotInventDefaultsOrDiscardTheAcceptedTarget() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh()); f.save = { f.load = { CarSettingKind.AutoHibernate() }; true }
        assertFalse(f.c.updateAutoHibernate(f.session(), enable)); assertNotNull(f.state().submittedValue)
        assertTrue(f.c.refresh()); assertNotNull(f.state().submittedValue)
        assertEquals(CarSettingKind.AutoHibernate(), f.state().configuredValue)
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, f.state().writeState)
        assertFalse(f.c.updateAutoHibernate(f.session(), enable)); assertEquals(1, f.writes.size)
    }

    @Test fun lateSaveAndRereadDoNotApplyOldVinStateToTheNewVehicle() = runBlocking {
        val f = Fixture(); assertTrue(f.c.refresh()); val saved = CompletableDeferred<Boolean>(); f.save = { saved.await() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.c.updateAutoHibernate(f.session(), enable) }
        f.vin = "VIN-B"; f.c.onVinChanged(f.vin); f.configured = CarSettingKind.AutoHibernate(true, 21); assertTrue(f.c.refresh())
        saved.complete(true); job.join()
        assertEquals(CarSettingKind.AutoHibernate(true, 21), f.state().configuredValue)
        assertNull(f.state().submittedValue); assertEquals(SettingWriteState.IDLE, f.state().writeState)
    }
}
