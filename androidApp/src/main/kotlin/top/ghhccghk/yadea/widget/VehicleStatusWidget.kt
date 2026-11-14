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
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.action.Action
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
import top.ghhccghk.yadea.ui.MainScreenStartDestination
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
import top.ghhccghk.yadea.vehicle.YadeaVehicleCloudStatusGateway
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleStatusSource
import top.ghhccghk.yadea.vehicle.decodeCachedVehicleStatus
import top.ghhccghk.yadea.vehicle.withColorTypeOverride

private const val WIDGET_UPDATE_INTERVAL_MS = 5_000L
private const val VEHICLE_IMAGE_MAX_DIMENSION = 384
private const val VEHICLE_BACKGROUND_MAX_DIMENSION = 960
private const val WIDGET_ACTION_LOCK_TOGGLE = "LOCK_TOGGLE"
private const val WIDGET_ACTION_FIND = "FIND"
private const val WIDGET_ACTION_POWER_ON = "POWER_ON"
private const val WIDGET_ACTION_POWER_OFF = "POWER_OFF"
private const val WIDGET_ACTION_POWER_TOGGLE = "POWER_TOGGLE"
private const val CONTROL_MESSAGE_VISIBLE_MS = 15_000L
private const val WIDGET_TAG = "VehicleStatusWidget"
private val WidgetControlActionKey =
    ActionParameters.Key<String>("vehicle_widget_control_action")

/**
 * 打开应用时保留现有 MainActivity 实例。
 *
 * 仅使用 CLEAR_TOP 会销毁并重建标准 launchMode 的 Activity，表现为点击小部件后
 * 整个应用重新启动；同时加 SINGLE_TOP 才会清除上层页面并复用当前实例。
 */
private fun buildOpenAppIntent(context: Context): Intent =
    buildAppIntent(context, destination = null)

private fun buildVehicleInfoIntent(context: Context): Intent =
    buildAppIntent(context, MainScreenStartDestination.Vehicle)

private fun buildAppIntent(
    context: Context,
    destination: MainScreenStartDestination?,
): Intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TOP or
        Intent.FLAG_ACTIVITY_SINGLE_TOP
    destination?.let {
        putExtra(MainActivity.EXTRA_START_DESTINATION, it.name)
    }
}

private fun isWidgetBleConnected(context: Context): Boolean =
    AndroidVehicleBleSession.get(context).connectionState.value.let { state ->
        state == BleConnectionState.Connected || state == BleConnectionState.Ready
    }

/**
 * 小部件的开锁/锁车合并状态。
 *
 * BLE 在线时使用 TTP `parkingStatus` 的原 App 语义：`0` 为撤防，`2` 为 P 档，
 * 其他有效值为设防。蓝牙断开时使用原 App `VehicleDataBean.isLocked()` 的云端
 * 判断：`powerState=1` 或 `rideStatus=2` 视为解防，其余已知状态视为设防。
 */
private fun resolveWidgetLockedState(
    status: TtpInfo?,
    connected: Boolean,
    cloudStatus: VehicleCloudStatus?,
): Boolean? {
    if (connected && status != null && status.parkingStatus >= 0) {
        return when (status.parkingStatus) {
            0 -> false
            2 -> null
            else -> true
        }
    }
    val powerOn = cloudStatus?.powerState
    val rideStatus = cloudStatus?.rideStatus
    return when {
        powerOn == 1 -> false
        rideStatus == 2 -> false
        powerOn == 0 || (rideStatus != null && rideStatus in 0..1) -> true
        else -> null
    }
}

private fun hasWidgetRangeLabel(value: String?): Boolean {
    val normalized = value?.trim()?.removeSuffix("km")?.trim()
    return !normalized.isNullOrBlank() && normalized != "—"
}

private fun widgetCloudRangeLabel(value: String?): String {
    val normalized = value
        ?.trim()
        ?.removeSuffix("km")
        ?.trim()
        .orEmpty()
    return if (normalized.isBlank() || normalized == "—") "—" else "$normalized km"
}

private fun preferredWidgetRangeLabel(
    ttpLabel: String?,
    cloudRange: String?,
    cachedLabel: String? = null,
): String = listOf(
    ttpLabel,
    widgetCloudRangeLabel(cloudRange),
    cachedLabel,
).firstOrNull(::hasWidgetRangeLabel) ?: "—"

private suspend fun loadWidgetCloudStatus(
    context: Context,
    preferences: AppPreferences,
    profile: VehicleWidgetProfileCache,
): VehicleCloudStatus? {
    val auth = preferences.readAuth()
    if (auth.token.isBlank() || profile.vin.isBlank()) return null
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val appMarket = context.applicationInfo.metaData
        ?.getString("BUGLY_APP_CHANNEL")
        .orEmpty()
        .ifBlank { "Yadea" }
    return try {
        YadeaVehicleCloudStatusGateway(
            transport = createHttpTransport(),
            tokenProvider = { auth.token },
            loginIdProvider = { auth.loginId },
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
        ).load(profile.vin)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

class VehicleStatusWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        AndroidImageCacheContext.init(appContext)
        val appPreferences = AppPreferences(
            store = SharedPreferencesPreferenceStore(appContext),
            secretStore = AndroidSecretStore(appContext),
        )
        val fallbackAppearance = appPreferences.read()
        val fallbackWidgetProfile = appPreferences.readVehicleWidgetProfile()
        val cachedStatus = appPreferences.readVehicleStatus()
        val fallbackStatus = cachedStatus
            ?.let { decodeCachedVehicleStatus(it.packetHex) }
        val liveBleConnected = isWidgetBleConnected(appContext)
        val hasTtpRange = fallbackStatus
            ?.let { VehicleInfoFormatter.format(it, connected = false).rangeLabel }
            ?.let(::hasWidgetRangeLabel) == true
        val liveCloudStatus = if (!liveBleConnected || !hasTtpRange) {
            loadWidgetCloudStatus(
                context = appContext,
                preferences = appPreferences,
                profile = fallbackWidgetProfile,
            )
        } else {
            null
        }
        liveCloudStatus
            ?.soc
            ?.takeIf { it in 0..100 }
            ?.let { soc ->
                updateAppWidgetState(
                    context = appContext,
                    glanceId = id,
                ) { preferences ->
                    preferences.toMutablePreferences().apply {
                        this[KEY_HAS_STATUS] = true
                        this[KEY_CONNECTED] = false
                        this[KEY_CLOUD_ONLINE] = liveCloudStatus.onlineState == true
                        this[KEY_BATTERY_LABEL] = "$soc%"
                    }
                }
            }
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
                liveBleConnected = liveBleConnected,
                liveCloudStatus = liveCloudStatus,
            )
            VehicleStatusWidgetContent(state)
        }
    }
}

internal suspend fun refreshVehicleStatusWidgetViews(context: Context) {
    val appContext = context.applicationContext
    val glanceIds = try {
        GlanceAppWidgetManager(appContext)
            .getGlanceIds(VehicleStatusWidget::class.java)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(WIDGET_TAG, "Unable to query vehicle widget instances", error)
        return
    }
    val widget = VehicleStatusWidget()
    glanceIds.forEach { glanceId ->
        try {
            widget.update(appContext, glanceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(WIDGET_TAG, "Unable to refresh vehicle widget $glanceId", error)
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
            WIDGET_ACTION_LOCK_TOGGLE -> when (preferences[KEY_LOCKED]) {
                true -> VehicleControlCommand.UNLOCK
                false -> VehicleControlCommand.LOCK
                null -> {
                    completeControlAction(
                        context = appContext,
                        glanceId = glanceId,
                        message = "车辆设防状态未知，无法自动选择开锁或锁车指令",
                    )
                    return
                }
            }
            WIDGET_ACTION_FIND -> VehicleControlCommand.FIND
            WIDGET_ACTION_POWER_ON -> VehicleControlCommand.POWER_ON
            WIDGET_ACTION_POWER_OFF -> VehicleControlCommand.POWER_OFF
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
            preferCloud = !connected,
        )
        val locked = resolveWidgetLockedState(
            status = effectiveStatus,
            connected = connected,
            cloudStatus = cloudStatus,
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
            val powerLabel = when (powerState.powerOn) {
                true -> "已开机"
                false -> "已关机"
                null -> formatted.powerLabel
            }
            val lockLabel = when (locked) {
                true -> "已锁车"
                false -> "未锁车"
                null -> formatted.lockLabel
            }
            listOf(lockLabel, powerLabel, chargeLabel)
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
            locked = locked,
            connectionLabel = connectionLabel,
            connectionShortLabel = connectionShortLabel,
            batteryLabel = VehicleInfoFormatter.preferredBatteryLabel(
                bluetoothLabel = formatted.batteryLabel,
                cloudSoc = cloudStatus?.soc,
                connected = connected,
            ),
            rangeLabel = preferredWidgetRangeLabel(
                ttpLabel = formatted.rangeLabel,
                cloudRange = cloudStatus?.remainingMileage
                    ?.takeIf(::hasWidgetRangeLabel)
                    ?: profile?.remainingMileage,
            ),
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
    val locked: Boolean? = null,
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
        if (locked == null) {
            preferences.remove(KEY_LOCKED)
        } else {
            preferences[KEY_LOCKED] = locked
        }
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
            liveBleConnected: Boolean? = null,
            liveCloudStatus: VehicleCloudStatus? = null,
        ): VehicleStatusWidgetState {
            val fallbackFormatted = fallbackStatus
                ?.let { VehicleInfoFormatter.format(it, connected = false) }
            val hasStatus = preferences[KEY_HAS_STATUS]
                ?: (fallbackStatus != null || liveCloudStatus != null)
            val connected = liveBleConnected ?: preferences[KEY_CONNECTED] ?: false
            val fallbackPowerState = VehiclePowerStateResolver.resolve(
                status = fallbackStatus,
                cloudPowerState = liveCloudStatus?.powerState,
                cloudLockStatus = liveCloudStatus?.lockStatus,
                preferCloud = !connected,
            )
            val fallbackLocked = if (!connected && liveCloudStatus != null) {
                resolveWidgetLockedState(
                    status = fallbackStatus,
                    connected = connected,
                    cloudStatus = liveCloudStatus,
                )
            } else {
                preferences[KEY_LOCKED]
                    ?: resolveWidgetLockedState(
                        status = fallbackStatus,
                        connected = connected,
                        cloudStatus = liveCloudStatus,
                    )
            }
            val cloudOnline = liveCloudStatus?.onlineState == true ||
                preferences[KEY_CLOUD_ONLINE] == true
            val connectionLabel = when {
                cloudOnline && !connected -> "车联网在线"
                preferences[KEY_CONNECTION_LABEL] != null ->
                    preferences[KEY_CONNECTION_LABEL].orEmpty()
                hasStatus -> fallbackFormatted?.connectionLabel ?: "状态缓存"
                connected -> "已连接，等待车辆上报"
                else -> "打开应用连接车辆"
            }
            val connectionShortLabel = when {
                hasStatus && connected -> "在线"
                cloudOnline -> "云端"
                preferences[KEY_CONNECTION_SHORT_LABEL] != null ->
                    preferences[KEY_CONNECTION_SHORT_LABEL].orEmpty()
                hasStatus -> "缓存"
                connected -> "等待上报"
                else -> "打开应用"
            }
            val fallbackSummary = fallbackFormatted?.let { formatted ->
                val chargeLabel = formatted.chargeLabel.takeIf {
                    it == "未充电" || it == "充电中"
                }.orEmpty()
                val powerLabel = when (fallbackPowerState.powerOn) {
                    true -> "已开机"
                    false -> "已关机"
                    null -> formatted.powerLabel
                }
                val lockLabel = when (fallbackLocked) {
                    true -> "已锁车"
                    false -> "未锁车"
                    null -> formatted.lockLabel
                }
                listOf(lockLabel, powerLabel, chargeLabel)
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
                powerOn = if (!connected && liveCloudStatus != null) {
                    fallbackPowerState.powerOn
                } else {
                    preferences[KEY_POWER_ON] ?: fallbackPowerState.powerOn
                },
                keyOn = if (!connected && liveCloudStatus != null) {
                    fallbackPowerState.keyOn
                } else {
                    preferences[KEY_KEY_ON] ?: fallbackPowerState.keyOn
                },
                locked = fallbackLocked,
                connectionLabel = connectionLabel,
                connectionShortLabel = connectionShortLabel,
                batteryLabel = VehicleInfoFormatter.preferredBatteryLabel(
                    bluetoothLabel = preferences[KEY_BATTERY_LABEL]
                        ?: fallbackFormatted?.batteryLabel
                        ?: "—",
                    cloudSoc = liveCloudStatus?.soc,
                    connected = connected,
                ),
                rangeLabel = preferredWidgetRangeLabel(
                    ttpLabel = fallbackFormatted?.rangeLabel,
                    cloudRange = liveCloudStatus?.remainingMileage,
                    cachedLabel = preferences[KEY_RANGE_LABEL],
                ),
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
    val layout = widgetLayout(LocalSize.current, context.resources.configuration.fontScale)
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

    val openAppIntent = buildOpenAppIntent(context)
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
            ResponsiveWidgetContent(state, palette, vehicleBitmap, layout)
        }
    }
}

/** Sizes are in launcher-provided dp, rather than an assumed home-screen cell count. */
private data class WidgetLayout(
    val horizontal: Boolean,
    val compact: Boolean,
    val showSubtitle: Boolean,
    val showSummary: Boolean,
    val iconOnly: Boolean,
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val gap: Dp,
    val headerHeight: Dp,
    val metricsHeight: Dp,
    val tightMetrics: Boolean,
    val summaryHeight: Dp,
    val actionsHeight: Dp,
    val stageHeight: Dp,
    val stageWidth: Dp,
)

private fun widgetLayout(size: DpSize, fontScale: Float): WidgetLayout {
    val scale = fontScale.coerceAtLeast(1f)
    val narrow = size.width < 220.dp
    val compact = narrow || size.height < 210.dp
    val horizontalPadding = if (narrow) 8.dp else 14.dp
    val bottomPadding = if (compact) 16.dp else 20.dp
    val gap = if (compact) 4.dp else 8.dp
    val showSubtitle = size.height >= 230.dp && size.width >= 180.dp
    val showSummary = size.height.value >= 150f * scale
    val iconOnly = size.width < 180.dp || size.height.value < 160f * scale
    val headerHeight = ((if (showSubtitle) 36f else 18f) * scale).dp
    val desiredMetricsHeight = ((if (compact) 30f else 46f) * scale).dp
    val summaryHeight = if (showSummary) ((if (compact) 15f else 18f) * scale).dp else 0.dp
    val actionsHeight = if (iconOnly) 30.dp else (42f * scale).dp
    val innerWidth = (size.width - horizontalPadding * 2).coerceAtLeast(0.dp)
    val fixedContentHeight = bottomPadding + headerHeight + actionsHeight +
        gap * 2 + if (showSummary) summaryHeight + gap else 0.dp
    // Leave breathing room below the host's rounded top edge, including App Vault stacks.
    // At the minimum resize height, keep enough space for readings and all four controls.
    val preferredTopPadding = if (compact) 16.dp else 24.dp
    val maximumTopPadding = (size.height - fixedContentHeight - (21f * scale).dp)
        .coerceAtLeast(6.dp)
    val topPadding = minOf(preferredTopPadding, maximumTopPadding)
    // Reserve text and controls first. Only the vehicle area may consume the remaining height.
    val reservedHeight = topPadding + fixedContentHeight
    val bodyHeight = (size.height - reservedHeight).coerceAtLeast(0.dp)
    val horizontal = (!narrow && size.height < 210.dp) ||
        bodyHeight < desiredMetricsHeight + gap + 24.dp
    val tightMetrics = horizontal && bodyHeight < desiredMetricsHeight
    val metricsHeight = if (tightMetrics) bodyHeight else desiredMetricsHeight
    val stageHeight = if (horizontal) {
        bodyHeight.coerceAtMost(100.dp)
    } else {
        minOf(
            (bodyHeight - metricsHeight - gap).coerceAtLeast(0.dp),
            innerWidth * if (narrow) 0.60f else 0.48f,
            size.height * 0.34f,
            200.dp,
        )
    }
    // Keep room for both readings, even at intermediate launcher resize widths.
    val stageWidth = if (horizontal) {
        minOf(innerWidth * 0.38f, (innerWidth - (116f * scale).dp - gap).coerceAtLeast(0.dp))
    } else {
        innerWidth
    }
    return WidgetLayout(
        horizontal = horizontal,
        compact = compact,
        showSubtitle = showSubtitle,
        showSummary = showSummary,
        iconOnly = iconOnly,
        horizontalPadding = horizontalPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        gap = gap,
        headerHeight = headerHeight,
        metricsHeight = metricsHeight,
        tightMetrics = tightMetrics,
        summaryHeight = summaryHeight,
        actionsHeight = actionsHeight,
        stageHeight = stageHeight,
        stageWidth = stageWidth,
    )
}

@Composable
private fun ResponsiveWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    vehicleBitmap: Bitmap?,
    layout: WidgetLayout,
) {
    val miuix = state.uiStyle == UiThemeStyle.Miuix
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(
                start = layout.horizontalPadding,
                top = layout.topPadding,
                end = layout.horizontalPadding,
                bottom = layout.bottomPadding,
            ),
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(layout.headerHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (miuix) {
                MiuixHeader(state, palette, layout.showSubtitle, layout.compact)
            } else {
                MaterialHeader(state, palette, layout.showSubtitle, layout.compact)
            }
        }
        Spacer(GlanceModifier.height(layout.gap))
        if (layout.horizontal) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetMetrics(
                    state, palette, layout,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (layout.stageWidth > 0.dp) {
                    Spacer(GlanceModifier.width(layout.gap))
                    VehicleStage(
                        state = state,
                        bitmap = vehicleBitmap,
                        palette = palette,
                        height = layout.stageHeight,
                        radius = if (miuix) 14.dp else 0.dp,
                        useStageColor = miuix,
                        modifier = GlanceModifier.width(layout.stageWidth).height(layout.stageHeight),
                    )
                }
            }
        } else {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                VehicleStage(
                    state = state,
                    bitmap = vehicleBitmap,
                    palette = palette,
                    height = layout.stageHeight,
                    radius = if (miuix) 20.dp else 0.dp,
                    useStageColor = miuix,
                )
            }
            Spacer(GlanceModifier.height(layout.gap))
            WidgetMetrics(state, palette, layout)
        }
        if (layout.showSummary) {
            Spacer(GlanceModifier.height(layout.gap))
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(layout.summaryHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Status has one dedicated row; action feedback temporarily replaces it.
                val summary = state.displaySummary()
                Text(
                    text = if (layout.compact && summary == state.statusSummary) {
                        summary.replace("已锁车", "已锁").replace("未锁车", "未锁")
                            .replace("已开机", "开机").replace("已关机", "关机")
                    } else {
                        summary
                    },
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = widgetTextStyle(palette.secondaryText, if (layout.compact) 9.sp else 10.sp),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(layout.gap))
        WidgetActions(state, palette, layout)
    }
}

@Composable
private fun WidgetMetrics(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    layout: WidgetLayout,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    val miuix = state.uiStyle == UiThemeStyle.Miuix
    // Move the unit into the caption so a long range never loses its numeric digits.
    val range = state.rangeLabel.trim()
    val rangeHasUnit = range.endsWith("km", ignoreCase = true)
    val rangeValue = if (rangeHasUnit) range.dropLast(2).trim() else range
    Row(
        modifier = modifier
            .height(layout.metricsHeight)
            .background(ColorProvider(if (miuix) palette.stage else Color.Transparent))
            .cornerRadius(if (miuix) 14.dp else 0.dp)
            .padding(horizontal = if (miuix) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Metric(
            label = "电量",
            value = state.batteryLabel,
            palette = palette,
            modifier = GlanceModifier.defaultWeight(),
            valueSize = when {
                layout.tightMetrics -> 10.sp
                layout.compact -> 13.sp
                else -> 20.sp
            },
            labelSize = if (layout.tightMetrics) 6.sp else 8.sp,
        )
        Spacer(GlanceModifier.width(6.dp))
        Metric(
            label = if (rangeHasUnit) "续航 km" else "续航",
            value = rangeValue,
            palette = palette,
            modifier = GlanceModifier.defaultWeight(),
            valueSize = when {
                layout.tightMetrics -> 10.sp
                layout.compact -> 13.sp
                else -> 20.sp
            },
            labelSize = if (layout.tightMetrics) 6.sp else 8.sp,
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
    layout: WidgetLayout,
) {
    if (state.hasVehicleIdentity) {
        WidgetControls(
            state = state,
            palette = palette,
            layout = layout,
        )
    } else {
        ConnectVehicleBar(palette, layout)
    }
}

@Composable
private fun ConnectVehicleBar(
    palette: WidgetPalette,
    layout: WidgetLayout,
) {
    val context = LocalContext.current
    val openAppAction = actionStartActivity(buildOpenAppIntent(context))
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(layout.actionsHeight)
            .background(ColorProvider(palette.stage))
            .cornerRadius(12.dp)
            .clickable(openAppAction)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!layout.iconOnly) {
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
                size = if (layout.compact) 8.sp else 9.sp,
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
    layout: WidgetLayout,
) {
    val context = LocalContext.current
    val rowModifier = GlanceModifier
        .fillMaxWidth()
        .height(layout.actionsHeight)
        .background(ColorProvider(palette.stage))
        .cornerRadius(if (layout.iconOnly) 10.dp else 16.dp)
        .padding(3.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lockLabel = when (state.locked) {
            true -> "开锁"
            false -> "锁车"
            null -> "车锁"
        }
        WidgetControlButton(
            label = lockLabel,
            iconRes = if (state.locked == true) {
                R.drawable.ic_widget_unlock
            } else {
                R.drawable.ic_widget_lock
            },
            enabled = state.hasControlChannel && state.locked != null,
            action = actionRunCallback<VehicleWidgetControlAction>(
                actionParametersOf(
                    WidgetControlActionKey to WIDGET_ACTION_LOCK_TOGGLE,
                ),
            ),
            palette = palette,
            layout = layout,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = "寻车",
            iconRes = R.drawable.ic_widget_find,
            enabled = state.hasControlChannel,
            action = actionRunCallback<VehicleWidgetControlAction>(
                actionParametersOf(WidgetControlActionKey to WIDGET_ACTION_FIND),
            ),
            palette = palette,
            layout = layout,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = if (state.powerOn == true) "关机" else "开机",
            iconRes = R.drawable.ic_widget_power,
            enabled = state.hasControlChannel && state.keyOn != true,
            action = actionRunCallback<VehicleWidgetControlAction>(
                actionParametersOf(
                    WidgetControlActionKey to if (state.powerOn == true) {
                        WIDGET_ACTION_POWER_OFF
                    } else {
                        WIDGET_ACTION_POWER_ON
                    },
                ),
            ),
            palette = palette,
            layout = layout,
        )
        Spacer(GlanceModifier.width(3.dp))
        WidgetControlButton(
            label = "车辆信息",
            iconRes = R.drawable.ic_widget_vehicle_info,
            enabled = true,
            action = actionStartActivity(buildVehicleInfoIntent(context)),
            palette = palette,
            layout = layout,
        )
    }
}

@Composable
private fun RowScope.WidgetControlButton(
    label: String,
    iconRes: Int,
    enabled: Boolean,
    action: Action,
    palette: WidgetPalette,
    layout: WidgetLayout,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .height(layout.actionsHeight - 6.dp)
            .background(ColorProvider(if (enabled) Color.Transparent else palette.neutralPill))
            .cornerRadius(8.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        if (layout.iconOnly) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(
                    ColorProvider(
                        if (enabled) palette.primaryText else palette.secondaryText,
                    ),
                ),
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
                    text = if (label == "车辆信息" && LocalSize.current.width < 300.dp) "信息" else label,
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
    compact: Boolean,
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
                    size = if (compact) 12.sp else 14.sp,
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
        Spacer(GlanceModifier.width(if (compact) 3.dp else 5.dp))
        Text(
            text = state.connectionShortLabel,
            modifier = GlanceModifier
                .background(
                    ColorProvider(
                        if (state.hasControlChannel) palette.connected else palette.neutralPill,
                    ),
                )
                .cornerRadius(12.dp)
                .padding(
                    horizontal = if (compact) 4.dp else 6.dp,
                    vertical = if (compact) 2.dp else 3.dp,
                ),
            style = widgetTextStyle(
                color = if (state.hasControlChannel) palette.onConnected else palette.primaryText,
                size = if (compact) 8.sp else 9.sp,
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
    compact: Boolean,
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
                    size = if (compact) 12.sp else 14.sp,
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
        Spacer(GlanceModifier.width(if (compact) 3.dp else 6.dp))
        Text(
            text = state.connectionShortLabel,
            style = widgetTextStyle(
                color = if (state.hasControlChannel) palette.connected else palette.secondaryText,
                size = if (compact) 8.sp else 9.sp,
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
                    .fillMaxSize(),
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
private fun Metric(
    label: String,
    value: String,
    palette: WidgetPalette,
    modifier: GlanceModifier,
    valueSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    labelSize: androidx.compose.ui.unit.TextUnit = 8.sp,
    maxLines: Int = 1,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = widgetTextStyle(
                color = palette.secondaryText,
                size = labelSize,
            ),
            maxLines = 1,
        )
        Text(
            text = value,
            style = widgetTextStyle(
                color = palette.primaryText,
                size = valueSize,
                weight = FontWeight.Medium,
            ),
            maxLines = maxLines,
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
private val KEY_LOCKED = booleanPreferencesKey("vehicle_locked")
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
