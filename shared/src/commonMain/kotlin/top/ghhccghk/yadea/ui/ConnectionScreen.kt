package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.wifi.P2pStatus
import top.ghhccghk.yadea.wifi.WifiConnectionState

private enum class ConnectionMode {
    AccessPoint,
    PeerToPeer,
}

@Composable
fun ConnectionScreen(viewModel: AppViewModel) {
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ConnectionMode.AccessPoint) }

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

    val dimensions = AppDesign.dimensions
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
                title = "设备连接",
                subtitle = when (selectedMode) {
                    ConnectionMode.AccessPoint -> "通过 AP 热点或 BLE 连接仪表"
                    ConnectionMode.PeerToPeer -> "发现并连接附近的 P2P 仪表"
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppFilterChip(
                    selected = selectedMode == ConnectionMode.AccessPoint,
                    onClick = { selectedMode = ConnectionMode.AccessPoint },
                    label = "AP 热点",
                    modifier = Modifier.weight(1f),
                )
                AppFilterChip(
                    selected = selectedMode == ConnectionMode.PeerToPeer,
                    onClick = { selectedMode = ConnectionMode.PeerToPeer },
                    label = "P2P 设备",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (selectedMode == ConnectionMode.AccessPoint) {
            item {
                AppSection(title = "WiFi 连接") {
                    AppListRow(
                        title = "连接状态",
                        value = when (wifiState) {
                            WifiConnectionState.Connected -> "已连接 $wifiIp"
                            WifiConnectionState.Connecting -> "连接中..."
                            WifiConnectionState.Failed -> "连接失败"
                            WifiConnectionState.Disconnecting -> "断开中..."
                            else -> "未连接"
                        },
                    )
                    AppTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = "SSID",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                    )
                    AppTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = "密码",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        AppButton(
                            onClick = { viewModel.connectWifi(wifiSsid, wifiPassword) },
                            modifier = Modifier.weight(1f),
                        ) { AppText("连接") }
                        AppOutlinedButton(
                            onClick = { viewModel.scanWifi() },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(if (isScanningWifi) "扫描中..." else "扫描网络")
                        }
                        AppOutlinedButton(
                            onClick = { viewModel.disconnectWifi() },
                            modifier = Modifier.weight(1f),
                        ) { AppText("断开") }
                    }

                    if (scanResults.isNotEmpty()) {
                        AppText(
                            text = "附近网络",
                            style = AppDesign.typography.sectionTitle,
                            modifier = Modifier.padding(start = dimensions.itemHorizontalPadding, end = dimensions.itemHorizontalPadding, top = dimensions.compactSpacing),
                        )
                        scanResults.take(5).forEachIndexed { index, network ->
                            AppListRow(
                                title = network.ssid,
                                value = "${network.rssi} dBm",
                                showDivider = index < scanResults.take(5).lastIndex,
                                onClick = { wifiSsid = network.ssid },
                            )
                        }
                    }
                }
            }
        }

        if (selectedMode == ConnectionMode.PeerToPeer) {
            item {
                AppSection(title = "P2P 设备发现") {
                    AppListRow(
                        title = "连接状态",
                        value = if (p2pConnected) "已连接" else "未连接",
                    )
                    AppListRow(
                        title = "扫描状态",
                        value = p2pScanStatus,
                    )
                    AppButton(
                        onClick = { viewModel.scanP2p() },
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                    ) {
                        AppText("扫描 P2P 设备 (15秒)")
                    }
                    AppText(
                        text = "发现 ${p2pDevices.size} 个设备",
                        style = AppDesign.typography.sectionTitle,
                        modifier = Modifier.padding(start = dimensions.itemHorizontalPadding, end = dimensions.itemHorizontalPadding, top = dimensions.compactSpacing),
                    )
                    if (p2pDevices.isEmpty()) {
                        AppEmptyState("暂无设备。请确保对方设备已开启 WiFi Direct 并处于可发现状态")
                    } else {
                        p2pDevices.forEachIndexed { index, device ->
                            AppListRow(
                                title = device.name,
                                summary = device.address,
                                value = when (device.status) {
                                    P2pStatus.Connected -> "已连接"
                                    P2pStatus.Invited -> "邀请中"
                                    P2pStatus.Failed -> "失败"
                                    P2pStatus.Available -> "可用"
                                },
                                showDivider = index < p2pDevices.lastIndex,
                                onClick = { viewModel.connectP2p(device) },
                            )
                        }
                    }
                }
            }
        }

        item {
            AppSection(title = "BLE 仪表") {
                AppListRow(
                    title = "连接状态",
                    value = if (bleState == BleConnectionState.Connected) {
                        "已连接 $bleConnectedName"
                    } else {
                        "未连接"
                    },
                )
                AppButton(
                    onClick = { viewModel.scanBle() },
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    containerColor = if (isScanningBle) AppDesign.colors.error else AppDesign.colors.primary,
                ) {
                    AppText(if (isScanningBle) "停止扫描" else "扫描 BLE")
                }
                if (bleDevices.isEmpty()) {
                    AppEmptyState(if (isScanningBle) "正在扫描附近的 BLE 仪表..." else "暂无 BLE 设备")
                } else {
                    bleDevices.forEachIndexed { index, device ->
                        AppListRow(
                            title = device.name ?: "未知设备",
                            summary = device.address,
                            value = "${device.rssi} dBm",
                            showDivider = index < bleDevices.lastIndex,
                            onClick = { viewModel.connectBle(device.address, device.name ?: "未知") },
                        )
                    }
                }
            }
        }

        item {
            AppSection(title = "调试日志") {
                if (debugLog.isEmpty()) {
                    AppEmptyState("暂无调试日志")
                } else {
                    debugLog.reversed().forEach { line ->
                        AppText(
                            text = line,
                            style = AppDesign.typography.debug,
                            color = AppDesign.colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = dimensions.itemHorizontalPadding,
                                vertical = dimensions.compactSpacing,
                            ),
                        )
                    }
                }
            }
        }
    }
}
