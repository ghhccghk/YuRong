package top.ghhccghk.yadea.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
import top.ghhccghk.yadea.MainActivity
import top.ghhccghk.yadea.image.AndroidImageCacheContext
import top.ghhccghk.yadea.image.AppImages
import top.ghhccghk.yadea.settings.AppPreferences
import top.ghhccghk.yadea.settings.SharedPreferencesPreferenceStore
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleCloudTheme
import top.ghhccghk.yadea.vehicle.VehicleCloudThemeSource
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleProfileSource
import top.ghhccghk.yadea.vehicle.VehicleStatusSource
import top.ghhccghk.yadea.vehicle.decodeCachedVehicleStatus

private const val WIDGET_UPDATE_INTERVAL_MS = 5_000L
private const val VEHICLE_IMAGE_MAX_DIMENSION = 384
private const val VEHICLE_BACKGROUND_MAX_DIMENSION = 480

class VehicleStatusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        AndroidImageCacheContext.init(appContext)
        val appPreferences = AppPreferences(
            store = SharedPreferencesPreferenceStore(appContext),
        )
        val fallbackAppearance = appPreferences.read()
        val fallbackStatus = appPreferences.readVehicleStatus()
            ?.let { decodeCachedVehicleStatus(it.packetHex) }

        provideContent {
            val persistedState = currentState<Preferences>()
            val state = VehicleStatusWidgetState.fromPreferences(
                preferences = persistedState,
                fallbackAppearance = fallbackAppearance,
                fallbackStatus = fallbackStatus,
            )
            VehicleStatusWidgetContent(state)
        }
    }
}

class VehicleStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VehicleStatusWidget()
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
    private val preferences: AppPreferences,
    private val updater: VehicleStatusWidgetUpdater,
) {
    private var stateJob: Job? = null
    private var refreshJob: Job? = null
    private var latestState: VehicleStatusWidgetState? = null
    private var lastVisualKey: String? = null
    private var hasPendingStatusUpdate = false

    fun start() {
        if (stateJob != null) return
        stateJob = scope.launch {
            combine(
                profileSource.profile,
                statusSource.status,
                statusSource.connected,
                cloudThemeSource.theme,
                preferences.observe().onStart { emit(preferences.read()) },
            ) { profile, status, connected, cloudTheme, appearance ->
                buildState(
                    profile = profile,
                    status = status,
                    connected = connected,
                    cloudTheme = cloudTheme,
                    appearance = appearance,
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
        refreshJob?.cancel()
        stateJob = null
        refreshJob = null
    }

    private fun buildState(
        profile: VehicleProfile?,
        status: TtpInfo?,
        connected: Boolean,
        cloudTheme: VehicleCloudTheme?,
        appearance: AppAppearance,
    ): VehicleStatusWidgetState {
        val cached = preferences.readVehicleStatus()
        val cachedStatus = cached
            ?.takeIf { profile?.vin.isNullOrBlank() || it.vin == profile.vin }
            ?.let { decodeCachedVehicleStatus(it.packetHex) }
        val effectiveStatus = status ?: cachedStatus
        val formatted = VehicleInfoFormatter.format(effectiveStatus, connected)
        val darkTheme = resolveDarkTheme(context, appearance.themeMode)
        val matchedCloudTheme = cloudTheme?.takeIf {
            profile?.vin.isNullOrBlank() || it.vin == profile.vin
        }
        val backgroundImageUrl = matchedCloudTheme
            ?.bannerVehicleBgImgUrl(darkTheme)
            .orEmpty()
            .ifBlank { matchedCloudTheme?.loveCarImageUrl.orEmpty() }

        val vehicleName = profile?.let(::vehicleName)
        val subtitle = profile?.let(::vehicleSubtitle).orEmpty()
        val hasStatus = effectiveStatus != null
        val connectionLabel = when {
            hasStatus -> formatted.connectionLabel
            connected -> "已连接，等待车辆上报"
            else -> "打开应用连接车辆"
        }
        val connectionShortLabel = when {
            hasStatus && connected -> "在线"
            hasStatus -> "缓存"
            connected -> "等待上报"
            else -> "打开应用"
        }
        val statusSummary = if (hasStatus) {
            listOf(formatted.lockLabel, formatted.powerLabel, formatted.chargeLabel)
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
            hasStatus = hasStatus,
            connected = connected,
            connectionLabel = connectionLabel,
            connectionShortLabel = connectionShortLabel,
            batteryLabel = if (hasStatus) formatted.batteryLabel else "—",
            rangeLabel = if (hasStatus) formatted.rangeLabel else "—",
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
    val hasStatus: Boolean = false,
    val connected: Boolean = false,
    val connectionLabel: String = "打开应用连接车辆",
    val connectionShortLabel: String = "打开应用",
    val batteryLabel: String = "—",
    val rangeLabel: String = "—",
    val statusSummary: String = "打开应用连接车辆",
    val uiStyle: UiThemeStyle = UiThemeStyle.Miuix,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val seedColorArgb: Int = 0xFF7B4DFF.toInt(),
    val amoledDarkEnabled: Boolean = false,
) {
    fun visualKey(): String = listOf(
        hasVehicleIdentity,
        vin,
        vehicleName,
        vehicleSubtitle,
        vehicleImageUrl,
        backgroundImageUrl,
        hasStatus,
        connected,
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
    }

    fun writeStatus(preferences: MutablePreferences) {
        preferences[KEY_HAS_STATUS] = hasStatus
        preferences[KEY_CONNECTED] = connected
        preferences[KEY_CONNECTION_LABEL] = connectionLabel
        preferences[KEY_CONNECTION_SHORT_LABEL] = connectionShortLabel
        preferences[KEY_BATTERY_LABEL] = batteryLabel
        preferences[KEY_RANGE_LABEL] = rangeLabel
        preferences[KEY_STATUS_SUMMARY] = statusSummary
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
        ): VehicleStatusWidgetState {
            val fallbackFormatted = fallbackStatus
                ?.let { VehicleInfoFormatter.format(it, connected = false) }
            val hasStatus = preferences[KEY_HAS_STATUS] ?: (fallbackStatus != null)
            val connected = preferences[KEY_CONNECTED] ?: false
            val connectionLabel = preferences[KEY_CONNECTION_LABEL] ?: when {
                hasStatus -> fallbackFormatted?.connectionLabel ?: "状态缓存"
                connected -> "已连接，等待车辆上报"
                else -> "打开应用连接车辆"
            }
            val connectionShortLabel = preferences[KEY_CONNECTION_SHORT_LABEL] ?: when {
                hasStatus && connected -> "在线"
                hasStatus -> "缓存"
                connected -> "等待上报"
                else -> "打开应用"
            }
            return VehicleStatusWidgetState(
                hasVehicleIdentity = preferences[KEY_HAS_VEHICLE_IDENTITY] ?: false,
                vin = preferences[KEY_VIN].orEmpty(),
                vehicleName = preferences[KEY_VEHICLE_NAME]
                    .orEmpty()
                    .ifBlank { "我的车辆" },
                vehicleSubtitle = preferences[KEY_VEHICLE_SUBTITLE].orEmpty(),
                vehicleImageUrl = preferences[KEY_VEHICLE_IMAGE_URL].orEmpty(),
                backgroundImageUrl = preferences[KEY_BACKGROUND_IMAGE_URL].orEmpty(),
                hasStatus = hasStatus,
                connected = connected,
                connectionLabel = connectionLabel,
                connectionShortLabel = connectionShortLabel,
                batteryLabel = preferences[KEY_BATTERY_LABEL]
                    ?: fallbackFormatted?.batteryLabel
                    ?: "—",
                rangeLabel = preferences[KEY_RANGE_LABEL]
                    ?: fallbackFormatted?.rangeLabel
                    ?: "—",
                statusSummary = preferences[KEY_STATUS_SUMMARY]
                    ?: fallbackFormatted?.let {
                        listOf(it.lockLabel, it.powerLabel, it.chargeLabel)
                            .filter { value -> value.isNotBlank() && value != "—" }
                            .joinToString(" · ")
                    }?.ifBlank { "车辆状态已同步" }
                    ?: connectionLabel,
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
    val compact = LocalSize.current.height < 150.dp
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
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius),
                contentScale = ContentScale.Crop,
            )
        }
        val contentModifier = if (backgroundBitmap != null) {
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(palette.imageOverlay))
        } else {
            GlanceModifier.fillMaxSize()
        }
        Box(
            modifier = contentModifier,
            contentAlignment = Alignment.TopStart,
        ) {
            if (state.uiStyle == UiThemeStyle.Miuix) {
                MiuixWidgetContent(
                    state = state,
                    palette = palette,
                    compact = compact,
                    vehicleBitmap = vehicleBitmap,
                )
            } else {
                MaterialWidgetContent(
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
private fun MiuixWidgetContent(
    state: VehicleStatusWidgetState,
    palette: WidgetPalette,
    compact: Boolean,
    vehicleBitmap: Bitmap?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        MiuixHeader(state, palette, showSubtitle = !compact)
        Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))
        VehicleStage(
            state = state,
            bitmap = vehicleBitmap,
            palette = palette,
            height = if (compact) 34.dp else 58.dp,
            radius = if (compact) 12.dp else 20.dp,
        )
        Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))
        if (compact) {
            CompactMetrics(state, palette)
        } else {
            MiuixMetrics(state, palette)
        }
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = state.statusSummary,
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
            .padding(if (compact) 8.dp else 11.dp),
    ) {
        MaterialHeader(state, palette, showSubtitle = !compact)
        Spacer(GlanceModifier.height(if (compact) 1.dp else 3.dp))
        VehicleStage(
            state = state,
            bitmap = vehicleBitmap,
            palette = palette,
            height = if (compact) 34.dp else 54.dp,
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
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = state.statusSummary,
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
                        if (state.connected) palette.connected else palette.neutralPill,
                    ),
                )
                .cornerRadius(12.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp),
            style = widgetTextStyle(
                color = if (state.connected) palette.onConnected else palette.primaryText,
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
                color = if (state.connected) palette.connected else palette.secondaryText,
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
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(ColorProvider(if (useStageColor) palette.stage else palette.surface))
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
    val imageOverlay: Color,
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
        imageOverlay = surface.copy(alpha = if (darkTheme) 0.68f else 0.78f),
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

private suspend fun loadWidgetBitmap(url: String, maxDimension: Int): Bitmap? {
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
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }.getOrNull()
    }
}

private val KEY_HAS_VEHICLE_IDENTITY = booleanPreferencesKey("vehicle_has_identity")
private val KEY_VIN = stringPreferencesKey("vehicle_vin")
private val KEY_VEHICLE_NAME = stringPreferencesKey("vehicle_name")
private val KEY_VEHICLE_SUBTITLE = stringPreferencesKey("vehicle_subtitle")
private val KEY_VEHICLE_IMAGE_URL = stringPreferencesKey("vehicle_image_url")
private val KEY_BACKGROUND_IMAGE_URL = stringPreferencesKey("vehicle_background_image_url")
private val KEY_HAS_STATUS = booleanPreferencesKey("vehicle_has_status")
private val KEY_CONNECTED = booleanPreferencesKey("vehicle_connected")
private val KEY_CONNECTION_LABEL = stringPreferencesKey("vehicle_connection_label")
private val KEY_CONNECTION_SHORT_LABEL = stringPreferencesKey("vehicle_connection_short_label")
private val KEY_BATTERY_LABEL = stringPreferencesKey("vehicle_battery_label")
private val KEY_RANGE_LABEL = stringPreferencesKey("vehicle_range_label")
private val KEY_STATUS_SUMMARY = stringPreferencesKey("vehicle_status_summary")
private val KEY_UI_STYLE = stringPreferencesKey("widget_ui_style")
private val KEY_THEME_MODE = stringPreferencesKey("widget_theme_mode")
private val KEY_SEED_COLOR = intPreferencesKey("widget_seed_color")
private val KEY_AMOLED_DARK = booleanPreferencesKey("widget_amoled_dark")
