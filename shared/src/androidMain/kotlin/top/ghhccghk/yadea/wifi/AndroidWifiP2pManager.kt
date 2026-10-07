package top.ghhccghk.yadea.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.*
import top.ghhccghk.yadea.wifi.WifiP2pManager as CommonP2pManager

/**
 * Android WiFi P2P 管理实现
 *
 * 使用 android.net.wifi.p2p.WifiP2pManager 实现:
 * - 设备发现 (discoverPeers)
 * - P2P 连接 (connect)
 * - 广播接收器监听 P2P 状态
 */
@SuppressLint("MissingPermission")
class AndroidWifiP2pManager(private val context: Context) : CommonP2pManager {

    private val systemManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _discoveredPeers = MutableStateFlow<List<P2pDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<P2pDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionState = MutableStateFlow(P2pStatus.Available)
    override val connectionState: StateFlow<P2pStatus> = _connectionState.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    override val isAvailable: Boolean get() = true

    init {
        channel = systemManager.initialize(context, context.mainLooper) {
            // Channel 断开回调
            _connectionState.value = P2pStatus.Failed
        }
        registerReceiver()
    }

    override suspend fun discoverPeers(timeoutMs: Long) {
        _isDiscovering.value = true

        systemManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // 发现流程启动，等待 WIFI_P2P_PEERS_CHANGED_ACTION 广播
            }
            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
            }
        })

        // 超时自动停止
        if (timeoutMs > 0) {
            kotlinx.coroutines.delay(timeoutMs)
            if (_isDiscovering.value) {
                stopDiscovery()
            }
        }
    }

    private fun stopDiscovery() {
        systemManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { _isDiscovering.value = false }
            override fun onFailure(reason: Int) { _isDiscovering.value = false }
        })
    }

    override suspend fun connect(device: P2pDevice): Boolean {
        _connectionState.value = P2pStatus.Invited

        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }

        return try {
            systemManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _connectionState.value = P2pStatus.Connected
                }
                override fun onFailure(reason: Int) {
                    _connectionState.value = P2pStatus.Failed
                }
            })
            true
        } catch (e: Exception) {
            _connectionState.value = P2pStatus.Failed
            false
        }
    }

    override suspend fun disconnect() {
        systemManager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { _connectionState.value = P2pStatus.Available }
            override fun onFailure(reason: Int) {}
        })
        systemManager.removeGroup(channel, null)
    }

    override fun close() {
        unregisterReceiver()
    }

    // ---- 广播接收器 ----

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val peers = intent.getParcelableExtra<WifiP2pDeviceList>(
                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST
                        )
                        peers?.let { updatePeers(it) }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val connected = intent.getBooleanExtra(
                            WifiP2pManager.EXTRA_NETWORK_INFO, false
                        )
                        _connectionState.value = if (connected) P2pStatus.Connected else P2pStatus.Available
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    private fun updatePeers(deviceList: WifiP2pDeviceList) {
        val peers = deviceList.deviceList.map { device ->
            P2pDevice(
                name = device.deviceName ?: "未知设备",
                address = device.deviceAddress,
                status = when (device.status) {
                    WifiP2pDevice.CONNECTED -> P2pStatus.Connected
                    WifiP2pDevice.INVITED -> P2pStatus.Invited
                    WifiP2pDevice.FAILED -> P2pStatus.Failed
                    else -> P2pStatus.Available
                }
            )
        }
        _discoveredPeers.value = peers
        _isDiscovering.value = false
    }
}

actual fun createWifiP2pManager(): CommonP2pManager {
    throw UnsupportedOperationException("AndroidWifiP2pManager requires Context; instantiate directly")
}

