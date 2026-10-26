package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/**
 * `protocol/user/control-vehicle` 的云端控车链路。
 *
 * 原 App 在 BLE 不可用、或当前控车项要求车联网时使用该接口。
 * 请求字段固定为 `vin`、`id`、`clientId=1`、`controlMode=11`。
 */
class YadeaVehicleCloudControlGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleCloudControlSink {

    override suspend fun send(
        vin: String,
        command: VehicleControlCommand,
    ): VehicleCloudControlResult {
        val normalizedVin = vin.trim()
        if (normalizedVin.isBlank()) {
            return VehicleCloudControlResult(false, "缺少 VIN")
        }
        val commandId = command.cloudControlId
            ?: return VehicleCloudControlResult(false, "${command.label}不支持车联网下发")
        val token = tokenProvider().trim()
        if (token.isBlank()) {
            return VehicleCloudControlResult(false, "登录状态已失效")
        }
        val businessJson = sortedJson(
            mapOf(
                "_gateway" to "True",
                "clientId" to CLIENT_ID,
                "controlMode" to CONTROL_MODE,
                "id" to commandId,
                "vin" to normalizedVin,
            ),
        )
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write(
            "CLOUD_CONTROL -> POST ${baseUrl + PATH} " +
                "id=$commandId headers=${NetworkLog.headers(headers)} " +
                NetworkLog.formSummary(body),
        )
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + PATH,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return VehicleCloudControlResult(false, "网络不可用")
        if (response.status !in 200..299) {
            return VehicleCloudControlResult(false, "HTTP ${response.status}")
        }
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write(
            "CLOUD_CONTROL plain id=$commandId ${NetworkLog.maskSensitive(plain)}",
        )
        if (JsonSupport.boolean(plain, "success") == false) {
            return VehicleCloudControlResult(
                success = false,
                message = JsonSupport.string(plain, "msg").orEmpty().ifBlank { "云端控车失败" },
            )
        }
        return VehicleCloudControlResult(true)
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
        const val PATH = "protocol/user/control-vehicle"
        const val CLIENT_ID = "1"
        const val CONTROL_MODE = "11"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
