package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem as MiuixDropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference as MiuixCheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference as MiuixSpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference as MiuixSliderPreference
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
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
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
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
    enabled: Boolean = true,
) {
    AppStyleContent(
        material = {
            FilterChip(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                label = { Text(label) },
                modifier = modifier,
            )
        },
        miuix = {
            AppButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
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

/**
 * 分段选择控件：在一行内互斥选择，用于明暗模式、连接方式等“多选一”场景。
 *
 * 这里刻意不使用两套原生的 TabRow：Miuix 的 TabRow 内部注册了 nestedScroll 连接器，
 * 会把纵向滚动事件全部吞并，放在可滚动页面里会导致整页无法上下拖动。
 * 改为 Row + 各自原生容器（Material 3 的 SegmentedButton、Miuix 的 Surface）后，
 * 选中语义、按压反馈与禁用状态保持一致，同时不再干扰父级滚动。
 *
 * @param options 选项文案，顺序与 [selectedIndex] 一一对应。
 * @param selectedIndex 当前选中项索引；越界时视为全部未选中。
 * @param onSelected 选中新选项时回调；重复选中当前项不会触发。
 * @param enabled 逐项可用性判断，例如“动态”在系统不提供强调色时需要禁用。
 * @param modifier 应用于最外层可布局节点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (Int) -> Boolean = { true },
) {
    val dimensions = AppDesign.dimensions
    AppStyleContent(
        material = {
            SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { if (index != selectedIndex) onSelected(index) },
                        enabled = enabled(index),
                        // 首尾两端的圆角由 Material 3 的 itemShape 统一处理。
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        modifier = Modifier.weight(1f),
                        // 默认的对勾图标会挤占文字宽度，这里只保留文字，与 Miuix 分支保持一致。
                        icon = {},
                        label = {
                            Text(text = option, style = AppDesign.typography.label, maxLines = 1)
                        },
                    )
                }
            }
        },
        miuix = {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    val itemEnabled = enabled(index)
                    // 选中项用主色填充，未选中项用次级容器色，禁用项统一降级为灰色。
                    val itemColor = when {
                        !itemEnabled -> MiuixTheme.colorScheme.disabledSecondaryVariant
                        selected -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.secondaryVariant
                    }
                    val itemContentColor = when {
                        !itemEnabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        selected -> MiuixTheme.colorScheme.onPrimary
                        else -> MiuixTheme.colorScheme.onSecondaryVariant
                    }
                    MiuixSurface(
                        onClick = { if (!selected) onSelected(index) },
                        enabled = itemEnabled,
                        shape = AppDesign.shapes.item,
                        color = itemColor,
                        contentColor = itemContentColor,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimensions.buttonInsideHorizontal,
                                    vertical = dimensions.buttonInsideVertical,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            MiuixText(
                                text = option,
                                style = AppDesign.typography.label,
                                color = itemContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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

/** 设置项左侧图标的尺寸，与两套风格的标题字号保持视觉平衡。 */
private val PreferenceLeadingIconSize = 20.dp

/**
 * 两套风格共用的装饰性前置图标（设置项、列表行左侧）。
 *
 * 图标的含义已由同一行的标题表达，因此 contentDescription 固定为 null，
 * 避免无障碍服务重复朗读；需要独立语义的图标请改用 [AppIconButton]。
 * 这里只依赖语义色 token，因此 Miuix 与 Material 3 下渲染结果一致。
 */
@Composable
internal fun AppPreferenceLeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AppDesign.colors.onSurfaceVariant,
        modifier = Modifier.size(PreferenceLeadingIconSize),
    )
}

/**
 * Miuix 分支的装饰性前置图标，作为 preference 的 startAction 插槽内容。
 *
 * [icon] 为空时不渲染任何内容，Miuix 的插槽会自动收敛为 0 宽度且不占用间距。
 */
@Composable
private fun MiuixPreferenceLeadingIcon(icon: ImageVector?) {
    if (icon == null) return
    MiuixIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        modifier = Modifier.size(PreferenceLeadingIconSize),
    )
}

@Composable
fun AppSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
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
                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧图标只做视觉引导，语义由标题承担。
                if (leadingIcon != null) {
                    AppPreferenceLeadingIcon(leadingIcon)
                }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(title, style = AppDesign.typography.sectionTitle)
                    if (summary != null) {
                        AppText(summary, style = AppDesign.typography.summary)
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            }
        },
        miuix = {
            MiuixSwitchPreference(
                checked = checked,
                onCheckedChange = onCheckedChange,
                title = title,
                summary = summary,
                // 前置图标走 Miuix 原生 startAction 插槽，位置与 Material 分支一致。
                startAction = { MiuixPreferenceLeadingIcon(leadingIcon) },
                modifier = modifier,
                enabled = enabled,
            )
        },
    )
}

/**
 * 带标题与摘要的复选框设置项，对应 Miuix 的 CheckboxPreference 与 Material 3 的 Checkbox。
 * 两种风格下整行均可点击，回调时机与启用状态保持一致。
 */
@Composable
fun AppCheckboxPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    AppStyleContent(
        material = {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Checkbox 自身不处理点击，统一交给整行的 toggleable，避免重复的点击语义。
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                )
                Column(modifier = Modifier.weight(1f)) {
                    AppText(title, style = AppDesign.typography.sectionTitle)
                    if (summary != null) {
                        AppText(summary, style = AppDesign.typography.summary)
                    }
                }
            }
        },
        miuix = {
            MiuixCheckboxPreference(
                title = title,
                summary = summary,
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = modifier,
            )
        },
    )
}

/**
 * 带标题与摘要的滑条设置项，对应 Miuix 的 SliderPreference 与 Material 3 的 Slider。
 *
 * 两种风格都把滑条放在标题/摘要下方，并且保持相同的回调时机：
 * 拖动过程中持续回调 [onValueChange]（用于即时预览），拖动结束后再回调
 * [onValueChangeFinished]（用于落库），避免每一帧都触发设置写入。
 *
 * @param title 设置项标题。
 * @param value 当前值，超出 [valueRange] 时会被收敛。
 * @param valueRange 取值区间。
 * @param valueText 可选的当前值文本，显示在标题行末尾；为空时不显示。
 * @param steps 端点之间的离散档位数，0 表示连续取值；与两套原生 Slider 的语义一致。
 * @param onValueChangeFinished 拖动结束回调，可为空。
 * @param contentPadding 内容内边距，默认沿用 [AppDesign] 的行内边距。
 */
@Composable
fun AppSliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leadingIcon: ImageVector? = null,
    valueText: String? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    // 复用 AppSlider 的收敛规则：退化区间与越界值都先归一化，两套风格拿到的是同一份值。
    val sliderState = resolveSliderState(value, valueRange)
    val submitValue: (Float) -> Unit = { rawValue ->
        onValueChange(sliderState.submitValue(rawValue))
    }
    val safeSteps = steps.coerceAtLeast(0)

    AppStyleContent(
        material = {
            Column(
                modifier = modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧图标只做视觉引导，语义由标题承担。
                    if (leadingIcon != null) {
                        AppPreferenceLeadingIcon(leadingIcon)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(title, style = AppDesign.typography.itemTitle)
                        if (summary != null) {
                            AppText(summary, style = AppDesign.typography.summary)
                        }
                    }
                    if (valueText != null) {
                        AppText(valueText, style = AppDesign.typography.value)
                    }
                }
                Slider(
                    value = sliderState.value,
                    onValueChange = submitValue,
                    valueRange = sliderState.valueRange,
                    steps = safeSteps,
                    enabled = enabled,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        miuix = {
            // Miuix 的 SliderPreference 自带标题、摘要与滑条排布，滑条位于标题下方。
            MiuixSliderPreference(
                title = title,
                summary = summary,
                // 前置图标走 Miuix 原生 startAction 插槽，位置与 Material 分支一致。
                startAction = { MiuixPreferenceLeadingIcon(leadingIcon) },
                value = sliderState.value,
                onValueChange = submitValue,
                modifier = modifier,
                enabled = enabled,
                valueRange = sliderState.valueRange,
                steps = safeSteps,
                valueText = valueText,
                onValueChangeFinished = onValueChangeFinished,
            )
        },
    )
}

/**
 * 列表弹窗选择型设置项：整行可点击，点击后从列表弹窗中选择一项。
 *
 * Miuix 风格使用原生 [MiuixSpinnerPreference]，其弹层即 ListPopup；
 * Material 3 风格使用原生 DropdownMenu 承担同样的“从列表中选择”语义。
 * 两种风格都保持整行点击、启用状态和提交时机一致。
 *
 * @param options 选项文本列表，索引与 [selectedIndex] 一一对应。
 * @param selectedIndex 当前选中项索引，越界时视为未选中（只展示标题与摘要）。
 * @param onSelectedIndexChange 选中新选项时回调；重复选中当前项不会触发，避免多余写入。
 */
@Composable
fun AppPopupPreference(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    val selectedOption = options.getOrNull(selectedIndex)
    AppStyleContent(
        material = {
            var expanded by remember { mutableStateOf(false) }
            // modifier 应用在最外层可布局节点，保证与 Miuix 分支的插槽位置一致。
            Box(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = { expanded = true },
                        )
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧图标只做视觉引导，语义由标题承担。
                    if (leadingIcon != null) {
                        AppPreferenceLeadingIcon(leadingIcon)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = title,
                            style = AppDesign.typography.itemTitle,
                            color = if (enabled) AppDesign.colors.onSurface else AppDesign.colors.onSurfaceVariant,
                        )
                        if (summary != null) {
                            AppText(
                                text = summary,
                                style = AppDesign.typography.summary,
                                color = AppDesign.colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (selectedOption != null) {
                        AppText(
                            text = selectedOption,
                            style = AppDesign.typography.value,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = index == selectedIndex
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = if (selected) AppDesign.colors.primary
                                    else AppDesign.colors.onSurface,
                                )
                            },
                            onClick = {
                                expanded = false
                                if (!selected) onSelectedIndexChange(index)
                            },
                            // Material 3 没有 Miuix 的勾选图标，用文本勾号保持同一选中语义。
                            leadingIcon = if (selected) {
                                { Text(text = "\u2713", color = AppDesign.colors.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
        miuix = {
            MiuixSpinnerPreference(
                items = options.map { option -> MiuixDropdownItem(text = option) },
                selectedIndex = selectedIndex,
                title = title,
                summary = summary,
                // 前置图标走 Miuix 原生 startAction 插槽，位置与 Material 分支一致。
                startAction = { MiuixPreferenceLeadingIcon(leadingIcon) },
                enabled = enabled,
                modifier = modifier,
                onSelectedIndexChange = { index ->
                    if (index != selectedIndex) onSelectedIndexChange(index)
                },
            )
        },
    )
}

/**
 * 动作/跳转型设置项：整行可点击，右侧展示当前值与“可继续操作”的箭头。
 *
 * Miuix 风格使用原生 [MiuixArrowPreference]（自带箭头与按压反馈）；
 * Material 3 风格沿用与 AppListRow 一致的点击行，并补上箭头提示。
 */
@Composable
fun AppActionPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leadingIcon: ImageVector? = null,
    value: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    AppStyleContent(
        material = {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧图标只做视觉引导，语义由标题承担。
                if (leadingIcon != null) {
                    AppPreferenceLeadingIcon(leadingIcon)
                }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = title,
                        style = AppDesign.typography.itemTitle,
                        color = if (enabled) AppDesign.colors.onSurface else AppDesign.colors.onSurfaceVariant,
                    )
                    if (summary != null) {
                        AppText(
                            text = summary,
                            style = AppDesign.typography.summary,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    }
                }
                if (value != null) {
                    AppText(
                        text = value,
                        style = AppDesign.typography.value,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
                // Material 3 没有独立箭头组件，用字符箭头保持同一视觉语义。
                AppText(
                    text = "\u203A",
                    style = AppDesign.typography.value,
                    color = AppDesign.colors.outline,
                )
            }
        },
        miuix = {
            MiuixArrowPreference(
                title = title,
                summary = summary,
                // 前置图标走 Miuix 原生 startAction 插槽，位置与 Material 分支一致。
                startAction = { MiuixPreferenceLeadingIcon(leadingIcon) },
                modifier = modifier,
                enabled = enabled,
                onClick = onClick,
                endActions = {
                    if (value != null) {
                        AppText(
                            text = value,
                            style = AppDesign.typography.value,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    )
}

/**
 * 图标按钮：用于顶栏等紧凑位置的单一操作。
 *
 * 两种风格各自使用原生 IconButton；图标由调用方以 [ImageVector] 传入，
 * 业务页面因此不需要直接依赖某一套风格的图标集。
 *
 * @param icon 图标资源，两套风格共用同一个 [ImageVector]。
 * @param contentDescription 无障碍描述，说明按钮代表的操作。
 * @param onClick 点击回调。
 * @param enabled 是否可用；禁用时两套风格都保持不可点击。
 * @param tint 图标颜色；为 [Color.Unspecified] 时继承所在容器的内容色。
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    AppStyleContent(
        material = {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
                )
            }
        },
        miuix = {
            MiuixIconButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    // Miuix 的内容色是独立的 CompositionLocal，需要单独取值。
                    tint = if (tint == Color.Unspecified) MiuixLocalContentColor.current else tint,
                )
            }
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

/**
 * 设置项「标题 + 可选摘要」文案块，供输入型设置项的两套风格复用。
 *
 * 这里只输出两段文本，不负责布局，因此可以直接放进各自的 Column 里，
 * 保证 Miuix 与 Material 3 的标题层级、颜色规则完全一致。
 */
@Composable
private fun PreferenceTitleBlock(
    title: String,
    summary: String?,
    enabled: Boolean,
) {
    AppText(
        text = title,
        style = AppDesign.typography.itemTitle,
        color = if (enabled) AppDesign.colors.onSurface else AppDesign.colors.onSurfaceVariant,
    )
    if (summary != null) {
        AppText(
            text = summary,
            style = AppDesign.typography.summary,
            color = AppDesign.colors.onSurfaceVariant,
        )
    }
}

/**
 * 带标题与摘要的文本输入设置项。
 *
 * Material 3 分支使用原生 `OutlinedTextField`（经 [AppTextField] 转发），
 * Miuix 分支把原生 [MiuixTextField] 放进 [MiuixBasicComponent] 的内容槽，
 * 因此两套风格都保留各自原生的输入框外观、聚焦动画与键盘行为。
 *
 * 输入内容由调用方持有（受控组件）：每次键入都会回调 [onValueChange]，由调用方决定是否落库；
 * 两套风格的提交时机与启用状态完全一致。
 *
 * @param title 设置项标题。
 * @param value 当前文本。
 * @param onValueChange 文本变化回调。
 * @param summary 次级说明文案；为空时不渲染。
 * @param label 输入框的浮动标签文案；为空时输入框不显示标签。
 * @param leadingIcon 可选的装饰性前置图标。
 * @param enabled 是否可编辑。
 * @param singleLine 是否单行输入。
 * @param contentPadding 内容内边距，默认沿用 [AppDesign] 的行内边距。
 */
@Composable
fun AppTextInputPreference(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String = "",
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppDesign.dimensions.itemHorizontalPadding,
        vertical = AppDesign.dimensions.itemVerticalPadding,
    ),
) {
    AppStyleContent(
        material = {
            Column(
                modifier = modifier.fillMaxWidth().padding(contentPadding),
                // 标题与输入框之间保持紧凑间距，与其他设置项的行高节奏一致。
                verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左侧图标只做视觉引导，语义由标题承担。
                    if (leadingIcon != null) {
                        AppPreferenceLeadingIcon(leadingIcon)
                    }
                    Column { PreferenceTitleBlock(title = title, summary = summary, enabled = enabled) }
                }
                AppTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
        },
        miuix = {
            MiuixBasicComponent(
                modifier = modifier,
                // 前置图标走 Miuix 原生 startAction 插槽，位置与 Material 分支一致。
                startAction = { MiuixPreferenceLeadingIcon(leadingIcon) },
                insideMargin = contentPadding,
            ) {
                PreferenceTitleBlock(title = title, summary = summary, enabled = enabled)
                MiuixTextField(
                    value = value,
                    onValueChange = onValueChange,
                    // Miuix 的 BasicComponent 内容槽自带 Column，间距只能由子项自己补。
                    modifier = Modifier.fillMaxWidth().padding(top = AppDesign.dimensions.compactSpacing),
                    cornerRadius = AppDesign.dimensions.fieldCornerRadius,
                    enabled = enabled,
                    label = label,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
        },
    )
}
