package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.*

/** Only in-memory gateways, writers and packets. No production transport or credentials. */
class SettingsCacheRecoveryTest {
    private val cloud = CloudCarSettings(listOf(CloudBikeConfig("14"), CloudBikeConfig("13")),
        mapOf("alarmSoundEnable" to "1"))
    private fun info(raw: Int) = TtpInfoParser.parse(ByteArray(256) { -1 }).let {
        it.copy(extended = it.extended.copy(alarmSensitivityStatus = raw))
    }
    private fun cached(vin: String = "A", raw: Int = 1, sequence: Long = 1) =
        VehicleStatusObservation(vin, info(raw), VehicleStatusOrigin.CACHED_TTP, sequence)
    private class Gateway(var result: CloudCarSettings?) : CarSettingsGateway {
        var wait: CompletableDeferred<CloudCarSettings?>? = null
        override suspend fun load(vin: String) = wait?.await() ?: result
    }
    private val writer = object : CarSettingWriter {
        override suspend fun write(item: CarSettingItem) = true
        override suspend fun writeWithResult(item: CarSettingItem) =
            CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
    }

    @Test fun cacheRestoresBeforeCloudRefreshReturnsWithoutCreatingVehicleValue() = runBlocking {
        val g = Gateway(cloud)
        val c = CarSettingsController(CloudCarSettingMapper.toItems(cloud), writer, g, { "A" }, 0)
        c.updateVehicleObservation(cached())
        val value = c.state.value.valueState("cloud:14")
        assertEquals(50f, (value.displayKind() as CarSettingKind.Slider).value)
        assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
        assertEquals(SettingValueSource.CACHED_TTP, value.cachedValue!!.source)
        g.wait = CompletableDeferred()
        val refresh = async { c.refresh() }
        yield()
        assertTrue(c.state.value.loading)
        g.wait!!.complete(cloud)
        assertTrue(refresh.await())
        assertEquals(50f, (c.state.value.valueState("cloud:14").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun initialCacheWaitsForDynamicManifestButIsNotDropped() = runBlocking {
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        c.onVinChanged("A")
        c.updateVehicleObservation(cached())
        assertTrue(c.state.value.items.isEmpty()) // No invented offline configuration list.
        c.refresh()
        assertEquals(50f, (c.state.value.valueState("cloud:14").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun sameVinRefreshKeepsManifestAndCacheWhileWaitingAndAfterFailure() = runBlocking {
        val g = Gateway(cloud)
        val c = CarSettingsController(writer = writer, gateway = g, vinProvider = { "A" })
        c.refresh()
        c.updateVehicleObservation(cached())
        val items = c.state.value.items
        g.wait = CompletableDeferred()
        val refresh = async { c.refresh() }
        yield()
        assertEquals(items, c.state.value.items)
        assertNotNull(c.state.value.valueState("cloud:14").cachedValue)
        g.result = null
        g.wait!!.complete(null)
        assertFalse(refresh.await())
        assertEquals(items, c.state.value.items)
        assertTrue(c.state.value.loadedFromCloud)
        assertFalse(c.state.value.loading)
    }

    @Test fun refreshReplacesDynamicManifestWithoutFilteringUnknownOrReadOnlyItems() = runBlocking {
        val g = Gateway(cloud)
        val c = CarSettingsController(writer = writer, gateway = g, vinProvider = { "A" })
        c.refresh()
        g.result = CloudCarSettings(listOf(CloudBikeConfig("64", isAuth = false), CloudBikeConfig("89")), emptyMap())
        c.refresh()
        assertFalse(c.state.value.items.any { it.configId == "14" })
        assertTrue(c.state.value.items.any { it.configId == "64" && !it.writable })
        assertTrue(c.state.value.items.any { it.configId == "89" && it.writable })
    }

    @Test fun vinSwitchRejectsOldCacheAndClearsDisplay() = runBlocking {
        var vin = "A"
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { vin })
        c.refresh()
        c.updateVehicleObservation(cached())
        vin = "B"
        c.onVinChanged(vin)
        c.refresh()
        c.updateVehicleObservation(cached(sequence = 20))
        assertNull(c.state.value.valueState("cloud:14").displayKind())
    }

    @Test fun replacementControllerDoesNotInheritPreviousModelMemory() = runBlocking {
        val first = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        first.refresh()
        first.updateVehicleObservation(cached())
        val second = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        second.refresh()
        assertNull(second.state.value.valueState("cloud:14").cachedValue)
    }

    @Test fun liveValueWinsOverCacheAndDraftSurvivesBoth() = runBlocking {
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        c.refresh()
        c.updateVehicleObservation(cached())
        c.editDraft("cloud:14") { (it as CarSettingKind.Slider).copy(value = 100f) }
        c.updateVehicleObservation(VehicleStatusObservation("A", info(0), VehicleStatusOrigin.LIVE_TTP, 2))
        val v = c.state.value.valueState("cloud:14")
        assertEquals(100f, (v.displayKind() as CarSettingKind.Slider).value)
        assertEquals(0f, ((v.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        c.cancelDraft("cloud:14")
        assertEquals(0f, (c.state.value.valueState("cloud:14").displayKind() as CarSettingKind.Slider).value)
    }

    @Test fun cachedMatchCannotConfirmSubmittedTarget() = runBlocking {
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" }, debounceMs = 0)
        c.refresh()
        c.update("cloud:14") { (it as CarSettingKind.Slider).copy(value = 50f) }
        c.updateVehicleObservation(cached())
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState("cloud:14").confirmationState)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:14").vehicleValue)
    }

    @Test fun missingCacheFieldNeverBecomesDefaultFalseOrDefaultSlider() = runBlocking {
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        c.refresh()
        c.updateVehicleObservation(cached(raw = -1))
        assertNull(c.state.value.valueState("cloud:14").displayKind())
        assertNull(c.state.value.valueState("cloud:14").cachedValue)
    }

    @Test fun cloudToggleRestoresDisplayWithoutMasqueradingAsTtp() = runBlocking {
        val c = CarSettingsController(writer = writer, gateway = Gateway(cloud), vinProvider = { "A" })
        c.refresh()
        val value = c.state.value.valueState("cloud:13")
        assertTrue((value.displayKind() as CarSettingKind.Toggle).checked)
        assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
        assertEquals(SettingConfirmationState.UNKNOWN, value.confirmationState)
    }
}
