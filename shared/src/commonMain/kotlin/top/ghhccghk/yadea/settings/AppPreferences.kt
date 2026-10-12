package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppColorSource
import top.ghhccghk.yadea.ui.AppPaletteStyle
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle

object AppPreferenceKeys {
    const val SCHEMA_VERSION = 1

    val schemaVersion = PreferenceKey.int("app_settings_schema_version", SCHEMA_VERSION)
    val uiStyle = PreferenceKey.string("ui_style", UiThemeStyle.Miuix.storageName)
    val themeMode = PreferenceKey.string("theme_mode", AppThemeMode.System.name)
    val colorSource = PreferenceKey.string("color_source", AppColorSource.SeedColor.name)
    val seedColorArgb = PreferenceKey.int("seed_color_argb", 0xFF7B4DFF.toInt())
    val presetId = PreferenceKey.string("theme_preset_id", "default")
    val paletteStyle = PreferenceKey.string("palette_style", AppPaletteStyle.TonalSpot.name)
    val amoledDark = PreferenceKey.boolean("amoled_dark", false)
    val themeAnimations = PreferenceKey.boolean("theme_animations", true)

    val all: List<PreferenceKey<*>> = listOf(
        schemaVersion,
        uiStyle,
        themeMode,
        colorSource,
        seedColorArgb,
        presetId,
        paletteStyle,
        amoledDark,
        themeAnimations,
    )
}

class AppPreferences(
    private val store: PreferenceStore,
) {
    fun read(): AppAppearance = decode(
        schemaVersion = store.read(AppPreferenceKeys.schemaVersion),
        uiStyle = store.read(AppPreferenceKeys.uiStyle),
        themeMode = store.read(AppPreferenceKeys.themeMode),
        colorSource = store.read(AppPreferenceKeys.colorSource),
        seedColorArgb = store.read(AppPreferenceKeys.seedColorArgb),
        presetId = store.read(AppPreferenceKeys.presetId),
        paletteStyle = store.read(AppPreferenceKeys.paletteStyle),
        amoledDark = store.read(AppPreferenceKeys.amoledDark),
        themeAnimations = store.read(AppPreferenceKeys.themeAnimations),
    )

    fun observe(): Flow<AppAppearance> {
        val flows = AppPreferenceKeys.all.map { key ->
            store.observe(key).map { value -> value as Any }
        }
        return combine(flows) { values ->
            decode(
                schemaVersion = values[0] as Int,
                uiStyle = values[1] as String,
                themeMode = values[2] as String,
                colorSource = values[3] as String,
                seedColorArgb = values[4] as Int,
                presetId = values[5] as String,
                paletteStyle = values[6] as String,
                amoledDark = values[7] as Boolean,
                themeAnimations = values[8] as Boolean,
            )
        }
    }

    suspend fun write(appearance: AppAppearance): PreferenceWriteResult {
        val writes = listOf(
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
            AppPreferenceKeys.uiStyle to appearance.uiStyle.storageName,
            AppPreferenceKeys.themeMode to appearance.themeMode.name,
            AppPreferenceKeys.colorSource to appearance.colorSource.name,
            AppPreferenceKeys.seedColorArgb to appearance.seedColorArgb,
            AppPreferenceKeys.presetId to appearance.presetId,
            AppPreferenceKeys.paletteStyle to appearance.paletteStyle.name,
            AppPreferenceKeys.amoledDark to appearance.amoledDarkEnabled,
            AppPreferenceKeys.themeAnimations to appearance.themeAnimationsEnabled,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun reset(): PreferenceWriteResult = write(AppAppearance())

    private suspend fun writeTyped(key: PreferenceKey<*>, value: Any): PreferenceWriteResult =
        when (key.type) {
            PreferenceType.Boolean -> store.write(key as PreferenceKey<Boolean>, value as Boolean)
            PreferenceType.Int -> store.write(key as PreferenceKey<Int>, value as Int)
            PreferenceType.Long -> store.write(key as PreferenceKey<Long>, value as Long)
            PreferenceType.Float -> store.write(key as PreferenceKey<Float>, value as Float)
            PreferenceType.String -> store.write(key as PreferenceKey<String>, value as String)
            PreferenceType.StringSet -> store.write(key as PreferenceKey<Set<String>>, value as Set<String>)
        }

    private fun decode(
        schemaVersion: Int,
        uiStyle: String,
        themeMode: String,
        colorSource: String,
        seedColorArgb: Int,
        presetId: String,
        paletteStyle: String,
        amoledDark: Boolean,
        themeAnimations: Boolean,
    ): AppAppearance {
        // Unknown values are intentionally normalized to safe defaults.
        return AppAppearance(
            uiStyle = UiThemeStyle.fromStorageName(uiStyle),
            themeMode = AppThemeMode.entries.firstOrNull { it.name == themeMode } ?: AppThemeMode.System,
            colorSource = AppColorSource.entries.firstOrNull { it.name == colorSource } ?: AppColorSource.SeedColor,
            seedColorArgb = seedColorArgb,
            presetId = presetId.ifBlank { "default" },
            paletteStyle = AppPaletteStyle.entries.firstOrNull { it.name == paletteStyle } ?: AppPaletteStyle.TonalSpot,
            amoledDarkEnabled = amoledDark,
            themeAnimationsEnabled = themeAnimations,
        )
    }
}
