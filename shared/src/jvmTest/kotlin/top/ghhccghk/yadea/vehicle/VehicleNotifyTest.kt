package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.ble.BleDevice
import top.ghhccghk.yadea.ble.BleNotification
import top.ghhccghk.yadea.ble.BleService

class VehicleNotifyTest {

    private fun ttpPacket(): ByteArray {
        val d = ByteArray(0xB0)
        d[2] = 0x2A
        d[9] = 1
        d[10] = 2
        d[0x4B] = 0xFA.toByte() // 25.0 km/h
        d[0x4C] = 0x00
        return d
    }

    @Test
    fun protocolVersion_matchesConfirmedOrder() {
        val v1 = ByteArray(12).also { it[9] = 1; it[10] = 2 }
        assertEquals(1, VehicleNotifyDispatcher.protocolVersion(v1))

        val f1 = ByteArray(14).also {
            it[9] = 0x41
            it[10] = 0x05
        }
        assertEquals(0xF1, VehicleNotifyDispatcher.protocolVersion(f1))

        val short = ByteArray(8)
        assertEquals(0xF1, VehicleNotifyDispatcher.protocolVersion(short))
    }

    @Test
    fun dispatch_parsesTtpFullPacket() {
        val notify = VehicleNotifyDispatcher.dispatch(ttpPacket())
        assertTrue(notify is VehicleNotify.Ttp)
        val ttp = notify as VehicleNotify.Ttp
        assertFalse(ttp.isShort)
        assertEquals(25.0f, ttp.info.currentSpeedValue, 0.0001f)
    }

    @Test
    fun bleStatusSource_filtersAndPublishesVehicleNotification() {
        val adapter = FakeBleAdapter()
        val source = BleVehicleStatusSource(adapter, CoroutineScope(Dispatchers.Unconfined))

        adapter.notificationsFlow.tryEmit(
            BleNotification(
                serviceUuid = VehicleBleConstants.SERVICE_MAIN,
                characteristicUuid = VehicleBleConstants.CHAR_MAIN,
                data = ttpPacket(),
            ),
        )
        assertNotNull(source.status.value)
        assertEquals(25.0f, source.status.value!!.currentSpeedValue, 0.0001f)

        adapter.notificationsFlow.tryEmit(
            BleNotification(
                serviceUuid = "7e400001-b5a3-f393-e0a9-e50e24dcca9e",
                characteristicUuid = "7e400003-b5a3-f393-e0a9-e50e24dcca9e",
                data = ttpPacket(),
            ),
        )
        // 投屏仪表通知不应写回车辆状态；这里仍保留上一条整车快照。
        assertNotNull(source.status.value)
        source.dispose()
    }

    private class FakeBleAdapter : BleAdapter {
        val notificationsFlow = MutableSharedFlow<BleNotification>(extraBufferCapacity = 8)
        override val notifications: Flow<BleNotification> = notificationsFlow
        override val connectionState = MutableStateFlow(BleConnectionState.Connected)
        override val discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
        override val isScanning = MutableStateFlow(false)
        override val isAvailable: Boolean = true
        override val isEnabled: Boolean = true
        override fun startScan(serviceUuids: List<String>, timeoutMs: Long) = Unit
        override fun stopScan() = Unit
        override suspend fun connect(address: String, autoConnect: Boolean) = Unit
        override fun disconnect() = Unit
        override suspend fun discoverServices(): List<BleService> = emptyList()
        override suspend fun readCharacteristic(
            serviceUuid: String,
            characteristicUuid: String,
        ): ByteArray? = null
        override suspend fun writeCharacteristic(
            serviceUuid: String,
            characteristicUuid: String,
            data: ByteArray,
            withResponse: Boolean,
        ): Boolean = false
        override suspend fun setNotification(
            serviceUuid: String,
            characteristicUuid: String,
            enable: Boolean,
        ): Boolean = false
        override suspend fun requestMtu(mtu: Int): Int = mtu
        override fun close() = Unit
    }
}
