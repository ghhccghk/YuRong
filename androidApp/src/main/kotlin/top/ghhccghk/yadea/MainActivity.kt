package top.ghhccghk.yadea

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.hiddenapibypass.LSPass
import top.ghhccghk.yadea.ble.AndroidBleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.carsetting.VehicleSettingCommand
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.AndroidSecretStore
import top.ghhccghk.yadea.settings.SharedPreferencesPreferenceStore
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.createHttpTransport
import top.ghhccghk.yadea.auth.YadeaAuthGateway
import top.ghhccghk.yadea.carsetting.YadeaCarSettingsGateway
import top.ghhccghk.yadea.instrumentskin.AndroidInstrumentImageContext
import top.ghhccghk.yadea.image.AndroidImageCacheContext
import top.ghhccghk.yadea.ota.YadeaOtaGateway
import top.ghhccghk.yadea.ota.WifiOtaInstaller
import top.ghhccghk.yadea.ui.MainScreen
import top.ghhccghk.yadea.ui.MainScreenStartDestination
import top.ghhccghk.yadea.ui.initAndroidFileLogger
import top.ghhccghk.yadea.widget.VehicleStatusWidgetCoordinator
import top.ghhccghk.yadea.widget.VehicleStatusWidgetUpdater
import top.ghhccghk.yadea.vehicle.BleVehicleStatusSource
import top.ghhccghk.yadea.vehicle.MutableVehicleCloudThemeSource
import top.ghhccghk.yadea.vehicle.MutableVehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleCloudThemeController
import top.ghhccghk.yadea.vehicle.VehicleProfileController
import top.ghhccghk.yadea.vehicle.VehicleCloudStatusController
import top.ghhccghk.yadea.vehicle.YadeaVehicleCloudControlGateway
import top.ghhccghk.yadea.vehicle.VehicleBindingController
import top.ghhccghk.yadea.vehicle.YadeaVehicleProfileGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleCloudStatusGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleBindingGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleBleTokenGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleAccountRoleGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleFaultGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleCloudThemeGateway
import top.ghhccghk.yadea.vehicle.YadeaLostModeGateway
import top.ghhccghk.yadea.vehicle.EmergencyContactController
import top.ghhccghk.yadea.vehicle.YadeaEmergencyContactGateway
import top.ghhccghk.yadea.wifi.AndroidWifiP2pManager
import top.ghhccghk.yadea.wifi.AndroidWifiConnectionManager
import top.ghhccghk.yadea.mirror.source.AndroidFrameSourceFactory
import top.ghhccghk.yadea.mirror.MediaSourcePicker
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.mirror.toP2pMacAddress
import top.ghhccghk.yadea.ui.AndroidPermissionChecker
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var bleAdapter: AndroidBleAdapter? = null
    private var vehicleStatusSource: BleVehicleStatusSource? = null
    private var vehicleStatusWidgetCoordinator: VehicleStatusWidgetCoordinator? = null
    private val requestedStartDestination =
        mutableStateOf<MainScreenStartDestination?>(null)
    private val startDestinationRequestVersion = mutableStateOf(0)

    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add("android.permission.NEARBY_WIFI_DEVICES")
            }
        }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // 部分权限被拒绝
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleStartDestinationIntent(intent)
        initAndroidFileLogger(applicationContext)
        AndroidInstrumentImageContext.init(applicationContext)
        AndroidImageCacheContext.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.MODEL != "robolectric") {
            HiddenApiBypass.setHiddenApiExemptions("")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LSPass.setHiddenApiExemptions("")
        }

        requestPermissionsIfNeeded()

        val p2pManager = AndroidWifiP2pManager(this)
        val wifiConnectionManager = AndroidWifiConnectionManager(this)
        val vehicleBleAdapter = AndroidVehicleBleSession.get(this).also { bleAdapter = it }
        val appPreferences = AppPreferences(
            store = SharedPreferencesPreferenceStore(this),
            secretStore = AndroidSecretStore(this),
        )
        lifecycleScope.launch {
            appPreferences.migrateLegacyAuthProfile()
        }
        lifecycleScope.launch {
            vehicleBleAdapter.connectionState.collect { state ->
                when (state) {
                    BleConnectionState.Connected,
                    BleConnectionState.DiscoveringServices,
                    BleConnectionState.Ready,
                    -> if (appPreferences.readVehicleConnection().foregroundService) {
                        VehicleConnectionService.start(this@MainActivity)
                    } else {
                        VehicleConnectionService.stop(this@MainActivity)
                    }

                    BleConnectionState.Disconnected,
                    BleConnectionState.Failed,
                    -> VehicleConnectionService.stop(this@MainActivity)

                    else -> Unit
                }
            }
        }
        val bootstrapDeviceId = appPreferences.readAuth().loginId.ifBlank {
            UUID.randomUUID().toString()
        }
        lifecycleScope.launch {
            appPreferences.ensureDeviceId(bootstrapDeviceId)
        }
        val deviceIdProvider = {
            appPreferences.readAuth().loginId.ifBlank { bootstrapDeviceId }
        }
        val vehicleProfileSource = MutableVehicleProfileSource()
        val vehicleCloudThemeSource = MutableVehicleCloudThemeSource()
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val clientInfoProvider = {
            GatewayClientInfo(
                appVersion = packageInfo.versionName.orEmpty(),
                appVersionNum = packageInfo.versionCode.toString(),
                appMarket = applicationInfo.metaData
                    ?.getString("BUGLY_APP_CHANNEL")
                    .orEmpty()
                    .ifBlank { "Yadea" },
                appVersionType = "1",
                deviceBrand = Build.BRAND,
                phoneModel = Build.MODEL,
                // 原 App 按当前车辆系列的 seriesCode 发送；车辆资料尚未加载时回退 SERIES_4。
                bikeSeriesCode = vehicleProfileSource.profile.value?.seriesCode
                    ?.takeIf { it.isNotBlank() }
                    ?: "4",
            )
        }
        val httpTransport = createHttpTransport()
        val vehicleBleTokenGateway = YadeaVehicleBleTokenGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val vehicleAccountRoleGateway = YadeaVehicleAccountRoleGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val vehicleStatus = BleVehicleStatusSource(
            adapter = vehicleBleAdapter,
            scope = lifecycleScope,
            cache = appPreferences,
            vinFlow = vehicleProfileSource.profile.map { it?.vin.orEmpty() },
        ).also { vehicleStatusSource = it }
        val profileController = VehicleProfileController(
            source = vehicleProfileSource,
            gateway = YadeaVehicleProfileGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                loginIdProvider = deviceIdProvider,
                clientInfoProvider = clientInfoProvider,
                timestampProvider = { System.currentTimeMillis() },
            ),
            scope = lifecycleScope,
        )
        if (appPreferences.readAuth().token.isNotBlank()) {
            profileController.refresh()
        }
        val cloudThemeController = VehicleCloudThemeController(
            source = vehicleCloudThemeSource,
            gateway = YadeaVehicleCloudThemeGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                deviceIdProvider = deviceIdProvider,
                clientInfoProvider = clientInfoProvider,
                timestampProvider = { System.currentTimeMillis() },
            ),
            scope = lifecycleScope,
            cache = appPreferences,
        )
        val cloudStatusController = VehicleCloudStatusController(
            gateway = YadeaVehicleCloudStatusGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                loginIdProvider = deviceIdProvider,
                clientInfoProvider = clientInfoProvider,
                timestampProvider = { System.currentTimeMillis() },
            ),
            scope = lifecycleScope,
        )
        // 小部件不依赖 MainScreen 是否仍在组合中。蓝牙一旦断开就在 Activity
        // 生命周期内主动刷新云端状态，供车辆页和小部件共同读取。
        lifecycleScope.launch {
            combine(
                vehicleProfileSource.profile
                    .map { it?.vin.orEmpty() }
                    .distinctUntilChanged(),
                vehicleStatus.connected,
            ) { vin, connected -> vin to connected }
                .collect { (vin, connected) ->
                    if (vin.isNotBlank() && !connected) {
                        cloudStatusController.refresh(vin)
                    }
                }
        }
        VehicleStatusWidgetCoordinator(
            context = applicationContext,
            scope = lifecycleScope,
            profileSource = vehicleProfileSource,
            statusSource = vehicleStatus,
            cloudThemeSource = vehicleCloudThemeSource,
            cloudStatusSource = cloudStatusController.state,
            preferences = appPreferences,
            updater = VehicleStatusWidgetUpdater(applicationContext),
        ).also {
            vehicleStatusWidgetCoordinator = it
            it.start()
        }
        val vehicleFaultGateway = YadeaVehicleFaultGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val lostModeGateway = YadeaLostModeGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val vehicleBindingController = VehicleBindingController(
            gateway = YadeaVehicleBindingGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                deviceIdProvider = deviceIdProvider,
                clientInfoProvider = clientInfoProvider,
                timestampProvider = { System.currentTimeMillis() },
            ),
            scope = lifecycleScope,
            onChanged = { profileController.refresh() },
        )
        val emergencyContactController = EmergencyContactController(
            gateway = YadeaEmergencyContactGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                deviceIdProvider = deviceIdProvider,
                clientInfoProvider = clientInfoProvider,
                timestampProvider = { System.currentTimeMillis() },
            ),
            vinProvider = { vehicleProfileSource.profile.value?.vin.orEmpty() },
            scope = lifecycleScope,
        )
        val authGateway = YadeaAuthGateway(
            transport = httpTransport,
            timestampProvider = { System.currentTimeMillis() },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            sessionTokenProvider = { appPreferences.readAuth().token },
        )
        val otaGateway = YadeaOtaGateway(
            transport = httpTransport,
            profileProvider = { vehicleProfileSource.profile.value },
            tokenProvider = { appPreferences.readAuth().token },
            loginIdProvider = deviceIdProvider,
            timestampProvider = { System.currentTimeMillis() },
            clientInfoProvider = clientInfoProvider,
            bleAdapter = vehicleBleAdapter,
            wifiOtaInstaller = WifiOtaInstaller(
                p2pManager = p2pManager,
                localIpAddressProvider = { wifiConnectionManager.getLocalIpAddress() },
                prepareWifi = {
                    val bleReady = vehicleBleAdapter.connectionState.value ==
                        BleConnectionState.Connected ||
                        vehicleBleAdapter.connectionState.value ==
                        BleConnectionState.Ready
                    if (!bleReady) {
                        false
                    } else {
                        val sent = AndroidVehicleSettingCommandSink(vehicleBleAdapter).send(
                            VehicleSettingCommand(
                                commandType = "COMMAND_OPEN_WIFI_P2P_NEW",
                                commandId = 0xFD,
                            ),
                        )
                        sent && (withTimeoutOrNull(20_000L) {
                            while (vehicleStatus.status.value?.extended?.wifiStatusNew != 2) {
                                delay(250L)
                            }
                            true
                        } ?: false)
                    }
                },
                expectedP2pAddressProvider = {
                    toP2pMacAddress(vehicleBleAdapter.connectedAddress).orEmpty()
                },
                expectedDvrNameProvider = {
                    top.ghhccghk.yadea.ota.WifiOtaProtocol
                        .dvrP2pName(vehicleBleAdapter.connectedAddress)
                        .orEmpty()
                },
                scope = lifecycleScope,
            ),
        )
        val carSettingsGateway = YadeaCarSettingsGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val frameSourceFactory = AndroidFrameSourceFactory(this, this)
        val permissionChecker = AndroidPermissionChecker(this)
        val screenCaptureAuthorizer: ScreenCaptureAuthorizer = AndroidScreenCaptureAuthorizer(this)
        val mediaSourcePicker: MediaSourcePicker = AndroidMediaSourcePicker(this)
        val instrumentImagePicker = AndroidInstrumentImagePicker(this)
        val vehicleControlSink = AndroidVehicleControlSink(
            adapter = vehicleBleAdapter,
            bikeBondTypeHexProvider = {
                vehicleProfileSource.profile.value?.bikeBondTypeHex ?: "C8"
            },
        )
        val vehicleCloudControlSink = YadeaVehicleCloudControlGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = clientInfoProvider,
            timestampProvider = { System.currentTimeMillis() },
        )
        val carSettingCommandSink = AndroidVehicleSettingCommandSink(vehicleBleAdapter)

        fun refreshCloudTheme() {
            val profile = vehicleProfileSource.profile.value ?: return
            cloudThemeController.refresh(
                vin = profile.vin,
                modelNo = profile.modelNo,
                seriesCode = profile.seriesCode,
            )
        }

        setContent {
            val vehicleProfilesRefreshing by profileController.refreshing.collectAsState()
            val vehicleProfilesError by profileController.lastError.collectAsState()
            val vehicleCloudStatus by cloudStatusController.state.collectAsState()
            MainScreen(
                bleAdapter = vehicleBleAdapter,
                bleTokenProvider = vehicleBleTokenGateway,
                vehicleAccountRoleProvider = vehicleAccountRoleGateway,
                vehicleControlSink = vehicleControlSink,
                vehicleCloudControlSink = vehicleCloudControlSink,
                vehicleStatusSource = vehicleStatus,
                vehicleProfileSource = vehicleProfileSource,
                vehicleCloudThemeSource = vehicleCloudThemeSource,
                carSettingCommandSink = carSettingCommandSink,
                authGateway = authGateway,
                otaGateway = otaGateway,
                carSettingsGateway = carSettingsGateway,
                p2pManager = p2pManager,
                frameSourceFactory = frameSourceFactory,
                permissionChecker = permissionChecker,
                screenCaptureAuthorizer = screenCaptureAuthorizer,
                mediaSourcePicker = mediaSourcePicker,
                instrumentImagePicker = instrumentImagePicker,
                preferences = appPreferences,
                onOpenExternalUrl = { url ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onRefreshVehicleProfile = {
                    profileController.refresh()
                    refreshCloudTheme()
                },
                onRefreshVehicleCloudTheme = ::refreshCloudTheme,
                vehicleProfilesRefreshing = vehicleProfilesRefreshing,
                vehicleProfilesError = vehicleProfilesError,
                vehicleCloudStatus = vehicleCloudStatus,
                vehicleFaultGateway = vehicleFaultGateway,
                lostModeGateway = lostModeGateway,
                onRefreshVehicleCloudStatus = cloudStatusController::refresh,
                vehicleBindingController = vehicleBindingController,
                emergencyContactController = emergencyContactController,
                vehicleBackgroundContent = { url, modifier ->
                    AndroidVehicleBackgroundImage(url, modifier)
                },
                vehicleImageContent = { url, modifier ->
                    AndroidVehicleBodyImage(url, modifier)
                },
                systemAccentColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Color(ContextCompat.getColor(this@MainActivity, android.R.color.system_accent1_500))
                } else {
                    null
                },
                startDestination = requestedStartDestination.value
                    ?: MainScreenStartDestination.Connection,
                destinationRequest = requestedStartDestination.value,
                destinationRequestVersion = startDestinationRequestVersion.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartDestinationIntent(intent)
    }

    private fun handleStartDestinationIntent(intent: Intent?) {
        val destinationName = intent
            ?.getStringExtra(EXTRA_START_DESTINATION)
            ?: return
        val destination = runCatching {
            MainScreenStartDestination.valueOf(destinationName)
        }.getOrNull() ?: return
        requestedStartDestination.value = destination
        startDestinationRequestVersion.value += 1
    }

    override fun onDestroy() {
        vehicleStatusWidgetCoordinator?.stop()
        vehicleStatusWidgetCoordinator = null
        vehicleStatusSource?.dispose()
        vehicleStatusSource = null
        if (isFinishing) {
            VehicleConnectionService.stop(this)
            AndroidVehicleBleSession.close()
            bleAdapter = null
        }
        super.onDestroy()
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val EXTRA_START_DESTINATION = "top.ghhccghk.yadea.START_DESTINATION"
    }
}
