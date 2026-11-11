package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.AtmosphereSettings
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SliderDefaults as MiuixSliderDefaults
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface

// Presentation order differs from the existing command values. Do not use list indices as payloads.
internal val atmosphereModeOptions = listOf(0 to "常亮", 2 to "闪烁", 1 to "呼吸", 3 to "流水")
internal val atmosphereRunOptions = listOf(
    0 to "正序", 1 to "逆序", 4 to "先正序后逆序",
    5 to "先逆序后正序", 2 to "中间到两边", 3 to "两边到中间",
)

/** Stateless editor: the caller owns the draft, write lifecycle and vehicle observations. */
@Composable
internal fun AtmosphereSettingsEditor(
    draft: CarSettingKind.Atmosphere,
    hasSelection: Boolean,
    enabled: Boolean,
    pending: Boolean,
    status: String,
    error: String?,
    onEdit: ((CarSettingKind.Atmosphere) -> CarSettingKind.Atmosphere) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    customizationSwitch: @Composable () -> Unit = {},
) {
    val dimensions = AppDesign.dimensions
    val colorEditable = draft.showMode == 1 || draft.showMode == 3
    Column(modifier.fillMaxWidth().heightIn(max = 640.dp)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            customizationSwitch()
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText("灯光模式", Modifier.weight(1f), style = AppDesign.typography.itemTitle)
                    AppText(
                        if (!hasSelection) "未选择" else if (colorEditable) "该模式单色/双色可选" else "该模式仅支持单色",
                        Modifier.weight(1.4f),
                        style = AppDesign.typography.caption,
                        color = AppDesign.colors.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    atmosphereModeOptions.forEach { (value, label) ->
                        AppAtmosphereOption(
                            label, hasSelection && draft.showMode == value, enabled,
                            onClick = { onEdit { it.copy(showMode = value) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (hasSelection && draft.showMode == 3) {
                Column(
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppText("灯光运行方式", style = AppDesign.typography.itemTitle)
                    atmosphereRunOptions.chunked(2).forEach { options ->
                        Row(horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing)) {
                            options.forEach { (value, label) ->
                                AppAtmosphereOption(
                                    label, draft.runMode == value, enabled,
                                    onClick = { onEdit { it.copy(runMode = value) } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText("灯光颜色", Modifier.weight(1f), style = AppDesign.typography.itemTitle)
                    if (colorEditable && hasSelection) {
                        AppSegmentedControl(
                            options = listOf("单色", "双色"),
                            selectedIndex = if (draft.singleColor) 0 else 1,
                            onSelected = { index -> onEdit { it.copy(singleColor = index == 0) } },
                            enabled = { enabled },
                            modifier = Modifier.widthIn(max = 168.dp),
                        )
                    }
                }
                AtmosphereDivider()
                // The original page shows a color preview, not hue editors, for steady/flashing modes.
                if (!hasSelection) {
                    AtmosphereColorLabel("颜色", null)
                } else if (colorEditable) {
                    AppAtmosphereColor(
                        label = if (draft.singleColor) "颜色" else "颜色A",
                        color = atmosphereRgb(draft.red1, draft.green1, draft.blue1),
                        enabled = enabled,
                        onColorChange = { color ->
                            val rgb = color.toArgb()
                            onEdit { it.copy(red1 = (rgb shr 16) and 255, green1 = (rgb shr 8) and 255, blue1 = rgb and 255) }
                        },
                    )
                    if (!draft.singleColor) {
                        AppAtmosphereColor(
                            label = "颜色B",
                            color = atmosphereRgb(draft.red2, draft.green2, draft.blue2),
                            enabled = enabled,
                            onColorChange = { color ->
                                val rgb = color.toArgb()
                                onEdit { it.copy(red2 = (rgb shr 16) and 255, green2 = (rgb shr 8) and 255, blue2 = rgb and 255) }
                            },
                        )
                    }
                } else {
                    AtmosphereColorLabel("颜色", atmosphereRgb(draft.red1, draft.green1, draft.blue1))
                }
            }
            AppText(status, style = AppDesign.typography.caption, color = AppDesign.colors.onSurfaceVariant)
            error?.let { AppText(it, style = AppDesign.typography.summary, color = AppDesign.colors.error) }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = dimensions.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
        ) {
            AtmosphereDivider()
            AppButton(
                onClick = onApply,
                enabled = enabled && hasSelection && AtmosphereSettings.valid(draft) && !pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                AppText(if (pending) "应用中…" else "应用", style = AppDesign.typography.button)
            }
        }
    }
}

@Composable
private fun AppAtmosphereOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val color = AppDesign.colors.surface
    val contentColor = AppDesign.colors.onSurface
    val tileModifier = modifier
        .alpha(if (enabled) 1f else 0.45f)
        .border(if (selected) 2.dp else 1.dp, if (selected) contentColor else AppDesign.colors.outline, shape)
        .semantics { this.selected = selected; role = Role.RadioButton }
    val content: @Composable () -> Unit = {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(label, style = AppDesign.typography.label, textAlign = TextAlign.Center)
        }
    }
    AppStyleContent(
        material = {
            Surface(
                onClick = onClick, modifier = tileModifier, enabled = enabled,
                shape = shape, color = color, contentColor = contentColor, content = content,
            )
        },
        miuix = { MiuixSurface(onClick = onClick, modifier = tileModifier, enabled = enabled, shape = shape, color = color, contentColor = contentColor, content = content) },
    )
}

@Composable
private fun AtmosphereDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppDesign.colors.divider))
}

@Composable
private fun AtmosphereColorLabel(label: String, color: Color?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, Modifier.weight(1f), style = AppDesign.typography.summary, color = AppDesign.colors.onSurfaceVariant)
        Box(
            Modifier.size(20.dp)
                .background(color ?: Color.Transparent, CircleShape)
                .border(1.dp, AppDesign.colors.outline, CircleShape)
                .semantics { contentDescription = "$label ${color?.toHexColor() ?: "UNKNOWN"}" },
        )
    }
}

private fun atmosphereRgb(red: Int, green: Int, blue: Int): Color? =
    if (red in 0..255 && green in 0..255 && blue in 0..255) Color(red, green, blue) else null

@Composable
private fun AppAtmosphereColor(label: String, color: Color?, enabled: Boolean, onColorChange: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
        AtmosphereColorLabel(label, color)
        AppAtmosphereSpectrumSlider(color, onColorChange, enabled, label, Modifier.fillMaxWidth())
    }
}

/** A hue track with a white endpoint, backed by each theme's native slider input/semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppAtmosphereSpectrumSlider(
    color: Color?,
    onColorChange: (Color) -> Unit,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val position = remember(color) { color?.let(AtmosphereSpectrum::positionOf) ?: 0f }
    val thumbColor = color ?: Color.Transparent
    val onPick by rememberUpdatedState(onColorChange)
    val outline = if (color == null) Color.Transparent else AppDesign.colors.outline
    val sliderModifier = Modifier.fillMaxWidth().semantics {
        contentDescription = label
        stateDescription = color?.toHexColor() ?: "UNKNOWN，尚未选择颜色"
    }
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(12.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .background(AtmosphereSpectrum.brush, CircleShape)
                .border(1.dp, AppDesign.colors.outline, CircleShape),
        )
        AppStyleContent(
            material = {
                Slider(
                    value = position,
                    onValueChange = { if (enabled) onPick(AtmosphereSpectrum.colorAt(it)) },
                    valueRange = 0f..1f,
                    enabled = enabled,
                    modifier = sliderModifier,
                    track = { Box(Modifier.fillMaxWidth().height(12.dp)) },
                    thumb = {
                        Box(Modifier.size(28.dp).alpha(if (enabled) 1f else 0.45f).background(thumbColor, CircleShape).border(2.dp, outline, CircleShape))
                    },
                )
            },
            miuix = {
                MiuixSlider(
                    value = position,
                    onValueChange = { if (enabled) onPick(AtmosphereSpectrum.colorAt(it)) },
                    valueRange = 0f..1f,
                    enabled = enabled,
                    // Miuix 0.9.4 handles dragging, but not taps on the track.
                    modifier = sliderModifier.pointerInput(enabled) {
                        if (enabled) detectTapGestures { point ->
                            val inset = 14.dp.toPx()
                            val fraction = (point.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)
                            onPick(AtmosphereSpectrum.colorAt(fraction))
                        }
                    },
                    height = 28.dp,
                    colors = MiuixSliderDefaults.sliderColors(
                        foregroundColor = Color.Transparent, backgroundColor = Color.Transparent,
                        disabledForegroundColor = Color.Transparent, disabledBackgroundColor = Color.Transparent,
                        thumbColor = thumbColor, disabledThumbColor = thumbColor.copy(alpha = if (color == null) 0f else 0.45f),
                    ),
                )
            },
        )
    }
}

// HueSliderView draws six 16%-wide hue segments; the remaining endpoint selects white.
// This is only picker geometry. RGB-to-command normalization stays in the existing command mapper.
internal object AtmosphereSpectrum {
    private val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    private const val HueEnd = 0.96f
    val brush = Brush.horizontalGradient(
        colorStops = (hues.mapIndexed { index, color -> index * 0.16f to color } +
            listOf(0.9601f to Color.White, 1f to Color.White)).toTypedArray(),
    )

    fun colorAt(position: Float): Color {
        val fraction = position.coerceIn(0f, 1f)
        if (fraction >= HueEnd) return Color(254, 254, 254)
        val segment = (fraction / 0.16f).toInt().coerceIn(0, 5)
        return lerp(hues[segment], hues[segment + 1], (fraction - segment * 0.16f) / 0.16f)
    }

    fun positionOf(color: Color): Float {
        if (color.red >= 250f / 255 && color.green >= 250f / 255 && color.blue >= 250f / 255) return 1f
        // Match the original setColor's nearest-RGB placement without altering the supplied draft.
        return (0..960).minBy { step ->
            val candidate = colorAt(step / 1000f)
            val dr = color.red - candidate.red
            val dg = color.green - candidate.green
            val db = color.blue - candidate.blue
            dr * dr + dg * dg + db * db
        } / 1000f
    }
}
