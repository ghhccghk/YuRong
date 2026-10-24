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

/**
 * 设置页。
 *
 * 页面只消费业务配置与回调，视觉差异由 App* 语义组件承担。宽屏时通过
 * [AppAdaptiveTwoColumn] 把“外观”和“连接/显示”拆成两栏，窄屏仍按阅读顺序单栏排列。
 */
@Composable
fun SettingsScreen(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    vehicleAutoConnect: Boolean = false,
    vehicleForegroundService: Boolean = true,
    cloudThemeColorOverride: Int? = null,
    detectedCloudThemeColorType: Int? = null,
    onCloudThemeColorOverrideChange: (Int?) -> Unit = {},
    vehicleBackgroundAvailable: Boolean = false,
    vehicleBackgroundSeedColor: Color? = null,
    onVehicleAutoConnectChange: (Boolean) -> Unit = {},
    onVehicleForegroundServiceChange: (Boolean) -> Unit = {},
    systemAccentColor: Color? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    var showCustomColorPicker by remember { mutableStateOf(false) }
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    // Miuix 模糊的运行时能力检查：Android 需要 API 33+（RuntimeShader）才能模糊，
    // 不支持时把开关置灰，避免用户打开一个永远没有效果的选项。
    val blurSupport = rememberAppBlurSupport()
    val dimensions = AppDesign.dimensions
    val haptics = rememberAppHaptics()

    AppColorPickerDialog(
        show = showCustomColorPicker,
        initialColor = appSeedColor(
            appearance,
            systemAccentColor,
            vehicleBackgroundSeedColor,
        ),
        onConfirm = { color ->
            showCustomColorPicker = false
            haptics.confirm()
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
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "设置",
                subtitle = "外观、主题颜色与显示模式",
            )
        }

        item {
            AppAdaptiveTwoColumn(
                columnSpacing = dimensions.sectionSpacing,
                rowSpacing = dimensions.sectionSpacing,
                primary = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
                    ) {
                        SettingsThemeSection(
                            appearance = appearance,
                            onAppearanceChange = onAppearanceChange,
                            haptics = haptics,
                        )
                        SettingsStyleSection(
                            appearance = appearance,
                            onAppearanceChange = onAppearanceChange,
                            haptics = haptics,
                        )
                        SettingsColorSourceSection(
                            appearance = appearance,
                            systemAccentColor = systemAccentColor,
                            onAppearanceChange = onAppearanceChange,
                            vehicleBackgroundAvailable = vehicleBackgroundAvailable,
                            haptics = haptics,
                        )
                        SettingsThemeColorSection(
                            appearance = appearance,
                            systemAccentColor = systemAccentColor,
                            vehicleBackgroundSeedColor = vehicleBackgroundSeedColor,
                            onAppearanceChange = onAppearanceChange,
                            onRequestCustomColor = {
                                haptics.contextClick()
                                showCustomColorPicker = true
                            },
                            haptics = haptics,
                        )
                        SettingsDarkEnhancementSection(
                            appearance = appearance,
                            onAppearanceChange = onAppearanceChange,
                            haptics = haptics,
                        )
                    }
                },
                secondary = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
                    ) {
                        SettingsVehicleThemeSection(
                            colorOverride = cloudThemeColorOverride,
                            detectedColorType = detectedCloudThemeColorType,
                            onColorOverrideChange = onCloudThemeColorOverrideChange,
                            haptics = haptics,
                        )
                        SettingsVehicleSection(
                            vehicleAutoConnect = vehicleAutoConnect,
                            vehicleForegroundService = vehicleForegroundService,
                            onVehicleAutoConnectChange = onVehicleAutoConnectChange,
                            onVehicleForegroundServiceChange = onVehicleForegroundServiceChange,
                            haptics = haptics,
                        )
                        if (useMiuix) {
                            SettingsMiuixColorSection(
                                appearance = appearance,
                                onAppearanceChange = onAppearanceChange,
                                haptics = haptics,
                            )
                            SettingsMiuixInterfaceSection(
                                appearance = appearance,
                                blurSupport = blurSupport,
                                onAppearanceChange = onAppearanceChange,
                                haptics = haptics,
                            )
                        }
                        SettingsScaleSection(
                            appearance = appearance,
                            onAppearanceChange = onAppearanceChange,
                            haptics = haptics,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsVehicleThemeSection(
    colorOverride: Int?,
    detectedColorType: Int?,
    onColorOverrideChange: (Int?) -> Unit,
    haptics: AppHaptics,
) {
    val options = listOf(
        "跟随云端",
        "冯·莱卡恩",
        "快人一布",
        "艾莲·乔",
    )
    val selectedIndex = colorOverride?.coerceIn(1, 3) ?: 0
    AppSection(title = "绝区零联动主题背景") {
        AppPopupPreference(
            title = "车辆联动角色",
            leadingIcon = MiuixIcons.Theme,
            summary = when {
                colorOverride != null ->
                    "本机外观覆盖，不会修改云端车辆主题背景"
                detectedColorType != null ->
                    "跟随云端：${zzzThemeLabel(detectedColorType)}"
                else ->
                    "跟随云端；尚未获取到车辆的联动主题背景"
            },
            options = options,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { index ->
                haptics.perform(AppHapticKind.SegmentTick)
                onColorOverrideChange(index.takeIf { it in 1..3 })
            },
        )
    }
}

@Composable
private fun SettingsThemeSection(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    val dimensions = AppDesign.dimensions
    // 顶部主题区：手机缩略图预览 + 明暗模式分段选择。
    // 参考 KernelSU 主题设置页，把“预览”和“模式切换”收进同一视觉区块，切换前就能确认效果。
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                haptics.perform(AppHapticKind.SegmentTick)
                onAppearanceChange(appearance.copy(themeMode = AppThemeMode.entries[index]))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsStyleSection(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    val uiStyleOptions = UiThemeStyle.entries.map { it.label }
    AppSection(title = "界面风格") {
        // 主题切换使用列表弹窗：Miuix 走 SpinnerPreference 的 ListPopup，Material 3 走 DropdownMenu。
        AppPopupPreference(
            title = "界面风格",
            // 前置图标只做视觉引导，语义由标题承担。
            leadingIcon = MiuixIcons.Theme,
            summary = "Miuix 为 HyperOS 风格，Material 3 为 Material 3 设计",
            options = uiStyleOptions,
            selectedIndex = UiThemeStyle.entries.indexOf(appearance.uiStyle),
            onSelectedIndexChange = { index ->
                haptics.perform(AppHapticKind.SegmentTick)
                onAppearanceChange(appearance.copy(uiStyle = UiThemeStyle.entries[index]))
            },
        )
    }
}

@Composable
private fun SettingsVehicleSection(
    vehicleAutoConnect: Boolean,
    vehicleForegroundService: Boolean,
    onVehicleAutoConnectChange: (Boolean) -> Unit,
    onVehicleForegroundServiceChange: (Boolean) -> Unit,
    haptics: AppHaptics,
) {
    AppSection(title = "车辆连接") {
        AppSwitchPreference(
            checked = vehicleAutoConnect,
            onCheckedChange = {
                haptics.toggle(it)
                onVehicleAutoConnectChange(it)
            },
            title = "启动后自动连接",
            summary = "仅在同一账号和绑定 VIN 匹配时连接上次车辆",
        )
        AppSwitchPreference(
            checked = vehicleForegroundService,
            onCheckedChange = {
                haptics.toggle(it)
                onVehicleForegroundServiceChange(it)
            },
            title = "前台服务保持连接",
            summary = "车辆连接期间显示常驻通知，降低后台被系统回收的概率",
        )
    }
}

@Composable
private fun SettingsMiuixColorSection(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    val colorSpecOptions = AppColorSpec.entries.map { it.label }
    AppSection(title = "Miuix 配色") {
        AppSwitchPreference(
            checked = appearance.miuixMonetEnabled,
            onCheckedChange = {
                haptics.toggle(it)
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
                    haptics.perform(AppHapticKind.SegmentTick)
                    onAppearanceChange(appearance.copy(colorSpec = AppColorSpec.entries[index]))
                },
            )
        }
    }
}

@Composable
private fun SettingsMiuixInterfaceSection(
    appearance: AppAppearance,
    blurSupport: AppBlurSupport,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    AppSection(title = "Miuix 界面") {
        AppSwitchPreference(
            checked = appearance.floatingNavigationBarEnabled,
            onCheckedChange = {
                haptics.toggle(it)
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
                haptics.toggle(it)
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

@Composable
private fun SettingsScaleSection(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    // 界面缩放只在拖动结束后写入设置，拖动过程中的值先放在本地草稿里做即时预览。
    var scaleDraft by remember(appearance.uiScale) { mutableStateOf(appearance.uiScale) }
    AppSection(title = "界面缩放") {
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
                haptics.gestureEnd()
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

@Composable
private fun SettingsColorSourceSection(
    appearance: AppAppearance,
    systemAccentColor: Color?,
    onAppearanceChange: (AppAppearance) -> Unit,
    vehicleBackgroundAvailable: Boolean,
    haptics: AppHaptics,
) {
    val dimensions = AppDesign.dimensions
    val paletteOptions = AppPaletteStyle.entries.map { it.label }
    AppSection(title = "颜色来源") {
        // 三种来源互斥；系统不提供强调色时“动态”置灰，其余两项仍可切换。
        AppSegmentedControl(
            options = AppColorSource.entries.map { it.label },
            selectedIndex = AppColorSource.entries.indexOf(appearance.colorSource),
            onSelected = { index ->
                haptics.perform(AppHapticKind.SegmentTick)
                onAppearanceChange(appearance.copy(colorSource = AppColorSource.entries[index]))
            },
            enabled = { index ->
                when (AppColorSource.entries[index]) {
                    AppColorSource.SystemDynamic -> systemAccentColor != null
                    AppColorSource.VehicleBackground -> vehicleBackgroundAvailable
                    else -> true
                }
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
        if (!vehicleBackgroundAvailable) {
            AppListRow(
                title = "车辆背景色",
                leadingIcon = MiuixIcons.Image,
                summary = "当前车辆没有可用背景图，暂时不能从背景提取主色",
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
                haptics.perform(AppHapticKind.SegmentTick)
                onAppearanceChange(appearance.copy(paletteStyle = AppPaletteStyle.entries[index]))
            },
        )
    }
}

@Composable
private fun SettingsThemeColorSection(
    appearance: AppAppearance,
    systemAccentColor: Color?,
    vehicleBackgroundSeedColor: Color?,
    onAppearanceChange: (AppAppearance) -> Unit,
    onRequestCustomColor: () -> Unit,
    haptics: AppHaptics,
) {
    val dimensions = AppDesign.dimensions
    AppSection(title = "主题颜色") {
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
                            haptics.contextClick()
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
            value = appSeedColor(
                appearance,
                systemAccentColor,
                vehicleBackgroundSeedColor,
            ).toHexColor(),
            onClick = onRequestCustomColor,
        )
        if (appearance.colorSource == AppColorSource.VehicleBackground) {
            AppListRow(
                title = "背景种子色",
                leadingIcon = MiuixIcons.Image,
                value = vehicleBackgroundSeedColor?.toHexColor() ?: "提取中",
                summary = if (vehicleBackgroundSeedColor == null) {
                    "正在分析当前车辆背景图，失败时会回退到原自定义主色"
                } else {
                    "颜色来自当前车辆背景图中占比最高的鲜艳色"
                },
            )
        }
        AppActionPreference(
            title = "自定义主题色",
            // 前置图标只做视觉引导，语义由标题承担。
            leadingIcon = MiuixIcons.Edit,
            summary = "支持 HSV 滑条、网格调色盘和十六进制输入",
            value = "选择",
            onClick = onRequestCustomColor,
        )
    }
}

@Composable
private fun SettingsDarkEnhancementSection(
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    haptics: AppHaptics,
) {
    AppSection(title = "深色增强") {
        AppSwitchPreference(
            checked = appearance.amoledDarkEnabled,
            onCheckedChange = {
                haptics.toggle(it)
                onAppearanceChange(appearance.copy(amoledDarkEnabled = it))
            },
            title = "OLED 纯黑",
            // 前置图标只做视觉引导，语义由标题承担。
            leadingIcon = MiuixIcons.Background,
            summary = "仅暗色模式生效，Material 3 与 Miuix 的页面底色都会压成纯黑",
        )
        AppSwitchPreference(
            checked = appearance.themeAnimationsEnabled,
            onCheckedChange = {
                haptics.toggle(it)
                onAppearanceChange(appearance.copy(themeAnimationsEnabled = it))
            },
            title = "主题切换动画",
            // 前置图标只做视觉引导，语义由标题承担。
            leadingIcon = MiuixIcons.Update,
            summary = "在颜色方案变化时平滑过渡",
        )
    }
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.System -> "跟随系统"
        AppThemeMode.Light -> "浅色"
        AppThemeMode.Dark -> "深色"
    }

private fun zzzThemeLabel(colorType: Int?): String = when (colorType) {
    1 -> "冯·莱卡恩"
    2 -> "邦布"
    3 -> "艾莲·乔"
    else -> "未识别角色"
}

/** 颜色来源的显示名称，顺序与 [AppColorSource.entries] 一致。 */
private val AppColorSource.label: String
    get() = when (this) {
        AppColorSource.SystemDynamic -> "动态"
        AppColorSource.SeedColor -> "自定义"
        AppColorSource.Preset -> "预设"
        AppColorSource.VehicleBackground -> "爱车背景"
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
