package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val cropConfig by viewModel.cropConfig.collectAsState()
    val p2pGroupOwnerIp by viewModel.p2pGroupOwnerIp.collectAsState()
    val wifiIp by viewModel.wifiIp.collectAsState()
    val debugLog by viewModel.debugLog.collectAsState()

    var targetHost by remember { mutableStateOf("") }
    var tcpPort by remember { mutableStateOf("8888") }
    var udpPort by remember { mutableStateOf("8889") }

    LaunchedEffect(p2pGroupOwnerIp, wifiIp) {
        if (p2pGroupOwnerIp.isNotEmpty()) targetHost = p2pGroupOwnerIp
        else if (wifiIp.isNotEmpty() && targetHost.isEmpty()) {
            targetHost = wifiIp.replace(Regex("\\.\\d+$"), ".1")
        }
    }

    val isMirroring = mirrorState == MirrorState.Running

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle("投屏控制")
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("仪表连接", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    AppTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = "仪表 IP 地址",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        AppTextField(
                            value = tcpPort,
                            onValueChange = { tcpPort = it },
                            label = "TCP 控制端口",
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        AppTextField(
                            value = udpPort,
                            onValueChange = { udpPort = it },
                            label = "UDP 图片端口",
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    if (p2pGroupOwnerIp.isNotEmpty()) {
                        AppText("P2P GroupOwner: $p2pGroupOwnerIp", style = AppDesign.typography.summary, color = AppDesign.colors.primary)
                    }
                    if (wifiIp.isNotEmpty()) {
                        AppText("WiFi 本机: $wifiIp", style = AppDesign.typography.summary, color = AppDesign.colors.secondary)
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        AppText("投屏状态", style = AppDesign.typography.title)
                        StatusChip(isMirroring, when (mirrorState) {
                            MirrorState.Running -> "投屏中"
                            MirrorState.Negotiating -> "连接中..."
                            MirrorState.Failed -> "失败"
                            MirrorState.Stopped -> "已停止"
                            else -> "空闲"
                        })
                    }
                    AppText(mirrorStatus, style = AppDesign.typography.body)
                    if (isMirroring) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            StatItem("帧数", "$frameCount")
                            StatItem("已发送", formatBytes(bytesSent))
                            StatItem("帧率", "~10 fps")
                        }
                    }
                }
            }
        }

        item {
            AppText("投屏源", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                SourceButton("📷\n摄像头", selectedSource == 0, Modifier.weight(1f)) { viewModel.selectSource(0) }
                SourceButton("📱\n屏幕", selectedSource == 1, Modifier.weight(1f)) { viewModel.selectSource(1) }
                SourceButton("🎬\n视频", selectedSource == 2, Modifier.weight(1f)) { viewModel.selectSource(2) }
                SourceButton("🗺\n导航", selectedSource == 3, Modifier.weight(1f)) { viewModel.selectSource(3) }
            }
        }

        item {
            AppText("投屏区域", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
        }
        item {
            MirrorPreviewPanel(
                cropConfig = cropConfig,
                onCropChange = { viewModel.updateCrop(it) }
            )
        }

        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.spacedBy(12.dp)) {
                if (isMirroring) {
                    AppButton(
                        onClick = { viewModel.stopMirror() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        containerColor = AppDesign.colors.error
                    ) { AppText("⏹ 停止投屏", fontSize = 18.sp) }
                } else {
                    AppButton(
                        onClick = {
                            viewModel.startMirror(
                                targetHost,
                                tcpPort.toIntOrNull() ?: 8888,
                                udpPort.toIntOrNull() ?: 8889
                            )
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = targetHost.isNotEmpty()
                    ) { AppText("▶ 开始投屏", fontSize = 18.sp) }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText("调试日志", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    debugLog.reversed().take(15).forEach { line ->
                        AppText(line, fontSize = 10.sp, lineHeight = 13.sp, color = AppDesign.colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppText(value, style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
        AppText(label, style = AppDesign.typography.summary)
    }
}

@Composable
fun SourceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = modifier,
        containerColor = if (selected) AppDesign.colors.primaryContainer else AppDesign.colors.surfaceVariant) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppText(text, style = AppDesign.typography.body)
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> {
            val mb = (bytes * 10 / (1024 * 1024))
            "${mb / 10}.${mb % 10} MB"
        }
    }
}
