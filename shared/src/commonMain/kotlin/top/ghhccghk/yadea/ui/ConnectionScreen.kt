package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
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
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.wifi.P2pStatus
import top.ghhccghk.yadea.wifi.WifiConnectionState

/**
 * 连接管理页面 - WiFi / BLE / P2P 实际逻辑
 */
@Composable
fun ConnectionScreen(viewModel: AppViewModel) {
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var selectedPanel by remember { mutableStateOf(0) }

    val wifiState by viewModel.wifiState.collectAsState()
    val wifiIp by viewModel.wifiIp.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanningWifi by viewModel.isScanningWifi.collectAsState()

    val bleState by viewModel.bleState.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val isScanningBle by viewModel.isScanningBle.collectAsState()
    val bleConnectedName by viewModel.bleConnectedName.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("设备连接", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // 连接模式
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedPanel == 0, onClick = { selectedPanel = 0 },
                    label = { Text("AP 热点") }, modifier = Modifier.weight(1f))
                FilterChip(selected = selectedPanel == 1, onClick = { selectedPanel = 1 },
                    label = { Text("P2P 设备") }, modifier = Modifier.weight(1f))
            }
        }

        // WiFi 连接
        if (selectedPanel == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("WiFi 连接", style = MaterialTheme.typography.titleLarge)
                            StatusChip(wifiState == WifiConnectionState.Connected,
                                when (wifiState) {
                                    WifiConnectionState.Connected -> "已连接 $wifiIp"
                                    WifiConnectionState.Connecting -> "连接中..."
                                    WifiConnectionState.Failed -> "连接失败"
                                    else -> "未连接"
                                })
                        }
                        OutlinedTextField(wifiSsid, { wifiSsid = it }, label = { Text("SSID") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(wifiPassword, { wifiPassword = it }, label = { Text("密码") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.connectWifi(wifiSsid, wifiPassword) },
                                modifier = Modifier.weight(1f)) { Text("连接") }
                            OutlinedButton(onClick = { viewModel.scanWifi() },
                                modifier = Modifier.weight(1f)) {
                                Text(if (isScanningWifi) "扫描中..." else "扫描网络")
                            }
                            OutlinedButton(onClick = { viewModel.disconnectWifi() },
                                modifier = Modifier.weight(1f)) { Text("断开") }
                        }
                        // 扫描结果
                        if (scanResults.isNotEmpty()) {
                            Text("附近网络", style = MaterialTheme.typography.titleSmall)
                            scanResults.take(5).forEach { net ->
                                Row(Modifier.fillMaxWidth().clickable { wifiSsid = net.ssid }
                                    .padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                                    Text(net.ssid, style = MaterialTheme.typography.bodyMedium)
                                    Text("${net.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // P2P 设备
        if (selectedPanel == 1) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("P2P 设备发现", style = MaterialTheme.typography.titleLarge)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.scanP2p() }, modifier = Modifier.weight(1f)) {
                                Text("扫描 P2P 设备")
                            }
                        }
                        if (viewModel.p2pDevices.value.isEmpty()) {
                            Text("暂无发现的 P2P 设备", style = MaterialTheme.typography.bodySmall)
                        } else {
                            viewModel.p2pDevices.value.forEach { device ->
                                Card(modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.connectP2p(device)
                                }) {
                                    Row(Modifier.padding(12.dp).fillMaxWidth(),
                                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Column {
                                            Text(device.name, fontWeight = FontWeight.Bold)
                                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(when (device.status) {
                                            P2pStatus.Connected -> "已连接"
                                            P2pStatus.Invited -> "邀请中"
                                            P2pStatus.Failed -> "失败"
                                            else -> "可用"
                                        }, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // BLE 连接
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("BLE 仪表", style = MaterialTheme.typography.titleLarge)
                        StatusChip(bleState == BleConnectionState.Connected,
                            if (bleState == BleConnectionState.Connected) "已连接 $bleConnectedName" else "未连接")
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.scanBle() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanningBle) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )) {
                            Text(if (isScanningBle) "停止扫描" else "扫描 BLE")
                        }
                    }
                    if (bleDevices.isNotEmpty()) {
                        bleDevices.forEach { device ->
                            Card(modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.connectBle(device.address, device.name ?: "未知")
                            }) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(),
                                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column {
                                        Text(device.name ?: "未知设备", fontWeight = FontWeight.Bold)
                                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
