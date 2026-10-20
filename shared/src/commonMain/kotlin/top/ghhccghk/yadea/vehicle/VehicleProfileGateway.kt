package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

interface VehicleProfileGateway {
    suspend fun load(): VehicleProfile?
    suspend fun loadAll(): List<VehicleProfile> = listOfNotNull(load())
}

/**
 * `userBike/queryBindingBike` 的只读客户端。
 *
 * 原请求为 GET，响应类型是 `CacheResult<List<VehicleInfoBean>>`。
 * 这里只抽取界面需要的字段，避免为整个 295 字段模型引入 Gson 兼容层。
 */
class YadeaVehicleProfileGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val loginIdProvider: () -> String = { "" },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long = { 0L },
) : VehicleProfileGateway {

    override suspend fun load(): VehicleProfile? = loadAll().firstOrNull()

    override suspend fun loadAll(): List<VehicleProfile> {
        val token = tokenProvider().trim()
        if (token.isBlank()) return emptyList()
        val signedQuery = GatewaySigner.formBody(
            """{"_gateway":"True"}""",
            timestampProvider(),
        )
        val url = baseUrl + "userBike/queryBindingBike?" + signedQuery
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = loginIdProvider(),
            token = token,
        )
        NetworkLog.write("PROFILE -> GET $url headers=${NetworkLog.headers(headers)}")
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = headers,
            ),
        ) ?: run {
            NetworkLog.write("PROFILE !! no response")
            return emptyList()
        }
        NetworkLog.write(
            "PROFILE <- ${response.status} raw=${NetworkLog.body(response.body)}",
        )
        if (response.status !in 200..299) return emptyList()
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("PROFILE plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) {
            NetworkLog.write("PROFILE !! business failure")
            return emptyList()
        }
        val objectTexts = JsonSupport.arrayObjects(plain, "data")
            .ifEmpty { listOfNotNull(JsonSupport.objectField(plain, "data")) }
        val profiles = objectTexts.mapNotNull(::parseProfile)
        NetworkLog.write("PROFILE vehicles=${profiles.size} vins=${profiles.map { it.vin }}")
        return profiles
    }

    private fun parseProfile(objectText: String): VehicleProfile? {
        return VehicleProfile(
            vin = stringField(objectText, "vin").orEmpty(),
            bikeId = longField(objectText, "bikeId") ?: 0L,
            modelNo = stringField(objectText, "modelNo").orEmpty(),
            modelName = stringField(objectText, "modelName").orEmpty(),
            dLinkTag = stringField(objectText, "dLinkTag").orEmpty(),
            experimental = textOrNumber(objectText, "experimental").orEmpty(),
            backgroundImageUrl = stringField(objectText, "skinPicUrl").orEmpty(),
            vehicleImageUrl = stringField(objectText, "imgUrl").orEmpty(),
            bikeNickName = stringField(objectText, "bikeNickName").orEmpty(),
            remainingMileage = normalizeRangeText(stringField(objectText, "remMileage").orEmpty()),
            iotService = intField(objectText, "iotExpireDays")?.let { days ->
                VehicleIotServiceInfo(
                    expireDays = days,
                    cardExpireTime = stringField(objectText, "iotCardExpireTime").orEmpty(),
                    iccid = stringField(objectText, "iccid").orEmpty(),
                    cardStatus = intField(objectText, "cardStatus"),
                )
            },
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

class VehicleProfileController(
    private val source: MutableVehicleProfileSource,
    private val gateway: VehicleProfileGateway,
    private val scope: CoroutineScope,
) {
    private val _refreshing = MutableStateFlow(false)

    /** 是否正在进行云端车辆列表刷新；界面可据此禁用重复点击。 */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)

    /** 最近一次刷新失败的原因；成功刷新后清空。 */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var refreshJob: Job? = null

    /**
     * 主动刷新已登录账号的绑定车辆。
     *
     * 已有刷新请求进行时直接返回，避免重复点击或登录状态变化触发并发请求。
     * 接口返回空列表时保留上一次成功结果，避免一次网络抖动把车库清空。
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            _refreshing.value = true
            _lastError.value = null
            runCatching { gateway.loadAll() }
                .onSuccess { profiles ->
                    if (profiles.isNotEmpty()) {
                        source.updateAll(profiles)
                    } else if (source.profiles.value.isEmpty()) {
                        _lastError.value = "未获取到绑定车辆，请确认 Token 有效后重试。"
                    } else {
                        _lastError.value = "本次刷新未返回车辆，已保留上次列表。"
                    }
                }
                .onFailure { error ->
                    _lastError.value = "刷新失败：${error.message ?: error::class.simpleName}"
                }
            _refreshing.value = false
        }
    }
}

private fun firstDataObject(json: String): String? {
    val dataIndex = json.indexOf("\"data\"")
    if (dataIndex < 0) return null
    val arrayStart = json.indexOf('[', dataIndex)
    if (arrayStart < 0) return null
    var i = arrayStart + 1
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != '{') return null
    val start = i
    var depth = 0
    var inString = false
    var escaped = false
    while (i < json.length) {
        val c = json[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(start, i + 1)
                }
            }
        }
        i++
    }
    return null
}

private fun stringField(json: String, key: String): String? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(json) ?: return null
    return unescapeJson(match.groupValues[1])
}

private fun intField(json: String, key: String): Int? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(json)
        ?: return null
    return match.groupValues[1].toIntOrNull()
}

    private fun textOrNumber(json: String, key: String): String? =
        stringField(json, key)
            ?: Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
                .find(json)
                ?.groupValues
                ?.get(1)

private fun longField(json: String, key: String): Long? {
    val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(json)
        ?: return null
    return match.groupValues[1].toLongOrNull()
}

private fun unescapeJson(value: String): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i++]
        if (c != '\\' || i >= value.length) {
            out.append(c)
            continue
        }
        when (val escaped = value[i++]) {
            '"' -> out.append('"')
            '\\' -> out.append('\\')
            '/' -> out.append('/')
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> {
                if (i + 4 <= value.length) {
                    value.substring(i, i + 4).toIntOrNull(16)?.let { out.append(it.toChar()) }
                    i += 4
                }
            }
            else -> out.append(escaped)
        }
    }
    return out.toString()
}
