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

expect fun getCurrentTime(): DateTimeParts

class AppViewModel(
    private val wifiManager: WifiConnectionManager? = null,
    private val bleAdapter: BleAdapter? = null,
    private val p2pManager: WifiP2pManager? = null,
    private val frameSourceFactory: top.ghhccghk.yadea.mirror.FrameSourceFactory? = null
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Debug
    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    private fun debug(msg: String) {
        val line = "[${System.currentTimeMillis() % 100000}] $msg"
        _debugLog.value = (_debugLog.value + line).takeLast(20)
    }

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
    private val _p2pScanStatus = MutableStateFlow("空闲")
    val p2pScanStatus: StateFlow<String> = _p2pScanStatus.asStateFlow()
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

    private val _selectedSource = MutableStateFlow(0)
    val selectedSource: StateFlow<Int> = _selectedSource.asStateFlow()
    private val _cropConfig = MutableStateFlow(FrameCropConfig.FULL)
    val cropConfig: StateFlow<FrameCropConfig> = _cropConfig.asStateFlow()

    val frameBus = FrameBus()
    private var currentFrameSource: top.ghhccghk.yadea.mirror.FrameSource? = null
    private var mirrorEngine: MirrorEngine? = null
    private var tcpTransport: TcpTransport? = null
    private var udpTransport: UdpTransport? = null

    init {
        debug("AppViewModel 初始化, p2pManager=${if (p2pManager != null) "OK" else "NULL"}")
        debug("wifiManager=${if (wifiManager != null) "OK" else "NULL"}, bleAdapter=${if (bleAdapter != null) "OK" else "NULL"}")

        if (p2pManager != null) {
            debug("开始收集 P2P 设备列表...")
            scope.launch {
                p2pManager.discoveredPeers.collect { peers ->
                    debug("P2P 设备列表更新: ${peers.size} 个设备")
                    _p2pDevices.value = peers
                }
            }
            scope.launch {
                p2pManager.connectionState.collect { state ->
                    debug("P2P 连接状态: $state")
                    _p2pConnected.value = (state == P2pStatus.Connected)
                }
            }
            scope.launch {
                p2pManager.isDiscovering.collect { discovering ->
                    debug("P2P 扫描状态: ${if (discovering) "扫描中" else "空闲"}")
                    _p2pScanStatus.value = if (discovering) "扫描中..." else "空闲"
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
        debug("scanP2p() 被调用, p2pManager=${if (p2pManager != null) "OK" else "NULL"}")
        if (p2pManager == null) {
            _p2pScanStatus.value = "P2P 管理器未初始化!"
            return
        }
        scope.launch {
            _p2pScanStatus.value = "正在扫描..."
            debug("开始 P2P 扫描 (15秒超时)")
            try {
                p2pManager.discoverPeers(15_000)
                debug("P2P 扫描结束, 当前设备数=${_p2pDevices.value.size}")
                _p2pScanStatus.value = "扫描完成, 发现 ${_p2pDevices.value.size} 个设备"
            } catch (e: Exception) {
                debug("P2P 扫描异常: ${e.message}")
                _p2pScanStatus.value = "扫描失败: ${e.message}"
            }
        }
    }

    fun connectP2p(device: P2pDevice) {
        debug("连接 P2P 设备: ${device.name} (${device.address})")
        scope.launch {
            val ok = p2pManager?.connect(device) ?: false
            debug("P2P 连接结果: $ok")
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
                targetHost = targetHost, targetPort = targetPort, scope = scope,
                currentTimeProvider = { getCurrentTime() }
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

    private fun startFrameSource() {
        val factory = frameSourceFactory ?: return
        scope.launch {
            val source = when (_selectedSource.value) {
                0 -> factory.createCameraSource()
                2 -> factory.createVideoSource("")
                else -> factory.createCameraSource()
            }
            currentFrameSource = source
            source.start { bitmap -> frameBus.submit(bitmap) }
            debug("帧源已启动: ${_selectedSource.value}")
        }
    }

    fun submitFrame(bitmap: PlatformBitmap) { frameBus.submit(bitmap) }
    fun cleanup() { stopMirror(); frameBus.clear() }
}




