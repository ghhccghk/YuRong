package top.ghhccghk.yadea.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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
import top.ghhccghk.yadea.vehicle.Clock
import top.ghhccghk.yadea.vehicle.PublicState
import top.ghhccghk.yadea.vehicle.VehicleBleTokenProvider
import top.ghhccghk.yadea.vehicle.VehicleBleConstants
import top.ghhccghk.yadea.vehicle.VehicleConnectionCoordinator
import top.ghhccghk.yadea.vehicle.VehicleLocalTimeProvider
import top.ghhccghk.yadea.vehicle.VehicleTokenPairingStep
import top.ghhccghk.yadea.wifi.*

expect fun getCurrentTime(): DateTimeParts

class AppViewModel(
    private val wifiManager: WifiConnectionManager? = null,
    private val bleAdapter: BleAdapter? = null,
    private val p2pManager: WifiP2pManager? = null,
    private val frameSourceFactory: top.ghhccghk.yadea.mirror.FrameSourceFactory? = null,
    private val permissionChecker: PermissionChecker? = null,
    private val screenCaptureAuthorizer: ScreenCaptureAuthorizer? = null,
    private val mediaSourcePicker: MediaSourcePicker? = null,
    private val bleTokenProvider: VehicleBleTokenProvider? = null,
    private val vehicleVinProvider: () -> String = { "" },
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    private fun debug(msg: String) {
        val line = "[${top.ghhccghk.yadea.currentMonotonicMillis() % 100000}] $msg"
        _debugLog.value = (_debugLog.value + line).takeLast(20)
        logToFile(line)
    }

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

    private val _bleState = MutableStateFlow(BleConnectionState.Disconnected)
    val bleState: StateFlow<BleConnectionState> = _bleState.asStateFlow()
    private val _bleDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val bleDevices: StateFlow<List<BleDevice>> = _bleDevices.asStateFlow()
    private val _isScanningBle = MutableStateFlow(false)
    val isScanningBle: StateFlow<Boolean> = _isScanningBle.asStateFlow()
    private val _bleConnectedName = MutableStateFlow("")
    val bleConnectedName: StateFlow<String> = _bleConnectedName.asStateFlow()
    private val _bleFailureMessage = MutableStateFlow<String?>(null)
    val bleFailureMessage: StateFlow<String?> = _bleFailureMessage.asStateFlow()
    private var vehicleConnection: VehicleConnectionCoordinator? = null

    private val _p2pDevices = MutableStateFlow<List<P2pDevice>>(emptyList())
    val p2pDevices: StateFlow<List<P2pDevice>> = _p2pDevices.asStateFlow()
    private val _p2pConnected = MutableStateFlow(false)
    val p2pConnected: StateFlow<Boolean> = _p2pConnected.asStateFlow()
    private val _p2pScanStatus = MutableStateFlow("空闲")
    val p2pScanStatus: StateFlow<String> = _p2pScanStatus.asStateFlow()
    // 与 p2pScanStatus 文案同步的布尔状态，供下拉刷新等界面判断 P2P 是否仍在发现中。
    private val _isScanningP2p = MutableStateFlow(false)
    val isScanningP2p: StateFlow<Boolean> = _isScanningP2p.asStateFlow()
    val p2pGroupOwnerIp: StateFlow<String> = p2pManager?.groupOwnerIp ?: MutableStateFlow("")
    val p2pGroupFormed: StateFlow<Boolean> = p2pManager?.isGroupFormed ?: MutableStateFlow(false)

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

    private val _selectedSource = MutableStateFlow(
        frameSourceFactory?.capabilities
            ?.firstOrNull { it.supported }
            ?.type
            ?.ordinal
            ?: 0
    )
    val selectedSource: StateFlow<Int> = _selectedSource.asStateFlow()
    val sourceCapabilities: List<FrameSourceCapability> =
        frameSourceFactory?.capabilities ?: emptyList()
    private val _cropConfig = MutableStateFlow(FrameCropConfig.FULL)
    val cropConfig: StateFlow<FrameCropConfig> = _cropConfig.asStateFlow()
    private val _sourceUri = MutableStateFlow("")
    val sourceUri: StateFlow<String> = _sourceUri.asStateFlow()

    val frameBus = FrameBus()
    private var currentFrameSource: top.ghhccghk.yadea.mirror.FrameSource? = null
    private var mirrorEngine: MirrorEngine? = null
    private var tcpTransport: TcpTransport? = null
    private var udpTransport: UdpTransport? = null

    init {
        debug("AppViewModel 初始化, p2pManager=${if (p2pManager != null) "OK" else "NULL"}")
        if (bleAdapter != null) {
            vehicleConnection = VehicleConnectionCoordinator(
                adapter = bleAdapter,
                scope = scope,
                clock = Clock { top.ghhccghk.yadea.currentMonotonicMillis() },
                pairingStep = VehicleTokenPairingStep(
                    tokenProvider = bleTokenProvider,
                    vinProvider = vehicleVinProvider,
                    timeProvider = VehicleLocalTimeProvider { getCurrentTime() },
                    onDebug = ::debug,
                ),
                onDebug = ::debug,
            )
            scope.launch {
                bleAdapter.discoveredDevices.collect { _bleDevices.value = it }
            }
            scope.launch {
                bleAdapter.isScanning.collect { _isScanningBle.value = it }
            }
            scope.launch {
                vehicleConnection?.failure?.collect { failure ->
                    _bleFailureMessage.value = failure?.let {
                        "断开类型=${it.disconnectType?.name ?: "-"}，" +
                            "异常=${it.exception?.name ?: "-"}，${it.message}"
                    }
                    if (failure != null) {
                        _bleState.value = BleConnectionState.Failed
                    }
                }
            }
            scope.launch {
                vehicleConnection?.publicState?.collect { public ->
                    _bleState.value = when (public) {
                        PublicState.CONNECTING -> BleConnectionState.Connecting
                        PublicState.CONNECTED -> BleConnectionState.Connected
                        PublicState.DISCONNECTED,
                        null,
                        -> if (_bleFailureMessage.value != null) {
                            BleConnectionState.Failed
                        } else {
                            BleConnectionState.Disconnected
                        }
                    }
                }
            }
        }
        if (p2pManager != null) {
            scope.launch { p2pManager.discoveredPeers.collect { _p2pDevices.value = it } }
            scope.launch { p2pManager.connectionState.collect { _p2pConnected.value = (it == P2pStatus.Connected) } }
            scope.launch {
                p2pManager.isDiscovering.collect {
                    _isScanningP2p.value = it
                    _p2pScanStatus.value = if (it) "扫描中..." else "空闲"
                }
            }
        }
    }

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

    fun scanBle() {
        if (_isScanningBle.value) {
            stopBleScan()
            return
        }
        scope.launch {
            _isScanningBle.value = true
            // 同时扫描投屏仪表与整车服务；车辆页依赖整车 b360/b362 通道。
            bleAdapter?.startScan(
                listOf(BleProtocol.SERVICE_UUID, VehicleBleConstants.SERVICE_MAIN),
                10_000,
            )
        }
    }

    fun stopBleScan() { bleAdapter?.stopScan(); _isScanningBle.value = false }

    fun connectBle(address: String, name: String) {
        _bleConnectedName.value = name
        _bleFailureMessage.value = null
        val coordinator = vehicleConnection
        if (coordinator == null) {
            _bleFailureMessage.value = "当前平台未提供 BLE 适配器"
            _bleState.value = BleConnectionState.Failed
            return
        }
        coordinator.connect(address, name)
    }

    fun disconnectBle() {
        scope.launch {
            vehicleConnection?.disconnect()
            _bleFailureMessage.value = null
            if (vehicleConnection == null) {
                bleAdapter?.disconnect()
                _bleState.value = BleConnectionState.Disconnected
            }
            _bleConnectedName.value = ""
        }
    }

    fun scanP2p() {
        if (p2pManager == null) return
        scope.launch { p2pManager.discoverPeers(15_000) }
    }

    fun connectP2p(device: P2pDevice) {
        scope.launch { p2pManager?.connect(device) }
    }

    fun selectSource(index: Int) { _selectedSource.value = index }
    val canPickVideoSource: Boolean
        get() = mediaSourcePicker?.canPickVideo == true

    fun chooseVideoSource() {
        selectSource(2)
        val picker = mediaSourcePicker
        if (picker == null) return
        picker.pickVideo { uri ->
            if (uri.isNullOrBlank()) {
                debug("未选择视频文件")
            } else {
                updateSourceUri(uri)
                debug("已选择视频: $uri")
            }
        }
    }

    fun updateSourceUri(uri: String) {
        _sourceUri.value = uri.trim()
    }

    fun updateCrop(config: FrameCropConfig) {
        if (!config.isValid) return
        _cropConfig.value = config
        currentFrameSource?.updateCropConfig(config)
    }

    private suspend fun requestScreenCaptureGrant(): ScreenCaptureGrant =
        suspendCoroutine { continuation ->
            val authorizer = screenCaptureAuthorizer
                ?: throw IllegalStateException("当前平台未提供屏幕录制授权实现")
            authorizer.requestScreenCapture(
                onResult = { continuation.resume(it) },
                onError = { continuation.resumeWithException(it) },
            )
        }

    private fun releaseFrameSource(source: top.ghhccghk.yadea.mirror.FrameSource?) {
        source?.stop()
        screenCaptureAuthorizer?.releaseScreenCapture()
    }

    fun startMirror(targetHost: String, tcpControlPort: Int = 8888, udpImagePort: Int = 8889) {
        debug("startMirror: TCP=$targetHost:$tcpControlPort, UDP=$targetHost:$udpImagePort")

        val requiredPerms = PermissionChecker.permissionsForSource(_selectedSource.value)
        val missing = permissionChecker?.getMissingPermissions(requiredPerms) ?: emptyList()
        if (missing.isNotEmpty()) {
            debug("缺少权限: $missing")
            _mirrorStatus.value = "缺少权限，请授权后重试"
            _mirrorState.value = MirrorState.Failed
            permissionChecker?.requestPermissions(requiredPerms)
            return
        }

        scope.launch {
            _mirrorState.value = MirrorState.Negotiating
            _mirrorStatus.value = "正在准备投屏源..."

            val request = when (_selectedSource.value) {
                0 -> FrameSourceRequest.Camera(cropConfig = _cropConfig.value)
                1 -> FrameSourceRequest.Screen(cropConfig = _cropConfig.value)
                2 -> FrameSourceRequest.Video(_sourceUri.value, _cropConfig.value)
                3 -> FrameSourceRequest.Navigation(_sourceUri.value, _cropConfig.value)
                else -> FrameSourceRequest.Camera(cropConfig = _cropConfig.value)
            }

            val source = try {
                val factory = frameSourceFactory
                    ?: throw IllegalStateException("当前平台未提供投屏源实现")
                val createdSource = if (
                    request is FrameSourceRequest.Screen &&
                    screenCaptureAuthorizer != null
                ) {
                    val grant = requestScreenCaptureGrant()
                    factory.createScreenSource(grant.resultCode, grant.resultData)
                } else {
                    factory.createSource(request)
                }
                createdSource.also {
                    it.updateCropConfig(_cropConfig.value)
                }
            } catch (e: Exception) {
                screenCaptureAuthorizer?.releaseScreenCapture()
                debug("投屏源准备失败: ${e.message}")
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "投屏源不可用: ${e.message}"
                return@launch
            }
            currentFrameSource = source
            source.onFailure { message -> handleSourceFailure(source, message) }

            val tcp = createTcpTransport()
            val udp = createUdpTransport()
            tcpTransport = tcp
            udpTransport = udp
            udp.bind(0)

            try {
                debug("TCP 连接 $targetHost:$tcpControlPort ...")
                var lastError: Exception? = null
                var connected = false
                for (index in 0 until 16) {
                    try {
                        tcp.connect(targetHost, tcpControlPort, timeoutMs = 700)
                        connected = true
                        if (index > 0) debug("TCP 第 ${index + 1} 次连接成功")
                        break
                    } catch (e: Exception) {
                        lastError = e
                        if (index < 15) {
                            debug("TCP 第 ${index + 1}/16 次连接失败: ${e.message}，250ms 后重试")
                            kotlinx.coroutines.delay(250)
                        }
                    }
                }
                if (!connected) throw lastError ?: IllegalStateException("TCP 连接失败")
                debug("TCP 连接成功")
            } catch (e: Exception) {
                debug("TCP 连接失败: ${e.message}")
                releaseFrameSource(source)
                currentFrameSource = null
                tcp.close()
                udp.close()
                tcpTransport = null
                udpTransport = null
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "TCP 连接失败: ${e.message}"
                return@launch
            }

            val sender = FrameSender(tcp, udp) { msg -> debug(msg) }
            val engine = MirrorEngine(sender, frameBus) { _mirrorStatus.value = it }
            mirrorEngine = engine

            val success = engine.start(
                targetHost = targetHost,
                targetPort = udpImagePort,
                scope = scope,
                currentTimeProvider = { getCurrentTime() }
            )

            if (!success) {
                releaseFrameSource(source)
                currentFrameSource = null
                tcp.close()
                udp.close()
                tcpTransport = null
                udpTransport = null
                mirrorEngine = null
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "启动失败"
                debug("投屏启动失败")
                return@launch
            }

            try {
                source.start { bitmap -> frameBus.submit(bitmap) }
                _mirrorState.value = MirrorState.Running
                _navConfig.value = engine.currentConfig
                _mirrorStatus.value = "投屏中"
                debug("投屏启动成功")
                launch {
                    while (engine.isRunning) {
                        _frameCount.value = engine.sentFrameCount
                        _bytesSent.value = engine.sentByteCount.toLong()
                        kotlinx.coroutines.delay(500)
                    }
                }
            } catch (e: Exception) {
                debug("帧源启动失败: ${e.message}")
                engine.stop()
                releaseFrameSource(source)
                currentFrameSource = null
                tcp.close()
                udp.close()
                tcpTransport = null
                udpTransport = null
                mirrorEngine = null
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "帧源启动失败: ${e.message}"
            }
        }
    }
    private fun handleSourceFailure(source: top.ghhccghk.yadea.mirror.FrameSource, message: String) {
        scope.launch {
            if (currentFrameSource !== source) return@launch
            debug("投屏源运行失败: $message")
            releaseFrameSource(source)
            currentFrameSource = null
            mirrorEngine?.stop()
            mirrorEngine = null
            tcpTransport?.close()
            udpTransport?.close()
            tcpTransport = null
            udpTransport = null
            frameBus.clear()
            _mirrorState.value = MirrorState.Failed
            _mirrorStatus.value = "投屏源中断: $message"
        }
    }

    fun stopMirror() {
        scope.launch {
            releaseFrameSource(currentFrameSource)
            currentFrameSource = null
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



