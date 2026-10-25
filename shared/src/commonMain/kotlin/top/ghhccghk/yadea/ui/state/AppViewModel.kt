package top.ghhccghk.yadea.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.concurrent.Volatile
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.ble.BleDevice
import top.ghhccghk.yadea.mirror.*
import top.ghhccghk.yadea.carsetting.VehicleSettingCommand
import top.ghhccghk.yadea.carsetting.VehicleSettingCommandSink
import top.ghhccghk.yadea.instrumentskin.InstrumentImagePicker
import top.ghhccghk.yadea.model.NavDisplayConfig
import top.ghhccghk.yadea.network.FrameSender
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.network.UdpTransport
import top.ghhccghk.yadea.network.createTcpTransport
import top.ghhccghk.yadea.network.createUdpTransport
import top.ghhccghk.yadea.vehicle.Clock
import top.ghhccghk.yadea.vehicle.BindingEvent
import top.ghhccghk.yadea.vehicle.BindingFlow
import top.ghhccghk.yadea.vehicle.BindingState
import top.ghhccghk.yadea.vehicle.PublicState
import top.ghhccghk.yadea.vehicle.VehicleBleTokenProvider
import top.ghhccghk.yadea.vehicle.VehicleAccountRoleProvider
import top.ghhccghk.yadea.vehicle.VehicleConnectionCoordinator
import top.ghhccghk.yadea.vehicle.VehicleLocalTimeProvider
import top.ghhccghk.yadea.vehicle.VehicleTokenPairingStep
import top.ghhccghk.yadea.vehicle.TtpInfo
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
    private val instrumentImagePicker: InstrumentImagePicker? = null,
    private val bleTokenProvider: VehicleBleTokenProvider? = null,
    private val vehicleAccountRoleProvider: VehicleAccountRoleProvider? = null,
    private val vehicleVinProvider: () -> String = { "" },
    private val vehicleBikeIdProvider: () -> Long = { 0L },
    private val currentUserIdProvider: () -> Long = { 0L },
    private val vehicleMacProvider: () -> String = { "" },
    private val bikeBondTypeHexProvider: () -> String = { "C8" },
    private val settingCommandSink: VehicleSettingCommandSink? = null,
    private val vehicleStatusProvider: () -> TtpInfo? = { null },
    private val onVehicleConnectionEstablished: (mac: String, name: String, binding: Boolean) -> Unit =
        { _, _, _ -> },
) {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val instrumentTheme = InstrumentThemeController(
        scope = scope,
        imagePicker = instrumentImagePicker,
        interactionGuard = {
            when (_mirrorState.value) {
                MirrorState.Negotiating,
                MirrorState.Running,
                -> "请先停止当前投屏会话，仪表同一时间只允许一个数据会话"

                else -> null
            }
        },
    )

    @Volatile
    private var connectedDeviceRealMac = ""

    @Volatile
    private var connectedDeviceAdvertisedProtocolVersion = 0

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
    private val _bleConnectedAddress = MutableStateFlow("")
    val bleConnectedAddress: StateFlow<String> = _bleConnectedAddress.asStateFlow()
    private val _bindingState = MutableStateFlow(BindingState())
    val bindingState: StateFlow<BindingState> = _bindingState.asStateFlow()
    private var pendingBinding = false
    private var bindingScanJob: Job? = null
    private var bleConnectJob: Job? = null
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
    private val _mirrorConnectState = MutableStateFlow(MirrorConnectState())
    val mirrorConnectState: StateFlow<MirrorConnectState> = _mirrorConnectState.asStateFlow()
    private var autoMirrorJob: Job? = null
    private val _frameCount = MutableStateFlow(0)
    val frameCount: StateFlow<Int> = _frameCount.asStateFlow()
    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()
    private val _navConfig = MutableStateFlow(NavDisplayConfig())
    val navConfig: StateFlow<NavDisplayConfig> = _navConfig.asStateFlow()
    private val _mirrorMetrics = MutableStateFlow(MirrorMetricsSnapshot())
    val mirrorMetrics: StateFlow<MirrorMetricsSnapshot> = _mirrorMetrics.asStateFlow()
    private val acceptingFramesFrom = MutableStateFlow<top.ghhccghk.yadea.mirror.FrameSource?>(null)
    private val cleaningUp = MutableStateFlow(false)
    @Volatile private var mirrorSessionJob: Job? = null
    @Volatile private var stopMirrorJob: Job? = null

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
    @Volatile private var currentFrameSource: top.ghhccghk.yadea.mirror.FrameSource? = null
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
                bikeBondTypeHexProvider = bikeBondTypeHexProvider,
                pairingStep = VehicleTokenPairingStep(
                    tokenProvider = bleTokenProvider,
                    accountRoleProvider = vehicleAccountRoleProvider,
                    vinProvider = vehicleVinProvider,
                    bikeIdProvider = vehicleBikeIdProvider,
                    currentUserIdProvider = currentUserIdProvider,
                    vehicleMacProvider = {
                        connectedDeviceRealMac.ifBlank { vehicleMacProvider() }
                    },
                    timeProvider = VehicleLocalTimeProvider { getCurrentTime() },
                    onDebug = ::debug,
                ),
                onDebug = ::debug,
            )
            scope.launch {
                bleAdapter.discoveredDevices.collect { devices ->
                    _bleDevices.value = devices
                    for (device in devices) {
                        _bindingState.value = BindingFlow.reduce(
                            _bindingState.value,
                            BindingEvent.DeviceFound(device),
                        )
                    }
                }
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
                        if (pendingBinding) {
                            _bindingState.value = BindingFlow.reduce(
                                _bindingState.value,
                                BindingEvent.Failed(
                                    failure.message.ifBlank { "车辆绑定失败" },
                                ),
                            )
                            pendingBinding = false
                        }
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
                    if (public == PublicState.CONNECTED) {
                        val mac = _bleConnectedAddress.value
                        val name = _bleConnectedName.value
                        val binding = pendingBinding
                        if (binding) {
                            _bindingState.value = BindingFlow.reduce(
                                _bindingState.value,
                                BindingEvent.PairingSucceeded,
                            )
                        }
                        pendingBinding = false
                        onVehicleConnectionEstablished(mac, name, binding)
                    }
                }
            }
        }
        if (p2pManager != null) {
            scope.launch { p2pManager.discoveredPeers.collect { _p2pDevices.value = it } }
            scope.launch { p2pManager.connectionState.collect { _p2pConnected.value = (it == P2pStatus.Connected) } }
            scope.launch {
                p2pManager.groupOwnerIp.collect { host -> instrumentTheme.suggestTargetHost(host) }
            }
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
            // 原 App 扫描不设置服务过滤；1AFF 广播中的协议版本决定后续状态通道。
            bleAdapter?.startScan(emptyList(), 10_000)
        }
    }

    fun stopBleScan() { bleAdapter?.stopScan(); _isScanningBle.value = false }

    fun connectBle(address: String, name: String, binding: Boolean = false) {
        bleConnectJob?.cancel()
        _bleConnectedName.value = name
        _bleConnectedAddress.value = address
        _bleFailureMessage.value = null
        pendingBinding = binding
        bleConnectJob = scope.launch {
            val discoveredDevice = resolveVehicleAdvertisement(address)
            connectedDeviceRealMac = discoveredDevice?.realMac.orEmpty()
            connectedDeviceAdvertisedProtocolVersion =
                discoveredDevice?.bleProtocolVersion ?: 0
            debug(
                "连接车辆广播参数：" +
                    "advertisedProtocolVersion=$connectedDeviceAdvertisedProtocolVersion " +
                    "parkingStatus=${discoveredDevice?.parkingStatus ?: -1}",
            )
            if (binding) {
                _bindingState.value = BindingFlow.reduce(
                    _bindingState.value,
                    BindingEvent.ConnectionStarted,
                )
            }
            val coordinator = vehicleConnection
            if (coordinator == null) {
                _bleFailureMessage.value = "当前平台未提供 BLE 适配器"
                _bleState.value = BleConnectionState.Failed
                return@launch
            }
            coordinator.connect(address, name)
        }
    }

    /**
     * 自动连接上次车辆时通常没有扫描缓存，此时短暂无过滤扫描一次，
     * 以取得 `1AFF` 广播中的 BLE 协议版本；超时后仍允许直接连接。
     */
    private suspend fun resolveVehicleAdvertisement(address: String): BleDevice? {
        val adapter = bleAdapter ?: return null
        val cached = adapter.discoveredDevices.value
            .firstOrNull { it.address.equals(address, ignoreCase = true) }
        if (cached != null) return cached

        _isScanningBle.value = true
        return try {
            adapter.startScan(emptyList(), 3_000)
            withTimeoutOrNull(3_000L) {
                adapter.discoveredDevices.first { devices ->
                    devices.any { it.address.equals(address, ignoreCase = true) }
                }.firstOrNull { it.address.equals(address, ignoreCase = true) }
            }
        } finally {
            adapter.stopScan()
            _isScanningBle.value = false
        }
    }

    fun disconnectBle() {
        scope.launch {
            bleConnectJob?.cancel()
            bleConnectJob = null
            bindingScanJob?.cancel()
            bindingScanJob = null
            vehicleConnection?.disconnect()
            _bleFailureMessage.value = null
            if (vehicleConnection == null) {
                bleAdapter?.disconnect()
                _bleState.value = BleConnectionState.Disconnected
            }
            _bleConnectedName.value = ""
            _bleConnectedAddress.value = ""
            pendingBinding = false
            _bindingState.value = BindingFlow.reduce(
                _bindingState.value,
                BindingEvent.Cancelled,
            )
        }
    }

    fun startBindingScan(
        isAll: Boolean = true,
        type: Int = 0,
        timeoutMs: Long = 15_000L,
    ) {
        bindingScanJob?.cancel()
        _bindingState.value = BindingFlow.reduce(
            _bindingState.value,
            BindingEvent.StartScan(isAll = isAll, type = type),
        )
        bleAdapter?.startScan(emptyList(), timeoutMs)
        bindingScanJob = scope.launch {
            kotlinx.coroutines.delay(timeoutMs)
            bleAdapter?.stopScan()
            _bindingState.value = BindingFlow.reduce(
                _bindingState.value,
                BindingEvent.ScanFinished,
            )
        }
    }

    fun bindBle(device: BleDevice) {
        _bindingState.value = BindingFlow.reduce(
            _bindingState.value,
            BindingEvent.ConnectionStarted,
        )
        connectBle(device.address, device.name ?: "未知", binding = true)
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
        runCatching { source?.release() }
            .onFailure { debug("释放帧源失败: ${it.message}") }
        runCatching { screenCaptureAuthorizer?.releaseScreenCapture() }
            .onFailure { debug("释放屏幕录制授权失败: ${it.message}") }
    }

    private fun submitSourceFrame(
        source: top.ghhccghk.yadea.mirror.FrameSource,
        bitmap: PlatformBitmap,
    ) {
        val capturedAt = top.ghhccghk.yadea.currentMonotonicMillis()
        val buffered = frameBus.submit(bitmap, capturedAt)
        if (acceptingFramesFrom.value !== source) {
            frameBus.discardIf(buffered)
        }
    }

    /**
     * 原 App 的一键投屏链路：
     * BLE 0xFD -> TTP wifiStatusNew=2 -> P2P -> TCP -> MirrorEngine。
     */
    fun startAutoMirror() {
        if (autoMirrorJob?.isActive == true) return
        if (instrumentTheme.state.value.busy) {
            publishMirrorConnect(MirrorConnectEvent.Failed("请先停止仪表壁纸或皮肤任务"))
            return
        }
        val sink = settingCommandSink
        val p2p = p2pManager
        if (sink == null) {
            publishMirrorConnect(MirrorConnectEvent.Failed("车辆设置通道未接入"))
            return
        }
        if (p2p == null || !p2p.isAvailable) {
            publishMirrorConnect(MirrorConnectEvent.Failed("当前设备不支持 Wi-Fi P2P"))
            return
        }
        if (bleState.value != BleConnectionState.Connected &&
            bleState.value != BleConnectionState.Ready
        ) {
            publishMirrorConnect(MirrorConnectEvent.Failed("请先连接车辆 BLE"))
            return
        }
        autoMirrorJob = scope.launch {
            publishMirrorConnect(MirrorConnectEvent.Start)
            val sent = runCatching {
                sink.send(
                    VehicleSettingCommand(
                        commandType = "COMMAND_OPEN_WIFI_P2P_NEW",
                        commandId = 0xFD,
                    ),
                )
            }.getOrDefault(false)
            if (!sent) {
                publishMirrorConnect(MirrorConnectEvent.Failed("发送 Wi-Fi/P2P 指令失败"))
                return@launch
            }
            publishMirrorConnect(MirrorConnectEvent.BleCommandSent)

            val ready = withTimeoutOrNull(WIFI_READY_TIMEOUT_MS) {
                while (vehicleStatusProvider()?.extended?.wifiStatusNew != 2) {
                    delay(STATUS_POLL_INTERVAL_MS)
                }
                true
            } ?: false
            if (!ready) {
                publishMirrorConnect(MirrorConnectEvent.WifiTimeout)
                return@launch
            }
            publishMirrorConnect(MirrorConnectEvent.WifiReady)

            val expectedMac = toP2pMacAddress(bleConnectedAddress.value).orEmpty()
            publishMirrorConnect(MirrorConnectEvent.P2pFindStarted(expectedMac))
            runCatching { p2p.discoverPeers(P2P_DISCOVER_TIMEOUT_MS) }
            val peers = p2p.discoveredPeers.value
            val peer = peers.firstOrNull {
                expectedMac.isNotBlank() && it.address.equals(expectedMac, ignoreCase = true)
            } ?: peers.firstOrNull()
            if (peer == null) {
                publishMirrorConnect(MirrorConnectEvent.P2pTimeout)
                return@launch
            }
            publishMirrorConnect(MirrorConnectEvent.P2pSelected(peer.name, peer.address))
            val connected = runCatching { p2p.connect(peer) }.getOrDefault(false)
            if (!connected) {
                publishMirrorConnect(MirrorConnectEvent.P2pTimeout)
                return@launch
            }
            val host = withTimeoutOrNull(P2P_CONNECT_TIMEOUT_MS) {
                while (p2p.groupOwnerIp.value.isBlank()) {
                    delay(STATUS_POLL_INTERVAL_MS)
                }
                p2p.groupOwnerIp.value
            }
            if (host.isNullOrBlank()) {
                publishMirrorConnect(MirrorConnectEvent.P2pTimeout)
                return@launch
            }

            publishMirrorConnect(MirrorConnectEvent.TcpConnecting(host))
            startMirror(host)
            val result = withTimeoutOrNull(TCP_CONNECT_TIMEOUT_MS) {
                mirrorState.first {
                    it == MirrorState.Running || it == MirrorState.Failed
                }
            }
            if (result == MirrorState.Running) {
                publishMirrorConnect(MirrorConnectEvent.TcpConnected)
            } else {
                publishMirrorConnect(
                    MirrorConnectEvent.Failed(
                        mirrorStatus.value.ifBlank { "TCP 连接失败" },
                    ),
                )
            }
        }
    }

    fun cancelAutoMirror() {
        autoMirrorJob?.cancel()
        autoMirrorJob = null
        publishMirrorConnect(MirrorConnectEvent.Cancel)
    }

    private fun publishMirrorConnect(event: MirrorConnectEvent) {
        _mirrorConnectState.value = MirrorConnectFlow.reduce(_mirrorConnectState.value, event)
    }

    fun startMirror(targetHost: String, tcpControlPort: Int = 8888, udpImagePort: Int = 8889) {
        if (cleaningUp.value) return
        if (instrumentTheme.state.value.busy) {
            _mirrorState.value = MirrorState.Failed
            _mirrorStatus.value = "请先停止仪表壁纸或皮肤任务"
            return
        }
        if (_mirrorState.value == MirrorState.Running || _mirrorState.value == MirrorState.Negotiating) {
            debug("忽略重复的 startMirror 请求")
            return
        }
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

        _mirrorState.value = MirrorState.Negotiating
        _mirrorStatus.value = "正在准备投屏源..."
        mirrorSessionJob = scope.launch {
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
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                screenCaptureAuthorizer?.releaseScreenCapture()
                debug("投屏源准备失败: ${e.message}")
                _mirrorState.value = MirrorState.Failed
                _mirrorStatus.value = "投屏源不可用: ${e.message}"
                return@launch
            }
            if (cleaningUp.value || !currentCoroutineContext().isActive) {
                releaseFrameSource(source)
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
                    } catch (cancellation: CancellationException) {
                        throw cancellation
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
            } catch (cancellation: CancellationException) {
                releaseFrameSource(source)
                currentFrameSource = null
                tcp.close()
                udp.close()
                tcpTransport = null
                udpTransport = null
                throw cancellation
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
            _mirrorMetrics.value = engine.metrics.value

            val success = try {
                engine.start(
                    targetHost = targetHost,
                    targetPort = udpImagePort,
                    scope = scope,
                    currentTimeProvider = { getCurrentTime() },
                )
            } catch (cancellation: CancellationException) {
                engine.release()
                releaseFrameSource(source)
                currentFrameSource = null
                tcp.close()
                udp.close()
                tcpTransport = null
                udpTransport = null
                mirrorEngine = null
                throw cancellation
            }

            if (!success) {
                engine.release()
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
                acceptingFramesFrom.value = source
                frameBus.clear()
                runCatching { source.updateTargetFps(engine.effectiveFps) }
                    .onFailure { debug("更新帧源目标帧率失败: ${it.message}") }
                source.start { bitmap -> submitSourceFrame(source, bitmap) }
                _mirrorState.value = MirrorState.Running
                _navConfig.value = engine.currentConfig
                _mirrorStatus.value = "投屏中"
                debug("投屏启动成功 (${engine.effectiveFps}fps)")
                scope.launch {
                    while (engine.isRunning) {
                        _mirrorMetrics.value = engine.metrics.value
                        _frameCount.value = engine.sentFrameCount
                        _bytesSent.value = engine.sentByteCount
                        _navConfig.value = engine.currentConfig
                        kotlinx.coroutines.delay(500)
                    }
                    _mirrorMetrics.value = engine.metrics.value
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                debug("帧源启动失败: ${e.message}")
                acceptingFramesFrom.value = null
                frameBus.discardLatestFrame()
                engine.stop()
                _mirrorMetrics.value = engine.metrics.value
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
        if (cleaningUp.value || currentFrameSource !== source) return
        debug("投屏源运行失败: $message")
        stopMirrorInternal(MirrorState.Failed, "投屏源中断: $message")
    }

    fun stopMirror() {
        stopMirrorInternal(MirrorState.Stopped, "已停止")
    }

    private fun stopMirrorInternal(finalState: MirrorState, statusMessage: String) {
        if (cleaningUp.value || stopMirrorJob?.isActive == true) return
        stopMirrorJob = scope.launch {
            val source = currentFrameSource
            currentFrameSource = null
            acceptingFramesFrom.value = null
            frameBus.discardLatestFrame()
            releaseFrameSource(source)

            val session = mirrorSessionJob
            mirrorSessionJob = null
            session?.cancelAndJoin()

            val engine = mirrorEngine
            mirrorEngine = null
            try {
                if (engine != null) {
                    _mirrorMetrics.value = engine.metrics.value
                    engine.stop()
                    _mirrorMetrics.value = engine.metrics.value
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                debug("停止投屏失败: ${e.message}")
            } finally {
                frameBus.discardLatestFrame()
                tcpTransport?.close()
                udpTransport?.close()
                tcpTransport = null
                udpTransport = null
                if (!cleaningUp.value) {
                    _mirrorState.value = finalState
                    _mirrorStatus.value = statusMessage
                }
            }
        }
    }

    fun submitFrame(bitmap: PlatformBitmap) {
        if (cleaningUp.value) {
            bitmap.recycle()
            return
        }
        val buffered = frameBus.submit(bitmap)
        if (cleaningUp.value) {
            frameBus.discardIf(buffered)
        }
    }

    /** Immediate cleanup for Compose/Activity destruction; it never waits for NAV_END. */
    fun cleanup() {
        if (!cleaningUp.compareAndSet(expect = false, update = true)) return
        acceptingFramesFrom.value = null
        frameBus.discardLatestFrame()
        autoMirrorJob?.cancel()
        autoMirrorJob = null
        stopMirrorJob?.cancel()
        stopMirrorJob = null
        mirrorSessionJob?.cancel()
        mirrorSessionJob = null

        val source = currentFrameSource
        currentFrameSource = null
        runCatching { releaseFrameSource(source) }
            .onFailure { debug("清理帧源失败: ${it.message}") }

        val engine = mirrorEngine
        mirrorEngine = null
        engine?.release()
        _mirrorMetrics.value = engine?.metrics?.value ?: _mirrorMetrics.value
        tcpTransport?.close()
        udpTransport?.close()
        tcpTransport = null
        udpTransport = null
        instrumentTheme.close()
        _mirrorState.value = MirrorState.Stopped
        scope.cancel()
    }
    private companion object {
        const val WIFI_READY_TIMEOUT_MS = 15_000L
        const val P2P_DISCOVER_TIMEOUT_MS = 15_000L
        const val P2P_CONNECT_TIMEOUT_MS = 15_000L
        const val TCP_CONNECT_TIMEOUT_MS = 15_000L
        const val STATUS_POLL_INTERVAL_MS = 500L
    }
}


