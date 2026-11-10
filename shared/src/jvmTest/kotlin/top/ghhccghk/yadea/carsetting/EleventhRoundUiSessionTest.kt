package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.carSettingsModelKey
import top.ghhccghk.yadea.ui.carSettingsUiSessionKey
import top.ghhccghk.yadea.vehicle.VehicleProfile

/** Tests session policy and controller boundaries, not a running Compose composition. */
class EleventhRoundUiSessionTest {
    private val cloud = CloudCarSettings(
        listOf("14", "64", "89").map { CloudBikeConfig(confId = it) }, emptyMap(),
    )
    private val gateway = object : CarSettingsGateway {
        override suspend fun load(vin: String) = cloud
    }

    @Test fun sameVinModelChangesInvalidateOrdinarySettingsIdentity() {
        val profile = VehicleProfile(vin = "VIN-A", bikeModelId = 1, modelNo = "M1", seriesCode = "S1")
        val key = carSettingsModelKey(profile)
        assertEquals(key, carSettingsModelKey(profile.copy(bikeNickName = "local name")))
        assertNotEquals(key, carSettingsModelKey(profile.copy(bikeModelId = 2)))
        assertNotEquals(key, carSettingsModelKey(profile.copy(modelNo = "M2")))
        assertNotEquals(key, carSettingsModelKey(profile.copy(seriesCode = "S2")))
        assertNotEquals(key, carSettingsModelKey(null))
    }

    @Test fun controllerReplacementResetsUiEvenWhenVinAndGenerationAreIdentical() {
        val first = CarSettingsController(writer = null)
        val second = CarSettingsController(writer = null)
        val state = CarSettingsState(vin = "VIN-A", loadGeneration = 2, loadedFromCloud = true)
        assertEquals(carSettingsUiSessionKey(first, state), carSettingsUiSessionKey(first, state.copy()))
        assertNotEquals(carSettingsUiSessionKey(first, state), carSettingsUiSessionKey(second, state))
        assertNotEquals(carSettingsUiSessionKey(first, state), carSettingsUiSessionKey(first, state.copy(loadGeneration = 3)))
    }

    @Test fun vinAtoBtoAOldResultAndFinallyCannotClearNewOperation() = runBlocking {
        for (configId in listOf("64", "89")) {
            var vin = "VIN-A"
            val responses = listOf(CompletableDeferred<Boolean>(), CompletableDeferred<Boolean>())
            var calls = 0
            val writer = object : CarSettingWriter {
                override suspend fun write(item: CarSettingItem) = responses[calls++].await()
            }
            val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 0)
            c.refresh()
            val id = "cloud:$configId:adaptive"
            val old = launch(start = CoroutineStart.UNDISPATCHED) { assertFalse(c.update(id) { CarSettingKind.Toggle(true) }) }
            val oldKey = carSettingsUiSessionKey(c, c.state.value)
            vin = "VIN-B"
            c.onVinChanged(vin)
            c.refresh()
            vin = "VIN-A"
            c.onVinChanged(vin)
            c.refresh()
            assertNotEquals(oldKey, carSettingsUiSessionKey(c, c.state.value))
            val current = launch(start = CoroutineStart.UNDISPATCHED) { c.update(id) { CarSettingKind.Toggle(false) } }
            val pending = c.state.value
            responses[0].complete(false)
            old.join()
            assertEquals(pending, c.state.value)
            assertTrue(id in c.state.value.pendingIds)
            responses[1].complete(true)
            current.join()
            assertTrue(c.state.value.pendingIds.isEmpty())
            assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(id).vehicleValue)
        }
    }

    @Test fun cancelledOldFinallyCannotClearNewSameVinOperationAfterReload() = runBlocking {
        val oldResponse = CompletableDeferred<Boolean>()
        val newResponse = CompletableDeferred<Boolean>()
        var calls = 0
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem): Boolean = if (calls++ == 0) {
                withContext(NonCancellable) { oldResponse.await() }
            } else newResponse.await()
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0)
        c.refresh()
        val id = "cloud:89:adaptive"
        val old = launch(start = CoroutineStart.UNDISPATCHED) { c.update(id) { CarSettingKind.Toggle(true) } }
        old.cancel()
        c.refresh()
        val current = launch(start = CoroutineStart.UNDISPATCHED) { c.update(id) { CarSettingKind.Toggle(false) } }
        val pending = c.state.value
        oldResponse.complete(true)
        old.join()
        assertEquals(pending, c.state.value)
        newResponse.complete(false)
        current.join()
        assertEquals(SettingWriteState.FAILED, c.state.value.valueState(id).writeState)
    }

    @Test fun cancellingUiSubmissionPropagatesAndDoesNotLeaveLoadingOrBusinessFailure() = runBlocking {
        val waiting = CompletableDeferred<Boolean>()
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = waiting.await()
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0)
        c.refresh()
        var propagated = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try { c.update("cloud:64:adaptive") { CarSettingKind.Toggle(true) } }
            catch (cancelled: CancellationException) { propagated = true; throw cancelled }
        }
        job.cancelAndJoin()
        assertTrue(propagated)
        assertTrue(c.state.value.pendingIds.isEmpty())
        val value = c.state.value.valueState("cloud:64:adaptive")
        assertEquals(SettingWriteState.CANCELLED, value.writeState)
        assertNull(value.error)
        assertEquals(VehicleSettingValue.Unknown, value.vehicleValue)
    }

    @Test fun lateModelControllerResponseDoesNotEnterReplacementController() = runBlocking {
        val waiting = CompletableDeferred<Boolean>()
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = waiting.await()
        }
        val old = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0)
        old.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { old.update("cloud:64:adaptive") { CarSettingKind.Toggle(true) } }
        val replacement = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" })
        replacement.refresh()
        val untouched = replacement.state.value
        waiting.complete(true)
        job.join()
        assertEquals(untouched, replacement.state.value)
        assertNotEquals(carSettingsUiSessionKey(old, old.state.value), carSettingsUiSessionKey(replacement, untouched))
    }

    @Test fun retainedDraftOnViewReentryIsNotAnObservationAndExplicitCancelClearsIt() = runBlocking {
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" })
        c.refresh()
        val key = carSettingsUiSessionKey(c, c.state.value)
        assertTrue(c.editDraft("cloud:14") { (it as CarSettingKind.Slider).copy(value = 100f) })
        assertEquals(key, carSettingsUiSessionKey(c, c.state.value))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:14").vehicleValue)
        assertNotNull(c.state.value.valueState("cloud:14").draftValue)
        c.cancelDraft("cloud:14")
        assertNull(c.state.value.valueState("cloud:14").draftValue)
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState("cloud:14").vehicleValue)
    }
}
