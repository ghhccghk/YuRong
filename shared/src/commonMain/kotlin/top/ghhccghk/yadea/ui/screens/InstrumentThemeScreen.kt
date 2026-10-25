package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.instrumentskin.InstrumentSkinPreset

@Composable
fun InstrumentThemeScreen(
    controller: InstrumentThemeController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val dimensions = AppDesign.dimensions
    val pickerAvailable = controller.canPickImages
    val controlsEnabled = pickerAvailable && !state.busy

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
    ) {
        item {
            AppTip(
                kind = AppTipKind.Warning,
                title = "请先连接仪表 Wi-Fi",
                message = "壁纸和皮肤都通过仪表 TCP 8880 直传。AP 模式通常是 192.168.7.1；" +
                    "P2P 模式使用群主地址。请先停止投屏，仪表同一时间只允许一个数据会话。",
            )
        }
        if (!pickerAvailable) {
            item {
                AppTip(
                    kind = AppTipKind.Info,
                    title = "当前平台暂不支持图片管线",
                    message = "Android 与 Desktop 提供图片选择、重绘和上传；其他平台会明确禁用本页操作。",
                )
            }
        }
        item {
            AppSection(title = "仪表连接") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppTextField(
                        value = state.targetHost,
                        onValueChange = controller::updateTargetHost,
                        label = "仪表 IP",
                        enabled = controlsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(
                        text = "端口固定 8880。自动识别到 P2P 群主地址时会替换默认的 192.168.7.1。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            AppSection(title = "仪表壁纸") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppText(
                        text = "壁纸会先读取仪表尺寸和图片格式，再按该尺寸直接拉伸并分块上传。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                    AppListRow(
                        title = "壁纸图片",
                        summary = state.wallpaperSource?.let(::sourceLabel) ?: "尚未选择",
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                    ) {
                        AppOutlinedButton(
                            onClick = controller::chooseWallpaper,
                            enabled = controlsEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText("选择图片", style = AppDesign.typography.button)
                        }
                        AppButton(
                            onClick = controller::startWallpaper,
                            enabled = controlsEnabled && !state.wallpaperSource.isNullOrBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText("设置壁纸", style = AppDesign.typography.button)
                        }
                    }
                }
            }
        }
        item {
            AppSection(title = "仪表皮肤") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppSegmentedControl(
                        options = InstrumentSkinPreset.entries.map { it.label },
                        selectedIndex = InstrumentSkinPreset.entries.indexOf(state.preset),
                        onSelected = { controller.selectPreset(InstrumentSkinPreset.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = { controlsEnabled },
                    )
                    if (state.preset != InstrumentSkinPreset.BackgroundOnly) {
                        ImageSourceRow(
                            title = "小圈图片",
                            source = state.portraitSource,
                            enabled = controlsEnabled,
                            onPick = controller::choosePortrait,
                        )
                    }
                    if (state.preset != InstrumentSkinPreset.PortraitOnly) {
                        ImageSourceRow(
                            title = "背景图片",
                            source = state.backgroundSource,
                            enabled = controlsEnabled,
                            onPick = controller::chooseBackground,
                        )
                    }
                    AppButton(
                        onClick = controller::startSkin,
                        enabled = controlsEnabled && skinSourcesReady(state),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText("生成并设置皮肤", style = AppDesign.typography.button)
                    }
                    AppText(
                        text = "皮肤会按原 App 的 20 帧姿态表重绘，固定用于 800×480 仪表；" +
                            "生成包最大 332496 字节。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            AppSection(title = "任务状态") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppTip(
                        kind = when {
                            state.error != null -> AppTipKind.Error
                            !state.busy && state.status.contains("成功") -> AppTipKind.Success
                            else -> AppTipKind.Info
                        },
                        title = state.status,
                        message = state.error,
                    )
                    if (state.busy) {
                        AppLinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppOutlinedButton(
                            onClick = controller::cancelCurrent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText("停止任务", style = AppDesign.typography.button)
                        }
                    }
                }
            }
        }
        item {
            AppText(
                text = "仪表确认生效前，请保持页面在前台并确保手机与仪表 Wi-Fi 连接稳定。",
                style = AppDesign.typography.summary,
                color = AppDesign.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensions.compactSpacing),
            )
        }
    }
}

@Composable
private fun ImageSourceRow(
    title: String,
    source: String?,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing),
    ) {
        AppListRow(
            title = title,
            summary = source?.let(::sourceLabel) ?: "尚未选择",
            modifier = Modifier.weight(1f),
        )
        AppOutlinedButton(
            onClick = onPick,
            enabled = enabled,
        ) {
            AppText("选择", style = AppDesign.typography.button)
        }
    }
}

private fun skinSourcesReady(state: InstrumentThemeState): Boolean = when (state.preset) {
    InstrumentSkinPreset.PortraitOnly -> !state.portraitSource.isNullOrBlank()
    InstrumentSkinPreset.BackgroundOnly -> !state.backgroundSource.isNullOrBlank()
    InstrumentSkinPreset.PortraitAndBackground ->
        !state.portraitSource.isNullOrBlank() && !state.backgroundSource.isNullOrBlank()
}

private fun sourceLabel(source: String): String =
    source.substringAfterLast('/').substringAfterLast('\\').ifBlank { source }
