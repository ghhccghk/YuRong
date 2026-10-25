package top.ghhccghk.yadea.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import top.ghhccghk.yadea.instrumentskin.InstrumentImagePicker
import top.ghhccghk.yadea.instrumentskin.InstrumentSkinGenerator
import top.ghhccghk.yadea.instrumentskin.InstrumentSkinPreset
import top.ghhccghk.yadea.instrumentskin.InstrumentTransferSession
import top.ghhccghk.yadea.instrumentskin.loadInstrumentSkinReferencePack
import top.ghhccghk.yadea.instrumentskin.prepareInstrumentWallpaper
import top.ghhccghk.yadea.network.TcpTransport
import top.ghhccghk.yadea.network.createTcpTransport
import top.ghhccghk.yadea.toEpochMillis

enum class InstrumentThemeOperation {
    Wallpaper,
    Skin,
}

data class InstrumentThemeState(
    val targetHost: String = "192.168.7.1",
    val wallpaperSource: String? = null,
    val portraitSource: String? = null,
    val backgroundSource: String? = null,
    val preset: InstrumentSkinPreset = InstrumentSkinPreset.PortraitOnly,
    val operation: InstrumentThemeOperation? = null,
    val progress: Float? = null,
    val status: String = "待命",
    val error: String? = null,
) {
    val busy: Boolean get() = operation != null
}

/**
 * 仪表壁纸/皮肤的公共状态与控制入口。
 *
 * 协议、图片选择和位图管线都由平台适配；页面只依赖本类的语义状态。
 */
class InstrumentThemeController(
    private val scope: CoroutineScope,
    private val imagePicker: InstrumentImagePicker?,
    private val tcpFactory: () -> TcpTransport = ::createTcpTransport,
    private val skinGenerator: InstrumentSkinGenerator = InstrumentSkinGenerator(),
    private val interactionGuard: () -> String? = { null },
) {
    private val _state = MutableStateFlow(InstrumentThemeState())
    val state: StateFlow<InstrumentThemeState> = _state.asStateFlow()

    val canPickImages: Boolean get() = imagePicker?.canPick == true

    @Volatile
    private var activeTcp: TcpTransport? = null
    private var activeJob: Job? = null
    private var targetWasEdited = false

    fun updateTargetHost(value: String) {
        if (_state.value.busy) return
        targetWasEdited = true
        update { copy(targetHost = value, error = null) }
    }

    fun suggestTargetHost(value: String) {
        val host = value.trim()
        if (host.isBlank() || targetWasEdited || _state.value.busy) return
        update { copy(targetHost = host) }
    }

    fun selectPreset(preset: InstrumentSkinPreset) {
        if (_state.value.busy) return
        update { copy(preset = preset, error = null) }
    }

    fun chooseWallpaper() = chooseImage("壁纸") { source ->
        copy(wallpaperSource = source)
    }

    fun choosePortrait() = chooseImage("小圈图片") { source ->
        copy(portraitSource = source)
    }

    fun chooseBackground() = chooseImage("背景图片") { source ->
        copy(backgroundSource = source)
    }

    fun startWallpaper() {
        val source = _state.value.wallpaperSource
        if (source.isNullOrBlank()) {
            update { copy(error = "请先选择壁纸图片") }
            return
        }
        launchTask(InstrumentThemeOperation.Wallpaper, "开始设置仪表壁纸") {
            val tcp = openTarget()
            val session = createSession(tcp)
            session.uploadWallpaper { display ->
                update {
                    copy(status = "正在按 ${display.width}×${display.height} 转换壁纸")
                }
                prepareInstrumentWallpaper(
                    source = source,
                    width = display.width,
                    height = display.height,
                    format = display.format,
                ) ?: error("壁纸图片读取或转换失败")
            }
        }
    }

    fun startSkin() {
        val current = _state.value
        val preset = current.preset
        if (preset != InstrumentSkinPreset.BackgroundOnly && current.portraitSource.isNullOrBlank()) {
            update { copy(error = "当前预设需要选择小圈图片") }
            return
        }
        if (preset != InstrumentSkinPreset.PortraitOnly && current.backgroundSource.isNullOrBlank()) {
            update { copy(error = "当前预设需要选择背景图片") }
            return
        }
        val portrait = current.portraitSource
        val background = current.backgroundSource

        launchTask(InstrumentThemeOperation.Skin, "开始生成并设置仪表皮肤") {
            val reference = loadInstrumentSkinReferencePack()
                ?: error("皮肤参考包不可用")
            update { copy(status = "正在准备参考皮肤", progress = 0.02f) }
            val pack = withContext(Dispatchers.Default) {
                skinGenerator.generate(
                    referencePack = reference,
                    preset = preset,
                    portraitSource = portrait,
                    backgroundSource = background,
                    onStatus = { message ->
                        update { copy(status = message, progress = 0.35f) }
                    },
                    cancelled = { activeJob?.isActive != true },
                )
            }
            update {
                copy(
                    status = "皮肤包已生成（${pack.size} 字节），正在连接仪表",
                    progress = 0.5f,
                )
            }

            val tcp = openTarget()
            val session = createSession(tcp, progressStart = 0.5f, progressScale = 0.5f)
            session.uploadSkin(pack)
        }
    }

    fun cancelCurrent() {
        activeTcp?.close()
        activeJob?.cancel()
    }

    fun close() {
        activeTcp?.close()
        activeTcp = null
        activeJob?.cancel()
        activeJob = null
    }

    private fun chooseImage(label: String, updateSource: InstrumentThemeState.(String) -> InstrumentThemeState) {
        if (_state.value.busy) return
        val picker = imagePicker
        if (picker == null || !picker.canPick) {
            update { copy(error = "当前平台未提供图片选择器") }
            return
        }
        picker.pick { result ->
            if (result.isNullOrBlank()) {
                update { copy(status = "已取消选择$label") }
            } else {
                update { updateSource(result).copy(status = "已选择$label", error = null) }
            }
        }
    }

    private fun launchTask(
        operation: InstrumentThemeOperation,
        initialStatus: String,
        block: suspend () -> Unit,
    ) {
        if (_state.value.busy) return
        interactionGuard()?.let { reason ->
            update { copy(error = reason, status = "仪表任务未开始") }
            return
        }
        update {
            copy(
                operation = operation,
                progress = null,
                status = initialStatus,
                error = null,
            )
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                update { copy(status = "仪表任务已停止", progress = null) }
            } catch (error: Throwable) {
                update {
                    copy(
                        status = "仪表任务失败",
                        error = error.message?.takeIf { it.isNotBlank() } ?: "未知错误",
                        progress = null,
                    )
                }
            } finally {
                activeTcp?.close()
                activeTcp = null
                activeJob = null
                update { copy(operation = null, progress = null) }
            }
        }
        activeJob = job
        job.start()
    }

    private suspend fun openTarget(): TcpTransport {
        val host = _state.value.targetHost.trim()
        require(host.isNotBlank()) { "仪表 IP 不能为空" }
        val tcp = tcpFactory()
        activeTcp = tcp
        update { copy(status = "正在连接仪表 $host:8880") }
        try {
            tcp.connect(host, INSTRUMENT_PORT, timeoutMs = CONNECT_TIMEOUT_MS)
        } catch (error: Throwable) {
            tcp.close()
            if (activeTcp === tcp) activeTcp = null
            throw error
        }
        return tcp
    }

    private fun createSession(
        tcp: TcpTransport,
        progressStart: Float = 0f,
        progressScale: Float = 1f,
    ): InstrumentTransferSession = InstrumentTransferSession(
        tcp = tcp,
        scope = scope,
        timestampMillis = { getCurrentTime().toEpochMillis() },
        currentTime = ::getCurrentTime,
        onStatus = { message -> update { copy(status = message) } },
        onProgress = { progress ->
            progress?.let {
                update {
                    copy(progress = (progressStart + it.coerceIn(0f, 1f) * progressScale).coerceIn(0f, 1f))
                }
            }
        },
        cancelled = { activeJob?.isActive != true },
    )

    private fun update(transform: InstrumentThemeState.() -> InstrumentThemeState) {
        _state.value = _state.value.transform()
    }

    private companion object {
        const val INSTRUMENT_PORT = 8880
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
