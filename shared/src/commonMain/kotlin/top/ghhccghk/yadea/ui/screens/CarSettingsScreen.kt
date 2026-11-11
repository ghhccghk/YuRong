package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.ghhccghk.yadea.carsetting.CarSettingChannel
import top.ghhccghk.yadea.carsetting.CarSettingClassification
import top.ghhccghk.yadea.carsetting.CarSettingItem
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.CarSettingItemAccess
import top.ghhccghk.yadea.carsetting.CarSettingValueState
import top.ghhccghk.yadea.carsetting.VehicleSettingValue
import top.ghhccghk.yadea.carsetting.SettingWriteState
import top.ghhccghk.yadea.carsetting.SettingConfirmationState
import top.ghhccghk.yadea.carsetting.CarSettingWriteChannel
import top.ghhccghk.yadea.carsetting.SettingReadbackState
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.carsetting.CarSettingsState
import top.ghhccghk.yadea.carsetting.CustomSoundController
import top.ghhccghk.yadea.carsetting.CustomSoundCategory
import top.ghhccghk.yadea.carsetting.CustomSoundResource
import top.ghhccghk.yadea.carsetting.CustomSoundData
import top.ghhccghk.yadea.carsetting.CustomSoundOperationStatus
import top.ghhccghk.yadea.carsetting.createSoundPreviewPlayer
import top.ghhccghk.yadea.vehicle.ContactType
import top.ghhccghk.yadea.vehicle.EmergencyContactController
import top.ghhccghk.yadea.vehicle.PasswordFlow
import top.ghhccghk.yadea.vehicle.PasswordWriteState
import top.ghhccghk.yadea.vehicle.VehiclePasswordController
import top.ghhccghk.yadea.vehicle.sanitizeContactNameInput

/** 车辆设置页。业务状态由 [CarSettingsController] 提供，页面只负责语义渲染。 */
@Suppress("DEPRECATION")
@Composable
fun CarSettingsScreen(
    controller: CarSettingsController,
    contentPadding: PaddingValues,
    vehicleConnected: Boolean,
    vehiclePowerOn: Boolean?,
    emergencyContactController: EmergencyContactController? = null,
    passwordController: VehiclePasswordController? = null,
    onOpenLostMode: (() -> Unit)? = null,
    customSoundController: CustomSoundController? = null,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val contactState = emergencyContactController?.state?.collectAsState()?.value
    val customSoundState = customSoundController?.state?.collectAsState()?.value
    val passwordState = passwordController?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    val dimensions = AppDesign.dimensions
    val uiResetKey = carSettingsUiSessionKey(controller, state)
    val settingScope = key(uiResetKey) { rememberCoroutineScope() }
    var confirmDisable by remember(uiResetKey) { mutableStateOf<CarSettingItem?>(null) }
    var confirmBraceDependency by remember(uiResetKey) { mutableStateOf<CarSettingItem?>(null) }
    var atmosphereItem by remember(uiResetKey) { mutableStateOf<CarSettingItem?>(null) }
    val atmosphereDraft = atmosphereItem?.let { state.valueState(it.id).editableKind(it.kind) as? CarSettingKind.Atmosphere }
        ?: CarSettingKind.Atmosphere()
    fun editAtmosphere(transform: (CarSettingKind.Atmosphere) -> CarSettingKind.Atmosphere) {
        atmosphereItem?.let { item -> controller.editDraft(item.id) { transform(it as CarSettingKind.Atmosphere) } }
    }
    var showPasswordManager by remember(uiResetKey) { mutableStateOf(false) }
    var passwordFlow by remember(uiResetKey) { mutableStateOf<PasswordFlow?>(null) }
    var passwordDraft by remember(uiResetKey) { mutableStateOf("") }
    var passwordCode by remember(uiResetKey) { mutableStateOf("") }
    var customizeItem by remember(uiResetKey) { mutableStateOf<CarSettingItem?>(null) }
    val customizeDraft = customizeItem?.let { state.valueState(it.id).editableKind(it.kind) as? CarSettingKind.Customize }
        ?: CarSettingKind.Customize()
    val customizeValueKnown = customizeDraft.valueKnown
    fun editCustomize(transform: (CarSettingKind.Customize) -> CarSettingKind.Customize) {
        customizeItem?.let { item -> controller.editDraft(item.id) { transform(it as CarSettingKind.Customize) } }
    }
    var detailItem by remember(uiResetKey) { mutableStateOf<CarSettingItem?>(null) }
    var showLowPowerReminder by remember(uiResetKey) { mutableStateOf(false) }
    var showCustomSounds by remember(uiResetKey) { mutableStateOf(false) }
    var showCustomKeys by remember(uiResetKey) { mutableStateOf(false) }
    var selectedSoundCategory by remember(uiResetKey) { mutableStateOf<CustomSoundCategory?>(null) }
    var deleteSoundTarget by remember(uiResetKey) { mutableStateOf<CustomSoundResource?>(null) }
    var renameSoundTarget by remember(uiResetKey) { mutableStateOf<CustomSoundResource?>(null) }
    var renameSoundText by remember(uiResetKey) { mutableStateOf("") }
    var newSoundName by remember(uiResetKey) { mutableStateOf("") }
    var newSoundUrl by remember(uiResetKey) { mutableStateOf("") }
    var playingSoundId by remember(uiResetKey) { mutableStateOf<String?>(null) }
    val soundPreviewPlayer = remember { createSoundPreviewPlayer() }
    DisposableEffect(customSoundController) {
        onDispose { customSoundController?.cancelPending() }
    }
    LaunchedEffect(customSoundState?.vin, customSoundState?.modelId, customSoundState?.generation) {
        showCustomSounds = false
        selectedSoundCategory = null
        deleteSoundTarget = null
        renameSoundTarget = null
        renameSoundText = ""
        newSoundName = ""
        newSoundUrl = ""
        soundPreviewPlayer.stop()
        playingSoundId = null
    }
    DisposableEffect(soundPreviewPlayer) {
        onDispose { soundPreviewPlayer.release() }
    }
    var showEmergencyContacts by remember(uiResetKey) { mutableStateOf(false) }
    var ownerName by remember(uiResetKey) { mutableStateOf("") }
    var ownerMobile by remember(uiResetKey) { mutableStateOf("") }
    var emergencyName by remember(uiResetKey) { mutableStateOf("") }
    var emergencyMobile by remember(uiResetKey) { mutableStateOf("") }
    var showMissingCloudFeatures by remember(uiResetKey) { mutableStateOf(false) }
    var showChannelHelp by remember(uiResetKey) { mutableStateOf(false) }
    var selectedClassification by remember(uiResetKey) { mutableStateOf(CarSettingClassification.SECURITY) }
    var copiedConfigJson by remember(uiResetKey) { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(uiResetKey, contactState?.contacts?.vin) {
        contactState?.contacts?.takeIf {
            state.vin.isBlank() || it.vin == state.vin
        }?.let { contacts ->
            ownerName = contacts.ownerName
            ownerMobile = contacts.ownerMobile
            emergencyName = contacts.emergencyName
            emergencyMobile = contacts.emergencyMobile
        }
    }
    LaunchedEffect(copiedConfigJson) {
        if (copiedConfigJson) {
            delay(2_000)
            copiedConfigJson = false
        }
    }
    LaunchedEffect(state.missingCloudItems) {
        if (state.missingCloudItems.isEmpty()) {
            showMissingCloudFeatures = false
        }
    }
    val submitSettingUpdate:
        (CarSettingItem, (CarSettingKind) -> CarSettingKind) -> Unit = { item, transform ->
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
            val braceOn = (state.items + state.missingCloudItems).any {
                it.configId == "2" &&
                    (it.kind as? CarSettingKind.Toggle)?.checked == true
            }
            if (braceOn) {
                scope.launch { controller.update(item.id, transform) }
            } else {
                confirmBraceDependency = item
            }
        } else {
            settingScope.launch { controller.update(item.id, transform) }
        }
    }
    val capacitySwitchOn = (state.items + state.missingCloudItems)
        .firstOrNull { it.id == "cloud:60:enable" }
        ?.kind
        .let { (it as? CarSettingKind.Toggle)?.checked == true }
    val settingDisabledReason: (CarSettingItem) -> String? = { item ->
        if (state.loading) {
            "正在刷新设置"
        } else if (item.configId in BLE_POWER_SETTING_IDS &&
            (!vehicleConnected || vehiclePowerOn != true)
        ) {
            "请先连接车辆并开机"
        } else {
            adaptivePushUiDisabledReason(item, state, vehicleConnected, vehiclePowerOn)
        }
    }
    val settingControlEnabled: (CarSettingItem) -> Boolean = { item ->
        val displayedKind = state.valueState(item.id).displayKind() ?: item.kind
        controller.available &&
            CarSettingItemAccess.canWrite(item) &&
            (item.configId !in setOf("4", "58", "61") ||
                (displayedKind is CarSettingKind.Slider && displayedKind.valueKnown && displayedKind.rangeKnown &&
                    (item.kind as? CarSettingKind.Slider)?.rangeKnown == true)) &&
            (item.configId != "58" || (item.kind as? CarSettingKind.Slider)?.speedPresets?.size == 3) &&
            (item.configId !in setOf("55", "92") ||
                (state.items + state.missingCloudItems).none {
                    it.configId in setOf("55", "92") && (it.id in state.pendingIds ||
                        (it.id != item.id && state.valueState(it.id).submittedValue != null))
                }) &&
            item.id !in state.pendingIds &&
            settingDisabledReason(item) == null &&
            (item.id != "cloud:60:level" || capacitySwitchOn)
    }
    val visibleItems = lowPowerReminderVisibleItems(state.items)
    val availableClassifications = CarSettingClassification.entries.filter { classification ->
        visibleItems.any { it.classification == classification } ||
            (showMissingCloudFeatures && state.missingCloudItems.any {
                it.classification == classification
            })
    }
    val activeClassification = selectedClassification
        .takeIf { it in availableClassifications }
        ?: availableClassifications.firstOrNull()
    val selectedItems = activeClassification?.let { classification ->
        visibleItems.filter { it.classification == classification }
    }.orEmpty()
    val selectedMissingCloudItems = activeClassification?.let { classification ->
        state.missingCloudItems.filter { it.classification == classification }
    }.orEmpty()

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
        if (availableClassifications.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    items(
                        items = availableClassifications,
                        key = { it.name },
                    ) { classification ->
                        AppFilterChip(
                            selected = classification == activeClassification,
                            onClick = { selectedClassification = classification },
                            label = classification.label,
                        )
                    }
                }
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
        if (showMissingCloudFeatures && selectedMissingCloudItems.isNotEmpty()) {
            item {
                AppSection(title = "云端未下发功能") {
                    AppText(
                        text = "这些功能没有由云端下发。带控件的项目属于实验性设置，可能不受当前车型支持。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                    )
                    selectedMissingCloudItems.forEach { item ->
                        CarSettingRow(
                            item = item,
                            valueState = state.valueState(item.id),
                            enabled = settingControlEnabled(item),
                            pending = item.id in state.pendingIds,
                            disabledReason = settingDisabledReason(item),
                            onOpenDetail = {
                                detailItem = item
                            },
                            onUpdate = { transform ->
                                submitSettingUpdate(item, transform)
                            },
                        )
                    }
                }
            }
        }
        val grouped = selectedItems
            .groupBy { it.group }
            .entries
            .sortedWith(compareBy({ it.key.sort }, { it.key.title }))
        for ((group, items) in grouped) {
            if (items.isEmpty()) continue
            item {
                AppSection(title = group.title) {
                    items
                        .filterNot {
                            it.id == "cloud:1005:short" ||
                                it.id == "cloud:1005:long" ||
                                it.id == "cloud:60"
                        }
                        .sortedWith(compareBy({ it.sort }, { it.title }))
                        .forEach { item ->
                        CarSettingRow(
                            item = item,
                            valueState = state.valueState(item.id),
                            enabled = settingControlEnabled(item),
                            pending = item.id in state.pendingIds,
                            disabledReason = settingDisabledReason(item),
                            onOpenDetail = {
                                if (item.kind is CarSettingKind.LowPowerInfo || item.kind is CarSettingKind.LowPowerPhone) {
                                    showLowPowerReminder = true
                                } else if (item.kind is CarSettingKind.Atmosphere) {
                                    atmosphereItem = item
                                } else if (item.kind is CarSettingKind.Customize) {
                                    customizeItem = item
                                } else if (item.configId == "8" && onOpenLostMode != null) {
                                    onOpenLostMode()
                                } else if (item.configId == "41" || item.configId == "88") {
                                    if (customSoundController != null) {
                                        customSoundController.clearMessage()
                                        customSoundController.refresh()
                                        selectedSoundCategory = null
                                        showCustomSounds = true
                                    }
                                } else if (item.configId == "1005") {
                                    showCustomKeys = true
                                } else {
                                    detailItem = item
                                }
                            },
                            onUpdate = { transform ->
                                submitSettingUpdate(item, transform)
                            },
                        )
                        if (item.id == "cloud:25" && passwordController != null) {
                            AppActionPreference(
                                title = "密码管理",
                                summary = "设置、修改或忘记密码；敏感内容不会写入日志或持久化",
                                value = "进入",
                                onClick = {
                                    passwordController.refresh()
                                    passwordFlow = null
                                    passwordDraft = ""
                                    passwordCode = ""
                                    showPasswordManager = true
                                },
                            )
                        }
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
                    }
                }
            }
        }
        item {
            AppSection(title = "设置通道与诊断") {
                AppActionPreference(
                    title = "本机、蓝牙、车联网",
                    summary = "本机＝App 内处理 · 蓝牙＝连接车辆后生效",
                    value = "说明",
                    onClick = { showChannelHelp = true },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppOutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(state.rawConfigJson))
                            copiedConfigJson = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.rawConfigJson.isNotBlank(),
                    ) {
                        AppText(
                            if (copiedConfigJson) {
                                "已复制到剪贴板"
                            } else {
                                "复制当前配置 JSON"
                            },
                            style = AppDesign.typography.button,
                        )
                    }
                    AppText(
                        text = "复制云端本次返回的原始配置 JSON，便于核对未下发功能。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
                AppSwitchPreference(
                    checked = showMissingCloudFeatures,
                    onCheckedChange = { showMissingCloudFeatures = it },
                    title = "显示云端未下发功能",
                    summary = when {
                        state.loading -> "正在加载云端配置"
                        state.missingCloudItems.isEmpty() -> "本地已知功能均已由云端下发"
                        else -> "显示 ${state.missingCloudItems.size} 个本地已知但本次未下发的功能"
                    },
                    enabled = state.missingCloudItems.isNotEmpty(),
                )
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
                        (current as? CarSettingKind.Toggle)?.copy(checked = false, valueKnown = true) ?: current
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
                    val brace = (state.items + state.missingCloudItems)
                        .firstOrNull { it.configId == "2" }
                    val braceOn =
                        (brace?.kind as? CarSettingKind.Toggle)?.checked == true
                    if (brace != null && !braceOn) {
                        val enabled = controller.update(brace.id) { current ->
                            (current as? CarSettingKind.Toggle)?.copy(checked = true, valueKnown = true) ?: current
                        }
                        if (!enabled) return@launch
                    }
                    controller.update(item.id) { current ->
                        (current as? CarSettingKind.Toggle)?.copy(checked = true, valueKnown = true) ?: current
                    }
                }
            }
        },
        onDismissRequest = { confirmBraceDependency = null },
    )

    val sliderTarget = detailItem?.id?.let { id ->
        (state.items + state.missingCloudItems).firstOrNull { it.id == id && it.kind is CarSettingKind.Slider }
    }
    AppBottomSheet(
        show = sliderTarget != null,
        onDismissRequest = { detailItem?.let { controller.cancelDraft(it.id) }; detailItem = null },
        title = sliderTarget?.title.orEmpty(),
    ) {
        val slider = sliderTarget?.kind as? CarSettingKind.Slider
        if (sliderTarget != null && slider != null) {
            val draft = (state.valueState(sliderTarget.id).editableKind(slider) as CarSettingKind.Slider).value
            if (sliderTarget.configId == "58") {
                val presets = slider.speedPresets
                val position = if (state.valueState(sliderTarget.id).displayKind() != null) {
                    speedPresetPosition(slider, draft)
                } else null
                if (presets.size != 3) {
                    AppText("车型档位范围未知，暂不可编辑或保存", style = AppDesign.typography.summary)
                } else if (position == null) {
                    // Do not place an unmatched/unknown observation at a fabricated tick.
                    AppText("当前值未对应预设档位，请明确选择目标档位", style = AppDesign.typography.summary)
                    presets.forEachIndexed { index, speed ->
                        AppActionPreference(
                            title = if (speed == 0) "不限速" else "$speed km/h",
                            enabled = settingControlEnabled(sliderTarget),
                            onClick = {
                                val target = speedPresetTarget(slider, index.toFloat()) ?: return@AppActionPreference
                                if (controller.editDraft(sliderTarget.id) { target }) {
                                    settingScope.launch { controller.submitDraft(sliderTarget.id) }
                                }
                            },
                        )
                    }
                } else {
                    AppSliderPreference(
                        title = sliderTarget.title,
                        summary = settingDescriptionSummary(sliderTarget),
                        value = position,
                        valueRange = 0f..2f,
                        steps = 1,
                        valueText = settingSliderValueLabel(state.valueState(sliderTarget.id).displayKind() as? CarSettingKind.Slider),
                        enabled = settingControlEnabled(sliderTarget),
                        onValueChange = { raw ->
                            val target = speedPresetTarget(slider, raw) ?: return@AppSliderPreference
                            controller.editDraft(sliderTarget.id) { target }
                        },
                        onValueChangeFinished = { settingScope.launch { controller.submitDraft(sliderTarget.id) } },
                    )
                }
            } else if (sliderTarget.configId in setOf("4", "61") && !slider.rangeKnown) {
                AppText("车型速度范围未知，暂不可编辑或保存", style = AppDesign.typography.summary)
            } else if (sliderTarget.configId in setOf("4", "61") &&
                state.valueState(sliderTarget.id).displayKind() == null
            ) {
                AppText("状态未知，等待有效配置或车辆回读", style = AppDesign.typography.summary)
            } else AppSliderPreference(
                title = sliderTarget.title,
                summary = settingDescriptionSummary(sliderTarget),
                value = draft,
                onValueChange = { raw ->
                    if (isAuditedThreeTickSlider(sliderTarget) && !settingControlEnabled(sliderTarget)) return@AppSliderPreference
                    val uiValue = settingSliderDraftValue(sliderTarget, raw) ?: return@AppSliderPreference
                    val minimum = slider.minimumNonZero
                    val next = if (minimum != null && uiValue > 0f && uiValue < minimum) {
                        if (uiValue < minimum / 2f) 0f else minimum
                    } else {
                        uiValue
                    }
                    controller.editDraft(sliderTarget.id) { slider.copy(value = next, valueKnown = true) }
                },
                onValueChangeFinished = {
                    if (isAuditedThreeTickSlider(sliderTarget) && !settingControlEnabled(sliderTarget)) return@AppSliderPreference
                    settingScope.launch { controller.submitDraft(sliderTarget.id) }
                },
                valueRange = slider.range,
                steps = slider.steps,
                valueText = settingSliderValueLabel(state.valueState(sliderTarget.id).displayKind() as? CarSettingKind.Slider),
                enabled = settingControlEnabled(sliderTarget),
            )
            AppText(settingValueSourceSummary(state.valueState(sliderTarget.id)), style = AppDesign.typography.summary)
            if (isAuditedThreeTickSlider(sliderTarget)) {
                AppButton(
                    enabled = settingControlEnabled(sliderTarget),
                    onClick = {
                        val next = settingSliderDraftValue(sliderTarget, draft)
                        if (next != null && settingControlEnabled(sliderTarget) &&
                            controller.editDraft(sliderTarget.id) { slider.copy(value = next, valueKnown = true) }
                        ) {
                            settingScope.launch { controller.submitDraft(sliderTarget.id) }
                        }
                    },
                ) { AppText("保存") }
            }
            AppOutlinedButton(onClick = { controller.cancelDraft(sliderTarget.id); detailItem = null }) {
                AppText("取消编辑")
            }
        }
    }

    val optionTarget = detailItem?.takeIf { it.kind is CarSettingKind.Choice }
    AppBottomSheet(
        show = optionTarget != null,
        onDismissRequest = { optionTarget?.let { controller.cancelDraft(it.id) }; detailItem = null },
        title = optionTarget?.title.orEmpty(),
    ) {
        if (optionTarget != null) {
            AppText(settingValueSourceSummary(state.valueState(optionTarget.id)), style = AppDesign.typography.summary)
            val options = (optionTarget.kind as CarSettingKind.Choice).options
            options.forEachIndexed { index, label ->
                AppActionPreference(title = label, enabled = settingControlEnabled(optionTarget), onClick = {
                    submitSettingUpdate(optionTarget) { kind ->
                        when (kind) {
                            is CarSettingKind.Choice -> kind.copy(selectedIndex = index, valueKnown = true)
                            else -> kind
                        }
                    }
                    detailItem = null
                })
            }
        }
    }

    if (showLowPowerReminder) key(uiResetKey) {
        LowPowerReminderSheet(
            controller = controller,
            state = state,
            onDismissRequest = { showLowPowerReminder = false },
            controlEnabled = settingControlEnabled,
        )
    }

    val detailTarget = detailItem
    AppAlertDialog(
        show = detailTarget != null && detailTarget.kind !is CarSettingKind.Slider &&
            detailTarget.kind !is CarSettingKind.Choice && detailTarget.kind !is CarSettingKind.Toggle,
        title = detailTarget?.title.orEmpty(),
        message = buildString {
            append(detailTarget?.let(::settingDescriptionSummary).orEmpty())
            append("\n\n")
            append(
                "该功能在原 App 中通过独立子页面完成，当前版本尚未确认完整的云端写入或 BLE 参数，因此保持只读，避免向车辆发送不确定命令。",
            )
        },
        onDismissRequest = { detailItem = null },
        confirmText = "知道了",
        onConfirm = { detailItem = null },
    )

    val atmosphereTarget = atmosphereItem
    AppBottomSheet(
        show = atmosphereTarget != null,
        onDismissRequest = { atmosphereItem?.let { controller.cancelDraft(it.id) }; atmosphereItem = null },
        title = "氛围灯设置",
    ) {
        if (atmosphereTarget != null) key(uiResetKey, atmosphereTarget.id) {
            val valueState = state.valueState(atmosphereTarget.id)
            val customization = (state.items + state.missingCloudItems).firstOrNull { it.id == "cloud:73" }
            AtmosphereSettingsEditor(
                draft = atmosphereDraft,
                hasSelection = valueState.displayKind() is CarSettingKind.Atmosphere,
                enabled = settingControlEnabled(atmosphereTarget) && customization?.id !in state.pendingIds,
                pending = atmosphereTarget.id in state.pendingIds,
                status = carSettingSummary(
                    atmosphereTarget.copy(summary = ""), valueState.copy(error = null),
                    atmosphereTarget.id in state.pendingIds, settingDisabledReason(atmosphereTarget),
                ),
                error = valueState.error,
                onEdit = ::editAtmosphere,
                onApply = {
                    settingScope.launch {
                        controller.update(atmosphereTarget.id) { atmosphereDraft.copy(valueKnown = false) }
                    }
                },
                customizationSwitch = {
                    customization?.let { item ->
                        val switchState = state.valueState(item.id)
                        val toggle = switchState.displayKind() as? CarSettingKind.Toggle
                        AppSwitchPreference(
                            title = "氛围灯自定义",
                            summary = when {
                                item.id in state.pendingIds -> "正在提交…"
                                switchState.error != null -> switchState.error.orEmpty()
                                toggle == null -> "状态未知，等待配置或回读"
                                else -> "车辆当前值：${vehicleValueLabel(switchState)}"
                            },
                            checked = toggle?.checked ?: false,
                            enabled = toggle != null && settingControlEnabled(item) && atmosphereTarget.id !in state.pendingIds,
                            onCheckedChange = { checked ->
                                settingSwitchTarget(switchState, checked)?.let { target ->
                                    submitSettingUpdate(item) { target }
                                }
                            },
                        )
                    }
                },
            )
        }
    }
    val customizeTarget = customizeItem
    AppBottomSheet(
        show = customizeTarget != null,
        onDismissRequest = { customizeItem?.let { controller.cancelDraft(it.id) }; customizeItem = null },
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
            if (!customizeValueKnown) {
                AppText(
                    text = "尚未读取到完整车辆配置，避免覆盖其他参数，暂不可编辑或保存。",
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                )
            }
            if (customizeValueKnown) AppPopupPreference(
                title = "油门灵敏度",
                summary = "改变转把响应的灵敏度",
                options = listOf("迅猛", "平稳", "缓慢"),
                selectedIndex = customizeDraft.throttleLevel!!,
                onSelectedIndexChange = {
                    editCustomize { draft -> draft.copy(throttleLevel = it) }
                },
                enabled = customizeValueKnown && customizeTarget?.let(settingControlEnabled) == true,
            )
            if (customizeValueKnown) AppPopupPreference(
                title = "能量回收档位",
                summary = "松转把时的能量回收强度",
                options = listOf("弱", "中", "强"),
                selectedIndex = customizeDraft.energyLevel!!,
                onSelectedIndexChange = {
                    editCustomize { draft -> draft.copy(energyLevel = it) }
                },
                enabled = customizeValueKnown && customizeTarget?.let(settingControlEnabled) == true,
            )
            if (customizeValueKnown) AppSwitchPreference(
                title = "能量回收",
                summary = "关闭后 FREE 模式不进行能量回收",
                checked = customizeDraft.energyState!!,
                onCheckedChange = {
                    editCustomize { draft -> draft.copy(energyState = it) }
                },
                enabled = customizeValueKnown && customizeTarget?.let(settingControlEnabled) == true,
            )
            if (customizeValueKnown) AppSwitchPreference(
                title = "TCS 车身稳定",
                summary = "FREE 模式下保持车身稳定辅助",
                checked = customizeDraft.tcs!!,
                onCheckedChange = {
                    editCustomize { draft -> draft.copy(tcs = it) }
                },
                enabled = customizeValueKnown && customizeTarget?.let(settingControlEnabled) == true,
            )
            AppOutlinedButton(
                onClick = {
                    val target = customizeTarget ?: return@AppOutlinedButton
                    settingScope.launch {
                        val saved = controller.submitDraft(target.id)
                        if (saved) customizeItem = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = customizeTarget != null &&
                    customizeValueKnown &&
                    settingControlEnabled(customizeTarget) &&
                    state.valueState(customizeTarget.id).draftValue != null &&
                    customizeTarget.id !in state.pendingIds,
            ) {
                AppText(
                    if (customizeTarget?.id in state.pendingIds) "保存中…" else "保存参数",
                    style = AppDesign.typography.button,
                )
            }
        }
    }

    AppBottomSheet(
        show = showCustomKeys,
        onDismissRequest = { showCustomKeys = false },
        title = "自定义按键",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
        ) {
            val keyItems = state.items.filter {
                it.id == "cloud:1005:short" || it.id == "cloud:1005:long"
            }
            if (keyItems.isEmpty()) {
                AppEmptyState("当前车辆没有下发自定义按键配置")
            } else {
                keyItems.forEach { item ->
                    CarSettingRow(
                        item = item,
                        valueState = state.valueState(item.id),
                        enabled = settingControlEnabled(item),
                        pending = item.id in state.pendingIds,
                        disabledReason = settingDisabledReason(item),
                        onOpenDetail = { showCustomKeys = false; detailItem = item },
                        onUpdate = { submitSettingUpdate(item, it) },
                    )
                }
            }
        }
    }

    if (passwordController != null) {
        AppBottomSheet(
            show = showPasswordManager,
            onDismissRequest = {
                showPasswordManager = false
                passwordFlow = null
                passwordDraft = ""
                passwordCode = ""
            },
            title = "密码解锁管理",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppText(
                    text = buildString {
                        append("当前密码：")
                        append(
                            when (passwordState?.hasPassword) {
                                true -> "已设置"
                                false -> "未设置"
                                null -> "未知"
                            },
                        )
                        append(" · 开关状态：")
                        append(
                            when (passwordState?.enabledStatus) {
                                1 -> "开启"
                                0 -> "关闭"
                                else -> "未知"
                            },
                        )
                    },
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                )
                passwordState?.message?.let { message ->
                    AppText(
                        text = message,
                        style = AppDesign.typography.summary,
                        color = if (passwordState.success) {
                            AppDesign.colors.primary
                        } else {
                            AppDesign.colors.error
                        },
                    )
                }
                val currentFlow = passwordFlow
                if (currentFlow == null) {
                    AppOutlinedButton(
                        onClick = {
                            passwordFlow = PasswordFlow.Set
                            passwordController.begin(PasswordFlow.Set)
                            passwordDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passwordState?.busy != true,
                    ) { AppText("设置密码", style = AppDesign.typography.button) }
                    AppOutlinedButton(
                        onClick = {
                            passwordFlow = PasswordFlow.Change
                            passwordController.begin(PasswordFlow.Change)
                            passwordDraft = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passwordState?.busy != true,
                    ) { AppText("修改密码", style = AppDesign.typography.button) }
                    AppOutlinedButton(
                        onClick = {
                            passwordFlow = PasswordFlow.Forget
                            passwordController.begin(PasswordFlow.Forget)
                            passwordDraft = ""
                            passwordCode = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = passwordState?.busy != true,
                    ) { AppText("忘记密码", style = AppDesign.typography.button) }
                } else {
                    if (currentFlow == PasswordFlow.Change && passwordState?.smsVerified != true) {
                        AppTextField(
                            value = passwordDraft,
                            onValueChange = { passwordDraft = it.filter(Char::isDigit).take(4) },
                            label = "原密码",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = passwordState?.busy != true,
                        )
                        AppOutlinedButton(
                            onClick = { passwordController.verifyCurrentPassword(passwordDraft) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = passwordDraft.length == 4 && passwordState?.busy != true,
                        ) { AppText("验证原密码", style = AppDesign.typography.button) }
                    } else if (currentFlow == PasswordFlow.Forget && passwordState?.smsVerified != true) {
                        AppTextField(
                            value = passwordCode,
                            onValueChange = { passwordCode = it.filter(Char::isDigit).take(6) },
                            label = "短信验证码",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = passwordState?.busy != true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                        ) {
                            AppOutlinedButton(
                                onClick = { passwordController.sendSmsCode() },
                                modifier = Modifier.weight(1f),
                                enabled = passwordState?.busy != true,
                            ) { AppText("发送验证码", style = AppDesign.typography.button) }
                            AppOutlinedButton(
                                onClick = { passwordController.verifySmsCode(passwordCode) },
                                modifier = Modifier.weight(1f),
                                enabled = passwordCode.isNotEmpty() && passwordState?.busy != true,
                            ) { AppText("校验验证码", style = AppDesign.typography.button) }
                        }
                    } else {
                        AppTextField(
                            value = passwordDraft,
                            onValueChange = { passwordDraft = it.filter(Char::isDigit).take(4) },
                            label = "新密码（4 位数字）",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = passwordState?.busy != true,
                        )
                        AppOutlinedButton(
                            onClick = { passwordController.submitPassword(passwordDraft) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = passwordDraft.length == 4 && passwordState?.busy != true,
                        ) {
                            AppText(
                                if (passwordState?.busy == true) "提交中…" else "保存密码",
                                style = AppDesign.typography.button,
                            )
                        }
                    }
                    if (passwordState?.writeState == PasswordWriteState.VEHICLE_CONFIRMED) {
                        AppText(
                            text = "车辆已确认密码设置",
                            style = AppDesign.typography.summary,
                            color = AppDesign.colors.primary,
                        )
                    }
                }
            }
        }
    }
    if (customSoundController != null) {
        val soundState = customSoundState
        val soundBusy = soundState?.busy == true || soundState?.loading == true || soundState?.reconciliationRequired == true
        AppBottomSheet(
            show = showCustomSounds,
            onDismissRequest = {
                showCustomSounds = false
                selectedSoundCategory = null
                renameSoundTarget = null
                deleteSoundTarget = null
                customSoundController.cancelPending()
                customSoundController.clearMessage()
                soundPreviewPlayer.stop()
                playingSoundId = null
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
                val localEnabled = soundState?.draftEnabled ?: soundState?.submittedEnabled?.takeIf {
                    soundState.operationStatus == CustomSoundOperationStatus.SUCCESS
                }
                val enabledKnown = CustomSoundData.ENABLED in soundState?.loadedFields.orEmpty()
                if (enabledKnown || localEnabled != null) AppSwitchPreference(
                    title = "自定义音效开关",
                    summary = if (enabledKnown && soundState != null) {
                        "云端记录：${if (soundState.enabled) "开启" else "关闭"}；车辆回读：UNKNOWN"
                    } else "云端状态：UNKNOWN；车辆回读：UNKNOWN",
                    checked = localEnabled ?: (soundState?.enabled == true),
                    onCheckedChange = { customSoundController.setEnabled(it) },
                    enabled = !soundBusy && soundState?.cloudLoaded == true,
                )
                else {
                    AppText("自定义音效开关：UNKNOWN", style = AppDesign.typography.summary)
                    AppActionPreference(title = "开启自定义音效", enabled = !soundBusy && soundState?.cloudLoaded == true,
                        onClick = { customSoundController.setEnabled(true) })
                    AppActionPreference(title = "关闭自定义音效", enabled = !soundBusy && soundState?.cloudLoaded == true,
                        onClick = { customSoundController.setEnabled(false) })
                }
                if (soundState?.reconciliationRequired == true) {
                    AppText("上次请求已取消等待，需重新查询云端状态", style = AppDesign.typography.summary)
                }
                soundState?.submittedEnabled?.let { target ->
                    AppText("本次选择：${if (target) "开启" else "关闭"}；${soundState.operationStatus}", style = AppDesign.typography.summary)
                }
                soundState?.submittedSelection?.let { target ->
                    AppText("本次选择：${target.category.name} / ${target.resource.name}；${soundState.operationStatus}", style = AppDesign.typography.summary)
                }
                if (soundState?.requestAccepted != null || soundState?.serverStatus != null) {
                    AppText("云端接受：${soundState.requestAccepted ?: "UNKNOWN"}；服务端结果码：${soundState.serverStatus ?: "UNKNOWN"}；车辆未确认", style = AppDesign.typography.summary)
                }
                soundState?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    AppText(
                        text = message,
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
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
                        summary = "云端配置记录，非车辆实时回读",
                        value = if (CustomSoundData.SELECTION in soundState?.loadedFields.orEmpty()) category.selectedName.ifBlank { "未设置" } else "UNKNOWN",
                        onClick = { selectedSoundCategory = category },
                    )
                }
            }
        }

        val pickerCategory = selectedSoundCategory?.let { selected ->
            soundState?.categories
                ?.firstOrNull { it.sonConfId == selected.sonConfId }
                ?: selected
        }
        AppBottomSheet(
            show = showCustomSounds && pickerCategory != null,
            onDismissRequest = {
                selectedSoundCategory = null
                renameSoundTarget = null
                soundPreviewPlayer.stop()
                playingSoundId = null
            },
            title = pickerCategory?.name ?: "选择音效",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                pickerCategory?.let { category ->
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
                            AppEmptyState("云端暂无可用音效")
                        }
                        resources.forEach { resource ->
                            val localSelection = soundState?.submittedSelection?.takeIf {
                                soundState.operationStatus == CustomSoundOperationStatus.SUCCESS && it.category.sonConfId == category.sonConfId
                            }
                            val selected = if (localSelection != null) resource.id == localSelection.resource.id
                                else CustomSoundData.SELECTION in soundState?.loadedFields.orEmpty() && resource.id == category.selectedResourceId
                            AppListRow(
                                title = resource.name,
                                summary = if (selected) {
                                    if (localSelection != null) "本次选择，车辆未确认" else "当前分类云端记录"
                                } else {
                                    "点击应用到「${category.name}」"
                                },
                                value = if (selected) { if (localSelection != null) "本次选择" else "云端已选" } else "应用",
                                onClick = {
                                    if (!selected && !soundBusy) {
                                        soundPreviewPlayer.stop()
                                        playingSoundId = null
                                        customSoundController.apply(category, resource)
                                    }
                                },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    dimensions.controlSpacing,
                                ),
                            ) {
                                if (soundPreviewPlayer.isAvailable) {
                                    AppOutlinedButton(
                                        onClick = {
                                            if (playingSoundId == resource.id) {
                                                soundPreviewPlayer.stop()
                                                playingSoundId = null
                                            } else {
                                                playingSoundId = resource.id
                                                soundPreviewPlayer.play(resource.url) {
                                                    playingSoundId = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !soundBusy && resource.url.isNotBlank(),
                                    ) {
                                        AppText(
                                            if (playingSoundId == resource.id) "停止" else "试听",
                                            style = AppDesign.typography.button,
                                        )
                                    }
                                }
                                AppOutlinedButton(
                                    onClick = {
                                        soundPreviewPlayer.stop()
                                        playingSoundId = null
                                        renameSoundTarget = resource
                                        renameSoundText = resource.name
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !soundBusy,
                                ) {
                                    AppText("重命名", style = AppDesign.typography.button)
                                }
                                AppOutlinedButton(
                                    onClick = {
                                        soundPreviewPlayer.stop()
                                        playingSoundId = null
                                        deleteSoundTarget = resource
                                    },
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
                    onValueChange = { ownerName = sanitizeContactNameInput(it) },
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
                    enabled = contactState?.savingType == null &&
                        contactState?.loading != true,
                ) {
                    AppText(
                        if (contactState?.savingType == ContactType.Owner) "保存中…" else "保存车主",
                        style = AppDesign.typography.button,
                    )
                }

                AppText("紧急联系人", style = AppDesign.typography.itemTitle)
                AppTextField(
                    value = emergencyName,
                    onValueChange = { emergencyName = sanitizeContactNameInput(it) },
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
                    enabled = contactState?.savingType == null &&
                        contactState?.loading != true,
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

    AppAlertDialog(
        show = showChannelHelp,
        title = "设置通道说明",
        message = CarSettingChannel.entries.joinToString("\n\n") {
            "${it.label}：${it.description}"
        },
        dismissText = null,
        confirmText = "知道了",
        onDismissRequest = { showChannelHelp = false },
        onConfirm = { showChannelHelp = false },
    )
}

internal data class CarSettingsUiResetKey(
    val vin: String,
    val loadGeneration: Long,
    val loadedFromCloud: Boolean,
)

/** Keep cloud metadata intact, but present confId 9/82 through one issued entry. */
internal fun lowPowerReminderVisibleItems(items: List<CarSettingItem>): List<CarSettingItem> =
    if (items.any { it.id == "cloud:9" }) items.filterNot { it.id == "cloud:82" } else items

internal fun carSettingsUiSessionKey(controller: CarSettingsController, state: CarSettingsState) =
    controller to carSettingsUiResetKey(state)

internal fun carSettingsUiResetKey(state: CarSettingsState): CarSettingsUiResetKey =
    CarSettingsUiResetKey(
        vin = state.vin,
        loadGeneration = state.loadGeneration,
        loadedFromCloud = state.loadedFromCloud,
    )

private val SAFETY_CONFIRM_IDS = setOf("37", "38")
private val BLE_POWER_SETTING_IDS = setOf("37", "38")

internal fun settingSwitchTarget(state: CarSettingValueState, targetChecked: Boolean): CarSettingKind.Toggle? =
    (state.displayKind() as? CarSettingKind.Toggle)?.copy(checked = targetChecked, valueKnown = true)

internal fun isAuditedThreeTickSlider(item: CarSettingItem): Boolean =
    item.id == "cloud:14" || item.id == "cloud:64:level" || item.id == "cloud:89:level"

internal fun speedPresetPosition(slider: CarSettingKind.Slider, raw: Float): Float? =
    if (slider.speedPresets.size != 3 || !raw.isFinite()) null
    else slider.speedPresets.indexOfFirst { it.toFloat() == raw }.takeIf { it >= 0 }?.toFloat()

internal fun speedPresetTarget(slider: CarSettingKind.Slider, position: Float): CarSettingKind.Slider? {
    if (!position.isFinite() || position !in 0f..2f || slider.speedPresets.size != 3) return null
    return slider.copy(value = slider.speedPresets[position.roundToInt()].toFloat(), valueKnown = true)
}

/** Normalize UI input only; observed values and command validation remain unchanged. */
internal fun settingSliderDraftValue(item: CarSettingItem, raw: Float): Float? {
    if (item.configId in setOf("4", "61")) {
        val slider = item.kind as? CarSettingKind.Slider ?: return null
        if (!slider.rangeKnown || !raw.isFinite() || raw !in slider.range) return null
        return raw.roundToInt().toFloat()
    }
    if (!isAuditedThreeTickSlider(item)) return raw
    val slider = item.kind as? CarSettingKind.Slider ?: return null
    val expectedRange = if (item.configId == "14") 0f..100f else 0f..2f
    if (slider.range != expectedRange || slider.steps != 1 || !raw.isFinite() || raw !in expectedRange) return null
    val interval = (expectedRange.endInclusive - expectedRange.start) / 2f
    return expectedRange.start + ((raw - expectedRange.start) / interval).roundToInt() * interval
}

internal fun adaptivePushUiDisabledReason(
    item: CarSettingItem,
    state: CarSettingsState,
    connected: Boolean,
    powerOn: Boolean?,
): String? {
    if (item.configId !in setOf("64", "89")) return null
    if (!connected || powerOn != true) return "请先连接车辆并开机"
    if (!item.id.endsWith(":adaptive") && !item.id.endsWith(":level")) return null
    val mainId = "cloud:${item.configId}"
    if (mainId in state.pendingIds) return "请先等待辅助推行开关提交完成"
    val main = state.valueState(mainId)
    val observed = main.vehicleValue as? VehicleSettingValue.Known
    if (observed == null || !observed.live || observed.vin != state.vin ||
        main.readbackState != SettingReadbackState.VALID
    ) return "请先等待辅助推行状态回读"
    return if ((observed.kind as? CarSettingKind.Toggle)?.checked == true) null else "请先开启辅助推行"
}

internal fun settingDescriptionSummary(item: CarSettingItem): String =
    item.summary.takeUnless { it.trim() == "云端车辆设置项" }.orEmpty()

internal fun carSettingSummary(
    item: CarSettingItem,
    valueState: CarSettingValueState,
    pending: Boolean,
    disabledReason: String?,
): String = buildString {
        if (pending) {
            append("正在保存…")
        } else {
            append(settingDescriptionSummary(item))
            val channel = when (item.channel) {
                CarSettingChannel.IOT -> null
                CarSettingChannel.BOTH -> CarSettingChannel.BLE.label
                else -> item.channel.label
            }
            channel?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            disabledReason?.let {
                append(" · ")
                append(it)
            }
            if (!item.writable) append(" · 只读")
        }
        append(" · ")
        append(settingValueSourceSummary(valueState))
        valueState.draftValue?.let { append(" · 草稿：${settingKindLabel(it)}") }
        valueState.submittedValue?.let { append(" · 本次选择：${settingKindLabel(it)}") }
        append(when (valueState.writeState) {
            SettingWriteState.PREPARING -> " · 准备提交"
            SettingWriteState.SUBMITTING -> " · 提交中"
            SettingWriteState.CLOUD_ACCEPTED -> " · 云端已接受"
            SettingWriteState.LOCAL_UPDATED -> if (valueState.writeChannel == CarSettingWriteChannel.BLE)
                " · 蓝牙通道已接收，未代表车辆执行" else " · 本次选择已提交，通道未知"
            SettingWriteState.FAILED -> " · 写入失败"
            SettingWriteState.CANCELLED -> " · 本次等待已取消，不代表远端操作撤销"
            SettingWriteState.IDLE -> ""
        })
        append(when (valueState.confirmationState) {
            SettingConfirmationState.READBACK_MATCHED -> " · 回读一致"
            SettingConfirmationState.READBACK_MISMATCH -> " · 回读与目标不一致"
            SettingConfirmationState.CONFIRMED_BY_RESULT -> " · 结果接口已确认"
            SettingConfirmationState.TIMED_OUT -> " · 确认超时"
            SettingConfirmationState.WAITING_READBACK -> " · 等待车辆回读（能力未知时保持未确认）"
            else -> ""
        })
        valueState.error?.let { append(" · $it") }
}.removePrefix(" · ")

@Composable
private fun CarSettingRow(
    item: CarSettingItem,
    valueState: CarSettingValueState,
    enabled: Boolean,
    pending: Boolean,
    disabledReason: String?,
    onOpenDetail: () -> Unit,
    onUpdate: ((CarSettingKind) -> CarSettingKind) -> Unit,
) {
    val summary = carSettingSummary(item, valueState, pending, disabledReason)
    val observed = valueState.displayKind()
    when (val kind = observed ?: item.kind) {
        is CarSettingKind.LowPowerInfo, is CarSettingKind.LowPowerPhone -> AppListRow(
            title = "低电量提醒", summary = summary, value = "设置", onClick = onOpenDetail,
        )
        is CarSettingKind.Toggle -> AppSwitchPreference(
            checked = (observed as? CarSettingKind.Toggle)?.checked ?: false,
            onCheckedChange = { checked ->
                settingSwitchTarget(valueState, checked)?.let { target -> onUpdate { target } }
            },
            title = item.title,
            summary = if (observed == null) "$summary · 状态未知，等待配置或回读" else summary,
            enabled = enabled && observed is CarSettingKind.Toggle,
        )
        is CarSettingKind.Choice -> if (observed == null) AppActionPreference(
            title = item.title, summary = summary, value = "未知", enabled = enabled, onClick = onOpenDetail,
        ) else AppPopupPreference(
            title = item.title,
            summary = summary,
            options = kind.options,
            selectedIndex = kind.selectedIndex,
            onSelectedIndexChange = { index ->
                onUpdate { (it as? CarSettingKind.Choice)?.copy(selectedIndex = index, valueKnown = true) ?: it }
            },
            enabled = enabled,
        )
        is CarSettingKind.Slider -> AppActionPreference(
            title = item.title,
            summary = summary,
            value = settingSliderValueLabel(observed as? CarSettingKind.Slider),
            enabled = enabled,
            onClick = onOpenDetail,
        )
        is CarSettingKind.Atmosphere -> AppListRow(
            title = item.title,
            summary = summary,
            value = "设置",
            onClick = onOpenDetail,
        )
        is CarSettingKind.Info -> AppListRow(
            title = item.title,
            summary = settingDescriptionSummary(item),
            value = kind.value,
            onClick = onOpenDetail,
        )
        is CarSettingKind.Customize -> AppListRow(
            title = item.title,
            summary = settingDescriptionSummary(item),
            value = "设置",
            onClick = onOpenDetail,
        )
        is CarSettingKind.Entrance -> AppListRow(
            title = item.title,
            summary = settingDescriptionSummary(item),
            value = kind.hint,
            onClick = onOpenDetail,
        )
    }
}

internal fun settingValueSourceSummary(state: CarSettingValueState): String = buildList {
    if (state.configuredValue == null || state.vehicleValue is VehicleSettingValue.Known) {
        add("车辆当前值：${vehicleValueLabel(state)}")
    }
    state.cachedValue?.let { add("本地缓存：${settingKindLabel(it.kind)}（历史值）") }
    state.configuredValue?.let { add("云端配置：${settingKindLabel(it)}（非车辆回读）") }
}.joinToString(" · ")

internal fun settingSliderValueLabel(kind: CarSettingKind.Slider?): String = when {
    kind == null -> "状态未知"
    kind.minimumNonZero != null && kind.value == 0f -> "不限速"
    else -> formatSettingValue(kind.value, kind.unit)
}

private fun vehicleValueLabel(state: CarSettingValueState): String {
    val known = state.vehicleValue as? VehicleSettingValue.Known ?: return "UNKNOWN"
    return if (known.live && state.readbackState == SettingReadbackState.VALID) settingKindLabel(known.kind)
        else "UNKNOWN（历史回读：${settingKindLabel(known.kind)}）"
}

private fun settingKindLabel(kind: CarSettingKind): String = when (kind) {
    is CarSettingKind.LowPowerInfo -> "信息提醒：${kind.alarmOn?.let { if (it) "开启" else "关闭" } ?: "UNKNOWN"} / ${kind.alarmPower?.let { "$it%" } ?: "UNKNOWN"}"
    is CarSettingKind.LowPowerPhone -> "电话告警：${kind.enabled?.let { if (it) "开启" else "关闭" } ?: "UNKNOWN"} / ${kind.startTime ?: "UNKNOWN"} — ${kind.endTime ?: "UNKNOWN"}"
    is CarSettingKind.Toggle -> if (kind.checked) "开启" else "关闭"
    is CarSettingKind.Choice -> kind.options.getOrNull(kind.selectedIndex) ?: "UNKNOWN"
    is CarSettingKind.Slider -> formatSettingValue(kind.value, kind.unit)
    is CarSettingKind.Customize -> "自定义参数"
    is CarSettingKind.Atmosphere -> "模式 ${kind.showMode} / RGB1 ${atmosphereHex(kind.red1, kind.green1, kind.blue1)}"
    else -> "UNKNOWN"
}

private fun atmosphereHex(red: Int, green: Int, blue: Int): String {
    fun part(value: Int) = value.coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "#${part(red)}${part(green)}${part(blue)}"
}

private fun formatSettingValue(value: Float, unit: String): String {
    val text = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
    return if (unit.isBlank()) text else "$text$unit"
}
