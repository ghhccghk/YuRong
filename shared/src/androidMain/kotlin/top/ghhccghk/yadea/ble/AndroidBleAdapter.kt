package top.ghhccghk.yadea.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * Android BLE 适配器实现
 *
 * 基于 android.bluetooth.BluetoothGatt / BluetoothLeScanner
 */
@SuppressLint("MissingPermission")
class AndroidBleAdapter(private val context: Context) : BleAdapter {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val leScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _notifications = MutableSharedFlow<BleNotification>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val notifications: Flow<BleNotification> = _notifications.asSharedFlow()

    private val discoveredServices = mutableListOf<BleService>()
    private var negotiatedMtu = 23

    override val isAvailable: Boolean get() = bluetoothAdapter != null
    override val isEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    override fun startScan(serviceUuids: List<String>, timeoutMs: Long) {
        val scanner = leScanner ?: return
        if (_isScanning.value) return

        val filters = serviceUuids.map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(it))).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BleDevice(
                    address = result.device.address,
                    name = result.device.name ?: result.scanRecord?.deviceName,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                )
                _discoveredDevices.update { list ->
                    if (list.any { it.address == device.address }) list
                    else list + device
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
            }
        }
        scanCallback = callback

        if (filters.isEmpty()) {
            scanner.startScan(callback)
        } else {
            scanner.startScan(filters, settings, callback)
        }
        _isScanning.value = true

        if (timeoutMs > 0) {
            // 超时自动停止由调用方通过协程控制
        }
    }

    override fun stopScan() {
        scanCallback?.let { leScanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
    }

    override suspend fun connect(address: String, autoConnect: Boolean) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        _connectionState.value = BleConnectionState.Connecting
        gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun disconnect() {
        _connectionState.value = BleConnectionState.Disconnecting
        gatt?.disconnect()
    }

    override suspend fun discoverServices(): List<BleService> {
        _connectionState.value = BleConnectionState.DiscoveringServices
        gatt?.discoverServices()
        return discoveredServices.toList()
    }

    override suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): ByteArray? {
        val service = gatt?.getService(UUID.fromString(serviceUuid)) ?: return null
        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid)) ?: return null
        gatt?.readCharacteristic(characteristic)
        return characteristic.value
    }

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        withResponse: Boolean
    ): Boolean {
        val service = gatt?.getService(UUID.fromString(serviceUuid)) ?: return false
        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid)) ?: return false
        characteristic.value = data
        val writeType = if (withResponse)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return gatt?.writeCharacteristic(characteristic) == true
    }

    override suspend fun setNotification(serviceUuid: String, characteristicUuid: String, enable: Boolean): Boolean {
        val service = gatt?.getService(UUID.fromString(serviceUuid)) ?: return false
        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid)) ?: return false
        gatt?.setCharacteristicNotification(characteristic, enable)

        // 写入 CCCD 描述符
        val descriptor = characteristic.getDescriptor(UUID.fromString(BleProtocol.CLIENT_CONFIG_UUID))
        if (descriptor != null) {
            descriptor.value = if (enable)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }
        return true
    }

    override suspend fun requestMtu(mtu: Int): Int {
        gatt?.requestMtu(mtu)
        return negotiatedMtu
    }

    override fun close() {
        stopScan()
        gatt?.close()
        gatt = null
    }

    // ---- GATT 回调 ----
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                discoveredServices.clear()
                for (service in gatt.services) {
                    val chars = service.characteristics.map {
                        BleCharacteristic(
                            uuid = it.uuid.toString(),
                            properties = it.properties,
                            value = it.value
                        )
                    }
                    discoveredServices.add(BleService(service.uuid.toString(), chars))
                }
                _connectionState.value = BleConnectionState.Ready
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val serviceUuid = characteristic.service?.uuid?.toString() ?: return
            val data = characteristic.value ?: return
            _notifications.tryEmit(
                BleNotification(serviceUuid, characteristic.uuid.toString(), data)
            )
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
        }
    }
}

actual fun createBleAdapter(): BleAdapter {
    throw UnsupportedOperationException("AndroidBleAdapter requires Context; use AndroidBleAdapter(context) directly")
}
