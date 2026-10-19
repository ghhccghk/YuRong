package top.ghhccghk.yadea.auth

import top.ghhccghk.yadea.network.JsonSupport

/**
 * 从已登录请求中的 `usr_token` / `Authorization` JWT 恢复本地用户资料。
 *
 * 原客户端 JWT payload 形如：
 * `{"userInfo":"{\"id\":...,\"mobile\":\"...\",\"nickName\":\"...\"}"}`。
 * 这条路径不需要再调用登录接口，也不依赖 `app-common/ping`。
 */
internal object JwtUserInfoDecoder {

    fun decode(token: String): AuthUser? {
        val normalized = normalize(token)
        val payload = payload(normalized) ?: return null
        val userJson = JsonSupport.string(payload, "userInfo")
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?: payload

        val id = JsonSupport.long(userJson, "id")
            ?: JsonSupport.string(userJson, "id")?.toLongOrNull()
            ?: 0L
        val userId = JsonSupport.string(userJson, "userId").orEmpty()
        val mobile = JsonSupport.string(userJson, "mobile").orEmpty()
        val email = JsonSupport.string(userJson, "email").orEmpty()
        val nickName = JsonSupport.string(userJson, "nickName").orEmpty()
        val avatar = JsonSupport.string(userJson, "picUrl")
            ?: JsonSupport.string(userJson, "avatar")
            ?: JsonSupport.string(userJson, "figureurlQq")
            ?: ""
        if (id == 0L && userId.isBlank() && mobile.isBlank() && email.isBlank() &&
            nickName.isBlank()
        ) {
            return null
        }

        val expiresAtSeconds = JsonSupport.long(payload, "exp")
            ?: JsonSupport.long(userJson, "exp")
        return AuthUser(
            id = id,
            token = normalized,
            tokenExpire = expiresAtSeconds?.times(1_000L)
                ?: JsonSupport.long(userJson, "tokenExpire")
                ?: 0L,
            userId = userId,
            mobile = mobile,
            email = email,
            nickName = nickName,
            authKey = JsonSupport.string(userJson, "authKey").orEmpty(),
            loginAppType = JsonSupport.int(userJson, "loginAppType") ?: 1,
            userType = JsonSupport.int(userJson, "userType") ?: 0,
            picUrl = avatar,
        )
    }

    fun payload(token: String): String? {
        val normalized = normalize(token)
        val parts = normalized.split('.')
        if (parts.size < 2) return null
        return decodeBase64Url(parts[1])
    }

    private fun normalize(token: String): String = token.trim()
        .removePrefix("Bearer ")
        .removePrefix("bearer ")
        .trim()

    private fun decodeBase64Url(value: String): String? {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        return Crypto.base64Decode(normalized)?.decodeToString()
    }
}
