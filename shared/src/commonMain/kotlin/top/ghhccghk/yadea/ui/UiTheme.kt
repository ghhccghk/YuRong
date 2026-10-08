package top.ghhccghk.yadea.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
)

@Immutable
data class AppTypography(
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val summary: TextStyle,
    val value: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val debug: TextStyle,
)

@Immutable
data class AppShapes(
    val card: Shape,
    val button: Shape,
    val field: Shape,
)

@Immutable
data class AppThemeTokens(
    val colors: AppColors,
    val typography: AppTypography,
    val shapes: AppShapes,
)

val LocalUiThemeStyle = staticCompositionLocalOf { UiThemeStyle.Miuix }
private val LocalAppThemeTokens = staticCompositionLocalOf<AppThemeTokens> {
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
}

@Composable
fun rememberUiThemeStyle(): MutableState<UiThemeStyle> =
    rememberSaveable(saver = UiThemeStyleSaver) {
        mutableStateOf(UiThemeStyle.Miuix)
    }

@Composable
fun AppTheme(
    uiThemeStyle: UiThemeStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiThemeStyle provides uiThemeStyle) {
        when (uiThemeStyle) {
            UiThemeStyle.Miuix -> {
                val controller = remember {
                    ThemeController(ColorSchemeMode.System)
                }
                MiuixTheme(controller = controller) {
                    val tokens = rememberMiuixAppThemeTokens()
                    CompositionLocalProvider(LocalAppThemeTokens provides tokens) {
                        MaterialTheme(content = content)
                    }
                }
            }

            UiThemeStyle.Material3 -> MaterialTheme {
                val tokens = rememberMaterialAppThemeTokens()
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
private fun rememberMaterialAppThemeTokens(): AppThemeTokens {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colors, typography) {
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
            ),
            typography = AppTypography(
                pageTitle = typography.headlineMedium,
                sectionTitle = typography.titleMedium,
                title = typography.titleLarge,
                body = typography.bodyMedium,
                summary = typography.bodySmall,
                value = typography.bodyMedium,
                label = typography.labelLarge,
                button = typography.labelLarge,
                debug = typography.bodySmall.copy(fontSize = 10.sp),
            ),
            shapes = AppShapes(
                card = RoundedCornerShape(12.dp),
                button = RoundedCornerShape(20.dp),
                field = RoundedCornerShape(8.dp),
            ),
        )
    }
}

@Composable
private fun rememberMiuixAppThemeTokens(): AppThemeTokens {
    val colors = MiuixTheme.colorScheme
    val typography = MiuixTheme.textStyles
    return remember(colors, typography) {
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
            ),
            typography = AppTypography(
                pageTitle = typography.title2,
                sectionTitle = typography.subtitle,
                title = typography.main,
                body = typography.body2,
                summary = typography.body2,
                value = typography.body2,
                label = typography.footnote1,
                button = typography.button,
                debug = typography.footnote2,
            ),
            shapes = AppShapes(
                card = RoundedCornerShape(16.dp),
                button = RoundedCornerShape(16.dp),
                field = RoundedCornerShape(16.dp),
            ),
        )
    }
}

private val UiThemeStyleSaver = Saver<MutableState<UiThemeStyle>, String>(
    save = { it.value.storageName },
    restore = { value -> mutableStateOf(UiThemeStyle.fromStorageName(value)) },
)
