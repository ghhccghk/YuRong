package top.ghhccghk.yadea.vehicle

/**
 * 连接状态机的不可变状态。
 *
 * [connectStartedAtMs] 由扫描命中或重连时写入，用于配合 [Clock] 做假时钟超时测试。
 */
data class ConnectState(
    val link: LinkState = LinkState.IDLE,
    val targetMac: String? = null,
    val reconnectAttempts: Int = 0,
    val connectStartedAtMs: Long? = null,
)

/** 进入连接状态机的事件。`mac == null` 表示系统没有携带设备信息。 */
sealed interface ConnectEvent {
    data class ScanHit(val mac: String) : ConnectEvent
    data class GattConnected(val mac: String?, val status: Int) : ConnectEvent
    data class ServicesDiscovered(val mac: String?, val status: Int) : ConnectEvent
    data class GattDisconnected(val mac: String?, val status: Int) : ConnectEvent
    data object ConnectTimeout : ConnectEvent
    data object ScanTimeout : ConnectEvent
    data object WatchdogTimeout : ConnectEvent
    data class PairingSucceeded(val mac: String?) : ConnectEvent
    data class PairingFailed(val mac: String?, val type: DisconnectType) : ConnectEvent
    data object ReconnectRequested : ConnectEvent
}

/** 状态机只描述"该做什么"，实际 I/O 由平台编排层执行。 */
sealed interface ConnectAction {
    data object StartConnect : ConnectAction
    data object Delay500ThenDiscover : ConnectAction
    data object DiscoverServices : ConnectAction
    data object StartPairing : ConnectAction
    data object RefreshGattCache : ConnectAction
    data class ScheduleReconnect(
        val delayMs: Long = VehicleTimeouts.RECONNECT_DELAY_MS,
    ) : ConnectAction
    data object StopScanAndClose : ConnectAction
    data object RunInitTasks : ConnectAction
}

/**
 * 一次转移的结果。
 *
 * [public] 为 `null` 时表示不重复上报同一对外状态；[actions] 按顺序执行。
 */
data class ConnectTransition(
    val state: ConnectState,
    val public: PublicState? = null,
    val disconnectType: DisconnectType? = null,
    val exception: VehicleException? = null,
    val actions: List<ConnectAction> = emptyList(),
)

/**
 * 纯函数连接状态机。
 *
 * 行为要点（来自 `wi` / `connection.md`）：
 * - GATT 连上后延迟 500ms 才发现服务；
 * - 只有服务发现成功并完成配对握手后才对外上报 `CONNECTED`；
 * - 已连接后断开不重连；
 * - 只有"连接中收到断开"才最多重连 1 次；
 * - 连接超时、服务发现失败不重连。
 */
object ConnectFlow {

    /** 处理一个事件。`nowMs` 只用于记录超时起点，不读取系统时钟。 */
    fun reduce(
        state: ConnectState,
        event: ConnectEvent,
        nowMs: Long,
    ): ConnectTransition {
        val eventMac = event.mac()
        if (eventMac != null && state.targetMac != null && eventMac != state.targetMac) {
            return unchanged(state)
        }

        return when (event) {
            is ConnectEvent.ScanHit -> onScanHit(state, event.mac, nowMs)
            is ConnectEvent.GattConnected -> onGattConnected(state, event)
            is ConnectEvent.ServicesDiscovered -> onServicesDiscovered(state, event)
            is ConnectEvent.PairingSucceeded ->
                if (state.link == LinkState.CONNECTING) {
                    ConnectTransition(
                        state = state.copy(link = LinkState.CONNECTED, connectStartedAtMs = null),
                        public = PublicState.CONNECTED,
                        actions = listOf(ConnectAction.RunInitTasks),
                    )
                } else {
                    unchanged(state)
                }

            is ConnectEvent.PairingFailed ->
                if (state.link == LinkState.CONNECTING) {
                    failure(state, event.type, VehicleException.OTHER)
                } else {
                    unchanged(state)
                }

            is ConnectEvent.GattDisconnected -> onGattDisconnected(state, event)
            ConnectEvent.ConnectTimeout ->
                if (state.link == LinkState.CONNECTING) {
                    failure(state, DisconnectType.TIMEOUT_15S, VehicleException.TIMEOUT)
                } else {
                    unchanged(state)
                }

            ConnectEvent.ScanTimeout,
            ConnectEvent.WatchdogTimeout,
            -> if (state.link == LinkState.IDLE || state.link == LinkState.CONNECTING) {
                failure(
                    state = state,
                    type = DisconnectType.TIMEOUT_15S,
                    exception = VehicleException.TIMEOUT,
                    actions = listOf(ConnectAction.StopScanAndClose),
                )
            } else {
                unchanged(state)
            }

            ConnectEvent.ReconnectRequested -> onReconnectRequested(state, nowMs)
        }
    }

    /** 对外状态的单一映射入口。 */
    fun publicState(link: LinkState): PublicState? = when (link) {
        LinkState.IDLE -> null
        LinkState.CONNECTING -> PublicState.CONNECTING
        LinkState.CONNECTED -> PublicState.CONNECTED
        LinkState.FAILURE,
        LinkState.DISCONNECT,
        -> PublicState.DISCONNECTED
    }

    /**
     * 假时钟驱动的 GATT 连接超时检查。
     *
     * 返回 `null` 表示尚未到期。到期时返回与 [ConnectEvent.ConnectTimeout] 相同的转移。
     */
    fun pollTimeout(state: ConnectState, nowMs: Long): ConnectTransition? {
        if (state.link != LinkState.CONNECTING) return null
        val started = state.connectStartedAtMs ?: return null
        if (nowMs - started < VehicleTimeouts.GATT_CONNECT_MS) return null
        return failure(state, DisconnectType.TIMEOUT_15S, VehicleException.TIMEOUT)
    }

    /** 扫描超时判断；扫描起点由调用方保存，状态机不持有扫描态。 */
    fun scanTimedOut(startedAtMs: Long, nowMs: Long): Boolean =
        nowMs - startedAtMs >= VehicleTimeouts.SCAN_MS

    // ==================== 私有转移 ====================

    private fun onScanHit(state: ConnectState, mac: String, nowMs: Long): ConnectTransition {
        if (state.link != LinkState.IDLE) return unchanged(state)
        if (state.targetMac != null && state.targetMac != mac) return unchanged(state)
        return ConnectTransition(
            state = ConnectState(
                link = LinkState.CONNECTING,
                targetMac = mac,
                reconnectAttempts = 0,
                connectStartedAtMs = nowMs,
            ),
            public = PublicState.CONNECTING,
            actions = listOf(ConnectAction.StartConnect),
        )
    }

    private fun onGattConnected(
        state: ConnectState,
        event: ConnectEvent.GattConnected,
    ): ConnectTransition {
        if (state.link != LinkState.CONNECTING) return unchanged(state)
        if (event.status == 0) {
            return ConnectTransition(
                state = state,
                actions = listOf(ConnectAction.Delay500ThenDiscover),
            )
        }
        val actions = if (event.status == VehicleTimeouts.ERROR_133) {
            listOf<ConnectAction>(ConnectAction.RefreshGattCache)
        } else {
            emptyList()
        }
        return failure(
            state = state,
            type = DisconnectType.CONNECT_FAILED,
            exception = VehicleException.GATT,
            actions = actions,
        )
    }

    private fun onServicesDiscovered(
        state: ConnectState,
        event: ConnectEvent.ServicesDiscovered,
    ): ConnectTransition {
        if (state.link != LinkState.CONNECTING) return unchanged(state)
        return if (event.status == 0) {
            ConnectTransition(
                state = state,
                actions = listOf(ConnectAction.StartPairing),
            )
        } else {
            failure(state, DisconnectType.CONNECT_FAILED, VehicleException.GATT)
        }
    }

    /**
     * 断开事件。
     *
     * 只有 `CONNECTING` 状态才安排重连，且最多一次。`CONNECTED` 后断开直接进入
     * `DISCONNECT`，不再自动连接。
     */
    private fun onGattDisconnected(
        state: ConnectState,
        event: ConnectEvent.GattDisconnected,
    ): ConnectTransition = when (state.link) {
        LinkState.CONNECTED -> ConnectTransition(
            state = state.copy(link = LinkState.DISCONNECT, connectStartedAtMs = null),
            public = PublicState.DISCONNECTED,
            disconnectType = DisconnectType.TARGET_DISCONNECTED,
        )

        LinkState.CONNECTING -> if (state.reconnectAttempts < VehicleTimeouts.MAX_RECONNECT) {
            failure(
                state = state.copy(reconnectAttempts = state.reconnectAttempts + 1),
                type = DisconnectType.CONNECT_FAILED,
                exception = VehicleException.BLE,
                actions = buildList {
                    if (event.status == VehicleTimeouts.ERROR_133) {
                        add(ConnectAction.RefreshGattCache)
                    }
                    add(ConnectAction.ScheduleReconnect())
                },
            )
        } else {
            failure(
                state = state,
                type = DisconnectType.CONNECT_FAILED,
                exception = VehicleException.BLE,
            )
        }

        else -> unchanged(state)
    }

    private fun onReconnectRequested(state: ConnectState, nowMs: Long): ConnectTransition {
        if (state.link != LinkState.FAILURE) return unchanged(state)
        if (state.targetMac == null) return unchanged(state)
        if (state.reconnectAttempts > VehicleTimeouts.MAX_RECONNECT) return unchanged(state)
        return ConnectTransition(
            state = state.copy(
                link = LinkState.CONNECTING,
                connectStartedAtMs = nowMs,
            ),
            public = PublicState.CONNECTING,
            actions = listOf(ConnectAction.StartConnect),
        )
    }

    private fun failure(
        state: ConnectState,
        type: DisconnectType,
        exception: VehicleException,
        actions: List<ConnectAction> = emptyList(),
    ): ConnectTransition = ConnectTransition(
        state = state.copy(link = LinkState.FAILURE, connectStartedAtMs = null),
        public = PublicState.DISCONNECTED,
        disconnectType = type,
        exception = exception,
        actions = actions,
    )

    private fun unchanged(state: ConnectState): ConnectTransition =
        ConnectTransition(state = state)

    private fun ConnectEvent.mac(): String? = when (this) {
        is ConnectEvent.ScanHit -> mac
        is ConnectEvent.GattConnected -> mac
        is ConnectEvent.ServicesDiscovered -> mac
        is ConnectEvent.GattDisconnected -> mac
        is ConnectEvent.PairingSucceeded -> mac
        is ConnectEvent.PairingFailed -> mac
        else -> null
    }
}
