package top.ghhccghk.yadea.wifi
import kotlinx.coroutines.flow.*
class WasmWifiConnectionManager : WifiConnectionManager {
    private val _cs = MutableStateFlow(WifiConnectionState.Disconnected); override val connectionState: StateFlow<WifiConnectionState> = _cs.asStateFlow()
    private val _cn = MutableStateFlow<WifiNetworkInfo?>(null); override val currentNetwork: StateFlow<WifiNetworkInfo?> = _cn.asStateFlow()
    private val _sr = MutableStateFlow<List<WifiNetworkInfo>>(emptyList()); override val scanResults: StateFlow<List<WifiNetworkInfo>> = _sr.asStateFlow()
    private val _sc = MutableStateFlow(false); override val isScanning: StateFlow<Boolean> = _sc.asStateFlow()
    private val _ne = MutableSharedFlow<WifiNetworkEvent>(extraBufferCapacity = 16); override val networkEvents: Flow<WifiNetworkEvent> = _ne.asSharedFlow()
    override val isAvailable = true; override val isEnabled = true
    override suspend fun scan(timeoutMs: Long) {}
    override suspend fun connect(ssid: String, password: String?, bssid: String?): Boolean = false
    override suspend fun connect(profile: WifiProfile): Boolean = false
    override suspend fun disconnect() {}
    override suspend fun getLocalIpAddress(): String? = null
    override fun close() {}
}
class WasmWifiP2pManager : WifiP2pManager {
    private val _dp = MutableStateFlow<List<P2pDevice>>(emptyList()); override val discoveredPeers: StateFlow<List<P2pDevice>> = _dp.asStateFlow()
    private val _cs = MutableStateFlow(P2pStatus.Available); override val connectionState: StateFlow<P2pStatus> = _cs.asStateFlow()
    private val _id = MutableStateFlow(false); override val isDiscovering: StateFlow<Boolean> = _id.asStateFlow()
    override val isAvailable = true
    override suspend fun discoverPeers(timeoutMs: Long) {}
    override suspend fun connect(device: P2pDevice): Boolean = false
    override suspend fun disconnect() {}
    override fun close() {}
}
actual fun createWifiConnectionManager(): WifiConnectionManager = WasmWifiConnectionManager()
actual fun createWifiP2pManager(): WifiP2pManager = WasmWifiP2pManager()
