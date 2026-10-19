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
import kotlin.math.round

/**
 * 部分车型的 `remainingMileage` 以 10 米为单位，原实现按 0.1 km 放大后
 * 会出现 2867.4 km 这种明显不可能的值。按电动自行车合理上限做归一化。
 */
internal fun normalizeRangeKm(value: Float): Float =
    if (value > 500f) value / 100f else value

internal fun normalizeRangeText(raw: String): String {
    val value = raw.trim().toFloatOrNull() ?: return raw
    val normalized = normalizeRangeKm(value)
    if (normalized == value) return raw
    val rounded = round(normalized * 10f) / 10f
    return if (rounded == rounded.toInt().toFloat()) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

/**
 * `bikeRealStatus/getVehRealStatus` 的界面子集。
 *
 * 原 App 用 `VehicleStatusBean.totalMileage/remMileage/currentOdoValue`
 * 在未连接 BLE 时展示最近一次云端状态。
 */
data class VehicleCloudStatus(
    val remainingMileage: String = "",
    val totalMileage: String = "",
    val currentOdoValue: Float? = null,
    val soc: Int? = null,
    val soc1: String = "",
    val soc2: String = "",
)

interface VehicleCloudStatusGateway {
    suspend fun load(vin: String): VehicleCloudStatus?
}

class YadeaVehicleCloudStatusGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val loginIdProvider: () -> String = { "" },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long = { 0L },
) : VehicleCloudStatusGateway {

    override suspend fun load(vin: String): VehicleCloudStatus? {
        val token = tokenProvider().trim()
        val normalizedVin = vin.trim()
        if (token.isBlank() || normalizedVin.isBlank()) return null
        val businessJson = """{"_gateway":"True","vin":"${escape(normalizedVin)}"}"""
        val signedQuery = GatewaySigner.formBody(businessJson, timestampProvider())
        val url = baseUrl + PATH + "?" + signedQuery
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = loginIdProvider(),
            token = token,
        )
        NetworkLog.write("CLOUD_STATUS -> GET $url")
        val response = transport.execute(
            HttpRequest(method = "GET", url = url, headers = headers),
        ) ?: run {
            NetworkLog.write("CLOUD_STATUS !! no response")
            return null
        }
        if (response.status !in 200..299) {
            NetworkLog.write("CLOUD_STATUS !! http ${response.status}")
            return null
        }
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("CLOUD_STATUS plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) {
            NetworkLog.write("CLOUD_STATUS !! business failure")
            return null
        }
        val data = JsonSupport.objectField(plain, "data") ?: return null
        val rawRange = text(data, "remMileage")
        val rawRangeValue = rawRange.toFloatOrNull()
        val status = VehicleCloudStatus(
            // 超大的 remMileage 不能当公里数展示；估算接口失败时宁可显示空。
            remainingMileage = if (rawRangeValue != null && rawRangeValue > 500f) {
                ""
            } else {
                normalizeRangeText(rawRange)
            },
            totalMileage = text(data, "totalMileage"),
            currentOdoValue = text(data, "currentOdoValue").toFloatOrNull(),
            soc = JsonSupport.int(data, "totalSoc")
                ?: JsonSupport.string(data, "soc1")?.toIntOrNull(),
            soc1 = JsonSupport.string(data, "soc1").orEmpty(),
            soc2 = JsonSupport.string(data, "soc2").orEmpty(),
        )
        // 云端状态常常只给 totalSoc，soc1/soc2 为空；估算接口仍可用总 SOC 计算。
        val estimateSoc1 = status.soc1.ifBlank { status.soc?.toString().orEmpty() }
        val estimateSoc2 = status.soc2.ifBlank { "0" }
        val estimated = loadEstimatedMileage(normalizedVin, estimateSoc1, estimateSoc2)
        NetworkLog.write(
            "CLOUD_STATUS values rem=${status.remainingMileage} " +
                "estimate=${estimated.orEmpty()} total=${status.totalMileage} " +
                "odo=${status.currentOdoValue} soc=${status.soc} " +
                "soc1=${estimateSoc1} soc2=${estimateSoc2}",
        )
        return status.copy(remainingMileage = estimated ?: status.remainingMileage)
    }

    /**
     * 原 App 首页的剩余续航来自 `estimateMileage/cal2`：
     * `RemainingMileageData.estimateMileage`，而不是 `remMileage`。
     */
    private suspend fun loadEstimatedMileage(
        vin: String,
        soc1: String,
        soc2: String,
    ): String? {
        if (soc1.isBlank()) return null
        val businessJson = sortedJson(
            mapOf(
                "_gateway" to "True",
                "soc1" to soc1,
                "soc2" to soc2.ifBlank { "0" },
                "vin" to vin,
            ),
        )
        val url = baseUrl + ESTIMATE_PATH + "?" +
            GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = loginIdProvider(),
            token = tokenProvider().trim(),
        )
        val response = transport.execute(
            HttpRequest(method = "GET", url = url, headers = headers),
        ) ?: return null
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        if (JsonSupport.boolean(plain, "success") == false) return null
        val data = JsonSupport.objectField(plain, "data") ?: plain
        val estimate = JsonSupport.numberText(data, "estimateMileage")
            ?.toDoubleOrNull()
            ?: JsonSupport.string(data, "estimateMileage")?.toDoubleOrNull()
            ?: return null
        val rounded = round(estimate.toFloat() * 10f) / 10f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
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

    private fun text(json: String, key: String): String =
        JsonSupport.string(json, key)
            ?: JsonSupport.numberText(json, key)
            ?: ""

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val PATH = "bikeRealStatus/getVehRealStatus"
        const val ESTIMATE_PATH = "vehiclebase/estimateMileage/cal2"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

class VehicleCloudStatusController(
    private val gateway: VehicleCloudStatusGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<VehicleCloudStatus?>(null)
    val state: StateFlow<VehicleCloudStatus?> = _state.asStateFlow()
    private var job: Job? = null

    /** 刷新失败时保留上一次云端状态，避免未连接 BLE 时页面闪回空值。 */
    fun refresh(vin: String) {
        val normalizedVin = vin.trim()
        if (normalizedVin.isBlank()) return
        if (job?.isActive == true) return
        job = scope.launch {
            runCatching { gateway.load(normalizedVin) }
                .getOrNull()
                ?.let { _state.value = it }
        }
    }
}
