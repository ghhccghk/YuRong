package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.TtpInfoParser

/** No transport, credentials, filesystem, BLE adapter or network client is constructed. */
class EighthRoundValueStateTest {
    private val unknown = TtpInfoParser.parse(ByteArray(256) { 0xFF.toByte() })
    private val bleWriter = object : CarSettingWriter {
        override suspend fun write(item: CarSettingItem) = true
        override suspend fun writeWithResult(item: CarSettingItem) = CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
    }

    private suspend fun controller(ids: List<String>, writer: CarSettingWriter = bleWriter): CarSettingsController {
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(ids.map { CloudBikeConfig(confId = it) }, emptyMap())
        }
        return CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0).also {
            assertTrue(it.refresh())
        }
    }

    private fun CarSettingsController.value(id: String) = state.value.valueState(id)
    private fun volume(value: Int): TtpInfo = unknown.copy(extended = unknown.extended.copy(alarmWarningVolumeLevel = value))

    @Test fun sevenUnknownDefaultsBecomeKnownOnlyWithValidFields() = runBlocking {
        val c = controller(listOf("14", "20", "21", "27", "31", "54", "81"))
        val ids = listOf("cloud:14", "cloud:20", "cloud:21:time", "cloud:27", "cloud:31", "cloud:54", "cloud:81:time")
        ids.forEach { assertEquals(VehicleSettingValue.Unknown, c.value(it).vehicleValue) }
        c.updateVehicleStatus("VIN-A", unknown)
        ids.forEach { assertEquals(VehicleSettingValue.Unknown, c.value(it).vehicleValue) }
        val valid = unknown.copy(extended = unknown.extended.copy(
            alarmSensitivityStatus = 1, alarmWarningVolumeLevel = 4, withGoHomeTimeStatus = 2,
            offModeStatus = 1, metricSystemStatus = 1, voltageSettingStatus = 2,
        ))
        c.updateVehicleStatus("VIN-OLD", valid)
        ids.forEach { assertEquals(VehicleSettingValue.Unknown, c.value(it).vehicleValue) }
        c.updateVehicleStatus("VIN-A", valid)
        ids.forEach { id ->
            assertTrue("$id must be observed", c.value(id).vehicleValue is VehicleSettingValue.Known)
        }
        val prior = ids.associateWith { c.value(it).vehicleValue }
        c.updateVehicleStatus("VIN-A", unknown)
        ids.forEach { assertEquals(prior[it], c.value(it).vehicleValue) }
    }

    @Test fun draftAndCancellationDoNotChangeVehicleValue() = runBlocking {
        val c = controller(listOf("20"))
        c.updateVehicleStatus("VIN-A", volume(3))
        val observed = c.value("cloud:20").vehicleValue
        assertTrue(c.editDraft("cloud:20") { (it as CarSettingKind.Slider).copy(value = 5f) })
        c.updateVehicleStatus("VIN-A", volume(4))
        assertEquals(5f, (c.value("cloud:20").draftValue as CarSettingKind.Slider).value)
        assertNotEquals(observed, c.value("cloud:20").vehicleValue)
        c.cancelDraft("cloud:20")
        assertNull(c.value("cloud:20").draftValue)
        assertEquals(4f, ((c.value("cloud:20").vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
    }

    @Test fun failurePreservesReadbackReceivedDuringSending() = runBlocking {
        val result = CompletableDeferred<Boolean>()
        val writer = object : CarSettingWriter { override suspend fun write(item: CarSettingItem) = result.await() }
        val c = controller(listOf("20"), writer)
        c.updateVehicleStatus("VIN-A", volume(3))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            assertFalse(c.update("cloud:20") { (it as CarSettingKind.Slider).copy(value = 5f) })
        }
        c.updateVehicleStatus("VIN-A", volume(4))
        result.complete(false)
        job.join()
        val state = c.value("cloud:20")
        assertEquals(4f, ((state.vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Slider).value)
        assertEquals(SettingWriteState.FAILED, state.writeState)
        assertNull(state.submittedValue)
        assertNotNull(state.error)
    }

    @Test fun receiptsAndUnrelatedOrOldReadbackNeverConfirmWriteOnlyItem() = runBlocking {
        val c = controller(listOf("50", "20"))
        c.updateVehicleStatus("VIN-A", volume(3))
        assertTrue(c.update("cloud:50") { (it as CarSettingKind.Choice).copy(selectedIndex = 1) })
        c.updateVehicleStatus("VIN-A", volume(4))
        val state = c.value("cloud:50")
        assertEquals(VehicleSettingValue.Unknown, state.vehicleValue)
        assertEquals(SettingWriteState.LOCAL_UPDATED, state.writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, state.confirmationState)
        assertEquals(CarSettingConfirmationStatus.LOCAL_UPDATED, c.state.value.confirmationStatuses["cloud:50"])
        assertEquals(1, (state.submittedValue as CarSettingKind.Choice).selectedIndex)
        // Recollecting the controller state cannot convert a target into an observation.
        assertEquals(state, c.state.value.valueState("cloud:50"))
    }

    @Test fun oldEqualValueAndMissingFieldCannotConfirmButFreshMatchCan() = runBlocking {
        val c = controller(listOf("20"))
        c.updateVehicleStatus("VIN-A", volume(3))
        assertTrue(c.update("cloud:20") { (it as CarSettingKind.Slider).copy(value = 3f) })
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.value("cloud:20").confirmationState)
        c.updateVehicleStatus("VIN-A", unknown)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.value("cloud:20").confirmationState)
        c.updateVehicleStatus("VIN-A", volume(4))
        assertEquals(SettingConfirmationState.READBACK_MISMATCH, c.value("cloud:20").confirmationState)
        c.updateVehicleStatus("VIN-A", volume(3))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.value("cloud:20").confirmationState)
        assertNull(c.value("cloud:20").submittedValue)
    }

    @Test fun readbackDuringSendIsNotDroppedOrPrematurelyConfirmed() = runBlocking {
        val receipt = CompletableDeferred<CarSettingWriteResult>()
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = receipt.await()
        }
        val c = controller(listOf("20"), writer)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            c.update("cloud:20") { (it as CarSettingKind.Slider).copy(value = 5f) }
        }
        c.updateVehicleStatus("VIN-A", volume(5))
        assertEquals(SettingConfirmationState.UNKNOWN, c.value("cloud:20").confirmationState)
        receipt.complete(CarSettingWriteResult(true, CarSettingWriteChannel.BLE))
        job.join()
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.value("cloud:20").confirmationState)
    }

    @Test fun cloudReceiptIsSeparateFromResultConfirmationAndObservation() = runBlocking {
        val result = CompletableDeferred<Boolean?>()
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(listOf(CloudBikeConfig(confId = "10")), emptyMap())
            override suspend fun checkStatus(configId: String) = result.await()
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = CarSettingWriteResult(true, CarSettingWriteChannel.CLOUD)
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0, statusPollIntervalMs = 0)
        c.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.update("cloud:10") { CarSettingKind.Toggle(true) } }
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value("cloud:10").writeState)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.value("cloud:10").confirmationState)
        result.complete(true)
        job.join()
        assertEquals(SettingConfirmationState.CONFIRMED_BY_RESULT, c.value("cloud:10").confirmationState)
        assertEquals(CarSettingConfirmationStatus.CLOUD_ACCEPTED, c.state.value.confirmationStatuses["cloud:10"])
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:10").vehicleValue)
    }

    @Test fun cancellingCloudConfirmationRetainsAcceptanceAndAllowsLaterTtpConfirmation() = runBlocking {
        val waiting = CompletableDeferred<Boolean?>()
        val gateway = object : CarSettingsGateway {
            override suspend fun load(vin: String) = CloudCarSettings(listOf(CloudBikeConfig(confId = "17")), emptyMap())
            override suspend fun checkStatus(configId: String): Boolean? = waiting.await()
        }
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = CarSettingWriteResult(true, CarSettingWriteChannel.CLOUD)
        }
        val c = CarSettingsController(writer = writer, gateway = gateway, vinProvider = { "VIN-A" }, debounceMs = 0, statusPollIntervalMs = 0)
        c.refresh()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.update("cloud:17") { CarSettingKind.Toggle(true) } }
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value("cloud:17").writeState)
        job.cancel(); job.join()
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value("cloud:17").writeState)
        assertEquals(SettingConfirmationState.UNKNOWN, c.value("cloud:17").confirmationState)
        c.updateVehicleStatus("VIN-A", unknown.copy(extended = unknown.extended.copy(slideModePowerRecoverStatus = 1)))
        assertEquals(SettingWriteState.CLOUD_ACCEPTED, c.value("cloud:17").writeState)
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.value("cloud:17").confirmationState)
    }

    @Test fun invalidTargetDoesNotInvokeWriterOrLeakBusyOperation() = runBlocking {
        val c = controller(listOf("20"))
        assertFalse(c.update("cloud:20") { (it as CarSettingKind.Slider).copy(value = Float.NaN) })
        assertTrue(c.state.value.pendingIds.isEmpty())
        assertTrue(c.editDraft("cloud:20") { (it as CarSettingKind.Slider).copy(value = 5f) })
        assertTrue(c.submitDraft("cloud:20"))
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:20").vehicleValue)
    }

    @Test fun panelStyleUsesProvenTtpBitsAndOnlyOriginalPageOptions() = runBlocking {
        val c = controller(listOf("50"))
        val item = c.state.value.items.single { it.id == "cloud:50" }
        assertEquals(2, (item.kind as CarSettingKind.Choice).options.size)
        assertFalse(item.kind.valueKnown)
        assertTrue(c.editDraft(item.id) { (it as CarSettingKind.Choice).copy(selectedIndex = 1) })
        assertEquals(VehicleSettingValue.Unknown, c.value(item.id).vehicleValue)
        assertTrue(c.submitDraft(item.id))
        assertEquals(VehicleSettingValue.Unknown, c.value(item.id).vehicleValue)
        for (raw in listOf(2, 3)) {
            val data = ByteArray(256) { -1 }.also { it[0x2E] = (0xFC or raw).toByte() }
            val parsed = TtpInfoParser.parse(data)
            assertEquals(if (raw == 3) -1 else raw, parsed.extended.panelThemeCurrent)
            c.updateVehicleStatus("VIN-A", parsed)
            assertEquals(VehicleSettingValue.Unknown, c.value(item.id).vehicleValue)
        }
        val data = ByteArray(256) { -1 }.also { it[0x2E] = 0xFD.toByte() }
        c.updateVehicleStatus("VIN-A", TtpInfoParser.parse(data))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.value(item.id).confirmationState)
        assertEquals(1, ((c.value(item.id).vehicleValue as VehicleSettingValue.Known).kind as CarSettingKind.Choice).selectedIndex)
    }

    @Test fun timeSyncResultFlagIsNotActualSettingOrTransactionConfirmation() = runBlocking {
        val c = controller(listOf("51"))
        c.update("cloud:51") { CarSettingKind.Toggle(true) }
        c.updateVehicleStatus("VIN-A", unknown.copy(extended = unknown.extended.copy(syncTimeResultStatus = 1)))
        assertEquals(VehicleSettingValue.Unknown, c.value("cloud:51").vehicleValue)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.value("cloud:51").confirmationState)
    }
}
