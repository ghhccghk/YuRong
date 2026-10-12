package top.ghhccghk.yadea.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

            when (mode) {
                AppColorPickerMode.Sliders -> MiuixColorPicker(
                    color = draft,
                    onColorChanged = { color ->
                        draft = color.copy(alpha = 1f)
                        hexInput = draft.toHexColor()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    showPreview = true,
                    colorSpace = MiuixColorSpace.HSV,
                )

                AppColorPickerMode.Grid -> MiuixColorPalette(
                    color = draft,
                    onColorChanged = { color ->
                        draft = color.copy(alpha = 1f)
                        hexInput = draft.toHexColor()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    showPreview = true,
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
                        .semantics { contentDescription = "当前颜色 ${draft.toHexColor()}" },
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
            .semantics { contentDescription = "${preset.label} ${color.toHexColor()}" }
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
