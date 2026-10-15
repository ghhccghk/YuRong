package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppColorSource
import top.ghhccghk.yadea.ui.AppColorSpec
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
        assertEquals(true, appearance.miuixMonetEnabled)
        assertEquals(false, appearance.floatingNavigationBarEnabled)
        assertEquals(AppColorSpec.Auto, appearance.colorSpec)
        assertEquals(1f, appearance.uiScale)
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
            miuixMonetEnabled = false,
            floatingNavigationBarEnabled = true,
            colorSpec = AppColorSpec.Spec2025,
            uiScale = 1.2f,
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
                miuixMonetEnabled = false,
            ),
        )

        assertTrue(preferences.reset() is PreferenceWriteResult.Success)
        assertEquals(AppAppearance(), preferences.read())
    }
@Test
    fun invalidSchemaVersionDoesNotCrash() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(AppPreferenceKeys.schemaVersion, 0)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Material3.storageName)

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Material3, appearance.uiStyle)
    }

    @Test
    fun writePersistsCurrentSchemaVersion() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(preferences.write(AppAppearance()) is PreferenceWriteResult.Success)

        assertEquals(AppPreferenceKeys.SCHEMA_VERSION, store.read(AppPreferenceKeys.schemaVersion))
    }

    @Test
    fun legacySchemaKeepsUserDataAndDefaultsMonetOn() = runBlocking {
        val store = InMemoryPreferenceStore()
        // 模拟 schema 1 的旧配置：只写入当时存在的键，没有 miuix_monet。
        store.write(AppPreferenceKeys.schemaVersion, 1)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Material3.storageName)
        store.write(AppPreferenceKeys.themeMode, AppThemeMode.Dark.name)

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Material3, appearance.uiStyle)
        assertEquals(AppThemeMode.Dark, appearance.themeMode)
        assertTrue(appearance.miuixMonetEnabled)
    }

    @Test
    fun legacySchemaKeepsUserDataAndDefaultsFloatingNavigationOff() = runBlocking {
        val store = InMemoryPreferenceStore()
        // 模拟 schema 2 的旧配置：只写入当时存在的键，没有 floating_navigation_bar。
        store.write(AppPreferenceKeys.schemaVersion, 2)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Miuix.storageName)
        store.write(AppPreferenceKeys.miuixMonet, false)

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Miuix, appearance.uiStyle)
        assertEquals(false, appearance.miuixMonetEnabled)
        assertEquals(false, appearance.floatingNavigationBarEnabled)
    }

    @Test
    fun floatingNavigationBarToggleRoundTripsThroughStore() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(
            preferences.write(AppAppearance(floatingNavigationBarEnabled = true)) is
                PreferenceWriteResult.Success,
        )

        assertEquals(true, preferences.read().floatingNavigationBarEnabled)
        assertEquals(true, preferences.observe().first().floatingNavigationBarEnabled)
    }

    @Test
    fun legacySchemaKeepsUserDataAndDefaultsColorSpecToAuto() = runBlocking {
        val store = InMemoryPreferenceStore()
        // 模拟 schema 3 的旧配置：只写入当时存在的键，没有 color_spec。
        store.write(AppPreferenceKeys.schemaVersion, 3)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Miuix.storageName)
        store.write(AppPreferenceKeys.paletteStyle, AppPaletteStyle.Vibrant.name)

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Miuix, appearance.uiStyle)
        assertEquals(AppPaletteStyle.Vibrant, appearance.paletteStyle)
        assertEquals(AppColorSpec.Auto, appearance.colorSpec)
        assertEquals(1f, appearance.uiScale)
    }

    @Test
    fun colorSpecRoundTripsThroughStore() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(
            preferences.write(AppAppearance(colorSpec = AppColorSpec.Spec2025)) is
                PreferenceWriteResult.Success,
        )

        assertEquals(AppColorSpec.Spec2025, preferences.read().colorSpec)
        assertEquals(AppColorSpec.Spec2025, preferences.observe().first().colorSpec)
    }

    @Test
    fun unknownColorSpecFallsBackToAuto() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(AppPreferenceKeys.colorSpec, "Spec3000")

        assertEquals(AppColorSpec.Auto, AppPreferences(store).read().colorSpec)
    }

    @Test
    fun newerSchemaIsNotOverwritten() = runBlocking {
        val store = InMemoryPreferenceStore()
        val futureVersion = AppPreferenceKeys.SCHEMA_VERSION + 1
        store.write(AppPreferenceKeys.schemaVersion, futureVersion)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Material3.storageName)
        val preferences = AppPreferences(store)

        val result = preferences.write(AppAppearance(uiStyle = UiThemeStyle.Miuix))

        assertTrue(result is PreferenceWriteResult.Failure)
        assertEquals(futureVersion, store.read(AppPreferenceKeys.schemaVersion))
        assertEquals(UiThemeStyle.Material3, preferences.read().uiStyle)
    }

    @Test
    fun legacySchemaKeepsUserDataAndDefaultsUiScaleTo100Percent() = runBlocking {
        val store = InMemoryPreferenceStore()
        // 模拟 schema 4 的旧配置：只写入当时存在的键，没有 ui_scale_percent。
        store.write(AppPreferenceKeys.schemaVersion, 4)
        store.write(AppPreferenceKeys.uiStyle, UiThemeStyle.Miuix.storageName)
        store.write(AppPreferenceKeys.colorSpec, AppColorSpec.Spec2025.name)

        val appearance = AppPreferences(store).read()

        assertEquals(UiThemeStyle.Miuix, appearance.uiStyle)
        assertEquals(AppColorSpec.Spec2025, appearance.colorSpec)
        assertEquals(1f, appearance.uiScale)
    }

    @Test
    fun uiScaleRoundTripsThroughStoreAsPercent() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(preferences.write(AppAppearance(uiScale = 1.3f)) is PreferenceWriteResult.Success)

        // 界面缩放按百分比整数落库，避免浮点误差。
        assertEquals(130, store.read(AppPreferenceKeys.uiScalePercent))
        assertEquals(1.3f, preferences.read().uiScale)
        assertEquals(1.3f, preferences.observe().first().uiScale)
    }

    @Test
    fun uiScaleIsSnappedToNearestStepOnWrite() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(preferences.write(AppAppearance(uiScale = 1.24f)) is PreferenceWriteResult.Success)

        // 1.24 更接近 1.2 而不是 1.3。
        assertEquals(120, store.read(AppPreferenceKeys.uiScalePercent))
        assertEquals(1.2f, preferences.read().uiScale)
    }

    @Test
    fun unknownUiScalePercentFallsBackToNearestStep() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(AppPreferenceKeys.uiScalePercent, 0)

        assertEquals(0.8f, AppPreferences(store).read().uiScale)
    }

    @Test
    fun miuixMonetToggleRoundTripsThroughStore() = runBlocking {
        val store = InMemoryPreferenceStore()
        val preferences = AppPreferences(store)

        assertTrue(preferences.write(AppAppearance(miuixMonetEnabled = false)) is PreferenceWriteResult.Success)

        assertEquals(false, preferences.read().miuixMonetEnabled)
        assertEquals(false, preferences.observe().first().miuixMonetEnabled)
    }
}
