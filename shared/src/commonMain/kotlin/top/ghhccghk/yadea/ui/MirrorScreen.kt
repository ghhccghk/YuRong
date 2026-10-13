package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.mirror.FrameCropConfig
import top.ghhccghk.yadea.mirror.MirrorPreviewPanel
import top.ghhccghk.yadea.mirror.MirrorState

@Composable
fun MirrorScreen(viewModel: AppViewModel) {
    val mirrorState by viewModel.mirrorState.collectAsState()
    val mirrorStatus by viewModel.mirrorStatus.collectAsState()
    val frameCount by viewModel.frameCount.collectAsState()
    val bytesSent by viewModel.bytesSent.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val sourceCapabilities = viewModel.sourceCapabilities
    val sourceUri by viewModel.sourceUri.collectAsState()
    val cropConfig by viewModel.cropConfig.collectAsState()
    val p2pGroupOwnerIp by viewModel.p2pGroupOwnerIp.collectAsState()
    val wifiIp by viewModel.wifiIp.collectAsState()

    var targetHost by remember { mutableStateOf("") }
    var tcpPort by remember { mutableStateOf("8888") }
    var udpPort by remember { mutableStateOf("8889") }

    LaunchedEffect(p2pGroupOwnerIp, wifiIp) {
        if (p2pGroupOwnerIp.isNotEmpty()) {
            targetHost = p2pGroupOwnerIp
        } else if (wifiIp.isNotEmpty() && targetHost.isEmpty()) {
            targetHost = wifiIp.replace(Regex("\\.\\d+$"), ".1")
        }
    }

    val isMirroring = mirrorState == MirrorState.Running
    val dimensions = AppDesign.dimensions

    // 协商阶段的加载弹窗允许手动关闭，避免长时间等待时界面被完全锁死。
    var loadingDialogDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(mirrorState) {
        if (mirrorState == MirrorState.Negotiating) loadingDialogDismissed = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensions.pageHorizontalPadding,
            vertical = dimensions.pageVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "投屏控制",
                subtitle = mirrorStatus,
            )
        }

        if (mirrorState == MirrorState.Failed) {
            item {
                AppTip(
                    kind = AppTipKind.Error,
                    title = "投屏失败",
                    message = mirrorStatus,
                )
            }
        }

        item {
            AppSection(title = "仪表连接") {
                AppTextField(
                    value = targetHost,
                    onValueChange = { targetHost = it },
                    label = "仪表 IP 地址",
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.compactSpacing,
                    ),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.compactSpacing,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppTextField(
                        value = tcpPort,
                        onValueChange = { tcpPort = it },
                        label = "TCP 控制端口",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    AppTextField(
                        value = udpPort,
                        onValueChange = { udpPort = it },
                        label = "UDP 图片端口",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                if (p2pGroupOwnerIp.isNotEmpty()) {
                    AppListRow(title = "P2P GroupOwner", value = p2pGroupOwnerIp)
                }
                if (wifiIp.isNotEmpty()) {
                    AppListRow(title = "WiFi 本机", value = wifiIp)
                }
            }
        }

        item {
            AppSection(title = "投屏状态") {
                AppListRow(
                    title = "当前状态",
                    value = when (mirrorState) {
                        MirrorState.Running -> "投屏中"
                        MirrorState.Negotiating -> "连接中..."
                        MirrorState.Failed -> "失败"
                        MirrorState.Stopped -> "已停止"
                        MirrorState.Idle -> "空闲"
                    },
                    trailing = {
                        if (mirrorState == MirrorState.Negotiating) {
                            AppLoadingIndicator()
                        }
                    },
                )
                AppListRow(title = "运行详情", summary = mirrorStatus)
                if (isMirroring) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatItem("帧数", "$frameCount")
                        StatItem("已发送", formatBytes(bytesSent))
                        StatItem("帧率", "~10 fps")
                    }
                }
            }
        }

        item {
            AppText(
                text = "投屏源",
                style = AppDesign.typography.sectionTitle,
                color = AppDesign.colors.primary,
                modifier = Modifier.padding(horizontal = dimensions.itemHorizontalPadding),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                SourceButton(
                    text = "📷\n摄像头",
                    selected = selectedSource == 0,
                    modifier = Modifier.weight(1f),
                    enabled = sourceCapabilities.supports(0),
                    reason = sourceCapabilities.reason(0),
                ) { viewModel.selectSource(0) }
                SourceButton(
                    text = "📱\n屏幕",
                    selected = selectedSource == 1,
                    modifier = Modifier.weight(1f),
                    enabled = sourceCapabilities.supports(1),
                    reason = sourceCapabilities.reason(1),
                ) { viewModel.selectSource(1) }
                SourceButton(
                    text = "🎬\n视频",
                    selected = selectedSource == 2,
                    modifier = Modifier.weight(1f),
                    enabled = sourceCapabilities.supports(2),
                    reason = sourceCapabilities.reason(2),
                ) { viewModel.chooseVideoSource() }
                SourceButton(
                    text = "🗺\n导航",
                    selected = selectedSource == 3,
                    modifier = Modifier.weight(1f),
                    enabled = sourceCapabilities.supports(3),
                    reason = sourceCapabilities.reason(3),
                ) { viewModel.selectSource(3) }
            }
        }

        if (selectedSource == 2 || selectedSource == 3) {
            item {
                AppSection(title = if (selectedSource == 2) "视频源" else "导航源") {
                    AppTextField(
                        value = sourceUri,
                        onValueChange = viewModel::updateSourceUri,
                        label = if (selectedSource == 2) "视频文件或网络地址" else "导航图片流或视频地址",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                    )
                    if (selectedSource == 2 && viewModel.canPickVideoSource) {
                        AppButton(
                            onClick = viewModel::chooseVideoSource,
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = dimensions.itemHorizontalPadding,
                                vertical = dimensions.itemVerticalPadding,
                            ),
                        ) {
                            AppText("选择视频文件")
                        }
                    }
                }
            }
        }

        item {
            AppText(
                text = "投屏区域",
                style = AppDesign.typography.sectionTitle,
                color = AppDesign.colors.primary,
                modifier = Modifier.padding(horizontal = dimensions.itemHorizontalPadding),
            )
        }

        item {
            MirrorPreviewPanel(
                cropConfig = cropConfig,
                onCropChange = { viewModel.updateCrop(it) },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.compactSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                if (isMirroring) {
                    AppButton(
                        onClick = { viewModel.stopMirror() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        containerColor = AppDesign.colors.error,
                    ) {
                        AppText("⏹ 停止投屏", style = AppDesign.typography.button)
                    }
                } else {
                    AppButton(
                        onClick = {
                            viewModel.startMirror(
                                targetHost,
                                tcpPort.toIntOrNull() ?: 8888,
                                udpPort.toIntOrNull() ?: 8889,
                            )
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = targetHost.isNotEmpty() &&
                            (selectedSource != 2 && selectedSource != 3 || sourceUri.isNotEmpty()),
                    ) {
                        AppText("▶ 开始投屏", style = AppDesign.typography.button)
                    }
                }
            }
        }
    }

    AppLoadingDialog(
        show = mirrorState == MirrorState.Negotiating && !loadingDialogDismissed,
        title = "正在连接仪表",
        message = mirrorStatus,
        onDismissRequest = { loadingDialogDismissed = true },
    )
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(value, style = AppDesign.typography.sectionTitle)
        AppText(label, style = AppDesign.typography.summary)
    }
}

@Composable
fun SourceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reason: String? = null,
    onClick: () -> Unit,
) {
    AppCard(
        onClick = if (enabled) onClick else null,
        modifier = modifier,
        containerColor = if (selected) AppDesign.colors.primaryContainer else AppDesign.colors.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = AppDesign.dimensions.itemHorizontalPadding,
                vertical = AppDesign.dimensions.compactSpacing,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = text,
                style = AppDesign.typography.body,
                color = if (enabled) AppDesign.colors.onSurface else AppDesign.colors.onSurfaceVariant,
            )
            if (reason != null) {
                AppText(reason, style = AppDesign.typography.summary)
            }
        }
    }
}

private fun List<top.ghhccghk.yadea.mirror.FrameSourceCapability>.supports(index: Int): Boolean {
    val type = when (index) {
        0 -> top.ghhccghk.yadea.mirror.FrameSourceType.Camera
        1 -> top.ghhccghk.yadea.mirror.FrameSourceType.Screen
        2 -> top.ghhccghk.yadea.mirror.FrameSourceType.Video
        else -> top.ghhccghk.yadea.mirror.FrameSourceType.Navigation
    }
    return firstOrNull { it.type == type }?.supported ?: false
}

private fun List<top.ghhccghk.yadea.mirror.FrameSourceCapability>.reason(index: Int): String? {
    val type = when (index) {
        0 -> top.ghhccghk.yadea.mirror.FrameSourceType.Camera
        1 -> top.ghhccghk.yadea.mirror.FrameSourceType.Screen
        2 -> top.ghhccghk.yadea.mirror.FrameSourceType.Video
        else -> top.ghhccghk.yadea.mirror.FrameSourceType.Navigation
    }
    return firstOrNull { it.type == type }?.reason
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> {
            val mb = bytes * 10 / (1024 * 1024)
            "${mb / 10}.${mb % 10} MB"
        }
    }
}
