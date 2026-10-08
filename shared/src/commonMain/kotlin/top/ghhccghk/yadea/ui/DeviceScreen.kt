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
            ScreenTitle("设备信息")
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("仪表配置", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    InfoRow("画布宽度", "960 px")
                    InfoRow("画布高度", "368 px")
                    InfoRow("旋转", "0°")
                    InfoRow("分包倍率", "1x")
                    InfoRow("默认包大小", "1024 B")
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("BLE 协议", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    InfoRow("MTU", "517")
                    InfoRow("包间隔", "18 ms")
                    InfoRow("最大重试", "20 次")
                    InfoRow("写入超时", "1500 ms")
                    InfoRow("重连延迟", "1000 ms")
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("应用信息", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    InfoRow("版本", "1.0.0")
                    InfoRow("平台", "Kotlin Multiplatform")
                    InfoRow("目标", "Android / iOS / Desktop / Web")
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppText("协议命令", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
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
        AppText(label, style = AppDesign.typography.body)
        AppText(value, style = AppDesign.typography.body, fontWeight = FontWeight.Bold)
    }
}
