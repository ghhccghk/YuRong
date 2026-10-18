package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport

interface VehicleProfileGateway {
    suspend fun load(): VehicleProfile?
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
) : VehicleProfileGateway {

    override suspend fun load(): VehicleProfile? {
        val token = tokenProvider().trim()
        if (token.isBlank()) return null
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = baseUrl + "userBike/queryBindingBike",
                headers = buildMap {
                    put("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
                    put("request-agent", "2")
                    put("deviceId", loginIdProvider())
                    put("os", "android")
                    put("app-name", "YADEA")
                    put("language", "zh_CN")
                    put("push-operator", "2")
                },
            ),
        ) ?: return null
        if (response.status !in 200..299) return null
        val objectText = firstDataObject(response.body) ?: return null
        val image = stringField(objectText, "skinPicUrl").orEmpty()
            .ifBlank { stringField(objectText, "imgUrl").orEmpty() }
        return VehicleProfile(
            vin = stringField(objectText, "vin").orEmpty(),
            modelNo = stringField(objectText, "modelNo").orEmpty(),
            dLinkTag = stringField(objectText, "dLinkTag").orEmpty(),
            vehicleImageUrl = image,
            bikeNickName = stringField(objectText, "bikeNickName").orEmpty(),
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
    fun refresh() {
        scope.launch {
            runCatching { gateway.load() }
                .getOrNull()
                ?.let(source::update)
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
