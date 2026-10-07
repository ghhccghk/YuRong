package top.ghhccghk.yadea.wifi

import kotlinx.coroutines.flow.*

/**
 * JVM/Desktop WiFi 管理实现 (Windows/Linux/macOS)
 *
 * 骨架实现，可接入平台命令行工具:
 * - Windows: netsh wlan
 * - Linux: nmcli / iwctl
 * - macOS: networksetup / airport
 */
class JvmWifiConnectionManager : WifiConnectionManager {
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
    override val isAvailable: Boolean = true
    override val isEnabled: Boolean = true

    override suspend fun scan(timeoutMs: Long) {
        _isScanning.value = true
        // TODO: netsh wlan show networks / nmcli dev wifi list
        _isScanning.value = false
    }

    override suspend fun connect(ssid: String, password: String?, bssid: String?): Boolean {
        _connectionState.value = WifiConnectionState.Connecting
        // TODO: netsh wlan connect / nmcli dev wifi connect
        _connectionState.value = WifiConnectionState.Connected
        _currentNetwork.value = WifiNetworkInfo(ssid, bssid ?: "")
        return true
    }

    override suspend fun connect(profile: WifiProfile): Boolean =
        connect(profile.ssid, profile.password, profile.bssid)

    override suspend fun disconnect() {
        _connectionState.value = WifiConnectionState.Disconnected
        _currentNetwork.value = null
    }

    override suspend fun getLocalIpAddress(): String? = null
    override fun close() {}
}

class JvmWifiP2pManager : WifiP2pManager {
    private val _discoveredPeers = MutableStateFlow<List<P2pDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<P2pDevice>> = _discoveredPeers.asStateFlow()
    private val _connectionState = MutableStateFlow(P2pStatus.Available)
    override val connectionState: StateFlow<P2pStatus> = _connectionState.asStateFlow()
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    override val isAvailable: Boolean = true

    override suspend fun discoverPeers(timeoutMs: Long) {
        _isDiscovering.value = true
        // TODO: mDNS/UDP 广播发现
        _isDiscovering.value = false
    }

    override suspend fun connect(device: P2pDevice): Boolean {
        _connectionState.value = P2pStatus.Connected
        return true
    }

    override suspend fun disconnect() {
        _connectionState.value = P2pStatus.Available
    }

    override fun close() {}
}

actual fun createWifiConnectionManager(): WifiConnectionManager = JvmWifiConnectionManager()
actual fun createWifiP2pManager(): WifiP2pManager = JvmWifiP2pManager()
