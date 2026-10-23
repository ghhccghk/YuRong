package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.network.NetworkLog

data class CachedVehicleStatus(
    val vin: String,
    val packetHex: String,
)

interface VehicleStatusCache {
    fun readVehicleStatus(): CachedVehicleStatus?
    suspend fun writeVehicleStatus(status: CachedVehicleStatus)
    suspend fun clearVehicleStatus()
}

/**
 * 把实车 BLE 通知解析成 UI 可消费的状态流。
 *
 * 只处理整车服务/特征上的通知；投屏仪表通知不会污染车辆状态。
 */
class BleVehicleStatusSource(
    adapter: BleAdapter,
    private val scope: CoroutineScope,
    private val cache: VehicleStatusCache? = null,
    private val vinFlow: Flow<String> = emptyFlow(),
) : VehicleStatusSource {

    private val _status = MutableStateFlow<TtpInfo?>(null)
    override val status: StateFlow<TtpInfo?> = _status.asStateFlow()

    override val connected: StateFlow<Boolean> = adapter.connectionState
        .map { it == BleConnectionState.Connected || it == BleConnectionState.Ready }
        .stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile
    private var currentVin = ""

    @Volatile
    private var lastCacheWriteAt = 0L

    private val cacheRestoreJob: Job = scope.launch {
        vinFlow
            .map(String::trim)
            .distinctUntilChanged()
            .collect { vin ->
                if (vin == currentVin) return@collect
                currentVin = vin
                lastCacheWriteAt = 0L
                _status.value = null
                if (vin.isBlank()) return@collect
                val cached = cache?.readVehicleStatus() ?: return@collect
                if (cached.vin != vin) return@collect
                val parsed = restore(cached) ?: return@collect
                _status.value = parsed
                NetworkLog.write("恢复车辆状态缓存 vin=$vin bytes=${parsed.raw.size}")
            }
    }

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
                    cacheStatus(parsed.info)
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

    private fun restore(cached: CachedVehicleStatus): TtpInfo? {
        val bytes = cached.packetHex.hexToBytes() ?: return null
        val parsed = VehicleNotifyDispatcher.dispatch(bytes) as? VehicleNotify.Ttp ?: return null
        return parsed.info.takeUnless { parsed.isShort }
    }

    private fun cacheStatus(info: TtpInfo) {
        val activeCache = cache ?: return
        val vin = currentVin
        if (vin.isBlank()) return
        val now = top.ghhccghk.yadea.currentMonotonicMillis()
        if (lastCacheWriteAt != 0L && now - lastCacheWriteAt < CACHE_WRITE_INTERVAL_MS) return
        lastCacheWriteAt = now
        scope.launch {
            runCatching {
                activeCache.writeVehicleStatus(
                    CachedVehicleStatus(
                        vin = vin,
                        packetHex = info.raw.toHex(),
                    ),
                )
            }
        }
    }

    fun dispose() {
        cacheRestoreJob.cancel()
        collectJob.cancel()
    }

    private companion object {
        const val CACHE_WRITE_INTERVAL_MS = 30_000L
    }
}
