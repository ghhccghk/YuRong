package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.network.NetworkLog

/**
 * 把实车 BLE 通知解析成 UI 可消费的状态流。
 *
 * 只处理整车服务/特征上的通知；投屏仪表通知不会污染车辆状态。
 */
class BleVehicleStatusSource(
    adapter: BleAdapter,
    private val scope: CoroutineScope,
) : VehicleStatusSource {

    private val _status = MutableStateFlow<TtpInfo?>(null)
    override val status: StateFlow<TtpInfo?> = _status.asStateFlow()

    override val connected: StateFlow<Boolean> = adapter.connectionState
        .map { it == BleConnectionState.Connected || it == BleConnectionState.Ready }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val collectJob: Job = scope.launch {
        adapter.notifications.collect { notification ->
            NetworkLog.write(
                "BLE notification service=${notification.serviceUuid} " +
                    "characteristic=${notification.characteristicUuid} " +
                    "len=${notification.data.size} hex=${notification.data.toHex()}",
            )
            if (!VehicleBleConstants.isVehicleNotification(
                    notification.serviceUuid,
                    notification.characteristicUuid,
                )
            ) {
                return@collect
            }
            when (val parsed = VehicleNotifyDispatcher.dispatch(notification.data)) {
                is VehicleNotify.Ttp -> if (!parsed.isShort) {
                    _status.value = parsed.info
                    NetworkLog.write(
                        "TTP mileage rem=${parsed.info.remainingMileage} " +
                            "odo=${parsed.info.currentOdoValue} " +
                            "trip=${parsed.info.currentTripValue} " +
                            "bytes=${parsed.info.raw.size}",
                    )
                }
                else -> Unit
            }
        }
    }

    fun dispose() {
        collectJob.cancel()
    }
}
