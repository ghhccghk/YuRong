package top.ghhccghk.yadea.ble
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
class IosBleAdapter : BleAdapter {
    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _notifications = MutableSharedFlow<BleNotification>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notifications: Flow<BleNotification> = _notifications.asSharedFlow()
    override val isAvailable: Boolean = true
    override val isEnabled: Boolean = true
    override fun startScan(serviceUuids: List<String>, timeoutMs: Long) { _isScanning.value = true }
    override fun stopScan() { _isScanning.value = false }
    override suspend fun connect(address: String, autoConnect: Boolean) { _connectionState.value = BleConnectionState.Connected }
    override fun disconnect() { _connectionState.value = BleConnectionState.Disconnected }
    override suspend fun discoverServices(): List<BleService> { _connectionState.value = BleConnectionState.Ready; return emptyList() }
    override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): ByteArray? = null
    override suspend fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, data: ByteArray, withResponse: Boolean): Boolean = false
    override suspend fun setNotification(serviceUuid: String, characteristicUuid: String, enable: Boolean): Boolean = false
    override suspend fun requestMtu(mtu: Int): Int = mtu
    override fun close() { stopScan() }
}
actual fun createBleAdapter(): BleAdapter = IosBleAdapter()
