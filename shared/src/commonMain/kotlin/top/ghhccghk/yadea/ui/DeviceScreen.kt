package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设备信息页面
 */
@Composable
fun DeviceScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("设备信息", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仪表配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoRow("画布宽度", "960 px")
                    InfoRow("画布高度", "368 px")
                    InfoRow("旋转", "0°")
                    InfoRow("分包倍率", "1x")
                    InfoRow("默认包大小", "1024 B")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BLE 协议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoRow("MTU", "517")
                    InfoRow("包间隔", "18 ms")
                    InfoRow("最大重试", "20 次")
                    InfoRow("写入超时", "1500 ms")
                    InfoRow("重连延迟", "1000 ms")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("应用信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoRow("版本", "1.0.0")
                    InfoRow("平台", "Kotlin Multiplatform")
                    InfoRow("目标", "Android / iOS / Desktop / Web")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("协议命令", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    InfoRow("0x02", "导航结束")
                    InfoRow("0x12", "图像数据")
                    InfoRow("0x14", "导航提示")
                    InfoRow("0x1F", "心跳")
                    InfoRow("0x20", "导航开始")
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
