package top.ghhccghk.yadea.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用 BLE 适配器接口
 *
 * 跨平台蓝牙协议抽象层，支持:
 * - Android (android.bluetooth)
 * - iOS (CoreBluetooth)
 * - JVM/Desktop (Windows/Linux/macOS)
 * - Web (Web Bluetooth API)
 *
 * 平台实现方只需实现此接口。
 */
interface BleAdapter {
    /** 蓝牙是否可用 */
    val isAvailable: Boolean

    /** 蓝牙是否已开启 */
    val isEnabled: Boolean

    /** 平台 BLE 能力；默认不声明任何未经验证的能力。 */
    val capabilities: BleCapabilities
        get() = BleCapabilities.Unsupported

    /** 平台授权状态；默认 Unknown，不能当作已授权。 */
    val permissionState: BlePermissionState
        get() = BlePermissionState.Unknown

    /** 当前连接状态 */
    val connectionState: StateFlow<BleConnectionState>

    /** 扫描到的设备列表 */
    val discoveredDevices: StateFlow<List<BleDevice>>

    /** 是否正在扫描 */
    val isScanning: StateFlow<Boolean>

    /**
     * 开始扫描 BLE 设备
     * @param serviceUuids 可选过滤 UUID 列表
     * @param timeoutMs 超时自动停止 (0=不超时)
     */
    fun startScan(serviceUuids: List<String> = emptyList(), timeoutMs: Long = BleProtocol.SCAN_TIMEOUT_MS)

    /** 停止扫描 */
    fun stopScan()

    /**
     * 连接到 BLE 设备
     * @param address 设备地址 (MAC/UUID)
     * @param autoConnect 是否自动重连
     */
    suspend fun connect(address: String, autoConnect: Boolean = false)

    /** 断开连接 */
    fun disconnect()

    /**
     * 发现 GATT 服务
     * @return 服务列表
     */
    suspend fun discoverServices(): List<BleService>

    /**
     * 读取特征值
     * @param serviceUuid 服务 UUID
     * @param characteristicUuid 特征 UUID
     * @return 特征值数据
     */
    suspend fun readCharacteristic(serviceUuid: String, characteristicUuid: String): ByteArray?

    /**
     * 写入特征值
     * @param serviceUuid 服务 UUID
     * @param characteristicUuid 特征 UUID
     * @param data 数据
     * @param withResponse 是否等待写入响应
     * @return 是否写入成功
     */
    suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        withResponse: Boolean = true
    ): Boolean

    /**
     * 订阅特征通知
     * @param serviceUuid 服务 UUID
     * @param characteristicUuid 特征 UUID
     * @param enable 是否启用
     */
    suspend fun setNotification(serviceUuid: String, characteristicUuid: String, enable: Boolean): Boolean

    /**
     * 通知数据流 (特征值变化)
     */
    val notifications: Flow<BleNotification>

    /**
     * 请求 MTU
     * @param mtu 目标 MTU
     * @return 协商后的 MTU
     */
    suspend fun requestMtu(mtu: Int): Int

    /**
     * 清理 Android 系统缓存的 GATT 服务表。
     *
     * 这是 Android 133 错误的规避手段。没有对应能力的平台返回 `false`。
     */
    suspend fun refreshGattCache(): Boolean = false

    /**
     * 释放所有资源
     */
    fun close()
}

/**
 * BLE 服务
 */
data class BleService(
    val uuid: String,
    val characteristics: List<BleCharacteristic>
)

/**
 * BLE 通知
 */
data class BleNotification(
    val serviceUuid: String,
    val characteristicUuid: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleNotification) return false
        return serviceUuid == other.serviceUuid &&
                characteristicUuid == other.characteristicUuid &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return (serviceUuid.hashCode() * 31 + characteristicUuid.hashCode()) * 31 + data.contentHashCode()
    }
}

/**
 * 创建平台默认的 BleAdapter
 *
 * 各平台通过 expect/actual 提供实现。
 */
expect fun createBleAdapter(): BleAdapter
