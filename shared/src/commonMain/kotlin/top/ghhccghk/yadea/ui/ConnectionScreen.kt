package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    val p2pDevices by viewModel.p2pDevices.collectAsState()
    val p2pConnected by viewModel.p2pConnected.collectAsState()
    val p2pScanStatus by viewModel.p2pScanStatus.collectAsState()
    val debugLog by viewModel.debugLog.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)
    ) {
        item {
            ScreenTitle("设备连接")
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
                AppFilterChip(selected = selectedPanel == 0, onClick = { selectedPanel = 0 },
                    label = "AP 热点", modifier = Modifier.weight(1f))
                AppFilterChip(selected = selectedPanel == 1, onClick = { selectedPanel = 1 },
                    label = "P2P 设备", modifier = Modifier.weight(1f))
            }
        }

        if (selectedPanel == 0) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding), verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            AppText("WiFi 连接", style = AppDesign.typography.title)
                            StatusChip(wifiState == WifiConnectionState.Connected,
                                when (wifiState) {
                                    WifiConnectionState.Connected -> "已连接 $wifiIp"
                                    WifiConnectionState.Connecting -> "连接中..."
                                    WifiConnectionState.Failed -> "连接失败"
                                    else -> "未连接"
                                })
                        }
                        AppTextField(wifiSsid, { wifiSsid = it }, label = "SSID",
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        AppTextField(wifiPassword, { wifiPassword = it }, label = "密码",
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
                            AppButton(onClick = { viewModel.connectWifi(wifiSsid, wifiPassword) },
                                modifier = Modifier.weight(1f)) { AppText("连接") }
                            AppOutlinedButton(onClick = { viewModel.scanWifi() },
                                modifier = Modifier.weight(1f)) {
                                AppText(if (isScanningWifi) "扫描中..." else "扫描网络")
                            }
                            AppOutlinedButton(onClick = { viewModel.disconnectWifi() },
                                modifier = Modifier.weight(1f)) { AppText("断开") }
                        }
                        if (scanResults.isNotEmpty()) {
                            AppText("附近网络", style = AppDesign.typography.sectionTitle)
                            scanResults.take(5).forEach { net ->
                                Row(Modifier.fillMaxWidth().clickable { wifiSsid = net.ssid }
                                    .padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                                    AppText(net.ssid, style = AppDesign.typography.body)
                                    AppText("${net.rssi} dBm", style = AppDesign.typography.summary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedPanel == 1) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding), verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            AppText("P2P 设备发现", style = AppDesign.typography.title)
                            StatusChip(p2pConnected, if (p2pConnected) "已连接" else "未连接")
                        }
                        AppText("状态: $p2pScanStatus", style = AppDesign.typography.summary)
                        AppButton(
                            onClick = { viewModel.scanP2p() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppText("扫描 P2P 设备 (15秒)")
                        }
                        AppText("发现 ${p2pDevices.size} 个设备", style = AppDesign.typography.sectionTitle)
                        if (p2pDevices.isEmpty()) {
                            AppText("暂无设备。请确保对方设备已开启 WiFi Direct 并处于可发现状态",
                                style = AppDesign.typography.summary)
                        } else {
                            p2pDevices.forEach { device ->
                                AppCard(modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.connectP2p(device)
                                }) {
                                    Row(Modifier.padding(12.dp).fillMaxWidth(),
                                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Column {
                                            AppText(device.name, fontWeight = FontWeight.Bold)
                                            AppText(device.address, style = AppDesign.typography.summary)
                                        }
                                        AppText(when (device.status) {
                                            P2pStatus.Connected -> "已连接"
                                            P2pStatus.Invited -> "邀请中"
                                            P2pStatus.Failed -> "失败"
                                            else -> "可用"
                                        }, style = AppDesign.typography.summary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding), verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        AppText("BLE 仪表", style = AppDesign.typography.title)
                        StatusChip(bleState == BleConnectionState.Connected,
                            if (bleState == BleConnectionState.Connected) "已连接 $bleConnectedName" else "未连接")
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(AppDesign.dimensions.controlSpacing)) {
                        AppButton(onClick = { viewModel.scanBle() }, modifier = Modifier.weight(1f),
                            containerColor = if (isScanningBle) AppDesign.colors.error else AppDesign.colors.primary) {
                            AppText(if (isScanningBle) "停止扫描" else "扫描 BLE")
                        }
                    }
                    if (bleDevices.isNotEmpty()) {
                        bleDevices.forEach { device ->
                            AppCard(modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.connectBle(device.address, device.name ?: "未知")
                            }) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(),
                                    Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column {
                                        AppText(device.name ?: "未知设备", fontWeight = FontWeight.Bold)
                                        AppText(device.address, style = AppDesign.typography.summary)
                                    }
                                    AppText("${device.rssi} dBm", style = AppDesign.typography.summary)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
                    AppText("调试日志", style = AppDesign.typography.sectionTitle, fontWeight = FontWeight.Bold)
                    debugLog.reversed().forEach { line ->
                        AppText(line, style = AppDesign.typography.debug,
                            color = AppDesign.colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
