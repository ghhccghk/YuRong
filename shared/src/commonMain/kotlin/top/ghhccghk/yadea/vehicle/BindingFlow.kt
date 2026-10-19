package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.ble.BleDevice

enum class BindingStage {
    IDLE,
    SCANNING,
    FOUND,
    CONNECTING,
    PAIRING,
    BOUND,
    FAILED,
    CANCELLED,
}

data class BindingState(
    val stage: BindingStage = BindingStage.IDLE,
    val targetMac: String? = null,
    val candidates: List<BleDevice> = emptyList(),
    val message: String? = null,
    val isAll: Boolean = false,
    val type: Int = 0,
)

sealed interface BindingEvent {
    data class StartScan(val isAll: Boolean, val type: Int) : BindingEvent
    data class DeviceFound(val device: BleDevice) : BindingEvent
    data object ScanFinished : BindingEvent
    data object ConnectionStarted : BindingEvent
    data object Connected : BindingEvent
    data object PairingStarted : BindingEvent
    data object PairingSucceeded : BindingEvent
    data class Failed(val message: String) : BindingEvent
    data object Cancelled : BindingEvent
}

/** 绑定列表筛选，对应 `BleBicycleBinding$a$a.b`。 */
fun BleDevice.isBindableForVehicle(isAll: Boolean, type: Int): Boolean =
    when {
        isAll -> name?.startsWith("YD") == true || isScooter || isBox
        type == 2 -> isBox
        type == 0 -> bleProtocolVersion != 0 && parkingStatus in 0..1
        else -> true
    }

/**
 * 绑定流程纯状态机。扫描/GATT/配对 I/O 由平台层执行。
 */
object BindingFlow {
    fun reduce(
        state: BindingState,
        event: BindingEvent,
    ): BindingState = when (event) {
        is BindingEvent.StartScan -> state.copy(
            stage = BindingStage.SCANNING,
            targetMac = null,
            candidates = emptyList(),
            message = null,
            isAll = event.isAll,
            type = event.type,
        )

        is BindingEvent.DeviceFound -> {
            if (state.stage != BindingStage.SCANNING) {
                state
            } else if (!event.device.isBindableForVehicle(
                    isAll = state.isAll,
                    type = state.type,
                )
            ) {
                state
            } else if (state.candidates.any { it.address == event.device.address }) {
                state
            } else {
                state.copy(candidates = state.candidates + event.device)
            }
        }

        BindingEvent.ScanFinished -> state.copy(
            stage = if (state.candidates.isEmpty()) BindingStage.FAILED else BindingStage.FOUND,
            message = if (state.candidates.isEmpty()) "扫描未发现车辆" else null,
        )

        BindingEvent.ConnectionStarted -> state.copy(
            stage = BindingStage.CONNECTING,
            message = null,
        )

        BindingEvent.Connected -> state.copy(stage = BindingStage.FOUND)
        BindingEvent.PairingStarted -> state.copy(stage = BindingStage.PAIRING)
        BindingEvent.PairingSucceeded -> state.copy(
            stage = BindingStage.BOUND,
            message = "车辆绑定成功",
        )

        is BindingEvent.Failed -> state.copy(
            stage = BindingStage.FAILED,
            message = event.message,
        )

        BindingEvent.Cancelled -> state.copy(
            stage = BindingStage.CANCELLED,
            message = "已取消绑定",
        )
    }
}
