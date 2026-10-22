package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.carsetting.CarSettingItem
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.carsetting.CustomSoundController
import top.ghhccghk.yadea.carsetting.CustomSoundCategory
import top.ghhccghk.yadea.carsetting.CustomSoundResource
import top.ghhccghk.yadea.vehicle.ContactType
import top.ghhccghk.yadea.vehicle.EmergencyContactController

/** 车辆设置页。业务状态由 [CarSettingsController] 提供，页面只负责语义渲染。 */
@Composable
fun CarSettingsScreen(
    controller: CarSettingsController,
    contentPadding: PaddingValues,
    emergencyContactController: EmergencyContactController? = null,
    onOpenLostMode: (() -> Unit)? = null,
    customSoundController: CustomSoundController? = null,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val contactState = emergencyContactController?.state?.collectAsState()?.value
    val customSoundState = customSoundController?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    val dimensions = AppDesign.dimensions
    var confirmDisable by remember { mutableStateOf<CarSettingItem?>(null) }
    var confirmBraceDependency by remember { mutableStateOf<CarSettingItem?>(null) }
    var customizeItem by remember { mutableStateOf<CarSettingItem?>(null) }
    var customizeDraft by remember(customizeItem?.id) {
        mutableStateOf(
            (customizeItem?.kind as? CarSettingKind.Customize)
                ?: CarSettingKind.Customize(),
        )
    }
    var detailItem by remember { mutableStateOf<CarSettingItem?>(null) }
    var showCustomSounds by remember { mutableStateOf(false) }
    var selectedSoundCategory by remember { mutableStateOf<CustomSoundCategory?>(null) }
    var deleteSoundTarget by remember { mutableStateOf<CustomSoundResource?>(null) }
    var renameSoundTarget by remember { mutableStateOf<CustomSoundResource?>(null) }
    var renameSoundText by remember { mutableStateOf("") }
    var newSoundName by remember { mutableStateOf("") }
    var newSoundUrl by remember { mutableStateOf("") }
    var showEmergencyContacts by remember { mutableStateOf(false) }
    var ownerName by remember { mutableStateOf("") }
    var ownerMobile by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyMobile by remember { mutableStateOf("") }

    LaunchedEffect(contactState?.contacts) {
        contactState?.contacts?.let { contacts ->
            ownerName = contacts.ownerName
            ownerMobile = contacts.ownerMobile
            emergencyName = contacts.emergencyName
            emergencyMobile = contacts.emergencyMobile
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车辆设置",
                subtitle = if (controller.cloudAvailable) {
                    "设置项、分组和可用权限来自云端车辆配置"
                } else {
                    "云端设置服务未接入"
                },
            )
        }
        item {
            AppOutlinedButton(
                onClick = { scope.launch { controller.refresh() } },
                modifier = Modifier.fillMaxWidth(),
                enabled = controller.cloudAvailable && !state.loading,
            ) {
                AppText(
                    if (state.loading) "正在加载…" else "刷新云端设置",
                    style = AppDesign.typography.button,
                )
            }
        }
        if (state.loading) {
            item {
                AppSection {
                    AppEmptyState("正在从云端加载当前车辆的设置项…")
                }
            }
        }
        if (state.message != null) {
            item {
                AppSection {
                    AppText(
                        text = state.message.orEmpty(),
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.error,
                        modifier = Modifier.padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                    )
                }
            }
        }
        val grouped = state.items
            .groupBy { it.group }
            .entries
            .sortedWith(compareBy({ it.key.sort }, { it.key.title }))
        val customSoundEntryId = state.items
            .firstOrNull { it.configId == "41" || it.configId == "88" }
            ?.id
        for ((group, items) in grouped) {
            if (items.isEmpty()) continue
            item {
                AppSection(title = group.title) {
                    items.sortedWith(compareBy({ it.sort }, { it.title })).forEach { item ->
                        CarSettingRow(
                            item = item,
                            enabled = controller.available &&
                                item.writable &&
                                item.id !in state.pendingIds,
                            pending = item.id in state.pendingIds,
                            onOpenDetail = {
                                if (item.kind is CarSettingKind.Customize) {
                                    customizeItem = item
                                } else if (item.configId == "8" && onOpenLostMode != null) {
                                    onOpenLostMode()
                                } else {
                                    detailItem = item
                                }
                            },
                            onUpdate = { transform ->
                                val next = transform(item.kind)
                                if (next is CarSettingKind.Toggle &&
                                    !next.checked &&
                                    item.configId in SAFETY_CONFIRM_IDS
                                ) {
                                    confirmDisable = item
                                } else if (next is CarSettingKind.Toggle &&
                                    next.checked &&
                                    item.configId == "39"
                                ) {
                                    val braceOn = state.items.any {
                                        it.configId == "2" &&
                                            (it.kind as? CarSettingKind.Toggle)?.checked == true
                                    }
                                    if (braceOn) {
                                        scope.launch {
                                            controller.update(item.id, transform)
                                        }
                                    } else {
                                        confirmBraceDependency = item
                                    }
                                } else {
                                    scope.launch { controller.update(item.id, transform) }
                                }
                            },
                        )
                        if (item.configId in setOf("24", "85") &&
                            emergencyContactController != null
                        ) {
                            AppActionPreference(
                                title = "编辑车主与紧急联系人",
                                summary = "车主和紧急联系人分别保存；开启提醒前需要至少填写紧急联系人",
                                value = "编辑",
                                onClick = {
                                    emergencyContactController.clearMessage()
                                    emergencyContactController.load()
                                    showEmergencyContacts = true
                                },
                            )
                        }
                        if (item.id == customSoundEntryId && customSoundController != null) {
                            AppActionPreference(
                                title = "管理自定义音效",
                                summary = "查看云端音效列表、应用音效、重命名或删除",
                                value = "打开",
                                onClick = {
                                    customSoundController.clearMessage()
                                    customSoundController.refresh()
                                    selectedSoundCategory = null
                                    showCustomSounds = true
                                },
                            )
                        }
                    }
                }
            }
        }
        if (!state.loading && state.items.isEmpty()) {
            item {
                AppSection {
                    AppEmptyState(
                        if (controller.cloudAvailable) {
                            "暂无可用的云端设置项。请确认车辆已绑定且账号有权限。"
                        } else {
                            "登录并绑定车辆后，设置项会从云端加载。"
                        },
                    )
                }
            }
        }
    }

    val safetyTarget = confirmDisable
    AppAlertDialog(
        show = safetyTarget != null,
        title = "关闭${safetyTarget?.title.orEmpty()}",
        message = "该功能属于安全辅助，关闭后可能影响车辆稳定性。确定要关闭吗？",
        onConfirm = {
            confirmDisable = null
            safetyTarget?.let { item ->
                scope.launch {
                    controller.update(item.id) { current ->
                        (current as? CarSettingKind.Toggle)?.copy(checked = false) ?: current
                    }
                }
            }
        },
        onDismissRequest = { confirmDisable = null },
    )

    val braceTarget = confirmBraceDependency
    AppAlertDialog(
        show = braceTarget != null,
        title = "开启边撑自动关机",
        message = "该功能依赖边撑感应。开启时会同时开启边撑感应，是否继续？",
        onConfirm = {
            confirmBraceDependency = null
            braceTarget?.let { item ->
                scope.launch {
                    val brace = state.items.firstOrNull { it.configId == "2" }
                    val braceOn =
                        (brace?.kind as? CarSettingKind.Toggle)?.checked == true
                    if (brace != null && !braceOn) {
                        val enabled = controller.update(brace.id) { current ->
                            (current as? CarSettingKind.Toggle)?.copy(checked = true) ?: current
                        }
                        if (!enabled) return@launch
                    }
                    controller.update(item.id) { current ->
                        (current as? CarSettingKind.Toggle)?.copy(checked = true) ?: current
                    }
                }
            }
        },
        onDismissRequest = { confirmBraceDependency = null },
    )

    val detailTarget = detailItem
    AppAlertDialog(
        show = detailTarget != null,
        title = detailTarget?.title.orEmpty(),
        message = buildString {
            append(detailTarget?.summary.orEmpty())
            append("\n\n")
            append(
                "该功能在原 App 中通过独立子页面完成，当前版本尚未确认完整的云端写入或 BLE 参数，因此保持只读，避免向车辆发送不确定命令。",
            )
        },
        onDismissRequest = { detailItem = null },
        confirmText = "知道了",
        onConfirm = { detailItem = null },
    )

    val customizeTarget = customizeItem
    AppBottomSheet(
        show = customizeTarget != null,
        onDismissRequest = { customizeItem = null },
        title = "FREE 自由模式参数",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
        ) {
            AppText(
                text = "以上参数切换到 FREE 模式后生效，保存时会一次性下发到车辆。",
                style = AppDesign.typography.summary,
                color = AppDesign.colors.onSurfaceVariant,
            )
            AppPopupPreference(
                title = "油门灵敏度",
                summary = "改变转把响应的灵敏度",
                options = listOf("迅猛", "平稳", "缓慢"),
                selectedIndex = customizeDraft.throttleLevel,
                onSelectedIndexChange = {
                    customizeDraft = customizeDraft.copy(throttleLevel = it)
                },
            )
            AppPopupPreference(
                title = "能量回收档位",
                summary = "松转把时的能量回收强度",
                options = listOf("弱", "中", "强"),
                selectedIndex = customizeDraft.energyLevel,
                onSelectedIndexChange = {
                    customizeDraft = customizeDraft.copy(energyLevel = it)
                },
            )
            AppSwitchPreference(
                title = "能量回收",
                summary = "关闭后 FREE 模式不进行能量回收",
                checked = customizeDraft.energyState,
                onCheckedChange = {
                    customizeDraft = customizeDraft.copy(energyState = it)
                },
            )
            AppSwitchPreference(
                title = "TCS 车身稳定",
                summary = "FREE 模式下保持车身稳定辅助",
                checked = customizeDraft.tcs,
                onCheckedChange = {
                    customizeDraft = customizeDraft.copy(tcs = it)
                },
            )
            AppOutlinedButton(
                onClick = {
                    val target = customizeTarget ?: return@AppOutlinedButton
                    scope.launch {
                        val saved = controller.update(target.id) { customizeDraft }
                        if (saved) customizeItem = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = customizeTarget != null &&
                    customizeTarget.id !in state.pendingIds,
            ) {
                AppText(
                    if (customizeTarget?.id in state.pendingIds) "保存中…" else "保存参数",
                    style = AppDesign.typography.button,
                )
            }
        }
    }

    if (customSoundController != null) {
        val soundState = customSoundState
        val soundBusy = soundState?.busy == true
        AppBottomSheet(
            show = showCustomSounds,
            onDismissRequest = {
                showCustomSounds = false
                selectedSoundCategory = null
                renameSoundTarget = null
                customSoundController.clearMessage()
            },
            title = "自定义音效",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                if (soundState?.loading == true) {
                    AppText(
                        text = "正在加载云端音效…",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
                AppSwitchPreference(
                    title = "自定义音效开关",
                    summary = "关闭后车辆使用默认开机音效",
                    checked = soundState?.enabled == true,
                    onCheckedChange = { customSoundController.setEnabled(it) },
                    enabled = !soundBusy,
                )
                soundState?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    AppText(
                        text = message,
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
                val activeCategory = selectedSoundCategory?.let { selected ->
                    soundState?.categories
                        ?.firstOrNull { it.sonConfId == selected.sonConfId }
                        ?: selected
                }
                if (activeCategory == null) {
                    val remaining = soundState?.weeklyRemaining
                    val limit = soundState?.weeklyLimit
                    AppText(
                        text = if (remaining != null && limit != null) {
                            "一周可设置${limit}次，剩余${remaining}次"
                        } else {
                            "正在查询本周可设置次数…"
                        },
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                    val categories = soundState?.categories.orEmpty()
                    if (soundState?.loading != true && categories.isEmpty()) {
                        AppEmptyState("暂无可配置的自定义音效分类")
                    }
                    categories.forEach { category ->
                        AppListRow(
                            title = category.name,
                            summary = "点击选择或更换音效",
                            value = category.selectedName.ifBlank { "未设置" },
                            onClick = { selectedSoundCategory = category },
                        )
                    }
                } else {
                    AppActionPreference(
                        title = "返回自定义音效",
                        summary = "返回音效分类列表",
                        value = "返回",
                        onClick = {
                            selectedSoundCategory = null
                            renameSoundTarget = null
                        },
                    )
                    AppText(
                        text = activeCategory.name,
                        style = AppDesign.typography.itemTitle,
                    )
                val renameTarget = renameSoundTarget
                if (renameTarget != null) {
                    AppText(
                        text = "重命名「${renameTarget.name}」",
                        style = AppDesign.typography.itemTitle,
                    )
                    AppTextField(
                        value = renameSoundText,
                        onValueChange = { renameSoundText = it.take(30) },
                        label = "音效名称",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !soundBusy,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        AppOutlinedButton(
                            onClick = { renameSoundTarget = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText("取消", style = AppDesign.typography.button)
                        }
                        AppOutlinedButton(
                            onClick = {
                                customSoundController.rename(renameTarget, renameSoundText)
                                renameSoundTarget = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = renameSoundText.isNotBlank() && !soundBusy,
                        ) {
                            AppText("保存名称", style = AppDesign.typography.button)
                        }
                    }
                } else {
                    val resources = soundState?.resources.orEmpty()
                    if (soundState?.loading != true && resources.isEmpty()) {
                        AppEmptyState("云端暂无用户自定义音效")
                    }
                    resources.forEach { resource ->
                        val selected = resource.id == activeCategory.selectedResourceId
                        AppListRow(
                            title = resource.name,
                            summary = if (selected) {
                                "当前车辆正在使用"
                            } else {
                                "点击应用到车辆"
                            },
                            value = if (selected) "使用中" else "应用",
                            onClick = {
                                if (!selected) {
                                    customSoundController.apply(activeCategory, resource)
                                }
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                        ) {
                            AppOutlinedButton(
                                onClick = {
                                    renameSoundTarget = resource
                                    renameSoundText = resource.name
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !soundBusy,
                            ) {
                                AppText("重命名", style = AppDesign.typography.button)
                            }
                            AppOutlinedButton(
                                onClick = { deleteSoundTarget = resource },
                                modifier = Modifier.weight(1f),
                                enabled = !soundBusy,
                            ) {
                                AppText("删除", style = AppDesign.typography.button)
                            }
                        }
                    }
                    AppText(
                        text = "添加已有音频链接",
                        style = AppDesign.typography.itemTitle,
                    )
                    AppTextField(
                        value = newSoundName,
                        onValueChange = { newSoundName = it.take(30) },
                        label = "音效名称",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !soundBusy,
                    )
                    AppTextField(
                        value = newSoundUrl,
                        onValueChange = { newSoundUrl = it },
                        label = "音频资源 URL",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !soundBusy,
                    )
                    AppOutlinedButton(
                        onClick = {
                            customSoundController.addFromUrl(newSoundName, newSoundUrl)
                            newSoundName = ""
                            newSoundUrl = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newSoundName.isNotBlank() &&
                            newSoundUrl.isNotBlank() &&
                            !soundBusy,
                    ) {
                        AppText("添加音效", style = AppDesign.typography.button)
                    }
                }
                }
            }
        }

        val deleteTarget = deleteSoundTarget
        AppAlertDialog(
            show = deleteTarget != null,
            title = "删除自定义音效",
            message = "确定删除「${deleteTarget?.name.orEmpty()}」吗？删除后无法恢复。",
            onConfirm = {
                deleteSoundTarget = null
                deleteTarget?.let { customSoundController.delete(it) }
            },
            onDismissRequest = { deleteSoundTarget = null },
        )
    }

    if (emergencyContactController != null) {
        AppBottomSheet(
            show = showEmergencyContacts,
            onDismissRequest = { showEmergencyContacts = false },
            title = "车主与紧急联系人",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                if (contactState?.loading == true) {
                    AppText(
                        text = "正在加载联系人…",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
                AppText("车主", style = AppDesign.typography.itemTitle)
                AppTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = "车主姓名",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = contactState?.savingType == null && contactState?.loading != true,
                )
                AppTextField(
                    value = ownerMobile,
                    onValueChange = { ownerMobile = it.filter(Char::isDigit).take(11) },
                    label = "车主手机号",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = contactState?.savingType == null && contactState?.loading != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                AppOutlinedButton(
                    onClick = {
                        emergencyContactController.save(
                            type = ContactType.Owner,
                            name = ownerName,
                            mobile = ownerMobile,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ownerName.isNotBlank() &&
                        ownerMobile.isNotBlank() &&
                        contactState?.savingType == null,
                ) {
                    AppText(
                        if (contactState?.savingType == ContactType.Owner) "保存中…" else "保存车主",
                        style = AppDesign.typography.button,
                    )
                }

                AppText("紧急联系人", style = AppDesign.typography.itemTitle)
                AppTextField(
                    value = emergencyName,
                    onValueChange = { emergencyName = it },
                    label = "联系人姓名",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = contactState?.savingType == null && contactState?.loading != true,
                )
                AppTextField(
                    value = emergencyMobile,
                    onValueChange = { emergencyMobile = it.filter(Char::isDigit).take(11) },
                    label = "联系人手机号",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = contactState?.savingType == null && contactState?.loading != true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                AppButton(
                    onClick = {
                        emergencyContactController.save(
                            type = ContactType.Emergency,
                            name = emergencyName,
                            mobile = emergencyMobile,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = emergencyName.isNotBlank() &&
                        emergencyMobile.isNotBlank() &&
                        contactState?.savingType == null,
                ) {
                    AppText(
                        if (contactState?.savingType == ContactType.Emergency) {
                            "保存中…"
                        } else {
                            "保存紧急联系人"
                        },
                        style = AppDesign.typography.button,
                    )
                }
                contactState?.message?.let { message ->
                    AppText(
                        text = message,
                        style = AppDesign.typography.summary,
                        color = if (contactState.success) {
                            AppDesign.colors.primary
                        } else {
                            AppDesign.colors.error
                        },
                    )
                }
            }
        }
    }
}

private val SAFETY_CONFIRM_IDS = setOf("37", "38")

@Composable
private fun CarSettingRow(
    item: CarSettingItem,
    enabled: Boolean,
    pending: Boolean,
    onOpenDetail: () -> Unit,
    onUpdate: ((CarSettingKind) -> CarSettingKind) -> Unit,
) {
    val summary = buildString {
        if (pending) {
            append("正在保存…")
        } else {
            append(item.summary)
            append(" · ")
            append(item.channel.label)
            if (!item.writable) append(" · 只读")
        }
    }
    when (val kind = item.kind) {
        is CarSettingKind.Toggle -> AppSwitchPreference(
            checked = kind.checked,
            onCheckedChange = { checked ->
                onUpdate { (it as? CarSettingKind.Toggle)?.copy(checked = checked) ?: it }
            },
            title = item.title,
            summary = summary,
            enabled = enabled,
        )
        is CarSettingKind.Choice -> AppPopupPreference(
            title = item.title,
            summary = summary,
            options = kind.options,
            selectedIndex = kind.selectedIndex,
            onSelectedIndexChange = { index ->
                onUpdate { (it as? CarSettingKind.Choice)?.copy(selectedIndex = index) ?: it }
            },
            enabled = enabled,
        )
        is CarSettingKind.Slider -> {
            var draft by remember(item.id, kind.value) { mutableStateOf(kind.value) }
            val minNonZero = kind.minimumNonZero
            AppSliderPreference(
                title = item.title,
                summary = summary,
                value = draft,
                onValueChange = { raw ->
                    draft = if (minNonZero != null && raw > 0f && raw < minNonZero) {
                        if (raw < minNonZero / 2f) 0f else minNonZero
                    } else {
                        raw
                    }
                },
                onValueChangeFinished = {
                    onUpdate { (it as? CarSettingKind.Slider)?.copy(value = draft) ?: it }
                },
                valueRange = kind.range,
                steps = kind.steps,
                valueText = if (minNonZero != null && draft == 0f) {
                    "不限速"
                } else {
                    formatSettingValue(draft, kind.unit)
                },
                enabled = enabled,
            )
        }
        is CarSettingKind.Info -> AppListRow(
            title = item.title,
            summary = item.summary,
            value = kind.value,
            onClick = onOpenDetail,
        )
        is CarSettingKind.Customize -> AppListRow(
            title = item.title,
            summary = item.summary,
            value = "设置",
            onClick = onOpenDetail,
        )
        is CarSettingKind.Entrance -> AppListRow(
            title = item.title,
            summary = item.summary,
            value = kind.hint,
            onClick = onOpenDetail,
        )
    }
}

private fun formatSettingValue(value: Float, unit: String): String {
    val text = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
    return if (unit.isBlank()) text else "$text$unit"
}
