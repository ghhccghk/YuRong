package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 连接管理页面 - WiFi / BLE 连接
 */
@Composable
fun ConnectionScreen() {
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var bleScanning by remember { mutableStateOf(false) }
    var wifiConnected by remember { mutableStateOf(false) }
    var bleConnected by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        item {
            Text(
                "设备连接",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 连接模式选择
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedPanel == 0,
                    onClick = { selectedPanel = 0 },
                    label = { Text("AP 热点") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedPanel == 1,
                    onClick = { selectedPanel = 1 },
                    label = { Text("P2P 设备") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // WiFi 连接卡片
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WiFi 连接", style = MaterialTheme.typography.titleLarge)
                        StatusChip(wifiConnected, if (wifiConnected) "已连接" else "未连接")
                    }

                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("WiFi 名称 (SSID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { wifiConnected = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("连接 WiFi") }

                        OutlinedButton(
                            onClick = { wifiConnected = false },
                            modifier = Modifier.weight(1f)
                        ) { Text("断开") }
                    }
                }
            }
        }

        // BLE 扫描卡片
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BLE 仪表连接", style = MaterialTheme.typography.titleLarge)
                        StatusChip(bleConnected, if (bleConnected) "已连接" else "未连接")
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { bleScanning = !bleScanning },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (bleScanning)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (bleScanning) "停止扫描" else "扫描 BLE 设备")
                        }
                    }

                    if (bleScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在扫描附近设备...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 模拟设备列表
        item {
            Text("附近设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(mockBleDevices) { device ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { bleConnected = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(device.name, fontWeight = FontWeight.Bold)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun StatusChip(connected: Boolean, text: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(text, fontSize = 12.sp, color = if (connected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline
            )
        },
        leadingIcon = { Text(if (connected) "●" else "○") }
    )
}

private data class MockDevice(val name: String, val address: String, val rssi: Int)

private val mockBleDevices = listOf(
    MockDevice("雅迪仪表-X3", "7E:40:00:01:B5:A3", -45),
    MockDevice("雅迪仪表-S1", "7E:40:00:02:B5:A3", -62),
    MockDevice("BLE-Scanner", "AA:BB:CC:DD:EE:FF", -78)
)
