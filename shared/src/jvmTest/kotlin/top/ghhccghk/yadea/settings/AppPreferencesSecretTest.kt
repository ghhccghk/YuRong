package top.ghhccghk.yadea.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.auth.AuthUser

class AppPreferencesSecretTest {

    @Test
    fun writeAuthStoresTokenOnlyInSecretStore() = runBlocking {
        val store = InMemoryPreferenceStore()
        val secret = FakeSecretStore()
        val preferences = AppPreferences(store, secret)

        val result = preferences.writeAuth(AuthUser(id = 7, token = "secret-token", mobile = "13800000000"))

        assertEquals(PreferenceWriteResult.Success, result)
        assertEquals("secret-token", secret.values[AppSecretKeys.userToken])
        assertTrue(store.read(AppPreferenceKeys.usrToken).isEmpty())
        assertTrue(!store.read(AppPreferenceKeys.usrInfo).contains("secret-token"))
        assertEquals("secret-token", preferences.readAuth().token)
        assertEquals("secret-token", preferences.readAuth().user?.token)
    }

    @Test
    fun readAuthFallsBackToLegacyPlainTokenUntilMigration() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(AppPreferenceKeys.usrToken, "legacy-token")
        val preferences = AppPreferences(store, FakeSecretStore())

        assertEquals("legacy-token", preferences.readAuth().token)
    }

    @Test
    fun clearAuthRemovesSecureAndLegacyToken() = runBlocking {
        val store = InMemoryPreferenceStore()
        val secret = FakeSecretStore()
        store.write(AppPreferenceKeys.usrToken, "legacy-token")
        secret.values[AppSecretKeys.userToken] = "secure-token"
        val preferences = AppPreferences(store, secret)

        assertEquals(PreferenceWriteResult.Success, preferences.clearAuth())

        assertTrue(store.read(AppPreferenceKeys.usrToken).isEmpty())
        assertTrue(secret.values[AppSecretKeys.userToken] == null)
        assertTrue(preferences.readAuth().token.isEmpty())
    }

    @Test
    fun migrationRemovesTokenFromLegacyProfile() = runBlocking {
        val store = InMemoryPreferenceStore()
        val secret = FakeSecretStore()
        store.write(
            AppPreferenceKeys.usrInfo,
            """{"id":1,"token":"legacy-token","mobile":"13800000000"}""",
        )
        secret.values[AppSecretKeys.userToken] = "legacy-token"
        val preferences = AppPreferences(store, secret)

        assertEquals(PreferenceWriteResult.Success, preferences.migrateLegacyAuthProfile())

        assertTrue(!store.read(AppPreferenceKeys.usrInfo).contains("legacy-token"))
        assertEquals("legacy-token", preferences.readAuth().user?.token)
    }
}

private class FakeSecretStore : SecretStore {
    val values = mutableMapOf<String, String>()

    override fun read(name: String): String? = values[name]

    override fun write(name: String, value: String) {
        values[name] = value
    }

    override fun delete(name: String) {
        values.remove(name)
    }
}
