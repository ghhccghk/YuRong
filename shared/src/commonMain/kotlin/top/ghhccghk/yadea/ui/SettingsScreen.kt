package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    systemAccentColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    var showCustomColorPicker by remember { mutableStateOf(false) }
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    val dimensions = AppDesign.dimensions
    val pagePadding = PaddingValues(
        horizontal = if (useMiuix) 0.dp else dimensions.pageHorizontalPadding,
        vertical = if (useMiuix) 0.dp else dimensions.pageVerticalPadding,
    )
    val sectionModifier = if (useMiuix) {
        Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    AppColorPickerDialog(
        show = showCustomColorPicker,
        initialColor = appSeedColor(appearance, systemAccentColor),
        onConfirm = { color ->
            showCustomColorPicker = false
            onAppearanceChange(
                appearance.copy(
                    colorSource = AppColorSource.SeedColor,
                    seedColorArgb = color.toArgb(),
                    presetId = "custom",
                ),
            )
        },
        onDismissRequest = { showCustomColorPicker = false },
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = pagePadding,
        verticalArrangement = Arrangement.spacedBy(if (useMiuix) 8.dp else dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "设置",
                subtitle = "外观、主题颜色与显示模式",
            )
        }

        item {
            AppSection(title = "界面风格", modifier = sectionModifier) {
                AppSwitchPreference(
                    checked = appearance.uiStyle == UiThemeStyle.Miuix,
                    onCheckedChange = { enabled ->
                        onAppearanceChange(
                            appearance.copy(
                                uiStyle = if (enabled) UiThemeStyle.Miuix else UiThemeStyle.Material3,
                            ),
                        )
                    },
                    title = "Miuix 界面",
                    summary = "关闭后使用 Material 3",
                )
            }
        }

        item {
            AppSection(title = "明暗模式", modifier = sectionModifier) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        AppFilterChip(
                            selected = appearance.themeMode == mode,
                            onClick = { onAppearanceChange(appearance.copy(themeMode = mode)) },
                            label = mode.label,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            AppSection(title = "颜色来源", modifier = sectionModifier) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppFilterChip(
                        selected = appearance.colorSource == AppColorSource.SystemDynamic,
                        onClick = {
                            onAppearanceChange(appearance.copy(colorSource = AppColorSource.SystemDynamic))
                        },
                        label = "动态",
                        enabled = systemAccentColor != null,
                        modifier = Modifier.weight(1f),
                    )
                    AppFilterChip(
                        selected = appearance.colorSource == AppColorSource.SeedColor,
                        onClick = {
                            onAppearanceChange(appearance.copy(colorSource = AppColorSource.SeedColor))
                        },
                        label = "自定义",
                        modifier = Modifier.weight(1f),
                    )
                    AppFilterChip(
                        selected = appearance.colorSource == AppColorSource.Preset,
                        onClick = {
                            onAppearanceChange(appearance.copy(colorSource = AppColorSource.Preset))
                        },
                        label = "预设",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (systemAccentColor == null) {
                    AppListRow(
                        title = "动态颜色",
                        summary = "当前平台不支持，已回退到自定义颜色",
                    )
                }

                AppListRow(
                    title = "调色板",
                    value = appearance.paletteStyle.label,
                    onClick = {
                        val entries = AppPaletteStyle.entries
                        val next = entries[(entries.indexOf(appearance.paletteStyle) + 1) % entries.size]
                        onAppearanceChange(appearance.copy(paletteStyle = next))
                    },
                )
            }
        }

        item {
            AppSection(title = "主题颜色", modifier = sectionModifier) {
                if (appearance.colorSource == AppColorSource.Preset) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        items(AppThemePresets.all, key = { it.id }) { preset ->
                            AppThemePresetSwatch(
                                preset = preset,
                                selected = appearance.presetId == preset.id,
                                onClick = {
                                    onAppearanceChange(
                                        appearance.copy(
                                            colorSource = AppColorSource.Preset,
                                            presetId = preset.id,
                                            seedColorArgb = preset.seedColorArgb,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                AppListRow(
                    title = "当前颜色",
                    value = appSeedColor(appearance, systemAccentColor).toHexColor(),
                    onClick = { showCustomColorPicker = true },
                )
                AppListRow(
                    title = "自定义主题色",
                    summary = "支持 HSV 滑条、网格调色盘和十六进制输入",
                    value = "选择",
                    onClick = { showCustomColorPicker = true },
                )
            }
        }

        item {
            AppSection(title = "深色增强", modifier = sectionModifier) {
                AppSwitchPreference(
                    checked = appearance.amoledDarkEnabled,
                    onCheckedChange = {
                        onAppearanceChange(appearance.copy(amoledDarkEnabled = it))
                    },
                    title = "AMOLED 纯黑",
                    summary = "仅暗色模式生效，保持卡片和控件层次",
                )
                AppSwitchPreference(
                    checked = appearance.themeAnimationsEnabled,
                    onCheckedChange = {
                        onAppearanceChange(appearance.copy(themeAnimationsEnabled = it))
                    },
                    title = "主题切换动画",
                    summary = "在颜色方案变化时平滑过渡",
                )
            }
        }
    }
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.System -> "跟随系统"
        AppThemeMode.Light -> "亮色"
        AppThemeMode.Dark -> "暗色"
    }

private val AppPaletteStyle.label: String
    get() = when (this) {
        AppPaletteStyle.TonalSpot -> "Tonal Spot"
        AppPaletteStyle.Neutral -> "Neutral"
        AppPaletteStyle.Vibrant -> "Vibrant"
        AppPaletteStyle.Expressive -> "Expressive"
        AppPaletteStyle.Rainbow -> "Rainbow"
        AppPaletteStyle.FruitSalad -> "Fruit Salad"
        AppPaletteStyle.Monochrome -> "Monochrome"
        AppPaletteStyle.Fidelity -> "Fidelity"
        AppPaletteStyle.Content -> "Content"
    }
