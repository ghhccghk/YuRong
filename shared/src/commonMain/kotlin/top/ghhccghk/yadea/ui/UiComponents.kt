package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = AppDesign.shapes.section,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppStyleContent(
        material = {
            val colors = if (containerColor == null && contentColor == null) {
                CardDefaults.cardColors()
            } else {
                CardDefaults.cardColors(
                    containerColor = containerColor ?: AppDesign.colors.surfaceContainer,
                    contentColor = contentColor ?: AppDesign.colors.onSurfaceContainer,
                )
            }
            if (onClick == null) {
                Card(modifier = modifier, shape = shape, colors = colors) { Column(Modifier.padding(contentPadding), content = content) }
            } else {
                Card(modifier = modifier, shape = shape, colors = colors, onClick = onClick) { Column(Modifier.padding(contentPadding), content = content) }
            }
        },
        miuix = {
            val resolvedContentColor = contentColor ?: MiuixTheme.colorScheme.onSurfaceContainer
            val colors = MiuixCardDefaults.defaultColors(
                color = containerColor ?: MiuixTheme.colorScheme.surfaceContainer,
                contentColor = resolvedContentColor,
            )
            if (onClick == null) {
                MiuixCard(modifier = modifier, insideMargin = contentPadding, cornerRadius = AppDesign.dimensions.cardCornerRadius, colors = colors) {
                    CompositionLocalProvider(
                        LocalContentColor provides resolvedContentColor,
                        MiuixLocalContentColor provides resolvedContentColor,
                    ) { content() }
                }
            } else {
                MiuixCard(
                    modifier = modifier,
                    insideMargin = contentPadding,
                    cornerRadius = AppDesign.dimensions.cardCornerRadius,
                    colors = colors,
                    onClick = onClick,
                    showIndication = true,
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides resolvedContentColor,
                        MiuixLocalContentColor provides resolvedContentColor,
                    ) { content() }
                }
            }
        },
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppDesign.shapes.button,
    containerColor: Color? = null,
    contentColor: Color? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    AppStyleContent(
        material = {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor ?: AppDesign.colors.primary,
                    contentColor = contentColor ?: AppDesign.colors.onPrimary,
                ),
                shape = shape,
                contentPadding = contentPadding ?: PaddingValues(
                    horizontal = AppDesign.dimensions.buttonInsideHorizontal,
                    vertical = AppDesign.dimensions.buttonInsideVertical,
                ),
                content = content,
            )
        },
        miuix = {
            val resolvedContentColor = contentColor ?: MiuixTheme.colorScheme.onPrimary
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColorsPrimary(
                    color = containerColor ?: MiuixTheme.colorScheme.primary,
                    contentColor = resolvedContentColor,
                ),
                cornerRadius = AppDesign.dimensions.buttonCornerRadius,
                insideMargin = contentPadding ?: PaddingValues(
                    horizontal = AppDesign.dimensions.buttonInsideHorizontal,
                    vertical = AppDesign.dimensions.buttonInsideVertical,
                ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides resolvedContentColor,
                    MiuixLocalContentColor provides resolvedContentColor,
                ) { content() }
            }
        },
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = AppDesign.shapes.button,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    AppStyleContent(
        material = {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                contentPadding = contentPadding ?: PaddingValues(
                    horizontal = AppDesign.dimensions.buttonInsideHorizontal,
                    vertical = AppDesign.dimensions.buttonInsideVertical,
                ),
                content = content,
            )
        },
        miuix = {
            val resolvedContentColor = MiuixTheme.colorScheme.onSecondaryVariant
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColors(),
                cornerRadius = AppDesign.dimensions.buttonCornerRadius,
                insideMargin = contentPadding ?: PaddingValues(
                    horizontal = AppDesign.dimensions.buttonInsideHorizontal,
                    vertical = AppDesign.dimensions.buttonInsideVertical,
                ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides resolvedContentColor,
                    MiuixLocalContentColor provides resolvedContentColor,
                ) { content() }
            }
        },
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = AppDesign.shapes.field,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    AppStyleContent(
        material = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                shape = shape,
                enabled = enabled,
                label = { Text(label) },
                singleLine = singleLine,
            )
        },
        miuix = {
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                cornerRadius = AppDesign.dimensions.fieldCornerRadius,
                enabled = enabled,
                label = label,
                singleLine = singleLine,
            )
        },
    )
}

internal data class ResolvedSliderState(
    val rangeStart: Float,
    val rangeEnd: Float,
    val value: Float,
    val submitStart: Float,
    val submitEnd: Float,
) {
    val valueRange: ClosedFloatingPointRange<Float>
        get() = rangeStart..rangeEnd

    fun submitValue(value: Float): Float =
        value.coerceIn(submitStart, submitEnd)
}

internal fun resolveSliderState(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): ResolvedSliderState {
    val requestedStart = minOf(valueRange.start, valueRange.endInclusive)
    val requestedEnd = maxOf(valueRange.start, valueRange.endInclusive)
    val requestedValue = value.coerceIn(requestedStart, requestedEnd)

    if (requestedEnd > requestedStart) {
        return ResolvedSliderState(
            rangeStart = requestedStart,
            rangeEnd = requestedEnd,
            value = requestedValue,
            submitStart = requestedStart,
            submitEnd = requestedEnd,
        )
    }

    val halfSpan = maxOf(kotlin.math.abs(requestedValue) * 0.000001f, 0.000001f)
    return ResolvedSliderState(
        rangeStart = requestedValue - halfSpan,
        rangeEnd = requestedValue + halfSpan,
        value = requestedValue,
        submitStart = requestedValue,
        submitEnd = requestedValue,
    )
}


@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sliderState = resolveSliderState(value, valueRange)
    val submitValue: (Float) -> Unit = { rawValue ->
        onValueChange(sliderState.submitValue(rawValue))
    }

    AppStyleContent(
        material = {
            Slider(
                value = sliderState.value,
                onValueChange = submitValue,
                valueRange = sliderState.valueRange,
                modifier = modifier,
                enabled = enabled,
            )
        },
        miuix = {
            MiuixSlider(
                value = sliderState.value,
                onValueChange = submitValue,
                valueRange = sliderState.valueRange,
                modifier = modifier,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    AppStyleContent(
        material = {
            FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label) },
                modifier = modifier,
            )
        },
        miuix = {
            AppButton(
                onClick = onClick,
                modifier = modifier,
                containerColor = if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.secondaryVariant,
                contentColor = if (selected) MiuixTheme.colorScheme.onPrimary
                else MiuixTheme.colorScheme.onSecondaryVariant,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label, style = AppDesign.typography.label)
            }
        },
    )
}

@Composable
fun AppStatusChip(connected: Boolean, text: String) {
    AppStyleContent(
        material = {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = text,
                        color = if (connected) AppDesign.colors.primary
                        else AppDesign.colors.outline,
                    )
                },
                leadingIcon = { Text(if (connected) "●" else "○") },
            )
        },
        miuix = {
            Text(
                text = if (connected) "● $text" else "○ $text",
                color = if (connected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = AppDesign.typography.summary,
            )
        },
    )
}

@Composable
fun AppSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    AppStyleContent(
        material = {
            Row(
                modifier = modifier.fillMaxWidth().padding(contentPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(title, style = AppDesign.typography.sectionTitle)
                    if (summary != null) {
                        AppText(summary, style = AppDesign.typography.summary)
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
        },
        miuix = {
            MiuixSwitchPreference(
                checked = checked,
                onCheckedChange = onCheckedChange,
                title = title,
                summary = summary,
                modifier = modifier,
            )
        },
    )
}
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = AppDesign.typography.body,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
) {
    AppStyleContent(
        material = {
            Text(
                text = text,
                modifier = modifier,
                color = color,
                style = style,
                fontWeight = fontWeight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = textAlign,
                overflow = overflow,
                maxLines = maxLines,
            )
        },
        miuix = {
            MiuixText(
                text = text,
                modifier = modifier,
                color = color,
                style = style,
                fontWeight = fontWeight,
                fontSize = fontSize,
                lineHeight = lineHeight,
                textAlign = textAlign,
                overflow = overflow,
                maxLines = maxLines,
            )
        },
    )
}
@Composable
fun ScreenTitle(title: String) {
    if (AppDesign.style == UiThemeStyle.Material3) {
        Text(
            text = title,
            style = AppDesign.typography.pageTitle,
            color = AppDesign.colors.onBackground,
        )
    }
}
