package top.ghhccghk.yadea.mirror

/** 投屏自动建链阶段。 */
enum class MirrorConnectStage(val label: String) {
    Idle("空闲"),
    SendingBle("发送车端指令"),
    WaitingWifi("等待车端 Wi-Fi"),
    DiscoveringP2p("搜索 P2P 设备"),
    ConnectingP2p("连接 P2P"),
    ConnectingTcp("连接仪表 TCP"),
    Running("投屏中"),
    Failed("失败"),
    Cancelled("已取消"),
}

data class MirrorConnectState(
    val stage: MirrorConnectStage = MirrorConnectStage.Idle,
    val message: String = "",
    val targetHost: String = "",
)

sealed interface MirrorConnectEvent {
    data object Start : MirrorConnectEvent
    data object BleCommandSent : MirrorConnectEvent
    data object WifiReady : MirrorConnectEvent
    data object WifiTimeout : MirrorConnectEvent
    data class P2pFindStarted(val expectedMac: String) : MirrorConnectEvent
    data class P2pSelected(val name: String, val address: String) : MirrorConnectEvent
    data object P2pTimeout : MirrorConnectEvent
    data class TcpConnecting(val host: String) : MirrorConnectEvent
    data object TcpConnected : MirrorConnectEvent
    data class Failed(val message: String) : MirrorConnectEvent
    data object Cancel : MirrorConnectEvent
}

/**
 * 投屏自动建链的纯状态机。
 *
 * 实际的 BLE 写、TTP 轮询、P2P 扫描和 TCP 连接由 AppViewModel 执行；
 * 这里只负责阶段、提示和目标地址，便于后续用假时钟单测。
 */
object MirrorConnectFlow {
    fun reduce(state: MirrorConnectState, event: MirrorConnectEvent): MirrorConnectState =
        when (event) {
            MirrorConnectEvent.Start -> state.copy(
                stage = MirrorConnectStage.SendingBle,
                message = "正在发送打开 Wi-Fi/P2P 指令",
                targetHost = "",
            )
            MirrorConnectEvent.BleCommandSent -> state.copy(
                stage = MirrorConnectStage.WaitingWifi,
                message = "等待车辆打开 Wi-Fi（wifiStatusNew=2）",
            )
            MirrorConnectEvent.WifiReady -> state.copy(
                stage = MirrorConnectStage.DiscoveringP2p,
                message = "车辆 Wi-Fi 已就绪，开始搜索 P2P",
            )
            MirrorConnectEvent.WifiTimeout -> state.copy(
                stage = MirrorConnectStage.Failed,
                message = "等待车辆 Wi-Fi 超时，请确认车辆已连接后重试",
            )
            is MirrorConnectEvent.P2pFindStarted -> state.copy(
                stage = MirrorConnectStage.DiscoveringP2p,
                message = "正在搜索 P2P 设备，目标 MAC ${event.expectedMac.ifBlank { "未知" }}",
            )
            is MirrorConnectEvent.P2pSelected -> state.copy(
                stage = MirrorConnectStage.ConnectingP2p,
                message = "正在连接 ${event.name.ifBlank { event.address }}",
            )
            MirrorConnectEvent.P2pTimeout -> state.copy(
                stage = MirrorConnectStage.Failed,
                message = "未找到车辆 P2P 设备，请确认手机支持 Wi-Fi Direct",
            )
            is MirrorConnectEvent.TcpConnecting -> state.copy(
                stage = MirrorConnectStage.ConnectingTcp,
                message = "正在连接仪表 ${event.host}:8888",
                targetHost = event.host,
            )
            MirrorConnectEvent.TcpConnected -> state.copy(
                stage = MirrorConnectStage.Running,
                message = "投屏链路已建立",
            )
            is MirrorConnectEvent.Failed -> state.copy(
                stage = MirrorConnectStage.Failed,
                message = event.message,
            )
            MirrorConnectEvent.Cancel -> state.copy(
                stage = MirrorConnectStage.Cancelled,
                message = "已取消自动建链",
            )
        }
}

/**
 * `MacAddressConverter` 的等价实现：BLE MAC 第二字节 + 0x82 得到 P2P MAC。
 */
fun toP2pMacAddress(bleMac: String): String? {
    val parts = bleMac.trim().uppercase().split(':')
    if (parts.size != 6) return null
    val second = parts[1].toIntOrNull(16) ?: return null
    val converted = (second + 0x82) and 0xFF
    return parts.toMutableList().also { it[1] = converted.toString(16).padStart(2, '0') }
        .joinToString(":")
        .uppercase()
}
