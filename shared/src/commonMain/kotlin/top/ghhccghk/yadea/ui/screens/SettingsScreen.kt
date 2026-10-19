package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.ZoomOut

@Composable
fun SettingsScreen(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    vehicleAutoConnect: Boolean = false,
    vehicleForegroundService: Boolean = true,
    onVehicleAutoConnectChange: (Boolean) -> Unit = {},
    onVehicleForegroundServiceChange: (Boolean) -> Unit = {},
    systemAccentColor: Color? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    var showCustomColorPicker by remember { mutableStateOf(false) }
    // 界面缩放只在拖动结束后写入设置，拖动过程中的值先放在本地草稿里做即时预览。
    var scaleDraft by remember(appearance.uiScale) { mutableStateOf(appearance.uiScale) }
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    // Miuix 模糊的运行时能力检查：Android 需要 API 33+（RuntimeShader）才能模糊，
    // 不支持时把开关置灰，避免用户打开一个永远没有效果的选项。
    val blurSupport = rememberAppBlurSupport()
    val dimensions = AppDesign.dimensions
    // 下拉菜单的选项文本与枚举 entries 一一对应，保证索引可直接回写到 AppAppearance。
    val uiStyleOptions = UiThemeStyle.entries.map { it.label }
    val paletteOptions = AppPaletteStyle.entries.map { it.label }
    val colorSpecOptions = AppColorSpec.entries.map { it.label }
    val pagePadding = contentPadding
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
            // 顶部主题区：手机缩略图预览 + 明暗模式分段选择。
            // 参考 KernelSU 主题设置页，把“预览”和“模式切换”收进同一视觉区块，切换前就能确认效果。
            Column(
                modifier = sectionModifier,
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppThemePreview(
                    styleLabel = appearance.uiStyle.label,
                    modeLabel = appearance.themeMode.label,
                )
                // 三种明暗模式互斥，用分段控件表达比开关更贴近设置页语义；
                // 分段控件不注册 nestedScroll，切换时不会让整页失去上下拖动能力。
                AppSegmentedControl(
                    options = AppThemeMode.entries.map { it.label },
                    selectedIndex = AppThemeMode.entries.indexOf(appearance.themeMode),
                    onSelected = { index ->
                        onAppearanceChange(appearance.copy(themeMode = AppThemeMode.entries[index]))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AppSection(title = "界面风格", modifier = sectionModifier) {
                // 主题切换使用列表弹窗：Miuix 走 SpinnerPreference 的 ListPopup，Material 3 走 DropdownMenu。
                AppPopupPreference(
                    title = "界面风格",
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.Theme,
                    summary = "Miuix 为 HyperOS 风格，Material 3 保留原生 Material 设计",
                    options = uiStyleOptions,
                    selectedIndex = UiThemeStyle.entries.indexOf(appearance.uiStyle),
                    onSelectedIndexChange = { index ->
                        onAppearanceChange(appearance.copy(uiStyle = UiThemeStyle.entries[index]))
                    },
                )
            }
        }
        item {
            AppSection(title = "车辆连接", modifier = sectionModifier) {
                AppSwitchPreference(
                    checked = vehicleAutoConnect,
                    onCheckedChange = onVehicleAutoConnectChange,
                    title = "启动后自动连接",
                    summary = "仅在同一账号和绑定 VIN 匹配时连接上次车辆",
                )
                AppSwitchPreference(
                    checked = vehicleForegroundService,
                    onCheckedChange = onVehicleForegroundServiceChange,
                    title = "前台服务保持连接",
                    summary = "车辆连接期间显示常驻通知，降低后台被系统回收的概率",
                )
            }
        }

        // Monet 与悬浮底栏都只作用于 Miuix，因此仅在 Miuix 风格下显示。
        if (useMiuix) {
            item {
                AppSection(title = "Miuix 配色", modifier = sectionModifier) {
                    AppSwitchPreference(
                        checked = appearance.miuixMonetEnabled,
                        onCheckedChange = {
                            onAppearanceChange(appearance.copy(miuixMonetEnabled = it))
                        },
                        title = "Monet 动态配色",
                        // 前置图标只做视觉引导，语义由标题承担。
                        leadingIcon = MiuixIcons.Tune,
                        summary = if (appearance.miuixMonetEnabled) {
                            "根据壁纸或种子色生成 Miuix 配色"
                        } else {
                            "已关闭，使用 Miuix 原生 HyperOS 配色"
                        },
                    )
                    if (appearance.miuixMonetEnabled) {
                        // 颜色标准只影响 Monet 生成的配色，关闭 Monet 后没有作用，因此跟随 Monet 开关显示。
                        AppPopupPreference(
                            title = "颜色标准",
                            // 前置图标只做视觉引导，语义由标题承担。
                            leadingIcon = MiuixIcons.Filter,
                            summary = "Spec 2025 支持 Tonal Spot、Neutral、Vibrant、Expressive 四种调色板",
                            options = colorSpecOptions,
                            selectedIndex = AppColorSpec.entries.indexOf(appearance.colorSpec),
                            onSelectedIndexChange = { index ->
                                onAppearanceChange(appearance.copy(colorSpec = AppColorSpec.entries[index]))
                            },
                        )
                    }
                }
            }
            item {
                AppSection(title = "Miuix 界面", modifier = sectionModifier) {
                    AppSwitchPreference(
                        checked = appearance.floatingNavigationBarEnabled,
                        onCheckedChange = {
                            onAppearanceChange(appearance.copy(floatingNavigationBarEnabled = it))
                        },
                        title = "悬浮底栏",
                        // 前置图标只做视觉引导，语义由标题承担。
                        leadingIcon = MiuixIcons.Layers,
                        summary = if (appearance.floatingNavigationBarEnabled) {
                            "底部导航使用 HyperOS 悬浮胶囊样式"
                        } else {
                            "使用常规底部导航栏"
                        },
                    )
                    AppSwitchPreference(
                        checked = appearance.blurEnabled,
                        onCheckedChange = {
                            onAppearanceChange(appearance.copy(blurEnabled = it))
                        },
                        title = "背景模糊",
                        // 前置图标只做视觉引导，语义由标题承担。
                        leadingIcon = MiuixIcons.Background,
                        // 平台不支持时禁用开关，并直接说明原因。
                        enabled = blurSupport.blurSupported,
                        summary = when {
                            !blurSupport.blurSupported ->
                                "当前平台或 Android 版本不支持 Miuix 模糊效果"
                            appearance.blurEnabled ->
                                "顶栏与底栏使用官方 miuix-blur 背景模糊"
                            else ->
                                "使用纯色顶栏与底栏"
                        },
                    )
                }
            }
        }

        item {
            AppSection(title = "界面缩放", modifier = sectionModifier) {
                // 缩放同时作用于布局尺寸与文字，两套风格共用同一份设置。
                AppSliderPreference(
                    title = "界面缩放",
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.ZoomOut,
                    summary = "同时缩放界面尺寸与文字，共 6 个档位",
                    value = scaleDraft,
                    onValueChange = { scaleDraft = it },
                    onValueChangeFinished = {
                        // 松手后吸附到最近的档位再落库，避免写入 109.9999% 这类中间值。
                        val committed = AppInterfaceScale.snap(scaleDraft)
                        scaleDraft = committed
                        if (committed != appearance.uiScale) {
                            onAppearanceChange(appearance.copy(uiScale = committed))
                        }
                    },
                    valueRange = AppInterfaceScale.range,
                    steps = AppInterfaceScale.STEPS,
                    valueText = AppInterfaceScale.label(scaleDraft),
                )
            }
        }

        item {
            AppSection(title = "颜色来源", modifier = sectionModifier) {
                // 三种来源互斥；系统不提供强调色时“动态”置灰，其余两项仍可切换。
                AppSegmentedControl(
                    options = AppColorSource.entries.map { it.label },
                    selectedIndex = AppColorSource.entries.indexOf(appearance.colorSource),
                    onSelected = { index ->
                        onAppearanceChange(appearance.copy(colorSource = AppColorSource.entries[index]))
                    },
                    enabled = { index ->
                        AppColorSource.entries[index] != AppColorSource.SystemDynamic ||
                            systemAccentColor != null
                    },
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                )

                if (systemAccentColor == null) {
                    AppListRow(
                        title = "动态颜色",
                        // 前置图标只做视觉引导，语义由标题承担。
                        leadingIcon = MiuixIcons.Photos,
                        summary = "当前平台不支持，已回退到自定义颜色",
                    )
                }

                // 调色板取值较多，改用列表弹窗选择，避免反复点击才能找到目标项。
                AppPopupPreference(
                    title = "调色板",
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.GridView,
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
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.Image,
                    value = appSeedColor(appearance, systemAccentColor).toHexColor(),
                    onClick = { showCustomColorPicker = true },
                )
                AppActionPreference(
                    title = "自定义主题色",
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.Edit,
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
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.Background,
                    summary = "仅暗色模式生效，Material 3 与 Miuix 的页面底色都会压成纯黑",
                )
                AppSwitchPreference(
                    checked = appearance.themeAnimationsEnabled,
                    onCheckedChange = {
                        onAppearanceChange(appearance.copy(themeAnimationsEnabled = it))
                    },
                    title = "主题切换动画",
                    // 前置图标只做视觉引导，语义由标题承担。
                    leadingIcon = MiuixIcons.Update,
                    summary = "在颜色方案变化时平滑过渡",
                )
            }
        }
    }
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.System -> "跟随系统"
        AppThemeMode.Light -> "浅色"
        AppThemeMode.Dark -> "深色"
    }

/** 颜色来源的显示名称，顺序与 [AppColorSource.entries] 一致。 */
private val AppColorSource.label: String
    get() = when (this) {
        AppColorSource.SystemDynamic -> "动态"
        AppColorSource.SeedColor -> "自定义"
        AppColorSource.Preset -> "预设"
    }

private val AppColorSpec.label: String
    get() = when (this) {
        AppColorSpec.Auto -> "跟随调色板"
        AppColorSpec.Spec2021 -> "Spec 2021"
        AppColorSpec.Spec2025 -> "Spec 2025"
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
