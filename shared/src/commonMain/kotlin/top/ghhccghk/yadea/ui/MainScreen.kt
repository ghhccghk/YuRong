package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings

private enum class MainDestination(
    val label: String,
    val miuixIcon: ImageVector,
    val fallbackText: String,
) {
    Connection("连接", MiuixIcons.SearchDevice, "📡"),
    Device("设备", MiuixIcons.Phone, "📱"),
    Settings("设置", MiuixIcons.Settings, "⚙"),
}

/**
 * 连接页的两个子功能。
 *
 * 合并后「投屏」不再是顶层入口，而是连接页下的子功能，
 * 与「设备连接」共用同一个脚手架、顶栏动作和滚动宿主。
 */
private enum class ConnectionSection(val label: String) {
    Device("设备连接"),
    Mirror("投屏"),
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
    // 投屏日志属于次级信息，改为顶栏动作 + 底部面板，避免长期占用页面空间。
    val debugLog by viewModel.debugLog.collectAsState()
    var showMirrorLog by remember { mutableStateOf(false) }
    // 连接页内的子功能（设备连接 / 投屏）；离开连接页后保留选择，返回时回到原来的子页。
    var connectionSection by remember { mutableStateOf(ConnectionSection.Device) }
    // 顶栏滚动行为由页面创建一次，再分别交给脚手架与顶栏；两种风格各自使用原生实现。
    val scrollBehavior = rememberAppScrollBehavior()
    // 轻量提示状态同样在页面顶层创建一次：宿主挂在脚手架上，各页面共用同一个状态。
    val snackbarHostState = rememberAppSnackbarHostState()
    val feedbackScope = rememberCoroutineScope()
    val topBarActions: (@Composable RowScope.() -> Unit)? = when {
        // 断开 BLE 只与设备连接子页相关，切到投屏子页时收起，避免误触。
        selectedDestination == MainDestination.Connection &&
            connectionSection == ConnectionSection.Device &&
            bleState == BleConnectionState.Connected -> {
            {
                AppIconButton(
                    icon = MiuixIcons.Close,
                    contentDescription = "断开 BLE 连接",
                    onClick = { confirmBleDisconnect = true },
                )
            }
        }

        selectedDestination == MainDestination.Connection &&
            connectionSection == ConnectionSection.Mirror -> {
            {
                AppIconButton(
                    icon = MiuixIcons.ListView,
                    contentDescription = "查看调试日志",
                    onClick = { showMirrorLog = true },
                )
            }
        }

        else -> null
    }

    // 系统返回手势：非连接页先回到连接页；连接页的投屏子页先回到设备子页；
    // 连接页的设备子页不拦截，直接把事件交回系统处理（Android 13+ 会播放预测性返回动画）。
    val canNavigateBack = selectedDestination != MainDestination.Connection ||
        connectionSection != ConnectionSection.Device
    AppBackHandler(enabled = canNavigateBack) {
        if (selectedDestination != MainDestination.Connection) {
            onDestinationChange(MainDestination.Connection)
        } else {
            connectionSection = ConnectionSection.Device
        }
    }

    // 宽屏自动换成侧边导航栏，窄屏保持底部导航；两种布局共用同一份导航项与回调。
    AppAdaptiveScaffold(
        navigationItems = navigationItems,
        selectedIndex = selectedDestination.ordinal,
        onSelected = { onDestinationChange(MainDestination.entries[it]) },
        topBar = {
            AppTopBar(
                title = selectedDestination.label,
                actions = topBarActions,
                scrollBehavior = scrollBehavior,
            )
        },
        // 悬浮底栏是 Miuix 专属外观选项，Material 3 下该开关不生效。
        floatingNavigation = appearance.floatingNavigationBarEnabled,
        // 提示宿主由脚手架挂载，窄屏与宽屏两种布局下都能显示。
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        scrollBehavior = scrollBehavior,
    ) { padding ->
        MainDestinationContent(
            destination = selectedDestination,
            connectionSection = connectionSection,
            onConnectionSectionChange = { connectionSection = it },
            viewModel = viewModel,
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            systemAccentColor = systemAccentColor,
            modifier = Modifier.padding(padding),
            // 连接页的下拉刷新需要与顶栏滚动行为协同，这里把同一个对象继续传下去。
            scrollBehavior = scrollBehavior,
            // 连接页与投屏页的操作结果复用同一个提示状态。
            snackbarHostState = snackbarHostState,
        )
    }

    AppBottomSheet(
        // 离开投屏子页时面板自动关闭，避免切页后仍停留在旧的日志内容上。
        show = showMirrorLog && selectedDestination == MainDestination.Connection &&
            connectionSection == ConnectionSection.Mirror,
        onDismissRequest = { showMirrorLog = false },
        title = "调试日志",
    ) {
        if (debugLog.isEmpty()) {
            AppEmptyState("暂无调试日志")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 最新日志排在最前，便于判断最近一次操作的结果。
                debugLog.reversed().forEach { line ->
                    AppText(
                        text = line,
                        style = AppDesign.typography.debug,
                        color = AppDesign.colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(
                            vertical = AppDesign.dimensions.compactSpacing,
                        ),
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = confirmBleDisconnect,
        title = "断开 BLE 连接",
        message = "断开后将停止与仪表的 BLE 通信，需要重新扫描并连接。",
        onConfirm = {
            confirmBleDisconnect = false
            viewModel.disconnectBle()
            // 断开属于一次性操作，用轻量提示确认结果，避免再占用页面空间。
            feedbackScope.launch {
                snackbarHostState.showSnackbar("已断开 BLE 连接")
            }
        },
        onDismissRequest = { confirmBleDisconnect = false },
    )
}

@Composable
private fun MainDestinationContent(
    destination: MainDestination,
    connectionSection: ConnectionSection,
    onConnectionSectionChange: (ConnectionSection) -> Unit,
    viewModel: AppViewModel,
    appearance: AppAppearance,
    onAppearanceChange: (AppAppearance) -> Unit,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
    scrollBehavior: AppScrollBehavior? = null,
    snackbarHostState: AppSnackbarHostState? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            // 连接页承载两个子功能：设备连接与投屏。
            MainDestination.Connection -> ConnectionHubScreen(
                section = connectionSection,
                onSectionChange = onConnectionSectionChange,
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                snackbarHostState = snackbarHostState,
            )
            MainDestination.Device -> DeviceScreen()
            MainDestination.Settings -> SettingsScreen(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
                systemAccentColor = systemAccentColor,
            )
        }
    }
}

/**
 * 连接页宿主：顶部用分段控件切换「设备连接」与「投屏」两个子功能。
 *
 * 两个子页面各自保留原有实现与滚动容器，这里只用 weight 占位，
 * 避免把两个 LazyColumn 嵌套在一起；分段控件不注册 nestedScroll，
 * 因此切换子功能不会影响下方页面的上下拖动。
 */
@Composable
private fun ConnectionHubScreen(
    section: ConnectionSection,
    onSectionChange: (ConnectionSection) -> Unit,
    viewModel: AppViewModel,
    scrollBehavior: AppScrollBehavior? = null,
    snackbarHostState: AppSnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    val dimensions = AppDesign.dimensions
    Column(modifier = modifier.fillMaxSize()) {
        AppSegmentedControl(
            options = ConnectionSection.entries.map { it.label },
            selectedIndex = ConnectionSection.entries.indexOf(section),
            onSelected = { onSectionChange(ConnectionSection.entries[it]) },
            // 只保留上方与两侧间距：下方间距由子页面自己的 contentPadding 提供，避免双重留白。
            modifier = Modifier.fillMaxWidth().padding(
                start = dimensions.pageHorizontalPadding,
                top = dimensions.controlSpacing,
                end = dimensions.pageHorizontalPadding,
            ),
        )
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                ConnectionSection.Device -> ConnectionScreen(viewModel, scrollBehavior, snackbarHostState)
                ConnectionSection.Mirror -> MirrorScreen(viewModel, snackbarHostState)
            }
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
