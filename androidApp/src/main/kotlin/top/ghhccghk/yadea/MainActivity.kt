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
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.hiddenapibypass.LSPass
import top.ghhccghk.yadea.ble.AndroidBleAdapter
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.SharedPreferencesPreferenceStore
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.createHttpTransport
import top.ghhccghk.yadea.auth.YadeaAuthGateway
import top.ghhccghk.yadea.carsetting.YadeaCarSettingsGateway
import top.ghhccghk.yadea.ota.YadeaOtaGateway
import top.ghhccghk.yadea.ui.MainScreen
import top.ghhccghk.yadea.ui.initAndroidFileLogger
import top.ghhccghk.yadea.vehicle.BleVehicleStatusSource
import top.ghhccghk.yadea.vehicle.MutableVehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleProfileController
import top.ghhccghk.yadea.vehicle.YadeaVehicleProfileGateway
import top.ghhccghk.yadea.vehicle.YadeaVehicleBleTokenGateway
import top.ghhccghk.yadea.wifi.AndroidWifiP2pManager
import top.ghhccghk.yadea.mirror.source.AndroidFrameSourceFactory
import top.ghhccghk.yadea.mirror.MediaSourcePicker
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.ui.AndroidPermissionChecker
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var bleAdapter: AndroidBleAdapter? = null
    private var vehicleStatusSource: BleVehicleStatusSource? = null

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
        initAndroidFileLogger(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.MODEL != "robolectric") {
            HiddenApiBypass.setHiddenApiExemptions("")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LSPass.setHiddenApiExemptions("")
        }

        requestPermissionsIfNeeded()

        val p2pManager = AndroidWifiP2pManager(this)
        val vehicleBleAdapter = AndroidVehicleBleSession.get(this).also { bleAdapter = it }
        val vehicleStatus = BleVehicleStatusSource(vehicleBleAdapter, lifecycleScope)
            .also { vehicleStatusSource = it }
        val appPreferences = AppPreferences(SharedPreferencesPreferenceStore(this))
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
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val clientInfo = GatewayClientInfo(
            appVersion = packageInfo.versionName.orEmpty(),
            appVersionNum = packageInfo.versionCode.toString(),
            appMarket = applicationInfo.metaData
                ?.getString("BUGLY_APP_CHANNEL")
                .orEmpty()
                .ifBlank { "Yadea" },
            appVersionType = "1",
            deviceBrand = Build.BRAND,
            phoneModel = Build.MODEL,
            // CarType.SERIES_4.key；没有云端车辆系列时原客户端也回退到该值。
            bikeSeriesCode = "4",
        )
        val httpTransport = createHttpTransport()
        val vehicleBleTokenGateway = YadeaVehicleBleTokenGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = { clientInfo },
            timestampProvider = { System.currentTimeMillis() },
        )
        val vehicleProfileSource = MutableVehicleProfileSource()
        val profileController = VehicleProfileController(
            source = vehicleProfileSource,
            gateway = YadeaVehicleProfileGateway(
                transport = httpTransport,
                tokenProvider = { appPreferences.readAuth().token },
                loginIdProvider = deviceIdProvider,
                clientInfoProvider = { clientInfo },
            ),
            scope = lifecycleScope,
        )
        profileController.refresh()
        val authGateway = YadeaAuthGateway(
            transport = httpTransport,
            timestampProvider = { System.currentTimeMillis() },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = { clientInfo },
            sessionTokenProvider = { appPreferences.readAuth().token },
        )
        val otaGateway = YadeaOtaGateway(
            transport = httpTransport,
            profileProvider = { vehicleProfileSource.profile.value },
            tokenProvider = { appPreferences.readAuth().token },
            loginIdProvider = deviceIdProvider,
            timestampProvider = { System.currentTimeMillis() },
            clientInfoProvider = { clientInfo },
        )
        val carSettingsGateway = YadeaCarSettingsGateway(
            transport = httpTransport,
            tokenProvider = { appPreferences.readAuth().token },
            deviceIdProvider = deviceIdProvider,
            clientInfoProvider = { clientInfo },
            timestampProvider = { System.currentTimeMillis() },
        )
        val frameSourceFactory = AndroidFrameSourceFactory(this, this)
        val permissionChecker = AndroidPermissionChecker(this)
        val screenCaptureAuthorizer: ScreenCaptureAuthorizer = AndroidScreenCaptureAuthorizer(this)
        val mediaSourcePicker: MediaSourcePicker = AndroidMediaSourcePicker(this)

        setContent {
            MainScreen(
                bleAdapter = vehicleBleAdapter,
                bleTokenProvider = vehicleBleTokenGateway,
                vehicleControlSink = AndroidVehicleControlSink(vehicleBleAdapter),
                vehicleStatusSource = vehicleStatus,
                vehicleProfileSource = vehicleProfileSource,
                carSettingCommandSink = AndroidVehicleSettingCommandSink(vehicleBleAdapter),
                authGateway = authGateway,
                otaGateway = otaGateway,
                carSettingsGateway = carSettingsGateway,
                p2pManager = p2pManager,
                frameSourceFactory = frameSourceFactory,
                permissionChecker = permissionChecker,
                screenCaptureAuthorizer = screenCaptureAuthorizer,
                mediaSourcePicker = mediaSourcePicker,
                preferences = appPreferences,
                onOpenExternalUrl = { url ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                onRefreshVehicleProfile = profileController::refresh,
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
            )
        }
    }

    override fun onDestroy() {
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
}
