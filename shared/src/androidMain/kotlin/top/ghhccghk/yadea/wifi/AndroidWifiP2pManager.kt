package top.ghhccghk.yadea.wifi

import android.annotation.SuppressLint
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.*
import top.ghhccghk.yadea.wifi.WifiP2pManager as CommonP2pManager

@SuppressLint("MissingPermission")
class AndroidWifiP2pManager(private val context: Context) : CommonP2pManager {
    private fun d(msg: String) { Log.d("P2P", msg) }

    private val systemManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _discoveredPeers = MutableStateFlow<List<P2pDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<P2pDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionState = MutableStateFlow(P2pStatus.Available)
    override val connectionState: StateFlow<P2pStatus> = _connectionState.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isGroupFormed = MutableStateFlow(false)
    override val isGroupFormed: StateFlow<Boolean> = _isGroupFormed.asStateFlow()

    private val _groupOwnerIp = MutableStateFlow("")
    override val groupOwnerIp: StateFlow<String> = _groupOwnerIp.asStateFlow()

    override val isAvailable: Boolean get() = true

    init {
        channel = systemManager.initialize(context, context.mainLooper) {
            _connectionState.value = P2pStatus.Failed
        }
        registerReceiver()
    }

    override suspend fun discoverPeers(timeoutMs: Long) {
        d("discoverPeers 开始, timeoutMs=$timeoutMs")
        // 清理旧连接
        d("清理旧连接...")
        systemManager.cancelConnect(channel, null)
        systemManager.removeGroup(channel, null)
        _isGroupFormed.value = false
        _groupOwnerIp.value = ""

        // 等待清理完成
        d("等待清理完成 1.5s...")
        kotlinx.coroutines.delay(1500)

        _isDiscovering.value = true

        d("调用 systemManager.discoverPeers...")
        systemManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { d("discoverPeers onSuccess") }
            override fun onFailure(reason: Int) { d("discoverPeers onFailure: reason=$reason"); _isDiscovering.value = false }
        })

        if (timeoutMs > 0) {
            kotlinx.coroutines.delay(timeoutMs)
            if (_isDiscovering.value) {
                systemManager.stopPeerDiscovery(channel, null)
                _isDiscovering.value = false
            }
        }
    }

    override suspend fun connect(device: P2pDevice): Boolean {
        _connectionState.value = P2pStatus.Invited
        val config = WifiP2pConfig().apply {
            deviceAddress = device.address
        }
        return try {
            systemManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
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

    private fun onConnectionEstablished() {
        systemManager.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
            if (info != null && info.groupFormed) {
                _isGroupFormed.value = true
                _groupOwnerIp.value = info.groupOwnerAddress?.hostAddress ?: ""
                _connectionState.value = P2pStatus.Connected
            }
        }
    }

    override suspend fun disconnect() {
        d("清理旧连接...")
        systemManager.cancelConnect(channel, null)
        systemManager.removeGroup(channel, null)
        _connectionState.value = P2pStatus.Available
        _isGroupFormed.value = false
        _groupOwnerIp.value = ""
    }

    override fun close() {
        unregisterReceiver()
    }

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
                        @Suppress("DEPRECATION")
                        val peers = intent.getParcelableExtra<WifiP2pDeviceList>(
                            WifiP2pManager.EXTRA_P2P_DEVICE_LIST
                        )
                        d("收到 PEERS_CHANGED 广播")
                        peers?.let { d("设备列表: ${it.deviceList.size} 个"); updatePeers(it) }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(
                            WifiP2pManager.EXTRA_NETWORK_INFO
                        )
                        if (networkInfo != null && networkInfo.isConnected) {
                            onConnectionEstablished()
                        } else {
                            _connectionState.value = P2pStatus.Available
                            _isGroupFormed.value = false
                            _groupOwnerIp.value = ""
                        }
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
    }
}

actual fun createWifiP2pManager(): CommonP2pManager {
    throw UnsupportedOperationException("AndroidWifiP2pManager requires Context; instantiate directly")
}

