package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.ble.BleGattException
import top.ghhccghk.yadea.ble.BleGattTimeoutException
import top.ghhccghk.yadea.ble.BleService

data class VehicleConnectionFailure(
    val disconnectType: DisconnectType?,
    val exception: VehicleException?,
    val message: String,
)

/**
 * 平台无关的 BLE 连接编排层。
 *
 * [ConnectFlow] 仍保持纯函数；这里只执行它返回的动作。对外 `CONNECTED` 会等
 * 服务发现、配对步骤和五步初始化任务全部结束后才发布。
 */
class VehicleConnectionCoordinator(
    private val adapter: BleAdapter,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val initTaskQueue: VehicleInitTaskQueue =
        VehicleInitTaskQueue(adapter),
    private val pairingStep: VehiclePairingStep = GattLinkPairingStep,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onDebug: (String) -> Unit = {},
) {

    private val _publicState = MutableStateFlow<PublicState?>(null)
    val publicState: StateFlow<PublicState?> = _publicState.asStateFlow()

    private val _failure = MutableStateFlow<VehicleConnectionFailure?>(null)
    val failure: StateFlow<VehicleConnectionFailure?> = _failure.asStateFlow()

    private val _deviceInfo = MutableStateFlow(VehicleDeviceInfo())
    val deviceInfo: StateFlow<VehicleDeviceInfo> = _deviceInfo.asStateFlow()

    private var state = ConnectState()
    private var connectJob: Job? = null
    private var watchdogJob: Job? = null
    private var gattEstablished = false
    private var discoveredServices: List<BleService> = emptyList()

    init {
        scope.launch {
            adapter.connectionState.collect { adapterState ->
                if (adapterState == BleConnectionState.Disconnected && gattEstablished) {
                    gattEstablished = false
                    connectJob?.cancel()
                    connectJob = scope.launch {
                        handle(ConnectEvent.GattDisconnected(state.targetMac, status = 0))
                    }
                }
            }
        }
    }

    fun connect(address: String, name: String) {
        if (!adapter.isAvailable || !adapter.isEnabled) {
            _failure.value = VehicleConnectionFailure(
                disconnectType = DisconnectType.CONNECT_FAILED,
                exception = VehicleException.BLE,
                message = if (!adapter.isAvailable) "当前设备不支持蓝牙" else "蓝牙未开启",
            )
            _publicState.value = PublicState.DISCONNECTED
            return
        }

        connectJob?.cancel()
        watchdogJob?.cancel()
        state = ConnectState()
        gattEstablished = false
        discoveredServices = emptyList()
        _failure.value = null
        onDebug("开始连接车辆：$name ($address)")
        connectJob = scope.launch {
            handle(ConnectEvent.ScanHit(address))
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        gattEstablished = false
        discoveredServices = emptyList()
        adapter.stopScan()
        adapter.disconnect()
        state = ConnectState()
        _failure.value = null
        _publicState.value = PublicState.DISCONNECTED
        onDebug("已主动断开车辆连接")
    }

    private suspend fun handle(event: ConnectEvent) {
        val transition = ConnectFlow.reduce(state, event, clock.nowMs())
        state = transition.state
        updateWatchdog()

        transition.disconnectType?.let { type ->
            val message = transition.exception?.name ?: type.name
            _failure.value = VehicleConnectionFailure(type, transition.exception, message)
            onDebug("车辆连接事件失败：$message")
        }

        val deferConnected = transition.public == PublicState.CONNECTED &&
            transition.actions.any { it is ConnectAction.RunInitTasks }
        if (!deferConnected) {
            transition.public?.let { _publicState.value = it }
        }

        for (action in transition.actions) {
            execute(action)
        }
    }

    private fun updateWatchdog() {
        if (state.link == LinkState.CONNECTING) {
            if (watchdogJob?.isActive == true) return
            watchdogJob = scope.launch {
                pause(VehicleTimeouts.SCAN_MS)
                if (state.link == LinkState.CONNECTING) {
                    onDebug("连接看门狗超时（${VehicleTimeouts.SCAN_MS}ms）")
                    connectJob?.cancel()
                    connectJob = scope.launch {
                        handle(ConnectEvent.WatchdogTimeout)
                    }
                }
            }
        } else {
            watchdogJob?.cancel()
            watchdogJob = null
        }
    }

    private suspend fun execute(action: ConnectAction) {
        when (action) {
            ConnectAction.StartConnect -> startGattConnect()
            ConnectAction.Delay500ThenDiscover -> {
                pause(VehicleTimeouts.POST_CONNECT_DELAY_MS)
                discoverServices()
            }

            ConnectAction.DiscoverServices -> discoverServices()
            ConnectAction.StartPairing -> beginPairing()
            ConnectAction.RefreshGattCache -> {
                val refreshed = adapter.refreshGattCache()
                onDebug("清理 GATT 缓存：${if (refreshed) "成功" else "平台不支持或失败"}")
            }

            is ConnectAction.ScheduleReconnect -> {
                onDebug("${action.delayMs}ms 后重连车辆")
                pause(action.delayMs)
                handle(ConnectEvent.ReconnectRequested)
            }

            ConnectAction.StopScanAndClose -> {
                adapter.stopScan()
                adapter.disconnect()
                gattEstablished = false
            }

            ConnectAction.RunInitTasks -> runInitialization()
        }
    }

    private suspend fun startGattConnect() {
        val mac = state.targetMac ?: return
        try {
            adapter.connect(mac)
            gattEstablished = true
            onDebug("GATT 已连接，等待 500ms 后发现服务")
            handle(ConnectEvent.GattConnected(mac, status = 0))
        } catch (error: CancellationException) {
            throw error
        } catch (error: BleGattException) {
            onDebug("GATT 连接失败：status=${error.status}")
            handle(ConnectEvent.GattConnected(mac, status = error.status))
        } catch (error: BleGattTimeoutException) {
            onDebug("GATT 连接超时")
            handle(ConnectEvent.ConnectTimeout)
        } catch (error: Throwable) {
            onDebug("GATT 连接异常：${error.message}")
            handle(ConnectEvent.GattConnected(mac, status = -1))
        }
    }

    private suspend fun discoverServices() {
        val mac = state.targetMac
        try {
            discoveredServices = adapter.discoverServices()
            onDebug("服务发现完成：${discoveredServices.size} 个服务")
            handle(ConnectEvent.ServicesDiscovered(mac, status = 0))
        } catch (error: CancellationException) {
            throw error
        } catch (error: BleGattException) {
            onDebug("服务发现失败：status=${error.status}")
            handle(ConnectEvent.ServicesDiscovered(mac, status = error.status))
        } catch (error: BleGattTimeoutException) {
            onDebug("服务发现超时")
            handle(ConnectEvent.ServicesDiscovered(mac, status = -1))
        } catch (error: Throwable) {
            onDebug("服务发现异常：${error.message}")
            handle(ConnectEvent.ServicesDiscovered(mac, status = -1))
        }
    }

    private suspend fun beginPairing() {
        val mac = state.targetMac
        val success = try {
            pairingStep.pair(adapter, discoveredServices, mac.orEmpty())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onDebug("配对步骤异常：${error.message}")
            false
        }

        if (success) {
            onDebug("配对步骤完成")
            handle(ConnectEvent.PairingSucceeded(mac))
        } else {
            handle(ConnectEvent.PairingFailed(mac, DisconnectType.PAIR_FAILED))
        }
    }

    private suspend fun runInitialization() {
        val report = try {
            initTaskQueue.run(discoveredServices)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onDebug("初始化队列异常：${error.message}")
            null
        }
        report?.let {
            _deviceInfo.value = it.deviceInfo
            onDebug("初始化任务完成：${it.successfulCount}/${it.results.size}")
            activateStatusNotifications()
        }
        _publicState.value = PublicState.CONNECTED
        onDebug("车辆连接可用")
    }

    /**
     * 复现原 `BleBicycleConnect.toPairCar` 握手成功后的延时状态激活流程：
     * 1 秒后重新注册状态通知，再等待 500ms 发送 `0x34` 激活查询。
     */
    private suspend fun activateStatusNotifications() {
        val profile = resolveVehicleNotificationProfile(discoveredServices)
        pause(VehicleTimeouts.POST_AUTH_STATUS_RESUBSCRIBE_MS)
        val resubscribed = runCatching {
            adapter.setNotification(
                serviceUuid = profile.serviceUuid,
                characteristicUuid = profile.notifyCharacteristicUuid,
                enable = true,
            )
        }.getOrDefault(false)
        onDebug("握手后状态通知重订阅：${if (resubscribed) "成功" else "失败"}")
        if (!resubscribed) return

        val frame = VehicleStatusQueryEncoder.encodeActivation(profile.protocolVersion)
            ?: return
        pause(VehicleTimeouts.POST_AUTH_STATUS_ACTIVATION_MS)
        val sent = runCatching {
            adapter.writeCharacteristic(
                serviceUuid = profile.serviceUuid,
                characteristicUuid = profile.queryCharacteristicUuid,
                data = frame,
                withResponse = true,
            )
        }.getOrDefault(false)
        onDebug(
            "状态激活查询：${if (sent) "已发送" else "失败"} " +
                "len=${frame.size}",
        )
    }
}

/**
 * 配对/鉴权步骤的可替换入口。
 *
 * 当前实现只确认 GATT 服务链可用；token 握手所需的帧字段仍在 smali 核对中，
 * 后续替换为真实实现时不需要改动连接状态机。
 */
fun interface VehiclePairingStep {
    suspend fun pair(adapter: BleAdapter, services: List<BleService>, mac: String): Boolean
}

object GattLinkPairingStep : VehiclePairingStep {
    override suspend fun pair(
        adapter: BleAdapter,
        services: List<BleService>,
        mac: String,
    ): Boolean =
        services.isNotEmpty()
}
