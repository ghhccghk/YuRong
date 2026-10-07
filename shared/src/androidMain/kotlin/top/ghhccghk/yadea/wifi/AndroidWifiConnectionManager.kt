package top.ghhccghk.yadea.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkRequest
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import kotlinx.coroutines.flow.*

/**
 * Android WiFi 连接管理实现
 *
 * 基于 WifiManager / ConnectivityManager / WifiNetworkSpecifier
 */
class AndroidWifiConnectionManager(private val context: Context) : WifiConnectionManager {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectionState = MutableStateFlow(WifiConnectionState.Disconnected)
    override val connectionState: StateFlow<WifiConnectionState> = _connectionState.asStateFlow()

    private val _currentNetwork = MutableStateFlow<WifiNetworkInfo?>(null)
    override val currentNetwork: StateFlow<WifiNetworkInfo?> = _currentNetwork.asStateFlow()

    private val _scanResults = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    override val scanResults: StateFlow<List<WifiNetworkInfo>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _networkEvents = MutableSharedFlow<WifiNetworkEvent>(extraBufferCapacity = 16)
    override val networkEvents: Flow<WifiNetworkEvent> = _networkEvents.asSharedFlow()

    override val isAvailable: Boolean get() = wifiManager.isWifiEnabled
    override val isEnabled: Boolean get() = wifiManager.isWifiEnabled

    override suspend fun scan(timeoutMs: Long) {
        _isScanning.value = true
        _connectionState.value = WifiConnectionState.Scanning
        wifiManager.startScan()
        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults.map {
            WifiNetworkInfo(
                ssid = it.SSID?.removeSurrounding("\"") ?: "",
                bssid = it.BSSID ?: "",
                rssi = it.level,
                frequency = it.frequency,
                security = parseSecurity(it.capabilities)
            )
        }
        _scanResults.value = results
        _isScanning.value = false
        _connectionState.value = WifiConnectionState.Disconnected
        _networkEvents.tryEmit(WifiNetworkEvent.ScanResults(results))
    }

    override suspend fun connect(ssid: String, password: String?, bssid: String?): Boolean {
        return connect(WifiProfile(ssid, password, bssid))
    }

    override suspend fun connect(profile: WifiProfile): Boolean {
        _connectionState.value = WifiConnectionState.Connecting
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(profile.ssid)
            .apply {
                if (profile.bssid != null) setBssid(android.net.MacAddress.fromString(profile.bssid))
                profile.password?.let { setWpa2Passphrase(it) }
            }
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        return try {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    _connectionState.value = WifiConnectionState.Connected
                    _currentNetwork.value = WifiNetworkInfo(profile.ssid, profile.bssid ?: "")
                    _networkEvents.tryEmit(WifiNetworkEvent.Connected(WifiNetworkInfo(profile.ssid)))
                }
                override fun onLost(network: android.net.Network) {
                    _connectionState.value = WifiConnectionState.Disconnected
                    _currentNetwork.value = null
                    _networkEvents.tryEmit(WifiNetworkEvent.Disconnected)
                }
            }
            connectivityManager.requestNetwork(request, networkCallback)
            true
        } catch (e: Exception) {
            _connectionState.value = WifiConnectionState.Failed
            _networkEvents.tryEmit(WifiNetworkEvent.Error(e.message ?: "连接失败"))
            false
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = WifiConnectionState.Disconnecting
        _connectionState.value = WifiConnectionState.Disconnected
        _currentNetwork.value = null
    }

    override suspend fun getLocalIpAddress(): String? {
        @Suppress("DEPRECATION")
        return if (wifiManager.connectionInfo != null) {
            val ip = wifiManager.connectionInfo.ipAddress
            String.format("%d.%d.%d.%d", ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
        } else null
    }

    override fun close() {}

    private fun parseSecurity(capabilities: String?): WifiSecurity {
        return when {
            capabilities == null -> WifiSecurity.Open
            capabilities.contains("WPA3") -> WifiSecurity.WPA3_PSK
            capabilities.contains("WPA2") -> WifiSecurity.WPA2_PSK
            capabilities.contains("WPA") -> WifiSecurity.WPA_PSK
            capabilities.contains("WEP") -> WifiSecurity.WEP
            else -> WifiSecurity.Open
        }
    }
}

actual fun createWifiConnectionManager(): WifiConnectionManager {
    throw UnsupportedOperationException("AndroidWifiConnectionManager requires Context; instantiate directly")
}
