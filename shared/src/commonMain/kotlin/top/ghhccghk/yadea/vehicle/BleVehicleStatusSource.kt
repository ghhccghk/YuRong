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
import kotlinx.coroutines.flow.update
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
    private val _observation = MutableStateFlow<VehicleStatusObservation?>(null)
    override val observation: StateFlow<VehicleStatusObservation?> = _observation.asStateFlow()

    override val connected: StateFlow<Boolean> = adapter.connectionState
        .map { it == BleConnectionState.Connected || it == BleConnectionState.Ready }
        .stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile
    private var currentVin = ""

    @Volatile
    private var sessionVin: String? = null

    @Volatile
    private var requiresNewSession = false

    @Volatile
    private var lastCacheWriteAt = 0L

    private val cacheRestoreJob: Job = scope.launch {
        vinFlow
            .map(String::trim)
            .distinctUntilChanged()
            .collect { vin ->
                if (vin == currentVin) return@collect
                val previousVin = currentVin
                currentVin = vin
                if (previousVin.isNotBlank() && adapter.connectionState.value.isLinkActive()) {
                    // A profile change cannot relabel notifications from an existing connection.
                    requiresNewSession = true
                    sessionVin = null
                } else if (!requiresNewSession && adapter.connectionState.value.isLinkActive()) {
                    sessionVin = vin.takeIf { it.isNotBlank() }
                }
                lastCacheWriteAt = 0L
                _status.value = null
                publishObservation(vin, null, VehicleStatusOrigin.STALE)
                if (vin.isBlank()) return@collect
                val cached = cache?.readVehicleStatus() ?: return@collect
                if (cached.vin != vin) return@collect
                val parsed = decodeCachedVehicleStatus(cached.packetHex) ?: return@collect
                _status.value = parsed
                publishObservation(vin, parsed, VehicleStatusOrigin.CACHED_TTP)
                NetworkLog.write("恢复车辆状态缓存 bytes=${parsed.raw.size}")
            }
    }

    private val sessionJob: Job = scope.launch {
        adapter.connectionState.collect { state ->
            when {
                state == BleConnectionState.Connecting -> {
                    requiresNewSession = false
                    sessionVin = currentVin.takeIf { it.isNotBlank() }
                }
                state.isLinkActive() -> if (!requiresNewSession && sessionVin == null) {
                    sessionVin = currentVin.takeIf { it.isNotBlank() }
                }
                else -> {
                    requiresNewSession = false
                    sessionVin = null
                    publishObservation(currentVin, null, VehicleStatusOrigin.STALE)
                }
            }
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
                    val boundVin = sessionVin
                    if (requiresNewSession || (currentVin.isNotBlank() &&
                            (boundVin != currentVin || !adapter.connectionState.value.isLinkActive()))) return@collect
                    _status.value = parsed.info
                    if (!boundVin.isNullOrBlank()) {
                        publishObservation(boundVin, parsed.info, VehicleStatusOrigin.LIVE_TTP)
                        cacheStatus(boundVin, parsed.info)
                    }
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

    private fun publishObservation(vin: String, info: TtpInfo?, origin: VehicleStatusOrigin) {
        _observation.update { previous ->
            VehicleStatusObservation(vin, info, origin, (previous?.sequence ?: 0L) + 1L)
        }
    }

    private fun BleConnectionState.isLinkActive(): Boolean =
        this == BleConnectionState.Connected || this == BleConnectionState.Ready

    private fun cacheStatus(vin: String, info: TtpInfo) {
        val activeCache = cache ?: return
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
        sessionJob.cancel()
        collectJob.cancel()
    }

    private companion object {
        const val CACHE_WRITE_INTERVAL_MS = 30_000L
    }
}
