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
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.hiddenapibypass.LSPass
import top.ghhccghk.yadea.ble.AndroidBleAdapter
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.SharedPreferencesPreferenceStore
import top.ghhccghk.yadea.network.createHttpTransport
import top.ghhccghk.yadea.ui.MainScreen
import top.ghhccghk.yadea.vehicle.BleVehicleStatusSource
import top.ghhccghk.yadea.vehicle.MutableVehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleProfileController
import top.ghhccghk.yadea.vehicle.YadeaVehicleProfileGateway
import top.ghhccghk.yadea.wifi.AndroidWifiP2pManager
import top.ghhccghk.yadea.mirror.source.AndroidFrameSourceFactory
import top.ghhccghk.yadea.mirror.MediaSourcePicker
import top.ghhccghk.yadea.mirror.ScreenCaptureAuthorizer
import top.ghhccghk.yadea.ui.AndroidPermissionChecker

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.MODEL != "robolectric") {
            HiddenApiBypass.setHiddenApiExemptions("")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LSPass.setHiddenApiExemptions("")
        }

        requestPermissionsIfNeeded()

        val p2pManager = AndroidWifiP2pManager(this)
        val vehicleBleAdapter = AndroidBleAdapter(this).also { bleAdapter = it }
        val vehicleStatus = BleVehicleStatusSource(vehicleBleAdapter, lifecycleScope)
            .also { vehicleStatusSource = it }
        val appPreferences = AppPreferences(SharedPreferencesPreferenceStore(this))
        val vehicleProfileSource = MutableVehicleProfileSource()
        val profileController = VehicleProfileController(
            source = vehicleProfileSource,
            gateway = YadeaVehicleProfileGateway(
                transport = createHttpTransport(),
                tokenProvider = { appPreferences.readAuth().token },
                loginIdProvider = { appPreferences.readAuth().loginId },
            ),
            scope = lifecycleScope,
        )
        profileController.refresh()
        val frameSourceFactory = AndroidFrameSourceFactory(this, this)
        val permissionChecker = AndroidPermissionChecker(this)
        val screenCaptureAuthorizer: ScreenCaptureAuthorizer = AndroidScreenCaptureAuthorizer(this)
        val mediaSourcePicker: MediaSourcePicker = AndroidMediaSourcePicker(this)

        setContent {
            MainScreen(
                bleAdapter = vehicleBleAdapter,
                vehicleStatusSource = vehicleStatus,
                vehicleProfileSource = vehicleProfileSource,
                carSettingCommandSink = AndroidVehicleSettingCommandSink(vehicleBleAdapter),
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
                vehicleImageContent = { url, modifier ->
                    AndroidVehicleImage(url, modifier)
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
        bleAdapter?.close()
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
