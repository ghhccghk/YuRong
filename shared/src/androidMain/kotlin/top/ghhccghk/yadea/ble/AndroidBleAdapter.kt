package top.ghhccghk.yadea.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Android BLE 适配器实现。
 *
 * Android 的 `connectGatt` / `discoverServices` / 读写 / CCCD / MTU 都是异步
 * 提交接口：返回 `true` 只表示命令已交给协议栈，不代表操作完成。本实现把
 * 每个 GATT 回调转换为可等待结果，避免上层在服务发现或初始化数据尚未就绪时
 * 就显示"已连接"。
 */
@SuppressLint("MissingPermission")
class AndroidBleAdapter(private val context: Context) : BleAdapter {

    private companion object {
        const val GATT_CONNECT_TIMEOUT_MS = 20_000L
        const val GATT_OPERATION_TIMEOUT_MS = 5_000L
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val leScanner = bluetoothAdapter?.bluetoothLeScanner
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Android 同一时间只允许一个 GATT 操作。读写、CCCD 和 MTU 共用这把锁。
     */
    private val operationMutex = Mutex()
    private val pendingLock = Any()

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var scanTimeoutJob: Job? = null

    private var pendingConnect: CompletableDeferred<Int>? = null
    private var pendingDiscover: CompletableDeferred<Int>? = null
    private var pendingRead: CompletableDeferred<ReadResult>? = null
    private var pendingWrite: CompletableDeferred<Int>? = null
    private var pendingDescriptorWrite: CompletableDeferred<Int>? = null
    private var pendingMtu: CompletableDeferred<MtuResult>? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** 当前 GATT 对应设备地址；Wi-Fi OTA 用它换算目标 P2P MAC。 */
    var connectedAddress: String = ""
        private set

    private val _notifications = MutableSharedFlow<BleNotification>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val notifications: Flow<BleNotification> = _notifications.asSharedFlow()

    private val discoveredServices = mutableListOf<BleService>()
    private var negotiatedMtu = 23

    override val isAvailable: Boolean get() = bluetoothAdapter != null
    override val isEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true
    override val capabilities: BleCapabilities = BleCapabilities.Android

    override fun startScan(serviceUuids: List<String>, timeoutMs: Long) {
        val scanner = leScanner ?: return
        if (_isScanning.value) return

        val filters = serviceUuids.mapNotNull { uuid ->
            runCatching {
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(UUID.fromString(uuid)))
                    .build()
            }.getOrNull()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertised = BleAdvertisementParser.parse(
                    mac = result.device.address,
                    data = result.scanRecord?.bytes,
                )
                val device = BleDevice(
                    address = result.device.address,
                    name = result.device.name ?: result.scanRecord?.deviceName,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids
                        ?.map { it.uuid.toString() }
                        .orEmpty(),
                    isScooter = advertised.isScooter,
                    isBox = advertised.isBox,
                    supportsToken = advertised.supportsToken,
                    bleProtocolVersion = advertised.bleProtocolVersion,
                    parkingStatus = advertised.parkingStatus,
                )
                _discoveredDevices.update { list ->
                    if (list.any { it.address == device.address }) list else list + device
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
                scanCallback = null
            }
        }
        scanCallback = callback
        _discoveredDevices.value = emptyList()

        if (filters.isEmpty()) {
            scanner.startScan(callback)
        } else {
            scanner.startScan(filters, settings, callback)
        }
        _isScanning.value = true

        if (timeoutMs > 0) {
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(timeoutMs)
                stopScan()
            }
        }
    }

    override fun stopScan() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        scanCallback?.let { callback ->
            runCatching { leScanner?.stopScan(callback) }
        }
        scanCallback = null
        _isScanning.value = false
    }

    override suspend fun connect(address: String, autoConnect: Boolean) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
            ?: throw BleGattException(
                operation = BleGattOperation.Connect,
                status = BluetoothGatt.GATT_FAILURE,
                message = "Bluetooth device $address is unavailable",
            )

        disconnectCurrentGatt()

        val result = CompletableDeferred<Int>()
        synchronized(pendingLock) {
            pendingConnect = result
        }
        _connectionState.value = BleConnectionState.Connecting

        val newGatt = device.connectGatt(
            context,
            autoConnect,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        ) ?: run {
            synchronized(pendingLock) {
                if (pendingConnect === result) pendingConnect = null
            }
            _connectionState.value = BleConnectionState.Failed
            throw BleGattException(
                operation = BleGattOperation.Connect,
                status = BluetoothGatt.GATT_FAILURE,
                message = "connectGatt returned null",
            )
        }
        gatt = newGatt

        val status = try {
            withTimeout(GATT_CONNECT_TIMEOUT_MS) { result.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            synchronized(pendingLock) {
                if (pendingConnect === result) pendingConnect = null
            }
            _connectionState.value = BleConnectionState.Failed
            throw BleGattTimeoutException(BleGattOperation.Connect)
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            throw BleGattException(BleGattOperation.Connect, status)
        }
        connectedAddress = address
        _connectionState.value = BleConnectionState.Connected
    }

    override fun disconnect() {
        _connectionState.value = BleConnectionState.Disconnecting
        gatt?.disconnect()
    }

    override suspend fun discoverServices(): List<BleService> {
        val currentGatt = gatt ?: throw BleGattDisconnectedException(
            BleGattOperation.DiscoverServices,
        )
        val result = CompletableDeferred<Int>()
        synchronized(pendingLock) {
            pendingDiscover = result
        }
        _connectionState.value = BleConnectionState.DiscoveringServices

        if (!currentGatt.discoverServices()) {
            synchronized(pendingLock) {
                if (pendingDiscover === result) pendingDiscover = null
            }
            _connectionState.value = BleConnectionState.Failed
            throw BleGattException(
                operation = BleGattOperation.DiscoverServices,
                status = BluetoothGatt.GATT_FAILURE,
                message = "discoverServices returned false",
            )
        }

        val status = awaitStatus(result, BleGattOperation.DiscoverServices)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            _connectionState.value = BleConnectionState.Failed
            throw BleGattException(BleGattOperation.DiscoverServices, status)
        }
        _connectionState.value = BleConnectionState.Ready
        return discoveredServices.toList()
    }

    override suspend fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
    ): ByteArray? = operationMutex.withLock {
        val currentGatt = gatt ?: throw BleGattDisconnectedException(
            BleGattOperation.ReadCharacteristic,
        )
        val characteristic = findCharacteristic(currentGatt, serviceUuid, characteristicUuid)
            ?: throw BleGattException(
                operation = BleGattOperation.ReadCharacteristic,
                status = BluetoothGatt.GATT_FAILURE,
                message = "Characteristic $characteristicUuid not found",
            )

        val result = CompletableDeferred<ReadResult>()
        synchronized(pendingLock) {
            pendingRead = result
        }
        if (!currentGatt.readCharacteristic(characteristic)) {
            synchronized(pendingLock) {
                if (pendingRead === result) pendingRead = null
            }
            throw BleGattException(
                operation = BleGattOperation.ReadCharacteristic,
                status = BluetoothGatt.GATT_FAILURE,
                message = "readCharacteristic returned false",
            )
        }

        val read = awaitRead(result, BleGattOperation.ReadCharacteristic)
        if (read.status != BluetoothGatt.GATT_SUCCESS) {
            throw BleGattException(BleGattOperation.ReadCharacteristic, read.status)
        }
        read.value
    }

    override suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        withResponse: Boolean,
    ): Boolean = operationMutex.withLock {
        val currentGatt = gatt ?: throw BleGattDisconnectedException(
            BleGattOperation.WriteCharacteristic,
        )
        val characteristic = findCharacteristic(currentGatt, serviceUuid, characteristicUuid)
            ?: return@withLock false

        val result = CompletableDeferred<Int>()
        synchronized(pendingLock) {
            pendingWrite = result
        }
        characteristic.value = data
        characteristic.writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        if (!currentGatt.writeCharacteristic(characteristic)) {
            synchronized(pendingLock) {
                if (pendingWrite === result) pendingWrite = null
            }
            throw BleGattException(
                operation = BleGattOperation.WriteCharacteristic,
                status = BluetoothGatt.GATT_FAILURE,
                message = "writeCharacteristic returned false",
            )
        }

        val status = awaitStatus(result, BleGattOperation.WriteCharacteristic)
        status == BluetoothGatt.GATT_SUCCESS
    }

    override suspend fun setNotification(
        serviceUuid: String,
        characteristicUuid: String,
        enable: Boolean,
    ): Boolean = operationMutex.withLock {
        val currentGatt = gatt ?: throw BleGattDisconnectedException(
            BleGattOperation.WriteDescriptor,
        )
        val characteristic = findCharacteristic(currentGatt, serviceUuid, characteristicUuid)
            ?: return@withLock false
        val descriptor = characteristic.getDescriptor(
            UUID.fromString(BleProtocol.CLIENT_CONFIG_UUID),
        ) ?: return@withLock false

        if (!currentGatt.setCharacteristicNotification(characteristic, enable)) {
            return@withLock false
        }

        val result = CompletableDeferred<Int>()
        synchronized(pendingLock) {
            pendingDescriptorWrite = result
        }
        descriptor.value = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (!currentGatt.writeDescriptor(descriptor)) {
            synchronized(pendingLock) {
                if (pendingDescriptorWrite === result) pendingDescriptorWrite = null
            }
            throw BleGattException(
                operation = BleGattOperation.WriteDescriptor,
                status = BluetoothGatt.GATT_FAILURE,
                message = "writeDescriptor returned false",
            )
        }

        val status = awaitStatus(result, BleGattOperation.WriteDescriptor)
        status == BluetoothGatt.GATT_SUCCESS
    }

    override suspend fun requestMtu(mtu: Int): Int = operationMutex.withLock {
        val currentGatt = gatt ?: throw BleGattDisconnectedException(
            BleGattOperation.RequestMtu,
        )
        val result = CompletableDeferred<MtuResult>()
        synchronized(pendingLock) {
            pendingMtu = result
        }
        if (!currentGatt.requestMtu(mtu)) {
            synchronized(pendingLock) {
                if (pendingMtu === result) pendingMtu = null
            }
            throw BleGattException(
                operation = BleGattOperation.RequestMtu,
                status = BluetoothGatt.GATT_FAILURE,
                message = "requestMtu returned false",
            )
        }

        val changed = awaitMtu(result, BleGattOperation.RequestMtu)
        if (changed.status != BluetoothGatt.GATT_SUCCESS) {
            throw BleGattException(BleGattOperation.RequestMtu, changed.status)
        }
        negotiatedMtu = changed.mtu
        changed.mtu
    }

    override suspend fun refreshGattCache(): Boolean {
        val currentGatt = gatt ?: return false
        return runCatching {
            val method = BluetoothGatt::class.java.getMethod("refresh")
            method.isAccessible = true
            method.invoke(currentGatt) as? Boolean ?: true
        }.getOrElse { false }
    }

    override fun close() {
        stopScan()
        _connectionState.value = BleConnectionState.Disconnected
        failPending()
        disconnectCurrentGatt()
        scope.cancel()
    }

    private suspend fun awaitStatus(
        deferred: CompletableDeferred<Int>,
        operation: BleGattOperation,
    ): Int = awaitOperation(deferred, operation)

    private suspend fun awaitRead(
        deferred: CompletableDeferred<ReadResult>,
        operation: BleGattOperation,
    ): ReadResult = awaitOperation(deferred, operation)

    private suspend fun awaitMtu(
        deferred: CompletableDeferred<MtuResult>,
        operation: BleGattOperation,
    ): MtuResult = awaitOperation(deferred, operation)

    private suspend fun <T> awaitOperation(
        deferred: CompletableDeferred<T>,
        operation: BleGattOperation,
    ): T = try {
        withTimeout(GATT_OPERATION_TIMEOUT_MS) { deferred.await() }
    } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
        throw BleGattTimeoutException(operation)
    } finally {
        clearPending(operation)
    }

    private fun clearPending(operation: BleGattOperation) {
        synchronized(pendingLock) {
            when (operation) {
                BleGattOperation.Connect -> pendingConnect = null
                BleGattOperation.DiscoverServices -> pendingDiscover = null
                BleGattOperation.ReadCharacteristic -> pendingRead = null
                BleGattOperation.WriteCharacteristic -> pendingWrite = null
                BleGattOperation.WriteDescriptor -> pendingDescriptorWrite = null
                BleGattOperation.RequestMtu -> pendingMtu = null
            }
        }
    }

    private fun failPending() {
        synchronized(pendingLock) {
            pendingConnect?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.Connect),
            )
            pendingDiscover?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.DiscoverServices),
            )
            pendingRead?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.ReadCharacteristic),
            )
            pendingWrite?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.WriteCharacteristic),
            )
            pendingDescriptorWrite?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.WriteDescriptor),
            )
            pendingMtu?.completeExceptionally(
                BleGattDisconnectedException(BleGattOperation.RequestMtu),
            )
            pendingConnect = null
            pendingDiscover = null
            pendingRead = null
            pendingWrite = null
            pendingDescriptorWrite = null
            pendingMtu = null
        }
    }

    private fun disconnectCurrentGatt() {
        val current = gatt ?: return
        gatt = null
        runCatching { current.disconnect() }
        runCatching { current.close() }
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        serviceUuid: String,
        characteristicUuid: String,
    ): BluetoothGattCharacteristic? = runCatching {
        gatt.getService(UUID.fromString(serviceUuid))
            ?.getCharacteristic(UUID.fromString(characteristicUuid))
    }.getOrNull()

    private data class ReadResult(val value: ByteArray?, val status: Int)
    private data class MtuResult(val mtu: Int, val status: Int)

    // ---- GATT 回调 ----
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            callbackGatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            val currentGatt = gatt
            if (currentGatt != null && currentGatt !== callbackGatt) {
                runCatching { callbackGatt.close() }
                return
            }
            // 极少数设备会在 connectGatt() 返回前就投递首个回调；先认领 GATT，
            // 避免把这次合法回调误判为旧连接事件。
            if (currentGatt == null) {
                gatt = callbackGatt
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        connectedAddress = callbackGatt.device.address
                        _connectionState.value = BleConnectionState.Connected
                        takePendingConnect()?.complete(status)
                    } else {
                        _connectionState.value = BleConnectionState.Failed
                        takePendingConnect()?.complete(status)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (gatt === callbackGatt) {
                        connectedAddress = ""
                    }
                    _connectionState.value = BleConnectionState.Disconnected
                    if (status == 0x85) {
                        invokeRefresh(callbackGatt)
                    }
                    takePendingConnect()?.complete(
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            status
                        } else {
                            BluetoothGatt.GATT_FAILURE
                        },
                    )
                    failPending()
                    if (gatt === callbackGatt) {
                        gatt = null
                    }
                    runCatching { callbackGatt.close() }
                }
            }
        }

        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            if (gatt !== callbackGatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                discoveredServices.clear()
                callbackGatt.services.forEach { service ->
                    discoveredServices += BleService(
                        uuid = service.uuid.toString(),
                        characteristics = service.characteristics.map { characteristic ->
                            BleCharacteristic(
                                uuid = characteristic.uuid.toString(),
                                properties = characteristic.properties,
                                value = characteristic.value,
                            )
                        },
                    )
                }
            }
            takePendingDiscover()?.complete(status)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            completeRead(characteristic.value, status)
        }

        override fun onCharacteristicRead(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            completeRead(value, status)
        }

        override fun onCharacteristicWrite(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            takePendingWrite()?.complete(status)
        }

        override fun onDescriptorWrite(
            callbackGatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            takePendingDescriptorWrite()?.complete(status)
        }

        override fun onMtuChanged(callbackGatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            }
            takePendingMtu()?.complete(MtuResult(mtu, status))
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            emitNotification(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitNotification(characteristic, value)
        }
    }

    private fun emitNotification(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?,
    ) {
        val serviceUuid = characteristic.service?.uuid?.toString() ?: return
        _notifications.tryEmit(
            BleNotification(
                serviceUuid = serviceUuid,
                characteristicUuid = characteristic.uuid.toString(),
                data = value ?: return,
            ),
        )
    }

    private fun completeRead(value: ByteArray?, status: Int) {
        takePendingRead()?.complete(ReadResult(value?.copyOf(), status))
    }

    private fun invokeRefresh(currentGatt: BluetoothGatt) {
        runCatching {
            val method = BluetoothGatt::class.java.getMethod("refresh")
            method.isAccessible = true
            method.invoke(currentGatt)
        }
    }

    private fun takePendingConnect(): CompletableDeferred<Int>? =
        synchronized(pendingLock) {
            pendingConnect.also { pendingConnect = null }
        }

    private fun takePendingDiscover(): CompletableDeferred<Int>? =
        synchronized(pendingLock) {
            pendingDiscover.also { pendingDiscover = null }
        }

    private fun takePendingRead(): CompletableDeferred<ReadResult>? =
        synchronized(pendingLock) {
            pendingRead.also { pendingRead = null }
        }

    private fun takePendingWrite(): CompletableDeferred<Int>? =
        synchronized(pendingLock) {
            pendingWrite.also { pendingWrite = null }
        }

    private fun takePendingDescriptorWrite(): CompletableDeferred<Int>? =
        synchronized(pendingLock) {
            pendingDescriptorWrite.also { pendingDescriptorWrite = null }
        }

    private fun takePendingMtu(): CompletableDeferred<MtuResult>? =
        synchronized(pendingLock) {
            pendingMtu.also { pendingMtu = null }
        }
}

actual fun createBleAdapter(): BleAdapter {
    throw UnsupportedOperationException(
        "AndroidBleAdapter requires Context; use AndroidBleAdapter(context) directly",
    )
}
