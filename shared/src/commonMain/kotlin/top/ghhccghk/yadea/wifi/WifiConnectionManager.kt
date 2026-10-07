package top.ghhccghk.yadea.wifi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用 WiFi 连接管理接口
 *
 * 跨平台 WiFi 协议抽象层，支持:
 * - Android (WifiManager / WifiP2pManager)
 * - iOS (NEHotspotConfiguration)
 * - JVM/Desktop (Windows/Linux/macOS)
 * - Web (浏览器限制，仅提供状态查询)
 */
interface WifiConnectionManager {
    /** WiFi 是否可用 */
    val isAvailable: Boolean

    /** WiFi 是否已开启 */
    val isEnabled: Boolean

    /** 当前连接状态 */
    val connectionState: StateFlow<WifiConnectionState>

    /** 当前连接的网络 */
    val currentNetwork: StateFlow<WifiNetworkInfo?>

    /** 扫描到的网络列表 */
    val scanResults: StateFlow<List<WifiNetworkInfo>>

    /** 是否正在扫描 */
    val isScanning: StateFlow<Boolean>

    /**
     * 扫描 WiFi 网络
     * @param timeoutMs 超时自动停止
     */
    suspend fun scan(timeoutMs: Long = 10_000)

    /**
     * 连接到 WiFi 网络
     * @param ssid 网络名称
     * @param password 密码 (开放网络传 null)
     * @param bssid 可选 BSSID 过滤
     * @return 是否连接成功
     */
    suspend fun connect(ssid: String, password: String? = null, bssid: String? = null): Boolean

    /**
     * 连接到 WiFi 网络 (使用完整配置)
     */
    suspend fun connect(profile: WifiProfile): Boolean

    /** 断开当前 WiFi 连接 */
    suspend fun disconnect()

    /**
     * 获取当前网络的 IP 地址
     * @return IP 地址字符串，未连接返回 null
     */
    suspend fun getLocalIpAddress(): String?

    /**
     * 监听网络状态变化
     */
    val networkEvents: Flow<WifiNetworkEvent>

    /** 释放资源 */
    fun close()
}

/**
 * WiFi 连接状态
 */
enum class WifiConnectionState {
    Disconnected,
    Scanning,
    Connecting,
    Connected,
    Disconnecting,
    Failed
}

/**
 * WiFi 网络信息
 */
data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String = "",
    val rssi: Int = -100,
    val frequency: Int = 0,
    val security: WifiSecurity = WifiSecurity.Open,
    val isSaved: Boolean = false
)

/**
 * WiFi 安全类型
 */
enum class WifiSecurity {
    Open, WEP, WPA_PSK, WPA2_PSK, WPA3_PSK, WPA_EAP, WPA2_EAP, Unknown
}

/**
 * WiFi 连接配置
 */
data class WifiProfile(
    val ssid: String,
    val password: String? = null,
    val bssid: String? = null,
    val security: WifiSecurity = WifiSecurity.WPA2_PSK,
    val hiddenSsid: Boolean = false
)

/**
 * WiFi 网络事件
 */
sealed class WifiNetworkEvent {
    data class Connected(val network: WifiNetworkInfo) : WifiNetworkEvent()
    data object Disconnected : WifiNetworkEvent()
    data class ScanResults(val networks: List<WifiNetworkInfo>) : WifiNetworkEvent()
    data class Error(val message: String) : WifiNetworkEvent()
}

/**
 * P2P 设备信息
 */
data class P2pDevice(
    val name: String,
    val address: String,
    val status: P2pStatus = P2pStatus.Available
)

/**
 * P2P 连接状态
 */
enum class P2pStatus {
    Available, Invited, Connected, Failed
}

/**
 * 通用 WiFi P2P 管理接口
 */
interface WifiP2pManager {
    /** P2P 是否可用 */
    val isAvailable: Boolean

    /** 发现的 P2P 设备 */
    val discoveredPeers: StateFlow<List<P2pDevice>>

    /** P2P 连接状态 */
    val connectionState: StateFlow<P2pStatus>

    /** 是否正在搜索 */
    val isDiscovering: StateFlow<Boolean>

    /**
     * 发现 P2P 设备
     * @param timeoutMs 超时
     */
    suspend fun discoverPeers(timeoutMs: Long = 30_000)

    /**
     * 连接到 P2P 设备
     * @param device 目标设备
     * @return 是否成功
     */
    suspend fun connect(device: P2pDevice): Boolean

    /** 断开 P2P 连接 */
    suspend fun disconnect()

    /** 释放资源 */
    fun close()
}

/**
 * 创建平台默认的 WifiConnectionManager
 */
expect fun createWifiConnectionManager(): WifiConnectionManager

/**
 * 创建平台默认的 WifiP2pManager
 */
expect fun createWifiP2pManager(): WifiP2pManager
