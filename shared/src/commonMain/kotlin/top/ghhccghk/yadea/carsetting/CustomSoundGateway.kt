package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** `resource/queryResourceList` 返回的一条用户音效资源。 */
data class CustomSoundResource(
    val id: String,
    val name: String,
    val url: String = "",
    val isSelected: Boolean = false,
    val source: Int = 0,
)

/** `selectConfigListByVin` 下 `zdyyx001` 的一个子配置，对应官方页面的一个音效分类。 */
data class CustomSoundCategory(
    val sonConfId: String,
    val name: String,
    val selectedResourceId: String = "",
    val selectedName: String = "",
    val sort: Int = 0,
)

enum class CustomSoundOperationStatus { IDLE, PENDING, SUCCESS, FAILED, CANCELLED }
enum class CustomSoundOperation { ENABLE, APPLY, DELETE, RENAME, ADD }
enum class CustomSoundData { RESOURCES, CATEGORIES, SELECTION, ENABLED, COUNT }

data class CustomSoundSelection(val category: CustomSoundCategory, val resource: CustomSoundResource)

/** Internal to this request chain; a stale return is cancellation, never a new vehicle's result. */
internal class StaleCustomSoundRequest : CancellationException("Stale custom sound request")

internal class CustomSoundRequestContext(
    val isCurrent: () -> Boolean,
    val receipt: (Boolean?, Int?) -> Unit,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CustomSoundRequestContext>
}

internal suspend fun checkCustomSoundRequest() {
    val context = currentCoroutineContext()
    context.ensureActive()
    if (context[CustomSoundRequestContext]?.isCurrent?.invoke() == false) throw StaleCustomSoundRequest()
}

internal suspend fun recordCustomSoundReceipt(accepted: Boolean? = null, status: Int? = null) {
    checkCustomSoundRequest()
    currentCoroutineContext()[CustomSoundRequestContext]?.receipt?.invoke(accepted, status)
}

/** 自定义音效列表、当前选中项以及应用时需要的云端配置字段。 */
data class CustomSoundState(
    val enabled: Boolean = false,
    val resources: List<CustomSoundResource> = emptyList(),
    val categories: List<CustomSoundCategory> = emptyList(),
    val weeklyRemaining: Int? = null,
    val weeklyLimit: Int? = null,
    val selectedResourceId: String = "",
    val parentConfId: String = "",
    val parentConfName: String = "自定义音效",
    val sonConfId: String = "",
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val vin: String = "",
    val modelId: Long = 0L,
    val generation: Long = 0L,
    val cloudLoaded: Boolean = false,
    val loadStatus: CustomSoundOperationStatus = CustomSoundOperationStatus.IDLE,
    val operationStatus: CustomSoundOperationStatus = CustomSoundOperationStatus.IDLE,
    val operation: CustomSoundOperation? = null,
    val operationId: Long? = null,
    val draftEnabled: Boolean? = null,
    val submittedEnabled: Boolean? = null,
    val applyingSelection: CustomSoundSelection? = null,
    val submittedSelection: CustomSoundSelection? = null,
    val requestAccepted: Boolean? = null,
    val serverStatus: Int? = null,
    val gatewaySucceeded: Boolean? = null,
    val loadedFields: Set<CustomSoundData> = emptySet(),
    val reconciliationRequired: Boolean = false,
)

/**
 * 自定义音效链路。
 *
 * 原 App 的流程：
 * 1. `resource/queryResourceList` 查询用户资源；
 * 2. `resourceBike/queryResourceBikeList` 查询车辆当前自定义音效配置；
 * 3. `resourcesBike/setResourceBike` 应用资源；
 * 4. `resourcesBike/queryResultSetResourceBike` 轮询结果；
 * 5. `protocol/control-vehicle/user_defined_sound_effect` 开关自定义音效。
 */
interface CustomSoundGateway {
    suspend fun loadCustomSoundState(vin: String, modelId: Long): CustomSoundState?
    suspend fun setCustomSoundEnabled(vin: String, enabled: Boolean): Boolean
    suspend fun applyCustomSound(
        vin: String,
        state: CustomSoundState,
        category: CustomSoundCategory,
        resource: CustomSoundResource,
    ): Boolean
    suspend fun deleteCustomSound(vin: String, resourceId: String): Boolean
    suspend fun renameCustomSound(vin: String, resourceId: String, name: String): Boolean
    suspend fun addCustomSound(
        vin: String,
        modelId: Long,
        name: String,
        resourceUrl: String,
    ): Boolean
}

class CustomSoundController(
    private val gateway: CustomSoundGateway?,
    private val scope: CoroutineScope,
    private val vinProvider: () -> String,
    private val modelIdProvider: () -> Long,
) {
    private val _state = MutableStateFlow(CustomSoundState())
    val state: StateFlow<CustomSoundState> = _state.asStateFlow()

    private data class Request(val vin: String, val modelId: Long, val generation: Long, val id: Long)
    private var sequence = 0L
    private var loadRequest: Request? = null
    private var writeRequest: Request? = null
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var refreshRequested = false
    private var disposed = false
    private val inFlightWrites = mutableMapOf<String, Job>()
    private val reconcileVins = mutableSetOf<String>()

    val available: Boolean get() = gateway != null

    // Called by the profile collector and checked again at every asynchronous boundary.
    fun onVehicleChanged() {
        val vin = vinProvider().trim()
        val modelId = modelIdProvider()
        if (_state.value.vin == vin && _state.value.modelId == modelId) return
        val oldLoad = loadJob
        val oldWrite = writeJob
        if (oldWrite?.isCompleted == false) reconcileVins += _state.value.vin
        loadRequest = null
        writeRequest = null
        loadJob = null
        writeJob = null
        refreshRequested = false
        _state.value = CustomSoundState(
            vin = vin, modelId = modelId, generation = _state.value.generation + 1,
            busy = inFlightWrites[vin]?.isCompleted == false,
            reconciliationRequired = vin in reconcileVins,
        )
        oldLoad?.cancel()
        oldWrite?.cancel()
    }

    private fun current(request: Request, write: Boolean): Boolean {
        onVehicleChanged()
        val state = _state.value
        return !disposed && request.vin == state.vin && request.modelId == state.modelId &&
            request.generation == state.generation && request == (if (write) writeRequest else loadRequest)
    }

    private fun newRequest(): Request = _state.value.let { Request(it.vin, it.modelId, it.generation, ++sequence) }

    fun refresh(): Job? {
        val active = gateway ?: return null
        onVehicleChanged()
        if (disposed) return null
        if (_state.value.vin.isBlank()) {
            _state.value = _state.value.copy(message = "登录并绑定车辆后才能管理自定义音效")
            return null
        }
        if (writeRequest != null || inFlightWrites[_state.value.vin]?.isCompleted == false) {
            refreshRequested = true
            return null
        }
        val request = newRequest()
        val oldJob = loadJob
        loadRequest = request
        oldJob?.cancel()
        _state.value = _state.value.copy(loading = true, loadStatus = CustomSoundOperationStatus.PENDING)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val loaded = withContext(CustomSoundRequestContext({ current(request, false) }, { _, _ -> })) {
                    checkCustomSoundRequest()
                    active.loadCustomSoundState(request.vin, request.modelId).also { checkCustomSoundRequest() }
                }
                if (current(request, false)) {
                    if (loaded == null) {
                        _state.value = _state.value.copy(loadStatus = CustomSoundOperationStatus.FAILED, message = "自定义音效列表加载失败")
                    } else {
                        mergeLoaded(loaded)
                        _state.value = _state.value.copy(loadStatus = CustomSoundOperationStatus.SUCCESS)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (current(request, false)) _state.value = _state.value.copy(loadStatus = CustomSoundOperationStatus.CANCELLED)
                throw cancelled
            } catch (_: Exception) {
                if (current(request, false)) _state.value = _state.value.copy(loadStatus = CustomSoundOperationStatus.FAILED, message = "自定义音效列表加载失败")
            } finally {
                finishLoad(request)
            }
        }
        loadJob = job
        job.invokeOnCompletion { finishLoad(request) }
        job.start()
        return job
    }

    private fun finishLoad(request: Request) {
        if (!current(request, false)) return
        val status = _state.value.loadStatus
        _state.value = _state.value.copy(loading = false, loadStatus = if (status == CustomSoundOperationStatus.PENDING) CustomSoundOperationStatus.CANCELLED else status)
        loadRequest = null
        loadJob = null
    }

    private fun mergeLoaded(loaded: CustomSoundState) {
        val old = _state.value
        val fields = loaded.loadedFields
        val coreLoaded = fields.containsAll(setOf(CustomSoundData.RESOURCES, CustomSoundData.CATEGORIES, CustomSoundData.SELECTION))
        if (coreLoaded) reconcileVins -= old.vin
        _state.value = old.copy(
            enabled = if (CustomSoundData.ENABLED in fields) loaded.enabled else old.enabled,
            resources = if (CustomSoundData.RESOURCES in fields) loaded.resources else old.resources,
            categories = if (CustomSoundData.CATEGORIES in fields) loaded.categories.map { category ->
                if (CustomSoundData.SELECTION in fields) category else {
                    val previous = old.categories.firstOrNull { it.sonConfId == category.sonConfId }
                    category.copy(selectedResourceId = previous?.selectedResourceId.orEmpty(), selectedName = previous?.selectedName.orEmpty())
                }
            } else old.categories,
            weeklyRemaining = if (CustomSoundData.COUNT in fields) loaded.weeklyRemaining else old.weeklyRemaining,
            weeklyLimit = if (CustomSoundData.COUNT in fields) loaded.weeklyLimit else old.weeklyLimit,
            selectedResourceId = if (CustomSoundData.SELECTION in fields) loaded.selectedResourceId else old.selectedResourceId,
            parentConfId = if (CustomSoundData.CATEGORIES in fields) loaded.parentConfId else old.parentConfId,
            parentConfName = if (CustomSoundData.CATEGORIES in fields) loaded.parentConfName else old.parentConfName,
            sonConfId = if (CustomSoundData.CATEGORIES in fields) loaded.sonConfId else old.sonConfId,
            cloudLoaded = fields.isNotEmpty(), loadedFields = fields,
            reconciliationRequired = old.vin in reconcileVins,
        )
    }

    private fun mutate(
        operation: CustomSoundOperation,
        failure: String,
        enabled: Boolean? = null,
        selection: CustomSoundSelection? = null,
        call: suspend (CustomSoundGateway, Request, CustomSoundState) -> Boolean,
        onSuccess: (CustomSoundState) -> CustomSoundState = { it },
    ): Job? {
        val active = gateway ?: return null
        onVehicleChanged()
        if (disposed || _state.value.vin.isBlank() || writeRequest != null || !_state.value.cloudLoaded ||
            inFlightWrites[_state.value.vin]?.isCompleted == false || _state.value.reconciliationRequired) return null
        val initial = _state.value
        val request = newRequest()
        // Serialize mutations; invalidate any older snapshot before the first write suspension.
        val previousLoad = loadJob
        refreshRequested = loadRequest != null
        loadRequest = null
        loadJob = null
        previousLoad?.cancel()
        writeRequest = request
        _state.value = initial.copy(
            loading = false, busy = true, operation = operation, operationId = request.id,
            loadStatus = if (initial.loading) CustomSoundOperationStatus.CANCELLED else initial.loadStatus,
            operationStatus = CustomSoundOperationStatus.PENDING, draftEnabled = enabled,
            submittedEnabled = enabled, applyingSelection = selection, submittedSelection = selection,
            requestAccepted = null, serverStatus = null, gatewaySucceeded = null, message = null,
        )
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val success = withContext(CustomSoundRequestContext(
                    isCurrent = { current(request, true) },
                    receipt = { accepted, status ->
                        if (current(request, true)) _state.value = _state.value.copy(
                            requestAccepted = accepted ?: _state.value.requestAccepted,
                            serverStatus = status ?: _state.value.serverStatus,
                        )
                    },
                )) {
                    checkCustomSoundRequest()
                    call(active, request, initial).also { checkCustomSoundRequest() }
                }
                if (current(request, true)) {
                    _state.value = if (success) {
                        if (operation == CustomSoundOperation.ENABLE || operation == CustomSoundOperation.APPLY) refreshRequested = true
                        onSuccess(_state.value).copy(
                            operationStatus = CustomSoundOperationStatus.SUCCESS, gatewaySucceeded = true,
                            message = if (operation == CustomSoundOperation.ENABLE || operation == CustomSoundOperation.APPLY)
                                "请求链路已返回，车辆状态尚未回读确认" else "云端资源操作已返回成功",
                        )
                    } else {
                        _state.value.copy(operationStatus = CustomSoundOperationStatus.FAILED, gatewaySucceeded = false,
                            submittedEnabled = null, submittedSelection = null, message = failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                if (current(request, true)) markCancelled()
                throw cancelled
            } catch (_: Exception) {
                if (current(request, true)) _state.value = _state.value.copy(
                    operationStatus = CustomSoundOperationStatus.FAILED, gatewaySucceeded = false,
                    submittedEnabled = null, submittedSelection = null, message = failure,
                )
            } finally {
                finishWrite(request)
            }
        }
        writeJob = job
        inFlightWrites[request.vin] = job
        job.invokeOnCompletion {
            finishWrite(request)
            if (inFlightWrites[request.vin] === job) {
                inFlightWrites.remove(request.vin)
                if (_state.value.vin == request.vin && writeRequest == null) _state.value = _state.value.copy(busy = false)
                if (refreshRequested && _state.value.vin == request.vin && !disposed) {
                    refreshRequested = false
                    refresh()
                }
            }
        }
        job.start()
        return job
    }

    private fun markCancelled() {
        reconcileVins += _state.value.vin
        _state.value = _state.value.copy(
            operationStatus = CustomSoundOperationStatus.CANCELLED, reconciliationRequired = true, message = null,
        )
    }

    private fun finishWrite(request: Request) {
        if (!current(request, true)) return
        if (_state.value.operationStatus == CustomSoundOperationStatus.PENDING) markCancelled()
        _state.value = _state.value.copy(busy = false, draftEnabled = null, applyingSelection = null)
        writeRequest = null
        writeJob = null
    }

    fun setEnabled(enabled: Boolean): Job? = mutate(
        CustomSoundOperation.ENABLE, "自定义音效开关设置失败", enabled = enabled,
        call = { active, request, _ -> active.setCustomSoundEnabled(request.vin, enabled) },
    )

    fun apply(category: CustomSoundCategory, resource: CustomSoundResource): Job? {
        onVehicleChanged()
        val state = _state.value
        if (!state.loadedFields.containsAll(setOf(CustomSoundData.CATEGORIES, CustomSoundData.RESOURCES))) return null
        val currentCategory = state.categories.firstOrNull { it.sonConfId == category.sonConfId } ?: return null
        val currentResource = state.resources.firstOrNull { it.id == resource.id } ?: return null
        return mutate(CustomSoundOperation.APPLY, "音效应用失败，请稍后重试",
            selection = CustomSoundSelection(currentCategory, currentResource),
            call = { active, request, initial -> active.applyCustomSound(request.vin, initial, currentCategory, currentResource) })
    }

    fun delete(resource: CustomSoundResource): Job? {
        onVehicleChanged()
        if (CustomSoundData.RESOURCES !in _state.value.loadedFields) return null
        if (_state.value.resources.none { it.id == resource.id }) return null
        return mutate(CustomSoundOperation.DELETE, "删除音效失败",
            call = { active, request, _ -> active.deleteCustomSound(request.vin, resource.id) },
            onSuccess = { it.copy(resources = it.resources.filterNot { sound -> sound.id == resource.id }) })
    }

    fun rename(resource: CustomSoundResource, name: String): Job? {
        onVehicleChanged()
        if (CustomSoundData.RESOURCES !in _state.value.loadedFields) return null
        val newName = name.trim()
        if (newName.isBlank() || _state.value.resources.none { it.id == resource.id }) return null
        return mutate(CustomSoundOperation.RENAME, "重命名失败",
            call = { active, request, _ -> active.renameCustomSound(request.vin, resource.id, newName) },
            onSuccess = { state -> state.copy(
                resources = state.resources.map { if (it.id == resource.id) it.copy(name = newName) else it },
                categories = state.categories.map { if (it.selectedResourceId == resource.id) it.copy(selectedName = newName) else it },
            ) })
    }

    fun addFromUrl(name: String, url: String): Job? {
        val resourceName = name.trim()
        val resourceUrl = url.trim()
        if (resourceName.isBlank() || resourceUrl.isBlank()) return null
        return mutate(CustomSoundOperation.ADD, "添加音效失败",
            call = { active, request, _ ->
                val success = active.addCustomSound(request.vin, request.modelId, resourceName, resourceUrl)
                checkCustomSoundRequest()
                if (success) refreshRequested = true
                success
            })
    }

    fun cancelPending() {
        onVehicleChanged()
        refreshRequested = false
        val oldLoad = loadJob
        val oldWrite = writeJob
        if (loadRequest != null) _state.value = _state.value.copy(loading = false, loadStatus = CustomSoundOperationStatus.CANCELLED)
        if (writeRequest != null) {
            reconcileVins += _state.value.vin
            markCancelled()
            _state.value = _state.value.copy(
                busy = oldWrite?.isCompleted == false, draftEnabled = null, applyingSelection = null,
                reconciliationRequired = true,
            )
        }
        loadRequest = null
        writeRequest = null
        loadJob = null
        writeJob = null
        oldLoad?.cancel()
        oldWrite?.cancel()
    }

    fun dispose() {
        cancelPending()
        disposed = true
    }

    fun clearMessage() {
        onVehicleChanged()
        _state.value = _state.value.copy(message = null)
    }
}
