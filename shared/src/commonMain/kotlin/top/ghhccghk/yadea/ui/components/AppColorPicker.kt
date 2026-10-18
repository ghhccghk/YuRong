package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ColorPalette as MiuixColorPalette
import top.yukonga.miuix.kmp.basic.ColorPicker as MiuixColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace as MiuixColorSpace
import top.yukonga.miuix.kmp.window.WindowDialog

enum class AppColorPickerMode {
    Sliders,
    Grid,
}

@Composable
fun AppColorPickerDialog(
    show: Boolean,
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(show) { mutableStateOf(initialColor.copy(alpha = 1f)) }
    var hexInput by remember(show) { mutableStateOf(draft.toHexColor()) }
    var mode by remember(show) { mutableStateOf(AppColorPickerMode.Sliders) }
    val parsedHex = parseHexColor(hexInput)
    val canConfirm = parsedHex != null

    val applyColor: (Color) -> Unit = { color ->
        // 拾色过程中只改写弹窗内的草稿，确认后才写回全局主题，避免拖动时反复落库。
        draft = color.copy(alpha = 1f)
        hexInput = draft.toHexColor()
    }

    val pickerContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
            ) {
                AppFilterChip(
                    selected = mode == AppColorPickerMode.Sliders,
                    onClick = { mode = AppColorPickerMode.Sliders },
                    label = "滑条",
                    modifier = Modifier.weight(1f),
                )
                AppFilterChip(
                    selected = mode == AppColorPickerMode.Grid,
                    onClick = { mode = AppColorPickerMode.Grid },
                    label = "网格",
                    modifier = Modifier.weight(1f),
                )
            }

            // 两种模式都走 App* 语义组件：界面风格分支留在组件内部，
            // 因此 Material 3 模式下也不会再渲染 Miuix 原生拾色器。
            when (mode) {
                AppColorPickerMode.Sliders -> AppColorPicker(
                    color = draft,
                    onColorChanged = applyColor,
                    modifier = Modifier.fillMaxWidth(),
                )

                AppColorPickerMode.Grid -> AppColorPalette(
                    color = draft,
                    onColorChanged = applyColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AppTextField(
                value = hexInput,
                onValueChange = { value ->
                    hexInput = value
                    parseHexColor(value)?.let { parsed ->
                        draft = parsed.copy(alpha = 1f)
                    }
                },
                label = "十六进制颜色",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, AppDesign.colors.outline, CircleShape)
                        .background(draft, CircleShape)
                        .semantics { contentDescription = "当前颜色 " + draft.toHexColor() },
                )
                AppText(
                    text = if (canConfirm) draft.toHexColor() else "请输入 #RRGGBB",
                    style = AppDesign.typography.summary,
                    color = if (canConfirm) AppDesign.colors.onSurfaceVariant else AppDesign.colors.error,
                )
            }
        }
    }

    AppStyleContent(
        material = {
            if (show) {
                AlertDialog(
                    // modifier 与 Miuix 分支一样落在最外层弹窗上，保证两套风格的插槽位置一致。
                    modifier = modifier,
                    onDismissRequest = onDismissRequest,
                    title = { AppText("自定义主题色", style = AppDesign.typography.title) },
                    text = pickerContent,
                    confirmButton = {
                        AppButton(
                            onClick = { onConfirm(draft) },
                            enabled = canConfirm,
                        ) { AppText("确认") }
                    },
                    dismissButton = {
                        AppOutlinedButton(onClick = onDismissRequest) { AppText("取消") }
                    },
                )
            }
        },
        miuix = {
            WindowDialog(
                show = show,
                title = "自定义主题色",
                onDismissRequest = onDismissRequest,
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                ) {
                    pickerContent()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    ) {
                        AppOutlinedButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        ) { AppText("取消") }
                        AppButton(
                            onClick = { onConfirm(draft) },
                            enabled = canConfirm,
                            modifier = Modifier.weight(1f),
                        ) { AppText("确认") }
                    }
                }
            }
        },
    )
}

/** 渐变提示条高度：只做视觉参考，不参与触控。 */
private val ColorChannelGradientHeight = 6.dp

/** 当前颜色预览条高度。 */
private val ColorPreviewHeight = 24.dp

/**
 * 颜色滑条拾色器：用色相 / 饱和度 / 明度三个分量表达颜色。
 *
 * - Miuix 使用原生 [MiuixColorPicker]（HSV 滑条 + 原生预览）。
 * - Material 3 使用原生 Slider 组成同样的三个分量，颜色全部取自 [AppDesign] token。
 *
 * 两种风格都不持有颜色状态：当前颜色由调用方（取色器弹窗的草稿）持有，
 * 因此拖动过程中反复重组、或中途切换界面风格，都不会丢失已经选好的颜色。
 *
 * @param color 当前颜色；alpha 会被忽略，主题色始终不透明。
 * @param onColorChanged 颜色变化回调，拖动过程中实时触发。
 * @param modifier 应用于最外层可布局节点。
 * @param showPreview 是否展示当前颜色预览。
 */
@Composable
fun AppColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    val opaqueColor = color.copy(alpha = 1f)
    AppStyleContent(
        material = {
            MaterialColorPicker(
                color = opaqueColor,
                onColorChanged = onColorChanged,
                modifier = modifier,
                showPreview = showPreview,
            )
        },
        miuix = {
            MiuixColorPicker(
                color = opaqueColor,
                onColorChanged = onColorChanged,
                modifier = modifier,
                showPreview = showPreview,
                colorSpace = MiuixColorSpace.HSV,
            )
        },
    )
}

/**
 * 网格调色盘：在一张色格表里直接点选颜色。
 *
 * 两套风格共用同一份网格定义 [AppColorPaletteGrid]，因此同一个格子在两套风格下是同一个颜色：
 * Miuix 使用原生 [MiuixColorPalette]，Material 3 用 [AppDesign] token 自绘同构网格。
 *
 * @param color 当前颜色；用于高亮最接近的格子。
 * @param onColorChanged 点选色格时的回调。
 * @param modifier 应用于最外层可布局节点。
 * @param showPreview 是否展示当前颜色预览。
 */
@Composable
fun AppColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    val opaqueColor = color.copy(alpha = 1f)
    AppStyleContent(
        material = {
            MaterialColorPalette(
                color = opaqueColor,
                onColorChanged = onColorChanged,
                modifier = modifier,
                showPreview = showPreview,
            )
        },
        miuix = {
            MiuixColorPalette(
                color = opaqueColor,
                onColorChanged = onColorChanged,
                modifier = modifier,
                rows = AppColorPaletteGrid.Rows,
                hueColumns = AppColorPaletteGrid.HueColumns,
                includeGrayColumn = AppColorPaletteGrid.IncludeGrayColumn,
                showPreview = showPreview,
            )
        },
    )
}

/** Material 3 分支的滑条拾色器：三个分量各一根原生 Slider，外加随色相变化的渐变提示条。 */
@Composable
private fun MaterialColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    val hsv = color.toAppHsv()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
    ) {
        if (showPreview) {
            AppColorPreviewBar(color)
        }
        // 色相条是固定的彩虹渐变；饱和度与明度条随当前色相和明度变化，方便预判拖动结果。
        AppColorChannelSlider(
            label = "色相",
            valueText = hsv.hue.roundToInt().toString() + "°",
            value = hsv.hue,
            valueRange = 0f..360f,
            gradient = Brush.horizontalGradient(hueGradientStops()),
            onValueChange = { hue -> onColorChanged(appHsvToColor(hue, hsv.saturation, hsv.value)) },
        )
        AppColorChannelSlider(
            label = "饱和度",
            valueText = (hsv.saturation * 100f).roundToInt().toString() + "%",
            value = hsv.saturation,
            valueRange = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(
                    appHsvToColor(hsv.hue, 0f, hsv.value),
                    appHsvToColor(hsv.hue, 1f, hsv.value),
                ),
            ),
            onValueChange = { saturation ->
                onColorChanged(appHsvToColor(hsv.hue, saturation, hsv.value))
            },
        )
        AppColorChannelSlider(
            label = "明度",
            valueText = (hsv.value * 100f).roundToInt().toString() + "%",
            value = hsv.value,
            valueRange = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(
                    appHsvToColor(hsv.hue, hsv.saturation, 0f),
                    appHsvToColor(hsv.hue, hsv.saturation, 1f),
                ),
            ),
            onValueChange = { value ->
                onColorChanged(appHsvToColor(hsv.hue, hsv.saturation, value))
            },
        )
    }
}

/** 单个颜色分量：标题、当前值文本、渐变提示条与原生 Slider 自上而下排列。 */
@Composable
private fun AppColorChannelSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(text = label, style = AppDesign.typography.itemTitle)
            AppText(
                text = valueText,
                style = AppDesign.typography.value,
                color = AppDesign.colors.onSurfaceVariant,
            )
        }
        // 渐变条只是取值范围的视觉提示，真正的交互由下方原生 Slider 承担。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ColorChannelGradientHeight)
                .clip(CircleShape)
                .background(gradient),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 当前颜色预览条：只展示颜色本身，不带任何交互语义。 */
@Composable
private fun AppColorPreviewBar(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ColorPreviewHeight)
            .clip(AppDesign.shapes.item)
            .background(color)
            .border(1.dp, AppDesign.colors.outline, AppDesign.shapes.item)
            .semantics { contentDescription = "当前颜色 " + color.toHexColor() },
    )
}

/** Material 3 分支的网格调色盘：按 [AppColorPaletteGrid] 的行列定义自绘色格。 */
@Composable
private fun MaterialColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = true,
) {
    // 用最接近的格子表示当前颜色，避免出现“滑条选出来的颜色在网格里没有落点”的空状态。
    val selectedCell = AppColorPaletteGrid.nearestCell(color)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
    ) {
        if (showPreview) {
            AppColorPreviewBar(color)
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
            repeat(AppColorPaletteGrid.Rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing),
                ) {
                    repeat(AppColorPaletteGrid.ColumnCount) { column ->
                        val cellColor = AppColorPaletteGrid.colorAt(row, column)
                        AppColorPaletteCell(
                            color = cellColor,
                            selected = selectedCell == (row to column),
                            onClick = { onColorChanged(cellColor) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 单个色格：选中时用主色加粗描边，未选中时保留细描边保证浅色格子在浅色背景上仍可见。 */
@Composable
private fun AppColorPaletteCell(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(AppDesign.shapes.item)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    AppDesign.colors.previewOutline
                } else {
                    AppDesign.colors.previewOutline.copy(alpha = 0.35f)
                },
                shape = AppDesign.shapes.item,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "选择颜色 " + color.toHexColor() },
    )
}

/**
 * 网格调色盘的行列定义：两套风格共用，保证同一个格子对应同一个颜色。
 *
 * 网格为 7 行 × 12 个色相列，最后额外附一列灰阶；行越往下明度越低、饱和度越高，
 * 与 Miuix 原生调色盘的默认排布保持一致，切换界面风格时格子的含义不会变化。
 */
internal object AppColorPaletteGrid {
    /** 行数。 */
    const val Rows = 7

    /** 色相列数（不含灰阶列）。 */
    const val HueColumns = 12

    /** 是否附带灰阶列。 */
    const val IncludeGrayColumn = true

    /** 总列数；点击命中与布局都按它计算。 */
    val ColumnCount: Int = HueColumns + if (IncludeGrayColumn) 1 else 0

    // 7 行时的饱和度 / 明度排布，与 Miuix 原生调色板的默认值一致。
    private val Saturations = floatArrayOf(0.10f, 0.35f, 0.70f, 1.00f, 1.00f, 1.00f, 1.00f)
    private val Values = floatArrayOf(1.00f, 1.00f, 1.00f, 0.85f, 0.65f, 0.45f, 0.20f)

    /** 取指定格子的颜色；行或列越界时收敛到最近的合法格子。 */
    fun colorAt(row: Int, column: Int): Color {
        val safeRow = row.coerceIn(0, Rows - 1)
        val safeColumn = column.coerceIn(0, ColumnCount - 1)
        if (IncludeGrayColumn && safeColumn == ColumnCount - 1) {
            // 灰阶列固定色相与饱和度为 0，只按行降低明度。
            return appHsvToColor(0f, 0f, 1f - safeRow / (Rows - 1f))
        }
        val hue = safeColumn * 360f / HueColumns
        return appHsvToColor(hue, Saturations[safeRow], Values[safeRow])
    }

    /**
     * 找出与 [color] 最接近的格子，用于给当前颜色加选中描边。
     * 距离用 RGB 分量的平方和衡量；遍历顺序固定，出现并列时取靠上的行列。
     */
    fun nearestCell(color: Color): Pair<Int, Int> {
        var bestRow = 0
        var bestColumn = 0
        var bestDistance = Float.MAX_VALUE
        repeat(Rows) { row ->
            repeat(ColumnCount) { column ->
                val distance = colorDistance(color, colorAt(row, column))
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestRow = row
                    bestColumn = column
                }
            }
        }
        return bestRow to bestColumn
    }
}

/** HSV 分量：色相 0..360 度，饱和度与明度 0..1；alpha 不参与表达。 */
internal data class AppHsvColor(val hue: Float, val saturation: Float, val value: Float)

/** 把颜色拆成 HSV 分量；alpha 被忽略，主题色始终按不透明处理。 */
internal fun Color.toAppHsv(): AppHsvColor {
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    val rawHue = when {
        delta == 0f -> 0f
        maxChannel == red -> 60f * (((green - blue) / delta) % 6f)
        maxChannel == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return AppHsvColor(
        hue = if (rawHue < 0f) rawHue + 360f else rawHue,
        saturation = if (maxChannel == 0f) 0f else delta / maxChannel,
        value = maxChannel,
    )
}

/** 用 HSV 分量合成不透明颜色；分量越界时收敛到合法区间。 */
internal fun appHsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val secondary = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val match = v - chroma
    val channels = when {
        h < 60f -> Triple(chroma, secondary, 0f)
        h < 120f -> Triple(secondary, chroma, 0f)
        h < 180f -> Triple(0f, chroma, secondary)
        h < 240f -> Triple(0f, secondary, chroma)
        h < 300f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    return Color(
        red = channels.first + match,
        green = channels.second + match,
        blue = channels.third + match,
        alpha = 1f,
    )
}

/** 色相渐变条的停靠点：6 个主色相 + 回到起点的 360°，让渐变首尾闭合。 */
private fun hueGradientStops(): List<Color> = (0..6).map { step -> appHsvToColor(step * 60f, 1f, 1f) }

/** 颜色距离：RGB 分量的平方和，只用于在网格里挑最接近的格子。 */
private fun colorDistance(first: Color, second: Color): Float {
    val redDelta = first.red - second.red
    val greenDelta = first.green - second.green
    val blueDelta = first.blue - second.blue
    return redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta
}

@Composable
fun AppThemePresetSwatch(
    preset: AppThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(preset.seedColorArgb.toLong() and 0xFFFFFFFFL)
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = preset.label + " " + color.toHexColor() }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 38.dp else 32.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) AppDesign.colors.primary else AppDesign.colors.outline,
                    shape = CircleShape,
                )
                .background(color, CircleShape),
        )
    }
}

fun Color.toHexColor(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

fun parseHexColor(value: String): Color? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length != 6) return null
    val argb = normalized.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or argb) and 0xFFFFFFFFL)
}
