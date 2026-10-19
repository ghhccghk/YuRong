package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.auth.AuthController
import top.ghhccghk.yadea.auth.AuthGateway
import top.ghhccghk.yadea.auth.AuthSession
import top.ghhccghk.yadea.carsetting.CarSettingWriter
import top.ghhccghk.yadea.carsetting.CarSettingsGateway
import top.ghhccghk.yadea.carsetting.MappedCarSettingWriter
import top.ghhccghk.yadea.carsetting.CloudCarSettingWriter
import top.ghhccghk.yadea.carsetting.VehicleSettingCommandSink
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.mirror.FrameSourceFactory
import top.ghhccghk.yadea.mirror.MediaSourcePicker
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.InMemoryPreferenceStore
import top.ghhccghk.yadea.ota.OtaController
import top.ghhccghk.yadea.ota.OtaGateway
import top.ghhccghk.yadea.vehicle.EmptyVehicleStatusSource
import top.ghhccghk.yadea.vehicle.EmptyVehicleProfileSource
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleBleTokenProvider
import top.ghhccghk.yadea.vehicle.VehicleControlController
import top.ghhccghk.yadea.vehicle.VehicleControlSink
import top.ghhccghk.yadea.vehicle.VehicleStatusSource
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
    Device("车辆", MiuixIcons.Phone, "🚗"),
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
    bleTokenProvider: VehicleBleTokenProvider? = null,
    vehicleControlSink: VehicleControlSink? = null,
    p2pManager: WifiP2pManager? = null,
    frameSourceFactory: FrameSourceFactory? = null,
    permissionChecker: PermissionChecker? = null,
    screenCaptureAuthorizer: ScreenCaptureAuthorizer? = null,
    mediaSourcePicker: MediaSourcePicker? = null,
    preferences: AppPreferences? = null,
    vehicleStatusSource: VehicleStatusSource? = null,
    vehicleProfileSource: VehicleProfileSource? = null,
    authGateway: AuthGateway? = null,
    carSettingWriter: CarSettingWriter? = null,
    carSettingCommandSink: VehicleSettingCommandSink? = null,
    carSettingsGateway: CarSettingsGateway? = null,
    otaGateway: OtaGateway? = null,
    onOpenExternalUrl: ((String) -> Unit)? = null,
    onRefreshVehicleProfile: (() -> Unit)? = null,
    vehicleProfilesRefreshing: Boolean = false,
    vehicleProfilesError: String? = null,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)? = null,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)? = null,
    systemAccentColor: androidx.compose.ui.graphics.Color? = null,
) {
    val resolvedPreferences = preferences ?: remember { AppPreferences(InMemoryPreferenceStore()) }
    val appearanceState = rememberAppAppearance(resolvedPreferences)
    val featureScope = rememberCoroutineScope()
    var vehicleConnectionPreferences by remember {
        mutableStateOf(resolvedPreferences.readVehicleConnection())
    }
    val viewModel = remember(bleTokenProvider, vehicleProfileSource) {
        AppViewModel(
            wifiManager = wifiManager,
            bleAdapter = bleAdapter,
            p2pManager = p2pManager,
            frameSourceFactory = frameSourceFactory,
            permissionChecker = permissionChecker,
            screenCaptureAuthorizer = screenCaptureAuthorizer,
            mediaSourcePicker = mediaSourcePicker,
            bleTokenProvider = bleTokenProvider,
            vehicleVinProvider = { vehicleProfileSource?.profile?.value?.vin.orEmpty() },
            onVehicleConnectionEstablished = { mac, name, binding ->
                if (mac.isNotBlank()) {
                    featureScope.launch {
                        resolvedPreferences.writeLastVehicle(
                            mac = mac,
                            name = name,
                            boundVin = if (binding) {
                                vehicleProfileSource?.profile?.value?.vin.orEmpty()
                            } else {
                                null
                            },
                        )
                    }
                }
            },
        )
    }
    val resolvedVehicleSource = remember(vehicleStatusSource) {
        vehicleStatusSource ?: EmptyVehicleStatusSource()
    }
    val vehicleStatus by resolvedVehicleSource.status.collectAsState()
    val vehicleConnected by resolvedVehicleSource.connected.collectAsState()
    val resolvedProfileSource = remember(vehicleProfileSource) {
        vehicleProfileSource ?: EmptyVehicleProfileSource()
    }
    val vehicleProfile by resolvedProfileSource.profile.collectAsState()
    val vehicleProfiles by resolvedProfileSource.profiles.collectAsState()
    val authController = remember(resolvedPreferences, authGateway) {
        AuthController(AuthSession(resolvedPreferences), authGateway, featureScope)
    }
    val authUiState by authController.state.collectAsState()
    val vehicleVinProvider = { resolvedProfileSource.profile.value?.vin.orEmpty() }
    val resolvedCarSettingWriter = carSettingWriter ?: run {
        val bleWriter = carSettingCommandSink?.let(::MappedCarSettingWriter)
        carSettingsGateway?.let { gateway ->
            CloudCarSettingWriter(
                gateway = gateway,
                vinProvider = vehicleVinProvider,
                bleFallback = bleWriter,
            )
        } ?: bleWriter
    }
    val carSettingsController = remember(
        resolvedCarSettingWriter,
        carSettingsGateway,
        resolvedProfileSource,
    ) {
        CarSettingsController(
            initialItems = emptyList(),
            writer = resolvedCarSettingWriter,
            gateway = carSettingsGateway,
            vinProvider = { resolvedProfileSource.profile.value?.vin.orEmpty() },
        )
    }
    val otaController = remember(otaGateway) {
        OtaController(
            gateway = otaGateway,
            currentVersion = "—",
            scope = featureScope,
        )
    }
    val vehicleControlController = remember(vehicleControlSink, resolvedVehicleSource) {
        VehicleControlController(
            sink = vehicleControlSink,
            connectedProvider = { resolvedVehicleSource.connected.value },
            scope = featureScope,
        )
    }
    DisposableEffect(authController, otaController, vehicleControlController) {
        onDispose {
            authController.dispose()
            otaController.dispose()
            vehicleControlController.dispose()
        }
    }
    // Token 登录不保证能在本地解析出 userInfo；车辆接口只依赖 Token，
    // 因此 Token 一旦变化就刷新，避免被 userId 为空挡住。
    LaunchedEffect(authUiState.session.token) {
        if (authUiState.session.token.isNotBlank()) {
            onRefreshVehicleProfile?.invoke()
        }
    }
    LaunchedEffect(
        vehicleProfile?.vin,
        authUiState.session.token,
        carSettingsGateway,
    ) {
        if (authUiState.session.token.isNotBlank() && !vehicleProfile?.vin.isNullOrBlank()) {
            carSettingsController.refresh()
        }
    }
    LaunchedEffect(
        vehicleStatus?.extended?.tcsWorkMode,
        vehicleStatus?.extended?.absFunctionStatus,
        vehicleStatus?.extended?.simulatedSoundWaveStatus,
        vehicleStatus?.extended?.brightnessAdjustSwitch,
        vehicleStatus?.extended?.automaticHeadlightFunctionStatus,
        vehicleStatus?.extended?.turnSignalReturnsAutomaticallyStatus,
        vehicleStatus?.extended?.brakePowerDownFunctionStatus,
        vehicleStatus?.extended?.steepSlopeState,
        vehicleStatus?.extended?.rampSlopeState,
    ) {
        carSettingsController.updateVehicleStatus(vehicleStatus)
    }
    var autoConnectAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(authUiState.session.isLoggedIn, vehicleProfile?.vin) {
        if (!autoConnectAttempted && authUiState.session.isLoggedIn) {
            autoConnectAttempted = true
            val vehiclePreferences = resolvedPreferences.readVehicleConnection()
            val currentVin = vehicleProfile?.vin.orEmpty()
            if (vehiclePreferences.autoConnect &&
                vehiclePreferences.lastMac.isNotBlank() &&
                (vehiclePreferences.boundVin.isBlank() ||
                    currentVin.isBlank() ||
                    vehiclePreferences.boundVin == currentVin)
            ) {
                viewModel.connectBle(
                    address = vehiclePreferences.lastMac,
                    name = vehiclePreferences.lastName.ifBlank { "上次连接的车辆" },
                )
            }
        }
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
            vehicleStatus = vehicleStatus,
            vehicleConnected = vehicleConnected,
            vehicleProfile = vehicleProfile,
            vehicleProfiles = vehicleProfiles,
            onSelectVehicle = resolvedProfileSource::select,
            onRefreshVehicleProfile = onRefreshVehicleProfile,
            vehicleProfilesRefreshing = vehicleProfilesRefreshing,
            vehicleProfilesError = vehicleProfilesError,
            vehicleBackgroundContent = vehicleBackgroundContent,
            vehicleImageContent = vehicleImageContent,
            onOpenExternalUrl = onOpenExternalUrl,
            authController = authController,
            carSettingsController = carSettingsController,
            otaController = otaController,
            vehicleControlController = vehicleControlController,
            vehicleConnectionPreferences = vehicleConnectionPreferences,
            onVehicleAutoConnectChange = { enabled ->
                val updated = vehicleConnectionPreferences.copy(autoConnect = enabled)
                vehicleConnectionPreferences = updated
                featureScope.launch {
                    resolvedPreferences.writeVehiclePreferences(
                        autoConnect = updated.autoConnect,
                        foregroundService = updated.foregroundService,
                    )
                }
            },
            onVehicleForegroundServiceChange = { enabled ->
                val updated = vehicleConnectionPreferences.copy(
                    foregroundService = enabled,
                )
                vehicleConnectionPreferences = updated
                featureScope.launch {
                    resolvedPreferences.writeVehiclePreferences(
                        autoConnect = updated.autoConnect,
                        foregroundService = updated.foregroundService,
                    )
                }
            },
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
    vehicleStatus: TtpInfo?,
    vehicleConnected: Boolean,
    vehicleProfile: VehicleProfile?,
    vehicleProfiles: List<VehicleProfile>,
    onSelectVehicle: (String) -> Unit,
    onRefreshVehicleProfile: (() -> Unit)?,
    vehicleProfilesRefreshing: Boolean,
    vehicleProfilesError: String?,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)?,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)?,
    onOpenExternalUrl: ((String) -> Unit)?,
    authController: AuthController,
    carSettingsController: CarSettingsController,
    otaController: OtaController,
    vehicleControlController: VehicleControlController,
    vehicleConnectionPreferences: top.ghhccghk.yadea.settings.VehicleConnectionPreferences,
    onVehicleAutoConnectChange: (Boolean) -> Unit,
    onVehicleForegroundServiceChange: (Boolean) -> Unit,
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
    var vehicleSection by remember { mutableStateOf(VehicleSection.Info) }
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
        connectionSection != ConnectionSection.Device ||
        (selectedDestination == MainDestination.Device && vehicleSection != VehicleSection.Info)
    AppBackHandler(enabled = canNavigateBack) {
        when {
            selectedDestination == MainDestination.Device &&
                vehicleSection != VehicleSection.Info -> vehicleSection = VehicleSection.Info
            selectedDestination != MainDestination.Connection ->
                onDestinationChange(MainDestination.Connection)
            else -> connectionSection = ConnectionSection.Device
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
        // 背景模糊同样是 Miuix 专属外观选项：Material 3 与不支持的平台会自动退回纯色顶栏/底栏。
        blurEnabled = appearance.blurEnabled,
    ) { padding ->
        MainDestinationContent(
            destination = selectedDestination,
            connectionSection = connectionSection,
            onConnectionSectionChange = { connectionSection = it },
            viewModel = viewModel,
            appearance = appearance,
            onAppearanceChange = onAppearanceChange,
            systemAccentColor = systemAccentColor,
            vehicleSection = vehicleSection,
            onVehicleSectionChange = { vehicleSection = it },
            vehicleStatus = vehicleStatus,
            vehicleConnected = vehicleConnected &&
                bleState == BleConnectionState.Connected,
            vehicleProfile = vehicleProfile,
            vehicleProfiles = vehicleProfiles,
            onSelectVehicle = onSelectVehicle,
            onRefreshVehicleProfile = onRefreshVehicleProfile,
            vehicleProfilesRefreshing = vehicleProfilesRefreshing,
            vehicleProfilesError = vehicleProfilesError,
            vehicleBackgroundContent = vehicleBackgroundContent,
            vehicleImageContent = vehicleImageContent,
            onOpenExternalUrl = onOpenExternalUrl,
            authController = authController,
            carSettingsController = carSettingsController,
            otaController = otaController,
            vehicleControlController = vehicleControlController,
            vehicleConnectionPreferences = vehicleConnectionPreferences,
            onVehicleAutoConnectChange = onVehicleAutoConnectChange,
            onVehicleForegroundServiceChange = onVehicleForegroundServiceChange,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
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
fun appPagePadding(scaffoldPadding: PaddingValues): PaddingValues {
    val useMiuix = LocalUiThemeStyle.current == UiThemeStyle.Miuix
    val dimensions = AppDesign.dimensions
    // useMiuix 沿用你原来那处的取法
    val horizontal = if (useMiuix) 0.dp else dimensions.pageHorizontalPadding
    val vertical = if (useMiuix) 0.dp else dimensions.pageVerticalPadding
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical + scaffoldPadding.calculateTopPadding(),
        bottom = vertical + scaffoldPadding.calculateBottomPadding(),
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
    vehicleSection: VehicleSection,
    onVehicleSectionChange: (VehicleSection) -> Unit,
    vehicleStatus: TtpInfo?,
    vehicleConnected: Boolean,
    vehicleProfile: VehicleProfile?,
    vehicleProfiles: List<VehicleProfile>,
    onSelectVehicle: (String) -> Unit,
    onRefreshVehicleProfile: (() -> Unit)?,
    vehicleProfilesRefreshing: Boolean,
    vehicleProfilesError: String?,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)?,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)?,
    onOpenExternalUrl: ((String) -> Unit)?,
    authController: AuthController,
    carSettingsController: CarSettingsController,
    otaController: OtaController,
    vehicleControlController: VehicleControlController,
    vehicleConnectionPreferences: top.ghhccghk.yadea.settings.VehicleConnectionPreferences,
    onVehicleAutoConnectChange: (Boolean) -> Unit,
    onVehicleForegroundServiceChange: (Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    scrollBehavior: AppScrollBehavior? = null,
    snackbarHostState: AppSnackbarHostState? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            // 连接页承载两个子功能：设备连接与投屏。
            MainDestination.Connection -> ConnectionHubScreen(
                section = connectionSection,
                contentPadding = appPagePadding(contentPadding),       // 新增
                onSectionChange = onConnectionSectionChange,
                viewModel = viewModel,
                scrollBehavior = scrollBehavior,
                snackbarHostState = snackbarHostState,
            )
            MainDestination.Device -> VehicleHubScreen(
                section = vehicleSection,
                onSectionChange = onVehicleSectionChange,
                status = vehicleStatus,
                connected = vehicleConnected,
                profile = vehicleProfile,
                profiles = vehicleProfiles,
                onSelectVehicle = onSelectVehicle,
                onRefreshVehicles = onRefreshVehicleProfile,
                vehiclesRefreshing = vehicleProfilesRefreshing,
                vehiclesErrorMessage = vehicleProfilesError,
                vehicleBackgroundContent = vehicleBackgroundContent,
                vehicleImageContent = vehicleImageContent,
                onOpenIotRenewal = onOpenExternalUrl,
                authController = authController,
                carSettingsController = carSettingsController,
                otaController = otaController,
                vehicleControlController = vehicleControlController,
                contentPadding = contentPadding,
            )
            MainDestination.Settings -> SettingsScreen(
                appearance = appearance,
                contentPadding = appPagePadding(contentPadding),       // 新增
                onAppearanceChange = onAppearanceChange,
                vehicleAutoConnect = vehicleConnectionPreferences.autoConnect,
                vehicleForegroundService = vehicleConnectionPreferences.foregroundService,
                onVehicleAutoConnectChange = onVehicleAutoConnectChange,
                onVehicleForegroundServiceChange = onVehicleForegroundServiceChange,
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
    contentPadding: PaddingValues,
) {
    val dimensions = AppDesign.dimensions
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(contentPadding),) {
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
