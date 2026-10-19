package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpResponse
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/** 云端 `CarSettingBean$BikeConfig` 的 UI 子集。 */
data class CloudBikeConfig(
    val confId: String,
    val confName: String = "",
    val copy: String = "",
    val briefCopy: String = "",
    val newCopy: String = "",
    val parentId: String = "",
    val parentName: String = "",
    val sort: String = "",
    /** 云端未下发时保持 `null`；只有明确为 `false` 才视为无写权限。 */
    val isAuth: Boolean? = null,
    val isIntelligence: Boolean = false,
    val isUserLabFun: Boolean = false,
    val izLab: Int = 1,
    val copyType: Int = 0,
    val copyStatus: Int = 0,
    val icon: Int = 0,
)

/** `selectBikeSettingByVin` 的 `CarSettingBean`：设置项清单 + 扁平值。 */
data class CloudCarSettings(
    val configs: List<CloudBikeConfig>,
    val values: Map<String, String>,
) {
    fun intValue(key: String): Int? = values[key]?.toDoubleOrNull()?.toInt()

    fun boolValue(key: String): Boolean? = when (values[key]?.trim()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}

interface CarSettingsGateway {
    suspend fun load(vin: String): CloudCarSettings?
    suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = false
    suspend fun checkStatus(configId: String): Boolean? = null
}

/** 已确认的云端设置写入字段。 */
data class CloudCarSettingUpdate(
    val configId: String,
    val fields: Map<String, String>,
)

/**
 * `updateBikeSetting` 的直接字段写入。
 *
 * 只收纳 smali 中能确认字段名的布尔设置；需要 `valueJson` 的定位/充电命令
 * 暂不猜测，继续保留为入口或只读项。
 */
object CloudCarSettingUpdateBuilder {
    fun supports(configId: String, kind: CarSettingKind): Boolean =
        build(configId, kind, vin = "vin") != null

    fun build(
        configId: String,
        kind: CarSettingKind,
        vin: String,
    ): CloudCarSettingUpdate? {
        val (field, value) = when (kind) {
            is CarSettingKind.Toggle -> {
                val field = TOGGLE_FIELDS[configId] ?: return null
                field to if (kind.checked) "1" else "0"
            }
            is CarSettingKind.Slider -> {
                val field = SLIDER_FIELDS[configId] ?: return null
                field to kind.value.toInt().toString()
            }
            else -> return null
        }
        return CloudCarSettingUpdate(
            configId = configId,
            fields = mapOf(
                "_gateway" to "True",
                field to value,
                "vin" to vin,
            ),
        )
    }

    /** smali `VehicleSettingViewModel.P4` 与设置返回字段能直接对应的开关。 */
    private val TOGGLE_FIELDS = mapOf(
        "9" to "alarmOn",
        "13" to "alarmSoundEnable",
        "16" to "dumpAlarm",
        "17" to "resistanceOn",
        "24" to "emergencyContact",
        "25" to "passwordUnlockEnable",
        "40" to "colorEggSoundEffect",
        "41" to "userDefinedSoundEffect",
        "53" to "bleApproachPowerOnEnable",
        "75" to "tamperProofIotEnable",
        "76" to "positioningFunc",
        "77" to "sleepMode",
        "82" to "lowBatTelWarning",
        "91" to "sosEmergencyCall",
        "95" to "wxhlBind",
    )

    private val SLIDER_FIELDS = mapOf(
        "61" to "highestSpeedLimit",
    )
}

/**
 * `vehiclebase/bike-setting/selectBikeSettingByVin`
 *
 * 请求字段只有 `vin`（签名前加入 `_gateway=True`），响应为 `CarSettingBean`。
 */
class YadeaCarSettingsGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : CarSettingsGateway {

    override suspend fun load(vin: String): CloudCarSettings? {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return null
        val plain = postFields(
            path = PATH,
            fields = mapOf("_gateway" to "True", "vin" to vin),
            token = token,
        ) ?: return null
        NetworkLog.write("SETTINGS plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) return null
        val data = JsonSupport.objectField(plain, "data") ?: return null
        val configs = JsonSupport.arrayObjects(data, "bikeConfigVos")
            .mapNotNull(::parseBikeConfig)
        val values = VALUE_KEYS.mapNotNull { key ->
            readValue(data, key)?.let { key to it }
        }.toMap()
        return CloudCarSettings(configs = configs, values = values)
    }

    override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return false
        val plain = postFields(
            path = UPDATE_PATH,
            fields = update.fields + ("vin" to vin),
            token = token,
        ) ?: return false
        if (JsonSupport.boolean(plain, "success") == false) return false
        return JsonSupport.boolean(plain, "data") != false
    }

    override suspend fun checkStatus(configId: String): Boolean? {
        val token = tokenProvider().trim()
        if (token.isBlank() || configId.isBlank()) return null
        val businessJson = sortedJson(mapOf("id" to configId))
        val url = baseUrl + CHECK_STATUS_PATH + "?" +
            GatewaySigner.formBody(businessJson, effectiveTimestamp())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = headers,
            ),
        ) ?: return null
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        if (JsonSupport.boolean(plain, "success") == false) return false
        val data = JsonSupport.objectField(plain, "data") ?: plain
        val result = JsonSupport.int(data, "result")
            ?: JsonSupport.string(data, "result")?.toIntOrNull()
        return result == 0
    }

    private var timestampOffsetMs: Long = 0L

    private fun effectiveTimestamp(): Long = timestampProvider() + timestampOffsetMs

    private suspend fun postFields(
        path: String,
        fields: Map<String, String>,
        token: String,
    ): String? {
        val url = baseUrl + path
        val body = GatewaySigner.formBody(sortedJson(fields), timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write(
            "SETTINGS -> POST $url headers=${NetworkLog.headers(headers)} " +
                NetworkLog.formSummary(body),
        )
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: run {
            NetworkLog.write("SETTINGS !! no response")
            return null
        }
        NetworkLog.write(
            "SETTINGS <- ${response.status} raw=${NetworkLog.body(response.body)}",
        )
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        return Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
    }

    private fun parseBikeConfig(json: String): CloudBikeConfig? {
        val confId = textOrNumber(json, "confId").orEmpty().trim()
        if (confId.isBlank()) return null
        return CloudBikeConfig(
            confId = confId,
            confName = JsonSupport.string(json, "confName").orEmpty(),
            copy = JsonSupport.string(json, "copy").orEmpty(),
            briefCopy = JsonSupport.string(json, "briefCopy").orEmpty(),
            newCopy = JsonSupport.string(json, "newCopy").orEmpty(),
            parentId = textOrNumber(json, "parentId").orEmpty(),
            parentName = JsonSupport.string(json, "parentName").orEmpty(),
            sort = textOrNumber(json, "sort").orEmpty(),
            isAuth = boolFieldOrNull(json, "isAuth"),
            isIntelligence = boolField(json, "isIntelligence"),
            isUserLabFun = boolField(json, "isUserLabFun"),
            izLab = intField(json, "izLab") ?: 1,
            copyType = intField(json, "copyType") ?: 0,
            copyStatus = intField(json, "copyStatus") ?: 0,
            icon = intField(json, "icon") ?: 0,
        )
    }

    private fun textOrNumber(json: String, key: String): String? =
        JsonSupport.string(json, key) ?: JsonSupport.numberText(json, key)

    private fun readValue(json: String, key: String): String? =
        JsonSupport.string(json, key)
            ?: JsonSupport.numberText(json, key)
            ?: JsonSupport.boolean(json, key)?.toString()

    private fun intField(json: String, key: String): Int? =
        JsonSupport.int(json, key) ?: JsonSupport.string(json, key)?.toIntOrNull()

    private fun boolField(json: String, key: String): Boolean =
        JsonSupport.boolean(json, key)
            ?: when (JsonSupport.string(json, key)?.trim()?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> when (JsonSupport.numberText(json, key)) {
                    "1" -> true
                    "0" -> false
                    else -> false
                }
            }

    private fun boolFieldOrNull(json: String, key: String): Boolean? {
        if (!json.contains("\"$key\"")) return null
        return boolField(json, key)
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

    companion object {
        const val PATH = "vehiclebase/bike-setting/selectBikeSettingByVin"
        const val UPDATE_PATH = "vehiclebase/bike-setting/updateBikeSetting"
        const val CHECK_STATUS_PATH = "common-cmd/low-freq/info/param"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"

        private val VALUE_KEYS = listOf(
            "alarmOn", "alarmPower", "alarmSoundEnable", "bikeInfoSyncName",
            "bikeModelTopSpeedMax", "bikeModelTopSpeedMin", "bikeSpeedMax",
            "bleApproachPowerOnDistance", "bleApproachPowerOnEnable", "bleDistance",
            "bleLeavePowerOffDistance", "bleLeavePowerOffEnable", "bleUnlockOn",
            "capacity", "chargeUlVal", "chargeWattSwitch", "chargeWattVal",
            "colorEggSoundEffect", "cutPowerOn", "distance", "distanceOn",
            "dumpAlarm", "emergencyContact", "highestSpeedLimit", "lowBatTelWarning",
            "lowBatWarningEndTime", "lowBatWarningStartTime", "modeOn", "nfcOn",
            "offlineLock", "offlineLockValue", "onOffSound", "positioningFunc",
            "rechargeAppointment", "rechargeAppointmentBeginTime",
            "rechargeAppointmentEndTime", "resistanceOn", "sendBikeRecDynaOn",
            "shakeOn", "shutDownTime", "sleepMode", "sosEmergencyCall",
            "tamperProofIotEnable",
            "userDefinedSoundEffect", "vehiclePowerDownStatus", "voltage", "wxhlBind",
        )
    }
}

/** 将云端清单转换为现有设置控件模型。 */
object CloudCarSettingMapper {
    fun toItems(cloud: CloudCarSettings): List<CarSettingItem> {
        val items = cloud.configs.mapNotNull { config ->
            val confId = config.confId.trim()
            if (confId.isBlank()) return@mapNotNull null
            val classification = classificationOf(confId, config)
            val kind = kindOf(confId, cloud)
            val title = titleOf(config)
            val summary = summaryOf(config, title)
            CarSettingItem(
                id = "cloud:$confId",
                configId = confId,
                title = title,
                summary = summary,
                classification = classification,
                group = groupOf(config, classification),
                channel = channelOf(confId),
                kind = kind,
                // 云端没有下发 isAuth 时按“未禁止”处理；只有明确 false 才只读。
                writable = config.isAuth != false && (
                    VehicleSettingCommands.supports(confId, kind) ||
                        CloudCarSettingUpdateBuilder.supports(confId, kind)
                    ),
                sort = config.sort.toIntOrNull() ?: 0,
            )
        }.toMutableList()
        // ChargingPowerActivity 是“开关 + 瓦数滑杆”的二级页；云端只给一个
        // confId 47，这里拆成两个语义项，开关走 0xC9，滑杆走 0xCA。
        cloud.configs.firstOrNull { it.confId.trim() == "47" }?.let { config ->
            val classification = CarSettingClassification.BATTERY
            val enabled = cloud.boolValue("chargeWattSwitch") == true
            items += CarSettingItem(
                id = "cloud:47:enable",
                configId = "47",
                title = "充电功率自定义",
                summary = "开启后可设置充电功率上限",
                classification = classification,
                group = groupOf(config, classification),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(enabled),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) - 1,
            )
        }
        return items.sortedWith(compareBy({ it.group.sort }, { it.sort }, { it.title }))
    }

    private fun titleOf(config: CloudBikeConfig): String =
        config.confName.trim()
            .ifBlank { TITLES[config.confId].orEmpty() }
            .ifBlank { "设置 ${config.confId}" }

    private fun summaryOf(config: CloudBikeConfig, title: String): String {
        val raw = listOf(config.briefCopy, config.newCopy, config.copy)
            .firstOrNull { it.isNotBlank() }
            ?: return "云端车辆设置项"
        val text = readableCloudCopy(raw)
        return if (text.isBlank() || text == title.trim()) "云端车辆设置项" else text
    }

    /**
     * 云端 `newCopy` 是 `[{"type":"text","content":"..."}]` 形式的富文本 JSON，
     * `copy` 则可能是 HTML。这里只抽取可读文字，避免把原始标记直接显示在设置行里。
     */
    private fun readableCloudCopy(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val texts = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .findAll(trimmed)
                .map { unescapeJson(it.groupValues[1]) }
                .filter {
                    it.isNotBlank() &&
                        !it.startsWith("http://") &&
                        !it.startsWith("https://")
                }
                .toList()
            if (texts.isNotEmpty()) return texts.joinToString("\n")
        }
        return stripHtml(trimmed)
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun unescapeJson(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c != '\\' || i >= value.length) {
                out.append(c)
                continue
            }
            when (val escaped = value[i++]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 4 <= value.length) {
                        value.substring(i, i + 4).toIntOrNull(16)?.let { out.append(it.toChar()) }
                        i += 4
                    }
                }
                else -> out.append(escaped)
            }
        }
        return out.toString()
    }

    private fun groupOf(
        config: CloudBikeConfig,
        classification: CarSettingClassification,
    ): CarSettingGroup {
        val parentId = config.parentId.trim()
        return CarSettingGroup(
            id = parentId.ifBlank { classification.name },
            title = config.parentName.trim().ifBlank { classification.label },
            sort = parentId.toIntOrNull() ?: classification.ordinal,
        )
    }

    private fun classificationOf(
        confId: String,
        config: CloudBikeConfig,
    ): CarSettingClassification {
        if (config.isUserLabFun || config.izLab == 0 || confId.startsWith("special:footer_lab")) {
            return CarSettingClassification.LABORATORY
        }
        return when (confId) {
            in BATTERY_IDS -> CarSettingClassification.BATTERY
            in LIGHT_IDS -> CarSettingClassification.LIGHT
            in POWER_IDS -> CarSettingClassification.POWER
            in RIDE_IDS -> CarSettingClassification.RIDE_CONTROL
            in METER_IDS -> CarSettingClassification.METER
            in SECURITY_IDS -> CarSettingClassification.SECURITY
            else -> CarSettingClassification.OTHER
        }
    }

    private fun channelOf(confId: String): CarSettingChannel = when {
        confId in IOT_IDS -> CarSettingChannel.IOT
        confId.startsWith("special:") -> CarSettingChannel.LOCAL
        confId in ENTRANCE_IDS -> CarSettingChannel.LOCAL
        else -> CarSettingChannel.BLE
    }

    private fun kindOf(confId: String, cloud: CloudCarSettings): CarSettingKind = when (confId) {
        "13" -> CarSettingKind.Toggle(cloud.boolValue("alarmSoundEnable") == true)
        "16" -> CarSettingKind.Toggle(cloud.boolValue("dumpAlarm") == true)
        "3" -> CarSettingKind.Toggle(cloud.boolValue("resistanceOn") == true)
        "17" -> CarSettingKind.Toggle(cloud.boolValue("resistanceOn") == true)
        "40" -> CarSettingKind.Toggle(cloud.boolValue("colorEggSoundEffect") == true)
        "41" -> CarSettingKind.Toggle(cloud.boolValue("userDefinedSoundEffect") == true)
        "25" -> CarSettingKind.Toggle(cloud.boolValue("passwordUnlockEnable") == true)
        "53" -> CarSettingKind.Toggle(cloud.boolValue("bleApproachPowerOnEnable") == true)
        "77" -> CarSettingKind.Toggle(cloud.boolValue("sleepMode") == true)
        "91" -> CarSettingKind.Toggle(cloud.boolValue("sosEmergencyCall") == true)
        "95" -> CarSettingKind.Toggle(cloud.boolValue("wxhlBind") == true)
        // TCS/ABS 的当前值来自车辆 BLE 状态，云端清单只负责下发项目本身；
        // 初值由 CarSettingsController.updateVehicleStatus 回填。
        "37", "38" -> CarSettingKind.Toggle(false)
        // 模拟声浪、仪表亮度调节同样由子 Activity 读取车辆状态。
        "33", "86" -> CarSettingKind.Toggle(false)
        // 自动大灯、转向灯回位、刹车断电、陡坡缓降、坡道驻停。
        "19", "26", "36", "46", "49" -> CarSettingKind.Toggle(false)
        // 急刹爆闪、档位记忆、辅助驾驶小蓝灯使用动态位域命令。
        "68", "94", "84" -> CarSettingKind.Toggle(false)
        "76" -> CarSettingKind.Toggle(cloud.boolValue("positioningFunc") == true)
        "45" -> CarSettingKind.Toggle(false)
        "9" -> CarSettingKind.Toggle(cloud.boolValue("alarmOn") == true)
        "24" -> CarSettingKind.Toggle(cloud.boolValue("emergencyContact") == true)
        "75" -> CarSettingKind.Toggle(cloud.boolValue("tamperProofIotEnable") == true)
        "82" -> CarSettingKind.Toggle(cloud.boolValue("lowBatTelWarning") == true)
        "61" -> {
            val max = cloud.intValue("bikeSpeedMax")
                ?.takeIf { it in 15..120 }
                ?: cloud.intValue("bikeModelTopSpeedMax")?.takeIf { it in 15..120 }
                ?: 60
            val current = cloud.intValue("highestSpeedLimit")?.coerceIn(0, max) ?: 0
            CarSettingKind.Slider(
                value = current.toFloat(),
                range = 0f..max.toFloat(),
                steps = (max - 1).coerceAtLeast(0),
                unit = " km/h",
                // 0 表示不限速；非零最低 15 km/h。
                minimumNonZero = 15f,
            )
        }
        // 车辆音量和报警灵敏度都是 0..7 的档位，原 App 使用滑杆。
        "20", "14" -> CarSettingKind.Slider(
            value = 0f,
            range = 0f..7f,
            steps = 6,
        )
        "32", "42", "43", "44",
        "51", "63", "79", "80", "90", "2" ->
            CarSettingKind.Info("状态由车辆回读")
        "47" -> {
            // ChargingPowerActivity：100..1000W，50W 步进；最大值按云端/车型上限 1000W。
            val maxWatt = 1000
            val watt = (cloud.intValue("chargeWattVal")?.coerceIn(100, maxWatt) ?: 350)
                .let { it / 50 * 50 }
                .coerceIn(100, maxWatt)
            CarSettingKind.Slider(
                value = watt.toFloat(),
                range = 100f..maxWatt.toFloat(),
                steps = (maxWatt - 100) / 50 - 1,
                unit = " W",
            )
        }
        "48" -> {
            // ChargeCapActivity：70..100%，步进 5%。
            val cap = cloud.intValue("chargeUlVal")?.coerceIn(70, 100) ?: 100
            val snapped = (cap / 5 * 5).coerceIn(70, 100)
            CarSettingKind.Slider(
                value = snapped.toFloat(),
                range = 70f..100f,
                steps = 5,
                unit = "%",
            )
        }
        "10" -> CarSettingKind.Entrance(
            if (cloud.boolValue("distanceOn") == true) "已开启" else "去设置",
        )
        "52" -> CarSettingKind.Entrance(
            buildString {
                append(if (cloud.boolValue("bleUnlockOn") == true) "已开启" else "去设置")
                cloud.intValue("bleDistance")?.let { append(" · $it 米") }
            },
        )
        in ENTRANCE_IDS, in SPECIAL_IDS -> CarSettingKind.Entrance()
        else -> CarSettingKind.Info("云端设置项，暂未接入写入")
    }

    private val BATTERY_IDS = setOf("9", "47", "48")
    private val LIGHT_IDS = setOf(
        "5", "19", "20", "26", "33", "40", "41", "68", "73", "81", "84", "88", "93",
    )
    private val POWER_IDS = setOf(
        "2", "3", "7", "11", "15", "25", "39", "52", "53", "59", "79",
    )
    private val RIDE_IDS = setOf(
        "4", "17", "18", "43", "44", "45", "46", "49", "55", "58", "61", "63",
        "64", "72", "80", "89", "90", "92", "94", "1005",
    )
    private val METER_IDS = setOf("31", "42", "50", "51", "71", "86")
    private val SECURITY_IDS = setOf(
        "8", "10", "13", "14", "16", "24", "27", "32", "36", "37", "38", "69",
        "75", "76", "77", "82", "85", "91",
    )
    private val IOT_IDS = setOf(
        "9", "16", "24", "40", "47", "48", "60", "61", "62", "75", "76", "82", "91",
    )
    private val ENTRANCE_IDS = setOf(
        "4", "5", "7", "8", "9", "10", "11", "14", "15", "17", "18", "20", "24",
        "25", "27", "31", "33", "39", "41", "47", "48", "50", "55", "58", "59",
        "61", "64", "69", "71", "72", "73", "77", "81", "85", "86", "88", "89",
        "91", "92", "93", "95", "1005",
    )
    private val SPECIAL_IDS = setOf(
        "special:meter_wallpaper_skin",
        "special:meter_gauge_sync",
        "special:footer_intelligence",
        "special:footer_lab",
        "footer_device_detail",
    )

    private val TITLES = mapOf(
        "2" to "边撑",
        "3" to "携出助力",
        "4" to "档位设置",
        "5" to "音效开关",
        "7" to "自动落锁",
        "8" to "车辆报失",
        "9" to "低电量预警",
        "10" to "蓝牙感应范围",
        "11" to "自动开关机",
        "13" to "报警音",
        "14" to "报警灵敏度",
        "15" to "报警锁",
        "16" to "倾倒报警",
        "17" to "能量回收",
        "18" to "能量回收",
        "19" to "自动大灯",
        "20" to "音量",
        "24" to "紧急联系人",
        "25" to "密码设置",
        "26" to "转向灯复位",
        "27" to "防盗模式",
        "31" to "速度单位",
        "32" to "倾倒提醒",
        "33" to "模拟声浪",
        "36" to "刹车断电",
        "37" to "TCS 车身稳定",
        "38" to "ABS 防抱死",
        "39" to "边撑自动锁",
        "40" to "彩蛋音效",
        "41" to "自定义音效",
        "42" to "显示剩余里程",
        "43" to "倒车模式",
        "44" to "婴儿车模式",
        "45" to "定速巡航",
        "46" to "坡道缓降",
        "47" to "充电功率",
        "48" to "充电上限",
        "49" to "坡道驻车",
        "50" to "仪表风格切换",
        "51" to "仪表时间同步",
        "52" to "感应解锁",
        "53" to "支架供电",
        "55" to "自定义骑行",
        "58" to "档位设置",
        "59" to "报警锁",
        "60" to "电池设置",
        "61" to "最高限速",
        "62" to "座椅加热",
        "63" to "T 动力",
        "64" to "辅助骑行",
        "68" to "急刹灯",
        "69" to "雷达",
        "82" to "低电量提醒",
        "71" to "仪表设置",
        "72" to "长续航模式",
        "73" to "氛围灯设置",
        "75" to "IoT 防拆",
        "76" to "定位开关",
        "77" to "休眠模式",
        "79" to "座垫感应上电",
        "80" to "OTD 开关",
        "81" to "大灯延时",
        "84" to "辅助驾驶灯",
        "85" to "SOS 联系人",
        "86" to "仪表亮度切换",
        "88" to "自定义音效",
        "89" to "辅助骑行",
        "90" to "赛道模式",
        "91" to "SOS 紧急呼叫",
        "92" to "自定义骑行",
        "93" to "静音模式",
        "94" to "档位记忆",
        "95" to "微信互联",
        "1005" to "自定义按键",
        "special:meter_wallpaper_skin" to "壁纸皮肤设置",
        "special:meter_gauge_sync" to "车辆信息同步",
        "special:footer_intelligence" to "用户智能识别",
        "special:footer_lab" to "实验室",
        "footer_device_detail" to "设备详情",
    )
}
