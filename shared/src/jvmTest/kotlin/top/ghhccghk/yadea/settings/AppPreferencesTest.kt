package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppColorSource
import top.ghhccghk.yadea.ui.AppPaletteStyle
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle

class AppPreferencesTest {
    @Test
    fun defaultsAreStable() {
        val preferences = AppPreferences(InMemoryPreferenceStore())

        val appearance = preferences.read()

        assertEquals(UiThemeStyle.Miuix, appearance.uiStyle)
        assertEquals(AppThemeMode.System, appearance.themeMode)
        assertEquals(AppColorSource.SeedColor, appearance.colorSource)
        assertEquals(0xFF7B4DFF.toInt(), appearance.seedColorArgb)
        assertEquals("default", appearance.presetId)
        assertEquals(AppPaletteStyle.TonalSpot, appearance.paletteStyle)
        assertEquals(false, appearance.amoledDarkEnabled)
        assertEquals(true, appearance.themeAnimationsEnabled)
    }

    @Test
    fun appearanceRoundTripsThroughStore() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)
        val expected = AppAppearance(
            uiStyle = UiThemeStyle.Material3,
            themeMode = AppThemeMode.Dark,
            colorSource = AppColorSource.Preset,
            seedColorArgb = 0xFF123456.toInt(),
            presetId = "midnight",
            paletteStyle = AppPaletteStyle.Vibrant,
            amoledDarkEnabled = true,
            themeAnimationsEnabled = false,
        )

        assertTrue(preferences.write(expected) is PreferenceWriteResult.Success)
        assertEquals(expected, preferences.read())
        assertEquals(expected, preferences.observe().first())
    }

    @Test
    fun unknownValuesFallBackToSafeDefaults() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(AppPreferenceKeys.uiStyle, "unknown-style")
        store.write(AppPreferenceKeys.themeMode, "unknown-mode")
        store.write(AppPreferenceKeys.colorSource, "unknown-source")
        store.write(AppPreferenceKeys.paletteStyle, "unknown-palette")
        store.write(AppPreferenceKeys.presetId, "")

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Miuix, appearance.uiStyle)
        assertEquals(AppThemeMode.System, appearance.themeMode)
        assertEquals(AppColorSource.SeedColor, appearance.colorSource)
        assertEquals(AppPaletteStyle.TonalSpot, appearance.paletteStyle)
        assertEquals("default", appearance.presetId)
    }

    @Test
    fun resetRestoresDefaults() = runBlocking {
        val preferences = AppPreferences(InMemoryPreferenceStore())
        preferences.write(
            AppAppearance(
                uiStyle = UiThemeStyle.Material3,
                themeMode = AppThemeMode.Dark,
                colorSource = AppColorSource.SeedColor,
                seedColorArgb = 1,
                presetId = "custom",
                paletteStyle = AppPaletteStyle.Monochrome,
                amoledDarkEnabled = true,
                themeAnimationsEnabled = false,
            ),
        )

        assertTrue(preferences.reset() is PreferenceWriteResult.Success)
        assertEquals(AppAppearance(), preferences.read())
    }
}
