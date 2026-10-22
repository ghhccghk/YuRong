package top.ghhccghk.yadea.carsetting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** `resource/queryResourceList` 返回的一条用户音效资源。 */
data class CustomSoundResource(
    val id: String,
    val name: String,
    val url: String = "",
    val isSelected: Boolean = false,
    val source: Int = 0,
)

/** 自定义音效列表、当前选中项以及应用时需要的云端配置字段。 */
data class CustomSoundState(
    val enabled: Boolean = false,
    val resources: List<CustomSoundResource> = emptyList(),
    val selectedResourceId: String = "",
    val parentConfId: String = "",
    val parentConfName: String = "自定义音效",
    val sonConfId: String = "",
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
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

    val available: Boolean
        get() = gateway != null

    fun refresh() {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank()) {
            _state.value = _state.value.copy(
                loading = false,
                message = "登录并绑定车辆后才能管理自定义音效",
            )
            return
        }
        scope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val loaded = runCatching {
                active.loadCustomSoundState(vin, modelIdProvider())
            }.getOrNull()
            _state.value = if (loaded == null) {
                _state.value.copy(
                    loading = false,
                    message = "自定义音效列表加载失败",
                )
            } else {
                loaded.copy(loading = false, message = null)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank() || _state.value.busy) return
        val previous = _state.value
        _state.value = previous.copy(enabled = enabled, busy = true, message = null)
        scope.launch {
            val success = runCatching {
                active.setCustomSoundEnabled(vin, enabled)
            }.getOrDefault(false)
            _state.value = if (success) {
                _state.value.copy(busy = false, message = null)
            } else {
                previous.copy(busy = false, message = "自定义音效开关设置失败")
            }
        }
    }

    fun apply(resource: CustomSoundResource) {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank() || _state.value.busy) return
        val current = _state.value
        _state.value = current.copy(busy = true, message = "正在应用到车辆…")
        scope.launch {
            val success = runCatching {
                active.applyCustomSound(vin, current, resource)
            }.getOrDefault(false)
            _state.value = if (success) {
                current.copy(
                    enabled = true,
                    selectedResourceId = resource.id,
                    resources = current.resources.map {
                        it.copy(isSelected = it.id == resource.id)
                    },
                    busy = false,
                    message = "已应用「${resource.name}」",
                )
            } else {
                current.copy(busy = false, message = "音效应用失败，请稍后重试")
            }
        }
    }

    fun delete(resource: CustomSoundResource) {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank() || _state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val success = runCatching {
                active.deleteCustomSound(vin, resource.id)
            }.getOrDefault(false)
            if (success) {
                _state.value = _state.value.copy(
                    resources = _state.value.resources.filterNot { it.id == resource.id },
                    busy = false,
                    message = "已删除「${resource.name}」",
                )
            } else {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "删除音效失败",
                )
            }
        }
    }

    fun rename(resource: CustomSoundResource, name: String) {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        val newName = name.trim()
        if (vin.isBlank() || newName.isBlank() || _state.value.busy) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val success = runCatching {
                active.renameCustomSound(vin, resource.id, newName)
            }.getOrDefault(false)
            _state.value = if (success) {
                _state.value.copy(
                    resources = _state.value.resources.map {
                        if (it.id == resource.id) it.copy(name = newName) else it
                    },
                    busy = false,
                    message = "已重命名",
                )
            } else {
                _state.value.copy(busy = false, message = "重命名失败")
            }
        }
    }

    fun addFromUrl(name: String, url: String) {
        val active = gateway ?: return
        val vin = vinProvider().trim()
        val resourceName = name.trim()
        val resourceUrl = url.trim()
        if (vin.isBlank() || resourceName.isBlank() || resourceUrl.isBlank()) return
        scope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val success = runCatching {
                active.addCustomSound(vin, modelIdProvider(), resourceName, resourceUrl)
            }.getOrDefault(false)
            if (success) {
                refresh()
            } else {
                _state.value = _state.value.copy(busy = false, message = "添加音效失败")
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
