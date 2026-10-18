package top.ghhccghk.yadea.auth

/**
 * 登录用户快照。
 *
 * 字段名与原实现 `UserInfoBean` 的 JSON 名保持一致；这里只保留 UI 与会话
 * 需要的最小集合，其余资料字段可按需扩展。
 */
data class AuthUser(
    val id: Long = 0L,
    val token: String = "",
    val tokenExpire: Long = 0L,
    val userId: String = "",
    val mobile: String = "",
    val email: String = "",
    val nickName: String = "",
    val authKey: String = "",
    val loginAppType: Int = 1,
    val userType: Int = 0,
    val picUrl: String = "",
) {
    val displayName: String
        get() = nickName.ifBlank { mobile.ifBlank { email.ifBlank { "雅迪用户" } } }

    val accountLabel: String
        get() = mobile.ifBlank { email.ifBlank { userId } }
}

/** 本地会话快照。`token` 为空或用户资料损坏时视为未登录。 */
data class AuthSessionSnapshot(
    val token: String = "",
    val user: AuthUser? = null,
    val loginId: String = "",
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && user != null
}

/** 密码登录的账号类型。具体数值仍需服务端环境确认。 */
enum class AuthAccountType(val value: Int) {
    Mobile(1),
    Email(2),
}

data class PasswordLoginRequest(
    val account: String,
    val password: String,
    val accountType: AuthAccountType,
)

sealed interface AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>
    data class Failure(val message: String, val code: Int? = null) : AuthResult<Nothing>
}

/**
 * 登录网络层抽象。
 *
 * commonMain 不直接依赖 Ktor；Android / Desktop / Web 只需提供实现并在入口注入。
 * 未注入实现时，登录页会明确显示“登录服务未配置”。
 */
interface AuthGateway {
    suspend fun loginByPassword(request: PasswordLoginRequest): AuthResult<AuthUser>
    suspend fun sendSmsCode(mobile: String): AuthResult<Unit>
    suspend fun loginBySms(mobile: String, code: String): AuthResult<AuthUser>
}

/** `usr_info` 的扁平 JSON 编解码，避免为几个字段引入序列化依赖。 */
internal object AuthProfileCodec {
    fun encode(user: AuthUser): String = buildString {
        append('{')
        appendField("id", user.id)
        append(',')
        appendField("token", user.token)
        append(',')
        appendField("tokenExpire", user.tokenExpire)
        append(',')
        appendField("userId", user.userId)
        append(',')
        appendField("mobile", user.mobile)
        append(',')
        appendField("email", user.email)
        append(',')
        appendField("nickName", user.nickName)
        append(',')
        appendField("authKey", user.authKey)
        append(',')
        appendField("loginAppType", user.loginAppType)
        append(',')
        appendField("userType", user.userType)
        append(',')
        appendField("picUrl", user.picUrl)
        append('}')
    }

    fun decode(raw: String): AuthUser? {
        if (raw.isBlank()) return null
        val fields = FlatJsonReader(raw).readObject() ?: return null
        return AuthUser(
            id = fields["id"]?.toLongOrNull() ?: 0L,
            token = fields["token"].orEmpty(),
            tokenExpire = fields["tokenExpire"]?.toLongOrNull() ?: 0L,
            userId = fields["userId"].orEmpty(),
            mobile = fields["mobile"].orEmpty(),
            email = fields["email"].orEmpty(),
            nickName = fields["nickName"].orEmpty(),
            authKey = fields["authKey"].orEmpty(),
            loginAppType = fields["loginAppType"]?.toIntOrNull() ?: 1,
            userType = fields["userType"]?.toIntOrNull() ?: 0,
            picUrl = fields["picUrl"].orEmpty(),
        )
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append('"').append(name).append("\":\"")
        appendEscaped(value)
        append('"')
    }

    private fun StringBuilder.appendField(name: String, value: Long) {
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.appendField(name: String, value: Int) {
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.appendEscaped(value: String) {
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

    /** 只解析本项目写出的扁平 JSON 对象。 */
    private class FlatJsonReader(private val raw: String) {
        private var index = 0

        fun readObject(): Map<String, String>? {
            skipSpace()
            if (!consume('{')) return null
            val result = linkedMapOf<String, String>()
            while (true) {
                skipSpace()
                if (consume('}')) return result
                val key = readString() ?: return null
                skipSpace()
                if (!consume(':')) return null
                skipSpace()
                val value = if (peek() == '"') readString() else readScalar()
                if (value == null) return null
                result[key] = value
                skipSpace()
                when {
                    consume(',') -> Unit
                    consume('}') -> return result
                    else -> return null
                }
            }
        }

        private fun readString(): String? {
            skipSpace()
            if (!consume('"')) return null
            val out = StringBuilder()
            while (index < raw.length) {
                val c = raw[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= raw.length) return null
                        when (val escaped = raw[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > raw.length) return null
                                val code = raw.substring(index, index + 4).toIntOrNull(16) ?: return null
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> return null
                        }
                    }
                    else -> out.append(c)
                }
            }
            return null
        }

        private fun readScalar(): String? {
            val start = index
            while (index < raw.length && raw[index] != ',' && raw[index] != '}') index++
            return raw.substring(start, index).trim().takeIf { it.isNotEmpty() }
        }

        private fun skipSpace() {
            while (index < raw.length && raw[index].isWhitespace()) index++
        }

        private fun peek(): Char? = raw.getOrNull(index)

        private fun consume(expected: Char): Boolean {
            if (index >= raw.length || raw[index] != expected) return false
            index++
            return true
        }
    }
}
