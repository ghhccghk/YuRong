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
    // 下拉菜单的选项文本与枚举 entries 一一对应，保证索引可直接回写到 AppAppearance。
    val uiStyleOptions = UiThemeStyle.entries.map { it.label }
    val paletteOptions = AppPaletteStyle.entries.map { it.label }
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
                // 主题切换使用列表弹窗：Miuix 走 SpinnerPreference 的 ListPopup，Material 3 走 DropdownMenu。
                AppPopupPreference(
                    title = "界面风格",
                    summary = "Miuix 为 HyperOS 风格，Material 3 保留原生 Material 设计",
                    options = uiStyleOptions,
                    selectedIndex = UiThemeStyle.entries.indexOf(appearance.uiStyle),
                    onSelectedIndexChange = { index ->
                        onAppearanceChange(appearance.copy(uiStyle = UiThemeStyle.entries[index]))
                    },
                )
            }
        }

        // Monet 只影响 Miuix 配色，因此仅在 Miuix 风格下显示。
        if (useMiuix) {
            item {
                AppSection(title = "Miuix 配色", modifier = sectionModifier) {
                    AppSwitchPreference(
                        checked = appearance.miuixMonetEnabled,
                        onCheckedChange = {
                            onAppearanceChange(appearance.copy(miuixMonetEnabled = it))
                        },
                        title = "Monet 动态配色",
                        summary = if (appearance.miuixMonetEnabled) {
                            "根据壁纸或种子色生成 Miuix 配色"
                        } else {
                            "已关闭，使用 Miuix 原生 HyperOS 配色"
                        },
                    )
                }
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

                // 调色板取值较多，改用列表弹窗选择，避免反复点击才能找到目标项。
                AppPopupPreference(
                    title = "调色板",
                    options = paletteOptions,
                    selectedIndex = AppPaletteStyle.entries.indexOf(appearance.paletteStyle),
                    onSelectedIndexChange = { index ->
                        onAppearanceChange(appearance.copy(paletteStyle = AppPaletteStyle.entries[index]))
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

                AppActionPreference(
                    title = "当前颜色",
                    value = appSeedColor(appearance, systemAccentColor).toHexColor(),
                    onClick = { showCustomColorPicker = true },
                )
                AppActionPreference(
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
