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
    val behaviorId: String = "",
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
}

class OtaController(
    private val gateway: OtaGateway?,
    currentVersion: String,
    private val scope: CoroutineScope,
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

    fun updateCurrentVersion(version: String) {
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
                    return@launch
                }
                OtaMethod.Wifi, OtaMethod.WifiDvr, OtaMethod.Ble -> Unit
            }
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
    }
}
