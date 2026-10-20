package top.ghhccghk.yadea.ota

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OtaPackage(
    val version: String,
    val sizeBytes: Long = 0L,
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val method: OtaMethod = OtaMethod.Ble,
    val firmwareType: Int = 0,
    val typeName: String = "",
    val channelLabel: String = "",
    val status: String = "",
    val forceUpgrade: Boolean = false,
    val taskId: String = "",
    val firmwareModuleCode: String = "",
    val firmwareCode: String = "",
    val strategyId: String = "",
    val behaviorId: String = "",
)

data class OtaRecord(
    val typeName: String = "",
    val firmwareVersion: String = "",
    val versionDesc: String = "",
    val startTime: String = "",
)

data class OtaRecordPage(
    val records: List<OtaRecord> = emptyList(),
    val hasNext: Boolean = false,
)

/** `ota/upgrade/version` 返回的一个固件模块版本。 */
data class OtaFirmwareVersion(
    val firmwareType: Int = 0,
    val typeName: String = "",
    val firmwareName: String = "",
    val currentVersion: String = "",
    val latestVersion: String = "",
    val statusCode: Int? = null,
) {
    val label: String
        get() = firmwareName.ifBlank { typeName }.ifBlank {
            when (firmwareType) {
                1 -> "控制器"
                2 -> "电池"
                3 -> "仪表"
                else -> "固件模块"
            }
        }
}

enum class OtaCloudUpgradeState {
    Idle,
    Preparing,
    Upgrading,
    Succeeded,
    Failed,
    Unknown,
}

/**
 * `upgradeTask/queryUpgradingTask` 的界面子集。
 *
 * 原 App 使用字符串状态：`2` 升级中，`3` 成功，`4` 失败。
 * `behaviorId` 是任务结果确认和清除标记所需的主键。
 */
data class OtaCloudUpgradeStatus(
    val status: String = "",
    val behaviorId: String = "",
    val taskId: String = "",
    val taskName: String = "",
    val firmwareName: String = "",
    val firmwareVersion: String = "",
    val alertCode: Int? = null,
    val completedParts: Int? = null,
    val totalParts: Int? = null,
    val message: String = "",
) {
    val state: OtaCloudUpgradeState
        get() = when (status.trim()) {
            "1" -> OtaCloudUpgradeState.Preparing
            "2" -> OtaCloudUpgradeState.Upgrading
            "3" -> OtaCloudUpgradeState.Succeeded
            "4" -> OtaCloudUpgradeState.Failed
            "", "0" -> OtaCloudUpgradeState.Idle
            else -> OtaCloudUpgradeState.Unknown
        }

    val stateLabel: String
        get() = when (state) {
            OtaCloudUpgradeState.Idle -> "等待车辆"
            OtaCloudUpgradeState.Preparing -> "正在准备升级"
            OtaCloudUpgradeState.Upgrading -> "车辆升级中"
            OtaCloudUpgradeState.Succeeded -> "升级成功"
            OtaCloudUpgradeState.Failed -> "升级失败"
            OtaCloudUpgradeState.Unknown -> "状态 $status"
        }

    val isTerminal: Boolean
        get() = state == OtaCloudUpgradeState.Succeeded ||
            state == OtaCloudUpgradeState.Failed

    val isInProgress: Boolean
        get() = state == OtaCloudUpgradeState.Preparing ||
            state == OtaCloudUpgradeState.Upgrading

    val partProgressLabel: String?
        get() = when {
            completedParts == null && totalParts == null -> null
            else -> "${completedParts ?: 0}/${totalParts ?: 0}"
        }
}

/** `ota/upgradeLog/addUpgradeLog` 的请求字段。 */
data class OtaUpgradeLogEntry(
    val taskId: String = "",
    val strategyId: String = "",
    val firmwareName: String = "",
    val firmwareCode: String = "",
    val beforeVersion: String = "",
    val afterVersion: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val upgradeResult: Int = 1,
    val failureReason: String = "",
)

enum class OtaMethod(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    Ble("蓝牙升级", "蓝牙", "手机靠近车辆，通过 BLE 通道传输固件"),
    Iot("车联网升级", "车联网", "车辆联网后由云平台下发升级任务"),
    Wifi("Wi-Fi 升级", "Wi-Fi", "车辆与手机通过 Wi-Fi/P2P 通道升级"),
    WifiDvr("Wi-Fi 记录仪升级", "记录仪", "行车记录仪通过独立 Wi-Fi 通道升级"),
}

enum class OtaStage {
    Unavailable,
    Idle,
    Checking,
    Available,
    UpToDate,
    Downloading,
    Verifying,
    Installing,
    Submitted,
    Completed,
    Failed,
    Cancelled,
}

data class OtaState(
    val currentVersion: String = "—",
    val stage: OtaStage = OtaStage.Unavailable,
    val packageInfo: OtaPackage? = null,
    val packages: List<OtaPackage> = emptyList(),
    val selectedMethod: OtaMethod = OtaMethod.Ble,
    val progress: Float = 0f,
    val message: String? = null,
    val records: List<OtaRecord> = emptyList(),
    val recordsLoading: Boolean = false,
    val recordsError: String? = null,
    val recordsPage: Int = 0,
    val recordsHasNext: Boolean = false,
    val firmwareVersions: List<OtaFirmwareVersion> = emptyList(),
    val cloudUpgradeStatus: OtaCloudUpgradeStatus? = null,
    val cloudStatusLoading: Boolean = false,
    val cloudStatusError: String? = null,
) {
    val busy: Boolean
        get() = stage == OtaStage.Checking ||
            stage == OtaStage.Downloading ||
            stage == OtaStage.Verifying ||
            stage == OtaStage.Installing

    val canCheck: Boolean
        get() = stage != OtaStage.Unavailable && !busy

    val canInstall: Boolean
        get() = stage == OtaStage.Available || stage == OtaStage.Failed || stage == OtaStage.Cancelled

    val canCancel: Boolean
        get() = stage == OtaStage.Downloading || stage == OtaStage.Verifying || stage == OtaStage.Installing

    val selectedPackages: List<OtaPackage>
        get() = packages.filter { it.method == selectedMethod }
}

sealed interface OtaEvent {
    data class CurrentVersionChanged(val version: String) : OtaEvent
    data object CheckStarted : OtaEvent
    data class UpdateFound(val packageInfo: OtaPackage) : OtaEvent
    data class UpdatesFound(val packages: List<OtaPackage>) : OtaEvent
    data object UpToDate : OtaEvent
    data class CheckFailed(val message: String) : OtaEvent
    data object InstallStarted : OtaEvent
    data class Progress(val value: Float, val stage: OtaStage) : OtaEvent
    data class FirmwareVersionsLoaded(val versions: List<OtaFirmwareVersion>) : OtaEvent
    data class CloudStatusLoaded(val status: OtaCloudUpgradeStatus?) : OtaEvent
    data class CloudStatusFailed(val message: String) : OtaEvent
    data object Completed : OtaEvent
    data class Failed(val message: String) : OtaEvent
    data object Cancelled : OtaEvent
}

/** OTA 纯状态机；下载/写入等 I/O 留给 [OtaGateway]。 */
object OtaFlow {
    fun reduce(state: OtaState, event: OtaEvent): OtaState = when (event) {
        is OtaEvent.CurrentVersionChanged ->
            state.copy(currentVersion = event.version.ifBlank { "—" })
        OtaEvent.CheckStarted ->
            state.copy(stage = OtaStage.Checking, message = null, progress = 0f)
        is OtaEvent.UpdateFound ->
            state.copy(
                stage = OtaStage.Available,
                packageInfo = event.packageInfo,
                packages = listOf(event.packageInfo),
                selectedMethod = event.packageInfo.method,
                progress = 0f,
                message = null,
            )
        is OtaEvent.UpdatesFound ->
            if (event.packages.isEmpty()) {
                state.copy(
                    stage = OtaStage.UpToDate,
                    packageInfo = null,
                    packages = emptyList(),
                    message = "当前已是最新版本",
                )
            } else {
                val first = event.packages.first()
                state.copy(
                    stage = OtaStage.Available,
                    packageInfo = first,
                    packages = event.packages,
                    selectedMethod = first.method,
                    progress = 0f,
                    message = null,
                )
            }
        OtaEvent.UpToDate ->
            state.copy(
                stage = OtaStage.UpToDate,
                packageInfo = null,
                packages = emptyList(),
                message = "当前已是最新版本",
            )
        is OtaEvent.CheckFailed ->
            state.copy(stage = OtaStage.Failed, message = event.message)
        OtaEvent.InstallStarted ->
            state.copy(
                stage = OtaStage.Downloading,
                progress = 0f,
                message = null,
            )
        is OtaEvent.Progress ->
            state.copy(
                stage = event.stage,
                progress = event.value.coerceIn(0f, 1f),
            )
        is OtaEvent.FirmwareVersionsLoaded -> {
            val summary = firmwareVersionSummary(event.versions)
            state.copy(
                firmwareVersions = event.versions,
                currentVersion = summary.ifBlank { state.currentVersion },
            )
        }
        is OtaEvent.CloudStatusLoaded -> {
            val cloud = event.status
            state.copy(
                cloudUpgradeStatus = cloud,
                cloudStatusLoading = false,
                cloudStatusError = null,
                stage = when {
                    cloud?.state == OtaCloudUpgradeState.Succeeded &&
                        state.selectedMethod == OtaMethod.Iot -> OtaStage.Completed
                    cloud?.state == OtaCloudUpgradeState.Failed &&
                        state.selectedMethod == OtaMethod.Iot -> OtaStage.Failed
                    else -> state.stage
                },
                message = when {
                    cloud?.state == OtaCloudUpgradeState.Succeeded ->
                        "云端升级任务已完成"
                    cloud?.state == OtaCloudUpgradeState.Failed ->
                        cloud.message.ifBlank { "云端升级任务失败" }
                    cloud?.state == OtaCloudUpgradeState.Preparing ->
                        "车辆正在准备升级，请保持车辆在线"
                    cloud?.state == OtaCloudUpgradeState.Upgrading ->
                        "车辆正在执行云端升级，请勿断电或骑行"
                    else -> state.message
                },
            )
        }
        is OtaEvent.CloudStatusFailed ->
            state.copy(
                cloudStatusLoading = false,
                cloudStatusError = event.message,
            )
        OtaEvent.Completed ->
            state.copy(stage = OtaStage.Completed, progress = 1f, message = "升级完成，请重启车辆")
        is OtaEvent.Failed ->
            state.copy(stage = OtaStage.Failed, message = event.message)
        OtaEvent.Cancelled ->
            state.copy(stage = OtaStage.Cancelled, progress = 0f, message = "已取消升级")
    }
}

/** OTA 通道抽象。协议细节未在 docs 中确认，因此平台侧可在此接入官方升级链路。 */
interface OtaGateway {
    suspend fun checkForUpdate(currentVersion: String): OtaPackage?
    suspend fun checkForUpdates(currentVersion: String): List<OtaPackage> =
        listOfNotNull(checkForUpdate(currentVersion))
    suspend fun install(
        packageInfo: OtaPackage,
        onProgress: suspend (progress: Float, stage: OtaStage) -> Unit,
    ): Boolean
    /** IoT 升级只做云端任务确认，实际安装由云平台/车辆完成。 */
    suspend fun confirmIotUpgrade(packageInfo: OtaPackage): Boolean = false
    /** 查询进行中的云端升级任务，返回解密后的原始 JSON。 */
    suspend fun queryUpgradingTask(): String? = null
    /** 云端固件模块版本，原接口为 `ota/upgrade/version`。 */
    suspend fun queryFirmwareVersions(): List<OtaFirmwareVersion> = emptyList()
    /** 查询并结构化进行中的云端升级任务。 */
    suspend fun queryCloudUpgradeStatus(): OtaCloudUpgradeStatus? = null
    /** 升级结束后清除 `behaviorId` 对应的任务标记。 */
    suspend fun acknowledgeCloudUpgrade(behaviorId: String): Boolean = false
    /** 升级结束后上报固件信息，失败不影响本地升级结果。 */
    suspend fun uploadFirmwareInfo(packageInfo: OtaPackage, success: Boolean): Boolean = false
    /** 写入升级记录，失败不影响本地升级结果。 */
    suspend fun addUpgradeLog(entry: OtaUpgradeLogEntry): Boolean = false
    /** OTA 升级记录，原接口为 `upgradeLog/queryUpgradeLogByVin/V3`。 */
    suspend fun queryUpgradeRecords(current: Int, size: Int): OtaRecordPage = OtaRecordPage()
}

class OtaController(
    private val gateway: OtaGateway?,
    currentVersion: String,
    private val scope: CoroutineScope,
    private val timestampTextProvider: () -> String = { "" },
) {
    private val _state = MutableStateFlow(
        OtaState(
            currentVersion = currentVersion.ifBlank { "—" },
            stage = if (gateway == null) OtaStage.Unavailable else OtaStage.Idle,
            message = if (gateway == null) "OTA 服务未接入" else null,
        ),
    )
    val state: StateFlow<OtaState> = _state.asStateFlow()

    private var job: Job? = null
    private var recordsJob: Job? = null
    private var cloudJob: Job? = null
    private var cloudPollJob: Job? = null

    fun updateCurrentVersion(version: String) {
        if (_state.value.firmwareVersions.any { it.currentVersion.isNotBlank() }) return
        _state.value = OtaFlow.reduce(_state.value, OtaEvent.CurrentVersionChanged(version))
    }

    fun checkForUpdate() {
        val gateway = gateway ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = OtaFlow.reduce(_state.value, OtaEvent.CheckStarted)
            val result = runCatching { gateway.checkForUpdates(_state.value.currentVersion) }
            _state.value = result.fold(
                onSuccess = { packages ->
                    if (packages.isEmpty()) OtaFlow.reduce(_state.value, OtaEvent.UpToDate)
                    else OtaFlow.reduce(_state.value, OtaEvent.UpdatesFound(packages))
                },
                onFailure = {
                    OtaFlow.reduce(
                        _state.value,
                        OtaEvent.CheckFailed(it.message ?: "检查更新失败"),
                    )
                },
            )
        }
    }

    fun selectMethod(method: OtaMethod) {
        val selected = _state.value.packages.firstOrNull { it.method == method }
        _state.value = _state.value.copy(
            selectedMethod = method,
            packageInfo = selected,
            stage = if (selected != null) OtaStage.Available else OtaStage.Idle,
            progress = 0f,
            message = if (selected == null) "当前通道没有可用升级任务" else null,
        )
    }

    fun selectPackage(packageInfo: OtaPackage) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(
            packageInfo = packageInfo,
            selectedMethod = packageInfo.method,
            stage = OtaStage.Available,
            progress = 0f,
            message = null,
        )
    }

    /**
     * 刷新云端固件版本和进行中任务。
     *
     * 如果云端任务仍在升级中，会继续短轮询，直到成功/失败或达到轮询上限。
     */
    fun refreshCloudState() {
        val gateway = gateway ?: return
        if (cloudJob?.isActive == true) return
        cloudJob = scope.launch {
            _state.value = _state.value.copy(
                cloudStatusLoading = true,
                cloudStatusError = null,
            )
            val versions = runCatching { gateway.queryFirmwareVersions() }
            versions.onSuccess {
                _state.value = OtaFlow.reduce(
                    _state.value,
                    OtaEvent.FirmwareVersionsLoaded(it),
                )
            }
            val cloud = runCatching { gateway.queryCloudUpgradeStatus() }
            cloud.fold(
                onSuccess = { status ->
                    _state.value = OtaFlow.reduce(
                        _state.value,
                        OtaEvent.CloudStatusLoaded(status),
                    )
                    if (status?.isInProgress == true) {
                        startCloudStatusPolling()
                    } else if (status?.isTerminal == true) {
                        acknowledgeTerminalStatus(status)
                    }
                },
                onFailure = { error ->
                    _state.value = OtaFlow.reduce(
                        _state.value,
                        OtaEvent.CloudStatusFailed(
                            error.message ?: "查询云端升级状态失败",
                        ),
                    )
                },
            )
            _state.value = _state.value.copy(
                cloudStatusLoading = false,
                cloudStatusError = if (versions.isFailure && cloud.isFailure) {
                    "云端版本与升级状态查询失败"
                } else {
                    _state.value.cloudStatusError
                },
            )
        }
    }

    private fun startCloudStatusPolling() {
        val gateway = gateway ?: return
        if (cloudPollJob?.isActive == true) return
        cloudPollJob = scope.launch {
            repeat(CLOUD_STATUS_POLL_ATTEMPTS) {
                val status = runCatching { gateway.queryCloudUpgradeStatus() }.getOrNull()
                if (status != null) {
                    _state.value = OtaFlow.reduce(
                        _state.value,
                        OtaEvent.CloudStatusLoaded(status),
                    )
                    if (status.isTerminal) {
                        acknowledgeTerminalStatus(status)
                        runCatching { gateway.queryFirmwareVersions() }
                            .getOrNull()
                            ?.let {
                                _state.value = OtaFlow.reduce(
                                    _state.value,
                                    OtaEvent.FirmwareVersionsLoaded(it),
                                )
                            }
                        return@launch
                    }
                }
                if (it < CLOUD_STATUS_POLL_ATTEMPTS - 1) {
                    kotlinx.coroutines.delay(CLOUD_STATUS_POLL_MS)
                }
            }
            _state.value = _state.value.copy(
                message = "本轮云端状态轮询已结束，可手动刷新",
            )
        }
    }

    private fun acknowledgeTerminalStatus(status: OtaCloudUpgradeStatus) {
        val gateway = gateway ?: return
        if (status.behaviorId.isBlank()) return
        scope.launch {
            runCatching { gateway.acknowledgeCloudUpgrade(status.behaviorId) }
        }
    }

    fun loadRecords(reset: Boolean = true) {
        val gateway = gateway ?: return
        if (recordsJob?.isActive == true) return
        val page = if (reset) 1 else _state.value.recordsPage + 1
        recordsJob = scope.launch {
            _state.value = _state.value.copy(recordsLoading = true, recordsError = null)
            val result = runCatching { gateway.queryUpgradeRecords(page, RECORD_PAGE_SIZE) }
            _state.value = result.fold(
                onSuccess = { recordPage ->
                    _state.value.copy(
                        records = if (reset) {
                            recordPage.records
                        } else {
                            _state.value.records + recordPage.records
                        },
                        recordsLoading = false,
                        recordsError = null,
                        recordsPage = page,
                        recordsHasNext = recordPage.hasNext,
                    )
                },
                onFailure = { error ->
                    _state.value.copy(
                        recordsLoading = false,
                        recordsError = error.message ?: "读取 OTA 升级记录失败",
                    )
                },
            )
        }
    }

    fun install() {
        val gateway = gateway ?: return
        val pkg = _state.value.packageInfo ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            when (pkg.method) {
                OtaMethod.Iot -> {
                    _state.value = _state.value.copy(
                        stage = OtaStage.Installing,
                        message = "正在向云端确认升级任务…",
                    )
                    val confirmed = runCatching { gateway.confirmIotUpgrade(pkg) }
                        .getOrDefault(false)
                    _state.value = if (confirmed) {
                        _state.value.copy(
                            stage = OtaStage.Submitted,
                            progress = 0f,
                            message = "云端升级已提交，请在车辆或原 App 查看升级进度",
                        )
                    } else {
                        OtaFlow.reduce(
                            _state.value,
                            OtaEvent.Failed("云端升级确认失败，请检查网络或任务状态"),
                        )
                    }
                    if (confirmed) {
                        startCloudStatusPolling()
                    }
                    return@launch
                }
                OtaMethod.Wifi, OtaMethod.WifiDvr, OtaMethod.Ble -> Unit
            }
            val beforeVersion = _state.value.currentVersion
            val startedAt = timestampTextProvider()
            _state.value = OtaFlow.reduce(_state.value, OtaEvent.InstallStarted)
            val result = runCatching {
                gateway.install(pkg) { progress, stage ->
                    _state.value = OtaFlow.reduce(
                        _state.value,
                        OtaEvent.Progress(progress, stage),
                    )
                }
            }
            val success = result.getOrDefault(false)
            _state.value = if (success) {
                OtaFlow.reduce(_state.value, OtaEvent.Completed)
            } else {
                OtaFlow.reduce(
                    _state.value,
                    OtaEvent.Failed(
                        result.exceptionOrNull()?.message
                            ?: "升级失败，请检查车辆连接后重试",
                    ),
                )
            }
            val endedAt = timestampTextProvider()
            scope.launch {
                runCatching { gateway.uploadFirmwareInfo(pkg, success) }
                runCatching {
                    gateway.addUpgradeLog(
                        OtaUpgradeLogEntry(
                            taskId = pkg.taskId,
                            strategyId = pkg.strategyId,
                            firmwareName = pkg.typeName,
                            firmwareCode = pkg.firmwareCode
                                .ifBlank { pkg.firmwareModuleCode },
                            beforeVersion = beforeVersion,
                            afterVersion = if (success) pkg.version else beforeVersion,
                            startTime = startedAt,
                            endTime = endedAt,
                            upgradeResult = if (success) 0 else 1,
                            failureReason = if (success) {
                                ""
                            } else {
                                result.exceptionOrNull()?.message.orEmpty()
                            },
                        ),
                    )
                }
                if (success) {
                    runCatching { gateway.queryFirmwareVersions() }
                        .getOrNull()
                        ?.let {
                            _state.value = OtaFlow.reduce(
                                _state.value,
                                OtaEvent.FirmwareVersionsLoaded(it),
                            )
                        }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value.canCancel) {
            _state.value = OtaFlow.reduce(_state.value, OtaEvent.Cancelled)
        }
    }

    fun dispose() {
        job?.cancel()
        job = null
        recordsJob?.cancel()
        recordsJob = null
        cloudJob?.cancel()
        cloudJob = null
        cloudPollJob?.cancel()
        cloudPollJob = null
    }

    private companion object {
        const val RECORD_PAGE_SIZE = 50
        // 原 OtaPollingSource：立即查一次，间隔约 60 秒，整轮窗口约 1 分钟。
        const val CLOUD_STATUS_POLL_MS = 60_000L
        const val CLOUD_STATUS_POLL_ATTEMPTS = 2
    }
}

private fun firmwareVersionSummary(versions: List<OtaFirmwareVersion>): String =
    versions
        .filter { it.currentVersion.isNotBlank() }
        .joinToString(" · ") { "${it.label} ${it.currentVersion}" }
