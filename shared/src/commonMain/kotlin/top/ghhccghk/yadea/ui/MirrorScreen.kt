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
    var targetPort by remember { mutableStateOf("8080") }

    // 自动填充目标地址
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
            Text("投屏控制", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // 连接目标
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仪表连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("仪表 IP 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text("端口") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (p2pGroupOwnerIp.isNotEmpty()) {
                        Text("P2P GroupOwner IP: $p2pGroupOwnerIp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (wifiIp.isNotEmpty()) {
                        Text("WiFi 本机 IP: $wifiIp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // 状态
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("投屏状态", style = MaterialTheme.typography.titleLarge)
                        StatusChip(isMirroring,
                            when (mirrorState) {
                                MirrorState.Running -> "投屏中"
                                MirrorState.Negotiating -> "连接中..."
                                MirrorState.Failed -> "失败"
                                MirrorState.Stopped -> "已停止"
                                else -> "空闲"
                            })
                    }
                    Text(mirrorStatus, style = MaterialTheme.typography.bodyMedium)
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

        // 投屏源
        item {
            Text("投屏源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                SourceButton("📷\n摄像头", selectedSource == 0, Modifier.weight(1f)) { viewModel.selectSource(0) }
                SourceButton("📱\n屏幕", selectedSource == 1, Modifier.weight(1f)) { viewModel.selectSource(1) }
                SourceButton("🎬\n视频", selectedSource == 2, Modifier.weight(1f)) { viewModel.selectSource(2) }
                SourceButton("🗺\n导航", selectedSource == 3, Modifier.weight(1f)) { viewModel.selectSource(3) }
            }
        }

        // 投屏区域
        item {
            Text("投屏区域", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            MirrorPreviewPanel(
                cropConfig = cropConfig,
                onCropChange = { viewModel.updateCrop(it) }
            )
        }

        // 控制按钮
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.spacedBy(12.dp)) {
                if (isMirroring) {
                    Button(
                        onClick = { viewModel.stopMirror() },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("⏹ 停止投屏", fontSize = 18.sp) }
                } else {
                    Button(
                        onClick = {
                            viewModel.startMirror(targetHost, targetPort.toIntOrNull() ?: 8080)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        enabled = targetHost.isNotEmpty()
                    ) { Text("▶ 开始投屏", fontSize = 18.sp) }
                }
            }
        }

        // Debug 日志
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("调试日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    debugLog.reversed().take(15).forEach { line ->
                        Text(line, fontSize = 10.sp, lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SourceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
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
