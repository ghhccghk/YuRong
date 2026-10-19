package top.ghhccghk.yadea.ble

/**
 * BLE 扫描发现的设备
 */
data class BleDevice(
    val address: String,
    val name: String? = null,
    val rssi: Int = -100,
    val serviceUuids: List<String> = emptyList()
)

/**
 * BLE 连接状态
 */
enum class BleConnectionState {
    Disconnected,
    Connecting,
    Connected,
    DiscoveringServices,
    Ready,
    Disconnecting,
    Failed
}

/** GATT 操作阶段，用于把平台回调中的 status 原样带到连接编排层。 */
enum class BleGattOperation {
    Connect,
    DiscoverServices,
    ReadCharacteristic,
    WriteCharacteristic,
    WriteDescriptor,
    RequestMtu,
}

/**
 * GATT 操作失败。
 *
 * [status] 使用 Android `BluetoothGatt` 的原始状态码；`0x85`（133）不能在这里
 * 被折叠成普通失败，否则连接层无法执行清缓存规避。
 */
class BleGattException(
    val operation: BleGattOperation,
    val status: Int,
    message: String = "${operation.name} failed with GATT status $status",
) : Exception(message)

/** GATT 操作在约定时间内没有收到平台回调。 */
class BleGattTimeoutException(
    val operation: BleGattOperation,
) : Exception("${operation.name} timed out")

/** 连接已断开且尚未收到操作回调。 */
class BleGattDisconnectedException(
    val operation: BleGattOperation,
) : Exception("${operation.name} aborted because the GATT connection was lost")

/**
 * BLE GATT 特征值属性
 */
data class BleCharacteristic(
    val uuid: String,
    val properties: Int,
    val value: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleCharacteristic) return false
        return uuid == other.uuid && properties == other.properties &&
                value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return (uuid.hashCode() * 31 + properties) * 31 + (value?.contentHashCode() ?: 0)
    }
}

/**
 * BLE 写入请求
 */
data class BleWriteRequest(
    val characteristicUuid: String,
    val data: ByteArray,
    val withResponse: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleWriteRequest) return false
        return characteristicUuid == other.characteristicUuid &&
                data.contentEquals(other.data) && withResponse == other.withResponse
    }

    override fun hashCode(): Int {
        return (characteristicUuid.hashCode() * 31 + data.contentHashCode()) * 31 + if (withResponse) 1 else 0
    }
}

/**
 * BLE 通知回调
 */
fun interface BleNotificationCallback {
    fun onNotification(characteristicUuid: String, data: ByteArray)
}

/**
 * BLE 连接状态回调
 */
fun interface BleConnectionCallback {
    fun onStateChanged(state: BleConnectionState, message: String?)
}

/**
 * BLE 扫描结果回调
 */
fun interface BleScanCallback {
    fun onDeviceFound(device: BleDevice)
}
