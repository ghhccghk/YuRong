package top.ghhccghk.yadea.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.settings.AppPreferenceKeys
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.InMemoryPreferenceStore
import top.ghhccghk.yadea.settings.PreferenceKey
import top.ghhccghk.yadea.settings.PreferenceStore
import top.ghhccghk.yadea.settings.PreferenceWriteResult

class AuthControllerTest {
    @Test
    fun changedContact_reportsCleanupFailureAndReloadsStoredSession() = runBlocking {
        val store = FailingAuthDeleteStore()
        val preferences = AppPreferences(store)
        val user = AuthUser(id = 1, token = "token", mobile = "13800138000", nickName = "车主")
        assertTrue(preferences.writeAuth(user) is PreferenceWriteResult.Success)
        val controller = newController(preferences)
        try {
            controller.bindOrChangeMobile("13900139000", "1234", change = true)

            val state = awaitError(controller)
            assertFalse(state.busy)
            assertNull(state.notice)
            assertEquals("本地登录状态清理失败", state.error)
            assertFalse(state.session.isLoggedIn)
            assertNotNull(state.session.user)
            assertEquals("13800138000", state.session.user?.mobile)
        } finally {
            controller.dispose()
            controller.scope.cancel()
        }
    }

    @Test
    fun changedContact_reportsSuccessAfterCleanupSucceeds() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        val user = AuthUser(id = 1, token = "token", mobile = "13800138000", nickName = "车主")
        assertTrue(preferences.writeAuth(user) is PreferenceWriteResult.Success)
        val controller = newController(preferences)
        try {
            controller.bindOrChangeMobile("13900139000", "1234", change = true)

            val state = awaitNotice(controller)
            assertFalse(state.busy)
            assertNull(state.error)
            assertEquals("手机号已更换，请重新登录", state.notice)
            assertFalse(state.session.isLoggedIn)
            assertNull(state.session.user)
        } finally {
            controller.dispose()
            controller.scope.cancel()
        }
    }

    @Test
    fun logout_reportsCleanupFailureAndReloadsStoredSession() = runBlocking {
        val store = FailingAuthDeleteStore()
        val preferences = AppPreferences(store)
        val user = AuthUser(id = 1, token = "token", mobile = "13800138000", nickName = "车主")
        assertTrue(preferences.writeAuth(user) is PreferenceWriteResult.Success)
        val controller = newController(preferences)
        try {
            controller.logout()

            val state = awaitError(controller)
            assertFalse(state.busy)
            assertNull(state.notice)
            assertEquals("本地登录状态清理失败", state.error)
            assertFalse(state.session.isLoggedIn)
            assertNotNull(state.session.user)
            assertEquals("13800138000", state.session.user?.mobile)
        } finally {
            controller.dispose()
            controller.scope.cancel()
        }
    }

    private suspend fun awaitError(controller: TestAuthController): AuthUiState =
        withTimeout(5_000) {
            controller.state.first { !it.busy && it.error != null }
        }

    private suspend fun awaitNotice(controller: TestAuthController): AuthUiState =
        withTimeout(5_000) {
            controller.state.first { !it.busy && it.notice != null }
        }

    private fun newController(preferences: AppPreferences): TestAuthController {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val controller = AuthController(AuthSession(preferences), FakeAuthGateway(), scope)
        return TestAuthController(controller, scope)
    }
}

private data class TestAuthController(
    val controller: AuthController,
    val scope: CoroutineScope,
) {
    val state get() = controller.state
    fun bindOrChangeMobile(mobile: String, code: String, change: Boolean) =
        controller.bindOrChangeMobile(mobile, code, change)
    fun logout() = controller.logout()
    fun dispose() = controller.dispose()
}

private class FailingAuthDeleteStore : PreferenceStore {
    private val delegate = InMemoryPreferenceStore()
    private val values = MutableStateFlow<Map<String, Any>>(emptyMap())

    override fun <T : Any> read(key: PreferenceKey<T>): T = delegate.read(key)

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> =
        values.map { read(key) }.distinctUntilChanged()

    override suspend fun <T : Any> write(
        key: PreferenceKey<T>,
        value: T,
    ): PreferenceWriteResult = delegate.write(key, value).also {
        values.value = values.value + (key.name to value)
    }

    override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
        if (key == AppPreferenceKeys.usrInfo) {
            PreferenceWriteResult.Failure(IllegalStateException("本地登录状态清理失败"))
        } else {
            delegate.delete(key)
        }

    override suspend fun clear(): PreferenceWriteResult = delegate.clear()
}

private class FakeAuthGateway : AuthGateway {
    override suspend fun loginByPassword(request: PasswordLoginRequest): AuthResult<AuthUser> =
        AuthResult.Failure("unused")

    override suspend fun sendSmsCode(mobile: String): AuthResult<Unit> = AuthResult.Success(Unit)

    override suspend fun sendEmailCode(email: String, sendCodeType: Int): AuthResult<Unit> =
        AuthResult.Success(Unit)

    override suspend fun loginBySms(mobile: String, code: String): AuthResult<AuthUser> =
        AuthResult.Failure("unused")

    override suspend fun registerBySms(
        mobile: String,
        code: String,
        password: String,
    ): AuthResult<AuthUser> = AuthResult.Failure("unused")

    override suspend fun registerByEmail(
        email: String,
        code: String,
        password: String,
    ): AuthResult<AuthUser> = AuthResult.Failure("unused")

    override suspend fun updatePasswordByCode(
        account: String,
        code: String,
        password: String,
        accountType: AuthAccountType,
    ): AuthResult<Unit> = AuthResult.Failure("unused")

    override suspend fun bindOrChangeMobile(
        mobile: String,
        code: String,
        type: Int,
    ): AuthResult<Unit> = AuthResult.Success(Unit)

    override suspend fun bindOrChangeEmail(
        email: String,
        code: String,
        type: Int,
    ): AuthResult<Unit> = AuthResult.Success(Unit)

    override suspend fun loginByToken(request: TokenLoginRequest): AuthResult<AuthUser> =
        AuthResult.Failure("unused")

    override suspend fun importBearerToken(token: String): AuthResult<AuthUser> =
        AuthResult.Failure("unused")

    override suspend fun loginWithParameters(request: RawLoginRequest): AuthResult<AuthUser> =
        AuthResult.Failure("unused")
}
