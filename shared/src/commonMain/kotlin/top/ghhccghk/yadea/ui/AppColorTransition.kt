package top.ghhccghk.yadea.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors

@Composable
internal fun rememberColorTransition(target: List<Color>): List<Color> {
    val transition = remember { ColorListTransition(target) }
    LaunchedEffect(target) { transition.animateTo(target) }
    return transition.current
}

private class ColorListTransition(initial: List<Color>) {
    private var from: List<Color> = initial
    private var to: List<Color> = initial
    private val progress = Animatable(1f)

    val current: List<Color>
        get() {
            val fraction = progress.value
            if (fraction >= 1f) return to
            return List(to.size) { index -> lerp(from[index], to[index], fraction) }
        }

    suspend fun animateTo(target: List<Color>) {
        if (target == to) return
        from = current
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, spring())
    }
}

@Composable
internal fun ColorScheme.animateAsState(): ColorScheme {
    val animated = rememberColorTransition(toColorList())
    return remember(animated) { animated.toColorScheme() }
}

@Composable
internal fun MiuixColors.animateAsState(): MiuixColors {
    val animated = rememberColorTransition(toColorList())
    return remember(animated) { animated.toMiuixColors() }
}

private fun ColorScheme.toColorList(): List<Color> = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
    surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
    outline, outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer,
    surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
    primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant,
    secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant,
    tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant,
)

private fun List<Color>.toColorScheme(): ColorScheme {
    var index = 0
    fun next(): Color = this[index++]
    return ColorScheme(
        primary = next(), onPrimary = next(), primaryContainer = next(), onPrimaryContainer = next(),
        inversePrimary = next(), secondary = next(), onSecondary = next(), secondaryContainer = next(),
        onSecondaryContainer = next(), tertiary = next(), onTertiary = next(), tertiaryContainer = next(),
        onTertiaryContainer = next(), background = next(), onBackground = next(), surface = next(),
        onSurface = next(), surfaceVariant = next(), onSurfaceVariant = next(), surfaceTint = next(),
        inverseSurface = next(), inverseOnSurface = next(), error = next(), onError = next(),
        errorContainer = next(), onErrorContainer = next(), outline = next(), outlineVariant = next(),
        scrim = next(), surfaceBright = next(), surfaceDim = next(), surfaceContainer = next(),
        surfaceContainerHigh = next(), surfaceContainerHighest = next(), surfaceContainerLow = next(),
        surfaceContainerLowest = next(), primaryFixed = next(), primaryFixedDim = next(),
        onPrimaryFixed = next(), onPrimaryFixedVariant = next(), secondaryFixed = next(),
        secondaryFixedDim = next(), onSecondaryFixed = next(), onSecondaryFixedVariant = next(),
        tertiaryFixed = next(), tertiaryFixedDim = next(), onTertiaryFixed = next(), onTertiaryFixedVariant = next(),
    )
}

private fun MiuixColors.toColorList(): List<Color> = listOf(
    primary, onPrimary, primaryVariant, onPrimaryVariant, error, onError, errorContainer, onErrorContainer,
    disabledPrimary, disabledOnPrimary, disabledPrimaryButton, disabledOnPrimaryButton, disabledPrimarySlider,
    primaryContainer, onPrimaryContainer, secondary, onSecondary, secondaryVariant, onSecondaryVariant,
    disabledSecondary, disabledOnSecondary, disabledSecondaryVariant, disabledOnSecondaryVariant,
    secondaryContainer, onSecondaryContainer, secondaryContainerVariant, onSecondaryContainerVariant,
    tertiaryContainer, onTertiaryContainer, tertiaryContainerVariant,
    background, onBackground, onBackgroundVariant, surface, onSurface, surfaceVariant, onSurfaceSecondary,
    onSurfaceVariantSummary, onSurfaceVariantActions, disabledOnSurface, surfaceContainer, onSurfaceContainer,
    onSurfaceContainerVariant, surfaceContainerHigh, onSurfaceContainerHigh, surfaceContainerHighest,
    onSurfaceContainerHighest, outline, dividerLine, windowDimming, sliderKeyPoint, sliderKeyPointForeground, sliderBackground,
)

private fun List<Color>.toMiuixColors(): MiuixColors {
    var index = 0
    fun next(): Color = this[index++]
    return MiuixColors(
        primary = next(), onPrimary = next(), primaryVariant = next(), onPrimaryVariant = next(),
        error = next(), onError = next(), errorContainer = next(), onErrorContainer = next(),
        disabledPrimary = next(), disabledOnPrimary = next(), disabledPrimaryButton = next(),
        disabledOnPrimaryButton = next(), disabledPrimarySlider = next(), primaryContainer = next(),
        onPrimaryContainer = next(), secondary = next(), onSecondary = next(), secondaryVariant = next(),
        onSecondaryVariant = next(), disabledSecondary = next(), disabledOnSecondary = next(),
        disabledSecondaryVariant = next(), disabledOnSecondaryVariant = next(), secondaryContainer = next(),
        onSecondaryContainer = next(), secondaryContainerVariant = next(), onSecondaryContainerVariant = next(),
        tertiaryContainer = next(), onTertiaryContainer = next(), tertiaryContainerVariant = next(),
 background = next(), onBackground = next(), onBackgroundVariant = next(),
        surface = next(), onSurface = next(), surfaceVariant = next(), onSurfaceSecondary = next(),
        onSurfaceVariantSummary = next(), onSurfaceVariantActions = next(), disabledOnSurface = next(),
        surfaceContainer = next(), onSurfaceContainer = next(), onSurfaceContainerVariant = next(),
        surfaceContainerHigh = next(), onSurfaceContainerHigh = next(), surfaceContainerHighest = next(),
        onSurfaceContainerHighest = next(), outline = next(), dividerLine = next(), windowDimming = next(),
        sliderKeyPoint = next(), sliderKeyPointForeground = next(), sliderBackground = next(),
    )
}
