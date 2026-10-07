package top.ghhccghk.yadea.wifi

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.*

/**
 * Android WiFi P2P 管理实现
 */
class AndroidWifiP2pManager(private val context: Context) : WifiP2pManager {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _discoveredPeers = MutableStateFlow<List<P2pDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<P2pDevice>> = _discoveredPeers.asStateFlow()
    private val _connectionState = MutableStateFlow(P2pStatus.Available)
    override val connectionState: StateFlow<P2pStatus> = _connectionState.asStateFlow()
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    override val isAvailable: Boolean get() = true

    override suspend fun discoverPeers(timeoutMs: Long) {
        channel = manager.initialize(context, context.mainLooper, null)
        _isDiscovering.value = true
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) { _isDiscovering.value = false }
        })
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

actual fun createWifiP2pManager(): WifiP2pManager {
    throw UnsupportedOperationException("AndroidWifiP2pManager requires Context; instantiate directly")
}
