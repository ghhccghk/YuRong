package top.ghhccghk.yadea.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.ble.BleDevice
import top.ghhccghk.yadea.ble.BleProtocol
import top.ghhccghk.yadea.mirror.*
import top.ghhccghk.yadea.model.NavDisplayConfig
import top.ghhccghk.yadea.network.FrameSender
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.network.UdpTransport
import top.ghhccghk.yadea.network.createTcpTransport
import top.ghhccghk.yadea.network.createUdpTransport
import top.ghhccghk.yadea.wifi.*

/**
 * AppViewModel - 应用全局状态管理
 */
class AppViewModel(
    private val wifiManager: WifiConnectionManager? = null,
    private val bleAdapter: BleAdapter? = null,
    private val p2pManager: WifiP2pManager? = null
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // WiFi
    private val _wifiState = MutableStateFlow(WifiConnectionState.Disconnected)
    val wifiState: StateFlow<WifiConnectionState> = _wifiState.asStateFlow()
    private val _wifiSsid = MutableStateFlow("")
    val wifiSsid: StateFlow<String> = _wifiSsid.asStateFlow()
    private val _wifiIp = MutableStateFlow("")
    val wifiIp: StateFlow<String> = _wifiIp.asStateFlow()
    private val _scanResults = MutableStateFlow<List<WifiNetworkInfo>>(emptyList())
    val scanResults: StateFlow<List<WifiNetworkInfo>> = _scanResults.asStateFlow()
    private val _isScanningWifi = MutableStateFlow(false)
    val isScanningWifi: StateFlow<Boolean> = _isScanningWifi.asStateFlow()

    // BLE
    private val _bleState = MutableStateFlow(BleConnectionState.Disconnected)
    val bleState: StateFlow<BleConnectionState> = _bleState.asStateFlow()
    private val _bleDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleDevice>> = _bleDevices.asStateFlow()
    private val _isScanningBle = MutableStateFlow(false)
    val isScanningBle: StateFlow<Boolean> = _isScanningBle.asStateFlow()
    private val _bleConnectedName = MutableStateFlow("")
    val bleConnectedName: StateFlow<String> = _bleConnectedName.asStateFlow()

    // P2P
    private val _p2pDevices = MutableStateFlow<List<P2pDevice>>(emptyList())
    val p2pDevices: StateFlow<List<P2pDevice>> = _p2pDevices.asStateFlow()
    private val _p2pConnected = MutableStateFlow(false)
    val p2pConnected: StateFlow<Boolean> = _p2pConnected.asStateFlow()
    val p2pGroupOwnerIp: StateFlow<String> = p2pManager?.groupOwnerIp ?: MutableStateFlow("")
    val p2pGroupFormed: StateFlow<Boolean> = p2pManager?.isGroupFormed ?: MutableStateFlow(false)

    // Mirror
    private val _mirrorState = MutableStateFlow(MirrorState.Idle)
    val mirrorState: StateFlow<MirrorState> = _mirrorState.asStateFlow()
    private val _mirrorStatus = MutableStateFlow("待命")
    val mirrorStatus: StateFlow<String> = _mirrorStatus.asStateFlow()
    private val _frameCount = MutableStateFlow(0)
    val frameCount: StateFlow<Int> = _frameCount.asStateFlow()
    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()
    private val _navConfig = MutableStateFlow(NavDisplayConfig())
    val navConfig: StateFlow<NavDisplayConfig> = _navConfig.asStateFlow()

    // Source & Crop
    private val _selectedSource = MutableStateFlow(0)
    val selectedSource: StateFlow<Int> = _selectedSource.asStateFlow()
    private val _cropConfig = MutableStateFlow(FrameCropConfig.FULL)
    val cropConfig: StateFlow<FrameCropConfig> = _cropConfig.asStateFlow()

    // Internal
    val frameBus = FrameBus()
    private var mirrorEngine: MirrorEngine? = null
    private var tcpTransport: TcpTransport? = null
    private var udpTransport: UdpTransport? = null

    init {
        // 持续收集 P2P 设备列表变化
        if (p2pManager != null) {
            scope.launch {
                p2pManager.discoveredPeers.collect { peers ->
                    _p2pDevices.value = peers
                }
            }
            scope.launch {
                p2pManager.connectionState.collect { state ->
                    _p2pConnected.value = (state == P2pStatus.Connected)
                }
            }
        }
    }

    // ==================== WiFi ====================
    fun scanWifi() {
        scope.launch {
            _isScanningWifi.value = true
            wifiManager?.scan()
            _scanResults.value = wifiManager?.scanResults?.value ?: emptyList()
            _isScanningWifi.value = false
        }
    }

    fun connectWifi(ssid: String, password: String) {
        scope.launch {
            _wifiState.value = WifiConnectionState.Connecting
            _wifiSsid.value = ssid
            val success = wifiManager?.connect(ssid, password) ?: false
            _wifiState.value = if (success) WifiConnectionState.Connected else WifiConnectionState.Failed
            if (success) _wifiIp.value = wifiManager?.getLocalIpAddress() ?: ""
        }
    }

    fun disconnectWifi() {
        scope.launch {
            wifiManager?.disconnect()
            _wifiState.value = WifiConnectionState.Disconnected
            _wifiSsid.value = ""
            _wifiIp.value = ""
        }
    }

    // ==================== BLE ====================
    fun scanBle() {
        scope.launch {
            _isScanningBle.value = true
            bleAdapter?.startScan(listOf(BleProtocol.SERVICE_UUID), 10_000)
            _bleDevices.value = bleAdapter?.discoveredDevices?.value ?: emptyList()
            _isScanningBle.value = false
        }
    }

    fun stopBleScan() {
        bleAdapter?.stopScan()
        _isScanningBle.value = false
    }

    fun connectBle(address: String, name: String) {
        scope.launch {
            _bleState.value = BleConnectionState.Connecting
            bleAdapter?.connect(address)
            _bleState.value = BleConnectionState.Connected
            _bleConnectedName.value = name
        }
    }

    fun disconnectBle() {
        scope.launch {
            bleAdapter?.disconnect()
            _bleState.value = BleConnectionState.Disconnected
            _bleConnectedName.value = ""
        }
    }

    // ==================== P2P ====================
    fun scanP2p() {
        if (p2pManager == null) return
        scope.launch {
            p2pManager.discoverPeers(15_000)
        }
    }

    fun connectP2p(device: P2pDevice) {
        scope.launch {
            val ok = p2pManager?.connect(device) ?: false
            _p2pConnected.value = ok
        }
    }

    // ==================== Mirror ====================
    fun selectSource(index: Int) { _selectedSource.value = index }
    fun updateCrop(config: FrameCropConfig) { _cropConfig.value = config }

    fun startMirror(targetHost: String, targetPort: Int) {
        scope.launch {
            _mirrorState.value = MirrorState.Negotiating
            _mirrorStatus.value = "正在连接仪表..."

            val tcp = createTcpTransport()
            val udp = createUdpTransport()
            tcpTransport = tcp
            udpTransport = udp

            try {
                tcp.connect(targetHost, targetPort)
            } catch (e: Exception) {
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "连接失败: ${e.message}"
                return@launch
            }

            val sender = FrameSender(tcp, udp)
            val engine = MirrorEngine(sender, frameBus) { _mirrorStatus.value = it }
            mirrorEngine = engine

            val success = engine.start(
                targetHost = targetHost,
                targetPort = targetPort,
                scope = scope,
                currentTimeProvider = {
                    DateTimeParts(2026, 10, 8, 12, 0, 0)
                }
            )

            if (success) {
                _mirrorState.value = MirrorState.Running
                _navConfig.value = engine.currentConfig
                _mirrorStatus.value = "投屏中"
                launch {
                    while (engine.isRunning) {
                        _frameCount.value = engine.sentFrameCount
                        _bytesSent.value = engine.sentByteCount.toLong()
                        kotlinx.coroutines.delay(500)
                    }
                }
            } else {
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "启动失败"
            }
        }
    }

    fun stopMirror() {
        scope.launch {
            mirrorEngine?.stop()
            _mirrorState.value = MirrorState.Stopped
            _mirrorStatus.value = "已停止"
            tcpTransport?.close()
            udpTransport?.close()
            tcpTransport = null
            udpTransport = null
            mirrorEngine = null
        }
    }

    fun submitFrame(bitmap: PlatformBitmap) { frameBus.submit(bitmap) }
    fun cleanup() { stopMirror(); frameBus.clear() }
}
