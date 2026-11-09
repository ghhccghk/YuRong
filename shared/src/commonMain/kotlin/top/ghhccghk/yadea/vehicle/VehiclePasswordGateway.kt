package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.carsetting.VehicleSettingCommand
import top.ghhccghk.yadea.carsetting.VehicleSettingCommandSink
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

data class PasswordEnableState(
    val status: Int? = null,
    val hasPassword: Boolean? = null,
)

data class PasswordVerifyResult(
    val valid: Boolean,
    val errorCode: Int = 0,
    val message: String = "",
)

data class PasswordSmsVerifyResult(
    val success: Boolean,
    val validCode: String = "",
    val message: String = "",
)

data class PasswordRequestResult(
    val success: Boolean,
    val message: String = "",
)

interface VehiclePasswordGateway {
    suspend fun getEnableState(vin: String): PasswordEnableState
    suspend fun exists(vin: String): Boolean?
    suspend fun set(vin: String, password: String): PasswordRequestResult
    suspend fun reset(vin: String, password: String, validCode: String): PasswordRequestResult
    suspend fun verify(vin: String, password: String): PasswordVerifyResult
    suspend fun sendSmsCode(vin: String, mobile: String): PasswordRequestResult
    suspend fun verifySmsCode(vin: String, mobile: String, smsCode: String): PasswordSmsVerifyResult
    suspend fun updateEnable(vin: String, enabled: Boolean): PasswordRequestResult
}

class YadeaVehiclePasswordGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehiclePasswordGateway {

    override suspend fun getEnableState(vin: String): PasswordEnableState {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return PasswordEnableState()
        val plain = getSigned(GET_ENABLE_PATH, mapOf("vin" to vin), token) ?: return PasswordEnableState()
        val data = JsonSupport.objectField(plain, "data") ?: return PasswordEnableState()
        return PasswordEnableState(
            status = JsonSupport.int(data, "status"),
            hasPassword = JsonSupport.boolean(data, "hasPassword") ?: JsonSupport.string(data, "unlockPassword")?.isNotBlank(),
        )
    }

    override suspend fun exists(vin: String): Boolean? {
        val plain = postSigned(EXISTS_PATH, mapOf("vin" to vin)) ?: return null
        val data = JsonSupport.objectField(plain, "data")
        return when {
            data != null -> JsonSupport.boolean(data, "exists") ?: JsonSupport.boolean(data, "data")
            else -> JsonSupport.boolean(plain, "data")
        }
    }

    override suspend fun set(vin: String, password: String): PasswordRequestResult =
        postResult(
            SET_PATH,
            mapOf(
                "vin" to vin,
                "unlockPassword" to encryptPassword(password),
                "encryPtion" to "1",
            ),
        )

    override suspend fun reset(vin: String, password: String, validCode: String): PasswordRequestResult =
        postResult(
            RESET_PATH,
            mapOf(
                "vin" to vin,
                "validType" to "2",
                "unlockPassword" to encryptPassword(password),
                "validCode" to validCode,
                "encryPtion" to "1",
            ),
        )

    override suspend fun verify(vin: String, password: String): PasswordVerifyResult {
        val plain = postSigned(
            VERIFY_PATH,
            mapOf(
                "vin" to vin,
                "unlockPassword" to encryptPassword(password),
            ),
        ) ?: return PasswordVerifyResult(false, message = "网络异常")
        val data = JsonSupport.objectField(plain, "data")
        val valid = when {
            data != null -> JsonSupport.boolean(data, "isValid") == true
            else -> JsonSupport.boolean(plain, "isValid") == true
        }
        return PasswordVerifyResult(
            valid = valid,
            errorCode = data?.let { JsonSupport.int(it, "errorCode") } ?: 0,
            message = data?.let { JsonSupport.string(it, "msg") }.orEmpty(),
        )
    }

    override suspend fun sendSmsCode(vin: String, mobile: String): PasswordRequestResult =
        postResult(
            SMS_SEND_PATH,
            mapOf(
                "vin" to vin,
                "appName" to "YADEA",
                "sendCode" to "13",
                "mobile" to mobile,
            ),
        )

    override suspend fun verifySmsCode(
        vin: String,
        mobile: String,
        smsCode: String,
    ): PasswordSmsVerifyResult {
        val plain = postSigned(
            SMS_VERIFY_PATH,
            mapOf(
                "vin" to vin,
                "appName" to "YADEA",
                "smsScene" to "13",
                "mobile" to mobile,
                "smsCode" to smsCode,
            ),
        ) ?: return PasswordSmsVerifyResult(false, message = "网络异常")
        val data = JsonSupport.objectField(plain, "data")
        val validCode = data?.let { JsonSupport.string(it, "validCode") }.orEmpty()
        return PasswordSmsVerifyResult(
            success = validCode.isNotBlank(),
            validCode = validCode,
            message = if (validCode.isBlank()) "验证码校验未返回有效凭据" else "",
        )
    }

    override suspend fun updateEnable(vin: String, enabled: Boolean): PasswordRequestResult =
        postResult(
            ENABLE_UPDATE_PATH,
            mapOf(
                "vin" to vin,
                "status" to if (enabled) "1" else "0",
            ),
        )

    private suspend fun postResult(path: String, fields: Map<String, String>): PasswordRequestResult {
        val plain = postSigned(path, fields) ?: return PasswordRequestResult(false, "网络异常")
        val success = JsonSupport.boolean(plain, "success")
        return PasswordRequestResult(
            success = success != false,
            message = JsonSupport.string(plain, "msg").orEmpty(),
        )
    }

    private suspend fun getSigned(
        path: String,
        fields: Map<String, String>,
        token: String,
    ): String? {
        val body = GatewaySigner.formBody(sortedJson(fields), timestampProvider())
        val url = baseUrl + path + "?" + body
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("PASSWORD -> GET $url")
        val response = transport.execute(HttpRequest("GET", url, headers)) ?: return null
        if (response.status !in 200..299) return null
        return decrypt(response.body)
    }

    private suspend fun postSigned(path: String, fields: Map<String, String>): String? {
        val token = tokenProvider().trim()
        if (token.isBlank()) return null
        val body = GatewaySigner.formBody(sortedJson(fields), timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("PASSWORD -> POST ${baseUrl + path}")
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + path,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return null
        if (response.status !in 200..299) return null
        return decrypt(response.body)
    }

    private fun decrypt(raw: String): String {
        val decoded = Crypto.urlDecode(raw.trim())
        return Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
    }

    private fun encryptPassword(password: String): String =
        "::" + Crypto.aesEcbPkcs5Encrypt(PASSWORD_AES_KEY, password)

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
        const val GET_ENABLE_PATH = "unlock/password/enable/get"
        const val EXISTS_PATH = "unlock/password/exists"
        const val SET_PATH = "unlock/password/set"
        const val RESET_PATH = "unlock/password/reset"
        const val VERIFY_PATH = "unlock/password/verify"
        const val SMS_SEND_PATH = "unlock/password/sms-code/send"
        const val SMS_VERIFY_PATH = "unlock/password/sms-code/verify"
        const val ENABLE_UPDATE_PATH = "unlock/password/enable/update"
        const val PASSWORD_AES_KEY = "cIDJ9f05d1604703"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

enum class PasswordFlow {
    Set,
    Change,
    Forget,
}

enum class PasswordWriteState {
    LOCAL_UPDATED,
    CLOUD_ACCEPTED,
    VEHICLE_CONFIRMED,
    FAILED,
    UNKNOWN,
}

data class VehiclePasswordState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val flow: PasswordFlow? = null,
    val hasPassword: Boolean? = null,
    val enabledStatus: Int? = null,
    val codeSent: Boolean = false,
    val smsVerified: Boolean = false,
    val writeState: PasswordWriteState = PasswordWriteState.UNKNOWN,
    val message: String? = null,
    val success: Boolean = false,
)

class VehiclePasswordController(
    private val gateway: VehiclePasswordGateway,
    private val vinProvider: () -> String,
    private val mobileProvider: () -> String,
    private val commandSink: VehicleSettingCommandSink?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VehiclePasswordState())
    val state: StateFlow<VehiclePasswordState> = _state.asStateFlow()
    private var job: Job? = null
    private var pendingValidCode: String? = null

    fun refresh() {
        val vin = vinProvider().trim()
        if (vin.isBlank()) {
            _state.value = _state.value.copy(message = "请先绑定车辆")
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val enable = runCatching { gateway.getEnableState(vin) }
                .getOrDefault(PasswordEnableState())
            val exists = runCatching { gateway.exists(vin) }.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                hasPassword = exists ?: enable.hasPassword,
                enabledStatus = enable.status,
                message = if (exists == null && enable.status == null) "密码状态读取失败" else null,
            )
        }
    }

    fun begin(flow: PasswordFlow) {
        _state.value = _state.value.copy(
            flow = flow,
            codeSent = false,
            smsVerified = flow != PasswordFlow.Forget,
            writeState = PasswordWriteState.UNKNOWN,
            message = null,
            success = false,
        )
        if (flow == PasswordFlow.Forget) pendingValidCode = null
    }

    fun sendSmsCode() {
        val vin = vinProvider().trim()
        val mobile = mobileProvider().trim()
        if (vin.isBlank() || mobile.isBlank()) {
            _state.value = _state.value.copy(message = "缺少车辆或车主手机号")
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = runCatching { gateway.sendSmsCode(vin, mobile) }
                .getOrDefault(PasswordRequestResult(false, "网络异常"))
            _state.value = _state.value.copy(
                busy = false,
                codeSent = result.success,
                message = if (result.success) "验证码已发送" else result.message.ifBlank { "验证码发送失败" },
            )
        }
    }

    fun verifySmsCode(code: String) {
        val vin = vinProvider().trim()
        val mobile = mobileProvider().trim()
        if (vin.isBlank() || mobile.isBlank() || code.isBlank()) {
            _state.value = _state.value.copy(message = "请输入验证码")
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = runCatching { gateway.verifySmsCode(vin, mobile, code) }
                .getOrDefault(PasswordSmsVerifyResult(false, message = "网络异常"))
            pendingValidCode = result.validCode.ifBlank { null }
            _state.value = _state.value.copy(
                busy = false,
                smsVerified = result.success,
                message = result.message.ifBlank { if (result.success) "验证码校验成功" else "验证码校验失败" },
            )
        }
    }

    fun verifyCurrentPassword(password: String) {
        val vin = vinProvider().trim()
        if (vin.isBlank() || !isValidPassword(password)) {
            _state.value = _state.value.copy(message = passwordError(password))
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = runCatching { gateway.verify(vin, password) }
                .getOrDefault(PasswordVerifyResult(false, message = "网络异常"))
            _state.value = _state.value.copy(
                busy = false,
                smsVerified = result.valid,
                message = when {
                    result.valid -> "原密码验证成功"
                    result.errorCode == PASSWORD_ERROR_LIMIT -> "密码输入错误次数超限制，请一小时后再试"
                    else -> result.message.ifBlank { "密码错误" }
                },
            )
        }
    }

    fun submitPassword(password: String) {
        val vin = vinProvider().trim()
        if (vin.isBlank() || !isValidPassword(password)) {
            _state.value = _state.value.copy(message = passwordError(password))
            return
        }
        if (job?.isActive == true) return
        val flow = _state.value.flow
        val validCode = pendingValidCode
        if (flow == PasswordFlow.Forget && validCode.isNullOrBlank()) {
            _state.value = _state.value.copy(message = "请先完成验证码校验")
            return
        }
        job = scope.launch {
            _state.value = _state.value.copy(
                busy = true,
                message = null,
                writeState = PasswordWriteState.UNKNOWN,
                success = false,
            )
            val result = runCatching {
                if (flow == PasswordFlow.Forget) {
                    gateway.reset(vin, password, validCode.orEmpty())
                } else {
                    gateway.set(vin, password)
                }
            }.getOrDefault(PasswordRequestResult(false, "网络异常"))
            if (!result.success) {
                _state.value = _state.value.copy(
                    busy = false,
                    writeState = PasswordWriteState.FAILED,
                    message = result.message.ifBlank { "密码保存失败" },
                )
                return@launch
            }
            val sent = runCatching {
                commandSink?.send(
                    VehicleSettingCommand(
                        commandType = "COMMAND_PASSWORD_UNLOCK_SETTING",
                        commandId = 0x82,
                        parameter = password.toIntOrNull() ?: 0,
                    ),
                ) == true
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                busy = false,
                success = true,
                writeState = if (sent) PasswordWriteState.CLOUD_ACCEPTED else PasswordWriteState.CLOUD_ACCEPTED,
                message = if (sent) {
                    "云端已接受，等待车辆确认"
                } else {
                    "云端已接受，当前平台未发送车辆确认命令"
                },
            )
            pendingValidCode = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        val vin = vinProvider().trim()
        if (vin.isBlank()) return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val result = runCatching { gateway.updateEnable(vin, enabled) }
                .getOrDefault(PasswordRequestResult(false, "网络异常"))
            _state.value = _state.value.copy(
                busy = false,
                enabledStatus = if (result.success) if (enabled) 1 else 0 else _state.value.enabledStatus,
                message = if (result.success) "密码解锁状态已更新" else result.message.ifBlank { "状态更新失败" },
            )
        }
    }

    fun onVehiclePasswordResult(result: Int) {
        if (result < 0 || _state.value.writeState != PasswordWriteState.CLOUD_ACCEPTED) return
        _state.value = _state.value.copy(
            writeState = if (result == 0) PasswordWriteState.VEHICLE_CONFIRMED else PasswordWriteState.FAILED,
            message = if (result == 0) "车辆已确认密码设置" else "车辆未确认密码设置",
        )
    }

    companion object {
        const val PASSWORD_ERROR_LIMIT = 165303
    }
}

internal fun isValidPassword(value: String): Boolean {
    if (value.length != 4 || value.any { !it.isDigit() }) return false
    if (value[0] == value[1] && value[1] == value[2] && value[2] == value[3]) return false
    val ascending = (1 until value.length).all { value[it].code == value[it - 1].code + 1 }
    val descending = (1 until value.length).all { value[it].code == value[it - 1].code - 1 }
    return !ascending && !descending
}

private fun passwordError(value: String): String = when {
    value.length < 4 -> "请输入 4 位数字密码"
    value.any { !it.isDigit() } -> "密码只能包含数字"
    else -> "密码过于简单，请勿使用连续或重复数字"
}
