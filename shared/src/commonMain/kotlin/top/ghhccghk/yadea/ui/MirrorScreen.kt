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
import top.ghhccghk.yadea.mirror.MirrorPreviewPanel

/**
 * 投屏控制页面
 */
@Composable
fun MirrorScreen() {
    var selectedSource by remember { mutableStateOf(0) }
    var isMirroring by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("待命") }
    var frameCount by remember { mutableIntStateOf(0) }
    var bytesSent by remember { mutableLongStateOf(0L) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("投屏控制", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("投屏状态", style = MaterialTheme.typography.titleLarge)
                        StatusChip(isMirroring, if (isMirroring) "投屏中" else "空闲")
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    if (isMirroring) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("帧数", "$frameCount")
                            StatItem("已发送", formatBytes(bytesSent))
                            StatItem("帧率", "~10 fps")
                        }
                    }
                }
            }
        }

        item {
            Text("投屏源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceButton("📷\n摄像头", selectedSource == 0, Modifier.weight(1f)) { selectedSource = 0 }
                SourceButton("📱\n屏幕", selectedSource == 1, Modifier.weight(1f)) { selectedSource = 1 }
                SourceButton("🎬\n视频", selectedSource == 2, Modifier.weight(1f)) { selectedSource = 2 }
                SourceButton("🗺\n导航", selectedSource == 3, Modifier.weight(1f)) { selectedSource = 3 }
            }
        }

        item {
            Text("投屏区域", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MirrorPreviewPanel(
                        onCropChange = { }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isMirroring) {
                    Button(
                        onClick = { isMirroring = false; status = "已停止" },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("⏹ 停止投屏", fontSize = 18.sp) }
                } else {
                    Button(
                        onClick = { isMirroring = true; status = "投屏进行中..." },
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
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}


