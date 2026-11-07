package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import top.ghhccghk.yadea.vehicle.TtpInfo

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

sealed interface CarSettingKind {
    data class Toggle(val checked: Boolean, val valueKnown: Boolean = true) : CarSettingKind
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
    ) : CarSettingKind

    data class Info(val value: String) : CarSettingKind
    /**
     * 自定义骑行参数（confId 55）。原 App 用一个二级页同时下发四个字段，
     * 因此这里作为一个整体条目，避免逐项写入时覆盖其它参数。
     */
    data class Customize(
        val throttleLevel: Int = 1,
        val energyLevel: Int = 1,
        val energyState: Boolean = false,
        val tcs: Boolean = false,
    ) : CarSettingKind
    /** 云端下发、需要进入二级页面的入口；当前只展示云端提示。 */
    data class Entrance(val hint: String = "进入设置") : CarSettingKind
}

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
)

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
)

/** 平台侧把设置值翻译成 BLE `CommandTempBean` 或 IoT 请求。 */
enum class CarSettingWriteChannel {
    BLE,
    CLOUD,
}

data class CarSettingWriteResult(
    val success: Boolean,
    val channel: CarSettingWriteChannel? = null,
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
 * 更新采用乐观切换：先更新 UI，写入失败再回滚。这样开关不会在等待 BLE
 * 往返时卡住；失败信息保留到下一次操作或手动清除。
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
    private var loadGeneration = 0L

    private data class VehicleStatusSnapshot(
        val vin: String,
        val status: TtpInfo,
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
        if (_state.value.vin.isNotEmpty() && _state.value.vin != vin) {
            latestVehicleStatus = null
        }
        if (_state.value.loading && _state.value.vin == vin) return false

        val generation = ++loadGeneration
        _state.value = CarSettingsState(
            vin = vin,
            loadGeneration = generation,
            loading = true,
        )
        val cloud = try {
            activeGateway.load(vin)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (generation != loadGeneration || vinProvider().trim() != vin) return false
        if (cloud == null) {
            clearForVin(vin, "云端设置加载失败，请稍后重试")
            return false
        }
        val items = CloudCarSettingMapper.toItems(cloud)
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
        )
        latestVehicleStatus?.let { snapshot ->
            if (snapshot.vin == vin) applyVehicleStatus(snapshot.status)
        }
        return true
    }

    private fun clearForVin(vin: String, message: String) {
        val generation = ++loadGeneration
        _state.value = CarSettingsState(
            vin = vin,
            loadGeneration = generation,
            message = message,
        )
    }

    suspend fun update(id: String, transform: (CarSettingKind) -> CarSettingKind): Boolean {
        val current = _state.value
        val regularItem = current.items.firstOrNull { it.id == id }
        val missingItem = current.missingCloudItems.firstOrNull { it.id == id }
        val item = regularItem ?: missingItem ?: return false
        val isMissingCloudItem = regularItem == null
        fun CarSettingsState.withItem(target: CarSettingItem): CarSettingsState =
            if (isMissingCloudItem) {
                copy(missingCloudItems = missingCloudItems.replace(target))
            } else {
                copy(items = items.replace(target))
            }

        if (!item.writable) {
            _state.value = current.copy(message = "该设置项当前不可写")
            return false
        }
        val activeWriter = writer
        if (!available || activeWriter == null) {
            _state.value = current.copy(message = "车辆设置通道未接入")
            return false
        }
        if (id in current.pendingIds) return false

        val optimistic = item.copy(kind = transform(item.kind))
        _state.value = current
            .withItem(optimistic)
            .copy(
                pendingIds = current.pendingIds + id,
                message = null,
            )

        if (optimistic.kind is CarSettingKind.Toggle && debounceMs > 0) {
            delay(debounceMs)
        }
        val writeResult = runCatching { activeWriter.writeWithResult(optimistic) }
            .getOrDefault(CarSettingWriteResult(false))
        val success = writeResult.success
        val latest = _state.value
        if (!success) {
            _state.value = latest
                .withItem(item)
                .copy(
                    pendingIds = latest.pendingIds - id,
                    message = "设置保存失败，已恢复原值",
                )
            return false
        }

        val cloudUpdate = CloudCarSettingUpdateBuilder.supports(
            configId = optimistic.configId,
            kind = optimistic.kind,
        )
        val statusGateway = gateway
        val needsCloudConfirmation = writeResult.channel == CarSettingWriteChannel.CLOUD &&
            cloudUpdate &&
            !isMissingCloudItem
        if (statusGateway != null && needsCloudConfirmation) {
            var confirmed: Boolean? = null
            for (attempt in 0 until statusPollAttempts) {
                delay(statusPollIntervalMs)
                confirmed = runCatching { statusGateway.checkStatus(optimistic.configId) }
                    .getOrNull()
                if (confirmed != null) break
            }
            if (confirmed != true) {
                _state.value = latest
                    .withItem(item)
                    .copy(
                        pendingIds = latest.pendingIds - id,
                        message = if (confirmed == false) {
                            "车辆未确认设置，已恢复原值"
                        } else {
                            "设置状态确认超时，已恢复原值"
                        },
                    )
                return false
            }
        }

        _state.value = latest.copy(
            pendingIds = latest.pendingIds - id,
            message = null,
        )
        if (statusGateway != null && needsCloudConfirmation) {
            if (!refresh()) {
                _state.value = _state.value.copy(message = "设置已保存，但云端配置刷新失败")
            }
        }
        return true
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

    fun updateVehicleStatus(vin: String, status: TtpInfo?) {
        val normalizedVin = vin.trim()
        if (status == null || normalizedVin.isBlank()) {
            latestVehicleStatus = null
            return
        }
        latestVehicleStatus = VehicleStatusSnapshot(normalizedVin, status)
        if (_state.value.vin.isNotEmpty() && _state.value.vin != normalizedVin) return
        applyVehicleStatus(status)
    }

    private fun applyVehicleStatus(status: TtpInfo?) {
        val current = _state.value
        if (status == null || current.items.isEmpty()) return
        fun applyStatus(source: List<CarSettingItem>): List<CarSettingItem> = source.map { item ->
            if (item.id in current.pendingIds) return@map item
            when (item.configId) {
                // 原 App：tcsWorkMode == 0 为开启，1..7 为关闭，-1 表示未上报。
                "37" -> item.withToggle(status.extended.tcsWorkMode.zeroIsOnOrNull())
                "38" -> item.withToggle(status.extended.absFunctionStatus.flagOrNull())
                "33" -> item.withToggle(status.extended.simulatedSoundWaveStatus.flagOrNull())
                "86" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        status.extended.brightnessAdjustSwitch.flagOrNull(),
                    )
                    is CarSettingKind.Slider -> item.withSlider(
                        status.extended.brightnessAdjustValue.floatIn(0..100),
                    )
                    else -> item
                }
                "19" -> item.withToggle(
                    status.extended.automaticHeadlightFunctionStatus.flagOrNull(),
                )
                "26" -> item.withToggle(
                    status.extended.turnSignalReturnsAutomaticallyStatus.flagOrNull(),
                )
                "36" -> item.withToggle(
                    status.extended.brakePowerDownFunctionStatus.flagOrNull(),
                )
                "46" -> item.withToggle(status.extended.steepSlopeState.flagOrNull())
                "49" -> item.withToggle(status.extended.rampSlopeState.flagOrNull())
                "68" -> item.withToggle(status.extended.quickBrakeLightStatus.flagOrNull())
                "94" -> item.withToggle(
                    status.extended.gearMemorySettingFeedback.flagOrNull(),
                )
                "84" -> item.withToggle(status.extended.blueLightSwitch.flagOrNull())
                "47" -> when (item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        status.extended.batteryChargingPowerStatus.flagOrNull(),
                    )
                    is CarSettingKind.Slider -> {
                        val watt = status.extended.batteryChargingPowerValue
                            .takeIf { it in 200..1_000 }
                            ?.let { (it / 50 * 50).toFloat() }
                        item.withSlider(watt)
                    }
                    else -> item
                }
                "62" -> when (val kind = item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        status.extended.seatHeatSwitchStatus.flagOrNull(),
                    )
                    is CarSettingKind.Choice -> {
                        val level = status.extended.seatHeatLevelStatus
                        item.withIndex(
                            (level - 1).takeIf { level in 1..kind.options.size },
                        )
                    }
                    else -> item
                }
                "63" -> item.withToggle(status.extended.tPowerStatus.flagOrNull())
                "64", "89" -> when {
                    item.id.endsWith(":adaptive") -> item.withToggle(
                        status.extended.customDriveSwitchStatus.flagOrNull(),
                    )
                    item.kind is CarSettingKind.Slider -> item.withSlider(
                        status.extended.customDriveLevelStatus.floatIn(1..3),
                    )
                    else -> item.withToggle(status.extended.helpCarState.flagOrNull())
                }
                "55", "92" -> when (val kind = item.kind) {
                    is CarSettingKind.Customize -> item.copy(
                        kind = kind.copy(
                            throttleLevel = status.extended.customizeThrottleSensitivity
                                .takeIf { it in 0..2 } ?: kind.throttleLevel,
                            energyLevel = status.extended.customizeEnergyLevel
                                .takeIf { it in 0..2 } ?: kind.energyLevel,
                            energyState = status.extended.customizeEnergyState.flagOrNull()
                                ?: kind.energyState,
                            tcs = status.extended.customizeTcs.flagOrNull() ?: kind.tcs,
                        ),
                    )
                    else -> item
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
                "32" -> item.withToggle(status.extended.tiltWarningStatus.flagOrNull())
                "45" -> item.withToggle(status.cruiseControl.flagOrNull())
                "27" -> item.withIndex(
                    status.extended.offModeStatus.takeIf { it in 0..1 },
                )
                "31" -> item.withIndex(
                    status.extended.metricSystemStatus.takeIf { it in 0..1 },
                )
                "54" -> item.withIndex(
                    (status.extended.voltageSettingStatus - 1)
                        .takeIf { status.extended.voltageSettingStatus in 1..3 },
                )
                "15", "59" -> when (val kind = item.kind) {
                    is CarSettingKind.Toggle -> item.withToggle(
                        status.extended.vehiclePowerDownStatus.flagOrNull(),
                    )
                    is CarSettingKind.Slider -> item.withSlider(
                        status.extended.vehiclePowerDownTimeStatus.floatIn(0..20),
                    )
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.vehiclePowerDownTimeStatus
                            .takeIf { it in 0..kind.options.lastIndex },
                    )
                    else -> item
                }
                "42" -> item.withToggle(
                    status.extended.remainingMileageDebugStatus.flagOrNull(),
                )
                "43" -> item.withToggle(status.extended.reversingState.flagOrNull())
                "44" -> item.withToggle(status.extended.helpCarState.flagOrNull())
                "60" -> when {
                    item.id.endsWith(":enable") -> item.withToggle(
                        status.extended.capacitySwitchStatus.flagOrNull(),
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
                        status.extended.withGoHomeStatus.flagOrNull(),
                    )
                    is CarSettingKind.Choice -> item.withIndex(
                        status.extended.withGoHomeTimeStatus
                            .takeIf { it in 0..kind.options.lastIndex },
                    )
                    else -> item
                }
                "73" -> item.withToggle(status.extended.ambientStatus.flagOrNull())
                "51" -> item.withToggle(status.extended.syncTimeResultStatus.flagOrNull())
                "53" -> item.withToggle(status.extended.templeSettingStatus.flagOrNull())
                "72" -> item.withToggle(status.extended.longEnduranceStatus.flagOrNull())
                "77" -> item.withToggle(status.extended.sleepModeSwitch.flagOrNull())
                "79" -> item.withToggle(status.extended.seatPowerStatus.flagOrNull())
                "80" -> item.withToggle(status.extended.otdSwitch.flagOrNull())
                "90" -> item.withToggle(status.extended.trackModeStatus.flagOrNull())
                "17" -> item.withToggle(
                    status.extended.brakeModePowerRecoverFunctionStatus.flagOrNull(),
                )
                "18" -> item.withSlider(
                    status.extended.brakeModePowerRecoverGearStatus.floatIn(1..3),
                )
                "20" -> item.withSlider(
                    status.extended.alarmWarningVolumeLevel.floatIn(0..7),
                )
                "14" -> item.withSlider(
                    status.extended.alarmWarningMode.floatIn(0..7),
                )
                else -> item
            }
        }
        _state.value = current.copy(
            items = applyStatus(current.items),
            missingCloudItems = applyStatus(current.missingCloudItems),
        )
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
