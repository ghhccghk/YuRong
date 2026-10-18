package top.ghhccghk.yadea.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle as MaterialKolorPaletteStyle
import com.materialkolor.dynamicColorScheme
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.ThemeColorSpec as MiuixThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixThemePaletteStyle

data class AppThemePreset(
    val id: String,
    val label: String,
    val seedColorArgb: Int,
)

object AppThemePresets {
    val Default = AppThemePreset("default", "默认蓝", 0xFF7B4DFF.toInt())

    val all: List<AppThemePreset> = listOf(
        Default,
        AppThemePreset("violet", "紫罗兰", 0xFF6750A4.toInt()),
        AppThemePreset("coral", "珊瑚红", 0xFFB94073.toInt()),
        AppThemePreset("ocean", "海洋蓝", 0xFF00639B.toInt()),
        AppThemePreset("forest", "森林绿", 0xFF006D39.toInt()),
        AppThemePreset("sunset", "日落橙", 0xFF944A00.toInt()),
    )

    fun byId(id: String): AppThemePreset =
        all.firstOrNull { it.id == id } ?: Default
}

fun appSeedColor(
    appearance: AppAppearance,
    systemAccentColor: Color?,
): Color {
    val sourceColor = when (appearance.colorSource) {
        AppColorSource.SystemDynamic -> systemAccentColor
        AppColorSource.SeedColor -> Color(appearance.seedColorArgb.toLong() and 0xFFFFFFFFL)
        AppColorSource.Preset -> Color(
            AppThemePresets.byId(appearance.presetId).seedColorArgb.toLong() and 0xFFFFFFFFL,
        )
    }
    return sourceColor ?: Color(appearance.seedColorArgb.toLong() and 0xFFFFFFFFL)
}

@Composable
fun rememberAppMaterialColorScheme(
    appearance: AppAppearance,
    darkTheme: Boolean,
    systemAccentColor: Color?,
): ColorScheme {
    val seedColor = appSeedColor(appearance, systemAccentColor)
    return remember(seedColor, darkTheme, appearance.paletteStyle, appearance.amoledDarkEnabled) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            isAmoled = appearance.amoledDarkEnabled && darkTheme,
            style = appearance.paletteStyle.toMaterialKolorPaletteStyle(),
        ).withAmoledBackground(appearance.amoledDarkEnabled && darkTheme)
    }
}

fun AppPaletteStyle.toMaterialKolorPaletteStyle(): MaterialKolorPaletteStyle =
    when (this) {
        AppPaletteStyle.TonalSpot -> MaterialKolorPaletteStyle.TonalSpot
        AppPaletteStyle.Neutral -> MaterialKolorPaletteStyle.Neutral
        AppPaletteStyle.Vibrant -> MaterialKolorPaletteStyle.Vibrant
        AppPaletteStyle.Expressive -> MaterialKolorPaletteStyle.Expressive
        AppPaletteStyle.Rainbow -> MaterialKolorPaletteStyle.Rainbow
        AppPaletteStyle.FruitSalad -> MaterialKolorPaletteStyle.FruitSalad
        AppPaletteStyle.Monochrome -> MaterialKolorPaletteStyle.Monochrome
        AppPaletteStyle.Fidelity -> MaterialKolorPaletteStyle.Fidelity
        AppPaletteStyle.Content -> MaterialKolorPaletteStyle.Content
    }

fun AppPaletteStyle.toMiuixPaletteStyle(): MiuixThemePaletteStyle =
    when (this) {
        AppPaletteStyle.TonalSpot -> MiuixThemePaletteStyle.TonalSpot
        AppPaletteStyle.Neutral -> MiuixThemePaletteStyle.Neutral
        AppPaletteStyle.Vibrant -> MiuixThemePaletteStyle.Vibrant
        AppPaletteStyle.Expressive -> MiuixThemePaletteStyle.Expressive
        AppPaletteStyle.Rainbow -> MiuixThemePaletteStyle.Rainbow
        AppPaletteStyle.FruitSalad -> MiuixThemePaletteStyle.FruitSalad
        AppPaletteStyle.Monochrome -> MiuixThemePaletteStyle.Monochrome
        AppPaletteStyle.Fidelity -> MiuixThemePaletteStyle.Fidelity
        AppPaletteStyle.Content -> MiuixThemePaletteStyle.Content
    }

/** 调色板推导出的默认颜色标准，保留旧版本行为。 */
fun AppPaletteStyle.toMiuixColorSpec(): MiuixThemeColorSpec =
    when (this) {
        AppPaletteStyle.TonalSpot,
        AppPaletteStyle.Neutral,
        AppPaletteStyle.Vibrant,
        AppPaletteStyle.Expressive,
        -> MiuixThemeColorSpec.Spec2025

        else -> MiuixThemeColorSpec.Spec2021
    }

/**
 * 把设置项里的颜色标准映射为 Miuix 的 [MiuixThemeColorSpec]。
 *
 * [AppColorSpec.Auto] 继续使用调色板推导值；显式选择 Spec 2025 时，若当前调色板不支持，
 * Miuix 内部会自动降级为 Spec 2021。
 */
fun AppColorSpec.toMiuixColorSpec(paletteStyle: AppPaletteStyle): MiuixThemeColorSpec =
    when (this) {
        AppColorSpec.Auto -> paletteStyle.toMiuixColorSpec()
        AppColorSpec.Spec2021 -> MiuixThemeColorSpec.Spec2021
        AppColorSpec.Spec2025 -> MiuixThemeColorSpec.Spec2025
    }

private fun ColorScheme.withAmoledBackground(enabled: Boolean): ColorScheme =
    if (!enabled) {
        this
    } else {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
        )
    }

/**
 * Miuix 侧的 AMOLED 纯黑变换，与 Material 侧的 [ColorScheme.withAmoledBackground] 语义对齐。
 *
 * 只把承担"大面积底色"职责的 token 压成纯黑，文字、描边、分隔线和控件色保持原值，
 * 这样在纯黑背景上依然保留可读的层次。Miuix 没有 surfaceDim / surfaceContainerLow(est)，
 * 因此额外覆盖 surfaceVariant 与高低两档 container，以涵盖 Card、ListPopup、悬浮底栏等容器。
 */
internal fun MiuixColors.withAmoledBackground(enabled: Boolean): MiuixColors =
    if (!enabled) {
        this
    } else {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
        )
    }
