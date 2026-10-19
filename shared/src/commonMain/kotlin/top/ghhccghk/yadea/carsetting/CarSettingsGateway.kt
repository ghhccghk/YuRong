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
    val isAuth: Boolean = false,
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
        val toggle = kind as? CarSettingKind.Toggle ?: return null
        val value = if (toggle.checked) "1" else "0"
        val field = when (configId) {
            "9" -> "alarmOn"
            "16" -> "dumpAlarm"
            "24" -> "emergencyContact"
            "40" -> "colorEggSoundEffect"
            "75" -> "tamperProofIotEnable"
            "82" -> "lowBatTelWarning"
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
            isAuth = boolField(json, "isAuth"),
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
    fun toItems(cloud: CloudCarSettings): List<CarSettingItem> =
        cloud.configs.mapNotNull { config ->
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
                writable = config.isAuth && (
                    VehicleSettingCommands.supports(confId, kind) ||
                        CloudCarSettingUpdateBuilder.supports(confId, kind)
                    ),
                sort = config.sort.toIntOrNull() ?: 0,
            )
        }.sortedWith(compareBy({ it.group.sort }, { it.sort }, { it.title }))

    private fun titleOf(config: CloudBikeConfig): String =
        config.confName.trim()
            .ifBlank { TITLES[config.confId].orEmpty() }
            .ifBlank { "设置 ${config.confId}" }

    private fun summaryOf(config: CloudBikeConfig, title: String): String =
        listOf(config.briefCopy, config.copy, config.newCopy)
            .firstOrNull { it.isNotBlank() && it.trim() != title }
            ?.trim()
            .orEmpty()
            .ifBlank { "云端车辆设置项" }

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
        "13" -> CarSettingKind.Toggle(cloud.boolValue("alarmOn") == true)
        "16" -> CarSettingKind.Toggle(cloud.boolValue("dumpAlarm") == true)
        "3" -> CarSettingKind.Toggle(cloud.boolValue("resistanceOn") == true)
        "40" -> CarSettingKind.Toggle(cloud.boolValue("colorEggSoundEffect") == true)
        "41" -> CarSettingKind.Toggle(cloud.boolValue("userDefinedSoundEffect") == true)
        "76" -> CarSettingKind.Toggle(cloud.boolValue("positioningFunc") == true)
        "45" -> CarSettingKind.Toggle(false)
        "9" -> CarSettingKind.Toggle(cloud.boolValue("alarmOn") == true)
        "24" -> CarSettingKind.Toggle(cloud.boolValue("emergencyContact") == true)
        "75" -> CarSettingKind.Toggle(cloud.boolValue("tamperProofIotEnable") == true)
        "82" -> CarSettingKind.Toggle(cloud.boolValue("lowBatTelWarning") == true)
        "19", "26", "32", "36", "37", "38", "42", "43", "44", "46", "49",
        "51", "53", "63", "68", "79", "80", "84", "90", "94", "2" ->
            CarSettingKind.Info("状态由车辆回读")
        "47" -> CarSettingKind.Entrance(
            if (cloud.boolValue("chargeWattSwitch") == true) "已开启" else "已关闭",
        )
        "48" -> {
            val cap = cloud.intValue("chargeUlVal")?.takeIf { it in 75..100 } ?: 90
            CarSettingKind.Entrance("$cap%")
        }
        "10" -> CarSettingKind.Entrance(
            if (cloud.boolValue("distanceOn") == true) "已开启" else "去设置",
        )
        "61" -> CarSettingKind.Entrance(
            cloud.intValue("highestSpeedLimit")
                ?.takeIf { it > 0 }
                ?.let { "$it km/h" }
                ?: "去设置",
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
