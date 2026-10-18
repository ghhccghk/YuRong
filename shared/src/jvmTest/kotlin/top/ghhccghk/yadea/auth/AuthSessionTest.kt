package top.ghhccghk.yadea.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ghhccghk.yadea.settings.AppPreferenceKeys
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.InMemoryPreferenceStore
import top.ghhccghk.yadea.settings.PreferenceWriteResult
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.UiThemeStyle

class AuthSessionTest {
    @Test
    fun profileCodec_roundTripsEscapedFields() {
        val user = AuthUser(
            id = 42L,
            token = "token-123",
            tokenExpire = 1_700_000_000_000L,
            userId = "u-1",
            mobile = "13800138000",
            email = "a@b.com",
            nickName = "昵称 \"Y\"\n第二行",
            authKey = "abc",
            loginAppType = 1,
            userType = 2,
            picUrl = "https://example.com/a.png",
        )
        val encoded = AuthProfileCodec.encode(user)
        assertEquals(user, AuthProfileCodec.decode(encoded))
    }

    @Test
    fun damagedProfile_fallsBackToLoggedOut() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        store.write(AppPreferenceKeys.usrToken, "token")
        store.write(AppPreferenceKeys.usrInfo, "{broken")

        val snapshot = preferences.readAuth()
        assertEquals("token", snapshot.token)
        assertNull(snapshot.user)
        assertFalse(snapshot.isLoggedIn)
    }

    @Test
    fun saveAndClearAuth_preservesAppearance() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        assertTrue(
            preferences.write(
                AppAppearance(uiStyle = UiThemeStyle.Material3),
            ) is PreferenceWriteResult.Success,
        )
        val user = AuthUser(
            id = 1,
            token = "t",
            userId = "u",
            mobile = "13800138000",
            nickName = "车主",
        )
        assertTrue(preferences.writeAuth(user) is PreferenceWriteResult.Success)

        val session = AuthSession(preferences).read()
        assertTrue(session.isLoggedIn)
        assertNotNull(session.user)
        assertEquals("车主", session.user?.displayName)
        assertTrue(session.loginId.startsWith("device-"))

        assertTrue(preferences.clearAuth() is PreferenceWriteResult.Success)
        assertFalse(preferences.readAuth().isLoggedIn)
        assertEquals(UiThemeStyle.Material3, preferences.read().uiStyle)
    }
}
