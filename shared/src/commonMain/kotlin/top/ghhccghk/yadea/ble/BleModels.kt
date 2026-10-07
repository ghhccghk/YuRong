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
