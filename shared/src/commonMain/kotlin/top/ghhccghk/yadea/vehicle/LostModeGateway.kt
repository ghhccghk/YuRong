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

data class LostModeResult(
    val success: Boolean,
    val message: String = "",
)

/**
 * 报失模式云端链路：
 *
 * - `user/sendCode`，`sendCode=10`
 * - `user/verifySmsCode`，`smsScene=10`
 * - `common-cmd/low-freq/set-param/loss`，`00` 开、`01` 关
 */
interface LostModeGateway {
    suspend fun sendCode(mobile: String): LostModeResult
    suspend fun verifyCode(mobile: String, code: String): LostModeResult
    suspend fun setLostMode(vin: String, enabled: Boolean): LostModeResult
}

class YadeaLostModeGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : LostModeGateway {

    override suspend fun sendCode(mobile: String): LostModeResult {
        val normalized = mobile.trim()
        if (normalized.isBlank()) return LostModeResult(false, "请输入手机号")
        return postSigned(
            path = SEND_CODE_PATH,
            businessJson = sortedJson(
                mapOf(
                    "_gateway" to "True",
                    "mobile" to normalized,
                    "sendCode" to SEND_CODE_SCENE,
                ),
            ),
        )
    }

    override suspend fun verifyCode(mobile: String, code: String): LostModeResult {
        val normalizedMobile = mobile.trim()
        val normalizedCode = code.trim()
        if (normalizedMobile.isBlank() || normalizedCode.isBlank()) {
            return LostModeResult(false, "请输入手机号和验证码")
        }
        return postSigned(
            path = VERIFY_CODE_PATH,
            businessJson = sortedJson(
                mapOf(
                    "_gateway" to "True",
                    "appName" to "YADEA",
                    "mobile" to normalizedMobile,
                    "smsCode" to normalizedCode,
                    "smsScene" to SEND_CODE_SCENE,
                ),
            ),
        )
    }

    override suspend fun setLostMode(vin: String, enabled: Boolean): LostModeResult {
        val normalizedVin = vin.trim()
        if (normalizedVin.isBlank()) return LostModeResult(false, "缺少 VIN")
        val value = if (enabled) "00" else "01"
        val path = "$SET_LOSS_PATH?vin=$normalizedVin&value=$value"
        return postSigned(
            path = path,
            businessJson = sortedJson(
                mapOf(
                    "_gateway" to "True",
                    "value" to value,
                    "vin" to normalizedVin,
                ),
            ),
        )
    }

    private suspend fun postSigned(path: String, businessJson: String): LostModeResult {
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = tokenProvider().trim(),
        )
        NetworkLog.write(
            "LOST_MODE -> POST ${baseUrl + path} " +
                "headers=${NetworkLog.headers(headers)} ${NetworkLog.formSummary(body)}",
        )
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + path,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return LostModeResult(false, "网络不可用")
        if (response.status !in 200..299) {
            return LostModeResult(false, "HTTP ${response.status}")
        }
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("LOST_MODE plain path=$path ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) {
            return LostModeResult(
                success = false,
                message = JsonSupport.string(plain, "msg").orEmpty().ifBlank { "请求失败" },
            )
        }
        return LostModeResult(true)
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
        const val SEND_CODE_PATH = "user/sendCode"
        const val VERIFY_CODE_PATH = "user/verifySmsCode"
        const val SET_LOSS_PATH = "common-cmd/low-freq/set-param/loss"
        const val SEND_CODE_SCENE = "10"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

data class LostModeState(
    val busy: Boolean = false,
    val codeSent: Boolean = false,
    val enabled: Boolean? = null,
    val message: String? = null,
)

class LostModeController(
    private val gateway: LostModeGateway?,
    private val scope: CoroutineScope,
    private val onApplied: () -> Unit = {},
) {
    private val _state = MutableStateFlow(LostModeState())
    val state: StateFlow<LostModeState> = _state.asStateFlow()
    val available: Boolean
        get() = gateway != null
    private var job: Job? = null

    fun sendCode(mobile: String) {
        val active = gateway ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = runCatching { active.sendCode(mobile) }
                .getOrElse { LostModeResult(false, it.message.orEmpty()) }
            _state.value = _state.value.copy(
                busy = false,
                codeSent = result.success,
                message = if (result.success) {
                    "验证码已发送，请查看车主手机"
                } else {
                    result.message.ifBlank { "验证码发送失败" }
                },
            )
        }
    }

    fun apply(vin: String, mobile: String, code: String, enabled: Boolean) {
        val active = gateway ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val verify = runCatching { active.verifyCode(mobile, code) }
                .getOrElse { LostModeResult(false, it.message.orEmpty()) }
            if (!verify.success) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = verify.message.ifBlank { "验证码校验失败" },
                )
                return@launch
            }
            val result = runCatching { active.setLostMode(vin, enabled) }
                .getOrElse { LostModeResult(false, it.message.orEmpty()) }
            _state.value = _state.value.copy(
                busy = false,
                enabled = if (result.success) enabled else _state.value.enabled,
                message = if (result.success) {
                    if (enabled) "报失模式已开启" else "报失模式已关闭"
                } else {
                    result.message.ifBlank { "报失模式设置失败" }
                },
            )
            if (result.success) onApplied()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dispose() {
        job?.cancel()
        job = null
    }
}
