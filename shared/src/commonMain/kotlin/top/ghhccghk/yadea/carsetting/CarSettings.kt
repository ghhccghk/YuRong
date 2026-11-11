package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleStatusObservation
import top.ghhccghk.yadea.vehicle.VehicleStatusOrigin

enum class CarSettingClassification(val label: String) {
    SECURITY("安全守护"),
    LIGHT("灯光音效"),
    POWER("感应解锁"),
    RIDE_CONTROL("骑行控制"),
    BATTERY("智慧充电"),
    METER("智能仪表"),
    LABORATORY("实验室"),
    OTHER("其他"),
}

/** 云端 `BikeConfig.parentId/parentName` 对应的分组。 */
data class CarSettingGroup(
    val id: String,
    val title: String,
    val sort: Int,
)

private fun CarSettingClassification.asGroup(): CarSettingGroup =
    CarSettingGroup(name, label, ordinal)

enum class CarSettingChannel(
    val label: String,
    val description: String,
) {
    BLE(
        label = "蓝牙",
        description = "手机与车辆蓝牙连接后直接下发，需要靠近车辆并保持连接，状态通常能实时回读。",
    ),
    BLE_KEY(
        label = "蓝牙车钥匙",
        description = "由蓝牙车钥匙功能处理，需要车辆连接和相应鉴权，部分操作仍需进入原钥匙流程。",
    ),
    IOT(
        label = "车联网",
        description = "通过车联网云平台下发，不要求手机靠近，但车辆必须在线，生效可能有延迟。",
    ),
    BOTH(
        label = "蓝牙 + 车联网",
        description = "可能通过蓝牙或车联网写入，实际使用哪个通道取决于当前连接和功能实现。",
    ),
    LOCAL(
        label = "本机",
        description = "设置或操作在当前 App 内完成，或会打开 App 子页面，不直接作为车辆参数下发。",
    ),
}

/** 原 App 电池容量设置页固定提供的容量档位。 */
internal object BatteryCapacityOptions {
    val valuesAh = listOf(13, 15, 22, 25, 32, 38)
    val labels = valuesAh.map { "${it}Ah" }
}

/** Original alarm page: raw 0/1/2 -> progress 0/50/100; writes use the tick, not progress. */
internal object AlarmSensitivityMapping {
    fun progressFromRaw(raw: Int): Float? = when (raw) {
        0 -> 0f
        1 -> 50f
        2 -> 100f
        else -> null
    }

    fun tickFromProgress(progress: Float): Int? = when (progress) {
        0f -> 0
        50f -> 1
        100f -> 2
        else -> null
    }
}

internal object AdaptivePush64Mapping {
    fun checkedFromRaw(raw: Int): Boolean? = when (raw) {
        0, 2 -> false
        1 -> true
        else -> null
    }

    fun parameterForTarget(targetChecked: Boolean): Int = if (targetChecked) 1 else 0
}

internal object AdaptivePush89Mapping {
    fun checkedFromRaw(raw: Int): Boolean? = when (raw) {
        0, 1 -> false
        2 -> true
        else -> null
    }

    fun parameterForTarget(targetChecked: Boolean): Int = if (targetChecked) 2 else 0
}

sealed interface CarSettingKind {
    data class Toggle(val checked: Boolean, val valueKnown: Boolean = true) : CarSettingKind
    /** confId 9 always sends both siblings; nullable values never become protocol defaults. */
    enum class LowPowerInfoEdit { ALARM_SWITCH, THRESHOLD }
    data class LowPowerInfo(
        val alarmOn: Boolean? = null,
        val alarmPower: Int? = null,
        val edit: LowPowerInfoEdit? = null,
    ) : CarSettingKind
    enum class LowPowerPhoneEdit { SWITCH, TIMES }
    data class LowPowerPhone(
        val enabled: Boolean? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val edit: LowPowerPhoneEdit? = null,
    ) : CarSettingKind
    data class Choice(val selectedIndex: Int, val options: List<String>, val valueKnown: Boolean = true) : CarSettingKind
    data class Slider(
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val unit: String = "",
        /**
         * 非零最小值；例如安全限速只允许 `0`（不限速）或 `15..max`。
         * 滑杆经过 `1..minimumNonZero-1` 时会自动吸附到 0 或该最小值。
         */
        val minimumNonZero: Float? = null,
        val valueKnown: Boolean = true,
        /** ID58: raw parameters for its three UI positions, in original App order. */
        val speedPresets: List<Int> = emptyList(),
        /** Speed-setting model bounds must be explicit before a target can be submitted. */
        val rangeKnown: Boolean = true,
    ) : CarSettingKind

    data class Info(val value: String) : CarSettingKind
    /**
     * 自定义骑行参数（confId 55）。原 App 用一个二级页同时下发四个字段，
     * 因此这里作为一个整体条目，避免逐项写入时覆盖其它参数。
     */
    data class Customize(
        val throttleLevel: Int? = null,
        val energyLevel: Int? = null,
        val energyState: Boolean? = null,
        val tcs: Boolean? = null,
    ) : CarSettingKind {
        val valueKnown: Boolean
            get() = throttleLevel?.let { it in 0..2 } == true &&
                energyLevel?.let { it in 0..2 } == true && energyState != null && tcs != null
    }
    /** 云端下发、需要进入二级页面的入口；当前只展示云端提示。 */
    data class Entrance(val hint: String = "进入设置") : CarSettingKind

    /**
     * 氛围灯模式与颜色配置。原 App 通过 `ATMOSPHERE_CONFIG` 一次性提交，
     * 颜色内部使用 RGB 分量；`255` 在原 App 发送前会改为 `254`，因为 `0xFF`
     * 在状态报文中表示无效值。
     */
    data class Atmosphere(
        val showMode: Int = -1,
        val runMode: Int = -1,
        val singleColor: Boolean = true,
        val red1: Int = -1,
        val green1: Int = -1,
        val blue1: Int = -1,
        val red2: Int = -1,
        val green2: Int = -1,
        val blue2: Int = -1,
        val valueKnown: Boolean = false,
    ) : CarSettingKind
}

internal fun String?.isValidLowPowerTime(): Boolean {
    val value = this ?: return false
    if (value.length != 5 || value[2] != ':' ||
        value.filterIndexed { index, _ -> index != 2 }.any { it !in '0'..'9' }
    ) return false
    val hour = value.substring(0, 2).toIntOrNull() ?: return false
    val minute = value.substring(3, 5).toIntOrNull() ?: return false
    return hour in 0..23 && minute in 0..59
}

internal fun validLowPowerTimeRange(start: String?, end: String?): Boolean =
    start != null && end != null && start.isValidLowPowerTime() &&
        end.isValidLowPowerTime() && start < end

internal fun selectableLowPowerTime(value: String?): Boolean =
    value.isValidLowPowerTime() && value!!.substring(3).toInt() % 10 == 0

data class CarSettingItem(
    val id: String,
    val title: String,
    val summary: String,
    val classification: CarSettingClassification,
    val group: CarSettingGroup = classification.asGroup(),
    val channel: CarSettingChannel,
    val kind: CarSettingKind,
    val writable: Boolean = true,
    val sort: Int = 0,
    /** 云端 `BikeConfig.confId`；本地预览项可以留空。 */
    val configId: String = id,
    /** Read-only cloud companion values; never inferred from a control placeholder. */
    val geofenceConfiguration: GeofenceConfiguration? = null,
    /** Only attached to a single ID42 submission after a same-VIN eligibility read. */
    val remainingMileageEligibility: RemainingMileageEligibility? = null,
)

enum class CarSettingConfirmationStatus {
    LOCAL_UPDATED,
    CLOUD_ACCEPTED,
    VEHICLE_CONFIRMED,
    FAILED,
    UNKNOWN,
}

enum class SettingWriteState { IDLE, PREPARING, SUBMITTING, CLOUD_ACCEPTED, LOCAL_UPDATED, FAILED, CANCELLED }

enum class SettingConfirmationState {
    UNKNOWN, WAITING_READBACK, READBACK_MATCHED, READBACK_MISMATCH, CONFIRMED_BY_RESULT, FAILED, TIMED_OUT,
}

enum class SettingReadbackState { UNKNOWN, VALID, MISSING_OR_INVALID, UNSUPPORTED }
enum class SettingValueSource { LIVE_TTP, CACHED_TTP }

sealed interface VehicleSettingValue {
    data object Unknown : VehicleSettingValue
    data class Known(
        val kind: CarSettingKind,
        val vin: String,
        val sequence: Long,
        val source: SettingValueSource = SettingValueSource.LIVE_TTP,
        val live: Boolean = true,
    ) : VehicleSettingValue
}

/** Only a successfully decoded live TTP field creates a vehicle value. */
data class CarSettingValueState(
    val vehicleValue: VehicleSettingValue = VehicleSettingValue.Unknown,
    val cachedValue: VehicleSettingValue.Known? = null,
    val configuredValue: CarSettingKind? = null,
    val readbackState: SettingReadbackState = SettingReadbackState.UNKNOWN,
    val draftValue: CarSettingKind? = null,
    val submittedValue: CarSettingKind? = null,
    val operationId: Long? = null,
    val writeState: SettingWriteState = SettingWriteState.IDLE,
    val writeChannel: CarSettingWriteChannel? = null,
    val confirmationState: SettingConfirmationState = SettingConfirmationState.UNKNOWN,
    val error: String? = null,
) {
    val busy: Boolean get() = writeState in setOf(SettingWriteState.PREPARING, SettingWriteState.SUBMITTING)

    val confirmationStatus: CarSettingConfirmationStatus get() = when {
        confirmationState == SettingConfirmationState.READBACK_MATCHED ->
            CarSettingConfirmationStatus.VEHICLE_CONFIRMED
        writeState == SettingWriteState.FAILED -> CarSettingConfirmationStatus.FAILED
        writeState == SettingWriteState.CANCELLED -> CarSettingConfirmationStatus.UNKNOWN
        writeState == SettingWriteState.CLOUD_ACCEPTED -> CarSettingConfirmationStatus.CLOUD_ACCEPTED
        writeState == SettingWriteState.LOCAL_UPDATED -> CarSettingConfirmationStatus.LOCAL_UPDATED
        else -> CarSettingConfirmationStatus.UNKNOWN
    }

    fun displayKind(): CarSettingKind? = draftValue ?: submittedValue ?:
        (vehicleValue as? VehicleSettingValue.Known)?.kind ?: cachedValue?.kind ?: configuredValue

    fun editableKind(fallback: CarSettingKind): CarSettingKind = displayKind() ?: fallback
}

data class CarSettingsState(
    val items: List<CarSettingItem> = emptyList(),
    /** 本地已知、但本次云端没有下发的设置；仅用于控制页调试与实验性操作。 */
    val missingCloudItems: List<CarSettingItem> = emptyList(),
    val rawConfigJson: String = "",
    val pendingIds: Set<String> = emptySet(),
    val message: String? = null,
    val loading: Boolean = false,
    val loadedFromCloud: Boolean = false,
    val vin: String = "",
    val loadGeneration: Long = 0,
    val valueStates: Map<String, CarSettingValueState> = emptyMap(),
) {
    val confirmationStatuses: Map<String, CarSettingConfirmationStatus>
        get() = valueStates.filterValues { it.operationId != null }.mapValues { it.value.confirmationStatus }

    fun valueState(id: String): CarSettingValueState = valueStates[id] ?: CarSettingValueState()
}

/** 平台侧把设置值翻译成 BLE `CommandTempBean` 或 IoT 请求。 */
enum class CarSettingWriteChannel {
    BLE,
    CLOUD,
}

data class CarSettingWriteResult(
    val success: Boolean,
    val channel: CarSettingWriteChannel? = null,
    val confirmationStatus: CarSettingConfirmationStatus = when {
        !success -> CarSettingConfirmationStatus.FAILED
        channel == null -> CarSettingConfirmationStatus.UNKNOWN
        channel == CarSettingWriteChannel.BLE -> CarSettingConfirmationStatus.LOCAL_UPDATED
        else -> CarSettingConfirmationStatus.CLOUD_ACCEPTED
    },
)

object CarSettingItemAccess {
    private val splitSuffixes = mapOf(
        "7" to setOf("time"),
        "10" to setOf("distance"),
        "11" to setOf("distance"),
        "15" to setOf("time"),
        "21" to setOf("time"),
        "39" to setOf("time"),
        "47" to setOf("enable"),
        "52" to setOf("distance"),
        "59" to setOf("time"),
        "1" to setOf("enable", "distance"),
        "002" to setOf("enable", "level"),
        "003" to setOf("enable", "level"),
        "73" to setOf("config"),
        "60" to setOf("enable", "level"),
        "62" to setOf("level"),
        "64" to setOf("adaptive", "level"),
        "69" to setOf("fcw", "rcw", "bsd"),
        "81" to setOf("time"),
        "86" to setOf("value"),
        "89" to setOf("adaptive", "level"),
        "1005" to setOf("short", "long"),
    )

    fun canWrite(item: CarSettingItem): Boolean {
        if (!item.writable || !isStableEntryId(item)) return false
        return VehicleSettingCommands.supportsItem(item) ||
            CloudCarSettingUpdateBuilder.supports(item.configId, item.kind)
    }

    private fun isStableEntryId(item: CarSettingItem): Boolean {
        val id = item.id
        val configId = item.configId
        if (id == "cloud:$configId" || id == "missing:$configId") return true
        if (!id.startsWith("cloud:$configId:")) return true
        val suffix = id.substringAfter("cloud:$configId:")
        return suffix in splitSuffixes[configId].orEmpty()
    }
}

interface CarSettingWriter {

    suspend fun write(item: CarSettingItem): Boolean

    /** 兼容只返回成功状态的写入器；需要区分实际通道的实现应覆盖此方法。 */
    suspend fun writeWithResult(item: CarSettingItem): CarSettingWriteResult =
        CarSettingWriteResult(write(item))
}

/**
 * 车辆设置写入控制器。
 *
 * 草稿/提交目标独立于车辆回读。发送失败只撤销目标，不恢复过期的车辆快照。
 */
class CarSettingsController(
    initialItems: List<CarSettingItem> = emptyList(),
    private val writer: CarSettingWriter?,
    private val gateway: CarSettingsGateway? = null,
    private val vinProvider: () -> String = { "" },
    private val debounceMs: Long = 1_000L,
    private val statusPollAttempts: Int = 30,
    private val statusPollIntervalMs: Long = 1_000L,
) {
    private val _state = MutableStateFlow(CarSettingsState(initialItems))
    val state: StateFlow<CarSettingsState> = _state.asStateFlow()
    private var latestVehicleStatus: VehicleStatusSnapshot? = null
    private var latestCachedStatus: VehicleStatusSnapshot? = null
    private var vehicleStatusGeneration = 0L
    private val pendingVehicleConfirmations = mutableMapOf<String, PendingVehicleConfirmation>()
    private var loadGeneration = 0L
    private var nextOperationId = 0L
    private var lastSourceSequence = -1L
    private val activeOperations = mutableMapOf<String, WriteOperation>()

    private data class WriteOperation(
        val id: Long,
        val vin: String,
        val loadGeneration: Long,
    )

    private data class VehicleStatusSnapshot(
        val vin: String,
        val status: TtpInfo,
        val generation: Long,
        val live: Boolean = true,
    )

    private data class PendingVehicleConfirmation(
        val kind: CarSettingKind,
        val observedStatusGeneration: Long,
        val operationId: Long,
    )

    val available: Boolean
        get() = writer != null

    val cloudAvailable: Boolean
        get() = gateway != null

    /**
     * 从云端重新拉取当前 VIN 的可用设置项。
     *
     * 云端清单是唯一的项目/分组/权限来源；本地注册表只用于离线预览和单测。
     */
    suspend fun refresh(): Boolean {
        val vin = vinProvider().trim()
        val activeGateway = gateway
        if (activeGateway == null) {
            clearForVin(vin, "云端设置服务未接入")
            return false
        }
        if (vin.isBlank()) {
            clearForVin(vin, "登录并绑定车辆后加载云端设置")
            return false
        }
        if (_state.value.vin == vin && activeOperations.keys.any {
                findItem(it)?.configId.let { configId ->
                    configId in setOf("4", "58", "61", "55", "92") ||
                        configId in SecondPhaseSettings.ids || configId in BatterySettings.ids || configId in TimerSettings.ids ||
                        configId in SimpleChoiceSettings.ids || configId in SimpleToggleSettings.ids ||
                        configId in VolumeBrightnessSettings.ids || configId in EnergyRecoverySettings.ids ||
                        configId in CloudSwitchSettings.ids || configId in setOf("10", "73", "42")
                }
            }) {
            _state.value = _state.value.copy(message = "设置正在提交，请完成后刷新")
            return false
        }
        if (_state.value.vin.isNotEmpty() && _state.value.vin != vin) {
            latestVehicleStatus = null
            latestCachedStatus = null
        }
        if (_state.value.loading && _state.value.vin == vin) return false
        val generation = ++loadGeneration
        val previous = _state.value.takeIf { it.vin == vin && it.loadedFromCloud }
        val invalidatedIds = activeOperations.keys.toSet()
        activeOperations.clear()
        invalidatedIds.forEach(pendingVehicleConfirmations::remove)
        if (previous == null) {
            pendingVehicleConfirmations.clear()
        }
        _state.value = (previous ?: CarSettingsState(vin = vin)).copy(
            loadGeneration = generation, loading = true, pendingIds = emptySet(), message = null,
            valueStates = previous?.valueStates.orEmpty().mapValues { (id, value) ->
                when {
                    id !in invalidatedIds -> value
                    value.busy -> value.copy(
                        writeState = SettingWriteState.CANCELLED,
                        confirmationState = SettingConfirmationState.UNKNOWN,
                    )
                    else -> value.copy(confirmationState = SettingConfirmationState.UNKNOWN, error = null)
                }
            },
        )
        val cloud = try {
            activeGateway.load(vin)
        } catch (cancelled: CancellationException) {
            if (generation == loadGeneration && vinProvider().trim() == vin) {
                _state.value = _state.value.copy(loading = false)
            }
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (generation != loadGeneration || vinProvider().trim() != vin) return false
        if (cloud == null) {
            _state.value = _state.value.copy(loading = false, message = "云端设置加载失败，请稍后重试")
            return false
        }
        val items = CloudCarSettingMapper.toItems(cloud, vin)
        val missingCloudItems = CloudCarSettingMapper.toMissingCloudItems(cloud)
        _state.value = CarSettingsState(
            items = items,
            missingCloudItems = missingCloudItems,
            rawConfigJson = cloud.rawJson,
            loading = false,
            loadedFromCloud = true,
            vin = vin,
            loadGeneration = generation,
            message = if (items.isEmpty()) "云端未返回可用设置项" else null,
            valueStates = items.associate { item ->
                val retained = _state.value.valueState(item.id).takeIf { value ->
                    // Preserve explicit edits/receipts if model metadata changes; submission
                    // revalidates against the newly loaded item instead of discarding all sources.
                    item.configId in setOf("4", "58", "61", "55", "92") ||
                        ((value.draftValue == null || validDraftTarget(item.kind, value.draftValue)) &&
                            (value.submittedValue == null || validTarget(item.kind, value.submittedValue)))
                } ?: CarSettingValueState()
                val observed = (retained.vehicleValue as? VehicleSettingValue.Known)
                    ?.takeIf { validTarget(item.kind, it.kind) }
                item.id to retained.copy(
                    vehicleValue = observed ?: VehicleSettingValue.Unknown,
                    cachedValue = retained.cachedValue?.takeIf { validTarget(item.kind, it.kind) },
                    configuredValue = CloudCarSettingMapper.configuredValue(cloud, item),
                )
            },
        )
        pendingVehicleConfirmations.keys.retainAll(
            _state.value.valueStates.filterValues { it.submittedValue != null }.keys,
        )
        latestCachedStatus?.takeIf { it.vin == vin }?.let(::applyVehicleStatus)
        latestVehicleStatus?.let { snapshot ->
            if (snapshot.vin == vin) applyVehicleStatus(snapshot)
        }
        return true
    }

    private fun clearForVin(vin: String, message: String) {
        val generation = ++loadGeneration
        pendingVehicleConfirmations.clear()
        activeOperations.clear()
        latestVehicleStatus = null
        latestCachedStatus = null
        _state.value = CarSettingsState(
            vin = vin,
            loadGeneration = generation,
            message = message,
        )
    }

    fun onVinChanged(vin: String) {
        val normalizedVin = vin.trim()
        if (_state.value.vin != normalizedVin) {
            clearForVin(normalizedVin, "")
            clearMessage()
        }
    }

    private fun isCurrentOperation(id: String, operation: WriteOperation): Boolean =
        activeOperations[id] == operation &&
            operation.loadGeneration == loadGeneration &&
            vinProvider().trim() == operation.vin &&
            (_state.value.vin.isBlank() || _state.value.vin == operation.vin)

    private fun findItem(id: String): CarSettingItem? =
        (_state.value.items + _state.value.missingCloudItems).firstOrNull { it.id == id }

    private fun changeValueState(id: String, transform: (CarSettingValueState) -> CarSettingValueState) {
        val current = _state.value
        _state.value = current.copy(valueStates = current.valueStates + (id to transform(current.valueState(id))))
    }

    fun editDraft(id: String, transform: (CarSettingKind) -> CarSettingKind): Boolean {
        if (_state.value.loading) return false
        val item = findItem(id) ?: return false
        val editable = _state.value.valueState(id).editableKind(item.kind)
        if (!CarSettingItemAccess.canWrite(item) || hasActiveSettingWrite(item)) return false
        if (_state.value.vin.isNotBlank() && _state.value.vin != vinProvider().trim()) return false
        if (!hasKnownSettingValue(item)) return false
        val target = transform(editable)
        if (!validDraftTarget(item.kind, target) || !validSpeedTarget(item, target) ||
            !validSecondPhaseTarget(item, target) || !validBatteryTarget(item, target) || !validTimerTarget(item, target) ||
            !validSimpleChoiceTarget(item, target) || !validAtmosphereTarget(item, target) ||
            (item.configId == "10" && !GeofenceSettings.validItem(item.copy(kind = target)))
        ) return false
        changeValueState(id) { it.copy(draftValue = target, error = null) }
        return true
    }

    fun cancelDraft(id: String) {
        if (id !in activeOperations) changeValueState(id) { it.copy(draftValue = null) }
    }

    suspend fun submitDraft(id: String, confirmEnergyRecoveryHigh: Boolean = false): Boolean {
        val draft = _state.value.valueState(id).draftValue ?: return false
        return updateInternal(id, { draft }, confirmEnergyRecoveryHigh)
    }

    private fun validDraftTarget(original: CarSettingKind, target: CarSettingKind): Boolean = when {
        original is CarSettingKind.LowPowerInfo && target is CarSettingKind.LowPowerInfo ->
            target.alarmPower == null || target.alarmPower in 0..100
        original is CarSettingKind.LowPowerPhone && target is CarSettingKind.LowPowerPhone ->
            (target.startTime == null || target.startTime.isValidLowPowerTime()) &&
                (target.endTime == null || target.endTime.isValidLowPowerTime())
        else -> validTarget(original, target)
    }

    private fun hasKnownSettingValue(item: CarSettingItem): Boolean {
        if (item.configId == "73" && item.kind is CarSettingKind.Atmosphere) {
            val state = _state.value.valueState(item.id)
            return listOfNotNull((state.vehicleValue as? VehicleSettingValue.Known)?.kind, state.cachedValue?.kind)
                .filterIsInstance<CarSettingKind.Atmosphere>().any(AtmosphereSettings::valid)
        }
        if (item.configId !in setOf("4", "58", "61", "55", "92")) return true
        val state = _state.value.valueState(item.id)
        return listOfNotNull(state.configuredValue,
            (state.vehicleValue as? VehicleSettingValue.Known)?.kind, state.cachedValue?.kind
        ).any { kind ->
            when (kind) {
                is CarSettingKind.Slider -> kind.valueKnown
                is CarSettingKind.Customize -> kind.valueKnown
                else -> false
            }
        }
    }

    private fun validTarget(original: CarSettingKind, target: CarSettingKind): Boolean = when {
        original is CarSettingKind.Toggle && target is CarSettingKind.Toggle -> true
        original is CarSettingKind.LowPowerInfo && target is CarSettingKind.LowPowerInfo ->
            target.edit != null && target.alarmOn != null && target.alarmPower?.let { it in 0..100 } == true
        original is CarSettingKind.LowPowerPhone && target is CarSettingKind.LowPowerPhone ->
            when (target.edit) {
                CarSettingKind.LowPowerPhoneEdit.SWITCH ->
                    target.enabled != null && validLowPowerTimeRange(target.startTime, target.endTime)
                CarSettingKind.LowPowerPhoneEdit.TIMES -> validLowPowerTimeRange(target.startTime, target.endTime) &&
                    selectableLowPowerTime(target.startTime) && selectableLowPowerTime(target.endTime)
                null -> false
            }
        original is CarSettingKind.Choice && target is CarSettingKind.Choice ->
            target.options == original.options && target.selectedIndex in original.options.indices
        original is CarSettingKind.Slider && target is CarSettingKind.Slider ->
            target.range == original.range && target.value.isFinite() && target.value in original.range &&
                (original.minimumNonZero == null || target.value == 0f || target.value >= original.minimumNonZero)
        original is CarSettingKind.Customize && target is CarSettingKind.Customize ->
            target.valueKnown &&
                target.throttleLevel?.let { it in 0..2 } == true &&
                target.energyLevel?.let { it in 0..2 } == true
        original is CarSettingKind.Atmosphere && target is CarSettingKind.Atmosphere -> AtmosphereSettings.valid(target)
        else -> false
    }

    private fun validSpeedTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        item.configId !in setOf("4", "58", "61") ||
            (target is CarSettingKind.Slider &&
                (item.kind as? CarSettingKind.Slider)?.rangeKnown == true && target.rangeKnown &&
                target.speedPresets == item.kind.speedPresets &&
                VehicleSettingCommands.forConfigId(item.configId, target) != null)

    private fun validBatteryTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        item.configId !in BatterySettings.ids || BatterySettings.validItem(item.copy(kind = target))

    private fun validTimerTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        item.configId !in TimerSettings.ids || TimerSettings.validItem(item.copy(kind = target))

    private fun validSimpleChoiceTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        (item.configId != "42" || RemainingMileageSettings.validItem(item.copy(kind = target))) &&
        (item.configId !in SimpleChoiceSettings.ids || SimpleChoiceSettings.validItem(item.copy(kind = target))) &&
            (item.configId !in SimpleToggleSettings.ids || SimpleToggleSettings.validItem(item.copy(kind = target))) &&
            (item.configId !in CloudSwitchSettings.ids || CloudSwitchSettings.validItem(item.copy(kind = target))) &&
            (item.configId !in VolumeBrightnessSettings.ids ||
                (VolumeBrightnessSettings.validItem(item.copy(kind = target)) && VolumeBrightnessSettings.canEdit(item, _state.value))) &&
            (item.configId !in EnergyRecoverySettings.ids ||
                (EnergyRecoverySettings.validItem(item.copy(kind = target)) && EnergyRecoverySettings.canEdit(item, _state.value)))

    private fun validAtmosphereTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        item.configId != "73" || AtmosphereSettings.validItem(item.copy(kind = target))

    private fun hasActiveSettingWrite(item: CarSettingItem): Boolean =
        item.id in activeOperations || (item.configId == "10" &&
            activeOperations.keys.any { findItem(it)?.configId == "10" }) || SecondPhaseSettings.writeKey(item)?.let { key ->
            activeOperations.keys.any { id -> findItem(id)?.let { SecondPhaseSettings.writeKey(it) == key } == true }
        } == true || TimerSettings.writeKey(item)?.let { key ->
            activeOperations.keys.any { id -> findItem(id)?.let { TimerSettings.writeKey(it) == key } == true }
        } == true || (item.configId in setOf("55", "92") &&
            (activeOperations.keys.any { findItem(it)?.configId in setOf("55", "92") } ||
                pendingVehicleConfirmations.keys.any {
                    it != item.id && findItem(it)?.configId in setOf("55", "92")
                }))

    private fun validSecondPhaseTarget(item: CarSettingItem, target: CarSettingKind): Boolean =
        item.configId !in SecondPhaseSettings.ids ||
            (SecondPhaseSettings.validTarget(item.configId, target) &&
                SecondPhaseSettings.canEditChoice(item, _state.value))

    private fun fenceSiblingNeedsRefresh(item: CarSettingItem): Boolean = item.configId == "10" &&
        _state.value.items.any { sibling -> sibling.configId == "10" && sibling.id != item.id &&
            _state.value.valueState(sibling.id).let { value ->
                value.submittedValue?.let { submitted ->
                    value.writeState in setOf(SettingWriteState.CLOUD_ACCEPTED, SettingWriteState.LOCAL_UPDATED) &&
                        !GeofenceSettings.matches(item.geofenceConfiguration, submitted)
                } == true
            }
        }

    private fun finishFailure(id: String, message: String, confirmation: SettingConfirmationState) {
        pendingVehicleConfirmations.remove(id)
        changeValueState(id) {
            it.copy(draftValue = null, submittedValue = null, writeState = SettingWriteState.FAILED,
                confirmationState = confirmation, error = message)
        }
        _state.value = _state.value.copy(pendingIds = _state.value.pendingIds - id, message = message)
    }

    private fun finishConfirmationFailure(
        id: String,
        message: String,
        confirmation: SettingConfirmationState,
    ) {
        pendingVehicleConfirmations.remove(id)
        changeValueState(id) {
            // Keep the write receipt separate: confirmation timeout does not undo CLOUD_ACCEPTED.
            it.copy(draftValue = null, submittedValue = null, confirmationState = confirmation, error = message)
        }
        _state.value = _state.value.copy(pendingIds = _state.value.pendingIds - id, message = message)
    }

    suspend fun update(id: String, transform: (CarSettingKind) -> CarSettingKind): Boolean =
        updateInternal(id, transform, confirmEnergyRecoveryHigh = false)

    private suspend fun updateInternal(id: String, transform: (CarSettingKind) -> CarSettingKind,
                                       confirmEnergyRecoveryHigh: Boolean): Boolean {
        val current = _state.value
        if (current.loading) return false
        val regularItem = current.items.firstOrNull { it.id == id }
        val missingItem = current.missingCloudItems.firstOrNull { it.id == id }
        val item = regularItem ?: missingItem ?: return false
        val isMissingCloudItem = regularItem == null
        val editable = current.valueState(id).editableKind(item.kind)
        if (!CarSettingItemAccess.canWrite(item)) {
            _state.value = current.copy(message = "该设置项当前不可写")
            return false
        }
        val activeWriter = writer
        if (!available || activeWriter == null) {
            _state.value = current.copy(message = "车辆设置通道未接入")
            return false
        }
        if (id in current.pendingIds || hasActiveSettingWrite(item)) return false
        if (fenceSiblingNeedsRefresh(item)) {
            _state.value = current.copy(message = "围栏上次更改尚未刷新，请刷新配置后再保存")
            return false
        }

        val vin = vinProvider().trim()
        if (current.vin.isNotBlank() && current.vin != vin) return false
        if (!hasKnownSettingValue(item)) return false
        val target = transform(editable)
        if (!validTarget(item.kind, target) || !validSpeedTarget(item, target) ||
            !validSecondPhaseTarget(item, target) || !validBatteryTarget(item, target) || !validTimerTarget(item, target) ||
            !validSimpleChoiceTarget(item, target) || !validAtmosphereTarget(item, target) ||
            (item.configId == "10" && (!GeofenceSettings.validItem(item.copy(kind = target)) ||
                item.geofenceConfiguration?.let { it.complete && it.vin == vin } != true))
        ) {
            _state.value = current.copy(message = if (item.configId == "10")
                "围栏配置不完整或设置值无效，请刷新后再保存" else "设置值无效")
            return false
        }
        if (item.configId == "18" && !confirmEnergyRecoveryHigh && EnergyRecoverySettings.needsHighConfirmation(target, current)) {
            _state.value = current.copy(message = "请确认高档位能量回收提示后再保存")
            return false
        }
        val operation = WriteOperation(++nextOperationId, vin, loadGeneration)
        activeOperations[id] = operation
        pendingVehicleConfirmations.remove(id)
        var submitted = item.copy(kind = target)
        changeValueState(id) {
            it.copy(draftValue = null, submittedValue = target, operationId = operation.id,
                writeState = SettingWriteState.PREPARING, writeChannel = null,
                confirmationState = SettingConfirmationState.UNKNOWN, error = null)
        }
        _state.value = _state.value.copy(pendingIds = current.pendingIds + id, message = null)

        try {
            if (target is CarSettingKind.Toggle && debounceMs > 0) {
                delay(debounceMs)
            }
            if (!isCurrentOperation(id, operation)) return false
            if (item.configId == "42" && (target as CarSettingKind.Toggle).checked) {
                val eligibility = try {
                    gateway?.loadRemainingMileageEligibility(vin)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (!isCurrentOperation(id, operation)) return false
                if (eligibility == null || eligibility.vin != vin || !eligibility.allowed) {
                    finishFailure(id, eligibility?.takeIf { it.vin == vin }?.prompt?.takeIf { it.isNotBlank() }
                        ?: "剩余里程开启资格未知，请刷新后重试", SettingConfirmationState.FAILED)
                    return false
                }
                submitted = submitted.copy(remainingMileageEligibility = eligibility)
            }
            val observedGeneration = vehicleStatusGeneration
            changeValueState(id) { it.copy(writeState = SettingWriteState.SUBMITTING) }
            val writeResult = try {
                activeWriter.writeWithResult(submitted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CarSettingWriteResult(false)
            }
            if (!isCurrentOperation(id, operation)) return false
            val success = writeResult.success
            if (!success) {
                finishFailure(id, "设置保存失败，已撤销本次选择", SettingConfirmationState.FAILED)
                return false
            }

            // A writer receipt is never proof of execution, including legacy boolean writers.
            val phase = when (writeResult.channel) {
                CarSettingWriteChannel.CLOUD -> SettingWriteState.CLOUD_ACCEPTED
                else -> SettingWriteState.LOCAL_UPDATED
            }
            pendingVehicleConfirmations[id] = PendingVehicleConfirmation(target, observedGeneration, operation.id)
            changeValueState(id) {
                it.copy(writeState = phase, writeChannel = writeResult.channel,
                    confirmationState = SettingConfirmationState.WAITING_READBACK)
            }
            reconcileObservation(id)
            _state.value = _state.value.copy(message = "设置已提交，尚需车辆确认")

            val cloudUpdate = CloudCarSettingUpdateBuilder.supports(
                configId = submitted.configId,
                kind = submitted.kind,
            )
            val statusGateway = gateway
            val needsCloudConfirmation = writeResult.channel == CarSettingWriteChannel.CLOUD &&
                cloudUpdate &&
                !isMissingCloudItem
            if (statusGateway != null && needsCloudConfirmation) {
                var confirmed: Boolean? = null
                for (attempt in 0 until statusPollAttempts) {
                    delay(statusPollIntervalMs)
                    if (!isCurrentOperation(id, operation)) return false
                    confirmed = try {
                        statusGateway.checkStatus(submitted.configId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        false
                    }
                    if (!isCurrentOperation(id, operation)) return false
                    if (_state.value.valueState(id).confirmationState == SettingConfirmationState.READBACK_MATCHED) return true
                    if (confirmed != null) break
                }
                if (confirmed != true) {
                    finishConfirmationFailure(id, if (confirmed == false) "设置结果确认失败，车辆状态仍以回读为准" else "设置确认超时，车辆状态仍以回读为准",
                        if (confirmed == false) SettingConfirmationState.FAILED else SettingConfirmationState.TIMED_OUT)
                    return false
                }
                pendingVehicleConfirmations.remove(id)
                changeValueState(id) {
                    if (it.confirmationState == SettingConfirmationState.READBACK_MISMATCH) it
                    else it.copy(confirmationState = SettingConfirmationState.CONFIRMED_BY_RESULT)
                }
                _state.value = _state.value.copy(message = "设置结果已确认；车辆值仍以回读为准")
            }

            return true
        } catch (cancelled: CancellationException) {
            if (isCurrentOperation(id, operation)) {
                val value = _state.value.valueState(id)
                val hasWriteReceipt = value.writeState == SettingWriteState.CLOUD_ACCEPTED ||
                    value.writeState == SettingWriteState.LOCAL_UPDATED
                if (hasWriteReceipt) {
                    // Stop polling, but retain the receipt and pending readback reconciliation.
                    changeValueState(id) {
                        if (it.confirmationState == SettingConfirmationState.READBACK_MATCHED) it
                        else it.copy(confirmationState = SettingConfirmationState.UNKNOWN, error = null)
                    }
                    _state.value = _state.value.copy(message = "确认等待已取消；写入结果保留，车辆状态仍未确认")
                } else {
                    pendingVehicleConfirmations.remove(id)
                    changeValueState(id) { it.copy(draftValue = null, submittedValue = null,
                        writeState = SettingWriteState.CANCELLED, confirmationState = SettingConfirmationState.UNKNOWN, error = null) }
                    _state.value = _state.value.copy(message = "设置操作已取消")
                }
            }
            throw cancelled
        } finally {
            if (activeOperations[id] == operation) {
                activeOperations.remove(id)
                _state.value = _state.value.copy(pendingIds = _state.value.pendingIds - id)
            }
        }
    }

    private fun sameValue(left: CarSettingKind, right: CarSettingKind): Boolean = when {
        left is CarSettingKind.Toggle && right is CarSettingKind.Toggle -> left.checked == right.checked
        left is CarSettingKind.LowPowerInfo && right is CarSettingKind.LowPowerInfo ->
            left.alarmOn == right.alarmOn && left.alarmPower == right.alarmPower
        left is CarSettingKind.LowPowerPhone && right is CarSettingKind.LowPowerPhone ->
            left.enabled == right.enabled && left.startTime == right.startTime && left.endTime == right.endTime
        left is CarSettingKind.Choice && right is CarSettingKind.Choice -> left.selectedIndex == right.selectedIndex
        left is CarSettingKind.Slider && right is CarSettingKind.Slider -> left.value == right.value
        left is CarSettingKind.Customize && right is CarSettingKind.Customize -> left == right
        left is CarSettingKind.Atmosphere && right is CarSettingKind.Atmosphere -> AtmosphereSettings.matches(left, right)
        else -> false
    }

    private fun reconcileObservation(id: String) {
        val pending = pendingVehicleConfirmations[id] ?: return
        val state = _state.value.valueState(id)
        val observed = state.vehicleValue as? VehicleSettingValue.Known ?: return
        if (!observed.live || state.operationId != pending.operationId || observed.sequence <= pending.observedStatusGeneration) return
        val matches = sameValue(observed.kind, pending.kind)
        changeValueState(id) {
            it.copy(confirmationState = if (matches) SettingConfirmationState.READBACK_MATCHED else SettingConfirmationState.READBACK_MISMATCH,
                submittedValue = if (matches) null else it.submittedValue)
        }
        if (matches) pendingVehicleConfirmations.remove(id)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * TCS / ABS 这类子页面设置没有云端值，原 App 从车辆 BLE 状态读取。
     * 车辆状态更新后回填开关，避免设置页一直显示为关闭。
     */
    fun updateVehicleStatus(status: TtpInfo?) {
        updateVehicleStatus(vinProvider().trim(), status)
    }

    fun updateVehicleObservation(observation: VehicleStatusObservation?) {
        if (observation == null) {
            invalidateLiveValues()
            return
        }
        if (observation.vin != vinProvider().trim() || observation.sequence <= lastSourceSequence) return
        lastSourceSequence = observation.sequence
        if (observation.origin != VehicleStatusOrigin.LIVE_TTP) {
            invalidateLiveValues()
            if (observation.origin == VehicleStatusOrigin.CACHED_TTP && observation.info != null &&
                (_state.value.vin.isBlank() || _state.value.vin == observation.vin)
            ) {
                val snapshot = VehicleStatusSnapshot(observation.vin, observation.info, observation.sequence, live = false)
                latestCachedStatus = snapshot
                applyVehicleStatus(snapshot)
            }
            return
        }
        updateVehicleStatus(observation.vin, observation.info)
    }

    private fun invalidateLiveValues() {
        latestVehicleStatus = null
        val current = _state.value
        _state.value = current.copy(valueStates = current.valueStates.mapValues { (_, value) ->
            val known = value.vehicleValue as? VehicleSettingValue.Known
            if (known == null) value else value.copy(vehicleValue = known.copy(live = false))
        })
    }

    fun updateVehicleStatus(vin: String, status: TtpInfo?) {
        val normalizedVin = vin.trim()
        val activeVin = vinProvider().trim()
        if (normalizedVin.isNotBlank() && activeVin.isNotBlank() && normalizedVin != activeVin) return
        if (_state.value.vin.isNotEmpty() && _state.value.vin != normalizedVin) return
        if (status == null || normalizedVin.isBlank()) {
            invalidateLiveValues()
            return
        }
        val generation = ++vehicleStatusGeneration
        latestVehicleStatus = VehicleStatusSnapshot(normalizedVin, status, generation)
        applyVehicleStatus(latestVehicleStatus!!)
    }

    private fun applyVehicleStatus(snapshot: VehicleStatusSnapshot) {
        val status = snapshot.status
        val current = _state.value
        val generation = snapshot.generation
        if (current.items.isEmpty()) return
        val observations = mutableMapOf<String, CarSettingKind>()
        fun applyStatus(source: List<CarSettingItem>): List<CarSettingItem> = source.map { item ->
            val updated = when (item.configId) {
                "5" -> item.withIndex(status.extended.alarmRingThemeCurrent.takeIf { it in 0..1 })
                "7", "39" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(SecondPhaseSettings.autoLockEnabled(
                        status.extended.leaveVehicleAutoLockStatus, status.extended.sideBraceStatus,
                    ))
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.leaveVehicleAutoLockTimeStatus.takeIf { it in 0..3 },
                    )
                    else -> item
                }
                "11" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(SecondPhaseSettings.flag(status.extended.bleAutoTurnOnOffStatus))
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.bleAutoTurnOnOffGearStatus.takeIf { it in 0..2 },
                    )
                    else -> item
                }
                // 原 App：tcsWorkMode == 0 为开启，1..7 为关闭，-1 表示未上报。
                "37" -> item.withToggle(SimpleToggleSettings.tcs(status.extended.tcsWorkMode))
                "38" -> item.withToggle(SimpleToggleSettings.flag(status.extended.absFunctionStatus))
                "33" -> item.withToggle(status.extended.simulatedSoundWaveStatus.flagOrNull())
                "86" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        SimpleToggleSettings.flag(status.extended.brightnessAdjustSwitch),
                    )
                    is CarSettingKind.Slider -> item.withSlider(
                        status.extended.brightnessAdjustValue.floatIn(0..100),
                    )
                    else -> item
                }
                "19" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.automaticHeadlightFunctionStatus),
                )
                "26" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.turnSignalReturnsAutomaticallyStatus),
                )
                "36" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.brakePowerDownFunctionStatus),
                )
                "46" -> item.withToggle(SimpleToggleSettings.flag(status.extended.steepSlopeState))
                "49" -> item.withToggle(SimpleToggleSettings.flag(status.extended.rampSlopeState))
                "68" -> item.withToggle(SimpleToggleSettings.flag(status.extended.quickBrakeLightStatus))
                "94" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.gearMemorySettingFeedback),
                )
                "84" -> item.withToggle(SimpleToggleSettings.flag(status.extended.blueLightSwitch))
                "47" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        BatterySettings.flag(status.extended.batteryChargingPowerStatus),
                    )
                    is CarSettingKind.Slider -> {
                        val watt = status.extended.batteryChargingPowerValue
                            .takeIf { it in 200..1_000 }
                            ?.toFloat()
                        item.withSlider(watt)
                    }
                    else -> item
                }
                "48" -> item.withSlider(BatterySettings.chargingLimit(status.extended.batteryChargingLimitValue))
                "62" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        BatterySettings.flag(status.extended.seatHeatSwitchStatus),
                    )
                    is CarSettingKind.Choice -> {
                        val level = status.extended.seatHeatLevelStatus
                        item.withIndex(
                            level.takeIf { it in 0..2 },
                        )
                    }
                    else -> item
                }
                "63" -> item.withToggle(SimpleToggleSettings.flag(status.extended.tPowerStatus))
                "64", "89" -> when {
                    item.id.endsWith(":adaptive") -> item.withToggle(
                        status.extended.customDriveSwitchStatus.adaptivePushOrNull(item.configId),
                    )
                    item.kind is CarSettingKind.Slider -> item.withSlider(
                        status.extended.customDriveLevelStatus.floatIn(0..2),
                    )
                    else -> item.withToggle(status.extended.helpCarState.flagOrNull())
                }
                "55", "92" -> when (val kind = item.kind) {
                    is CarSettingKind.Customize -> {
                        val throttle = status.extended.customizeThrottleSensitivity
                        val energy = status.extended.customizeEnergyLevel
                        val energyState = status.extended.customizeEnergyState
                        val tcs = status.extended.customizeTcs
                        if (throttle in 0..2 && energy in 0..2 && energyState in 0..1 && tcs in 0..1) {
                            item.copy(kind = kind.copy(
                                throttleLevel = throttle,
                                energyLevel = energy,
                                energyState = energyState == 1,
                                tcs = tcs == 1,
                            ))
                        } else item
                    }
                    else -> item
                }
                "4" -> {
                    val slider = item.kind as? CarSettingKind.Slider
                    val speed = status.maximumSpeedCurrent.toFloat()
                    if (slider != null && speed in slider.range && speed >= 15f) item.withSlider(speed)
                    else item
                }
                "58" -> {
                    val slider = item.kind as? CarSettingKind.Slider
                    val speed = status.maximumSpeedCurrent.toFloat()
                    if (slider != null && speed in slider.range && (speed == 0f || speed >= 15f)) {
                        item.withSlider(speed)
                    } else item
                }
                "61" -> {
                    val slider = item.kind as? CarSettingKind.Slider
                    val speed = status.maximumSpeedCurrent.toFloat()
                    if (slider != null && speed in slider.range &&
                        (speed == 0f || slider.minimumNonZero == null || speed >= slider.minimumNonZero)
                    ) item.withSlider(speed) else item
                }
                "1005" -> {
                    val raw = if (item.id.endsWith(":short")) {
                        status.extended.customFunctionShort
                    } else {
                        status.extended.customFunctionLong
                    }
                    item.withIndex(customKeyIndex(raw))
                }
                "69" -> item.withToggle(
                    when (item.id) {
                        "cloud:69:fcw" -> status.extended.frontCollisionWarning
                        "cloud:69:rcw" -> status.extended.rcwRadarSwitch
                        "cloud:69:bsd" -> status.extended.bsdRadar
                        else -> -1
                    }.flagOrNull(),
                )
                "2" -> item.withToggle(status.extended.sideBraceStatus.flagOrNull())
                "32" -> item.withToggle(SimpleToggleSettings.flag(status.extended.tiltWarningStatus))
                "45" -> item.withToggle(SimpleToggleSettings.flag(status.extended.constSpeedState))
                "27" -> item.withIndex(
                    status.extended.offModeStatus.takeIf { it in 0..1 },
                )
                "31" -> item.withIndex(
                    status.extended.metricSystemStatus.takeIf { it in 0..1 },
                )
                "50" -> item.withIndex(
                    status.extended.panelThemeCurrent.takeIf { it in 0..1 },
                )
                "54" -> item.withIndex(
                    (status.extended.voltageSettingStatus - 1)
                        .takeIf { status.extended.voltageSettingStatus in 1..3 },
                )
                "15", "59" -> when (val kind = item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        TimerSettings.flag(status.extended.vehiclePowerDownStatus),
                    )
                    is CarSettingKind.Slider -> item.withSlider(
                        status.extended.vehiclePowerDownTimeStatus.floatIn(2..20),
                    )
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.vehiclePowerDownTimeStatus
                            .takeIf { it in 0..kind.options.lastIndex },
                    )
                    else -> item
                }
                "42" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.remainingMileageDebugStatus),
                )
                "43" -> item.withToggle(SimpleToggleSettings.flag(status.extended.reversingState))
                "44" -> item.withToggle(SimpleToggleSettings.flag(status.extended.helpCarState))
                "60" -> when {
                    item.id.endsWith(":enable") -> item.withToggle(
                        BatterySettings.flag(status.extended.capacitySwitchStatus),
                    )
                    item.kind is CarSettingKind.Choice -> {
                        val index = BatteryCapacityOptions.valuesAh
                            .indexOf(status.extended.capacitySettingStatus)
                        item.withIndex(index.takeIf { it >= 0 })
                    }
                    else -> item
                }
                "21", "81" -> when (val kind = item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        TimerSettings.flag(status.extended.withGoHomeStatus),
                    )
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.withGoHomeTimeStatus
                            .takeIf { it in 0..kind.options.lastIndex },
                    )
                    else -> item
                }
                "1" -> when {
                    item.id.endsWith(":enable") -> item.withToggle(
                        status.extended.bleKeyStatus.flagOrNull(),
                    )
                    item.kind is CarSettingKind.Slider -> item.withSlider(
                        status.extended.bleKeyDistanceLevel.floatIn(0..7),
                    )
                    else -> item
                }
                "001" -> item.withIndex(
                    status.extended.metricSystemStatus.takeIf { it in 0..1 },
                )
                "002" -> when {
                    item.id.endsWith(":enable") -> item.withToggle(
                        status.extended.bleAutoTurnOnOffStatus.flagOrNull(),
                    )
                    item.kind is CarSettingKind.Slider -> item.withSlider(
                        status.extended.bleAutoTurnOnOffGearStatus.floatIn(0..2),
                    )
                    else -> item
                }
                "003" -> when {
                    item.id.endsWith(":enable") -> item.withToggle(
                        status.extended.brakeModePowerRecoverFunctionStatus.flagOrNull(),
                    )
                    item.kind is CarSettingKind.Slider -> item.withSlider(
                        status.extended.brakeModePowerRecoverGearStatus.floatIn(1..3),
                    )
                    else -> item
                }
                "25" -> item.withToggle(status.extended.passwordUnlockStatus.flagOrNull())
                "73" -> when (item.kind) {
                    is CarSettingKind.Atmosphere -> AtmosphereSettings.observe(status.extended)?.let { item.copy(kind = it) } ?: item
                    else -> item.withToggle(status.extended.ambientStatus.takeIf { it in 0..1 }?.let { it == 1 })
                }
                "51" -> item.withToggle(status.extended.syncTimeResultStatus.flagOrNull())
                "53" -> item.withToggle(SimpleToggleSettings.flag(status.extended.templeSettingStatus))
                "72" -> item.withToggle(SimpleToggleSettings.invertedFlag(status.extended.longEnduranceStatus))
                "77" -> item.withToggle(status.extended.sleepModeSwitch.flagOrNull())
                "79" -> item.withToggle(SimpleToggleSettings.flag(status.extended.seatPowerStatus))
                "80" -> item.withToggle(SimpleToggleSettings.flag(status.extended.otdSwitch))
                "90" -> item.withToggle(SimpleToggleSettings.flag(status.extended.trackModeStatus))
                "17" -> item.withToggle(
                    SimpleToggleSettings.flag(status.extended.slideModePowerRecoverStatus),
                )
                "18" -> item.withSlider(
                    status.extended.slideModePowerRecoverCurrent.floatIn(1..3),
                )
                "20" -> item.withSlider(
                    status.extended.alarmWarningVolumeLevel.floatIn(0..2),
                )
                "14" -> item.withSlider(
                    AlarmSensitivityMapping.progressFromRaw(status.extended.alarmSensitivityStatus),
                )
                else -> item
            }
            // Helpers return the original instance when a field is missing/invalid.
            // Composite values require every field; defaults must not complete an observation.
            val complete = item.configId != "51" && (updated.kind !is CarSettingKind.Customize ||
                (status.extended.customizeThrottleSensitivity in 0..2 &&
                    status.extended.customizeEnergyLevel in 0..2 &&
                    status.extended.customizeEnergyState in 0..1 && status.extended.customizeTcs in 0..1))
            if (updated !== item && complete) observations[item.id] = updated.kind
            updated
        }
        val nextItems = applyStatus(current.items)
        val nextMissingCloudItems = applyStatus(current.missingCloudItems)
        if (!snapshot.live) {
            // Decode with the existing mappings, but never create a vehicle observation or receipt.
            _state.value = current.copy(valueStates = current.valueStates + observations.mapValues { (id, kind) ->
                current.valueState(id).copy(cachedValue = VehicleSettingValue.Known(
                    kind, snapshot.vin, generation, source = SettingValueSource.CACHED_TTP, live = false,
                ))
            })
            return
        }
        _state.value = current.copy(
            items = nextItems,
            missingCloudItems = nextMissingCloudItems,
            valueStates = current.valueStates.mapValues { (id, value) ->
                if (id !in observations && value.vehicleValue is VehicleSettingValue.Known) {
                    val configId = findItem(id)?.configId
                    val audited = configId in setOf("4", "58", "61", "55", "92") ||
                        configId in SecondPhaseSettings.ids || configId in BatterySettings.ids || configId in TimerSettings.ids ||
                        configId in SimpleChoiceSettings.ids || configId in SimpleToggleSettings.ids ||
                        configId in VolumeBrightnessSettings.ids || configId in EnergyRecoverySettings.ids || configId in setOf("73", "42")
                    value.copy(readbackState = SettingReadbackState.MISSING_OR_INVALID,
                        vehicleValue = if (audited) value.vehicleValue.copy(live = false) else value.vehicleValue)
                } else value
            } + observations.mapValues { (id, kind) ->
                current.valueState(id).copy(vehicleValue = VehicleSettingValue.Known(kind, snapshot.vin, generation),
                    readbackState = SettingReadbackState.VALID)
            },
        )
        observations.keys.forEach(::reconcileObservation)
    }

    private fun Int.adaptivePushOrNull(configId: String): Boolean? {
        return when (configId) {
            "64" -> AdaptivePush64Mapping.checkedFromRaw(this)
            "89" -> AdaptivePush89Mapping.checkedFromRaw(this)
            else -> null
        }
    }

    private fun customKeyIndex(raw: Int): Int? {
        if (raw < 0) return null
        return if (raw in 0..8) raw else 9
    }
    private fun Int.flagOrNull(): Boolean? = when {
        this < 0 -> null
        this == 1 -> true
        else -> false
    }

    private fun Int.zeroIsOnOrNull(): Boolean? =
        if (this < 0) null else this == 0

    private fun Int.floatIn(range: IntRange): Float? =
        takeIf { it in range }?.toFloat()

    private fun CarSettingItem.withToggle(value: Boolean?): CarSettingItem {
        val checked = value ?: return this
        val currentKind = kind as? CarSettingKind.Toggle ?: return this
        return copy(kind = currentKind.copy(checked = checked, valueKnown = true))
    }

    private fun CarSettingItem.withIndex(value: Int?): CarSettingItem {
        val selected = value ?: return this
        val currentKind = kind as? CarSettingKind.Choice ?: return this
        return copy(
            kind = currentKind.copy(
                selectedIndex = selected.coerceIn(
                    0,
                    currentKind.options.lastIndex.coerceAtLeast(0),
                ),
                valueKnown = true,
            ),
        )
    }

    private fun CarSettingItem.withSlider(value: Float?): CarSettingItem {
        val selected = value ?: return this
        val currentKind = kind as? CarSettingKind.Slider ?: return this
        return copy(kind = currentKind.copy(value = selected, valueKnown = true))
    }

    private fun List<CarSettingItem>.replace(item: CarSettingItem): List<CarSettingItem> =
        map { if (it.id == item.id) item else it }
}

/**
 * 默认注册表。
 *
 * 这里覆盖六个主分类与实验室入口。命令 ID 由平台侧 [CarSettingWriter]
 * 根据 [CarSettingItem.id] 映射，UI 不直接接触 BLE / IoT 协议。
 */
object CarSettingsRegistry {
    fun defaultItems(): List<CarSettingItem> = listOf(
        CarSettingItem(
            id = "security.auto_lock",
            title = "离车自动落锁",
            summary = "蓝牙钥匙离开后自动锁车",
            classification = CarSettingClassification.SECURITY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "security.ble_distance",
            title = "蓝牙感应距离",
            summary = "距离越远越容易触发自动解锁",
            classification = CarSettingClassification.SECURITY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Slider(2f, 1f..5f, steps = 3),
        ),
        CarSettingItem(
            id = "security.loss_alarm",
            title = "车辆防丢提醒",
            summary = "检测到异常移动时推送提醒",
            classification = CarSettingClassification.SECURITY,
            channel = CarSettingChannel.IOT,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "light.headlight_delay",
            title = "大灯延时关闭",
            summary = "停车后延时熄灭大灯",
            classification = CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Choice(1, listOf("关闭", "10 秒", "30 秒", "60 秒")),
        ),
        CarSettingItem(
            id = "light.find_sound",
            title = "寻车提示音",
            summary = "使用 App 寻车时播放提示音",
            classification = CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "light.ambient",
            title = "氛围灯",
            summary = "夜间自动点亮氛围灯",
            classification = CarSettingClassification.LIGHT,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "ride.cruise",
            title = "定速巡航",
            summary = "长按巡航键保持当前速度",
            classification = CarSettingClassification.RIDE_CONTROL,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(false),
        ),
        CarSettingItem(
            id = "ride.recover",
            title = "能量回收强度",
            summary = "松油门时的制动回收力度",
            classification = CarSettingClassification.RIDE_CONTROL,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Choice(1, listOf("弱", "中", "强")),
        ),
        CarSettingItem(
            id = "ride.hill_hold",
            title = "坡道辅助",
            summary = "坡道起步时短暂保持制动力",
            classification = CarSettingClassification.RIDE_CONTROL,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "battery.charge_limit",
            title = "充电上限",
            summary = "建议日常使用 80%–90% 延长电池寿命",
            classification = CarSettingClassification.BATTERY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Slider(90f, 75f..100f, steps = 4, unit = "%"),
        ),
        CarSettingItem(
            id = "battery.health",
            title = "电池健康度",
            summary = "由车辆 BMS 周期上报",
            classification = CarSettingClassification.BATTERY,
            channel = CarSettingChannel.LOCAL,
            kind = CarSettingKind.Info("等待车辆上报"),
            writable = false,
        ),
        CarSettingItem(
            id = "meter.brightness",
            title = "仪表亮度",
            summary = "调节仪表屏幕亮度",
            classification = CarSettingClassification.METER,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Slider(80f, 0f..100f, unit = "%"),
        ),
        CarSettingItem(
            id = "meter.auto_brightness",
            title = "自动亮度",
            summary = "根据环境光自动调节仪表亮度",
            classification = CarSettingClassification.METER,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(true),
        ),
        CarSettingItem(
            id = "lab.sound_wave",
            title = "模拟声浪",
            summary = "播放车辆模拟声浪",
            classification = CarSettingClassification.LABORATORY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(false),
        ),
        CarSettingItem(
            id = "lab.custom_drive",
            title = "自定义驾驶模式",
            summary = "使用自定义动力与能量回收组合",
            classification = CarSettingClassification.LABORATORY,
            channel = CarSettingChannel.BLE,
            kind = CarSettingKind.Toggle(false),
        ),
    )
}
