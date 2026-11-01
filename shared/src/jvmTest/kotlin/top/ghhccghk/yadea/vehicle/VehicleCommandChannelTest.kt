package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.ble.BleDevice
import top.ghhccghk.yadea.ble.BleNotification
import top.ghhccghk.yadea.ble.BleService

class VehicleCommandChannelTest {

    private data class WriteCall(
        val serviceUuid: String,
        val characteristicUuid: String,
        val data: ByteArray,
        val withResponse: Boolean,
    )

    private class FakeBleAdapter(
        private val result: (String, String) -> Boolean,
    ) : BleAdapter {
        val writes = mutableListOf<WriteCall>()
        override val notifications: Flow<BleNotification> = MutableStateFlow(
            BleNotification("", "", byteArrayOf()),
        )
        override val connectionState = MutableStateFlow(BleConnectionState.Ready)
        override val discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
        override val isScanning = MutableStateFlow(false)
        override val isAvailable = true
        override val isEnabled = true
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
        ): Boolean {
            writes += WriteCall(serviceUuid, characteristicUuid, data.copyOf(), withResponse)
            return result(serviceUuid, characteristicUuid)
        }
        override suspend fun setNotification(
            serviceUuid: String,
            characteristicUuid: String,
            enable: Boolean,
        ): Boolean = false
        override suspend fun requestMtu(mtu: Int): Int = mtu
        override fun close() = Unit
    }

    @Test
    fun writesProtocolV1WithoutResponse() = runBlocking {
        val adapter = FakeBleAdapter { service, _ ->
            service == VehicleBleConstants.SERVICE_NOTIFY_V1
        }
        val frame = byteArrayOf(0x59, 0x44, 0x3E)

        assertTrue(adapter.writeVehicleCommandFrame(frame))

        assertEquals(1, adapter.writes.size)
        val call = adapter.writes.single()
        assertEquals(VehicleBleConstants.SERVICE_NOTIFY_V1, call.serviceUuid)
        assertEquals(VehicleBleConstants.CHAR_STATUS_QUERY_V1, call.characteristicUuid)
        assertArrayEquals(frame, call.data)
        assertFalse(call.withResponse)
    }

    @Test
    fun fallsBackToOtherProtocolChannelWithItsOwnFrame() = runBlocking {
        val adapter = FakeBleAdapter { service, _ ->
            service == VehicleBleConstants.SERVICE_NOTIFY_OTHER
        }
        val v1Frame = byteArrayOf(0x01)
        val otherFrame = byteArrayOf(0x02)

        assertTrue(
            adapter.writeVehicleCommandFrame(
                protocolV1Frame = v1Frame,
                otherProtocolFrame = otherFrame,
            ),
        )

        assertEquals(2, adapter.writes.size)
        val fallback = adapter.writes.last()
        assertEquals(VehicleBleConstants.SERVICE_NOTIFY_OTHER, fallback.serviceUuid)
        assertEquals(VehicleBleConstants.CHAR_STATUS_QUERY_OTHER, fallback.characteristicUuid)
        assertArrayEquals(otherFrame, fallback.data)
        assertFalse(fallback.withResponse)
    }
}
