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

/**
 * 云端车辆绑定/解绑。
 *
 * 接口来自 `CarOwnerCodeInputModel`、`BikeBindCheckModel`、
 * `QRCodeScanModel` 与 `UserCarManageNewViewModel`。
 */
data class VehicleBindRequest(
    val userId: Long,
    val vin: String,
    val vinPwd: String,
    val smsCode: String,
    val primaryUserMobile: String,
    val primaryUserEmail: String,
)

sealed interface VehicleBindResult {
    data object Success : VehicleBindResult
    data class Failure(val message: String, val code: Int? = null) : VehicleBindResult
}

interface VehicleBindingGateway {
    suspend fun sendOwnerCode(vin: String, vinPwd: String): VehicleBindResult
    suspend fun bind(request: VehicleBindRequest): VehicleBindResult
    suspend fun unbind(bikeId: Long, mainAccount: Boolean): VehicleBindResult
}

class YadeaVehicleBindingGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleBindingGateway {

    override suspend fun sendOwnerCode(vin: String, vinPwd: String): VehicleBindResult {
        if (vin.isBlank() || vinPwd.isBlank()) {
            return VehicleBindResult.Failure("请输入 VIN 和车主码")
        }
        val plain = postFields(
            path = "userBike/sendMsgToCarOwner",
            fields = mapOf(
                "_gateway" to "True",
                "vin" to vin.trim(),
                "vinPwd" to vinPwd.trim(),
            ),
        ) ?: return VehicleBindResult.Failure("网络不可用")
        return resultOf(plain)
    }

    override suspend fun bind(request: VehicleBindRequest): VehicleBindResult {
        if (request.userId <= 0L) return VehicleBindResult.Failure("登录用户 ID 缺失")
        if (request.vin.isBlank() || request.vinPwd.isBlank()) {
            return VehicleBindResult.Failure("请输入 VIN 和车主码")
        }
        val plain = postFields(
            path = "userBike/manualOrQrBindVehicle",
            fields = mapOf(
                "_gateway" to "True",
                "userId" to request.userId,
                "vin" to request.vin.trim(),
                "vinPwd" to request.vinPwd.trim(),
                "smsCode" to request.smsCode.trim(),
                "primaryUserMobile" to request.primaryUserMobile.trim(),
                "primaryUserEmail" to request.primaryUserEmail.trim(),
            ),
        ) ?: return VehicleBindResult.Failure("网络不可用")
        val result = resultOf(plain)
        if (result is VehicleBindResult.Success) {
            // 原实现只有 data == 1 才算绑定成功。
            if (JsonSupport.int(plain, "data") != 1) {
                return VehicleBindResult.Failure("绑定失败，请检查车主验证码")
            }
        }
        return result
    }

    override suspend fun unbind(bikeId: Long, mainAccount: Boolean): VehicleBindResult {
        if (bikeId <= 0L) return VehicleBindResult.Failure("当前车辆缺少 bikeId")
        val plain = postFields(
            path = if (mainAccount) {
                "userBike/mainUnbundlingMain"
            } else {
                "userBike/childUnbundlingchild"
            },
            fields = mapOf(
                "_gateway" to "True",
                "bikeId" to bikeId,
            ),
        ) ?: return VehicleBindResult.Failure("网络不可用")
        return resultOf(plain)
    }

    private suspend fun postFields(path: String, fields: Map<String, Any>): String? {
        val token = tokenProvider().trim()
        if (token.isBlank()) return null
        val body = GatewaySigner.formBody(sortedJson(fields), timestampProvider())
        val url = baseUrl + path
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write(
            "BIND -> POST $url headers=${NetworkLog.headers(headers)} " +
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
            NetworkLog.write("BIND !! no response")
            return null
        }
        NetworkLog.write("BIND <- ${response.status} raw=${NetworkLog.body(response.body)}")
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("BIND plain ${NetworkLog.maskSensitive(plain)}")
        return plain
    }

    private fun resultOf(plain: String): VehicleBindResult {
        if (JsonSupport.boolean(plain, "success") == false) {
            return VehicleBindResult.Failure(
                message = JsonSupport.string(plain, "msg").orEmpty().ifBlank { "操作失败" },
                code = JsonSupport.int(plain, "code"),
            )
        }
        return VehicleBindResult.Success
    }

    private fun sortedJson(fields: Map<String, Any>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            val value = fields.getValue(key)
            if (index > 0) append(',')
            append('"').append(key).append("\":")
            when (value) {
                is Number, is Boolean -> append(value)
                else -> append('"')
                    .append(value.toString().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"')
            }
        }
        append('}')
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

data class VehicleBindingUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val success: Boolean = false,
)

class VehicleBindingController(
    private val gateway: VehicleBindingGateway,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit = {},
) {
    private val _state = MutableStateFlow(VehicleBindingUiState())
    val state: StateFlow<VehicleBindingUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun sendOwnerCode(vin: String, vinPwd: String) {
        launchRequest("车主验证码已发送") { gateway.sendOwnerCode(vin, vinPwd) }
    }

    fun bind(request: VehicleBindRequest) {
        launchRequest("绑定成功，已刷新车库") { gateway.bind(request) }
    }

    fun unbind(bikeId: Long, mainAccount: Boolean) {
        launchRequest("解绑成功，已刷新车库") { gateway.unbind(bikeId, mainAccount) }
    }

    fun clear() {
        _state.value = VehicleBindingUiState()
    }

    private fun launchRequest(
        successMessage: String,
        block: suspend () -> VehicleBindResult,
    ) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = VehicleBindingUiState(busy = true)
            val result = runCatching { block() }
                .getOrElse { VehicleBindResult.Failure(it.message ?: "操作失败") }
            _state.value = when (result) {
                VehicleBindResult.Success -> {
                    onChanged()
                    VehicleBindingUiState(message = successMessage, success = true)
                }
                is VehicleBindResult.Failure -> VehicleBindingUiState(
                    message = result.message,
                    success = false,
                )
            }
        }
    }
}
