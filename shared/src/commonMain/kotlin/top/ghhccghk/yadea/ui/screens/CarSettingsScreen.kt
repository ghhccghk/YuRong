package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.carsetting.CarSettingItem
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.CarSettingsController

/** 车辆设置页。业务状态由 [CarSettingsController] 提供，页面只负责语义渲染。 */
@Composable
fun CarSettingsScreen(
    controller: CarSettingsController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val dimensions = AppDesign.dimensions
    var confirmDisable by remember { mutableStateOf<CarSettingItem?>(null) }
    var detailItem by remember { mutableStateOf<CarSettingItem?>(null) }

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
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = dimensions.pageHorizontalPadding,
                ),
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
                            onOpenDetail = { detailItem = item },
                            onUpdate = { transform ->
                                val next = transform(item.kind)
                                if (next is CarSettingKind.Toggle &&
                                    !next.checked &&
                                    item.configId in SAFETY_CONFIRM_IDS
                                ) {
                                    confirmDisable = item
                                } else {
                                    scope.launch { controller.update(item.id, transform) }
                                }
                            },
                        )
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
