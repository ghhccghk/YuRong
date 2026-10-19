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
        val status = VehicleCloudStatus(
            remainingMileage = normalizeRangeText(text(data, "remMileage")),
            totalMileage = text(data, "totalMileage"),
            currentOdoValue = text(data, "currentOdoValue").toFloatOrNull(),
            soc = JsonSupport.int(data, "totalSoc")
                ?: JsonSupport.string(data, "soc1")?.toIntOrNull(),
        )
        NetworkLog.write(
            "CLOUD_STATUS values rem=${status.remainingMileage} " +
                "total=${status.totalMileage} odo=${status.currentOdoValue} soc=${status.soc}",
        )
        return status
    }

    private fun text(json: String, key: String): String =
        JsonSupport.string(json, key)
            ?: JsonSupport.numberText(json, key)
            ?: ""

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        const val PATH = "bikeRealStatus/getVehRealStatus"
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
