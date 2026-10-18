package top.ghhccghk.yadea.auth

import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.GatewayHeaders
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.NetworkLog

/**
 * Smali 确认的账号网络层。
 *
 * 请求使用 `_gateway=True` 的 form 签名；响应先 URLDecoder，
 * 再 AES-ECB 解密后读取 `success/code/msg/data`。
 */
class YadeaAuthGateway(
    private val transport: HttpTransport,
    private val timestampProvider: () -> Long,
    private val deviceIdProvider: () -> String = { "" },
    private val registrationIdProvider: () -> String = { "" },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val oppoPushIdProvider: () -> String = { "" },
) : AuthGateway {

    override suspend fun loginByPassword(request: PasswordLoginRequest): AuthResult<AuthUser> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "account" to request.account,
                "accountType" to request.accountType.value.toString(),
                "appType" to "1",
                "oppoPushId" to oppoPushIdProvider(),
                "password" to request.password,
                "registrationId" to registrationIdProvider(),
            ),
        )
        return post("user/loginByPassword", json)
    }

    override suspend fun sendSmsCode(mobile: String): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "mobile" to mobile,
                "registrationId" to registrationIdProvider(),
                "sendCode" to "1",
            ),
        )
        return when (val result = postRaw("user/sendCode", json)) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> result
        }
    }

    override suspend fun loginBySms(mobile: String, code: String): AuthResult<AuthUser> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "code" to code,
                "loginAppType" to "1",
                "mobile" to mobile,
                "oppoPushId" to oppoPushIdProvider(),
                "registrationId" to registrationIdProvider(),
            ),
        )
        return post("user/loginBySms", json)
    }

    private suspend fun post(path: String, businessJson: String): AuthResult<AuthUser> {
        val response = when (val result = postRaw(path, businessJson)) {
            is AuthResult.Failure -> return result
            is AuthResult.Success -> result.data
        }
        val data = JsonSupport.objectField(response, "data")
            ?: return AuthResult.Failure("登录响应缺少用户信息")
        val user = AuthUser(
            id = JsonSupport.long(data, "id") ?: 0L,
            token = JsonSupport.string(data, "token").orEmpty(),
            tokenExpire = JsonSupport.long(data, "tokenExpire") ?: 0L,
            userId = JsonSupport.string(data, "userId").orEmpty(),
            mobile = JsonSupport.string(data, "mobile").orEmpty(),
            email = JsonSupport.string(data, "email").orEmpty(),
            nickName = JsonSupport.string(data, "nickName").orEmpty(),
            authKey = JsonSupport.string(data, "authKey").orEmpty(),
            loginAppType = JsonSupport.int(data, "loginAppType") ?: 1,
            userType = JsonSupport.int(data, "userType") ?: 0,
            picUrl = JsonSupport.string(data, "picUrl").orEmpty(),
        )
        if (user.token.isBlank()) return AuthResult.Failure("登录响应缺少 token")
        return AuthResult.Success(user)
    }

    private suspend fun postRaw(path: String, businessJson: String): AuthResult<String> {
        val timestamp = timestampProvider()
        val body = GatewaySigner.formBody(businessJson, timestamp)
        val headers = GatewayHeaders.ensureGatewayAppId(baseUrl + path, commonHeaders())
        NetworkLog.write(
            "AUTH -> POST ${baseUrl + path} ts=$timestamp " +
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
        ) ?: run {
            NetworkLog.write("AUTH !! no response path=$path")
            return AuthResult.Failure("网络不可用")
        }
        NetworkLog.write(
            "AUTH <- ${response.status} path=$path raw=${NetworkLog.body(response.body)}",
        )
        val plain = decryptResponse(response) ?: run {
            NetworkLog.write("AUTH !! decrypt failed path=$path")
            return AuthResult.Failure("响应解密失败")
        }
        NetworkLog.write("AUTH plain path=$path ${NetworkLog.maskSensitive(plain)}")
        val success = JsonSupport.boolean(plain, "success") ?: false
        if (!success) {
            val message = JsonSupport.string(plain, "msg")
                ?: JsonSupport.string(plain, "message")
                ?: "请求失败"
            return AuthResult.Failure(message, JsonSupport.int(plain, "code"))
        }
        return AuthResult.Success(plain)
    }

    private fun commonHeaders(): Map<String, String> =
        GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
        )

    private fun decryptResponse(response: HttpResponse): String? {
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        NetworkLog.write("AUTH decoded ${NetworkLog.body(decoded)}")
        return Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
    }

    private fun sortedJson(fields: Map<String, String>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            val value = fields.getValue(key)
            if (index > 0) append(',')
            append('"').append(escape(key)).append("\":\"")
            append(escape(value)).append('"')
        }
        append('}')
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
