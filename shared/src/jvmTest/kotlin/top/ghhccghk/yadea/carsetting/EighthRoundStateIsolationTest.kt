package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EighthRoundStateIsolationTest {
    private val cloud = CloudCarSettings(
        configs = listOf(CloudBikeConfig(confId = "2", confName = "test")),
        values = emptyMap(),
    )

    private val gateway = object : CarSettingsGateway {
        override suspend fun load(vin: String) = cloud
    }

    private class DeferredWriter : CarSettingWriter {
        val result = CompletableDeferred<Boolean>()
        var calls = 0

        override suspend fun write(item: CarSettingItem): Boolean {
            calls++
            return result.await()
        }
    }

    @Test
    fun oldVinWriteResultDoesNotChangeNewVehicle() = runBlocking {
        for (success in listOf(true, false)) {
            var vin = "VIN-A"
            val writer = DeferredWriter()
            val controller = CarSettingsController(
                writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 0,
            )
            controller.refresh()
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                assertFalse(controller.update("cloud:2") { CarSettingKind.Toggle(true) })
            }
            assertEquals(1, writer.calls)
            vin = "VIN-B"
            controller.onVinChanged(vin)
            controller.refresh()
            val newVehicle = controller.state.value
            writer.result.complete(success)
            job.join()
            assertEquals(newVehicle, controller.state.value)
        }
    }

    @Test
    fun vehicleSwitchDuringDebounceDoesNotInvokeWriter() = runBlocking {
        var vin = "VIN-A"
        val writer = DeferredWriter()
        val controller = CarSettingsController(
            writer = writer, gateway = gateway, vinProvider = { vin }, debounceMs = 1,
        )
        controller.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(controller.update("cloud:2") { CarSettingKind.Toggle(true) })
        }
        vin = "VIN-B"
        controller.onVinChanged(vin)
        controller.refresh()
        job.join()
        assertEquals(0, writer.calls)
        assertEquals("VIN-B", controller.state.value.vin)
        assertTrue(controller.state.value.pendingIds.isEmpty())
    }

    @Test
    fun sameVinReloadInvalidatesOldWriteResult() = runBlocking {
        val writer = DeferredWriter()
        val controller = CarSettingsController(
            writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0,
        )
        controller.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(controller.update("cloud:2") { CarSettingKind.Toggle(true) })
        }
        controller.refresh()
        val reloaded = controller.state.value
        writer.result.complete(false)
        job.join()
        assertEquals(reloaded, controller.state.value)
    }

    @Test
    fun cancellationClearsPendingAndIsNotReportedAsWriteFailure() = runBlocking {
        val writer = DeferredWriter()
        val controller = CarSettingsController(
            writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0,
        )
        controller.refresh()
        val original = controller.state.value.items
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            controller.update("cloud:2") { CarSettingKind.Toggle(true) }
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(controller.state.value.pendingIds.isEmpty())
        assertEquals(original, controller.state.value.items)
        assertEquals(
            CarSettingConfirmationStatus.UNKNOWN,
            controller.state.value.confirmationStatuses["cloud:2"],
        )
    }

    @Test
    fun cancelledLoadCanBeRetried() = runBlocking {
        val response = CompletableDeferred<CloudCarSettings?>()
        var loads = 0
        val deferredGateway = object : CarSettingsGateway {
            override suspend fun load(vin: String): CloudCarSettings? {
                loads++
                return if (loads == 1) response.await() else cloud
            }
        }
        val controller = CarSettingsController(
            writer = null, gateway = deferredGateway, vinProvider = { "VIN-A" },
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) { controller.refresh() }
        assertTrue(controller.state.value.loading)
        job.cancelAndJoin()
        assertFalse(controller.state.value.loading)
        assertTrue(controller.refresh())
        assertEquals(2, loads)
    }

    @Test
    fun latePollCannotChangeNewVinAndCannotStartDuplicateWrite() = runBlocking {
        var vin = "VIN-A"
        val result = CompletableDeferred<Boolean?>()
        var writes = 0
        val pollGateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(
                listOf(CloudBikeConfig(confId = "10", confName = "test")), emptyMap(),
            )
            override suspend fun checkStatus(configId: String) = result.await()
        }
        val cloudWriter = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult {
                writes++
                return CarSettingWriteResult(true, CarSettingWriteChannel.CLOUD)
            }
        }
        val controller = CarSettingsController(
            writer = cloudWriter, gateway = pollGateway, vinProvider = { vin },
            debounceMs = 0, statusPollIntervalMs = 0,
        )
        controller.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(controller.update("cloud:10") { CarSettingKind.Toggle(true) })
        }
        assertFalse(controller.update("cloud:10") { CarSettingKind.Toggle(false) })
        assertEquals(1, writes)
        vin = "VIN-B"
        controller.onVinChanged(vin)
        controller.refresh()
        val newVehicle = controller.state.value
        result.complete(true)
        job.join()
        assertEquals(newVehicle, controller.state.value)
    }

    @Test
    fun changedVinDoesNotStartCloudFallbackAfterBleFailure() = runBlocking {
        var vin = "VIN-A"
        var cloudWrites = 0
        val deferredBle = DeferredWriter()
        val fakeGateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = cloud
            override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
                cloudWrites++
                return true
            }
        }
        val writer = CloudCarSettingWriter(fakeGateway, { vin }, deferredBle, { true })
        val item = CloudCarSettingMapper.toItems(
            CloudCarSettings(listOf(CloudBikeConfig(confId = "10")), emptyMap()),
        ).first { it.id == "cloud:10" }.copy(kind = CarSettingKind.Toggle(true))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(writer.write(item))
        }
        assertEquals(1, deferredBle.calls)
        vin = "VIN-B"
        deferredBle.result.complete(false)
        job.join()
        assertEquals(0, cloudWrites)
    }
}
