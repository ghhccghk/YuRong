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
    private val sessionTokenProvider: () -> String = { "" },
) : AuthGateway {

    private var timestampOffsetMs: Long = 0L

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
        return sendSmsCode(mobile, sendCodeType = 1)
    }

    override suspend fun sendSmsCode(mobile: String, sendCodeType: Int): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appType" to "1",
                "mobile" to mobile,
                "registrationId" to registrationIdProvider(),
                "sendCode" to sendCodeType.toString(),
            ),
        )
        return postUnit("user/sendCode", json)
    }

    override suspend fun sendEmailCode(email: String, sendCodeType: Int): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appType" to "1",
                "email" to email,
                "registrationId" to registrationIdProvider(),
                "sendCode" to sendCodeType.toString(),
            ),
        )
        return postUnit("user/sendCodeByEmail", json)
    }

    override suspend fun registerBySms(
        mobile: String,
        code: String,
        password: String,
    ): AuthResult<AuthUser> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "code" to code,
                "loginAppType" to "1",
                "mobile" to mobile,
                "oppoPushId" to oppoPushIdProvider(),
                "password" to password,
                "registrationId" to registrationIdProvider(),
            ),
        )
        return post("user/loginBySms", json)
    }

    override suspend fun registerByEmail(
        email: String,
        code: String,
        password: String,
    ): AuthResult<AuthUser> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appType" to "1",
                "code" to code,
                "email" to email,
                "password" to password,
                "registrationId" to registrationIdProvider(),
            ),
        )
        return post("user/registerByEmail", json)
    }

    override suspend fun updatePasswordByCode(
        account: String,
        code: String,
        password: String,
        accountType: AuthAccountType,
    ): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "account" to account,
                "accountType" to accountType.value.toString(),
                "appType" to "1",
                "code" to code,
                "password" to password,
            ),
        )
        return postUnit("user/updatePasswordByCode", json)
    }

    override suspend fun bindOrChangeMobile(
        mobile: String,
        code: String,
        type: Int,
    ): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appType" to "1",
                "code" to code,
                "mobile" to mobile,
                "type" to type.toString(),
            ),
        )
        return postUnit(
            path = "user/bindOrChangeMobile",
            businessJson = json,
            authToken = sessionTokenProvider(),
        )
    }

    override suspend fun bindOrChangeEmail(
        email: String,
        code: String,
        type: Int,
    ): AuthResult<Unit> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appType" to "1",
                "code" to code,
                "email" to email,
                "type" to type.toString(),
            ),
        )
        return postUnit(
            path = "user/bindOrChangeEmail",
            businessJson = json,
            authToken = sessionTokenProvider(),
        )
    }

    private suspend fun postUnit(
        path: String,
        businessJson: String,
        authToken: String = "",
    ): AuthResult<Unit> {
        return when (val result = postRaw(path, businessJson, authToken)) {
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

    override suspend fun loginByToken(request: TokenLoginRequest): AuthResult<AuthUser> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "appName" to "YADEA",
                "gyuid" to request.gyuid,
                "loginToken" to request.token,
                "oppoPushId" to oppoPushIdProvider(),
                "registrationId" to registrationIdProvider(),
                "verificationType" to "1",
            ),
        )
        return post("user/loginByToken", json)
    }

    override suspend fun importBearerToken(token: String): AuthResult<AuthUser> {
        val normalized = normalizeCapturedToken(token)
        if (normalized.isBlank()) return AuthResult.Failure("请输入 Token")

        // 抓包导出的 usr_token/Authorization 是 JWT，用户资料已在 payload.userInfo 内。
        // 优先本地恢复会话，避免依赖额外 ping 接口或再次联网登录。
        JwtUserInfoDecoder.decode(normalized)?.let { return AuthResult.Success(it) }
        if (normalized.count { it == '.' } >= 2 && JwtUserInfoDecoder.payload(normalized) == null) {
            return AuthResult.Failure("JWT 不完整或格式错误，请粘贴完整的 usr_token / Authorization")
        }

        val result = postRaw(
            path = "app-common/ping",
            businessJson = sortedJson(mapOf("_gateway" to "True")),
            authToken = normalized,
        )
        return when (result) {
            is AuthResult.Failure -> result
            is AuthResult.Success -> AuthResult.Success(
                AuthUser(
                    token = normalized,
                    nickName = "Token 用户",
                ),
            )
        }
    }

    private fun normalizeCapturedToken(raw: String): String {
        var value = raw.trim()
        listOf("authorization:", "authorization=", "usr_token=", "token=").forEach { prefix ->
            if (value.startsWith(prefix, ignoreCase = true)) {
                value = value.substring(prefix.length).trim()
            }
        }
        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substring(7).trim()
        }
        return value
    }

    override suspend fun loginWithParameters(request: RawLoginRequest): AuthResult<AuthUser> {
        val path = request.path.trim().trimStart('/')
        if (path.isBlank() ||
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            path.contains("..")
        ) {
            return AuthResult.Failure("登录路径不合法")
        }
        val json = ensureGatewayBusinessJson(request.businessJson)
            ?: return AuthResult.Failure(
                "业务 JSON 格式不正确；请填写签名前的 JSON，而不是 gatewaySign 表单",
            )
        val response = when (
            val result = postRaw(
                path = path,
                businessJson = json,
                headerOverrides = request.headers,
            )
        ) {
            is AuthResult.Failure -> return result
            is AuthResult.Success -> result.data
        }
        return parseUser(response)
    }

    private suspend fun post(path: String, businessJson: String): AuthResult<AuthUser> {
        val response = when (val result = postRaw(path, businessJson)) {
            is AuthResult.Failure -> return result
            is AuthResult.Success -> result.data
        }
        return parseUser(response)
    }

    private fun parseUser(response: String): AuthResult<AuthUser> {
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

    private suspend fun postRaw(
        path: String,
        businessJson: String,
        authToken: String = "",
        headerOverrides: Map<String, String> = emptyMap(),
    ): AuthResult<String> {
        val timestamp = effectiveTimestamp()
        val body = GatewaySigner.formBody(businessJson, timestamp)
        val headers = GatewayHeaders.ensureGatewayAppId(
            baseUrl + path,
            commonHeaders(authToken) + headerOverrides,
        )
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
        val businessCode = JsonSupport.int(plain, "code")
            ?: JsonSupport.string(plain, "code")?.toIntOrNull()
        when (businessCode) {
            CODE_TIME_INVALID -> {
                val serverTimestamp = JsonSupport.long(plain, "timestamp")
                    ?: JsonSupport.string(plain, "timestamp")?.toLongOrNull()
                if (serverTimestamp != null && serverTimestamp > 0L) {
                    timestampOffsetMs = serverTimestamp - timestampProvider()
                }
                return AuthResult.Failure("服务器时间已校正，请重试", businessCode)
            }
            CODE_TOKEN_INVALID,
            CODE_TOKEN_EXPIRED,
            -> return AuthResult.Failure("登录已失效，请重新登录", businessCode)
        }
        val success = JsonSupport.boolean(plain, "success") ?: false
        if (!success) {
            val message = JsonSupport.string(plain, "msg")
                ?: JsonSupport.string(plain, "message")
                ?: "请求失败"
            return AuthResult.Failure(message, JsonSupport.int(plain, "code"))
        }
        return AuthResult.Success(plain)
    }

    private fun ensureGatewayBusinessJson(raw: String): String? {
        val text = raw.trim()
        if (!text.startsWith("{") || !text.endsWith("}")) return null
        val compact = compactJson(text)
        if (Regex("\"_gateway\"\\s*:").containsMatchIn(compact)) return compact
        val body = compact.removePrefix("{").removeSuffix("}")
        return if (body.isBlank()) {
            """{"_gateway":"True"}"""
        } else {
            compactJson("""{"_gateway":"True",$body}""")
        }
    }

    /** 去掉 JSON 字符串外的空白，避免粘贴的多行参数影响签名稳定性。 */
    private fun compactJson(text: String): String = buildString(text.length) {
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                append(c)
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when {
                    c == '"' -> {
                        inString = true
                        append(c)
                    }
                    c.isWhitespace() -> Unit
                    else -> append(c)
                }
            }
        }
    }

    private fun commonHeaders(authToken: String = ""): Map<String, String> =
        GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = authToken,
        )

    private fun effectiveTimestamp(): Long = timestampProvider() + timestampOffsetMs

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
        const val CODE_TIME_INVALID = 405
        const val CODE_TOKEN_INVALID = 101000
        const val CODE_TOKEN_EXPIRED = 100995
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
