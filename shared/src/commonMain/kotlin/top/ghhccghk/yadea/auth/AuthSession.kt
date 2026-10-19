package top.ghhccghk.yadea.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.PreferenceWriteResult

/** 基于 `AppPreferenceKeys` 的登录态持久化。 */
class AuthSession(private val preferences: AppPreferences) {
    fun read(): AuthSessionSnapshot = preferences.readAuth()

    suspend fun save(user: AuthUser): PreferenceWriteResult = preferences.writeAuth(user)

    suspend fun clear(): PreferenceWriteResult = preferences.clearAuth()
}

data class AuthUiState(
    val session: AuthSessionSnapshot = AuthSessionSnapshot(),
    val available: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val smsCountdown: Int = 0,
) {
    val canSendCode: Boolean
        get() = available && !busy && smsCountdown <= 0
}

/**
 * 登录页状态与副作用。
 *
 * UI 只调用这里的方法；网络失败、校验失败与本地写入失败都收敛到 [AuthUiState]。
 */
class AuthController(
    private val session: AuthSession,
    private val gateway: AuthGateway?,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        AuthUiState(
            session = session.read(),
            available = gateway != null,
            notice = if (gateway == null) "登录服务未配置，请注入 AuthGateway" else null,
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    fun loginByPassword(account: String, password: String) {
        val validation = AuthValidation.validatePasswordLogin(account.trim(), password)
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        val type = AuthValidation.accountTypeOf(account.trim()) ?: return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                gateway.loginByPassword(
                    PasswordLoginRequest(
                        account = account.trim(),
                        password = password,
                        accountType = type,
                    ),
                )
            }.getOrElse { AuthResult.Failure(it.message ?: "登录失败") }) {
                is AuthResult.Success -> saveUser(result.data)
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun sendSmsCode(mobile: String, sendCodeType: Int = 1) {
        val trimmed = mobile.trim()
        if (!AuthValidation.isMobile(trimmed)) {
            _state.value = _state.value.copy(error = "请输入正确的手机号")
            return
        }
        val gateway = gateway ?: return unavailable()
        if (_state.value.smsCountdown > 0) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching { gateway.sendSmsCode(trimmed, sendCodeType) }
                .getOrElse { AuthResult.Failure(it.message ?: "验证码发送失败") }) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = "验证码已发送",
                        smsCountdown = 60,
                    )
                    startCountdown()
                }
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun sendEmailCode(email: String, sendCodeType: Int = 1) {
        val trimmed = email.trim()
        if (!AuthValidation.isEmail(trimmed)) {
            _state.value = _state.value.copy(error = "请输入正确的邮箱")
            return
        }
        val gateway = gateway ?: return unavailable()
        if (_state.value.smsCountdown > 0) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching { gateway.sendEmailCode(trimmed, sendCodeType) }
                .getOrElse { AuthResult.Failure(it.message ?: "验证码发送失败") }) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = "邮箱验证码已发送",
                        smsCountdown = 60,
                    )
                    startCountdown()
                }
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun loginBySms(mobile: String, code: String) {
        val trimmedMobile = mobile.trim()
        val validation = AuthValidation.validateSmsLogin(trimmedMobile, code.trim())
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching { gateway.loginBySms(trimmedMobile, code.trim()) }
                .getOrElse { AuthResult.Failure(it.message ?: "登录失败") }) {
                is AuthResult.Success -> saveUser(result.data)
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun loginByToken(token: String, gyuid: String = "") {
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            _state.value = _state.value.copy(error = "请输入 Token")
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                if (trimmedToken.startsWith("Bearer ", ignoreCase = true)) {
                    gateway.importBearerToken(trimmedToken)
                } else {
                    gateway.loginByToken(
                        TokenLoginRequest(
                            token = trimmedToken,
                            gyuid = gyuid.trim(),
                        ),
                    )
                }
            }.getOrElse { AuthResult.Failure(it.message ?: "Token 登录失败") }) {
                is AuthResult.Success -> saveUser(result.data)
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun loginWithParameters(path: String, businessJson: String, headersText: String) {
        val trimmedPath = path.trim()
        val trimmedJson = businessJson.trim()
        if (trimmedPath.isBlank()) {
            _state.value = _state.value.copy(error = "请输入登录接口路径")
            return
        }
        if (trimmedJson.isBlank()) {
            _state.value = _state.value.copy(error = "请输入签名前的业务 JSON")
            return
        }
        val (headers, headerError) = parseHeaderLines(headersText)
        if (headerError != null) {
            _state.value = _state.value.copy(error = headerError)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                gateway.loginWithParameters(
                    RawLoginRequest(
                        path = trimmedPath,
                        businessJson = trimmedJson,
                        headers = headers,
                    ),
                )
            }.getOrElse { AuthResult.Failure(it.message ?: "参数登录失败") }) {
                is AuthResult.Success -> saveUser(result.data)
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun registerBySms(mobile: String, code: String, password: String) {
        val trimmedMobile = mobile.trim()
        val validation = AuthValidation.validateSmsLogin(trimmedMobile, code.trim())
            ?: AuthValidation.validatePasswordLogin(trimmedMobile, password)
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                gateway.registerBySms(trimmedMobile, code.trim(), password)
            }.getOrElse { AuthResult.Failure(it.message ?: "注册失败") }) {
                is AuthResult.Success -> saveUser(result.data, "注册成功")
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun registerByEmail(email: String, code: String, password: String) {
        val trimmedEmail = email.trim()
        val validation = when {
            !AuthValidation.isEmail(trimmedEmail) -> "请输入正确的邮箱"
            !AuthValidation.isSmsCode(code.trim()) -> "验证码必须为 4 位数字"
            !AuthValidation.isPassword(password) -> "密码需为 6-18 位，且只能包含允许的字符"
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                gateway.registerByEmail(trimmedEmail, code.trim(), password)
            }.getOrElse { AuthResult.Failure(it.message ?: "注册失败") }) {
                is AuthResult.Success -> saveUser(result.data, "注册成功")
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun changePassword(account: String, code: String, password: String) {
        val trimmedAccount = account.trim()
        val accountType = AuthValidation.accountTypeOf(trimmedAccount)
            ?: run {
                _state.value = _state.value.copy(error = "请输入正确的手机号或邮箱")
                return
            }
        val validation = when {
            !AuthValidation.isSmsCode(code.trim()) -> "验证码必须为 4 位数字"
            !AuthValidation.isPassword(password) -> "密码需为 6-18 位，且只能包含允许的字符"
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                gateway.updatePasswordByCode(
                    account = trimmedAccount,
                    code = code.trim(),
                    password = password,
                    accountType = accountType,
                )
            }.getOrElse { AuthResult.Failure(it.message ?: "密码更新失败") }) {
                is AuthResult.Success -> _state.value = _state.value.copy(
                    busy = false,
                    error = null,
                    notice = "密码已更新",
                )
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun bindOrChangeMobile(mobile: String, code: String, change: Boolean) {
        val trimmed = mobile.trim()
        val validation = when {
            !AuthValidation.isMobile(trimmed) -> "请输入正确的手机号"
            !AuthValidation.isSmsCode(code.trim()) -> "验证码必须为 4 位数字"
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                // smali: BindTypeActivity.updatePhoneBySmsCode
                // 换绑 -> type=1；首次绑定 -> type=2。
                gateway.bindOrChangeMobile(trimmed, code.trim(), if (change) 1 else 2)
            }.getOrElse { AuthResult.Failure(it.message ?: "手机号更新失败") }) {
                is AuthResult.Success -> applyContactChange(
                    mobile = trimmed,
                    change = change,
                    successMessage = if (change) "手机号已更换，请重新登录" else "手机号已绑定",
                )
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun bindOrChangeEmail(email: String, code: String, change: Boolean) {
        val trimmed = email.trim()
        val validation = when {
            !AuthValidation.isEmail(trimmed) -> "请输入正确的邮箱"
            !AuthValidation.isSmsCode(code.trim()) -> "验证码必须为 4 位数字"
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(error = validation)
            return
        }
        val gateway = gateway ?: return unavailable()
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching {
                // smali: BindTypeActivity.getUpdateEmail
                // 换绑 -> type=1；首次绑定 -> type=2。
                gateway.bindOrChangeEmail(trimmed, code.trim(), if (change) 1 else 2)
            }.getOrElse { AuthResult.Failure(it.message ?: "邮箱更新失败") }) {
                is AuthResult.Success -> applyContactChange(
                    email = trimmed,
                    change = change,
                    successMessage = if (change) "邮箱已更换，请重新登录" else "邮箱已绑定",
                )
                is AuthResult.Failure -> _state.value = _state.value.copy(
                    busy = false,
                    error = result.message,
                )
            }
        }
    }

    fun logout() {
        countdownJob?.cancel()
        scope.launch {
            session.clear()
            _state.value = _state.value.copy(
                session = session.read(),
                busy = false,
                error = null,
                notice = "已退出登录",
                smsCountdown = 0,
            )
        }
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(error = null, notice = null)
    }

    fun dispose() {
        countdownJob?.cancel()
    }

    private suspend fun saveUser(user: AuthUser, notice: String = "登录成功") {
        when (val result = session.save(user)) {
            PreferenceWriteResult.Success -> _state.value = _state.value.copy(
                session = session.read(),
                busy = false,
                error = null,
                notice = notice,
            )
            is PreferenceWriteResult.Failure -> _state.value = _state.value.copy(
                busy = false,
                error = result.cause?.message ?: "登录状态保存失败",
            )
        }
    }

    private suspend fun applyContactChange(
        mobile: String? = null,
        email: String? = null,
        change: Boolean,
        successMessage: String,
    ) {
        if (change) {
            session.clear()
        } else {
            session.read().user?.let { current ->
                session.save(
                    current.copy(
                        mobile = mobile ?: current.mobile,
                        email = email ?: current.email,
                    ),
                )
            }
        }
        _state.value = _state.value.copy(
            session = session.read(),
            busy = false,
            error = null,
            notice = successMessage,
        )
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (remaining in 59 downTo 0) {
                delay(1_000)
                _state.value = _state.value.copy(smsCountdown = remaining)
            }
        }
    }

    private fun unavailable() {
        _state.value = _state.value.copy(
            busy = false,
            error = "登录服务未配置，请稍后再试",
        )
    }

    private fun parseHeaderLines(text: String): Pair<Map<String, String>, String?> {
        val result = linkedMapOf<String, String>()
        for ((index, rawLine) in text.lineSequence().withIndex()) {
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) continue
            val separator = line.indexOf('=')
            if (separator <= 0) {
                return emptyMap<String, String>() to
                    "请求头第 ${index + 1} 行格式应为 key=value"
            }
            val key = line.substring(0, separator).trim()
            if (key.isBlank()) {
                return emptyMap<String, String>() to
                    "请求头第 ${index + 1} 行缺少名称"
            }
            result[key] = line.substring(separator + 1).trim()
        }
        return result to null
    }
}
