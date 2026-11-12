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
import kotlinx.coroutines.delay

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
    val rawJson: String = "",
    /** Loaded from the independent appointment endpoint, never from the flat DTO by name. */
    val autoHibernate: CarSettingKind.AutoHibernate? = null,
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
    suspend fun loadRemainingMileageEligibility(vin: String): RemainingMileageEligibility? = null
    suspend fun loadLocationSwitchAuthorization(vin: String): LocationSwitchAuthorization? = null
    suspend fun loadHibernateAuthorization(vin: String): HibernateAuthorization? = null
    suspend fun updateHibernate(vin: String, update: CloudCarSettingUpdate,
                                authorization: HibernateAuthorization): HibernateWriteReceipt = HibernateWriteReceipt(false)
    suspend fun updateHibernateMetadata(vin: String, authorization: HibernateAuthorization): Boolean = false
    suspend fun loadAutoHibernate(vin: String): CarSettingKind.AutoHibernate? = null
    suspend fun updateAutoHibernate(vin: String, target: CarSettingKind.AutoHibernate,
                                    authorization: HibernateAuthorization): Boolean = false
    suspend fun updateLocationSwitch(vin: String, update: CloudCarSettingUpdate,
                                     authorization: LocationSwitchAuthorization): LocationSwitchWriteReceipt =
        LocationSwitchWriteReceipt(false)
    suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean = false
    suspend fun checkStatus(configId: String): Boolean? = null
}

/** 已确认的云端设置写入字段。 */
enum class CloudCarSettingUpdatePath {
    BIKE_SETTING,
    PARAM_SETTING,
}

data class CloudCarSettingUpdate(
    val configId: String,
    val fields: Map<String, Any?>,
    val path: CloudCarSettingUpdatePath = CloudCarSettingUpdatePath.BIKE_SETTING,
)

private data class VehicleParamValue(
    val sort: Int,
    val byteCount: Int,
    val value: Any?,
    val dataType: Int,
)

private fun jsonLiteral(value: Any?): String = when (value) {
    null -> "null"
    is Boolean -> value.toString()
    is Number -> value.toString()
    else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

private object VehicleParamJson {
    fun single(value: Any?, dataType: Int = 1): String =
        listOf(VehicleParamValue(1, 1, value, dataType)).toJson()

    fun capacity(value: Any?): String = listOf(
        VehicleParamValue(1, 1, null, 1),
        VehicleParamValue(2, 1, value, 1),
        VehicleParamValue(3, 1, null, 4),
        VehicleParamValue(4, 2, null, 4),
    ).toJson()

    private fun List<VehicleParamValue>.toJson(): String =
        joinToString(",", "[", "]") { param ->
            "{" +
                "\"byteCount\":${param.byteCount}," +
                "\"dataType\":${param.dataType}," +
                "\"sort\":${param.sort}," +
                "\"value\":${jsonLiteral(param.value)}" +
                "}"
        }
}

/**
 * `updateBikeSetting` 与 `low-freq/common-cmd/param-setting` 的字段写入。
 *
 * 参数型命令保留 Smali `s20.f()` 的 `oper/id/value/valueJson` 结构；
 * 没有完整 valueJson 证据的项目不进入写入器。
 */
object CloudCarSettingUpdateBuilder {
    // Schema support is independent of completeness; build() still rejects unknown siblings.
    fun supports(configId: String, kind: CarSettingKind): Boolean =
        (configId == "77" && (kind is CarSettingKind.Toggle || kind is CarSettingKind.AutoHibernate)) ||
        (configId == "71" && kind is CarSettingKind.Toggle) ||
        BatterySettings.supports(configId, kind) || CloudSwitchSettings.supports(configId, kind) || TrackerSettings.supports(configId, kind) ||
            (configId == "10" && (kind is CarSettingKind.Toggle || kind is CarSettingKind.Slider)) ||
            (configId == "9" && kind is CarSettingKind.LowPowerInfo) ||
            (configId == "82" && kind is CarSettingKind.LowPowerPhone) ||
            build(configId, kind, vin = "vin") != null

    fun build(
        configId: String,
        kind: CarSettingKind,
        vin: String,
        geofence: GeofenceConfiguration? = null,
    ): CloudCarSettingUpdate? {
        if (configId == "10") {
            if (!GeofenceSettings.validTarget(kind) || geofence?.complete != true || geofence.vin != vin) return null
            return CloudCarSettingUpdate(configId, linkedMapOf(
                "_gateway" to "True", "vin" to vin,
                "distanceOn" to if ((kind as? CarSettingKind.Toggle)?.checked ?: geofence.enabled!!) "1" else "0",
                "distance" to ((kind as? CarSettingKind.Slider)?.value?.toInt() ?: geofence.radius!!).toString(),
                "centerCircleLon" to geofence.longitude, "centerCircleLat" to geofence.latitude,
            ))
        }
        if (configId in BatterySettings.ids && !BatterySettings.validTarget(configId, kind)) return null
        if (configId in CloudSwitchSettings.ids && !CloudSwitchSettings.validTarget(kind)) return null
        if (configId in TrackerSettings.ids && !TrackerSettings.validTarget(configId, kind)) return null
        if (configId == "77" && !HibernateSettings.validTarget(kind)) return null
        if (configId == "71" && (kind as? CarSettingKind.Toggle)?.valueKnown != true) return null
        val fields = linkedMapOf<String, Any?>("_gateway" to "True", "vin" to vin)
        val path: CloudCarSettingUpdatePath
        when (kind) {
            is CarSettingKind.LowPowerInfo -> {
                if (configId != "9" || kind.edit == null || kind.alarmOn == null ||
                    kind.alarmPower?.let { it !in 0..100 } != false
                ) return null
                path = CloudCarSettingUpdatePath.BIKE_SETTING
                fields["alarmOn"] = if (kind.alarmOn) "1" else "0"
                fields["alarmPower"] = kind.alarmPower.toString()
            }
            is CarSettingKind.LowPowerPhone -> {
                if (configId != "82" || kind.edit == null ||
                    !validLowPowerTimeRange(kind.startTime, kind.endTime)
                ) return null
                path = CloudCarSettingUpdatePath.BIKE_SETTING
                if (kind.edit == CarSettingKind.LowPowerPhoneEdit.SWITCH) {
                    val enabled = kind.enabled ?: return null
                    fields["lowBatTelWarning"] = if (enabled) 1 else 0
                } else if (!selectableLowPowerTime(kind.startTime) || !selectableLowPowerTime(kind.endTime)) {
                    return null
                }
                fields["lowBatWarningStartTime"] = kind.startTime
                fields["lowBatWarningEndTime"] = kind.endTime
            }
            is CarSettingKind.Toggle -> {
                val checkedValue = if (kind.checked) 1 else 0
                when (configId) {
                    "40" -> {
                        path = if (kind.checked) {
                            CloudCarSettingUpdatePath.BIKE_SETTING
                        } else {
                            CloudCarSettingUpdatePath.PARAM_SETTING
                        }
                        if (kind.checked) {
                            fields["colorEggSoundEffect"] = checkedValue.toString()
                        } else {
                            fields["oper"] = "SET_PARAM"
                            fields["id"] = "CLEAR_SOUND_EFFECT"
                            fields["value"] = 7
                            fields["valueJson"] = VehicleParamJson.single(7)
                        }
                    }
                    "47" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["id"] = "CHARGE_WATT_SWITCH"
                        fields["value"] = checkedValue
                    }
                    "60" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "BATT_BD_SETTING"
                        fields["value"] = checkedValue
                        fields["valueJson"] = VehicleParamJson.single(checkedValue)
                    }
                    "62" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["seatHeating"] = checkedValue
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "SEAT_HEATING"
                        fields["value"] = checkedValue
                        fields["valueJson"] = VehicleParamJson.single(checkedValue)
                    }
                    "76" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "POSITIONING_FUNC"
                        fields["value"] = checkedValue
                        fields["valueJson"] = VehicleParamJson.single(checkedValue)
                    }
                    "77" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "SLEEP_MODE_SWITCH"
                        // P4 negates the OLD switch state; our kind already holds the target.
                        // sleepMode=1 belongs only to the separate BLE+metadata branch.
                        fields["value"] = checkedValue
                        fields["valueJson"] = VehicleParamJson.single(checkedValue)
                    }
                    else -> {
                        val field = TOGGLE_FIELDS[configId] ?: return null
                        path = CloudCarSettingUpdatePath.BIKE_SETTING
                        fields[field] = if (configId in TOGGLE_INTEGER_FIELDS) {
                            checkedValue
                        } else {
                            checkedValue.toString()
                        }
                    }
                }
            }
            is CarSettingKind.Slider -> {
                val value = kind.value.toInt()
                when (configId) {
                    "47" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["id"] = "CHARGE_WATT_VAL"
                        fields["value"] = value
                    }
                    "48" -> {
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["id"] = "CHARGE_UL_VAL"
                        fields["value"] = value
                    }
                    "61" -> {
                        if (!kind.rangeKnown || !kind.valueKnown || !kind.value.isFinite() || kind.value != value.toFloat() ||
                            kind.value !in kind.range || (value != 0 && value !in 15..120)
                        ) return null
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["highestSpeedLimit"] = value
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "HIGHEST_SPEED_LIMIT"
                        fields["value"] = value
                        fields["valueJson"] = VehicleParamJson.single(value)
                    }
                    else -> {
                        val field = SLIDER_FIELDS[configId] ?: return null
                        path = CloudCarSettingUpdatePath.BIKE_SETTING
                        fields[field] = value.toString()
                    }
                }
            }
            is CarSettingKind.Choice -> {
                when (configId) {
                    "60" -> {
                        val value = BatteryCapacityOptions.valuesAh.getOrNull(kind.selectedIndex)
                            ?: return null
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "BATT_V_A_CAP_MODEL"
                        fields["value"] = value
                        fields["valueJson"] = VehicleParamJson.capacity(value)
                    }
                    "62" -> {
                        val value = kind.selectedIndex
                        path = CloudCarSettingUpdatePath.PARAM_SETTING
                        fields["seatHeatingLevel"] = value
                        fields["oper"] = "SET_PARAM"
                        fields["id"] = "SEAT_HEATING_LEVEL"
                        fields["value"] = value
                        fields["valueJson"] = VehicleParamJson.single(value)
                    }
                    else -> {
                        val field = CHOICE_FIELDS[configId] ?: return null
                        path = CloudCarSettingUpdatePath.BIKE_SETTING
                        fields[field.field] = (kind.selectedIndex + field.offset).toString()
                    }
                }
            }
            else -> return null
        }
        return CloudCarSettingUpdate(configId = configId, fields = fields, path = path)
    }

    /** smali `s20.f()` 与 `VehicleSettingViewModel.P4()` 能直接对应的开关。 */
    private val TOGGLE_FIELDS = mapOf(
        "71" to "bikeInfoSyncName",
        "16" to "dumpAlarm",
        "24" to "emergencyContact",
        "40" to "colorEggSoundEffect",
        "41" to "userDefinedSoundEffect",
        "52" to "bleUnlockOn",
        "75" to "tamperProofIotEnable",
        "77" to "sleepMode",
        "82" to "lowBatTelWarning",
        "91" to "sosEmergencyCall",
        "95" to "wxhlBind",
    )

    private val TOGGLE_INTEGER_FIELDS = setOf("71", "75", "82")

    private val SLIDER_FIELDS = emptyMap<String, String>()

    private data class ChoiceField(val field: String, val offset: Int)

    private val CHOICE_FIELDS = mapOf<String, ChoiceField>()
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
) : CarSettingsGateway, CustomSoundGateway {

    override suspend fun loadRemainingMileageEligibility(vin: String): RemainingMileageEligibility? {
        val normalizedVin = vin.trim()
        val token = tokenProvider().trim()
        if (normalizedVin.isBlank() || token.isBlank()) return null
        // jp6.h/i:247–557: cloud SOC -> cal2. The string "0" is only the
        // original request fallback, never a setting value or an eligibility result.
        val status = remainingMileageGet("bikeRealStatus/getVehRealStatus", mapOf("vin" to normalizedVin), token)
            ?: return null
        fun soc(key: String): String = textOrNumber(status, key)?.takeIf { it.isNotBlank() } ?: "0"
        val data = remainingMileageGet("vehiclebase/estimateMileage/cal2",
            mapOf("vin" to normalizedVin, "soc1" to soc("soc1"), "soc2" to soc("soc2")), token) ?: return null
        return RemainingMileageSettings.parse(normalizedVin, data)
    }

    private suspend fun remainingMileageGet(path: String, fields: Map<String, String>, token: String): String? {
        val signed = GatewaySigner.formBody(sortedJson(fields + ("_gateway" to "True")), timestampProvider())
        val response = transport.execute(HttpRequest(method = "GET", url = baseUrl + path + "?" + signed,
            headers = GatewayRequestHeaders.build(clientInfoProvider(), deviceIdProvider(), token))) ?: return null
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        if (JsonSupport.boolean(plain, "success") != true) return null
        return JsonSupport.objectField(plain, "data")
    }

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
        return CloudCarSettings(configs = configs, values = values, rawJson = data)
    }

    override suspend fun update(vin: String, update: CloudCarSettingUpdate): Boolean {
        // These flows retain their transaction receipts and require independent online guards.
        if (update.configId in setOf("76", "77")) return false
        if (update.configId == "75" && update != CloudCarSettingUpdateBuilder.build("75", CarSettingKind.Toggle(true), vin)) return false
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return false
        val plain = postFields(
            path = when (update.path) {
                CloudCarSettingUpdatePath.BIKE_SETTING -> UPDATE_PATH
                CloudCarSettingUpdatePath.PARAM_SETTING -> PARAM_UPDATE_PATH
            },
            fields = update.fields + ("vin" to vin),
            token = token,
        ) ?: return false
        if (update.configId == "75") return JsonSupport.topLevelScalarText(plain, "success") == "true" &&
            JsonSupport.topLevelScalarText(plain, "data") != "false"
        if (JsonSupport.boolean(plain, "success") == false) return false
        return JsonSupport.boolean(plain, "data") != false
    }

    override suspend fun loadLocationSwitchAuthorization(vin: String): LocationSwitchAuthorization? {
        val normalized = vin.trim()
        val token = tokenProvider().trim()
        if (normalized.isBlank() || token.isBlank()) return null
        // VehicleDataBean.isOnline -> VehicleStatusBean.onlineState, not BLE connectivity.
        val data = remainingMileageGet("bikeRealStatus/getVehRealStatus", mapOf("vin" to normalized), token) ?: return null
        return if (JsonSupport.topLevelScalarText(data, "onlineState") == "true") LocationSwitchAuthorization(normalized) else null
    }

    override suspend fun loadHibernateAuthorization(vin: String): HibernateAuthorization? {
        val normalized = vin.trim()
        val token = tokenProvider().trim()
        if (normalized.isBlank() || token.isBlank()) return null
        val data = remainingMileageGet("bikeRealStatus/getVehRealStatus", mapOf("vin" to normalized), token) ?: return null
        return if (JsonSupport.topLevelScalarText(data, "onlineState") == "true") HibernateAuthorization(normalized) else null
    }

    override suspend fun updateHibernate(vin: String, update: CloudCarSettingUpdate,
                                         authorization: HibernateAuthorization): HibernateWriteReceipt {
        val token = tokenProvider().trim()
        if (vin.isBlank() || token.isBlank() || authorization.vin != vin || !authorization.enterConfirmed ||
            update != CloudCarSettingUpdateBuilder.build("77", CarSettingKind.Toggle(true), vin)) return HibernateWriteReceipt(false)
        val plain = postFields(PARAM_UPDATE_PATH, update.fields + ("vin" to vin), token) ?: return HibernateWriteReceipt(false)
        if (JsonSupport.topLevelScalarText(plain, "success") != "true" ||
            JsonSupport.topLevelScalarText(plain, "data") == "false") return HibernateWriteReceipt(false)
        val id = JsonSupport.topLevelScalarText(plain, "data")?.trim()
            ?.takeIf { it.isNotBlank() && it !in setOf("true", "false") }
        return HibernateWriteReceipt(true, id)
    }

    override suspend fun updateHibernateMetadata(vin: String, authorization: HibernateAuthorization): Boolean {
        val token = tokenProvider().trim()
        if (vin.isBlank() || token.isBlank() || authorization.vin != vin || !authorization.enterConfirmed) return false
        val plain = postFields(UPDATE_PATH, HibernateSettings.metadataUpdate(vin).fields, token) ?: return false
        return JsonSupport.topLevelScalarText(plain, "success") == "true" &&
            JsonSupport.topLevelScalarText(plain, "data") != "false"
    }

    override suspend fun loadAutoHibernate(vin: String): CarSettingKind.AutoHibernate? {
        val token = tokenProvider().trim()
        if (vin.isBlank() || token.isBlank()) return null
        val data = remainingMileageGet(AutoHibernateSettings.GET_PATH, mapOf("vin" to vin), token) ?: return null
        return AutoHibernateSettings.parse(data)
    }

    override suspend fun updateAutoHibernate(vin: String, target: CarSettingKind.AutoHibernate,
                                            authorization: HibernateAuthorization): Boolean {
        val token = tokenProvider().trim()
        if (vin.isBlank() || token.isBlank() || authorization.vin != vin || !AutoHibernateSettings.validTarget(target)) return false
        val plain = postFields(AutoHibernateSettings.UPDATE_PATH, mapOf("_gateway" to "True", "vin" to vin,
            "autoSleepSwitch" to if (target.enabled == true) 1 else 0, "dayCount" to target.dayCount), token) ?: return false
        return JsonSupport.topLevelScalarText(plain, "success") == "true" && JsonSupport.topLevelScalarText(plain, "data") != "false"
    }

    override suspend fun updateLocationSwitch(vin: String, update: CloudCarSettingUpdate,
                                             authorization: LocationSwitchAuthorization): LocationSwitchWriteReceipt {
        val token = tokenProvider().trim()
        if (vin.isBlank() || token.isBlank() || authorization.vin != vin || update.configId != "76" ||
            update.path != CloudCarSettingUpdatePath.PARAM_SETTING) return LocationSwitchWriteReceipt(false)
        val checked = when (update.fields["value"]) { 1 -> true; 0 -> false; else -> return LocationSwitchWriteReceipt(false) }
        if ((!checked && !authorization.closeConfirmed) || update != CloudCarSettingUpdateBuilder.build("76", CarSettingKind.Toggle(checked), vin)) {
            return LocationSwitchWriteReceipt(false)
        }
        val plain = postFields(PARAM_UPDATE_PATH, update.fields + ("vin" to vin), token)
            ?: return LocationSwitchWriteReceipt(false)
        if (JsonSupport.topLevelScalarText(plain, "success") != "true") return LocationSwitchWriteReceipt(false)
        val id = JsonSupport.topLevelScalarText(plain, "data")?.trim()
            ?.takeIf { it.isNotBlank() && it !in setOf("true", "false") }
        return LocationSwitchWriteReceipt(true, id)
    }

    override suspend fun checkStatus(configId: String): Boolean? {
        val token = tokenProvider().trim()
        if (token.isBlank() || configId.isBlank()) return null
        val businessJson = sortedJson(mapOf("_gateway" to "True", "id" to configId))
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
        ) ?: return false
        if (response.status !in 200..299) return false
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
        if (JsonSupport.boolean(plain, "success") == false) return false
        // `MixRequest$checkSettingStatus$3` only reads `data.result`; missing
        // data/result means pending and must continue polling.
        val data = JsonSupport.objectField(plain, "data") ?: return null
        val resultText = JsonSupport.string(data, "result")
            ?: JsonSupport.numberText(data, "result")
            ?: return null
        return resultText.trim().toIntOrNull()?.let { it == 0 } ?: false
    }

    override suspend fun loadCustomSoundState(
        vin: String,
        modelId: Long,
    ): CustomSoundState? {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return null
        val resourcePlain = postSoundFields(
            path = "resource/queryResourceList",
            fields = mapOf(
                "_gateway" to "True",
                "modelId" to modelId.toString(),
                "vin" to vin,
            ),
            token = token,
        )
        val resources = resourcePlain
            ?.takeIf { JsonSupport.boolean(it, "success") != false }
            ?.let(::parseCustomSoundResources)
            .orEmpty()
        val bikePlain = postSoundFields(
            path = "resourceBike/queryResourceBikeList",
            fields = mapOf("_gateway" to "True", "vin" to vin),
            token = token,
        )
        val bikeItems = bikePlain
            ?.takeIf { JsonSupport.boolean(it, "success") != false }
            ?.let(::soundDataObjects)
            .orEmpty()
        val configPlain = postSoundFields(
            path = "vehiclebase/bike-model/selectConfigListByVin",
            fields = mapOf(
                "_gateway" to "True",
                "confIds" to "zdyyx001",
                "vin" to vin,
            ),
            token = token,
        )
        val countPlain = postSoundFields(
            path = "resourcesBike/queryCountSetResourceBike",
            fields = mapOf(
                "_gateway" to "True",
                "type" to "1",
                "vin" to vin,
            ),
            token = token,
        )
        if (resourcePlain == null && bikePlain == null && configPlain == null) return null
        val categoryData = soundCategories(configPlain, bikeItems)
        val count = countPlain?.let(::soundCount)
        val resourceIds = resources.mapTo(mutableSetOf()) { it.id }
        val selected = bikeItems.firstOrNull {
            (textOrNumber(it, "resourceId") ?: "").let(resourceIds::contains)
        } ?: bikeItems.firstOrNull {
            boolField(it, "isSelected") || intField(it, "resourceType") == 1
        }
        val selectedId = selected?.let { textOrNumber(it, "resourceId") }.orEmpty()
        val fields = buildSet {
            if (soundResponseHasData(resourcePlain)) add(CustomSoundData.RESOURCES)
            if (configPlain?.let { JsonSupport.boolean(it, "success") } == true && categoryData.parentId.isNotBlank()) add(CustomSoundData.CATEGORIES)
            if (soundResponseHasData(bikePlain)) {
                if (CustomSoundData.CATEGORIES in this) add(CustomSoundData.SELECTION)
                if (selected != null && (boolFieldOrNull(selected, "isSelected") != null || intField(selected, "soundEffectSendType") != null)) add(CustomSoundData.ENABLED)
            }
            if (countPlain?.let { JsonSupport.boolean(it, "success") } == true && count?.first != null && count.second != null) add(CustomSoundData.COUNT)
        }
        if (fields.isEmpty()) return null
        return CustomSoundState(
            enabled = selected?.let {
                boolField(it, "isSelected") ||
                    intField(it, "soundEffectSendType") == 1
            } ?: resources.any { it.isSelected },
            resources = if (resources.isEmpty()) {
                bikeItems.mapNotNull { item ->
                    val id = textOrNumber(item, "resourceId") ?: return@mapNotNull null
                    CustomSoundResource(
                        id = id,
                        name = JsonSupport.string(item, "resourceName")
                            .orEmpty()
                            .ifBlank { JsonSupport.string(item, "musicName").orEmpty() }
                            .ifBlank { "未命名音效" },
                        isSelected = boolField(item, "isSelected"),
                    )
                }.distinctBy { it.id }
            } else {
                resources
            },
            categories = categoryData.categories,
            weeklyRemaining = count?.first,
            weeklyLimit = count?.second,
            selectedResourceId = selectedId,
            parentConfId = categoryData.parentId
                .ifBlank { selected?.let { textOrNumber(it, "parentConfId") }.orEmpty() },
            parentConfName = categoryData.parentName.ifBlank { "自定义音效" },
            sonConfId = categoryData.categories.firstOrNull()?.sonConfId
                ?: selected?.let { textOrNumber(it, "sonConfId") }.orEmpty(),
            loadedFields = fields,
        )
    }

    override suspend fun setCustomSoundEnabled(vin: String, enabled: Boolean): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return false
        val plain = postSoundFields(
            path = "protocol/control-vehicle/user_defined_sound_effect",
            fields = mapOf(
                "_gateway" to "True",
                "type" to if (enabled) "1" else "0",
                "vin" to vin,
            ),
            token = token,
        ) ?: return false
        recordCustomSoundReceipt(accepted = JsonSupport.boolean(plain, "success"))
        if (JsonSupport.boolean(plain, "success") == false) return false
        return pollSoundResult(
            token = token,
            vin = vin,
            otaSendId = otaSendIdOf(plain),
        )
    }

    override suspend fun applyCustomSound(
        vin: String,
        state: CustomSoundState,
        category: CustomSoundCategory,
        resource: CustomSoundResource,
    ): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return false
        if (state.parentConfId.isBlank() || category.sonConfId.isBlank()) return false
        val plain = postSoundFields(
            path = "resourcesBike/setResourceBike",
            fields = mapOf(
                "_gateway" to "True",
                "parentConfId" to state.parentConfId,
                "parentConfName" to state.parentConfName,
                "resourceId" to resource.id,
                "sonConfId" to category.sonConfId,
                "sonConfName" to resource.name,
                "type" to "1",
                "vin" to vin,
            ),
            token = token,
        ) ?: return false
        recordCustomSoundReceipt(accepted = JsonSupport.boolean(plain, "success"))
        if (JsonSupport.boolean(plain, "success") == false) return false
        return pollSoundResult(
            token = token,
            vin = vin,
            otaSendId = otaSendIdOf(plain),
        )
    }

    override suspend fun deleteCustomSound(vin: String, resourceId: String): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank() || resourceId.isBlank()) return false
        val plain = postSoundFields(
            path = "resource/deleteUserResource",
            fields = mapOf(
                "_gateway" to "True",
                "resourceId" to resourceId,
                "vin" to vin,
            ),
            token = token,
        ) ?: return false
        return JsonSupport.boolean(plain, "success") != false
    }

    override suspend fun renameCustomSound(
        vin: String,
        resourceId: String,
        name: String,
    ): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank() || resourceId.isBlank() || name.isBlank()) {
            return false
        }
        val plain = postSoundFields(
            path = "resource/renameUserResource",
            fields = mapOf(
                "_gateway" to "True",
                "resourceId" to resourceId,
                "resourceName" to name,
                "vin" to vin,
            ),
            token = token,
        ) ?: return false
        return JsonSupport.boolean(plain, "success") != false
    }

    override suspend fun addCustomSound(
        vin: String,
        modelId: Long,
        name: String,
        resourceUrl: String,
    ): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank() || name.isBlank() || resourceUrl.isBlank()) {
            return false
        }
        val plain = postSoundFields(
            path = "resource/addUserResource",
            fields = mapOf(
                "_gateway" to "True",
                "modelId" to modelId.toString(),
                "resourceName" to name,
                "resourceUrl" to resourceUrl,
                "vin" to vin,
            ),
            token = token,
        ) ?: return false
        return JsonSupport.boolean(plain, "success") != false
    }

    private data class SoundCategoryData(
        val parentId: String = "",
        val parentName: String = "",
        val categories: List<CustomSoundCategory> = emptyList(),
    )

    private fun soundResponseHasData(plain: String?): Boolean = plain != null &&
        JsonSupport.boolean(plain, "success") == true &&
        (JsonSupport.objectField(plain, "data") != null || Regex("\"data\"\\s*:\\s*\\[").containsMatchIn(plain))

    private fun soundCategories(
        plain: String?,
        bikeItems: List<String>,
    ): SoundCategoryData {
        val configs = plain
            ?.takeIf { JsonSupport.boolean(it, "success") != false }
            ?.let { value ->
                val direct = JsonSupport.arrayObjects(value, "data")
                if (direct.isNotEmpty()) {
                    direct
                } else {
                    val data = JsonSupport.objectField(value, "data")
                    if (data != null && JsonSupport.arrayObjects(data, "sonConfigs").isNotEmpty()) {
                        listOf(data)
                    } else {
                        data?.let {
                            JsonSupport.arrayObjects(it, "list")
                                .ifEmpty { JsonSupport.arrayObjects(it, "records") }
                        }.orEmpty()
                    }
                }
            }
            .orEmpty()
        val parent = configs.firstOrNull {
            textOrNumber(it, "confId") == "zdyyx001"
        } ?: configs.firstOrNull()
        if (parent == null) return SoundCategoryData()
        val parentId = textOrNumber(parent, "confId").orEmpty()
        val parentName = JsonSupport.string(parent, "confName")
            .orEmpty()
            .ifBlank { "自定义音效" }
        val bikeBySon = bikeItems.groupBy {
            textOrNumber(it, "sonConfId").orEmpty()
        }
        val categories = JsonSupport.arrayObjects(parent, "sonConfigs").mapNotNull { son ->
            val sonConfId = textOrNumber(son, "confId") ?: return@mapNotNull null
            val bike = bikeBySon[sonConfId]
                ?.firstOrNull { boolField(it, "isSelected") }
                ?: bikeBySon[sonConfId]?.firstOrNull()
            CustomSoundCategory(
                sonConfId = sonConfId,
                name = JsonSupport.string(son, "confName")
                    .orEmpty()
                    .ifBlank { "音效" },
                selectedResourceId = bike?.let { textOrNumber(it, "resourceId") }.orEmpty(),
                selectedName = bike?.let {
                    JsonSupport.string(it, "resourceName")
                        .orEmpty()
                        .ifBlank { JsonSupport.string(it, "musicName").orEmpty() }
                }.orEmpty(),
                sort = intField(son, "sort") ?: 0,
            )
        }.sortedBy { it.sort }
        return SoundCategoryData(
            parentId = parentId,
            parentName = parentName,
            categories = categories,
        )
    }

    private fun soundCount(plain: String): Pair<Int?, Int?> {
        val data = JsonSupport.objectField(plain, "data") ?: plain
        return intField(data, "count") to intField(data, "total")
    }

    private fun parseCustomSoundResources(plain: String): List<CustomSoundResource> =
        soundDataObjects(plain).mapNotNull { item ->
            val id = textOrNumber(item, "id")
                ?: textOrNumber(item, "resourceId")
                ?: return@mapNotNull null
            CustomSoundResource(
                id = id,
                name = JsonSupport.string(item, "resourceName")
                    .orEmpty()
                    .ifBlank { "未命名音效" },
                url = JsonSupport.string(item, "resourceUrl").orEmpty(),
                isSelected = boolField(item, "isSelected") || boolField(item, "isUsed"),
                source = intField(item, "source") ?: 0,
            )
        }

    /** `data` 既可能是数组，也可能包在 `list/records` 中。 */
    private fun soundDataObjects(plain: String): List<String> {
        val direct = JsonSupport.arrayObjects(plain, "data")
        if (direct.isNotEmpty()) return direct
        val data = JsonSupport.objectField(plain, "data") ?: return emptyList()
        return JsonSupport.arrayObjects(data, "list")
            .ifEmpty { JsonSupport.arrayObjects(data, "records") }
            .ifEmpty { JsonSupport.arrayObjects(data, "rows") }
    }

    private fun otaSendIdOf(plain: String): String {
        val data = JsonSupport.objectField(plain, "data")
        return listOfNotNull(data, plain)
            .firstNotNullOfOrNull { textOrNumber(it, "otaSendId") }
            .orEmpty()
    }

    private suspend fun pollSoundResult(
        token: String,
        vin: String,
        otaSendId: String,
    ): Boolean {
        if (otaSendId.isBlank()) return true
        repeat(15) { attempt ->
            if (attempt > 0) delay(1_000L)
            val plain = postSoundFields(
                path = "resourcesBike/queryResultSetResourceBike",
                fields = mapOf(
                    "_gateway" to "True",
                    "otaSendId" to otaSendId,
                    "vin" to vin,
                ),
                token = token,
            ) ?: return@repeat
            if (JsonSupport.boolean(plain, "success") == false) return false
            val data = JsonSupport.objectField(plain, "data") ?: plain
            val status = intField(data, "status")
            recordCustomSoundReceipt(status = status)
            when (status) {
                0 -> return true
                3 -> return false
                null -> if (attempt >= 2) return true
                else -> Unit
            }
        }
        return false
    }

    // Limit lifecycle checks to custom sound, without changing other settings or transport.
    private suspend fun postSoundFields(path: String, fields: Map<String, Any?>, token: String): String? {
        checkCustomSoundRequest()
        return postFields(path, fields, token).also { checkCustomSoundRequest() }
    }

    private var timestampOffsetMs: Long = 0L

    private fun effectiveTimestamp(): Long = timestampProvider() + timestampOffsetMs

    private suspend fun postFields(
        path: String,
        fields: Map<String, Any?>,
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
        JsonSupport.topLevelScalarText(json, key)

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

    private fun sortedJson(fields: Map<String, Any?>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            if (index > 0) append(',')
            append('"').append(key).append("\":")
                .append(jsonLiteral(fields.getValue(key)))
        }
        append('}')
    }

    companion object {
        const val PATH = "vehiclebase/bike-setting/selectBikeSettingByVin"
        const val UPDATE_PATH = "vehiclebase/bike-setting/updateBikeSetting"
        const val CHECK_STATUS_PATH = "common-cmd/low-freq/info/param"
        const val PARAM_UPDATE_PATH = "low-freq/common-cmd/param-setting"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"

        private val VALUE_KEYS = listOf(
            "alarmOn", "alarmPower", "alarmSoundEnable", "bikeInfoSyncName",
            "bikeModelTopSpeedMax", "bikeModelTopSpeedMin", "bikeSpeedMax",
            "bikeSpeedMaxDefaultMax", "bikeSpeedMaxDefaultMin",
            "bleApproachPowerOnDistance", "bleApproachPowerOnEnable",
            "bleLeavePowerOffDistance", "bleLeavePowerOffEnable", "bleUnlockOn",
            "brightnessAdjustValue", "capacity", "chargeUlVal", "chargeWattSwitch",
            "chargeWattVal",
            "colorEggSoundEffect", "cutPowerOn", "distance", "distanceOn", "centerCircleLon", "centerCircleLat",
            "dumpAlarm", "emergencyContact", "highestSpeedLimit", "lowBatTelWarning",
            "lowBatWarningEndTime", "lowBatWarningStartTime", "modeOn", "nfcOn",
            "offlineLock", "offlineLockValue", "onOffSound", "positioningFunc",
            "rechargeAppointment", "rechargeAppointmentBeginTime",
            "rechargeAppointmentEndTime", "resistanceOn", "sendBikeRecDynaOn",
            "shakeOn", "shutDownTime", "sleepMode", "sosEmergencyCall",
            "tamperProofIotEnable",
            "userDefinedSoundEffect", "vehiclePowerDownStatus", "voltage", "wxhlBind",
            "bleUnlockOn", "seatHeating", "seatHeatingLevel",
        )
    }
}

/** 将云端清单转换为现有设置控件模型。 */
object CloudCarSettingMapper {
    /** Explicit response fields only; item defaults never establish a configuration value. */
    internal fun configuredValue(cloud: CloudCarSettings, item: CarSettingItem): CarSettingKind? {
        if (item.id == AutoHibernateSettings.ID && item.kind is CarSettingKind.AutoHibernate) return cloud.autoHibernate
        if (item.configId == "71") return cloud.boolValue("bikeInfoSyncName")
            ?.let { CarSettingKind.Toggle(checked = it, valueKnown = true) }
        // DTO declarations alone do not prove these pages use the similarly named
        // cloud fields. Their current value is established by the verified TTP path.
        if (item.configId in SecondPhaseSettings.ids || item.configId in TimerSettings.ids) return null
        if (item.id == "cloud:52:distance") return null
        when (item.kind) {
            is CarSettingKind.LowPowerInfo -> {
                val alarmOn = cloud.boolValue("alarmOn")
                val alarmPower = cloud.values["alarmPower"]?.trim()?.toIntOrNull()
                    ?.takeIf { it in 0..100 }
                return if (alarmOn != null || alarmPower != null) {
                    CarSettingKind.LowPowerInfo(alarmOn, alarmPower)
                } else null
            }
            is CarSettingKind.LowPowerPhone -> {
                val enabled = cloud.boolValue("lowBatTelWarning")
                val start = cloud.values["lowBatWarningStartTime"]?.trim()
                    ?.takeIf { it.isValidLowPowerTime() }
                val end = cloud.values["lowBatWarningEndTime"]?.trim()
                    ?.takeIf { it.isValidLowPowerTime() }
                return if (enabled != null || start != null || end != null) {
                    CarSettingKind.LowPowerPhone(enabled, start, end)
                } else null
            }
            else -> Unit
        }
        val slider = item.kind as? CarSettingKind.Slider
        if (slider != null) {
            val key = when (item.id) {
                "cloud:10:distance" -> "distance"
                else -> when (item.configId) {
                    "4", "58" -> "bikeSpeedMax"
                    "47" -> "chargeWattVal"
                    "48" -> "chargeUlVal"
                    "61" -> "highestSpeedLimit"
                    else -> return null
                }
            }
            val value = cloud.values[key]?.trim()?.toIntOrNull()?.toFloat() ?: return null
            if (value !in slider.range ||
                (slider.minimumNonZero != null && value != 0f && value < slider.minimumNonZero)
            ) return null
            return slider.copy(value = value, valueKnown = true)
        }
        val choice = item.kind as? CarSettingKind.Choice
        if (choice != null) {
            val index = when (item.id) {
                "cloud:60:level" -> BatteryCapacityOptions.valuesAh.indexOf(
                    cloud.values["capacity"]?.trim()?.toIntOrNull(),
                )
                "cloud:62:level" -> cloud.values["seatHeatingLevel"]?.trim()?.toIntOrNull() ?: return null
                else -> return null
            }
            return index.takeIf { it in choice.options.indices }
                ?.let { choice.copy(selectedIndex = it, valueKnown = true) }
        }
        val toggle = item.kind as? CarSettingKind.Toggle ?: return null
        val key = when (item.id) {
            "cloud:47:enable" -> "chargeWattSwitch"
            else -> when (item.configId) {
                "9" -> "alarmOn"
                "10" -> "distanceOn"
                "16" -> "dumpAlarm"
                "24" -> "emergencyContact"
                "40" -> "colorEggSoundEffect"
                "52" -> "bleUnlockOn"
                "62" -> "seatHeating"
                "76" -> "positioningFunc"
                "77" -> "sleepMode"
                "82" -> "lowBatTelWarning"
                "91" -> "sosEmergencyCall"
                "95" -> "wxhlBind"
                else -> return null
            }
        }
        return cloud.boolValue(key)?.let { toggle.copy(checked = it, valueKnown = true) }
    }

    /** 设置目录中的 confId 回退显示名；云端返回 confName 时始终优先使用云端值。 */
    fun titleForConfigId(configId: String): String? = TITLES[configId.trim()]

    fun toItems(cloud: CloudCarSettings, vin: String = ""): List<CarSettingItem> {
        val items = cloud.configs.mapNotNull { config ->
            val confId = config.confId.trim()
            if (confId.isBlank()) return@mapNotNull null
            // 微信互联当前无法可靠控制，先临时隐藏，保留映射以便后续恢复。
            if (confId == "95") return@mapNotNull null
            // confId 1 在原 App 中由 OneKeyOpenActivity 与 OneKeyDistanceActivity
            // 共用，下面拆成语义稳定的一键开机开关和距离档位两项。
            if (confId == "1") return@mapNotNull null
            // X1 002/003 各自包含开关与档位，下面拆成稳定条目；001 是独立单位选择。
            if (confId == "002" || confId == "003") return@mapNotNull null
            // RadarActivity obtains each sub-control's visibility and permission from its own confId.
            if (confId in RadarSettings.configIds) return@mapNotNull null
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
                writable = config.isAuth != false &&
                    confId !in BLUETOOTH_KEY_IDS && (
                    (confId == "71" && cloud.boolValue("bikeInfoSyncName") != null) ||
                        VehicleSettingCommands.supports(confId, kind) ||
                        CloudCarSettingUpdateBuilder.supports(confId, kind)
                    ),
                sort = config.sort.toIntOrNull() ?: 0,
                geofenceConfiguration = if (confId == "10") GeofenceSettings.snapshot(cloud, vin) else null,
            )
        }.toMutableList()
        // ChargingPowerActivity 是“开关 + 瓦数滑杆”的二级页；云端只给一个
        // confId 47，这里拆成两个语义项，开关走 0xC9，滑杆走 0xCA。
        cloud.configs.firstOrNull { it.confId.trim() == "1" }?.let { config ->
            val classification = classificationOf("1", config)
            val group = groupOf(config, classification)
            val sort = config.sort.toIntOrNull() ?: 0
            items += CarSettingItem(
                id = "cloud:1:enable",
                configId = "1",
                title = "一键开机",
                summary = "开启后可在蓝牙感应范围内使用一键开机",
                classification = classification,
                group = group,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(false, valueKnown = false),
                writable = config.isAuth != false,
                sort = sort,
            )
            items += CarSettingItem(
                id = "cloud:1:distance",
                configId = "1",
                title = "开机距离档位",
                summary = "原 App 使用 0..7 档，不是米数",
                classification = classification,
                group = group,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(
                    value = 0f,
                    range = 0f..7f,
                    steps = 6,
                    unit = " 档",
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = sort + 1,
            )
        }
        for (x1Id in listOf("002", "003")) {
            cloud.configs.firstOrNull { it.confId.trim() == x1Id }?.let { config ->
                val classification = classificationOf(x1Id, config)
                val group = groupOf(config, classification)
                val sort = config.sort.toIntOrNull() ?: 0
                val levelTitle = if (x1Id == "002") "感应距离" else "制动回收"
                items += CarSettingItem(
                    id = "cloud:$x1Id:enable",
                    configId = x1Id,
                    title = titleOf(config),
                    summary = summaryOf(config, titleOf(config)),
                    classification = classification,
                    group = group,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false, valueKnown = false),
                    writable = config.isAuth != false,
                    sort = sort,
                )
                items += CarSettingItem(
                    id = "cloud:$x1Id:level",
                    configId = x1Id,
                    title = levelTitle,
                    summary = "原 App 提供 0%、50%、100% 三档",
                    classification = classification,
                    group = group,
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Slider(
                        value = 0f,
                        range = 0f..100f,
                        steps = 1,
                        unit = "%",
                        valueKnown = false,
                    ),
                    writable = config.isAuth != false,
                    sort = sort + 1,
                )
            }
        }
        cloud.configs.firstOrNull { it.confId.trim() == "47" }?.let { config ->
            val classification = CarSettingClassification.BATTERY
            val enabled = cloud.boolValue("chargeWattSwitch")
            items += CarSettingItem(
                id = "cloud:47:enable",
                configId = "47",
                title = "充电功率自定义",
                summary = "开启后可设置充电功率上限",
                classification = classification,
                group = groupOf(config, classification),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(enabled == true, valueKnown = enabled != null),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) - 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "73" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:73:config",
                configId = "73",
                title = "氛围灯模式与颜色",
                summary = "模式、运行方向和颜色由原 App 的 ATMOSPHERE_CONFIG 一次性下发",
                classification = classificationOf("73", config),
                group = groupOf(config, classificationOf("73", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Atmosphere(),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "60" }?.let { config ->
            val classification = CarSettingClassification.BATTERY
            val group = groupOf(config, classification)
            val sort = config.sort.toIntOrNull() ?: 0
            val capacityIndex = BatteryCapacityOptions.valuesAh
                .indexOf(cloud.values["capacity"]?.trim()?.toIntOrNull())
                .takeIf { it >= 0 }
                ?: -1
            items += CarSettingItem(
                id = "cloud:60:enable",
                configId = "60",
                title = "电池容量开关",
                summary = "开启后可选择车辆电池容量；关闭后容量档位不可编辑",
                classification = classification,
                group = group,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Toggle(false, valueKnown = false),
                writable = config.isAuth != false,
                sort = sort + 1,
            )
            items += CarSettingItem(
                id = "cloud:60:level",
                configId = "60",
                title = "电池容量",
                summary = "选择与车辆实际电池容量一致的档位",
                classification = classification,
                group = group,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(
                    selectedIndex = capacityIndex,
                    options = BatteryCapacityOptions.labels,
                    valueKnown = capacityIndex >= 0,
                ),
                writable = config.isAuth != false,
                sort = sort + 2,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "10" }?.let { config ->
            val distance = cloud.values["distance"]?.trim()?.toIntOrNull()?.takeIf { it in 200..2_000 }
            items += CarSettingItem(
                id = "cloud:10:distance",
                configId = "10",
                title = "电子围栏半径",
                summary = "车辆设防后以定位点为圆心的安防距离",
                classification = classificationOf("10", config),
                group = groupOf(config, classificationOf("10", config)),
                channel = CarSettingChannel.IOT,
                kind = CarSettingKind.Slider(
                    value = distance?.toFloat() ?: 200f,
                    range = 200f..2_000f,
                    steps = 1799,
                    unit = " m",
                    valueKnown = distance != null,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
                geofenceConfiguration = GeofenceSettings.snapshot(cloud, vin),
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "52" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:52:distance",
                configId = "52",
                title = "感应解锁距离",
                summary = "进入蓝牙感应范围后按一键开机键即可开关机",
                classification = classificationOf("52", config),
                group = groupOf(config, classificationOf("52", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(
                    value = 3f,
                    range = 1f..12f,
                    steps = 10,
                    unit = " m",
                    valueKnown = false,
                ),
                writable = false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "62" }?.let { config ->
            val level = cloud.values["seatHeatingLevel"]?.trim()?.toIntOrNull()?.takeIf { it in 0..2 }
            items += CarSettingItem(
                id = "cloud:62:level",
                configId = "62",
                title = "座椅加热档位",
                summary = "选择座椅加热强度",
                classification = classificationOf("62", config),
                group = groupOf(config, classificationOf("62", config)),
                channel = CarSettingChannel.IOT,
                kind = CarSettingKind.Choice(
                    selectedIndex = level ?: -1,
                    options = BatterySettings.heatOptions,
                    valueKnown = level != null,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "15" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:15:time",
                configId = "15",
                title = "离车定时关机时间",
                summary = "车辆静止超过设定时间后自动关机；可设置 2..20 分钟",
                classification = classificationOf("15", config),
                group = groupOf(config, classificationOf("15", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(
                    value = 2f,
                    range = 2f..20f,
                    steps = 17,
                    unit = " min",
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        for (autoLockId in listOf("7", "39")) {
            cloud.configs.firstOrNull { it.confId.trim() == autoLockId }?.let { config ->
                items += CarSettingItem(
                    id = "cloud:$autoLockId:time",
                    configId = autoLockId,
                    title = "自动锁定时间",
                    summary = "车辆离开后等待多长时间自动落锁",
                    classification = classificationOf(autoLockId, config),
                    group = groupOf(config, classificationOf(autoLockId, config)),
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Choice(
                        selectedIndex = 0,
                        options = SecondPhaseSettings.timeOptions,
                        valueKnown = false,
                    ),
                    writable = config.isAuth != false,
                    sort = (config.sort.toIntOrNull() ?: 0) + 1,
                )
            }
        }
        cloud.configs.firstOrNull { it.confId.trim() == "11" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:11:distance",
                configId = "11",
                title = "自动开关机感应距离",
                summary = "选择蓝牙接近车辆时的感应距离",
                classification = classificationOf("11", config),
                group = groupOf(config, classificationOf("11", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(
                    selectedIndex = 0,
                    options = SecondPhaseSettings.distanceOptions,
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "59" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:59:time",
                configId = "59",
                title = "定时关机时间",
                summary = "车辆静止达到设定时间后自动关机",
                classification = classificationOf("59", config),
                group = groupOf(config, classificationOf("59", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(
                    selectedIndex = -1,
                    options = TimerSettings.shutdownOptions,
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "1005" }?.let { config ->
            listOf(
                "short" to "短按功能",
                "long" to "长按功能",
            ).forEachIndexed { index, (key, title) ->
                items += CarSettingItem(
                    id = "cloud:1005:$key",
                    configId = "1005",
                    title = title,
                    summary = "选择车把自定义按键对应的功能",
                    classification = classificationOf("1005", config),
                    group = groupOf(config, classificationOf("1005", config)),
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Choice(
                        selectedIndex = KEY_FUNCTION_OPTIONS.lastIndex,
                        options = KEY_FUNCTION_OPTIONS,
                        valueKnown = false,
                    ),
                    writable = config.isAuth != false,
                    sort = (config.sort.toIntOrNull() ?: 0) + index + 1,
                )
            }
        }
        // 原 App 的 RideControlRegistry 将 1005 作为本地固定入口；
        // 部分车型的 bikeConfigVos 不下发它，仍应展示短按/长按设置。
        if (cloud.configs.none { it.confId.trim() == "1005" }) {
            val classification = CarSettingClassification.RIDE_CONTROL
            val group = CarSettingGroup("1002", classification.label, 1002)
            items += CarSettingItem(
                id = "local:1005",
                configId = "1005",
                title = "自定义按键",
                summary = "分别设置车把按键的短按和长按功能",
                classification = classification,
                group = group,
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Info("进入设置"),
                writable = false,
                sort = 95,
            )
            listOf("short" to "短按功能", "long" to "长按功能")
                .forEachIndexed { index, (key, title) ->
                    items += CarSettingItem(
                        id = "cloud:1005:$key",
                        configId = "1005",
                        title = title,
                        summary = "选择车把自定义按键对应的功能",
                        classification = classification,
                        group = group,
                        channel = CarSettingChannel.BLE,
                        kind = CarSettingKind.Choice(
                            selectedIndex = KEY_FUNCTION_OPTIONS.lastIndex,
                            options = KEY_FUNCTION_OPTIONS,
                            valueKnown = false,
                        ),
                        writable = true,
                        sort = 96 + index,
                    )
                }
        }
        cloud.configs.firstOrNull { it.confId.trim() == "21" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:21:time",
                configId = "21",
                title = "伴我回家灯时间",
                summary = "关闭车灯后继续照明的时间档位",
                classification = classificationOf("21", config),
                group = groupOf(config, classificationOf("21", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(
                    selectedIndex = 0,
                    options = TimerSettings.homeOptions,
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "81" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:81:time",
                configId = "81",
                title = "延时大灯时间",
                summary = "车辆关机后大灯继续照明的时间档位",
                classification = classificationOf("81", config),
                group = groupOf(config, classificationOf("81", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Choice(
                    selectedIndex = 0,
                    options = TimerSettings.delayOptions,
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "86" }?.let { config ->
            items += CarSettingItem(
                id = "cloud:86:value",
                configId = "86",
                title = "仪表亮度",
                summary = "手动调节仪表屏幕亮度；需同时开启亮度调节开关",
                classification = classificationOf("86", config),
                group = groupOf(config, classificationOf("86", config)),
                channel = CarSettingChannel.BLE,
                kind = CarSettingKind.Slider(
                    value = 0f,
                    range = 0f..100f,
                    steps = 99,
                    unit = "%",
                    valueKnown = false,
                ),
                writable = config.isAuth != false,
                sort = (config.sort.toIntOrNull() ?: 0) + 1,
            )
        }
        cloud.configs.firstOrNull { it.confId.trim() == "33" }?.let { config ->
            val classification = classificationOf("33", config)
            val base = CarSettingItem(
                id = "cloud:33:mode", configId = "33", title = "模拟声浪音效",
                summary = "车辆回读（只读）", classification = classification, group = groupOf(config, classification),
                channel = CarSettingChannel.BLE, writable = false, sort = (config.sort.toIntOrNull() ?: 0) + 1,
                kind = CarSettingKind.Choice(-1, SimulatedSoundSettings.modes, valueKnown = false),
            )
            items += base
            items += base.copy(id = "cloud:33:volume", title = "模拟声浪音量", sort = base.sort + 1,
                kind = CarSettingKind.Slider(1f, 1f..16f, steps = 14, valueKnown = false))
        }
        cloud.configs.firstOrNull { it.confId.trim() == "77" }?.let { config ->
            val classification = classificationOf("77", config)
            items += CarSettingItem(AutoHibernateSettings.ID, "自动休眠", "独立云端排程；车辆执行仍需验证",
                classification, group = groupOf(config, classification), channel = CarSettingChannel.IOT,
                kind = CarSettingKind.AutoHibernate(), writable = config.isAuth != false,
                configId = "77", sort = (config.sort.toIntOrNull() ?: 0) + 1)
        }
        for (pushAssistId in listOf("64", "89")) {
            cloud.configs.firstOrNull { it.confId.trim() == pushAssistId }?.let { config ->
                items += CarSettingItem(
                    id = "cloud:$pushAssistId:adaptive",
                    configId = pushAssistId,
                    title = "自适应推行",
                    summary = "勾选后无需拧动转把，推行约 5 秒自动进入推行模式",
                    classification = classificationOf(pushAssistId, config),
                    group = groupOf(config, classificationOf(pushAssistId, config)),
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false, valueKnown = false),
                    writable = config.isAuth != false,
                    sort = (config.sort.toIntOrNull() ?: 0) + 1,
                )
                items += CarSettingItem(
                    id = "cloud:$pushAssistId:level",
                    configId = pushAssistId,
                    title = "推行速度",
                    summary = "设置辅助推行时使用的速度档位",
                    classification = classificationOf(pushAssistId, config),
                    group = groupOf(config, classificationOf(pushAssistId, config)),
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Slider(
                        value = 1f,
                        range = 0f..2f,
                        steps = 1,
                        unit = "",
                        valueKnown = false,
                    ),
                    writable = config.isAuth != false,
                    sort = (config.sort.toIntOrNull() ?: 0) + 2,
                )
            }
        }
        RadarSettings.definitions.forEach { definition ->
            cloud.configs.firstOrNull { it.confId.trim() == definition.configId }?.let { config ->
                val classification = classificationOf(definition.configId, config)
                items += CarSettingItem(
                    id = definition.itemId,
                    configId = definition.configId,
                    title = definition.title,
                    summary = definition.summary,
                    classification = classification,
                    group = groupOf(config, classification),
                    channel = CarSettingChannel.BLE,
                    kind = CarSettingKind.Toggle(false, valueKnown = false),
                    writable = config.isAuth != false,
                    sort = (config.sort.toIntOrNull() ?: 0) + 1,
                )
            }
        }
        return items.sortedWith(compareBy({ it.group.sort }, { it.sort }, { it.title }))
    }

    /**
     * 本地功能目录减去本次云端 `bikeConfigVos`，生成云端未下发功能。
     *
     * 只有已有确认命令映射的非蓝牙车钥匙功能会标记为可写；其余保持只读，
     * 避免因为云端没有下发就向车辆发送不确定命令。
     */
    fun toMissingCloudItems(
        cloud: CloudCarSettings,
    ): List<CarSettingItem> {
        val issuedConfigIds = cloud.configs
            .mapTo(mutableSetOf()) { it.confId.trim() }
        return TITLES
            .asSequence()
            .filter { (configId) -> configId.all(Char::isDigit) }
            .filterNot { (configId) -> configId in issuedConfigIds }
            .flatMap { (configId, title) ->
                if (configId in setOf("001", "002", "003")) {
                    val classification = classificationOf(configId)
                    val group = CarSettingGroup(
                        id = "missing:${classification.name}",
                        title = classification.label,
                        sort = classification.ordinal,
                    )
                    val items = mutableListOf(
                        CarSettingItem(
                            id = "missing:$configId",
                            configId = configId,
                            title = title,
                            summary = "云端本次未下发；X1 配置保持只读，避免写入未授权车辆",
                            classification = classification,
                            group = group,
                            channel = CarSettingChannel.BLE,
                            kind = kindOf(configId, cloud),
                            writable = false,
                            sort = configId.trimStart('0').toIntOrNull() ?: Int.MAX_VALUE,
                        ),
                    )
                    if (configId != "001") {
                        items += CarSettingItem(
                            id = "cloud:$configId:level",
                            configId = configId,
                            title = if (configId == "002") "感应距离档位" else "制动回收档位",
                            summary = "云端本次未下发；档位保持只读",
                            classification = classification,
                            group = group,
                            channel = CarSettingChannel.BLE,
                            kind = CarSettingKind.Slider(
                                value = if (configId == "002") 0f else 1f,
                                range = if (configId == "002") 0f..2f else 1f..3f,
                                steps = 1,
                                unit = " 档",
                                valueKnown = false,
                            ),
                            writable = false,
                            sort = 900,
                        )
                    }
                    return@flatMap items.asSequence()
                }
                if (configId == "60") {
                    val classification = CarSettingClassification.BATTERY
                    val group = CarSettingGroup(
                        id = "missing:${classification.name}",
                        title = classification.label,
                        sort = classification.ordinal,
                    )
                    val capacityIndex = BatteryCapacityOptions.valuesAh
                        .indexOf(cloud.values["capacity"]?.trim()?.toIntOrNull())
                        .takeIf { it >= 0 }
                        ?: -1
                    return@flatMap sequenceOf(
                        CarSettingItem(
                            id = "cloud:60:enable",
                            configId = "60",
                            title = "电池容量开关",
                            summary = "云端本次未下发；可实验性设置电池容量功能",
                            classification = classification,
                            group = group,
                            channel = CarSettingChannel.BLE,
                            kind = CarSettingKind.Toggle(false, valueKnown = false),
                            writable = true,
                            sort = 60,
                        ),
                        CarSettingItem(
                            id = "cloud:60:level",
                            configId = "60",
                            title = "电池容量",
                            summary = "云端本次未下发；可选 13/15/22/25/32/38Ah",
                            classification = classification,
                            group = group,
                            channel = CarSettingChannel.BLE,
                            kind = CarSettingKind.Choice(
                                selectedIndex = capacityIndex,
                                options = BatteryCapacityOptions.labels,
                                valueKnown = capacityIndex >= 0,
                            ),
                            writable = true,
                            sort = 61,
                        ),
                    )
                }
                val classification = classificationOf(configId)
                val kind = kindOf(configId, cloud)
                val writable = configId !in BLUETOOTH_KEY_IDS &&
                    (
                        VehicleSettingCommands.supports(configId, kind) ||
                            CloudCarSettingUpdateBuilder.supports(configId, kind)
                        )
                sequenceOf(
                    CarSettingItem(
                        id = "missing:$configId",
                        configId = configId,
                        title = title,
                        summary = if (writable) {
                            "云端本次未下发；可实验性设置，车辆可能不支持该功能"
                        } else {
                            "云端本次未下发；暂无可确认的写入命令"
                        },
                        classification = classification,
                        group = CarSettingGroup(
                            id = "missing:${classification.name}",
                            title = classification.label,
                            sort = classification.ordinal,
                        ),
                        channel = channelOf(configId),
                        kind = kind,
                        writable = writable,
                        sort = configId.toIntOrNull() ?: Int.MAX_VALUE,
                    ),
                )
            }
            .sortedWith(compareBy({ it.group.sort }, { it.sort }, { it.title }))
            .toList()
    }

    private fun titleOf(config: CloudBikeConfig): String =
        config.confName.trim()
            .ifBlank { TITLES[config.confId].orEmpty() }
            .ifBlank { "设置 ${config.confId}" }

    private fun summaryOf(config: CloudBikeConfig, title: String): String {
        if (config.confId.trim() in BLUETOOTH_KEY_IDS) {
            return "蓝牙车钥匙功能，请在原 App 的车钥匙模块中设置。"
        }
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
        return classificationOf(confId)
    }

    private fun classificationOf(confId: String): CarSettingClassification =
        when (confId) {
            "001" -> CarSettingClassification.METER
            "002" -> CarSettingClassification.POWER
            "003" -> CarSettingClassification.RIDE_CONTROL
            in BLUETOOTH_KEY_IDS -> CarSettingClassification.POWER
            in BATTERY_IDS -> CarSettingClassification.BATTERY
            in LIGHT_IDS -> CarSettingClassification.LIGHT
            in POWER_IDS -> CarSettingClassification.POWER
            in RIDE_IDS -> CarSettingClassification.RIDE_CONTROL
            in METER_IDS -> CarSettingClassification.METER
            in SECURITY_IDS -> CarSettingClassification.SECURITY
            else -> CarSettingClassification.OTHER
        }

    private fun channelOf(confId: String): CarSettingChannel = when {
        confId in BLUETOOTH_KEY_IDS -> CarSettingChannel.BLE_KEY
        confId in IOT_IDS -> CarSettingChannel.IOT
        confId.startsWith("special:") -> CarSettingChannel.LOCAL
        confId in ENTRANCE_IDS -> CarSettingChannel.LOCAL
        else -> CarSettingChannel.BLE
    }

    private fun cloudToggle(cloud: CloudCarSettings, key: String): CarSettingKind.Toggle =
        CarSettingKind.Toggle(
            checked = cloud.boolValue(key) == true,
            valueKnown = cloud.boolValue(key) != null,
        )

    private fun unknownToggle(): CarSettingKind.Toggle =
        CarSettingKind.Toggle(checked = false, valueKnown = false)

    private fun kindOf(confId: String, cloud: CloudCarSettings): CarSettingKind = when (confId) {
        in BLUETOOTH_KEY_IDS -> CarSettingKind.Info("请在原 App 的车钥匙模块中设置")
        // VehicleAlarmActivity binds alarmSoundSwitchAfterStatus, not the DTO-only cloud key.
        "13" -> unknownToggle()
        "16" -> cloudToggle(cloud, "dumpAlarm")
        // Original page binds pushModeStatus; the protected TTP parser lacks it.
        "3" -> unknownToggle()
        // resistanceOn exists only in DTOs; original energy page consumes the TTP flag.
        "17" -> unknownToggle()
        "18" -> CarSettingKind.Slider(
            value = 1f,
            range = 1f..3f,
            steps = 1,
            unit = " 档",
            valueKnown = false,
        )
        "40" -> cloudToggle(cloud, "colorEggSoundEffect")
        // 41/88 在原 App 都进入自定义音效列表页，统一在下方管理。
        "41" -> CarSettingKind.Info("进入设置")
        "001" -> CarSettingKind.Choice(
            selectedIndex = 0,
            options = listOf("km/h", "mph"),
            valueKnown = false,
        )
        "002", "003" -> unknownToggle()
        "53" -> unknownToggle()
        "10" -> cloudToggle(cloud, "distanceOn")
        "52" -> cloudToggle(cloud, "bleUnlockOn")
        "62" -> cloudToggle(cloud, "seatHeating")
        "63", "72", "79", "80", "90" -> unknownToggle()
        "77" -> cloudToggle(cloud, "sleepMode")
        "91" -> cloudToggle(cloud, "sosEmergencyCall")
        "95" -> cloudToggle(cloud, "wxhlBind")
        // TCS/ABS 的当前值来自车辆 BLE 状态，云端清单只负责下发项目本身；
        // 初值由 CarSettingsController.updateVehicleStatus 回填。
        "37", "38" -> unknownToggle()
        // 模拟声浪、仪表亮度调节同样由子 Activity 读取车辆状态。
        "33", "86" -> unknownToggle()
        // 自动大灯、转向灯回位、刹车断电、陡坡缓降、坡道驻停。
        "19", "26", "36", "46", "49" -> unknownToggle()
        // 急刹爆闪、档位记忆、辅助驾驶小蓝灯使用动态位域命令。
        "68", "94", "84" -> unknownToggle()
        "76" -> cloudToggle(cloud, "positioningFunc")
        "45" -> unknownToggle()
        "27" -> CarSettingKind.Choice(
            selectedIndex = -1,
            options = SimpleChoiceSettings.options.getValue("27"),
            valueKnown = false,
        )
        "31" -> CarSettingKind.Choice(
            selectedIndex = -1,
            options = SimpleChoiceSettings.options.getValue("31"),
            valueKnown = false,
        )
        "54" -> CarSettingKind.Choice(
            selectedIndex = -1,
            options = SimpleChoiceSettings.options.getValue("54"),
            valueKnown = false,
        )
        // OldVehicleSoundEffectsActivity：两个报警音主题，参数 0/1。
        "5" -> CarSettingKind.Choice(
            selectedIndex = 0,
            options = SecondPhaseSettings.soundOptions,
            valueKnown = false,
        )
        // HelpPromotePlusActivity：辅助推行开关；档位和自适应模式在下方追加。
        "64", "89" -> unknownToggle()
        // VehicleCustomizeActivity：FREE 模式的四项参数一次下发。
        "55", "92" -> CarSettingKind.Customize()
        // TopSpeedActivity / MaxSpeedSettingActivity：最高车速。
        "4" -> {
            val modelMin = cloud.values["bikeSpeedMaxDefaultMin"]?.trim()?.toIntOrNull()
                ?.takeIf { it in 15..120 }
            val modelMax = cloud.values["bikeSpeedMaxDefaultMax"]?.trim()?.toIntOrNull()
                ?.takeIf { it in 15..120 }
            val boundsKnown = modelMin != null && modelMax != null && modelMin < modelMax
            // Protocol bounds only permit displaying a real value; they never enable a write.
            val min = if (boundsKnown) modelMin else 15
            val max = if (boundsKnown) modelMax else 120
            val configured = cloud.values["bikeSpeedMax"]?.trim()?.toIntOrNull()?.takeIf { it in min..max }
            CarSettingKind.Slider(
                value = configured?.toFloat() ?: min.toFloat(),
                range = min.toFloat()..max.toFloat(),
                steps = (max - min - 1).coerceAtLeast(0),
                unit = " km/h",
                valueKnown = configured != null,
                rangeKnown = boundsKnown,
            )
        }
        // MaxSpeedSettingActivity$a: positions 0/1/2 send model min/max/0, not the position.
        "58" -> {
            val min = cloud.values["bikeModelTopSpeedMin"]?.trim()?.toIntOrNull()?.takeIf { it in 15..120 }
            val max = cloud.values["bikeModelTopSpeedMax"]?.trim()?.toIntOrNull()?.takeIf { it in 15..120 }
            val presets = if (min != null && max != null && min < max) listOf(min, max, 0) else emptyList()
            val upper = max ?: 120
            val configured = cloud.values["bikeSpeedMax"]?.trim()?.toIntOrNull()
                ?.takeIf { it == 0 || it in 15..upper }
            CarSettingKind.Slider(
                value = configured?.toFloat() ?: 0f,
                range = 0f..upper.toFloat(),
                unit = " km/h",
                minimumNonZero = 15f,
                valueKnown = configured != null,
                speedPresets = presets,
                rangeKnown = presets.size == 3,
            )
        }
        // AutoShutDownActivity 与 BraceAutoShutDownActivity 共用离车自动锁命令。
        "7" -> unknownToggle()
        "11" -> unknownToggle()
        // GaugeStyleHandOffActivity.onCheckChange: rb_1=0, rb_2=1.
        "50" -> CarSettingKind.Choice(
            selectedIndex = -1,
            options = SimpleChoiceSettings.options.getValue("50"),
            valueKnown = false,
        )
        // ScheduledShutDown2Activity 与 confId 15 使用同一组定时关机命令。
        "59" -> unknownToggle()
        // SOS 联系人页复用紧急联系人编辑器。
        "85" -> CarSettingKind.Info("在下方编辑车主与紧急联系人")
        // RadarActivity：雷达分项在下方追加。
        "69" -> CarSettingKind.Info("雷达功能分项设置见下方")
        // QuietModeActivity：关闭超速提示，原实现同时下发新旧两个字段。
        "93" -> unknownToggle()
        // CustomSoundEffectListActivity：列表、开关和应用入口在下方管理。
        "88" -> CarSettingKind.Info("进入设置")
        // CustomizeKeyActivity：短按和长按功能在下方分别设置。
        "1005" -> CarSettingKind.Info("设置短按和长按功能")
        // GaugeSettingsActivity 是仪表蓝牙配对与同步页，不是可写参数。
        "71" -> CarSettingKind.Entrance("请到“连接”页配对仪表")
        // 边撑自动关机：原 App 只给开关，依赖边撑感应（confId 2）。
        "39" -> unknownToggle()
        // 报失模式需要短信验证，统一在“诊断 · 定位与报失”中操作。
        "8" -> CarSettingKind.Info("请到“诊断 · 定位与报失”中操作")
        "15" -> unknownToggle()
        "21", "81" -> unknownToggle()
        "73" -> unknownToggle()
        "9" -> CarSettingKind.LowPowerInfo()
        "24" -> cloudToggle(cloud, "emergencyContact")
        // Original binding t4 -> p4 reads TTP.iotRemoveAlarm, not this cloud request field.
        "75" -> unknownToggle()
        "82" -> CarSettingKind.LowPowerPhone()
        "61" -> {
            val modelMax = cloud.values["bikeModelTopSpeedMax"]?.trim()?.toIntOrNull()?.takeIf { it in 15..120 }
            val max = modelMax ?: 120
            val configured = cloud.values["highestSpeedLimit"]?.trim()?.toIntOrNull()
                ?.takeIf { it == 0 || it in 15..max }
            CarSettingKind.Slider(
                value = configured?.toFloat() ?: 0f,
                range = 0f..max.toFloat(),
                steps = (max - 1).coerceAtLeast(0),
                unit = " km/h",
                // 0 表示不限速；非零最低 15 km/h。
                minimumNonZero = 15f,
                valueKnown = configured != null,
                rangeKnown = modelMax != null,
            )
        }
        // VehicleVolumeActivity$a sends thumbPosition; original layout has exactly three ticks.
        "20" -> CarSettingKind.Slider(
            value = 0f,
            range = 0f..2f,
            steps = 1,
            valueKnown = false,
        )
        // Original alarm layout has three ticks on the default 0..100 progress range.
        "14" -> CarSettingKind.Slider(
            value = 0f,
            range = 0f..100f,
            steps = 1,
            valueKnown = false,
        )
        "2", "32" -> unknownToggle()
        "42" -> unknownToggle()
        // 倒车模式与婴儿车模式由车辆 TTP 状态回填。
        "43", "44" -> unknownToggle()
        "51" -> unknownToggle()
        "47" -> {
            // ChargingPowerActivity：200..1000W，50W 步进；最大值按云端/车型上限 1000W。
            val maxWatt = 1000
            val minWatt = 200
            val watt = cloud.values["chargeWattVal"]?.trim()?.toIntOrNull()?.takeIf { it in minWatt..maxWatt }
            CarSettingKind.Slider(
                value = watt?.toFloat() ?: minWatt.toFloat(),
                range = minWatt.toFloat()..maxWatt.toFloat(),
                steps = (maxWatt - minWatt) / 50 - 1,
                unit = " W",
                valueKnown = watt != null,
            )
        }
        "48" -> {
            // VehicleSettingViewModel accepts integer 75..100; BLE encodes multiples of five only.
            val cap = cloud.values["chargeUlVal"]?.trim()?.toIntOrNull()?.takeIf { it in 75..100 }
            CarSettingKind.Slider(
                value = cap?.toFloat() ?: 75f,
                range = 75f..100f,
                steps = 24,
                unit = "%",
                valueKnown = cap != null,
            )
        }
        // CapacitySettingActivity：电池容量功能拆成开关与固定容量档位。
        "60" -> CarSettingKind.Info("可分别设置电池容量开关和容量档位")
        in ENTRANCE_IDS, in SPECIAL_IDS -> CarSettingKind.Entrance()
        else -> CarSettingKind.Info("云端设置项，暂未接入写入")
    }

    /** 与 CustomizeKeyActivity.getFunctionId 的已确认映射一致。 */
    private val KEY_FUNCTION_OPTIONS = listOf(
        "ABS 防抱死",
        "TCS 车身稳定",
        "雷达功能",
        "行车记录仪紧急录制",
        "行车记录仪抓拍",
        "长续航/性能模式切换",
        "氛围灯功能",
        "坐垫加热功能",
        "AI 语音助手唤醒",
        "无定义",
    )

    private val BATTERY_IDS = setOf("9", "47", "48", "54")
    /** 蓝牙校准属于车钥匙链路；confId 1 已由原 App 的一键开机页面独立处理。 */
    private val BLUETOOTH_KEY_IDS = setOf("66")
    private val LIGHT_IDS = setOf(
        "5", "19", "20", "21", "26", "33", "40", "41", "68", "73", "81", "84", "88", "93",
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
        "8", "10", "13", "14", "16", "24", "27", "32", "36", "37", "38", "69", "70", "78",
        "75", "76", "77", "82", "85", "91",
    )
    private val IOT_IDS = setOf(
        "8", "9", "10", "16", "24", "40", "47", "48", "60", "61", "62", "71", "75", "76", "82", "91",
    )
    private val ENTRANCE_IDS = setOf(
        "9", "10", "14", "15", "17", "18", "20", "24",
        "25", "27", "31", "33", "41", "47", "48",
        "61", "72", "73", "77", "81", "86",
        "91", "95",
    )
    private val SPECIAL_IDS = setOf(
        "special:meter_wallpaper_skin",
        "special:meter_gauge_sync",
        "special:footer_intelligence",
        "special:footer_lab",
        "footer_device_detail",
    )

    private val TITLES = mapOf(
        "001" to "X1 距离单位",
        "002" to "X1 感应自动开关机",
        "003" to "X1 制动能量回收",
        "1" to "一键开机",
        "2" to "边撑感应",
        "3" to "携出助力",
        "4" to "档位设置",
        "5" to "车辆音效",
        "7" to "自动落锁",
        "8" to "一键报失",
        "9" to "信息提醒",
        "10" to "电子围栏",
        "11" to "自动开关机",
        "13" to "报警音",
        "14" to "车辆报警灵敏度",
        "15" to "离车定时关机",
        "16" to "倾倒报警",
        "17" to "能量回收开关",
        "18" to "能量回收档位",
        "19" to "自动大灯",
        "20" to "车辆音量",
        "21" to "伴我回家灯",
        "24" to "紧急联系人",
        "25" to "密码解锁",
        "26" to "转向灯自动回位",
        "27" to "设防模式",
        "31" to "速度单位",
        "32" to "倾倒提醒",
        "33" to "模拟声浪",
        "36" to "刹车断电",
        "37" to "TCS 车身稳定",
        "38" to "ABS 防抱死",
        "39" to "边撑自动关机",
        "40" to "彩蛋音效",
        "41" to "自定义音效",
        "42" to "车辆显示剩余里程",
        "43" to "倒车模式",
        "44" to "婴儿车模式",
        "45" to "定速巡航",
        "46" to "陡坡缓降",
        "47" to "充电功率",
        "48" to "充电上限",
        "49" to "坡道驻停",
        "50" to "仪表风格切换",
        "51" to "仪表时间同步",
        "52" to "手动距离自定义",
        "53" to "边撑开机",
        "54" to "电压设置",
        "55" to "FREE 自由模式",
        "58" to "档位设置",
        "59" to "报警锁",
        "60" to "电池设置",
        "61" to "安全限速",
        "62" to "座椅加热",
        "63" to "超级狂暴模式",
        "64" to "辅助推行",
        "68" to "急刹爆闪",
        "69" to "雷达",
        "82" to "电话告警",
        "71" to "仪表设置",
        "72" to "长续航模式",
        "73" to "氛围灯",
        "75" to "定位器防盗",
        "76" to "定位开关",
        "77" to "休眠模式",
        "79" to "座垫感应上电",
        "80" to "OTD 开关",
        "81" to "大灯延时",
        "84" to "安全辅助小蓝灯",
        "85" to "SOS 联系人",
        "86" to "仪表亮度调节",
        "88" to "自定义音效",
        "89" to "辅助骑行",
        "90" to "赛道模式",
        "91" to "SOS 紧急呼叫",
        "92" to "自定义骑行",
        "93" to "静音模式",
        "94" to "档位记忆",
        "95" to "微信互联",
        "1005" to "自定义按键",
        "66" to "自动校准",
        "special:meter_wallpaper_skin" to "壁纸皮肤设置",
        "special:meter_gauge_sync" to "车辆信息同步",
        "special:footer_intelligence" to "用户智能识别",
        "special:footer_lab" to "实验室",
        "footer_device_detail" to "设备详情",
    )
}
