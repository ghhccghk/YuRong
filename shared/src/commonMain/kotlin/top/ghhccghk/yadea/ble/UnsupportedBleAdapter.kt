package top.ghhccghk.yadea.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 明确表示当前平台没有真实 BLE 实现的 Adapter。
 *
 * 该类型只允许用于 fail-closed：不得把连接、服务发现、读写或 MTU
 * 报告为成功。平台入口应优先注入真实实现；真实实现不可用时才使用此类型。
 */
open class UnsupportedBleAdapter(
    private val platformName: String,
) : BleAdapter {
    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    override val notifications: Flow<BleNotification> = emptyFlow()

    override val isAvailable: Boolean = false
    override val isEnabled: Boolean = false
    override val capabilities: BleCapabilities = BleCapabilities.Unsupported
    override val permissionState: BlePermissionState = BlePermissionState.Unknown

    override fun startScan(serviceUuids: List<String>, timeoutMs: Long) {
        _isScanning.value = false
        _discoveredDevices.value = emptyList()
    }

    override fun stopScan() {
        _isScanning.value = false
    }

    override suspend fun connect(address: String, autoConnect: Boolean) {
        throw unsupported("BLE connect")
    }

    override fun disconnect() {
        _connectionState.value = BleConnectionState.Disconnected
    }

    override suspend fun discoverServices(): List<BleService> {
        throw unsupported("GATT service discovery")
    }

    override suspend fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
    ): ByteArray? {
        throw unsupported("GATT characteristic read")
    }

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        withResponse: Boolean,
    ): Boolean {
        throw unsupported("GATT characteristic write")
    }

    override suspend fun setNotification(
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean,
    ): Boolean {
        throw unsupported("GATT notification subscription")
    }

    override suspend fun requestMtu(mtu: Int): Int {
        throw unsupported("BLE MTU negotiation")
    }

    override suspend fun refreshGattCache(): Boolean = false

    override fun close() {
        _isScanning.value = false
        _discoveredDevices.value = emptyList()
        _connectionState.value = BleConnectionState.Disconnected
    }

    private fun unsupported(feature: String): BleUnsupportedException =
        BleUnsupportedException("$platformName $feature")
}