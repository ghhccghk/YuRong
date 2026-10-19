package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/** `BleTokenBean` 中握手真正使用的密钥材料。 */
data class VehicleBleToken(
    val dkInfo: String,
    val dkInfoMax: Int? = null,
)

interface VehicleBleTokenProvider {
    suspend fun fetch(mac: String, vin: String, refresh: Boolean): VehicleBleToken?
}

/**
 * `iot-auth/ble/token` 客户端。
 *
 * 请求字段为 `vin`、`mac`、`version=1`、`refreshToken=0/1`，并沿用网关统一
 * 的 `_gateway` 签名、登录 Token 与客户端请求头。
 */
class YadeaVehicleBleTokenGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleBleTokenProvider {

    override suspend fun fetch(mac: String, vin: String, refresh: Boolean): VehicleBleToken? {
        val loginToken = tokenProvider().trim()
        val normalizedMac = mac.trim()
        val normalizedVin = vin.trim()
        if (loginToken.isBlank() || normalizedMac.isBlank() || normalizedVin.isBlank()) {
            return null
        }

        val fields = buildMap {
            put("_gateway", "True")
            put("mac", normalizedMac)
            put("refreshToken", if (refresh) "1" else "0")
            put("version", "1")
            put("vin", normalizedVin)
        }
        val url = baseUrl + PATH
        val body = GatewaySigner.formBody(sortedJson(fields), timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = loginToken,
        )
        NetworkLog.write(
            "BLE_TOKEN -> POST $url headers=${NetworkLog.headers(headers)} " +
                NetworkLog.formSummary(body),
        )
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: run {
            NetworkLog.write("BLE_TOKEN !! no response")
            return null
        }
        NetworkLog.write(
            "BLE_TOKEN <- ${response.status} raw=${NetworkLog.body(response.body)}",
        )
        if (response.status !in 200..299) return null

        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        if (JsonSupport.boolean(plain, "success") == false) return null
        val data = JsonSupport.objectField(plain, "data") ?: plain
        val dkInfo = JsonSupport.string(data, "dkInfo").orEmpty().trim()
        if (dkInfo.isBlank()) return null
        return VehicleBleToken(
            dkInfo = dkInfo,
            dkInfoMax = JsonSupport.int(data, "dkInfoMax")
                ?: JsonSupport.string(data, "dkInfoMax")?.toIntOrNull(),
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
        const val PATH = "iot-auth/ble/token"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
