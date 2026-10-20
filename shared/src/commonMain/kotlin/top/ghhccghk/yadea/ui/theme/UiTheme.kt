package top.ghhccghk.yadea.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.PreferenceStore
import top.yukonga.miuix.kmp.theme.ThemeController

enum class UiThemeStyle(
    val storageName: String,
    val label: String,
    val description: String,
) {
    Miuix(
        storageName = "miuix",
        label = "Miuix",
        description = "HyperOS 风格组件与交互",
    ),
    Material3(
        storageName = "material3",
        label = "Material 3",
        description = "保留原有 Material Design 3 界面",
    );

    companion object {
        fun fromStorageName(value: String?): UiThemeStyle =
            entries.firstOrNull { it.storageName == value } ?: Miuix
    }
}

enum class AppThemeMode {
    System,
    Light,
    Dark,
}

enum class AppColorSource {
    SystemDynamic,
    SeedColor,
    Preset,
}

enum class AppPaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    Monochrome,
    Fidelity,
    Content,
}

/**
 * Miuix Monet 取色使用的 Material Color Spec。
 *
 * [Auto] 沿用调色板推导出的默认标准（与旧版本行为一致）；显式选择后由用户决定，
 * 当调色板不支持 Spec 2025 时 Miuix 会自动降级到 Spec 2021，不会报错。
 */
enum class AppColorSpec {
    Auto,
    Spec2021,
    Spec2025,
}
@Immutable
data class AppAppearance(
    val uiStyle: UiThemeStyle = UiThemeStyle.Miuix,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val colorSource: AppColorSource = AppColorSource.SeedColor,
    val seedColorArgb: Int = 0xFF7B4DFF.toInt(),
    val presetId: String = "default",
    val paletteStyle: AppPaletteStyle = AppPaletteStyle.TonalSpot,
    // Miuix Monet 的颜色标准；默认跟随调色板，保证旧版本升级后外观不变。
    val colorSpec: AppColorSpec = AppColorSpec.Auto,
    val amoledDarkEnabled: Boolean = false,
    val themeAnimationsEnabled: Boolean = true,
    // 是否让 Miuix 使用 Monet 动态取色；关闭后回退到 Miuix 原生 HyperOS 配色。
    val miuixMonetEnabled: Boolean = true,
    // 是否让 Miuix 使用 HyperOS 悬浮胶囊底栏；Material 3 没有对应的原生样式，始终使用常规底栏。
    val floatingNavigationBarEnabled: Boolean = false,
    // 界面缩放倍率；1.0 为原始尺寸，持久化时按百分比整数保存。
    val uiScale: Float = 1f,
    // 是否让 Miuix 顶栏/底栏使用官方 miuix-blur 背景模糊；默认关闭，保证旧配置升级后外观不变。
    val blurEnabled: Boolean = false,
)

@Immutable
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    // 顶栏专用颜色：Miuix 原生 HyperOS 配色开启时仍需跟随应用的动态取色，
    // 因此不能直接复用 Miuix 自己的 background。
    val topBar: Color,
    val onTopBar: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val divider: Color,
    val secondary: Color,
    val onSecondary: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surfaceContainer: Color,
    val onSurfaceContainer: Color,
    val surfaceContainerHighest: Color,
    val onSurfaceContainerHighest: Color,
    val page: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val previewBackground: Color,
    val previewScrim: Color,
    val previewSelection: Color,
    val previewOutline: Color,
)

@Immutable
data class AppTypography(
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val title: TextStyle,
    val itemTitle: TextStyle,
    val body: TextStyle,
    val summary: TextStyle,
    val value: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
    val debug: TextStyle,
)

@Immutable
data class AppShapes(
    val section: Shape,
    val item: Shape,
    val field: Shape,
    val button: Shape,
    val dialog: Shape,
)

@Immutable
data class AppDimensions(
    val pageHorizontalPadding: Dp = 16.dp,
    val pageVerticalPadding: Dp = 12.dp,
    val sectionSpacing: Dp = 12.dp,
    val itemHorizontalPadding: Dp = 16.dp,
    val itemVerticalPadding: Dp = 14.dp,
    val controlSpacing: Dp = 8.dp,
    val compactSpacing: Dp = 4.dp,
    val buttonInsideHorizontal: Dp = 16.dp,
    val buttonInsideVertical: Dp = 13.dp,
    val cardCornerRadius: Dp = 16.dp,
    val fieldCornerRadius: Dp = 14.dp,
    val buttonCornerRadius: Dp = 16.dp,
)

@Immutable
data class AppThemeTokens(
    val colors: AppColors,
    val typography: AppTypography,
    val shapes: AppShapes,
    val dimensions: AppDimensions,
)

val LocalUiThemeStyle = staticCompositionLocalOf { UiThemeStyle.Miuix }
private val LocalAppThemeTokens = compositionLocalOf<AppThemeTokens> {
    error("AppThemeTokens must be provided by AppTheme")
}

object AppDesign {
    val style: UiThemeStyle
        @Composable @ReadOnlyComposable
        get() = LocalUiThemeStyle.current

    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppThemeTokens.current.colors

    val typography: AppTypography
        @Composable @ReadOnlyComposable
        get() = LocalAppThemeTokens.current.typography

    val shapes: AppShapes
        @Composable @ReadOnlyComposable
        get() = LocalAppThemeTokens.current.shapes

    val dimensions: AppDimensions
        @Composable @ReadOnlyComposable
        get() = LocalAppThemeTokens.current.dimensions
}

@Composable
fun rememberAppAppearance(
    preferences: AppPreferences,
): MutableState<AppAppearance> {
    val state = remember(preferences) { mutableStateOf(preferences.read()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(preferences) {
        preferences.observe().collect { state.value = it }
    }

    return remember(preferences, state, scope) {
        object : MutableState<AppAppearance> {
            override var value: AppAppearance
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    scope.launch { preferences.write(newValue) }
                }

            override fun component1(): AppAppearance = value
            override fun component2(): (AppAppearance) -> Unit = { value = it }
        }
    }
}

@Composable
fun AppTheme(
    appearance: AppAppearance,
    systemAccentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance.themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val materialColors = rememberAppMaterialColorScheme(appearance, darkTheme, systemAccentColor).let {
        if (appearance.themeAnimationsEnabled) it.animateAsState() else it
    }
    // 界面缩放与界面风格一次性下发：同一份 Density 覆盖同时作用于两套风格，业务页面不需要感知缩放。
    CompositionLocalProvider(
        LocalUiThemeStyle provides appearance.uiStyle,
        LocalDensity provides rememberScaledDensity(appearance.uiScale),
    ) {
        when (appearance.uiStyle) {
            UiThemeStyle.Miuix -> {
                // 关闭 Monet 时不再由种子色生成配色，改用 Miuix 原生亮/暗色板。
                val controller = remember(appearance, darkTheme, systemAccentColor) {
                    if (appearance.miuixMonetEnabled) {
                        ThemeController(
                            colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                            keyColor = appSeedColor(appearance, systemAccentColor),
                            paletteStyle = appearance.paletteStyle.toMiuixPaletteStyle(),
                            colorSpec = appearance.colorSpec.toMiuixColorSpec(appearance.paletteStyle),
                            isDark = darkTheme,
                        )
                    } else {
                        ThemeController(
                            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                            isDark = darkTheme,
                        )
                    }
                }
                MiuixTheme(controller = controller) {
                    // AMOLED 纯黑只改底色 token；放在动画之前，开关切换也能复用同一套颜色过渡。
                    val baseColors = MiuixTheme.colorScheme
                        .withAmoledBackground(appearance.amoledDarkEnabled && darkTheme)
                    val animatedColors = if (appearance.themeAnimationsEnabled) {
                        baseColors.animateAsState()
                    } else {
                        baseColors
                    }
                    MiuixTheme(colors = animatedColors) {
                        // MiuixTheme(colors = ...) 只会重新提供 LocalColors / TextStyles / Indication，
                        // 不会更新 Miuix 自己的 LocalContentColor；而 MiuixText、MiuixTextField、MiuixIcon
                        // 等组件在没有显式颜色时都会回退到它。这里显式跟随动画后的 onBackground，
                        // 否则这些组件会一直沿用外层 controller 的静态内容色，动态配色切换时不同步。
                        CompositionLocalProvider(MiuixLocalContentColor provides animatedColors.onBackground) {
                            val tokens = rememberMiuixAppThemeTokens(darkTheme, materialColors)
                            CompositionLocalProvider(LocalAppThemeTokens provides tokens) {
                                MaterialTheme(
                                    colorScheme = materialColors,
                                    content = content,
                                )
                            }
                        }
                    }
                }
            }

            UiThemeStyle.Material3 -> MaterialTheme(
                colorScheme = materialColors,
            ) {
                val tokens = rememberMaterialAppThemeTokens(darkTheme)
                CompositionLocalProvider(LocalAppThemeTokens provides tokens) {
                    content()
                }
            }
        }
    }
}

/**
 * 计算界面缩放后的 Density：同时缩放布局用的 dp 与文字用的 sp。
 *
 * 缩放值先吸附到 [AppInterfaceScale] 的档位再使用，1.0 倍时直接返回原始 Density，
 * 保持未开启缩放时的渲染路径完全不变。
 */
@Composable
private fun rememberScaledDensity(scale: Float): Density {
    val density = LocalDensity.current
    val normalized = AppInterfaceScale.snap(scale)
    return remember(density, normalized) {
        if (normalized == 1f) {
            density
        } else {
            Density(
                density = density.density * normalized,
                fontScale = density.fontScale * normalized,
            )
        }
    }
}

@Composable
fun AppStyleContent(
    material: @Composable () -> Unit,
    miuix: @Composable () -> Unit,
) {
    when (LocalUiThemeStyle.current) {
        UiThemeStyle.Material3 -> material()
        UiThemeStyle.Miuix -> miuix()
    }
}

@Composable
private fun rememberMaterialAppThemeTokens(darkTheme: Boolean): AppThemeTokens {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val status = appStatusColors(darkTheme)
    // Material 的 ColorScheme 是不可变实例，配色变化时会换成新对象，因此可以直接作为 key。
    return remember(colors, typography, darkTheme) {
        AppThemeTokens(
            colors = AppColors(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.onBackground,
                topBar = colors.background,
                onTopBar = colors.onBackground,
                surface = colors.surface,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariant,
                outline = colors.outline,
                divider = colors.outlineVariant,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                error = colors.error,
                onError = colors.onError,
                errorContainer = colors.errorContainer,
                onErrorContainer = colors.onErrorContainer,
                primaryContainer = colors.primaryContainer,
                onPrimaryContainer = colors.onPrimaryContainer,
                secondaryContainer = colors.secondaryContainer,
                onSecondaryContainer = colors.onSecondaryContainer,
                surfaceContainer = colors.surfaceContainer,
                onSurfaceContainer = colors.onSurface,
                surfaceContainerHighest = colors.surfaceContainerHighest,
                onSurfaceContainerHighest = colors.onSurface,
                page = colors.surfaceContainer,
                successContainer = status.successContainer,
                onSuccessContainer = status.onSuccessContainer,
                warningContainer = status.warningContainer,
                onWarningContainer = status.onWarningContainer,
                previewBackground = colors.surfaceVariant,
                previewScrim = colors.onSurface.copy(alpha = 0.5f),
                previewSelection = colors.primary.copy(alpha = 0.18f),
                previewOutline = colors.primary,
            ),
            typography = AppTypography(
                pageTitle = typography.headlineMedium,
                sectionTitle = typography.titleMedium,
                title = typography.titleLarge,
                itemTitle = typography.titleMedium,
                body = typography.bodyMedium,
                summary = typography.bodySmall,
                value = typography.bodyMedium,
                label = typography.labelLarge,
                button = typography.labelLarge,
                caption = typography.labelSmall,
                debug = typography.bodySmall.copy(fontSize = 10.sp),
            ),
            shapes = AppShapes(
                section = RoundedCornerShape(12.dp),
                item = RoundedCornerShape(8.dp),
                field = RoundedCornerShape(8.dp),
                button = RoundedCornerShape(20.dp),
                dialog = RoundedCornerShape(20.dp),
            ),
            dimensions = AppDimensions(),
        )
    }
}

@Composable
private fun rememberMiuixAppThemeTokens(
    darkTheme: Boolean,
    dynamicColors: ColorScheme,
): AppThemeTokens {
    val colors = MiuixTheme.colorScheme
    val typography = MiuixTheme.textStyles
    val status = appStatusColors(darkTheme)
    // Miuix 的 Colors 是字段为 mutableStateOf 的可变 @Stable 类：切换配色时 MiuixTheme 只会就地更新
    // 同一个实例的字段，对象引用不变，也没有重写 equals。若直接拿实例当 remember 的 key，
    // token 会永远停留在首次组合的那套配色，表现就是“只有一部分组件跟着换色”。
    // 这里在组合期读一份颜色快照作为 key：任意字段变化都会触发重组并重算 token。
    val colorSnapshot = colors.toColorList()
    return remember(colorSnapshot, typography, darkTheme, dynamicColors) {
        AppThemeTokens(
            colors = AppColors(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.onBackground,
                // Miuix 原生 HyperOS 模式也使用应用的动态配色作为顶栏底色，
                // 避免纯黑/纯白顶栏和动态色卡片并排时割裂。
                topBar = dynamicColors.background,
                onTopBar = dynamicColors.onBackground,
                surface = colors.surface,
                onSurface = colors.onSurface,
                surfaceVariant = colors.surfaceVariant,
                onSurfaceVariant = colors.onSurfaceVariantSummary,
                outline = colors.outline,
                divider = colors.dividerLine,
                secondary = colors.secondary,
                onSecondary = colors.onSecondary,
                error = colors.error,
                onError = colors.onError,
                errorContainer = colors.errorContainer,
                onErrorContainer = colors.onErrorContainer,
                primaryContainer = colors.primaryContainer,
                onPrimaryContainer = colors.onPrimaryContainer,
                secondaryContainer = colors.secondaryContainer,
                onSecondaryContainer = colors.onSecondaryContainer,
                surfaceContainer = colors.surfaceContainer,
                onSurfaceContainer = colors.onSurfaceContainer,
                surfaceContainerHighest = colors.surfaceContainerHighest,
                onSurfaceContainerHighest = colors.onSurfaceContainerHighest,
                page = colors.surface,
                successContainer = status.successContainer,
                onSuccessContainer = status.onSuccessContainer,
                warningContainer = status.warningContainer,
                onWarningContainer = status.onWarningContainer,
                previewBackground = colors.surfaceVariant,
                previewScrim = colors.onSurface.copy(alpha = 0.5f),
                previewSelection = colors.primary.copy(alpha = 0.18f),
                previewOutline = colors.primary,
            ),
            typography = AppTypography(
                pageTitle = typography.title2,
                sectionTitle = typography.subtitle,
                title = typography.title3,
                itemTitle = typography.main,
                body = typography.body2,
                summary = typography.body2,
                value = typography.body2,
                label = typography.footnote1,
                button = typography.button,
                caption = typography.footnote2,
                debug = typography.footnote2,
            ),
            shapes = AppShapes(
                section = RoundedCornerShape(18.dp),
                item = RoundedCornerShape(14.dp),
                field = RoundedCornerShape(14.dp),
                button = RoundedCornerShape(16.dp),
                dialog = RoundedCornerShape(24.dp),
            ),
            dimensions = AppDimensions(),
        )
    }
}

private data class AppStatusColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private fun appStatusColors(darkTheme: Boolean): AppStatusColors =
    if (darkTheme) {
        AppStatusColors(
            successContainer = Color(0xFF1E3B2A),
            onSuccessContainer = Color(0xFF8FD9A8),
            warningContainer = Color(0xFF3B2F14),
            onWarningContainer = Color(0xFFF0D48A),
        )
    } else {
        AppStatusColors(
            successContainer = Color(0xFFD7F4E1),
            onSuccessContainer = Color(0xFF1E6B42),
            warningContainer = Color(0xFFFFE7A8),
            onWarningContainer = Color(0xFF7A5200),
        )
    }
