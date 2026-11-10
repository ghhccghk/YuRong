package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ble.*
import top.ghhccghk.yadea.carsetting.*

/** In-memory notifications only. No platform adapter or network client is constructed. */
class EighthRoundObservationTest {
    private fun packet() = ByteArray(0xB0).also { it[2] = 0x2A; it[9] = 1; it[10] = 2 }

    private class Adapter : BleAdapter {
        override val connectionState = MutableStateFlow(BleConnectionState.Connected)
        override val notifications = MutableSharedFlow<BleNotification>(extraBufferCapacity = 8)
        override val discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
        override val isScanning = MutableStateFlow(false)
        override val isAvailable = true
        override val isEnabled = true
        override fun startScan(serviceUuids: List<String>, timeoutMs: Long) = error("not allowed")
        override fun stopScan() = Unit
        override suspend fun connect(address: String, autoConnect: Boolean) = error("not allowed")
        override fun disconnect() = error("not allowed")
        override suspend fun discoverServices(): List<BleService> = error("not allowed")
        override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): ByteArray? = error("not allowed")
        override suspend fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, data: ByteArray, withResponse: Boolean): Boolean = error("not allowed")
        override suspend fun setNotification(serviceUuid: String, characteristicUuid: String, enable: Boolean): Boolean = error("not allowed")
        override suspend fun requestMtu(mtu: Int): Int = error("not allowed")
        override fun close() = Unit
    }

    @Test fun cachedAndLiveIdenticalPacketsHaveDifferentProvenanceAndSequence() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val adapter = Adapter()
        val cache = object : VehicleStatusCache {
            override fun readVehicleStatus() = CachedVehicleStatus("VIN-A", packet().toHex())
            override suspend fun writeVehicleStatus(status: CachedVehicleStatus) = Unit
            override suspend fun clearVehicleStatus() = Unit
        }
        val source = BleVehicleStatusSource(adapter, scope, cache, MutableStateFlow("VIN-A"))
        try {
            assertEquals(VehicleStatusOrigin.CACHED_TTP, source.observation.value!!.origin)
            val notification = BleNotification(VehicleBleConstants.SERVICE_MAIN, VehicleBleConstants.CHAR_MAIN, packet())
            adapter.notifications.tryEmit(notification)
            val first = source.observation.value!!
            assertEquals(VehicleStatusOrigin.LIVE_TTP, first.origin)
            adapter.notifications.tryEmit(notification)
            assertTrue(source.observation.value!!.sequence > first.sequence)
            assertEquals(first.info, source.observation.value!!.info)
        } finally { source.dispose(); scope.cancel() }
    }

    @Test fun vinSwitchDoesNotRelabelExistingConnectionNotifications() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val adapter = Adapter()
        val vin = MutableStateFlow("VIN-A")
        val source = BleVehicleStatusSource(adapter, scope, vinFlow = vin)
        val notification = BleNotification(VehicleBleConstants.SERVICE_MAIN, VehicleBleConstants.CHAR_MAIN, packet())
        try {
            adapter.notifications.tryEmit(notification)
            assertEquals("VIN-A", source.observation.value!!.vin)
            vin.value = "VIN-B"
            val switched = source.observation.value
            adapter.notifications.tryEmit(notification)
            assertEquals(switched, source.observation.value)
            assertNull(source.status.value)
            adapter.connectionState.value = BleConnectionState.Disconnected
            adapter.connectionState.value = BleConnectionState.Connected
            adapter.notifications.tryEmit(notification)
            assertEquals("VIN-B", source.observation.value!!.vin)
            assertEquals(VehicleStatusOrigin.LIVE_TTP, source.observation.value!!.origin)
        } finally { source.dispose(); scope.cancel() }
    }

    @Test fun cacheReplayOldSequenceAndOldVinCannotConfirmWrite() = runBlocking {
        val kind = CarSettingKind.Slider(0f, 0f..7f, valueKnown = false)
        val item = CarSettingItem("cloud:20", "volume", "", CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE, kind = kind, configId = "20")
        val writer = object : CarSettingWriter {
            override suspend fun write(item: CarSettingItem) = true
            override suspend fun writeWithResult(item: CarSettingItem) = CarSettingWriteResult(true, CarSettingWriteChannel.BLE)
        }
        val c = CarSettingsController(listOf(item), writer, vinProvider = { "VIN-A" }, debounceMs = 0)
        val unknown = TtpInfoParser.parse(ByteArray(256) { -1 })
        val info = unknown.copy(extended = unknown.extended.copy(alarmWarningVolumeLevel = 3))
        fun observation(vin: String, origin: VehicleStatusOrigin, sequence: Long) = VehicleStatusObservation(vin, info, origin, sequence)
        c.updateVehicleObservation(observation("VIN-A", VehicleStatusOrigin.CACHED_TTP, 1))
        assertEquals(VehicleSettingValue.Unknown, c.state.value.valueState(item.id).vehicleValue)
        c.updateVehicleObservation(observation("VIN-A", VehicleStatusOrigin.LIVE_TTP, 2))
        c.update(item.id) { kind.copy(value = 3f) }
        c.updateVehicleObservation(observation("VIN-A", VehicleStatusOrigin.LIVE_TTP, 2))
        c.updateVehicleObservation(observation("VIN-OLD", VehicleStatusOrigin.LIVE_TTP, 9))
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(item.id).confirmationState)
        c.updateVehicleObservation(observation("VIN-A", VehicleStatusOrigin.CACHED_TTP, 3))
        assertFalse((c.state.value.valueState(item.id).vehicleValue as VehicleSettingValue.Known).live)
        assertEquals(SettingConfirmationState.WAITING_READBACK, c.state.value.valueState(item.id).confirmationState)
        c.updateVehicleObservation(observation("VIN-A", VehicleStatusOrigin.LIVE_TTP, 4))
        assertEquals(SettingConfirmationState.READBACK_MATCHED, c.state.value.valueState(item.id).confirmationState)
        c.updateVehicleObservation(VehicleStatusObservation("VIN-A", null, VehicleStatusOrigin.STALE, 5))
        assertFalse((c.state.value.valueState(item.id).vehicleValue as VehicleSettingValue.Known).live)
    }
}
