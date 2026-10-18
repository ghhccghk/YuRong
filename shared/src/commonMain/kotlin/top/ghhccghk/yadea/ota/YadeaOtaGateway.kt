package top.ghhccghk.yadea.ota

import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.GatewayHeaders
import top.ghhccghk.yadea.vehicle.VehicleProfile

/**
 * OTA 网络层。
 *
 * 检查更新来自 `firmwareVersion/latestVersion`；任务列表来自
 * `upgradeTask/queryTaskList/V4`。真正的固件下载/写入仍由平台安装器完成，
 * 因为不同车型会走 BLE 或 Wi-Fi/P2P 通道。
 */
class YadeaOtaGateway(
    private val transport: HttpTransport,
    private val profileProvider: () -> VehicleProfile?,
    private val tokenProvider: () -> String,
    private val loginIdProvider: () -> String = { "" },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : OtaGateway {

    override suspend fun checkForUpdate(currentVersion: String): OtaPackage? {
        val profile = profileProvider() ?: return null
        if (profile.modelName.isBlank()) return null
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "firmwareTypes" to "4",
                "scooterModelName" to profile.modelName,
            ),
        )
        val plain = post("firmwareVersion/latestVersion", json) ?: return null
        val data = JsonSupport.firstArrayObject(plain, "data")
            ?: JsonSupport.objectField(plain, "data")
            ?: return null
        val version = JsonSupport.string(data, "firmwareVersion")
            ?: JsonSupport.numberText(data, "firmwareVersion")
            ?: return null
        if (version == currentVersion) return null
        return OtaPackage(
            version = version,
            releaseNotes = JsonSupport.string(data, "upgradeContent").orEmpty(),
            downloadUrl = JsonSupport.string(data, "url").orEmpty(),
        )
    }

    /**
     * 查询 OTA 任务列表。
     *
     * 返回原始解密 JSON；调用方可用 `taskId/status` 继续轮询。
     */
    suspend fun queryTaskList(): String? {
        val profile = profileProvider() ?: return null
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "experimental" to "",
                "izSkip" to true,
                "modelNo" to profile.modelNo,
                "vin" to profile.vin,
            ),
        )
        return post("upgradeTask/queryTaskList/V4", json)
    }

    /**
     * 固件写入交给平台 OTA 传输层；网络层只确认包存在。
     *
     * 当前返回 `false`，避免在未接入 BLE/Wi-Fi OTA 通道时误报升级成功。
     */
    override suspend fun install(
        packageInfo: OtaPackage,
        onProgress: suspend (Float, OtaStage) -> Unit,
    ): Boolean = false

    private suspend fun post(path: String, businessJson: String): String? {
        val token = tokenProvider().trim()
        if (token.isBlank()) return null
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + path,
                headers = buildMap {
                    put(GatewayHeaders.KEY_GATEWAY_APP_ID, GatewayHeaders.GATEWAY_APP_ID)
                    put("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
                    put("req-id", timestampProvider().toString())
                    put("request-agent", "2")
                    put("deviceId", loginIdProvider())
                    put("os", "android")
                    put("app-name", "YADEA")
                    put("language", "zh_CN")
                    put("push-operator", "2")
                },
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return null
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            decoded,
        ) ?: decoded
        if (JsonSupport.boolean(plain, "success") == false) return null
        return plain
    }

    private fun sortedJson(fields: Map<String, Any>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            val value = fields.getValue(key)
            if (index > 0) append(',')
            append('"').append(key).append("\":")
            when (value) {
                is String -> append('"')
                    .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"')
                is Boolean -> append(value)
                is Number -> append(value)
                else -> append('"').append(value.toString()).append('"')
            }
        }
        append('}')
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
