package top.ghhccghk.yadea.carsetting

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

enum class CarSettingChannel(val label: String) {
    BLE("蓝牙"),
    IOT("车联网"),
    BOTH("蓝牙 + 车联网"),
    LOCAL("本机"),
}

sealed interface CarSettingKind {
    data class Toggle(val checked: Boolean) : CarSettingKind
    data class Choice(val selectedIndex: Int, val options: List<String>) : CarSettingKind
    data class Slider(
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val unit: String = "",
    ) : CarSettingKind
    data class Info(val value: String) : CarSettingKind
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
    val pendingIds: Set<String> = emptySet(),
    val message: String? = null,
    val loading: Boolean = false,
    val loadedFromCloud: Boolean = false,
)

/** 平台侧把设置值翻译成 BLE `CommandTempBean` 或 IoT 请求。 */
interface CarSettingWriter {
    suspend fun write(item: CarSettingItem): Boolean
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
        if (_state.value.loading) return false
        val activeGateway = gateway
        if (activeGateway == null) {
            _state.value = _state.value.copy(
                loading = false,
                message = "云端设置服务未接入",
            )
            return false
        }
        val vin = vinProvider().trim()
        if (vin.isBlank()) {
            _state.value = _state.value.copy(
                loading = false,
                message = "登录并绑定车辆后加载云端设置",
            )
            return false
        }
        _state.value = _state.value.copy(loading = true, message = null)
        val cloud = runCatching { activeGateway.load(vin) }.getOrNull()
        if (cloud == null) {
            _state.value = _state.value.copy(
                loading = false,
                message = "云端设置加载失败，请稍后重试",
            )
            return false
        }
        val items = CloudCarSettingMapper.toItems(cloud)
        _state.value = _state.value.copy(
            items = items,
            pendingIds = emptySet(),
            loading = false,
            loadedFromCloud = true,
            message = if (items.isEmpty()) "云端未返回可用设置项" else null,
        )
        return true
    }

    suspend fun update(id: String, transform: (CarSettingKind) -> CarSettingKind): Boolean {
        val current = _state.value
        val item = current.items.firstOrNull { it.id == id } ?: return false
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
        _state.value = current.copy(
            items = current.items.replace(optimistic),
            pendingIds = current.pendingIds + id,
            message = null,
        )

        if (optimistic.kind is CarSettingKind.Toggle && debounceMs > 0) {
            delay(debounceMs)
        }
        val success = runCatching { activeWriter.write(optimistic) }.getOrDefault(false)
        val latest = _state.value
        if (!success) {
            _state.value = latest.copy(
                items = latest.items.replace(item),
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
        if (statusGateway != null && cloudUpdate) {
            var confirmed: Boolean? = null
            for (attempt in 0 until statusPollAttempts) {
                delay(statusPollIntervalMs)
                confirmed = runCatching { statusGateway.checkStatus(optimistic.configId) }
                    .getOrNull()
                if (confirmed != null) break
            }
            if (confirmed != true) {
                _state.value = latest.copy(
                    items = latest.items.replace(item),
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
        val current = _state.value
        if (status == null || current.items.isEmpty()) return
        val updated = current.items.map { item ->
            if (item.id in current.pendingIds) return@map item
            when (item.configId) {
                "37" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.tcsWorkMode != 0),
                )
                "38" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.absFunctionStatus != 0),
                )
                "33" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.simulatedSoundWaveStatus != 0),
                )
                "86" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.brightnessAdjustSwitch != 0),
                )
                "19" -> item.copy(
                    kind = CarSettingKind.Toggle(
                        status.extended.automaticHeadlightFunctionStatus != 0,
                    ),
                )
                "26" -> item.copy(
                    kind = CarSettingKind.Toggle(
                        status.extended.turnSignalReturnsAutomaticallyStatus != 0,
                    ),
                )
                "36" -> item.copy(
                    kind = CarSettingKind.Toggle(
                        status.extended.brakePowerDownFunctionStatus != 0,
                    ),
                )
                "46" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.steepSlopeState != 0),
                )
                "49" -> item.copy(
                    kind = CarSettingKind.Toggle(status.extended.rampSlopeState != 0),
                )
                else -> item
            }
        }
        _state.value = current.copy(items = updated)
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
