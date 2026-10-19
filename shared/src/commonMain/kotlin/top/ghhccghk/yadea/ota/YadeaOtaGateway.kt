package top.ghhccghk.yadea.ota

import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.NetworkLog
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.ble.BleAdapter

/**
 * OTA 网络层。
 *
 * 检查更新来自 `firmwareVersion/latestVersion`；任务列表来自
 * `upgradeTask/queryTaskList/V4`。真正的固件下载/写入仍由平台安装器完成，
 * 因为不同车型会走 BLE 或 Wi-Fi/P2P 通道。
 */
class YadeaOtaGateway(
    private val transport: HttpTransport,
    private val profileProvider: () -> VehicleProfile?,
    private val tokenProvider: () -> String,
    private val loginIdProvider: () -> String = { "" },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val bleAdapter: BleAdapter? = null,
    private val downloader: FirmwareDownloader = createFirmwareDownloader(),
) : OtaGateway {

    override suspend fun checkForUpdate(currentVersion: String): OtaPackage? =
        checkForUpdates(currentVersion).firstOrNull()

    override suspend fun checkForUpdates(currentVersion: String): List<OtaPackage> {
        val profile = profileProvider() ?: return emptyList()
        if (profile.modelName.isBlank()) return emptyList()
        val latest = loadLatestPackages(profile)
        val tasks = loadTaskPackages(profile)
        return (tasks + latest)
            .distinctBy { "${it.method}:${it.firmwareType}:${it.typeName}:${it.version}" }
            .sortedWith(
                compareBy<OtaPackage>(
                    { it.method.ordinal },
                    { it.firmwareType },
                    { it.typeName },
                    { it.version },
                ),
            )
    }

    private suspend fun loadLatestPackages(profile: VehicleProfile): List<OtaPackage> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                // 原 X1 页面查询控制器、电池、仪表三类最新版本。
                "firmwareTypes" to "[1,2,3]",
                "scooterModelName" to profile.modelName,
            ),
        )
        val plain = post("firmwareVersion/latestVersion", json) ?: return emptyList()
        val listObjects = JsonSupport.arrayObjects(plain, "data")
        val objects = if (listObjects.isNotEmpty()) {
            listObjects
        } else {
            listOfNotNull(JsonSupport.objectField(plain, "data"))
        }
        return objects.flatMap { typeObject ->
            val firmwareType = intField(typeObject, "firmwareType") ?: 0
            val versions = JsonSupport.arrayObjects(typeObject, "firmwareVersionVOList")
            versions.mapNotNull { versionObject ->
                val version = textOrNumber(versionObject, "firmwareVersion") ?: return@mapNotNull null
                OtaPackage(
                    version = version,
                    releaseNotes = stringField(versionObject, "upgradeContent").orEmpty(),
                    downloadUrl = stringField(versionObject, "url").orEmpty(),
                    method = OtaMethod.Wifi,
                    firmwareType = firmwareType,
                    typeName = firmwareTypeLabel(firmwareType),
                    channelLabel = "WI-FI",
                    forceUpgrade = intField(versionObject, "forceUpgrade") == 1,
                )
            }
        }
    }

    private suspend fun loadTaskPackages(profile: VehicleProfile): List<OtaPackage> {
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "experimental" to "",
                "izSkip" to true,
                "modelNo" to profile.modelNo,
                "vin" to profile.vin,
            ),
        )
        val plain = post("upgradeTask/queryTaskList/V4", json) ?: return emptyList()
        val dataObjects = JsonSupport.arrayObjects(plain, "data")
            .ifEmpty { listOfNotNull(JsonSupport.objectField(plain, "data")) }
        if (dataObjects.isEmpty()) return emptyList()
        val taskObjects = dataObjects.flatMap { JsonSupport.arrayObjects(it, "taskList") }
        val dataStatus = stringField(dataObjects.first(), "status").orEmpty()
        return taskObjects.flatMap { task ->
            val taskName = stringField(task, "taskName").orEmpty()
            val taskStatus = stringField(task, "status").orEmpty().ifBlank { dataStatus }
            val taskNotes = stringField(task, "upgradeContent").orEmpty()
            val taskId = textOrNumber(task, "taskId").orEmpty()
            val behaviorId = textOrNumber(task, "behaviorId").orEmpty()
            val firmwareObjects = JsonSupport.arrayObjects(task, "firmwareInfoList")
            firmwareObjects.mapNotNull { firmware ->
                val version = textOrNumber(firmware, "firmwareVersion") ?: return@mapNotNull null
                val channel = textOrNumber(firmware, "channel").orEmpty()
                OtaPackage(
                    version = version,
                    releaseNotes = stringField(firmware, "versionDesc")
                        .orEmpty()
                        .ifBlank { taskNotes },
                    downloadUrl = stringField(firmware, "downloadUrl").orEmpty(),
                    method = methodOfChannel(channel),
                    typeName = stringField(firmware, "firmwareName")
                        .orEmpty()
                        .ifBlank { stringField(firmware, "typeName").orEmpty() }
                        .ifBlank { taskName }
                        .ifBlank { "车辆固件" },
                    channelLabel = channelLabel(channel),
                    status = stringField(firmware, "firmwareUpgrdeStatus")
                        .orEmpty()
                        .ifBlank { taskStatus },
                    taskId = taskId,
                    firmwareModuleCode = textOrNumber(firmware, "firmwareModuleCode")
                        .orEmpty()
                        .ifBlank { textOrNumber(firmware, "firmwareCode").orEmpty() },
                    behaviorId = behaviorId,
                )
            }
        }
    }

    /**
     * 查询 OTA 任务列表。
     *
     * 返回原始解密 JSON；调用方可用 `taskId/status` 继续轮询。
     */
    suspend fun queryTaskList(): String? {
        val profile = profileProvider() ?: return null
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "experimental" to "",
                "izSkip" to true,
                "modelNo" to profile.modelNo,
                "vin" to profile.vin,
            ),
        )
        return post("upgradeTask/queryTaskList/V4", json)
    }

    override suspend fun confirmIotUpgrade(packageInfo: OtaPackage): Boolean {
        val profile = profileProvider() ?: return false
        if (packageInfo.firmwareModuleCode.isBlank()) return false
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "vin" to profile.vin,
                "os" to "1",
                "modelNo" to profile.modelNo,
                "modelName" to profile.modelName,
                "experimental" to "",
                "confirmUpgradeType" to "1",
                "firmwareModuleCode" to packageInfo.firmwareModuleCode,
            ),
        )
        val plain = post("upgradeTask/userConfirm/V4", json) ?: return false
        return JsonSupport.boolean(plain, "success") != false
    }

    override suspend fun queryUpgradingTask(): String? {
        val profile = profileProvider() ?: return null
        val json = sortedJson(
            mapOf(
                "_gateway" to "True",
                "experimental" to "",
                "vin" to profile.vin,
            ),
        )
        return post("upgradeTask/queryUpgradingTask", json)
    }

    override suspend fun install(
        packageInfo: OtaPackage,
        onProgress: suspend (Float, OtaStage) -> Unit,
    ): Boolean {
        if (packageInfo.method != OtaMethod.Ble) return false
        if (packageInfo.downloadUrl.isBlank()) return false
        val adapter = bleAdapter ?: return false
        onProgress(0.02f, OtaStage.Downloading)
        val firmware = downloader.download(packageInfo.downloadUrl) ?: return false
        onProgress(0.12f, OtaStage.Verifying)
        return BleOtaInstaller(adapter).install(firmware, onProgress)
    }

    private suspend fun post(path: String, businessJson: String): String? {
        val token = tokenProvider().trim()
        if (token.isBlank()) return null
        val body = GatewaySigner.formBody(businessJson, timestampProvider())
        val url = baseUrl + path
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = loginIdProvider(),
            token = token,
        )
        NetworkLog.write(
            "OTA -> POST $url headers=${NetworkLog.headers(headers)} " +
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
            NetworkLog.write("OTA !! no response path=$path")
            return null
        }
        NetworkLog.write("OTA <- ${response.status} raw=${NetworkLog.body(response.body)}")
        if (response.status !in 200..299) return null
        val decoded = Crypto.urlDecode(response.body.trim())
        val plain = Crypto.aesEcbPkcs5Decrypt(
            GatewaySigner.AES_KEY,
            decoded,
        ) ?: decoded
        NetworkLog.write("OTA plain path=$path ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) return null
        return plain
    }

    private fun sortedJson(fields: Map<String, Any>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            val value = fields.getValue(key)
            if (index > 0) append(',')
            append('"').append(key).append("\":")
            when (value) {
                is String -> append('"')
                    .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append('"')
                is Boolean -> append(value)
                is Number -> append(value)
                else -> append('"').append(value.toString()).append('"')
            }
        }
        append('}')
    }

    private fun textOrNumber(json: String, key: String): String? =
        stringField(json, key) ?: JsonSupport.numberText(json, key)

    private fun stringField(json: String, key: String): String? =
        JsonSupport.string(json, key)

    private fun intField(json: String, key: String): Int? =
        JsonSupport.int(json, key) ?: stringField(json, key)?.toIntOrNull()

    private fun methodOfChannel(channel: String): OtaMethod = when (channel) {
        "2" -> OtaMethod.Iot
        "4" -> OtaMethod.Wifi
        "15" -> OtaMethod.WifiDvr
        else -> OtaMethod.Ble
    }

    private fun channelLabel(channel: String): String = when (channel) {
        "1" -> "蓝牙"
        "2" -> "网络"
        "3" -> "蓝牙/网络"
        "4" -> "WI-FI"
        "15" -> "WI-FI 记录仪"
        else -> channel.ifBlank { "未知通道" }
    }

    private fun firmwareTypeLabel(type: Int): String = when (type) {
        1 -> "控制器"
        2 -> "电池"
        3 -> "仪表"
        else -> "固件类型 $type"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}
