package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 车辆状态数据源。
 *
 * 平台侧把 GATT 通知解析后的 [TtpInfo] 写入这里；UI 只读 StateFlow。
 * 没有注入实现时，车辆页会明确显示“等待车辆状态”，不会伪造数据。
 */
interface VehicleStatusSource {
    val status: StateFlow<TtpInfo?>
    val connected: StateFlow<Boolean>
}

class EmptyVehicleStatusSource : VehicleStatusSource {
    private val _status = MutableStateFlow<TtpInfo?>(null)
    private val _connected = MutableStateFlow(false)
    override val status: StateFlow<TtpInfo?> = _status.asStateFlow()
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()
}

/** 车辆信息页的格式化结果。 */
data class VehicleInfoUiModel(
    val connectionLabel: String,
    val lockLabel: String,
    val powerLabel: String,
    val speedLabel: String,
    val batteryLabel: String,
    val rangeLabel: String,
    val odoLabel: String,
    val tripLabel: String,
    val voltageLabel: String,
    val currentLabel: String,
    val temperatureLabel: String,
    val gearLabel: String,
    val cruiseLabel: String,
    val chargeLabel: String,
    val faultLabel: String,
    val sensorLines: List<Pair<String, String>>,
)

object VehicleInfoFormatter {
    private const val EMPTY = "—"

    fun format(info: TtpInfo?, connected: Boolean): VehicleInfoUiModel {
        if (info == null) {
            return VehicleInfoUiModel(
                connectionLabel = if (connected) "已连接，等待车辆上报" else "未连接",
                lockLabel = EMPTY,
                powerLabel = EMPTY,
                speedLabel = EMPTY,
                batteryLabel = EMPTY,
                rangeLabel = EMPTY,
                odoLabel = EMPTY,
                tripLabel = EMPTY,
                voltageLabel = EMPTY,
                currentLabel = EMPTY,
                temperatureLabel = EMPTY,
                gearLabel = EMPTY,
                cruiseLabel = EMPTY,
                chargeLabel = EMPTY,
                faultLabel = EMPTY,
                sensorLines = emptyList(),
            )
        }

        val ext = info.extended
        val faults = info.faultCodes.filter { it != 0 }.ifEmpty {
            if (info.faultCodeAmount > 0) listOf(info.faultCodeAmount) else emptyList()
        }
        return VehicleInfoUiModel(
            connectionLabel = if (connected) "车辆在线" else "状态缓存",
            lockLabel = when (info.parkingStatus) {
                0 -> "已锁车"
                1 -> "未锁车"
                else -> EMPTY
            },
            powerLabel = when (info.onOffStatus) {
                in 0..7 -> "已开机"
                in 8..16 -> "已关机"
                else -> EMPTY
            },
            speedLabel = unit(info.currentSpeedValue, "km/h", decimals = 1),
            batteryLabel = unit(info.totalBatterySoc, "%", decimals = 0),
            rangeLabel = unit(info.remainingMileage, "km", decimals = 1),
            odoLabel = unit(info.currentOdoValue, "km", decimals = 1),
            tripLabel = unit(info.currentTripValue, "km", decimals = 1),
            voltageLabel = unit(info.totalBatteryVoltage, "V", decimals = 1),
            currentLabel = unit(info.totalBatteryElectricity, "A", decimals = 1),
            temperatureLabel = unit(ext.batteryTemperature, "°C", decimals = 1),
            gearLabel = intOrDash(info.speedGear),
            cruiseLabel = switchLabel(info.cruiseControl),
            chargeLabel = when (info.batteryChargingStatus) {
                0 -> "未充电"
                1 -> "充电中"
                else -> intOrDash(info.batteryChargingStatus)
            },
            faultLabel = if (faults.isEmpty()) "无故障" else "发现 ${faults.size} 项",
            sensorLines = listOf(
                "前后电池 SOC" to "${soc(info.pBElectricitySoc)} / ${soc(info.sBElectricitySoc)}",
                "充电功率" to if (ext.batteryChargingPowerValue < 0) {
                    EMPTY
                } else {
                    "${ext.batteryChargingPowerValue} W"
                },
                "电池互认" to switchLabel(ext.batteryMutualRecognition),
                "座椅加热" to switchLabel(ext.seatHeatSwitchStatus),
                "定速巡航状态" to switchLabel(ext.constSpeedState),
                "倒车状态" to switchLabel(ext.reversingState),
                "FCM 雷达" to switchLabel(ext.fcmRadarEnable),
                "RCW 雷达" to switchLabel(ext.rcwRadarEnable),
                "充电上限" to if (ext.batteryChargingLimitValue < 0) {
                    EMPTY
                } else {
                    "${ext.batteryChargingLimitValue}/7"
                },
            ),
        )
    }

    fun softwareVersion(info: TtpInfo?): String {
        if (info == null) return EMPTY
        val major = info.softwarePrimaryVersionCode
        val minor = info.softwareSecondaryVersionCode
        if (major < 0 && minor < 0) return EMPTY
        return "${intOrDash(major)}.${intOrDash(minor)}"
    }

    private fun unit(value: Float, suffix: String, decimals: Int): String {
        if (value == Ttp.INVALID_F || value.isNaN()) return EMPTY
        val factor = when (decimals) {
            0 -> 1f
            1 -> 10f
            else -> 100f
        }
        val rounded = kotlin.math.round(value * factor) / factor
        val text = if (decimals == 0) {
            abs(rounded).toInt().toString()
        } else {
            val scaled = kotlin.math.round(abs(rounded) * factor).toInt()
            "${scaled / factor.toInt()}.${(scaled % factor.toInt()).toString().padStart(decimals, '0')}"
        }
        return if (rounded < 0) "-$text $suffix" else "$text $suffix"
    }

    private fun soc(value: Float): String =
        if (value == Ttp.INVALID_F) EMPTY else "${unit(value, "%", 1)}"

    private fun intOrDash(value: Int): String =
        if (value == Ttp.INVALID_I) EMPTY else value.toString()

    private fun switchLabel(value: Int): String = when (value) {
        -1 -> EMPTY
        0 -> "关闭"
        1 -> "开启"
        else -> value.toString()
    }
}
