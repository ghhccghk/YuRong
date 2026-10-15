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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.wifi.P2pStatus
import top.ghhccghk.yadea.wifi.WifiConnectionState

private enum class ConnectionMode {
    AccessPoint,
    PeerToPeer,
}

@Composable
fun ConnectionScreen(
    viewModel: AppViewModel,
    // 顶栏滚动行为由主界面创建后传入，下拉刷新时与 Miuix 顶栏的展开/收起协同。
    scrollBehavior: AppScrollBehavior? = null,
    // 轻量提示状态由主界面创建后传入，用于回报一次性操作的结果。
    snackbarHostState: AppSnackbarHostState? = null,
) {
    // 提示由本页面自己的作用域发起，页面离开组合时挂起的提示会随之取消。
    val feedbackScope = rememberCoroutineScope()
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ConnectionMode.AccessPoint) }
    // 三个设备列表各自维护搜索关键字：AP 扫描结果、P2P 设备与 BLE 设备互不影响。
    var wifiQuery by remember { mutableStateOf("") }
    var p2pQuery by remember { mutableStateOf("") }
    var bleQuery by remember { mutableStateOf("") }

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
    val isScanningP2p by viewModel.isScanningP2p.collectAsState()
    val debugLog by viewModel.debugLog.collectAsState()

    val dimensions = AppDesign.dimensions
    // 下拉刷新：按当前连接模式重新扫描对应设备列表；BLE 仍保留显式扫描按钮，避免与“停止扫描”状态互相干扰。
    val isRefreshing = when (selectedMode) {
        ConnectionMode.AccessPoint -> isScanningWifi
        ConnectionMode.PeerToPeer -> isScanningP2p
    }
    AppPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = {
            // 扫描进行中时忽略重复触发，避免叠加多次扫描。
            when (selectedMode) {
                ConnectionMode.AccessPoint -> if (!isScanningWifi) viewModel.scanWifi()
                ConnectionMode.PeerToPeer -> if (!isScanningP2p) viewModel.scanP2p()
            }
        },
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
    ) {
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
                // 两种连接方式互斥且都会切换下方内容，用分段标签栏表达比并排的 FilterChip 更准确。
                AppTabRow(
                    tabs = listOf("AP 热点", "P2P 设备"),
                    selectedIndex = if (selectedMode == ConnectionMode.AccessPoint) 0 else 1,
                    onSelected = { index ->
                        selectedMode = if (index == 0) ConnectionMode.AccessPoint else ConnectionMode.PeerToPeer
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (selectedMode == ConnectionMode.AccessPoint) {
                if (wifiState == WifiConnectionState.Failed) {
                    item {
                        AppTip(
                            kind = AppTipKind.Error,
                            title = "WiFi 连接失败",
                            message = "请确认 SSID 与密码是否正确，或改用 P2P 直连模式。",
                        )
                    }
                }
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
                                onClick = {
                                    viewModel.disconnectWifi()
                                    // 断开是一次性操作，用轻量提示确认，不需要长期占用页面空间。
                                    snackbarHostState?.let { state ->
                                        feedbackScope.launch { state.showSnackbar("已断开 Wi-Fi 连接") }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { AppText("断开") }
                        }

                        if (scanResults.isNotEmpty()) {
                            AppText(
                                text = "附近网络",
                                style = AppDesign.typography.sectionTitle,
                                modifier = Modifier.padding(start = dimensions.itemHorizontalPadding, end = dimensions.itemHorizontalPadding, top = dimensions.compactSpacing),
                            )
                            // 搜索框只过滤界面列表，不会改动扫描结果本身，也不参与任何 WiFi 连接逻辑。
                            AppSearchBar(
                                query = wifiQuery,
                                onQueryChange = { wifiQuery = it },
                                label = "搜索网络名称",
                                modifier = Modifier.padding(
                                    horizontal = dimensions.itemHorizontalPadding,
                                    vertical = dimensions.compactSpacing,
                                ),
                            )
                            // 先按关键字过滤再取前 5 条，避免关键字命中的网络被截断在列表之外。
                            val visibleNetworks = scanResults
                                .filter { matchesSearchQuery(wifiQuery, it.ssid) }
                                .take(5)
                            if (visibleNetworks.isEmpty()) {
                                AppEmptyState("没有匹配“$wifiQuery”的网络")
                            } else {
                                visibleNetworks.forEachIndexed { index, network ->
                                    AppListRow(
                                        title = network.ssid,
                                        value = "${network.rssi} dBm",
                                        showDivider = index < visibleNetworks.lastIndex,
                                        onClick = { wifiSsid = network.ssid },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedMode == ConnectionMode.PeerToPeer) {
                if (p2pDevices.any { it.status == P2pStatus.Failed }) {
                    item {
                        AppTip(
                            kind = AppTipKind.Warning,
                            title = "部分 P2P 设备连接失败",
                            message = "可在设备列表中重新点击该设备重试。",
                        )
                    }
                }
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
                        // 名称与地址都参与匹配，过滤只作用于界面列表，不影响 P2P 发现与连接流程。
                        val visibleP2pDevices = p2pDevices.filter {
                            matchesSearchQuery(p2pQuery, it.name, it.address)
                        }
                        AppText(
                            text = if (p2pQuery.isBlank()) {
                                "发现 ${p2pDevices.size} 个设备"
                            } else {
                                "匹配 ${visibleP2pDevices.size} / ${p2pDevices.size} 个设备"
                            },
                            style = AppDesign.typography.sectionTitle,
                            modifier = Modifier.padding(start = dimensions.itemHorizontalPadding, end = dimensions.itemHorizontalPadding, top = dimensions.compactSpacing),
                        )
                        if (p2pDevices.isEmpty()) {
                            AppEmptyState("暂无设备。请确保对方设备已开启 WiFi Direct 并处于可发现状态")
                        } else {
                            AppSearchBar(
                                query = p2pQuery,
                                onQueryChange = { p2pQuery = it },
                                label = "搜索设备名称或地址",
                                modifier = Modifier.padding(
                                    horizontal = dimensions.itemHorizontalPadding,
                                    vertical = dimensions.compactSpacing,
                                ),
                            )
                            if (visibleP2pDevices.isEmpty()) {
                                AppEmptyState("没有匹配“$p2pQuery”的设备")
                            } else {
                                visibleP2pDevices.forEachIndexed { index, device ->
                                    AppListRow(
                                        title = device.name,
                                        summary = device.address,
                                        value = when (device.status) {
                                            P2pStatus.Connected -> "已连接"
                                            P2pStatus.Invited -> "邀请中"
                                            P2pStatus.Failed -> "失败"
                                            P2pStatus.Available -> "可用"
                                        },
                                        showDivider = index < visibleP2pDevices.lastIndex,
                                        onClick = { viewModel.connectP2p(device) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (bleState == BleConnectionState.Failed) {
                item {
                    AppTip(
                        kind = AppTipKind.Error,
                        title = "BLE 连接失败",
                        message = "请确认仪表已上电并处于扫描范围内，然后重新扫描。",
                    )
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
                        AppSearchBar(
                            query = bleQuery,
                            onQueryChange = { bleQuery = it },
                            label = "搜索 BLE 设备",
                            modifier = Modifier.padding(
                                horizontal = dimensions.itemHorizontalPadding,
                                vertical = dimensions.compactSpacing,
                            ),
                        )
                        // BLE 设备可能没有广播名称，因此名称与地址都参与匹配；过滤不影响扫描与连接逻辑。
                        val visibleBleDevices = bleDevices.filter {
                            matchesSearchQuery(bleQuery, it.name, it.address)
                        }
                        if (visibleBleDevices.isEmpty()) {
                            AppEmptyState("没有匹配“$bleQuery”的 BLE 设备")
                        } else {
                            visibleBleDevices.forEachIndexed { index, device ->
                                AppListRow(
                                    title = device.name ?: "未知设备",
                                    summary = device.address,
                                    value = "${device.rssi} dBm",
                                    showDivider = index < visibleBleDevices.lastIndex,
                                    onClick = { viewModel.connectBle(device.address, device.name ?: "未知") },
                                )
                            }
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
}
