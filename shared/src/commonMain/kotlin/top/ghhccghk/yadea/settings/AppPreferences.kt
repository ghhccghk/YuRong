package top.ghhccghk.yadea.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import top.ghhccghk.yadea.auth.AuthProfileCodec
import top.ghhccghk.yadea.auth.AuthSessionSnapshot
import top.ghhccghk.yadea.auth.AuthUser
import top.ghhccghk.yadea.ui.AppAppearance
import top.ghhccghk.yadea.ui.AppColorSource
import top.ghhccghk.yadea.ui.AppColorSpec
import top.ghhccghk.yadea.ui.AppInterfaceScale
import top.ghhccghk.yadea.ui.AppPaletteStyle
import top.ghhccghk.yadea.ui.AppThemeMode
import top.ghhccghk.yadea.ui.UiThemeStyle
import top.ghhccghk.yadea.vehicle.CachedVehicleStatus
import top.ghhccghk.yadea.vehicle.VehicleCloudTheme
import top.ghhccghk.yadea.vehicle.VehicleCloudThemeCache
import top.ghhccghk.yadea.vehicle.VehicleStatusCache

object AppPreferenceKeys {
    // 设置结构版本。新增设置项时递增，并同步补上迁移逻辑与测试。
    // 1 -> 2：新增 miuix_monet（Miuix 是否启用 Monet 动态配色）。
    // 2 -> 3：新增 floating_navigation_bar（Miuix 是否启用悬浮底栏）。
    // 3 -> 4：新增 color_spec（Miuix Monet 使用的颜色标准）。
    // 4 -> 5：新增 ui_scale_percent（界面缩放百分比）。
    // 5 -> 6：新增 blur_enabled（Miuix 是否启用官方 miuix-blur 背景模糊）。
    // 6 -> 7：新增 usr_token / usr_info / login_id（登录态）。
    // 7 -> 8：新增车辆上次连接、自动连接、前台服务与绑定 VIN。
    // 8 -> 9：新增最近一次完整 BLE 车辆状态缓存（按 VIN 恢复）。
    // 9 -> 10：新增当前 VIN 的云端主题背景与定制款标记缓存。
    // 10 -> 11：缓存动态主题 colorType，用于选择 bannerVehicleBgImg 亮/暗背景。
    // 11 -> 12：新增绝区零联动主题的本地 colorType 覆盖。
    const val SCHEMA_VERSION = 12

    val schemaVersion = PreferenceKey.int("app_settings_schema_version", SCHEMA_VERSION)
    val uiStyle = PreferenceKey.string("ui_style", UiThemeStyle.Miuix.storageName)
    val themeMode = PreferenceKey.string("theme_mode", AppThemeMode.System.name)
    val colorSource = PreferenceKey.string("color_source", AppColorSource.SeedColor.name)
    val seedColorArgb = PreferenceKey.int("seed_color_argb", 0xFF7B4DFF.toInt())
    val presetId = PreferenceKey.string("theme_preset_id", "default")
    val paletteStyle = PreferenceKey.string("palette_style", AppPaletteStyle.TonalSpot.name)
    val amoledDark = PreferenceKey.boolean("amoled_dark", false)
    val themeAnimations = PreferenceKey.boolean("theme_animations", true)

    // 默认开启，保证旧版本（schema 1 及更早）升级后仍保持原有 Monet 配色。
    val miuixMonet = PreferenceKey.boolean("miuix_monet", true)

    // 默认关闭，保证旧版本（schema 2 及更早）升级后仍是原有常规底栏。
    val floatingNavigationBar = PreferenceKey.boolean("floating_navigation_bar", false)

    // 默认跟随调色板，保证旧版本（schema 3 及更早）升级后配色外观不变。
    val colorSpec = PreferenceKey.string("color_spec", AppColorSpec.Auto.name)

    // 默认 100%，保证旧版本（schema 4 及更早）升级后界面尺寸不变。
    val uiScalePercent = PreferenceKey.int("ui_scale_percent", 100)

    // 默认关闭，保证旧版本（schema 5 及更早）升级后仍是原有纯色顶栏与底栏。
    val blurEnabled = PreferenceKey.boolean("blur_enabled", false)

    val usrToken = PreferenceKey.string("usr_token", "")
    val usrInfo = PreferenceKey.string("usr_info", "")
    val loginId = PreferenceKey.string("login_id", "")

    val authKeys: List<PreferenceKey<*>> = listOf(usrToken, usrInfo, loginId)

    val vehicleLastMac = PreferenceKey.string("vehicle_last_mac", "")
    val vehicleLastName = PreferenceKey.string("vehicle_last_name", "")
    val vehicleAutoConnect = PreferenceKey.boolean("vehicle_auto_connect", false)
    val vehicleForegroundService = PreferenceKey.boolean("vehicle_foreground_service", true)
    val vehicleBoundVin = PreferenceKey.string("vehicle_bound_vin", "")
    val vehicleStatusVin = PreferenceKey.string("vehicle_status_vin", "")
    val vehicleStatusPacket = PreferenceKey.string("vehicle_status_packet", "")
    val vehicleCloudThemeVin = PreferenceKey.string("vehicle_cloud_theme_vin", "")
    val vehicleCloudThemeLoveCarImageUrl =
        PreferenceKey.string("vehicle_cloud_theme_love_car_image_url", "")
    val vehicleCloudThemeType = PreferenceKey.int("vehicle_cloud_theme_type", 0)
    val vehicleCloudThemeHasBannerBackground =
        PreferenceKey.boolean("vehicle_cloud_theme_has_banner_bg", false)
    val vehicleCloudThemeGameThemeColorType =
        PreferenceKey.int("vehicle_cloud_theme_game_color_type", 0)
    val vehicleCloudThemeColorOverride =
        PreferenceKey.int("vehicle_cloud_theme_color_override", 0)

    val vehicleKeys: List<PreferenceKey<*>> = listOf(
        vehicleLastMac,
        vehicleLastName,
        vehicleAutoConnect,
        vehicleForegroundService,
        vehicleBoundVin,
        vehicleStatusVin,
        vehicleStatusPacket,
        vehicleCloudThemeVin,
        vehicleCloudThemeLoveCarImageUrl,
        vehicleCloudThemeType,
        vehicleCloudThemeHasBannerBackground,
        vehicleCloudThemeGameThemeColorType,
    )

    val all: List<PreferenceKey<*>> = listOf(
        schemaVersion,
        uiStyle,
        themeMode,
        colorSource,
        seedColorArgb,
        presetId,
        paletteStyle,
        amoledDark,
        themeAnimations,
        miuixMonet,
        floatingNavigationBar,
        colorSpec,
        uiScalePercent,
        blurEnabled,
    )
}

data class VehicleConnectionPreferences(
    val lastMac: String = "",
    val lastName: String = "",
    val autoConnect: Boolean = false,
    val foregroundService: Boolean = true,
    val boundVin: String = "",
)

class AppPreferences(
    private val store: PreferenceStore,
    private val secretStore: SecretStore? = null,
) : VehicleStatusCache, VehicleCloudThemeCache {
    fun read(): AppAppearance = decode(
        uiStyle = store.read(AppPreferenceKeys.uiStyle),
        themeMode = store.read(AppPreferenceKeys.themeMode),
        colorSource = store.read(AppPreferenceKeys.colorSource),
        seedColorArgb = store.read(AppPreferenceKeys.seedColorArgb),
        presetId = store.read(AppPreferenceKeys.presetId),
        paletteStyle = store.read(AppPreferenceKeys.paletteStyle),
        amoledDark = store.read(AppPreferenceKeys.amoledDark),
        themeAnimations = store.read(AppPreferenceKeys.themeAnimations),
        miuixMonet = store.read(AppPreferenceKeys.miuixMonet),
        floatingNavigationBar = store.read(AppPreferenceKeys.floatingNavigationBar),
        colorSpec = store.read(AppPreferenceKeys.colorSpec),
        uiScalePercent = store.read(AppPreferenceKeys.uiScalePercent),
        blurEnabled = store.read(AppPreferenceKeys.blurEnabled),
    )

    fun observe(): Flow<AppAppearance> {
        val flows = AppPreferenceKeys.all.map { key ->
            store.observe(key).map { value -> value as Any }
        }
        // 索引与 AppPreferenceKeys.all 的顺序一一对应，新增 key 时必须同步调整。
        return combine(flows) { values ->
            decode(
                uiStyle = values[1] as String,
                themeMode = values[2] as String,
                colorSource = values[3] as String,
                seedColorArgb = values[4] as Int,
                presetId = values[5] as String,
                paletteStyle = values[6] as String,
                amoledDark = values[7] as Boolean,
                themeAnimations = values[8] as Boolean,
                miuixMonet = values[9] as Boolean,
                floatingNavigationBar = values[10] as Boolean,
                colorSpec = values[11] as String,
                uiScalePercent = values[12] as Int,
                blurEnabled = values[13] as Boolean,
            )
        }
    }

    suspend fun write(appearance: AppAppearance): PreferenceWriteResult {
        // 未来版本写入的配置不得被当前版本覆盖，避免降级时丢数据。
        val storedSchemaVersion = store.read(AppPreferenceKeys.schemaVersion)
        if (storedSchemaVersion > AppPreferenceKeys.SCHEMA_VERSION) {
            return PreferenceWriteResult.Failure(
                IllegalStateException(
                    "Stored settings schema $storedSchemaVersion is newer than supported " +
                        "${AppPreferenceKeys.SCHEMA_VERSION}.",
                ),
            )
        }

        val writes = listOf(
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
            AppPreferenceKeys.uiStyle to appearance.uiStyle.storageName,
            AppPreferenceKeys.themeMode to appearance.themeMode.name,
            AppPreferenceKeys.colorSource to appearance.colorSource.name,
            AppPreferenceKeys.seedColorArgb to appearance.seedColorArgb,
            AppPreferenceKeys.presetId to appearance.presetId,
            AppPreferenceKeys.paletteStyle to appearance.paletteStyle.name,
            AppPreferenceKeys.amoledDark to appearance.amoledDarkEnabled,
            AppPreferenceKeys.themeAnimations to appearance.themeAnimationsEnabled,
            AppPreferenceKeys.miuixMonet to appearance.miuixMonetEnabled,
            AppPreferenceKeys.floatingNavigationBar to appearance.floatingNavigationBarEnabled,
            AppPreferenceKeys.colorSpec to appearance.colorSpec.name,
            AppPreferenceKeys.uiScalePercent to AppInterfaceScale.percentOf(appearance.uiScale),
            AppPreferenceKeys.blurEnabled to appearance.blurEnabled,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun reset(): PreferenceWriteResult = write(AppAppearance())

    /** 读取持久化登录态；损坏的用户 JSON 会回退为未登录。 */
    fun readAuth(): AuthSessionSnapshot {
        val token = readSecureToken()
        // Token 单独保存；旧 profile 里即使还带 token，也以安全存储/旧 token 字段为准。
        val user = AuthProfileCodec.decode(store.read(AppPreferenceKeys.usrInfo))
            ?.copy(token = token)
        return AuthSessionSnapshot(
            token = token,
            user = user,
            loginId = store.read(AppPreferenceKeys.loginId),
        )
    }

    /** 保存登录态。`usr_info` 使用与原实现相同的 JSON 字段名。 */
    suspend fun writeAuth(user: AuthUser): PreferenceWriteResult {
        val storedSchemaVersion = store.read(AppPreferenceKeys.schemaVersion)
        if (storedSchemaVersion > AppPreferenceKeys.SCHEMA_VERSION) {
            return PreferenceWriteResult.Failure(
                IllegalStateException(
                    "Stored settings schema $storedSchemaVersion is newer than supported " +
                        "${AppPreferenceKeys.SCHEMA_VERSION}.",
                ),
            )
        }
        val currentLoginId = store.read(AppPreferenceKeys.loginId)
            .ifBlank { newLoginId() }
        val tokenResult = writeSecureToken(user.token)
        if (tokenResult != PreferenceWriteResult.Success) return tokenResult
        val writes = listOf(
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
            AppPreferenceKeys.usrInfo to AuthProfileCodec.encodeProfile(user),
            AppPreferenceKeys.loginId to currentLoginId,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    /**
     * 迁移旧版本 profile：去掉 `usr_info` 中的明文 token。
     *
     * 新登录写入已经使用 [AuthProfileCodec.encodeProfile]；这里覆盖升级用户，
     * 在应用启动时调用一次即可。
     */
    suspend fun migrateLegacyAuthProfile(): PreferenceWriteResult {
        val raw = store.read(AppPreferenceKeys.usrInfo)
        val user = AuthProfileCodec.decode(raw) ?: return PreferenceWriteResult.Success
        val token = readSecureToken()
        val sanitized = AuthProfileCodec.encodeProfile(user.copy(token = token))
        if (sanitized == raw) return PreferenceWriteResult.Success
        return writeTyped(AppPreferenceKeys.usrInfo, sanitized)
    }

    /** 只清理登录相关 key，不动外观设置。 */
    suspend fun clearAuth(): PreferenceWriteResult {
        val secretResult = deleteSecureToken()
        if (secretResult != PreferenceWriteResult.Success) return secretResult
        for (key in AppPreferenceKeys.authKeys) {
            val result = store.delete(key)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    private fun readSecureToken(): String {
        val secure = secretStore?.let {
            runCatching { it.read(AppSecretKeys.userToken) }.getOrNull()
        }
        // 未接入安全后端的平台继续读取旧字段；Android 上安全存储会负责迁移并删除它。
        return secure ?: store.read(AppPreferenceKeys.usrToken)
    }

    private suspend fun writeSecureToken(token: String): PreferenceWriteResult {
        val secure = secretStore ?: return writeTyped(AppPreferenceKeys.usrToken, token)
        return runCatching {
            secure.write(AppSecretKeys.userToken, token)
            // 安全写入完成后清理普通偏好里的旧明文。
            store.delete(AppPreferenceKeys.usrToken)
        }.getOrElse { PreferenceWriteResult.Failure(it) }
    }

    private suspend fun deleteSecureToken(): PreferenceWriteResult {
        val secure = secretStore ?: return PreferenceWriteResult.Success
        return runCatching {
            secure.delete(AppSecretKeys.userToken)
            PreferenceWriteResult.Success
        }.getOrElse { PreferenceWriteResult.Failure(it) }
    }

    fun readVehicleConnection(): VehicleConnectionPreferences =
        VehicleConnectionPreferences(
            lastMac = store.read(AppPreferenceKeys.vehicleLastMac),
            lastName = store.read(AppPreferenceKeys.vehicleLastName),
            autoConnect = store.read(AppPreferenceKeys.vehicleAutoConnect),
            foregroundService = store.read(AppPreferenceKeys.vehicleForegroundService),
            boundVin = store.read(AppPreferenceKeys.vehicleBoundVin),
        )

    override fun readVehicleStatus(): CachedVehicleStatus? {
        val vin = store.read(AppPreferenceKeys.vehicleStatusVin).trim()
        val packetHex = store.read(AppPreferenceKeys.vehicleStatusPacket).trim()
        if (vin.isBlank() || packetHex.isBlank()) return null
        return CachedVehicleStatus(vin = vin, packetHex = packetHex)
    }

    override suspend fun writeVehicleStatus(status: CachedVehicleStatus) {
        writeTyped(AppPreferenceKeys.vehicleStatusVin, status.vin)
        writeTyped(AppPreferenceKeys.vehicleStatusPacket, status.packetHex)
    }

    override suspend fun clearVehicleStatus() {
        store.delete(AppPreferenceKeys.vehicleStatusVin)
        store.delete(AppPreferenceKeys.vehicleStatusPacket)
    }

    override fun readVehicleCloudTheme(vin: String): VehicleCloudTheme? {
        val cachedVin = store.read(AppPreferenceKeys.vehicleCloudThemeVin).trim()
        if (cachedVin.isBlank() || cachedVin != vin.trim()) return null
        return VehicleCloudTheme(
            vin = cachedVin,
            loveCarImageUrl = store.read(AppPreferenceKeys.vehicleCloudThemeLoveCarImageUrl),
            themeType = store.read(AppPreferenceKeys.vehicleCloudThemeType),
            hasBannerVehicleBgImg =
                store.read(AppPreferenceKeys.vehicleCloudThemeHasBannerBackground),
            gameThemeColorType = store.read(
                AppPreferenceKeys.vehicleCloudThemeGameThemeColorType,
            ).takeIf { it in 1..3 },
        )
    }

    override suspend fun writeVehicleCloudTheme(theme: VehicleCloudTheme) {
        val writes = listOf(
            AppPreferenceKeys.vehicleCloudThemeVin to theme.vin,
            AppPreferenceKeys.vehicleCloudThemeLoveCarImageUrl to theme.loveCarImageUrl,
            AppPreferenceKeys.vehicleCloudThemeType to theme.themeType,
            AppPreferenceKeys.vehicleCloudThemeHasBannerBackground to
                theme.hasBannerVehicleBgImg,
            AppPreferenceKeys.vehicleCloudThemeGameThemeColorType to
                (theme.gameThemeColorType ?: 0),
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
        )
        for ((key, value) in writes) {
            writeTyped(key, value)
        }
    }

    override suspend fun clearVehicleCloudTheme() {
        store.delete(AppPreferenceKeys.vehicleCloudThemeVin)
        store.delete(AppPreferenceKeys.vehicleCloudThemeLoveCarImageUrl)
        store.delete(AppPreferenceKeys.vehicleCloudThemeType)
        store.delete(AppPreferenceKeys.vehicleCloudThemeHasBannerBackground)
        store.delete(AppPreferenceKeys.vehicleCloudThemeGameThemeColorType)
    }

    /**
     * `0` 表示跟随云端 `bikeGameThemeVOList.colorType`；`1..3` 是本机外观覆盖。
     */
    fun readVehicleCloudThemeColorOverride(): Int? =
        store.read(AppPreferenceKeys.vehicleCloudThemeColorOverride)
            .takeIf { it in 1..3 }

    suspend fun writeVehicleCloudThemeColorOverride(colorType: Int?): PreferenceWriteResult {
        val normalized = colorType?.takeIf { it in 1..3 } ?: 0
        val writes = listOf(
            AppPreferenceKeys.vehicleCloudThemeColorOverride to normalized,
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun writeLastVehicle(
        mac: String,
        name: String,
        boundVin: String? = null,
    ): PreferenceWriteResult {
        val writes = buildList {
            add(AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION)
            add(AppPreferenceKeys.vehicleLastMac to mac)
            add(AppPreferenceKeys.vehicleLastName to name)
            if (boundVin != null) {
                add(AppPreferenceKeys.vehicleBoundVin to boundVin)
            }
        }
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun writeVehiclePreferences(
        autoConnect: Boolean = readVehicleConnection().autoConnect,
        foregroundService: Boolean = readVehicleConnection().foregroundService,
    ): PreferenceWriteResult {
        val writes = listOf(
            AppPreferenceKeys.schemaVersion to AppPreferenceKeys.SCHEMA_VERSION,
            AppPreferenceKeys.vehicleAutoConnect to autoConnect,
            AppPreferenceKeys.vehicleForegroundService to foregroundService,
        )
        for ((key, value) in writes) {
            val result = writeTyped(key, value)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    suspend fun clearVehicleSession(): PreferenceWriteResult {
        for (key in AppPreferenceKeys.vehicleKeys) {
            val result = store.delete(key)
            if (result != PreferenceWriteResult.Success) return result
        }
        return PreferenceWriteResult.Success
    }

    /**
     * 生成并保存首次启动的设备标识。
     *
     * 原客户端的 `deviceId` 与登录态分开维护：首次请求前就要存在，
     * 不能等登录成功后才生成。已有值不会被覆盖。
     */
    suspend fun ensureDeviceId(candidate: String): String {
        val current = store.read(AppPreferenceKeys.loginId)
        if (current.isNotBlank()) return current
        if (candidate.isBlank()) return ""
        return when (val result = writeTyped(AppPreferenceKeys.loginId, candidate)) {
            PreferenceWriteResult.Success -> candidate
            is PreferenceWriteResult.Failure -> candidate
        }
    }

    private suspend fun writeTyped(key: PreferenceKey<*>, value: Any): PreferenceWriteResult =
        when (key.type) {
            PreferenceType.Boolean -> store.write(key as PreferenceKey<Boolean>, value as Boolean)
            PreferenceType.Int -> store.write(key as PreferenceKey<Int>, value as Int)
            PreferenceType.Long -> store.write(key as PreferenceKey<Long>, value as Long)
            PreferenceType.Float -> store.write(key as PreferenceKey<Float>, value as Float)
            PreferenceType.String -> store.write(key as PreferenceKey<String>, value as String)
            PreferenceType.StringSet -> store.write(key as PreferenceKey<Set<String>>, value as Set<String>)
        }

    private fun decode(
        uiStyle: String,
        themeMode: String,
        colorSource: String,
        seedColorArgb: Int,
        presetId: String,
        paletteStyle: String,
        amoledDark: Boolean,
        themeAnimations: Boolean,
        miuixMonet: Boolean,
        floatingNavigationBar: Boolean,
        colorSpec: String,
        uiScalePercent: Int,
        blurEnabled: Boolean,
    ): AppAppearance {
        // 未知枚举、损坏值一律回退到安全默认值，保证读取不崩溃。
        // 旧版本缺失的新键都由 PreferenceKey 默认值补齐
        // （miuix_monet 补 true，floating_navigation_bar 补 false，color_spec 补 Auto），
        // 因此每次版本升级都是纯增量迁移，不会丢弃用户已有配置。
        return AppAppearance(
            uiStyle = UiThemeStyle.fromStorageName(uiStyle),
            themeMode = AppThemeMode.entries.firstOrNull { it.name == themeMode } ?: AppThemeMode.System,
            colorSource = AppColorSource.entries.firstOrNull { it.name == colorSource } ?: AppColorSource.SeedColor,
            seedColorArgb = seedColorArgb,
            presetId = presetId.ifBlank { "default" },
            paletteStyle = AppPaletteStyle.entries.firstOrNull { it.name == paletteStyle } ?: AppPaletteStyle.TonalSpot,
            amoledDarkEnabled = amoledDark,
            themeAnimationsEnabled = themeAnimations,
            miuixMonetEnabled = miuixMonet,
            floatingNavigationBarEnabled = floatingNavigationBar,
            colorSpec = AppColorSpec.entries.firstOrNull { it.name == colorSpec } ?: AppColorSpec.Auto,
            // 界面缩放以百分比整数存储；损坏值由 AppInterfaceScale 收敛到最近的合法档位。
            uiScale = AppInterfaceScale.percentToScale(uiScalePercent),
            blurEnabled = blurEnabled,
        )
    }

    private fun newLoginId(): String {
        val bytes = ByteArray(8) { Random.nextInt(0, 256).toByte() }
        return "device-" + bytes.joinToString("") {
            ((it.toInt() and 0xFF)).toString(16).padStart(2, '0')
        }
    }
}
