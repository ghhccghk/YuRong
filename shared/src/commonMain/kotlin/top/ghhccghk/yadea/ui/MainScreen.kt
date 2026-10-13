package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.mirror.FrameSourceFactory
import top.ghhccghk.yadea.mirror.MediaSourcePicker
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.InMemoryPreferenceStore
import top.ghhccghk.yadea.wifi.WifiConnectionManager
import top.ghhccghk.yadea.wifi.WifiP2pManager
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings

private enum class MainDestination(
    val label: String,
    val miuixIcon: ImageVector,
    val fallbackText: String,
) {
    Connection("连接", MiuixIcons.SearchDevice, "📡"),
    Mirror("投屏", MiuixIcons.ScreenMirroring, "🖥"),
    Device("设备", MiuixIcons.Phone, "📱"),
    Settings("设置", MiuixIcons.Settings, "⚙"),
}

@Composable
fun MainScreen(
    wifiManager: WifiConnectionManager? = null,
    bleAdapter: BleAdapter? = null,
    p2pManager: WifiP2pManager? = null,
    frameSourceFactory: FrameSourceFactory? = null,
    permissionChecker: PermissionChecker? = null,
    screenCaptureAuthorizer: ScreenCaptureAuthorizer? = null,
    mediaSourcePicker: MediaSourcePicker? = null,
    preferences: AppPreferences? = null,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
) {
    val resolvedPreferences = preferences ?: remember { AppPreferences(InMemoryPreferenceStore()) }
    val appearanceState = rememberAppAppearance(resolvedPreferences)
    val viewModel = remember {
        AppViewModel(
            wifiManager,
            bleAdapter,
            p2pManager,
            frameSourceFactory,
            permissionChecker,
            screenCaptureAuthorizer,
            mediaSourcePicker,
        )
    }

    var selectedDestination by remember { mutableStateOf(MainDestination.Connection) }

    AppTheme(
        appearance = appearanceState.value,
        systemAccentColor = systemAccentColor,
    ) {
        MainScreenContent(
            viewModel = viewModel,
            appearance = appearanceState.value,
            onAppearanceChange = { appearanceState.value = it },
            systemAccentColor = systemAccentColor,
            selectedDestination = selectedDestination,
            onDestinationChange = { selectedDestination = it },
        )
    }
}

@Composable
private fun MainScreenContent(
    viewModel: AppViewModel,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
    selectedDestination: MainDestination,
    onDestinationChange: (MainDestination) -> Unit,
) {
    val navigationItems = MainDestination.entries.map {
        AppNavigationItem(
            label = it.label,
            icon = it.miuixIcon,
            fallbackText = it.fallbackText,
        )
    }

    // BLE 连接在页面内没有断开入口，这里把它提升为连接页的顶栏动作，
    // 并用确认弹窗避免误触；断开逻辑仍复用 AppViewModel 的既有方法。
    val bleState by viewModel.bleState.collectAsState()
    var confirmBleDisconnect by remember { mutableStateOf(false) }
    val topBarActions: (@Composable RowScope.() -> Unit)? =
        if (selectedDestination == MainDestination.Connection && bleState == BleConnectionState.Connected) {
            {
                AppIconButton(
                    icon = MiuixIcons.Close,
                    contentDescription = "断开 BLE 连接",
                    onClick = { confirmBleDisconnect = true },
                )
            }
        } else {
            null
        }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = selectedDestination.label,
                actions = topBarActions,
            )
        },
        bottomBar = {
            AppBottomNavigation(
                items = navigationItems,
                selectedIndex = selectedDestination.ordinal,
                onSelected = { onDestinationChange(MainDestination.entries[it]) },
                // 悬浮底栏是 Miuix 专属外观选项，Material 3 下该开关不生效。
                floating = appearance.floatingNavigationBarEnabled,
            )
        },
    ) { padding ->
        MainDestinationContent(
            destination = selectedDestination,
            viewModel = viewModel,
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            systemAccentColor = systemAccentColor,
            modifier = Modifier.padding(padding),
        )
    }

    AppAlertDialog(
        show = confirmBleDisconnect,
        title = "断开 BLE 连接",
        message = "断开后将停止与仪表的 BLE 通信，需要重新扫描并连接。",
        onConfirm = {
            confirmBleDisconnect = false
            viewModel.disconnectBle()
        },
        onDismissRequest = { confirmBleDisconnect = false },
    )
}

@Composable
private fun MainDestinationContent(
    destination: MainDestination,
    viewModel: AppViewModel,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            MainDestination.Connection -> ConnectionScreen(viewModel)
            MainDestination.Mirror -> MirrorScreen(viewModel)
            MainDestination.Device -> DeviceScreen()
            MainDestination.Settings -> SettingsScreen(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
                systemAccentColor = systemAccentColor,
            )
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, isConnected: Boolean, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (isConnected) AppDesign.colors.primaryContainer
        else AppDesign.colors.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding, vertical = AppDesign.dimensions.itemVerticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                AppText(title, style = AppDesign.typography.sectionTitle)
                AppText(status, style = AppDesign.typography.summary)
            }
            AppText(
                text = if (isConnected) "●" else "○",
                style = AppDesign.typography.title,
                color = if (isConnected) AppDesign.colors.primary else AppDesign.colors.outline,
            )
        }
    }
}

@Composable
fun StatusChip(connected: Boolean, text: String) {
    AppStatusChip(connected = connected, text = text)
}
