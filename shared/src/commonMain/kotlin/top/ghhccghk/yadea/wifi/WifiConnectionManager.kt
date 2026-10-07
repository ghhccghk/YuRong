package top.ghhccghk.yadea.wifi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用 WiFi 连接管理接口
 */
interface WifiConnectionManager {
    val isAvailable: Boolean
    val isEnabled: Boolean
    val connectionState: StateFlow<WifiConnectionState>
    val currentNetwork: StateFlow<WifiNetworkInfo?>
    val scanResults: StateFlow<List<WifiNetworkInfo>>
    val isScanning: StateFlow<Boolean>

    suspend fun scan(timeoutMs: Long = 10_000)
    suspend fun connect(ssid: String, password: String? = null, bssid: String? = null): Boolean
    suspend fun connect(profile: WifiProfile): Boolean
    suspend fun disconnect()
    suspend fun getLocalIpAddress(): String?
    val networkEvents: Flow<WifiNetworkEvent>
    fun close()
}

enum class WifiConnectionState {
    Disconnected, Scanning, Connecting, Connected, Disconnecting, Failed
}

data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String = "",
    val rssi: Int = -100,
    val frequency: Int = 0,
    val security: WifiSecurity = WifiSecurity.Open,
    val isSaved: Boolean = false
)

enum class WifiSecurity {
    Open, WEP, WPA_PSK, WPA2_PSK, WPA3_PSK, WPA_EAP, WPA2_EAP, Unknown
}

data class WifiProfile(
    val ssid: String,
    val password: String? = null,
    val bssid: String? = null,
    val security: WifiSecurity = WifiSecurity.WPA2_PSK,
    val hiddenSsid: Boolean = false
)

sealed class WifiNetworkEvent {
    data class Connected(val network: WifiNetworkInfo) : WifiNetworkEvent()
    data object Disconnected : WifiNetworkEvent()
    data class ScanResults(val networks: List<WifiNetworkInfo>) : WifiNetworkEvent()
    data class Error(val message: String) : WifiNetworkEvent()
}

/**
 * 创建平台默认的 WifiConnectionManager
 */
expect fun createWifiConnectionManager(): WifiConnectionManager
