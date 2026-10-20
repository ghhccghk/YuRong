package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

data class VehicleFaultLevel(
    val level: Int = 0,
    val name: String = "",
    val description: String = "",
    val displayName: String = "",
)

data class VehicleFaultReport(
    val vin: String = "",
    val levels: List<VehicleFaultLevel> = emptyList(),
) {
    val hasFaults: Boolean
        get() = levels.any { it.level == -1 || it.level > 1 }
}

interface VehicleFaultGateway {
    suspend fun load(modelNo: String): VehicleFaultReport?
}

/**
 * `bikeFault/queryBikeFaultStatus`。
 *
 * 原 App 只提交 `modelNo`；返回 `VehicleCheckBean.faultLevelMap`。
 * `faultLevel == 1` 表示检测正常，负数表示设备/网络错误。
 */
class YadeaVehicleFaultGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleFaultGateway {

    override suspend fun load(modelNo: String): VehicleFaultReport? {
        val token = tokenProvider().trim()
        val normalizedModel = modelNo.trim()
        if (token.isBlank() || normalizedModel.isBlank()) return null
        val businessJson = sortedJson(
            mapOf(
                "_gateway" to "True",
                "modelNo" to normalizedModel,
            ),
        )
        val url = baseUrl + PATH
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("FAULT -> POST $url ${NetworkLog.formSummary(body)}")
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return null
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("FAULT plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) return null
        val data = JsonSupport.objectField(plain, "data") ?: return null
        val levels = JsonSupport.arrayObjects(data, "faultLevelMap").map { item ->
            VehicleFaultLevel(
                level = JsonSupport.int(item, "faultLevel")
                    ?: JsonSupport.string(item, "faultLevel")?.toIntOrNull()
                    ?: 0,
                name = JsonSupport.string(item, "faultName").orEmpty(),
                description = JsonSupport.string(item, "faultLevelDesc").orEmpty(),
                displayName = JsonSupport.string(item, "fualtShowName").orEmpty(),
            )
        }
        return VehicleFaultReport(
            vin = JsonSupport.string(data, "vin").orEmpty(),
            levels = levels,
        )
    }

    private fun sortedJson(fields: Map<String, String>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            if (index > 0) append(',')
            append('"').append(key).append("\":\"")
                .append(fields.getValue(key).replace("\\", "\\\\").replace("\"", "\\\""))
                .append('"')
        }
        append('}')
    }

    companion object {
        const val PATH = "bikeFault/queryBikeFaultStatus"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

data class VehicleFaultState(
    val report: VehicleFaultReport? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class VehicleFaultController(
    private val gateway: VehicleFaultGateway?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VehicleFaultState())
    val state: StateFlow<VehicleFaultState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh(modelNo: String) {
        val active = gateway ?: return
        if (modelNo.isBlank() || job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            _state.value = runCatching { active.load(modelNo) }.fold(
                onSuccess = { report ->
                    if (report == null) {
                        VehicleFaultState(
                            report = _state.value.report,
                            loading = false,
                            error = "云端故障检测不可用",
                        )
                    } else {
                        VehicleFaultState(report = report)
                    }
                },
                onFailure = { error ->
                    VehicleFaultState(
                        report = _state.value.report,
                        loading = false,
                        error = error.message ?: "云端故障检测失败",
                    )
                },
            )
        }
    }

    fun dispose() {
        job?.cancel()
        job = null
    }
}

object VehicleDiagnosticsFormatter {
    fun faultCodes(info: TtpInfo?): List<String> {
        val codes = info?.faultCodes?.filter { it != 0 }.orEmpty()
        if (codes.isEmpty() && (info?.faultCodeAmount ?: 0) > 0) {
            return listOf("故障数量 ${info?.faultCodeAmount}")
        }
        return codes.map { code ->
            code.toUInt().toString(16).padStart(4, '0').uppercase()
        }
    }

    fun radarRows(info: TtpInfo?): List<Pair<String, String>> {
        val ext = info?.extended ?: return emptyList()
        return listOf(
            "FCM 雷达使能" to switchLabel(ext.fcmRadarEnable),
            "FCM 雷达开关" to switchLabel(ext.fcmRadarSwitch),
            "FCW 雷达模式" to modeLabel(ext.fcwRadarMode),
            "FCW 工作状态" to workingLabel(ext.fcwRadarWorking),
            "RCW 雷达使能" to switchLabel(ext.rcwRadarEnable),
            "RCW 雷达开关" to switchLabel(ext.rcwRadarSwitch),
            "RCW 雷达模式" to modeLabel(ext.rcwRadarMode),
            "RCW 工作状态" to workingLabel(ext.rcwRadarWorking),
            "BSD 雷达" to switchLabel(ext.bsdRadar),
        )
    }

    fun tireRows(status: VehicleCloudStatus?): List<Pair<String, String>> {
        if (status == null) return emptyList()
        return listOf(
            "前轮胎压" to pressureLabel(
                status.leftFrontPressure,
                status.leftFrontPressureWarning,
            ),
            "后轮胎压" to pressureLabel(
                status.leftRearPressure,
                status.leftRearPressureWarning,
            ),
            "前轮温度" to temperatureLabel(status.leftFrontTireTemp),
            "后轮温度" to temperatureLabel(status.leftRearTireTemp),
        )
    }

    fun lowBatteryRows(
        status: TtpInfo?,
        cloud: VehicleCloudStatus?,
    ): List<Pair<String, String>> {
        val soc = status?.totalBatterySoc?.takeIf { it >= 0f }?.toInt()
            ?: cloud?.soc
        return listOf(
            "当前电量" to (soc?.let { "$it%" } ?: "—"),
            "低电量电话提醒" to cloud?.lowBatTelWarning
                ?.let(::switchLabel)
                .orEmpty()
                .ifBlank { "—" },
            "提醒时段" to if (
                cloud?.lowBatWarningStartTime.isNullOrBlank() &&
                cloud?.lowBatWarningEndTime.isNullOrBlank()
            ) {
                "—"
            } else {
                "${cloud?.lowBatWarningStartTime.orEmpty().ifBlank { "—" }} - " +
                    cloud?.lowBatWarningEndTime.orEmpty().ifBlank { "—" }
            },
        )
    }

    fun locationRows(
        status: TtpInfo?,
        cloud: VehicleCloudStatus?,
    ): List<Pair<String, String>> {
        val coordinates = if (
            status == null ||
            status.longitude == -1.0 ||
            status.latitude == -1.0
        ) {
            "—"
        } else {
            "${status.longitude.formatCoordinate()}, ${status.latitude.formatCoordinate()}"
        }
        return listOf(
            "定位功能" to cloud?.positioningFunc?.let(::switchLabel).orEmpty().ifBlank { "—" },
            "最近定位" to coordinates,
            "报失模式" to when (cloud?.lossState) {
                0 -> "已开启"
                null -> "—"
                else -> "关闭"
            },
        )
    }

    fun cloudStatusRows(status: VehicleCloudStatus?): List<Pair<String, String>> {
        if (status == null) return emptyList()
        return buildList {
            status.onlineState?.let {
                add("在线状态" to if (it) "在线" else "离线")
            }
            status.powerState?.let { add("电源状态" to "状态 $it") }
            status.rideStatus?.let { add("骑行状态" to "状态 $it") }
            status.lockStatus?.let { add("锁状态" to "状态 $it") }
            status.chargingStatus?.let { add("充电状态" to "状态 $it") }
            status.powerDown?.let { add("断电状态" to "状态 $it") }
            status.bmsFault?.let {
                add("BMS 故障" to if (it == 0) "无" else "故障码 $it")
            }
            status.sleepMode?.let { add("休眠模式" to switchLabel(it)) }
            addIfPresent("总电压", status.totalVoltage, " V")
            addIfPresent("总电流", status.totalCurrent, " A")
            addIfPresent("环境温度", status.ambientTemperature, " ℃")
            addIfPresent("电池最高温度", status.maxBatteryTemperature, " ℃")
            addIfPresent("电池最低温度", status.minBatteryTemperature, " ℃")
            addIfPresent("MOS 温度", status.mosTemperature, " ℃")
            addIfPresent("剩余充电时间", status.remainChargeMinutes, " 分钟")
            status.realtimeChargeWatt?.let { add("实时充电功率" to "$it W") }
            addIfPresent("蜂窝信号", status.gsmSignal)
            addIfPresent("GPS 信号", status.gpsSignal)
            val gpsCoordinates = listOf(status.gpsLongitude, status.gpsLatitude)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            if (gpsCoordinates.isNotBlank()) add("GPS 坐标" to gpsCoordinates)
            if (status.mapImei.isNotBlank()) {
                add(
                    "地图定位信息" to buildString {
                        append("IMEI ").append(status.mapImei)
                        if (status.mapMcc.isNotBlank() || status.mapMnc.isNotBlank()) {
                            append(" · MCC/MNC ")
                            append(status.mapMcc.ifBlank { "—" })
                            append('/')
                            append(status.mapMnc.ifBlank { "—" })
                        }
                        if (status.baseStationCount > 0) {
                            append(" · 基站 ").append(status.baseStationCount)
                        }
                    },
                )
            }
            if (status.iotBatteryVoltage.isNotBlank() || status.iotBatteryCapacity != null) {
                add(
                    "IoT 内置电池" to buildString {
                        append(status.iotBatteryVoltage.ifBlank { "—" })
                        if (status.iotBatteryVoltage.isNotBlank()) append(" V")
                        status.iotBatteryCapacity?.let { append(" · $it%") }
                    },
                )
            }
            status.innerSoc?.let { add("内部 SOC" to "$it%") }
            status.tireStatus?.let { add("轮胎状态" to "状态 $it") }
            status.batteryTotalCount?.let { add("电池总数" to it.toString()) }
            status.doorLockStatus?.let { add("门锁状态" to "状态 $it") }
        }
    }

    fun cloudFaultRows(report: VehicleFaultReport?): List<Pair<String, String>> {
        if (report == null) return emptyList()
        if (report.levels.isEmpty()) return listOf("检测结果" to "未返回故障等级")
        return report.levels.map { level ->
            val title = level.displayName.ifBlank {
                level.name.ifBlank { faultLevelLabel(level.level) }
            }
            title to level.description.ifBlank { faultLevelLabel(level.level) }
        }
    }

    private fun pressureLabel(value: String, warning: Int?): String {
        val pressure = value.ifBlank { "—" }
        val warningText = when (warning) {
            0 -> " · 过低"
            1 -> " · 过高"
            2 -> ""
            null -> ""
            else -> " · 告警 $warning"
        }
        return pressure + warningText
    }

    private fun temperatureLabel(value: Int?): String =
        value?.let { "$it ℃" } ?: "—"

    private fun MutableList<Pair<String, String>>.addIfPresent(
        title: String,
        value: String,
        unit: String = "",
    ) {
        if (value.isNotBlank()) add(title to value + unit)
    }

    private fun switchLabel(value: Int): String = when (value) {
        0 -> "关闭"
        1 -> "开启"
        else -> "状态 $value"
    }

    private fun workingLabel(value: Int): String = when (value) {
        0 -> "未工作"
        1 -> "工作中"
        else -> "状态 $value"
    }

    private fun modeLabel(value: Int): String = when (value) {
        0 -> "关闭"
        else -> "模式 $value"
    }

    private fun faultLevelLabel(level: Int): String = when (level) {
        -2 -> "网络异常"
        -1 -> "部件故障"
        1 -> "正常"
        else -> "故障等级 $level"
    }

    private fun Double.formatCoordinate(): String =
        ((this * 1_000_000).toLong() / 1_000_000.0).toString()
}
