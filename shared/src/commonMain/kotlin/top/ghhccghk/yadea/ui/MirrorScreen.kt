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

/**
 * 投屏控制页面 - 连接实际 MirrorEngine
 */
@Composable
fun MirrorScreen(viewModel: AppViewModel) {
    val mirrorState by viewModel.mirrorState.collectAsState()
    val mirrorStatus by viewModel.mirrorStatus.collectAsState()
    val frameCount by viewModel.frameCount.collectAsState()
    val bytesSent by viewModel.bytesSent.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val cropConfig by viewModel.cropConfig.collectAsState()

    val isMirroring = mirrorState == MirrorState.Running

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("投屏控制", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // 状态卡片
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
                        onClick = { viewModel.startMirror("192.168.1.100", 8080) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) { Text("▶ 开始投屏", fontSize = 18.sp) }
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

