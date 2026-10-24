package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/**
 * `user/theme/queryUserApplyThemeAndVariedTheme` 的界面子集。
 *
 * `loveCarImageUrl` 对应 `userApplyThemeVo.themeImageVos` 中
 * `range1=1 && range2=2` 的 `pictureUrl`，是车辆页云端背景，不是车辆本体图。
 *
 * [hasBannerVehicleBgImg] 表示当前 VIN 命中的动态主题配置包含
 * `bannerVehicleBgImg`。当前原 App 的六个定制主题资产都包含该键，且
 * `bikeGameThemeVOList.colorType` 的 1/2/3 分别对应这些资产。
 */
data class VehicleCloudTheme(
    val vin: String = "",
    val loveCarImageUrl: String = "",
    val themeType: Int = 0,
    val hasBannerVehicleBgImg: Boolean = false,
    val gameThemeColorType: Int? = null,
) {
    val isCustomized: Boolean
        get() = hasBannerVehicleBgImg ||
            (gameThemeColorType?.let { it in CUSTOM_GAME_THEME_COLOR_TYPES } == true)

    val displayName: String
        get() = when (gameThemeColorType) {
            1 -> "绝区零联动 · Lycaon / 冯·莱卡恩"
            2 -> "绝区零联动 · Bangboo / 邦布"
            3 -> "绝区零联动 · Ellen Joe / 艾莲·乔"
            else -> if (isCustomized) "绝区零联动车辆" else "标准主题"
        }

    /**
     * 原 App 的动态主题按 GmeThemeType 选择本地样式，再由样式中的
     * `bannerVehicleBgImg.icon.url` 提供车图背景。
     */
    fun bannerVehicleBgImgUrl(darkTheme: Boolean): String {
        val style = VehicleDynamicThemeStyle.fromColorType(gameThemeColorType) ?: return ""
        return if (darkTheme) style.bannerDarkUrl else style.bannerLightUrl
    }
}

fun VehicleCloudTheme?.withColorTypeOverride(colorType: Int?): VehicleCloudTheme? {
    if (colorType == null || colorType !in CUSTOM_GAME_THEME_COLOR_TYPES) return this
    val base = this ?: VehicleCloudTheme()
    return base.copy(
        hasBannerVehicleBgImg = true,
        gameThemeColorType = colorType,
    )
}

internal enum class VehicleDynamicThemeStyle(
    val colorType: Int,
    val bannerLightUrl: String,
    val bannerDarkUrl: String,
) {
    GOLD(
        colorType = 1,
        bannerLightUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_gold_light.png",
        bannerDarkUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_gold_dark.png",
    ),
    YELLOW(
        colorType = 2,
        bannerLightUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_orange_light.png",
        bannerDarkUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_orange_dark.png",
    ),
    PINK(
        colorType = 3,
        bannerLightUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_pink_light.png",
        bannerDarkUrl =
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_pink_dark.png",
    ),
    ;

    companion object {
        fun fromColorType(colorType: Int?): VehicleDynamicThemeStyle? =
            entries.firstOrNull { it.colorType == colorType }
    }
}

interface VehicleCloudThemeGateway {
    suspend fun load(
        vin: String,
        modelNo: String,
        seriesCode: String,
        cityCode: String,
    ): VehicleCloudTheme?
}

interface VehicleCloudThemeCache {
    fun readVehicleCloudTheme(vin: String): VehicleCloudTheme?
    suspend fun writeVehicleCloudTheme(theme: VehicleCloudTheme)
    suspend fun clearVehicleCloudTheme()
}

interface VehicleCloudThemeSource {
    val theme: StateFlow<VehicleCloudTheme?>
}

class MutableVehicleCloudThemeSource : VehicleCloudThemeSource {
    private val _theme = MutableStateFlow<VehicleCloudTheme?>(null)
    override val theme: StateFlow<VehicleCloudTheme?> = _theme.asStateFlow()

    fun update(theme: VehicleCloudTheme?) {
        _theme.value = theme
    }
}

/**
 * 用户已应用主题的只读客户端。
 *
 * 原 App 使用 POST + `_gateway=True`，请求参数为 `cityCode`、`vin`、
 * `modelNo`、`seriesCode`。主题图片列表只接受原实现确认的五个 `range1`
 * 分组，并通过 `range2` 区分未选中、选中和车辆背景图。
 */
class YadeaVehicleCloudThemeGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long = { 0L },
    private val baseUrl: String = DEFAULT_BASE_URL,
) : VehicleCloudThemeGateway {

    override suspend fun load(
        vin: String,
        modelNo: String,
        seriesCode: String,
        cityCode: String,
    ): VehicleCloudTheme? {
        val normalizedVin = vin.trim()
        if (normalizedVin.isBlank()) return null
        val token = tokenProvider().trim()
        if (token.isBlank()) return null

        val businessJson = sortedJson(
            mapOf(
                "_gateway" to "True",
                "cityCode" to cityCode.trim().ifBlank { DEFAULT_CITY_CODE },
                "modelNo" to modelNo.trim(),
                "seriesCode" to seriesCode.trim(),
                "vin" to normalizedVin,
            ),
        )
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write(
            "CLOUD_THEME -> POST ${baseUrl + PATH} " +
                "vin=$normalizedVin headers=${NetworkLog.headers(headers)}",
        )
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = baseUrl + PATH,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: run {
            NetworkLog.write("CLOUD_THEME !! no response")
            return null
        }
        if (response.status !in 200..299) {
            NetworkLog.write("CLOUD_THEME !! http ${response.status}")
            return null
        }
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        NetworkLog.write("CLOUD_THEME plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) {
            NetworkLog.write("CLOUD_THEME !! business failure")
            return null
        }
        val data = JsonSupport.objectField(plain, "data") ?: return null
        val theme = parseVehicleCloudTheme(
            themeConfigJson = data,
            vin = normalizedVin,
        )
        NetworkLog.write(
            "CLOUD_THEME values vin=${theme.vin} themeType=${theme.themeType} " +
                "loveCar=${theme.loveCarImageUrl.isNotBlank()} " +
                "customized=${theme.isCustomized}",
        )
        return theme
    }

    companion object {
        const val PATH = "user/theme/queryUserApplyThemeAndVariedTheme"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
        const val DEFAULT_CITY_CODE = "110101"
        internal val SUPPORTED_THEME_RANGES = listOf(2, 3, 1, 5, 4)
    }
}

internal fun parseVehicleCloudTheme(
    themeConfigJson: String,
    vin: String,
): VehicleCloudTheme {
    val currentGameTheme = JsonSupport.arrayObjects(themeConfigJson, "bikeGameThemeVOList")
        .firstOrNull { JsonSupport.string(it, "vin") == vin }
    val gameThemeColorType = JsonSupport.int(currentGameTheme.orEmpty(), "colorType")
        ?.takeIf { it in CUSTOM_GAME_THEME_COLOR_TYPES }
    val hasBannerVehicleBgImg = gameThemeColorType != null
    val userApplyTheme = JsonSupport.objectField(themeConfigJson, "userApplyThemeVo")
        ?: return VehicleCloudTheme(
            vin = vin,
            hasBannerVehicleBgImg = hasBannerVehicleBgImg,
            gameThemeColorType = gameThemeColorType,
        )
    val rawThemeType = JsonSupport.int(userApplyTheme, "themeType") ?: 0
    val normalizedThemeType = if (rawThemeType == 1 || rawThemeType == 2) 0 else rawThemeType
    val supportedImages = JsonSupport.arrayObjects(userApplyTheme, "themeImageVos")
        .mapNotNull(::parseThemeImage)
        .filter { it.range1 in YadeaVehicleCloudThemeGateway.SUPPORTED_THEME_RANGES }
    val loveCarImageUrl = supportedImages.firstOrNull {
        it.range1 == LOVE_CAR_THEME_RANGE && it.range2 == LOVE_CAR_BACKGROUND_RANGE
    }?.pictureUrl.orEmpty()

    return VehicleCloudTheme(
        vin = vin,
        loveCarImageUrl = loveCarImageUrl,
        themeType = normalizedThemeType,
        hasBannerVehicleBgImg = hasBannerVehicleBgImg,
        gameThemeColorType = gameThemeColorType,
    )
}

private data class ThemeImage(
    val pictureUrl: String,
    val range1: Int,
    val range2: Int,
)

private fun parseThemeImage(json: String): ThemeImage? {
    val pictureUrl = JsonSupport.string(json, "pictureUrl")?.trim().orEmpty()
    if (pictureUrl.isBlank()) return null
    return ThemeImage(
        pictureUrl = pictureUrl,
        range1 = JsonSupport.int(json, "range1") ?: return null,
        range2 = JsonSupport.int(json, "range2") ?: return null,
    )
}

private fun sortedJson(fields: Map<String, String>): String = buildString {
    append('{')
    fields.keys.sorted().forEachIndexed { index, key ->
        if (index > 0) append(',')
        append('"').append(key).append("\":\"")
            .append(fields.getValue(key).replace("\\", "\\\\").replace("\"", "\\\""))
            .append('"')
    }
    append('}')
}

class VehicleCloudThemeController(
    private val source: MutableVehicleCloudThemeSource,
    private val gateway: VehicleCloudThemeGateway,
    private val scope: CoroutineScope,
    private val cache: VehicleCloudThemeCache? = null,
) {
    private var refreshJob: Job? = null
    private var requestedVin: String = ""

    /**
     * VIN 变化时立即切换到该车辆的缓存，再异步刷新云端主题，避免短暂显示上一辆车背景。
     */
    fun refresh(vin: String, modelNo: String, seriesCode: String) {
        val normalizedVin = vin.trim()
        refreshJob?.cancel()
        requestedVin = normalizedVin
        if (normalizedVin.isBlank()) {
            source.update(null)
            return
        }

        source.update(cache?.readVehicleCloudTheme(normalizedVin))
        refreshJob = scope.launch {
            val theme = runCatching {
                gateway.load(
                    vin = normalizedVin,
                    modelNo = modelNo,
                    seriesCode = seriesCode,
                    cityCode = YadeaVehicleCloudThemeGateway.DEFAULT_CITY_CODE,
                )
            }.getOrNull() ?: return@launch
            if (requestedVin != normalizedVin) {
                return@launch
            }
            source.update(theme)
            cache?.writeVehicleCloudTheme(theme)
        }
    }
}

private const val LOVE_CAR_THEME_RANGE = 1
private const val LOVE_CAR_BACKGROUND_RANGE = 2
private val CUSTOM_GAME_THEME_COLOR_TYPES = 1..3
