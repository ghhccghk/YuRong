package top.ghhccghk.yadea.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
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
@Immutable
data class AppAppearance(
    val uiStyle: UiThemeStyle = UiThemeStyle.Miuix,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val colorSource: AppColorSource = AppColorSource.SeedColor,
    val seedColorArgb: Int = 0xFF7B4DFF.toInt(),
    val presetId: String = "default",
    val paletteStyle: AppPaletteStyle = AppPaletteStyle.TonalSpot,
    val amoledDarkEnabled: Boolean = false,
    val themeAnimationsEnabled: Boolean = true,
)

@Immutable
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
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
    val materialColors = rememberAppMaterialColorScheme(appearance, darkTheme, systemAccentColor)
    CompositionLocalProvider(LocalUiThemeStyle provides appearance.uiStyle) {
        when (appearance.uiStyle) {
            UiThemeStyle.Miuix -> {
                val controller = remember(darkTheme, appearance.paletteStyle, systemAccentColor) {
                    ThemeController(
                        colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                        keyColor = appSeedColor(appearance, systemAccentColor),
                        paletteStyle = appearance.paletteStyle.toMiuixPaletteStyle(),
                        colorSpec = appearance.paletteStyle.toMiuixColorSpec(),
                        isDark = darkTheme,
                    )
                }
                MiuixTheme(controller = controller) {
                    val tokens = rememberMiuixAppThemeTokens(darkTheme)
                    CompositionLocalProvider(LocalAppThemeTokens provides tokens) {
                        MaterialTheme(
                            colorScheme = materialColors,
                            content = content,
                        )
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
    return remember(colors, typography, darkTheme) {
        AppThemeTokens(
            colors = AppColors(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.onBackground,
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
private fun rememberMiuixAppThemeTokens(darkTheme: Boolean): AppThemeTokens {
    val colors = MiuixTheme.colorScheme
    val typography = MiuixTheme.textStyles
    val status = appStatusColors(darkTheme)
    return remember(colors, typography, darkTheme) {
        AppThemeTokens(
            colors = AppColors(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                background = colors.background,
                onBackground = colors.onBackground,
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

private val AppAppearanceSaver = Saver<MutableState<AppAppearance>, String>(
    save = { state ->
        listOf(
            state.value.uiStyle.storageName,
            state.value.themeMode.name,
            state.value.colorSource.name,
            state.value.seedColorArgb.toString(),
            state.value.presetId,
            state.value.paletteStyle.name,
            state.value.amoledDarkEnabled.toString(),
            state.value.themeAnimationsEnabled.toString(),
        ).joinToString("|")
    },
    restore = { value ->
        val parts = value.split("|")
        mutableStateOf(
            AppAppearance(
                uiStyle = UiThemeStyle.fromStorageName(parts.getOrNull(0)),
                themeMode = AppThemeMode.entries.firstOrNull { it.name == parts.getOrNull(1) }
                    ?: AppThemeMode.System,
                colorSource = AppColorSource.entries.firstOrNull { it.name == parts.getOrNull(2) }
                    ?: AppColorSource.SeedColor,
                seedColorArgb = parts.getOrNull(3)?.toIntOrNull() ?: 0xFF7B4DFF.toInt(),
                presetId = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "default",
                paletteStyle = AppPaletteStyle.entries.firstOrNull { it.name == parts.getOrNull(5) }
                    ?: AppPaletteStyle.TonalSpot,
                amoledDarkEnabled = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
                themeAnimationsEnabled = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: true,
            )
        )
    },
)
