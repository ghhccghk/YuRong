package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import top.ghhccghk.yadea.auth.AuthProfileCodec
import top.ghhccghk.yadea.auth.AuthSessionSnapshot
import top.ghhccghk.yadea.auth.AuthUser
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppColorSource
import top.ghhccghk.yadea.ui.AppColorSpec
import top.ghhccghk.yadea.ui.AppInterfaceScale
import top.ghhccghk.yadea.ui.AppPaletteStyle
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle

object AppPreferenceKeys {
    // 设置结构版本。新增设置项时递增，并同步补上迁移逻辑与测试。
    // 1 -> 2：新增 miuix_monet（Miuix 是否启用 Monet 动态配色）。
    // 2 -> 3：新增 floating_navigation_bar（Miuix 是否启用悬浮底栏）。
    // 3 -> 4：新增 color_spec（Miuix Monet 使用的颜色标准）。
    // 4 -> 5：新增 ui_scale_percent（界面缩放百分比）。
    // 5 -> 6：新增 blur_enabled（Miuix 是否启用官方 miuix-blur 背景模糊）。
    // 6 -> 7：新增 usr_token / usr_info / login_id（登录态）。
    const val SCHEMA_VERSION = 7

    val schemaVersion = PreferenceKey.int("app_settings_schema_version", SCHEMA_VERSION)
    val uiStyle = PreferenceKey.string("ui_style", UiThemeStyle.Miuix.storageName)
    val themeMode = PreferenceKey.string("theme_mode", AppThemeMode.System.name)
    val colorSource = PreferenceKey.string("color_source", AppColorSource.SeedColor.name)
    val seedColorArgb = PreferenceKey.int("seed_color_argb", 0xFF7B4DFF.toInt())
    val presetId = PreferenceKey.string("theme_preset_id", "default")
    val paletteStyle = PreferenceKey.string("palette_style", AppPaletteStyle.TonalSpot.name)
    val amoledDark = PreferenceKey.boolean("amoled_dark", false)
    val themeAnimations = PreferenceKey.boolean("theme_animations", true)

    // 默认开启，保证旧版本（schema 1 及更早）升级后仍保持原有 Monet 配色。
    val miuixMonet = PreferenceKey.boolean("miuix_monet", true)

    // 默认关闭，保证旧版本（schema 2 及更早）升级后仍是原有常规底栏。
    val floatingNavigationBar = PreferenceKey.boolean("floating_navigation_bar", false)

    // 默认跟随调色板，保证旧版本（schema 3 及更早）升级后配色外观不变。
    val colorSpec = PreferenceKey.string("color_spec", AppColorSpec.Auto.name)

    // 默认 100%，保证旧版本（schema 4 及更早）升级后界面尺寸不变。
    val uiScalePercent = PreferenceKey.int("ui_scale_percent", 100)

    // 默认关闭，保证旧版本（schema 5 及更早）升级后仍是原有纯色顶栏与底栏。
    val blurEnabled = PreferenceKey.boolean("blur_enabled", false)

    val usrToken = PreferenceKey.string("usr_token", "")
    val usrInfo = PreferenceKey.string("usr_info", "")
    val loginId = PreferenceKey.string("login_id", "")

    val authKeys: List<PreferenceKey<*>> = listOf(usrToken, usrInfo, loginId)

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
        miuixMonet,
        floatingNavigationBar,
        colorSpec,
        uiScalePercent,
        blurEnabled,
    )
}

class AppPreferences(
    private val store: PreferenceStore,
) {
    fun read(): AppAppearance = decode(
        uiStyle = store.read(AppPreferenceKeys.uiStyle),
        themeMode = store.read(AppPreferenceKeys.themeMode),
        colorSource = store.read(AppPreferenceKeys.colorSource),
        seedColorArgb = store.read(AppPreferenceKeys.seedColorArgb),
        presetId = store.read(AppPreferenceKeys.presetId),
        paletteStyle = store.read(AppPreferenceKeys.paletteStyle),
        amoledDark = store.read(AppPreferenceKeys.amoledDark),
        themeAnimations = store.read(AppPreferenceKeys.themeAnimations),
        miuixMonet = store.read(AppPreferenceKeys.miuixMonet),
        floatingNavigationBar = store.read(AppPreferenceKeys.floatingNavigationBar),
        colorSpec = store.read(AppPreferenceKeys.colorSpec),
        uiScalePercent = store.read(AppPreferenceKeys.uiScalePercent),
        blurEnabled = store.read(AppPreferenceKeys.blurEnabled),
    )

    fun observe(): Flow<AppAppearance> {
        val flows = AppPreferenceKeys.all.map { key ->
            store.observe(key).map { value -> value as Any }
        }
        // 索引与 AppPreferenceKeys.all 的顺序一一对应，新增 key 时必须同步调整。
        return combine(flows) { values ->
            decode(
                uiStyle = values[1] as String,
                themeMode = values[2] as String,
                colorSource = values[3] as String,
                seedColorArgb = values[4] as Int,
                presetId = values[5] as String,
                paletteStyle = values[6] as String,
                amoledDark = values[7] as Boolean,
                themeAnimations = values[8] as Boolean,
                miuixMonet = values[9] as Boolean,
                floatingNavigationBar = values[10] as Boolean,
                colorSpec = values[11] as String,
                uiScalePercent = values[12] as Int,
                blurEnabled = values[13] as Boolean,
            )
        }
    }

    suspend fun write(appearance: AppAppearance): PreferenceWriteResult {
        // 未来版本写入的配置不得被当前版本覆盖，避免降级时丢数据。
        val storedSchemaVersion = store.read(AppPreferenceKeys.schemaVersion)
        if (storedSchemaVersion > AppPreferenceKeys.SCHEMA_VERSION) {
            return PreferenceWriteResult.Failure(
                IllegalStateException(
                    "Stored settings schema $storedSchemaVersion is newer than supported " +
                        "${AppPreferenceKeys.SCHEMA_VERSION}.",
                ),
            )
        }

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
            AppPreferenceKeys.miuixMonet to appearance.miuixMonetEnabled,
            AppPreferenceKeys.floatingNavigationBar to appearance.floatingNavigationBarEnabled,
            AppPreferenceKeys.colorSpec to appearance.colorSpec.name,
            AppPreferenceKeys.uiScalePercent to AppInterfaceScale.percentOf(appearance.uiScale),
            AppPreferenceKeys.blurEnabled to appearance.blurEnabled,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun reset(): PreferenceWriteResult = write(AppAppearance())

    /** 读取持久化登录态；损坏的用户 JSON 会回退为未登录。 */
    fun readAuth(): AuthSessionSnapshot {
        val token = store.read(AppPreferenceKeys.usrToken)
        val user = AuthProfileCodec.decode(store.read(AppPreferenceKeys.usrInfo))
        return AuthSessionSnapshot(
            token = token,
            user = user,
            loginId = store.read(AppPreferenceKeys.loginId),
        )
    }

    /** 保存登录态。`usr_info` 使用与原实现相同的 JSON 字段名。 */
    suspend fun writeAuth(user: AuthUser): PreferenceWriteResult {
        val storedSchemaVersion = store.read(AppPreferenceKeys.schemaVersion)
        if (storedSchemaVersion > AppPreferenceKeys.SCHEMA_VERSION) {
            return PreferenceWriteResult.Failure(
                IllegalStateException(
                    "Stored settings schema $storedSchemaVersion is newer than supported " +
                        "${AppPreferenceKeys.SCHEMA_VERSION}.",
                ),
            )
        }
        val currentLoginId = store.read(AppPreferenceKeys.loginId)
            .ifBlank { newLoginId() }
        val writes = listOf(
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
            AppPreferenceKeys.usrToken to user.token,
            AppPreferenceKeys.usrInfo to AuthProfileCodec.encode(user),
            AppPreferenceKeys.loginId to currentLoginId,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    /** 只清理登录相关 key，不动外观设置。 */
    suspend fun clearAuth(): PreferenceWriteResult {
        for (key in AppPreferenceKeys.authKeys) {
            val result = store.delete(key)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

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
        uiStyle: String,
        themeMode: String,
        colorSource: String,
        seedColorArgb: Int,
        presetId: String,
        paletteStyle: String,
        amoledDark: Boolean,
        themeAnimations: Boolean,
        miuixMonet: Boolean,
        floatingNavigationBar: Boolean,
        colorSpec: String,
        uiScalePercent: Int,
        blurEnabled: Boolean,
    ): AppAppearance {
        // 未知枚举、损坏值一律回退到安全默认值，保证读取不崩溃。
        // 旧版本缺失的新键都由 PreferenceKey 默认值补齐
        // （miuix_monet 补 true，floating_navigation_bar 补 false，color_spec 补 Auto），
        // 因此每次版本升级都是纯增量迁移，不会丢弃用户已有配置。
        return AppAppearance(
            uiStyle = UiThemeStyle.fromStorageName(uiStyle),
            themeMode = AppThemeMode.entries.firstOrNull { it.name == themeMode } ?: AppThemeMode.System,
            colorSource = AppColorSource.entries.firstOrNull { it.name == colorSource } ?: AppColorSource.SeedColor,
            seedColorArgb = seedColorArgb,
            presetId = presetId.ifBlank { "default" },
            paletteStyle = AppPaletteStyle.entries.firstOrNull { it.name == paletteStyle } ?: AppPaletteStyle.TonalSpot,
            amoledDarkEnabled = amoledDark,
            themeAnimationsEnabled = themeAnimations,
            miuixMonetEnabled = miuixMonet,
            floatingNavigationBarEnabled = floatingNavigationBar,
            colorSpec = AppColorSpec.entries.firstOrNull { it.name == colorSpec } ?: AppColorSpec.Auto,
            // 界面缩放以百分比整数存储；损坏值由 AppInterfaceScale 收敛到最近的合法档位。
            uiScale = AppInterfaceScale.percentToScale(uiScalePercent),
            blurEnabled = blurEnabled,
        )
    }

    private fun newLoginId(): String {
        val bytes = ByteArray(8) { Random.nextInt(0, 256).toByte() }
        return "device-" + bytes.joinToString("") {
            ((it.toInt() and 0xFF)).toString(16).padStart(2, '0')
        }
    }
}
