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
)

enum class OtaStage {
    Unavailable,
    Idle,
    Checking,
    Available,
    UpToDate,
    Downloading,
    Verifying,
    Installing,
    Completed,
    Failed,
    Cancelled,
}

data class OtaState(
    val currentVersion: String = "—",
    val stage: OtaStage = OtaStage.Unavailable,
    val packageInfo: OtaPackage? = null,
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
}

sealed interface OtaEvent {
    data class CurrentVersionChanged(val version: String) : OtaEvent
    data object CheckStarted : OtaEvent
    data class UpdateFound(val packageInfo: OtaPackage) : OtaEvent
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
                progress = 0f,
                message = null,
            )
        OtaEvent.UpToDate ->
            state.copy(stage = OtaStage.UpToDate, packageInfo = null, message = "当前已是最新版本")
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
    suspend fun install(
        packageInfo: OtaPackage,
        onProgress: suspend (progress: Float, stage: OtaStage) -> Unit,
    ): Boolean
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
            val result = runCatching { gateway.checkForUpdate(_state.value.currentVersion) }
            _state.value = result.fold(
                onSuccess = { pkg ->
                    if (pkg == null) OtaFlow.reduce(_state.value, OtaEvent.UpToDate)
                    else OtaFlow.reduce(_state.value, OtaEvent.UpdateFound(pkg))
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

    fun install() {
        val gateway = gateway ?: return
        val pkg = _state.value.packageInfo ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = OtaFlow.reduce(_state.value, OtaEvent.InstallStarted)
            val success = runCatching {
                gateway.install(pkg) { progress, stage ->
                    _state.value = OtaFlow.reduce(
                        _state.value,
                        OtaEvent.Progress(progress, stage),
                    )
                }
            }.getOrDefault(false)
            _state.value = if (success) {
                OtaFlow.reduce(_state.value, OtaEvent.Completed)
            } else {
                OtaFlow.reduce(_state.value, OtaEvent.Failed("升级失败，请检查车辆连接后重试"))
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
