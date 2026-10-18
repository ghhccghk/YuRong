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

    fun sendSmsCode(mobile: String) {
        val trimmed = mobile.trim()
        if (!AuthValidation.isMobile(trimmed)) {
            _state.value = _state.value.copy(error = "请输入正确的手机号")
            return
        }
        val gateway = gateway ?: return unavailable()
        if (_state.value.smsCountdown > 0) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            when (val result = runCatching { gateway.sendSmsCode(trimmed) }
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

    private suspend fun saveUser(user: AuthUser) {
        when (val result = session.save(user)) {
            PreferenceWriteResult.Success -> _state.value = _state.value.copy(
                session = session.read(),
                busy = false,
                error = null,
                notice = "登录成功",
            )
            is PreferenceWriteResult.Failure -> _state.value = _state.value.copy(
                busy = false,
                error = result.cause?.message ?: "登录状态保存失败",
            )
        }
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
}
