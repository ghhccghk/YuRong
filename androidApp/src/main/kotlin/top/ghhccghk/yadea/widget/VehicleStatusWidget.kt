package top.ghhccghk.yadea.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.ghhccghk.yadea.AndroidVehicleBleSession
import top.ghhccghk.yadea.AndroidVehicleControlSink
import top.ghhccghk.yadea.MainActivity
import top.ghhccghk.yadea.R
import top.ghhccghk.yadea.ble.BleConnectionState
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.createHttpTransport
import top.ghhccghk.yadea.image.AndroidImageCacheContext
import top.ghhccghk.yadea.image.AppImages
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.AndroidSecretStore
import top.ghhccghk.yadea.settings.SharedPreferencesPreferenceStore
import top.ghhccghk.yadea.settings.VehicleWidgetProfileCache
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleCloudTheme
import top.ghhccghk.yadea.vehicle.VehicleCloudThemeSource
import top.ghhccghk.yadea.vehicle.VehicleCloudStatus
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehiclePowerStateResolver
import top.ghhccghk.yadea.vehicle.YadeaVehicleCloudControlGateway
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleStatusSource
import top.ghhccghk.yadea.vehicle.decodeCachedVehicleStatus
import top.ghhccghk.yadea.vehicle.withColorTypeOverride

private const val WIDGET_UPDATE_INTERVAL_MS = 5_000L
private const val VEHICLE_IMAGE_MAX_DIMENSION = 384
private const val VEHICLE_BACKGROUND_MAX_DIMENSION = 960
private const val WIDGET_ACTION_UNLOCK = "UNLOCK"
private const val WIDGET_ACTION_LOCK = "LOCK"
private const val WIDGET_ACTION_FIND = "FIND"
private const val WIDGET_ACTION_POWER_TOGGLE = "POWER_TOGGLE"
private const val CONTROL_MESSAGE_VISIBLE_MS = 15_000L
private val WidgetControlActionKey =
    ActionParameters.Key<String>("vehicle_widget_control_action")

class VehicleStatusWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        AndroidImageCacheContext.init(appContext)
        val appPreferences = AppPreferences(
            store = SharedPreferencesPreferenceStore(appContext),
        )
        val fallbackAppearance = appPreferences.read()
        val fallbackWidgetProfile = appPreferences.readVehicleWidgetProfile()
        val cachedStatus = appPreferences.readVehicleStatus()
        val fallbackStatus = cachedStatus
            ?.let { decodeCachedVehicleStatus(it.packetHex) }
        val fallbackCloudTheme = cachedStatus
            ?.vin
            ?.takeIf(String::isNotBlank)
            ?.let(appPreferences::readVehicleCloudTheme)
            ?.withColorTypeOverride(appPreferences.readVehicleCloudThemeColorOverride())
        val fallbackBackgroundImageUrl = fallbackCloudTheme
            ?.bannerVehicleBgImgUrl(resolveDarkTheme(appContext, fallbackAppearance.themeMode))
            .orEmpty()
            .ifBlank { fallbackCloudTheme?.loveCarImageUrl.orEmpty() }

        provideContent {
            val persistedState = currentState<Preferences>()
            val state = VehicleStatusWidgetState.fromPreferences(
                preferences = persistedState,
                fallbackAppearance = fallbackAppearance,
                fallbackStatus = fallbackStatus,
                fallbackBackgroundImageUrl = fallbackBackgroundImageUrl,
                fallbackVehicleProfile = fallbackWidgetProfile,
            )
            VehicleStatusWidgetContent(state)
        }
    }
}

class VehicleStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VehicleStatusWidget()
}

/**
 * 小部件车控入口。
 *
 * 只允许已确认的经典 BLE 命令；当前没有可用的车辆蓝牙连接时不会自动建立连接或
 * 回退到云端下发，避免桌面误触产生不可见状态。
 */
class VehicleWidgetControlAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val action = parameters[WidgetControlActionKey] ?: return
        val appContext = context.applicationContext
        val preferences = getAppWidgetState<Preferences>(
            context = appContext,
            definition = PreferencesGlanceStateDefinition,
            glanceId = glanceId,
        )
        val command = when (action) {
            WIDGET_ACTION_UNLOCK -> VehicleControlCommand.UNLOCK
            WIDGET_ACTION_LOCK -> VehicleControlCommand.LOCK
            WIDGET_ACTION_FIND -> VehicleControlCommand.FIND
            WIDGET_ACTION_POWER_TOGGLE -> {
                if (preferences[KEY_KEY_ON] == true) {
                    completeControlAction(
                        context = appContext,
                        glanceId = glanceId,
                        message = "机械钥匙已开机，请使用机械钥匙操作车辆电源",
                    )
                    return
                }
                if (preferences[KEY_POWER_ON] == true) {
                    VehicleControlCommand.POWER_OFF
                } else {
                    VehicleControlCommand.POWER_ON
                }
            }
            else -> return
        }

        val bondTypeHex = preferences[KEY_BIKE_BOND_TYPE]
            .orEmpty()
            .ifBlank { "C8" }
        val result = VehicleWidgetCommandSender.send(
            context = appContext,
            command = command,
            bondTypeHex = bondTypeHex,
        )
        completeControlAction(
            context = appContext,
            glanceId = glanceId,
            message = result.message,
        )
    }
}

private data class VehicleWidgetControlResult(
    val success: Boolean,
    val message: String,
)

private object VehicleWidgetCommandSender {
    private val mutex = Mutex()

    suspend fun send(
        context: Context,
        command: VehicleControlCommand,
        bondTypeHex: String,
    ): VehicleWidgetControlResult = mutex.withLock {
        val adapter = AndroidVehicleBleSession.get(context)
        val connectionState = adapter.connectionState.value
        val bleReady = connectionState == BleConnectionState.Connected ||
            connectionState == BleConnectionState.Ready
        if (bleReady) {
            val bleSuccess = try {
                AndroidVehicleControlSink(
                    adapter = adapter,
                    bikeBondTypeHexProvider = { bondTypeHex },
                ).send(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (bleSuccess) {
                return@withLock VehicleWidgetControlResult(
                    success = true,
                    message = "${command.label}指令已通过蓝牙发送，等待车辆状态回报",
                )
            }
        }

        if (command.cloudControlId != null) {
            return@withLock sendViaCloud(context, command)
        }

        VehicleWidgetControlResult(
            success = false,
            message = "${command.label}蓝牙发送失败，且该指令不支持车联网下发",
        )
    }

    private suspend fun sendViaCloud(
        context: Context,
        command: VehicleControlCommand,
    ): VehicleWidgetControlResult {
        val appContext = context.applicationContext
        val preferences = AppPreferences(
            store = SharedPreferencesPreferenceStore(appContext),
            secretStore = AndroidSecretStore(appContext),
        )
        val auth = preferences.readAuth()
        if (auth.token.isBlank()) {
            return VehicleWidgetControlResult(false, "登录状态已失效，请打开应用重新登录")
        }
        val profile = preferences.readVehicleWidgetProfile()
        if (profile.vin.isBlank()) {
            return VehicleWidgetControlResult(false, "缺少车辆 VIN，请打开应用刷新车辆资料")
        }
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val appMarket = appContext.applicationInfo.metaData
            ?.getString("BUGLY_APP_CHANNEL")
            .orEmpty()
            .ifBlank { "Yadea" }
        val result = try {
            YadeaVehicleCloudControlGateway(
                transport = createHttpTransport(),
                tokenProvider = { auth.token },
                deviceIdProvider = { auth.loginId },
                clientInfoProvider = {
                    GatewayClientInfo(
                        appVersion = packageInfo.versionName.orEmpty(),
                        appVersionNum = packageInfo.versionCode.toString(),
                        appMarket = appMarket,
                        appVersionType = "1",
                        deviceBrand = Build.BRAND,
                        phoneModel = Build.MODEL,
                        bikeSeriesCode = profile.seriesCode.ifBlank { "4" },
                    )
                },
                timestampProvider = { System.currentTimeMillis() },
            ).send(profile.vin, command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return VehicleWidgetControlResult(
                false,
                "${command.label}车联网下发失败：${error.message ?: "网络异常"}",
            )
        }
        return VehicleWidgetControlResult(
            success = result.success,
            message = if (result.success) {
                "${command.label}指令已通过车联网发送，等待车辆状态更新"
            } else {
                result.message.ifBlank { "${command.label}车联网下发失败" }
            },
        )
    }
}

private suspend fun completeControlAction(
    context: Context,
    glanceId: GlanceId,
    message: String,
) {
    val messageAt = System.currentTimeMillis()
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences[KEY_CONTROL_MESSAGE] = message
        preferences[KEY_CONTROL_MESSAGE_AT] = messageAt
    }
    try {
        VehicleStatusWidget().update(context, glanceId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // 状态写入成功即可，刷新失败会在下一次小部件更新时恢复。
    }
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    delay(CONTROL_MESSAGE_VISIBLE_MS)
    updateAppWidgetState(context, glanceId) { preferences ->
        if (preferences[KEY_CONTROL_MESSAGE_AT] == messageAt) {
            preferences.remove(KEY_CONTROL_MESSAGE)
            preferences.remove(KEY_CONTROL_MESSAGE_AT)
        }
    }
    try {
        VehicleStatusWidget().update(context, glanceId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // 消息已清理，刷新失败不影响后续同步。
    }
}

internal class VehicleStatusWidgetUpdater(context: Context) {
    private val appContext = context.applicationContext
    private val updateMutex = Mutex()

    suspend fun update(state: VehicleStatusWidgetState) {
        updateMutex.withLock {
            val glanceIds = try {
                GlanceAppWidgetManager(appContext)
                    .getGlanceIds(VehicleStatusWidget::class.java)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Unable to query vehicle widget instances", error)
                return
            }
            if (glanceIds.isEmpty()) return

            val widget = VehicleStatusWidget()
            glanceIds.forEach { glanceId ->
                try {
                    updateAppWidgetState(appContext, glanceId) { preferences ->
                        val mutable = preferences.toMutablePreferences()
                        if (state.hasVehicleIdentity) {
                            state.writeIdentity(mutable)
                        }
                        if (state.hasVehicleIdentity || state.hasStatus) {
                            state.writeStatus(mutable)
                        }
                        state.writeAppearance(mutable)
                    }
                    widget.update(appContext, glanceId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(TAG, "Unable to update vehicle widget $glanceId", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "VehicleStatusWidget"
    }
}

private data class CoordinatorInputs(
    val profile: VehicleProfile?,
    val status: TtpInfo?,
    val connected: Boolean,
    val cloudTheme: VehicleCloudTheme?,
    val appearance: AppAppearance,
)

/**
 * 把应用内实时状态同步到桌面小部件实例。
 *
 * 车辆资料、外观图片和主题变化立即刷新；高频车辆状态最多每五秒刷新一次。
 */
internal class VehicleStatusWidgetCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val profileSource: VehicleProfileSource,
    private val statusSource: VehicleStatusSource,
    private val cloudThemeSource: VehicleCloudThemeSource,
    private val cloudStatusSource: kotlinx.coroutines.flow.StateFlow<VehicleCloudStatus?>,
    private val preferences: AppPreferences,
    private val updater: VehicleStatusWidgetUpdater,
) {
    private var stateJob: Job? = null
    private var profileCacheJob: Job? = null
    private var refreshJob: Job? = null
    private var latestState: VehicleStatusWidgetState? = null
    private var lastVisualKey: String? = null
    private var hasPendingStatusUpdate = false

    fun start() {
        if (stateJob != null) return
        stateJob = scope.launch {
            val liveState = combine(
                profileSource.profile,
                statusSource.status,
                statusSource.connected,
                cloudThemeSource.theme,
                preferences.observe().onStart { emit(preferences.read()) },
            ) { profile, status, connected, cloudTheme, appearance ->
                CoordinatorInputs(profile, status, connected, cloudTheme, appearance)
            }
            combine(liveState, cloudStatusSource) { live, cloudStatus ->
                buildState(
                    profile = live.profile,
                    status = live.status,
                    connected = live.connected,
                    cloudTheme = live.cloudTheme,
                    cloudStatus = cloudStatus,
                    appearance = live.appearance,
                )
            }
                .distinctUntilChanged()
                .collect { state ->
                    latestState = state
                    val visualKey = state.visualKey()
                    if (visualKey != lastVisualKey) {
                        lastVisualKey = visualKey
                        hasPendingStatusUpdate = false
                        updater.update(state)
                    } else {
                        hasPendingStatusUpdate = true
                    }
                }
        }
        profileCacheJob = scope.launch {
            profileSource.profile
                .collect { profile ->
                    if (profile != null) {
                        preferences.writeVehicleWidgetProfile(profile.toWidgetProfileCache())
                    }
                }
        }
        refreshJob = scope.launch {
            while (isActive) {
                delay(WIDGET_UPDATE_INTERVAL_MS)
                if (hasPendingStatusUpdate) {
                    hasPendingStatusUpdate = false
                    latestState?.let { updater.update(it) }
                }
            }
        }
    }

    fun stop() {
        stateJob?.cancel()
        profileCacheJob?.cancel()
        refreshJob?.cancel()
        stateJob = null
        profileCacheJob = null
        refreshJob = null
    }

    private fun VehicleProfile.toWidgetProfileCache(): VehicleWidgetProfileCache =
        VehicleWidgetProfileCache(
            vin = vin,
            name = vehicleName(this),
            subtitle = vehicleSubtitle(this),
            imageUrl = backgroundImageUrl
                .takeIf(String::isNotBlank)
                ?: vehicleImageUrl,
            bikeBondTypeHex = bikeBondTypeHex,
            seriesCode = seriesCode,
        )

    private fun buildState(
        profile: VehicleProfile?,
        status: TtpInfo?,
        connected: Boolean,
        cloudTheme: VehicleCloudTheme?,
        cloudStatus: VehicleCloudStatus?,
        appearance: AppAppearance,
    ): VehicleStatusWidgetState {
        val cached = preferences.readVehicleStatus()
        val cachedStatus = cached
            ?.takeIf { profile?.vin.isNullOrBlank() || it.vin == profile.vin }
            ?.let { decodeCachedVehicleStatus(it.packetHex) }
        val effectiveStatus = status ?: cachedStatus
        val formatted = VehicleInfoFormatter.format(effectiveStatus, connected)
        val powerState = VehiclePowerStateResolver.resolve(
            status = effectiveStatus,
            cloudPowerState = cloudStatus?.powerState,
            cloudLockStatus = cloudStatus?.lockStatus,
        )
        val darkTheme = resolveDarkTheme(context, appearance.themeMode)
        val matchedCloudTheme = cloudTheme
            ?.withColorTypeOverride(preferences.readVehicleCloudThemeColorOverride())
            ?.takeIf {
                profile?.vin.isNullOrBlank() || it.vin == profile.vin
            }
        val backgroundImageUrl = matchedCloudTheme
            ?.bannerVehicleBgImgUrl(darkTheme)
            .orEmpty()
            .ifBlank { matchedCloudTheme?.loveCarImageUrl.orEmpty() }

        val vehicleName = profile?.let(::vehicleName)
        val subtitle = profile?.let(::vehicleSubtitle).orEmpty()
        val hasStatus = effectiveStatus != null || cloudStatus != null
        val cloudOnline = cloudStatus?.onlineState == true
        val connectionLabel = when {
            cloudOnline && !connected -> "车联网在线"
            hasStatus -> formatted.connectionLabel
            connected -> "已连接，等待车辆上报"
            else -> "打开应用连接车辆"
        }
        val connectionShortLabel = when {
            hasStatus && connected -> "在线"
            cloudOnline -> "云端"
            hasStatus -> "缓存"
            connected -> "等待上报"
            else -> "打开应用"
        }
        val statusSummary = if (hasStatus) {
            val chargeLabel = formatted.chargeLabel.takeIf {
                it == "未充电" || it == "充电中"
            }.orEmpty()
            listOf(formatted.lockLabel, formatted.powerLabel, chargeLabel)
                .filter { it.isNotBlank() && it != "—" }
                .joinToString(" · ")
                .ifBlank { "车辆状态已同步" }
        } else {
            connectionLabel
        }

        return VehicleStatusWidgetState(
            hasVehicleIdentity = profile != null,
            vin = profile?.vin.orEmpty(),
            vehicleName = vehicleName.orEmpty(),
            vehicleSubtitle = subtitle,
            vehicleImageUrl = profile?.backgroundImageUrl
                ?.takeIf(String::isNotBlank)
                ?: profile?.vehicleImageUrl.orEmpty(),
            backgroundImageUrl = backgroundImageUrl,
            bikeBondTypeHex = profile?.bikeBondTypeHex.orEmpty().ifBlank { "C8" },
            hasStatus = hasStatus,
            connected = connected,
            cloudOnline = cloudOnline,
            powerOn = powerState.powerOn,
            keyOn = powerState.keyOn,
            connectionLabel = connectionLabel,
            connectionShortLabel = connectionShortLabel,
            batteryLabel = VehicleInfoFormatter.preferredBatteryLabel(
                bluetoothLabel = formatted.batteryLabel,
                cloudSoc = cloudStatus?.soc,
                connected = connected,
            ),
            rangeLabel = formatted.rangeLabel.takeIf { it != "—" }
                ?: cloudStatus?.remainingMileage
                    ?.takeIf(String::isNotBlank)
                    ?.let { "$it km" }
                ?: "—",
            statusSummary = statusSummary,
            uiStyle = appearance.uiStyle,
            themeMode = appearance.themeMode,
            seedColorArgb = appearance.seedColorArgb,
            amoledDarkEnabled = appearance.amoledDarkEnabled,
        )
    }

    private fun vehicleName(profile: VehicleProfile): String =
        profile.bikeNickName
            .ifBlank { profile.modelName }
            .ifBlank { profile.seriesName }
            .ifBlank { "我的车辆" }

    private fun vehicleSubtitle(profile: VehicleProfile): String = buildList {
        if (profile.seriesName.isNotBlank() && profile.seriesName != vehicleName(profile)) {
            add(profile.seriesName)
        }
        if (
            profile.modelName.isNotBlank() &&
            profile.modelName != vehicleName(profile) &&
            profile.modelName != profile.seriesName
        ) {
            add(profile.modelName)
        }
    }.joinToString(" · ")
}

internal data class VehicleStatusWidgetState(
    val hasVehicleIdentity: Boolean = false,
    val vin: String = "",
    val vehicleName: String = "我的车辆",
    val vehicleSubtitle: String = "",
    val vehicleImageUrl: String = "",
    val backgroundImageUrl: String = "",
    val bikeBondTypeHex: String = "C8",
    val hasStatus: Boolean = false,
    val connected: Boolean = false,
    val cloudOnline: Boolean = false,
    val powerOn: Boolean? = null,
    val keyOn: Boolean? = null,
    val connectionLabel: String = "打开应用连接车辆",
    val connectionShortLabel: String = "打开应用",
    val batteryLabel: String = "—",
    val rangeLabel: String = "—",
    val statusSummary: String = "打开应用连接车辆",
    val controlMessage: String = "",
    val controlMessageAtEpochMs: Long = 0L,
    val uiStyle: UiThemeStyle = UiThemeStyle.Miuix,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val seedColorArgb: Int = 0xFF7B4DFF.toInt(),
    val amoledDarkEnabled: Boolean = false,
) {
    val hasControlChannel: Boolean
        get() = connected || cloudOnline

    fun visualKey(): String = listOf(
        hasVehicleIdentity,
        vin,
        vehicleName,
        vehicleSubtitle,
        vehicleImageUrl,
        backgroundImageUrl,
        bikeBondTypeHex,
        hasStatus,
        connected,
        cloudOnline,
        uiStyle.storageName,
        themeMode.name,
        seedColorArgb,
        amoledDarkEnabled,
    ).joinToString("|")

    fun writeIdentity(preferences: MutablePreferences) {
        preferences[KEY_HAS_VEHICLE_IDENTITY] = true
        preferences[KEY_VIN] = vin
        preferences[KEY_VEHICLE_NAME] = vehicleName
        preferences[KEY_VEHICLE_SUBTITLE] = vehicleSubtitle
        preferences[KEY_VEHICLE_IMAGE_URL] = vehicleImageUrl
        preferences[KEY_BACKGROUND_IMAGE_URL] = backgroundImageUrl
        preferences[KEY_BIKE_BOND_TYPE] = bikeBondTypeHex
    }

    fun writeStatus(preferences: MutablePreferences) {
        preferences[KEY_HAS_STATUS] = hasStatus
        preferences[KEY_CONNECTED] = connected
        preferences[KEY_CLOUD_ONLINE] = cloudOnline
        preferences[KEY_CONNECTION_LABEL] = connectionLabel
        preferences[KEY_CONNECTION_SHORT_LABEL] = connectionShortLabel
        preferences[KEY_BATTERY_LABEL] = batteryLabel
        preferences[KEY_RANGE_LABEL] = rangeLabel
        preferences[KEY_STATUS_SUMMARY] = statusSummary
        if (powerOn == null) {
            preferences.remove(KEY_POWER_ON)
        } else {
            preferences[KEY_POWER_ON] = powerOn
        }
        if (keyOn == null) {
            preferences.remove(KEY_KEY_ON)
        } else {
            preferences[KEY_KEY_ON] = keyOn
        }
    }

    fun writeAppearance(preferences: MutablePreferences) {
        preferences[KEY_UI_STYLE] = uiStyle.storageName
        preferences[KEY_THEME_MODE] = themeMode.name
        preferences[KEY_SEED_COLOR] = seedColorArgb
        preferences[KEY_AMOLED_DARK] = amoledDarkEnabled
    }

    companion object {
        fun fromPreferences(
            preferences: Preferences,
            fallbackAppearance: AppAppearance,
            fallbackStatus: TtpInfo?,
            fallbackBackgroundImageUrl: String = "",
            fallbackVehicleProfile: VehicleWidgetProfileCache = VehicleWidgetProfileCache(),
        ): VehicleStatusWidgetState {
            val fallbackFormatted = fallbackStatus
                ?.let { VehicleInfoFormatter.format(it, connected = false) }
            val fallbackPowerState = VehiclePowerStateResolver.resolve(fallbackStatus)
            val hasStatus = preferences[KEY_HAS_STATUS] ?: (fallbackStatus != null)
            val connected = preferences[KEY_CONNECTED] ?: false
            val cloudOnline = preferences[KEY_CLOUD_ONLINE] ?: false
            val connectionLabel = preferences[KEY_CONNECTION_LABEL] ?: when {
                cloudOnline && !connected -> "车联网在线"
                hasStatus -> fallbackFormatted?.connectionLabel ?: "状态缓存"
                connected -> "已连接，等待车辆上报"
                else -> "打开应用连接车辆"
            }
            val connectionShortLabel = preferences[KEY_CONNECTION_SHORT_LABEL] ?: when {
                hasStatus && connected -> "在线"
                cloudOnline -> "云端"
                hasStatus -> "缓存"
                connected -> "等待上报"
                else -> "打开应用"
            }
            val fallbackSummary = fallbackFormatted?.let { formatted ->
                val chargeLabel = formatted.chargeLabel.takeIf {
                    it == "未充电" || it == "充电中"
                }.orEmpty()
                listOf(formatted.lockLabel, formatted.powerLabel, chargeLabel)
                    .filter { value -> value.isNotBlank() && value != "—" }
                    .joinToString(" · ")
            }?.takeIf(String::isNotBlank)
            val storedVin = preferences[KEY_VIN].orEmpty()
            val storedName = preferences[KEY_VEHICLE_NAME].orEmpty()
            val storedImageUrl = preferences[KEY_VEHICLE_IMAGE_URL].orEmpty()
            val storedBackgroundImageUrl = preferences[KEY_BACKGROUND_IMAGE_URL].orEmpty()
            return VehicleStatusWidgetState(
                hasVehicleIdentity = preferences[KEY_HAS_VEHICLE_IDENTITY] == true ||
                    storedVin.isNotBlank() ||
                    fallbackVehicleProfile.vin.isNotBlank(),
                vin = fallbackVehicleProfile.vin.ifBlank { storedVin },
                vehicleName = fallbackVehicleProfile.name
                    .ifBlank { storedName }
                    .ifBlank { "我的车辆" },
                vehicleSubtitle = fallbackVehicleProfile.subtitle
                    .ifBlank { preferences[KEY_VEHICLE_SUBTITLE].orEmpty() },
                vehicleImageUrl = fallbackVehicleProfile.imageUrl
                    .ifBlank { storedImageUrl },
                backgroundImageUrl = fallbackBackgroundImageUrl
                    .ifBlank { storedBackgroundImageUrl },
                bikeBondTypeHex = (
                    preferences[KEY_BIKE_BOND_TYPE]
                        ?: fallbackVehicleProfile.bikeBondTypeHex
                    )
                    .orEmpty()
                    .ifBlank { "C8" },
                hasStatus = hasStatus,
                connected = connected,
                cloudOnline = cloudOnline,
                powerOn = preferences[KEY_POWER_ON] ?: fallbackPowerState.powerOn,
                keyOn = preferences[KEY_KEY_ON] ?: fallbackPowerState.keyOn,
                connectionLabel = connectionLabel,
                connectionShortLabel = connectionShortLabel,
                batteryLabel = preferences[KEY_BATTERY_LABEL]
                    ?: fallbackFormatted?.batteryLabel
                    ?: "—",
                rangeLabel = preferences[KEY_RANGE_LABEL]
                    ?: fallbackFormatted?.rangeLabel
                    ?: "—",
                statusSummary = fallbackSummary
                    ?: preferences[KEY_STATUS_SUMMARY]
                    ?.ifBlank { "车辆状态已同步" }
                    ?: connectionLabel,
                controlMessage = preferences[KEY_CONTROL_MESSAGE].orEmpty(),
                controlMessageAtEpochMs = preferences[KEY_CONTROL_MESSAGE_AT] ?: 0L,
                uiStyle = preferences[KEY_UI_STYLE]
                    ?.let(UiThemeStyle::fromStorageName)
                    ?: fallbackAppearance.uiStyle,
                themeMode = preferences[KEY_THEME_MODE]
                    ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: fallbackAppearance.themeMode,
                seedColorArgb = preferences[KEY_SEED_COLOR]
                    ?: fallbackAppearance.seedColorArgb,
                amoledDarkEnabled = preferences[KEY_AMOLED_DARK]
                    ?: fallbackAppearance.amoledDarkEnabled,
            )
        }
    }
}

@Composable
private fun VehicleStatusWidgetContent(state: VehicleStatusWidgetState) {
    val context = LocalContext.current
    val widgetSize = LocalSize.current
    val wide = widgetSize.width >= 220.dp
    val compact = widgetSize.height < 150.dp
    val darkTheme = resolveDarkTheme(context, state.themeMode)
    val seedColor = Color(state.seedColorArgb.toLong() and 0xFFFFFFFFL)
    val palette = widgetPalette(
        uiStyle = state.uiStyle,
        seedColor = seedColor,
        darkTheme = darkTheme,
        amoledDark = state.amoledDarkEnabled,
    )
    val cornerRadius = if (state.uiStyle == UiThemeStyle.Miuix) 28.dp else 16.dp
    var vehicleBitmap by remember(state.vehicleImageUrl) { mutableStateOf<Bitmap?>(null) }
    var backgroundBitmap by remember(state.backgroundImageUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.vehicleImageUrl) {
        vehicleBitmap = loadWidgetBitmap(
            url = state.vehicleImageUrl,
            maxDimension = VEHICLE_IMAGE_MAX_DIMENSION,
            cropTransparentBounds = true,
        )
    }
    LaunchedEffect(state.backgroundImageUrl) {
        backgroundBitmap = loadWidgetBitmap(
            url = state.backgroundImageUrl,
            maxDimension = VEHICLE_BACKGROUND_MAX_DIMENSION,
        )
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ColorProvider(palette.surface))
            .cornerRadius(cornerRadius)
            .clickable(actionStartActivity(openAppIntent)),
    ) {
        backgroundBitmap?.let { bitmap ->
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                alpha = 0.20f,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                wide && state.uiStyle == UiThemeStyle.Miuix -> MiuixWideWidgetContent(
                    state,
                    palette,
                    vehicleBitmap,
                )
                wide -> MaterialWideWidgetContent(
                    state,
                    palette,
                    vehicleBitmap,
                )
                state.uiStyle == UiThemeStyle.Miuix -> MiuixWidgetContent(
                    state = state,
                    palette = palette,
                    compact = compact,
                    vehicleBitmap = vehicleBitmap,
                )
                else -> MaterialWidgetContent(
                    state = state,
                    palette = palette,
                    compact = compact,
                    vehicleBitmap = vehicleBitmap,
                )
            }
        }
    }
}

@Composable
private fun MiuixWideWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    vehicleBitmap: Bitmap?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(15.dp),
    ) {
        MiuixHeader(state, palette, showSubtitle = false)
        Spacer(GlanceModifier.height(1.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WideMetrics(
                state = state,
                palette = palette,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(7.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight(),
            ) {
                VehicleStage(
                    state = state,
                    bitmap = vehicleBitmap,
                    palette = palette,
                    height = 0.dp,
                    radius = 18.dp,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
        }
        Spacer(GlanceModifier.height(12.dp))
        WidgetActions(state, palette, compact = false)
    }
}

@Composable
private fun MaterialWideWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    vehicleBitmap: Bitmap?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(15.dp),
    ) {
        MaterialHeader(state, palette, showSubtitle = false)
        Spacer(GlanceModifier.height(1.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WideMetrics(
                state = state,
                palette = palette,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(7.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .defaultWeight(),
            ) {
                VehicleStage(
                    state = state,
                    bitmap = vehicleBitmap,
                    palette = palette,
                    height = 0.dp,
                    radius = 0.dp,
                    useStageColor = false,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
        }
        Spacer(GlanceModifier.height(12.dp))
        WidgetActions(state, palette, compact = false)
    }
}

@Composable
private fun WideMetrics(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    val range = state.rangeLabel.replace(" ", "")
    val battery = state.batteryLabel.replace(" ", "")
    val hasRange = range.isNotBlank() && range != "—"
    val primaryValue = if (hasRange) range else battery
    val primaryLabel = if (hasRange) "续航" else "电量"
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = primaryValue,
            style = widgetTextStyle(
                color = palette.primaryText,
                size = 24.sp,
                weight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = primaryLabel,
            style = widgetTextStyle(
                color = palette.secondaryText,
                size = 8.sp,
            ),
            maxLines = 1,
        )
        if (hasRange) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = battery,
                style = widgetTextStyle(
                    color = palette.primaryText,
                    size = 16.sp,
                    weight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = "电量",
                style = widgetTextStyle(
                    color = palette.secondaryText,
                    size = 8.sp,
                ),
                maxLines = 1,
            )
        } else {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = state.displaySummary(),
                style = widgetTextStyle(
                    color = palette.secondaryText,
                    size = 8.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MiuixWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    compact: Boolean,
    vehicleBitmap: Bitmap?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(if (compact) 6.dp else 12.dp),
    ) {
        MiuixHeader(state, palette, showSubtitle = !compact)
        Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))
        VehicleStage(
            state = state,
            bitmap = vehicleBitmap,
            palette = palette,
            height = if (compact) 28.dp else 58.dp,
            radius = if (compact) 12.dp else 20.dp,
        )
        Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))
        if (compact) {
            CompactMetrics(state, palette)
        } else {
            MiuixMetrics(state, palette)
        }
        Spacer(GlanceModifier.height(2.dp))
        WidgetActions(state, palette, compact = compact)
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = state.displaySummary(),
            modifier = GlanceModifier.fillMaxWidth(),
            style = widgetTextStyle(
                color = palette.secondaryText,
                size = if (compact) 9.sp else 10.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MaterialWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    compact: Boolean,
    vehicleBitmap: Bitmap?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(if (compact) 6.dp else 11.dp),
    ) {
        MaterialHeader(state, palette, showSubtitle = !compact)
        Spacer(GlanceModifier.height(if (compact) 1.dp else 3.dp))
        VehicleStage(
            state = state,
            bitmap = vehicleBitmap,
            palette = palette,
            height = if (compact) 28.dp else 54.dp,
            radius = 0.dp,
            useStageColor = false,
        )
        Spacer(GlanceModifier.height(if (compact) 1.dp else 3.dp))
        if (!compact) {
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorProvider(palette.outline)),
            )
            Spacer(GlanceModifier.height(3.dp))
        }
        if (compact) {
            CompactMetrics(state, palette)
        } else {
            MaterialMetrics(state, palette)
        }
        Spacer(GlanceModifier.height(2.dp))
        WidgetActions(state, palette, compact = compact)
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = state.displaySummary(),
            modifier = GlanceModifier.fillMaxWidth(),
            style = widgetTextStyle(
                color = palette.secondaryText,
                size = if (compact) 9.sp else 10.sp,
            ),
            maxLines = 1,
        )
    }
}

private fun VehicleStatusWidgetState.displaySummary(
    now: Long = System.currentTimeMillis(),
): String {
    return if (
        controlMessage.isNotBlank() &&
        now - controlMessageAtEpochMs in 0L..CONTROL_MESSAGE_VISIBLE_MS
    ) {
        controlMessage
    } else {
        statusSummary
    }
}

@Composable
private fun WidgetActions(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    compact: Boolean,
) {
    if (state.hasVehicleIdentity) {
        WidgetControls(state, palette, compact)
    } else {
        ConnectVehicleBar(palette, compact)
    }
    Spacer(GlanceModifier.height(1.dp))
}

@Composable
private fun ConnectVehicleBar(
    palette: WidgetPalette,
    compact: Boolean,
) {
    val context = LocalContext.current
    val openAppAction = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (compact) 20.dp else 34.dp)
            .background(ColorProvider(palette.stage))
            .cornerRadius(if (compact) 7.dp else 16.dp)
            .clickable(openAppAction)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_bluetooth),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(ColorProvider(palette.primaryText)),
            )
            Spacer(GlanceModifier.width(5.dp))
        }
        Text(
            text = "打开应用连接车辆",
            style = widgetTextStyle(
                color = palette.primaryText,
                size = if (compact) 8.sp else 9.sp,
                weight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetControls(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    compact: Boolean,
) {
    val rowModifier = if (compact) {
        GlanceModifier.fillMaxWidth()
    } else {
        GlanceModifier
            .fillMaxWidth()
            .height(36.dp)
            .background(ColorProvider(palette.stage))
            .cornerRadius(16.dp)
            .padding(3.dp)
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetControlButton(
            label = "开锁",
            actionName = WIDGET_ACTION_UNLOCK,
            iconRes = R.drawable.ic_widget_unlock,
            enabled = state.hasControlChannel || state.hasVehicleIdentity,
            palette = palette,
            compact = compact,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = "锁车",
            actionName = WIDGET_ACTION_LOCK,
            iconRes = R.drawable.ic_widget_lock,
            enabled = state.hasControlChannel || state.hasVehicleIdentity,
            palette = palette,
            compact = compact,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = "寻车",
            actionName = WIDGET_ACTION_FIND,
            iconRes = R.drawable.ic_widget_find,
            enabled = state.hasControlChannel || state.hasVehicleIdentity,
            palette = palette,
            compact = compact,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = if (state.powerOn == true) "关机" else "开机",
            actionName = WIDGET_ACTION_POWER_TOGGLE,
            iconRes = R.drawable.ic_widget_power,
            enabled = (state.hasControlChannel || state.hasVehicleIdentity) &&
                state.keyOn != true,
            palette = palette,
            compact = compact,
        )
    }
}

@Composable
private fun RowScope.WidgetControlButton(
    label: String,
    actionName: String,
    iconRes: Int,
    enabled: Boolean,
    palette: WidgetPalette,
    compact: Boolean,
) {
    val action = actionRunCallback<VehicleWidgetControlAction>(
        actionParametersOf(WidgetControlActionKey to actionName),
    )
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(if (compact) 20.dp else 30.dp)
            .background(
                ColorProvider(
                    when {
                        compact && enabled -> palette.stage
                        !enabled -> palette.neutralPill
                        else -> Color.Transparent
                    },
                ),
            )
            .cornerRadius(if (compact) 7.dp else 10.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Text(
                text = label,
                style = widgetTextStyle(
                    color = if (enabled) palette.primaryText else palette.secondaryText,
                    size = 8.sp,
                    weight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = label,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(
                        ColorProvider(
                            if (enabled) palette.primaryText else palette.secondaryText,
                        ),
                    ),
                )
                Text(
                    text = label,
                    style = widgetTextStyle(
                        color = if (enabled) palette.primaryText else palette.secondaryText,
                        size = 8.sp,
                        weight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MiuixHeader(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    showSubtitle: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.vehicleName,
                style = widgetTextStyle(
                    color = palette.primaryText,
                    size = 14.sp,
                    weight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (showSubtitle && state.vehicleSubtitle.isNotBlank()) {
                Text(
                    text = state.vehicleSubtitle,
                    style = widgetTextStyle(
                        color = palette.secondaryText,
                        size = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = state.connectionShortLabel,
            modifier = GlanceModifier
                .background(
                    ColorProvider(
                        if (state.hasControlChannel) palette.connected else palette.neutralPill,
                    ),
                )
                .cornerRadius(12.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            style = widgetTextStyle(
                color = if (state.hasControlChannel) palette.onConnected else palette.primaryText,
                size = 9.sp,
                weight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun MaterialHeader(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    showSubtitle: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.vehicleName,
                style = widgetTextStyle(
                    color = palette.primaryText,
                    size = 14.sp,
                    weight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (showSubtitle && state.vehicleSubtitle.isNotBlank()) {
                Text(
                    text = state.vehicleSubtitle,
                    style = widgetTextStyle(
                        color = palette.secondaryText,
                        size = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = state.connectionShortLabel,
            style = widgetTextStyle(
                color = if (state.hasControlChannel) palette.connected else palette.secondaryText,
                size = 9.sp,
                weight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun VehicleStage(
    state: VehicleStatusWidgetState,
    bitmap: Bitmap?,
    palette: WidgetPalette,
    height: Dp,
    radius: Dp,
    useStageColor: Boolean = true,
    modifier: GlanceModifier? = null,
) {
    val stageModifier = modifier ?: GlanceModifier
        .fillMaxWidth()
        .height(height)
    val stageColor = if (bitmap != null) {
        Color.Transparent
    } else if (useStageColor) {
        palette.stage
    } else {
        palette.surface
    }
    Box(
        modifier = stageModifier
            .background(ColorProvider(stageColor))
            .cornerRadius(radius),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "${state.vehicleName}车辆外观",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = when {
                    state.hasVehicleIdentity -> "暂无车辆外观图"
                    else -> "打开应用查看车辆"
                },
                modifier = GlanceModifier.padding(horizontal = 8.dp),
                style = widgetTextStyle(
                    color = palette.secondaryText,
                    size = 9.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MiuixMetrics(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(palette.stage))
            .cornerRadius(14.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(
            label = "电量",
            value = state.batteryLabel,
            palette = palette,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Metric(
            label = "续航",
            value = state.rangeLabel,
            palette = palette,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MaterialMetrics(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(
            label = "电量",
            value = state.batteryLabel,
            palette = palette,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Metric(
            label = "续航",
            value = state.rangeLabel,
            palette = palette,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactMetrics(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
) {
    val battery = state.batteryLabel.replace(" ", "")
    val range = state.rangeLabel.replace(" ", "")
    Text(
        text = "电量 $battery   续航 $range",
        modifier = GlanceModifier.fillMaxWidth(),
        style = widgetTextStyle(
            color = palette.primaryText,
            size = 9.sp,
            weight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = widgetTextStyle(
                color = palette.secondaryText,
                size = 8.sp,
            ),
            maxLines = 1,
        )
        Text(
            text = value,
            style = widgetTextStyle(
                color = palette.primaryText,
                size = 12.sp,
                weight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

private fun widgetTextStyle(
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight? = null,
    textAlign: TextAlign? = null,
): TextStyle = TextStyle(
    color = ColorProvider(color),
    fontSize = size,
    fontWeight = weight,
    textAlign = textAlign,
)

private data class WidgetPalette(
    val surface: Color,
    val stage: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val outline: Color,
    val connected: Color,
    val onConnected: Color,
    val neutralPill: Color,
)

private fun widgetPalette(
    uiStyle: UiThemeStyle,
    seedColor: Color,
    darkTheme: Boolean,
    amoledDark: Boolean,
): WidgetPalette {
    val black = Color(0xFF0B0B0D)
    val white = Color(0xFFFFFFFF)
    val surface = when {
        darkTheme && amoledDark -> black
        darkTheme -> mix(
            seedColor,
            Color(0xFF111114),
            if (uiStyle == UiThemeStyle.Miuix) 0.91f else 0.95f,
        )
        uiStyle == UiThemeStyle.Miuix -> mix(seedColor, white, 0.91f)
        else -> mix(seedColor, white, 0.97f)
    }
    val primaryText = if (darkTheme) Color(0xFFF6F2F8) else Color(0xFF1A1A1E)
    val secondaryText = mix(primaryText, surface, 0.38f)
    val accent = readableAccent(seedColor, darkTheme)
    val stage = if (uiStyle == UiThemeStyle.Miuix) {
        mix(surface, accent, if (darkTheme) 0.14f else 0.08f)
    } else {
        mix(primaryText, surface, 0.94f)
    }
    return WidgetPalette(
        surface = surface,
        stage = stage,
        primaryText = primaryText,
        secondaryText = secondaryText,
        outline = mix(primaryText, surface, 0.82f),
        connected = if (darkTheme) Color(0xFF45CF8A) else Color(0xFF147A47),
        onConnected = Color(0xFF071B10),
        neutralPill = mix(primaryText, surface, 0.92f),
    )
}

private fun readableAccent(seedColor: Color, darkTheme: Boolean): Color {
    val luminance = relativeLuminance(seedColor)
    return when {
        darkTheme && luminance < 0.32f -> mix(seedColor, Color.White, 0.28f)
        !darkTheme && luminance > 0.62f -> mix(seedColor, Color.Black, 0.32f)
        else -> seedColor
    }
}

private fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f
        else ((channel + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * linear(color.red) +
        0.7152f * linear(color.green) +
        0.0722f * linear(color.blue)
}

private fun mix(base: Color, overlay: Color, amount: Float): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (overlay.red - base.red) * fraction,
        green = base.green + (overlay.green - base.green) * fraction,
        blue = base.blue + (overlay.blue - base.blue) * fraction,
        alpha = 1f,
    )
}

private fun resolveDarkTheme(context: Context, themeMode: AppThemeMode): Boolean =
    when (themeMode) {
        AppThemeMode.System -> {
            val nightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

private suspend fun loadWidgetBitmap(
    url: String,
    maxDimension: Int,
    cropTransparentBounds: Boolean = false,
): Bitmap? {
    if (url.isBlank()) return null
    val bytes = try {
        AppImages.loadBytes(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    } ?: return null

    return withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > maxDimension ||
                bounds.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
            if (cropTransparentBounds && decoded != null) {
                cropTransparentBitmap(decoded) ?: decoded
            } else {
                decoded
            }
        }.getOrNull()
    }
}

private fun cropTransparentBitmap(source: Bitmap): Bitmap? {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val rowOffset = y * width
        for (x in 0 until width) {
            val alpha = pixels[rowOffset + x] ushr 24
            if (alpha <= 8) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (right < left || bottom < top) return null

    val padding = (minOf(width, height) / 40).coerceAtLeast(1)
    val cropLeft = (left - padding).coerceAtLeast(0)
    val cropTop = (top - padding).coerceAtLeast(0)
    val cropRight = (right + padding).coerceAtMost(width - 1)
    val cropBottom = (bottom + padding).coerceAtMost(height - 1)
    val cropWidth = cropRight - cropLeft + 1
    val cropHeight = cropBottom - cropTop + 1
    if (cropWidth >= width * 0.98f && cropHeight >= height * 0.98f) return source
    return Bitmap.createBitmap(source, cropLeft, cropTop, cropWidth, cropHeight)
}

private val KEY_HAS_VEHICLE_IDENTITY = booleanPreferencesKey("vehicle_has_identity")
private val KEY_VIN = stringPreferencesKey("vehicle_vin")
private val KEY_VEHICLE_NAME = stringPreferencesKey("vehicle_name")
private val KEY_VEHICLE_SUBTITLE = stringPreferencesKey("vehicle_subtitle")
private val KEY_VEHICLE_IMAGE_URL = stringPreferencesKey("vehicle_image_url")
private val KEY_BACKGROUND_IMAGE_URL = stringPreferencesKey("vehicle_background_image_url")
private val KEY_BIKE_BOND_TYPE = stringPreferencesKey("vehicle_bike_bond_type")
private val KEY_HAS_STATUS = booleanPreferencesKey("vehicle_has_status")
private val KEY_CONNECTED = booleanPreferencesKey("vehicle_connected")
private val KEY_CLOUD_ONLINE = booleanPreferencesKey("vehicle_cloud_online")
private val KEY_POWER_ON = booleanPreferencesKey("vehicle_power_on")
private val KEY_KEY_ON = booleanPreferencesKey("vehicle_key_on")
private val KEY_CONNECTION_LABEL = stringPreferencesKey("vehicle_connection_label")
private val KEY_CONNECTION_SHORT_LABEL = stringPreferencesKey("vehicle_connection_short_label")
private val KEY_BATTERY_LABEL = stringPreferencesKey("vehicle_battery_label")
private val KEY_RANGE_LABEL = stringPreferencesKey("vehicle_range_label")
private val KEY_STATUS_SUMMARY = stringPreferencesKey("vehicle_status_summary")
private val KEY_CONTROL_MESSAGE = stringPreferencesKey("vehicle_control_message")
private val KEY_CONTROL_MESSAGE_AT = longPreferencesKey("vehicle_control_message_at")
private val KEY_UI_STYLE = stringPreferencesKey("widget_ui_style")
private val KEY_THEME_MODE = stringPreferencesKey("widget_theme_mode")
private val KEY_SEED_COLOR = intPreferencesKey("widget_seed_color")
private val KEY_AMOLED_DARK = booleanPreferencesKey("widget_amoled_dark")
