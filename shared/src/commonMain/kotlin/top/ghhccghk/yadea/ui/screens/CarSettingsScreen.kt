package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import top.ghhccghk.yadea.carsetting.CarSettingClassification
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车辆设置",
                subtitle = if (controller.available) {
                    "修改后会同步到车辆；失败时自动回滚"
                } else {
                    "当前未接入车辆设置通道，以下为可写项预览"
                },
            )
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
        for (classification in CarSettingClassification.entries) {
            val items = state.items.filter { it.classification == classification }
            if (items.isEmpty()) continue
            item {
                AppSection(title = classification.label) {
                    items.forEach { item ->
                        CarSettingRow(
                            item = item,
                            enabled = controller.available &&
                                item.writable &&
                                item.id !in state.pendingIds,
                            pending = item.id in state.pendingIds,
                            onUpdate = { transform ->
                                scope.launch { controller.update(item.id, transform) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarSettingRow(
    item: CarSettingItem,
    enabled: Boolean,
    pending: Boolean,
    onUpdate: ((CarSettingKind) -> CarSettingKind) -> Unit,
) {
    when (val kind = item.kind) {
        is CarSettingKind.Toggle -> AppSwitchPreference(
            checked = kind.checked,
            onCheckedChange = { checked ->
                onUpdate { (it as? CarSettingKind.Toggle)?.copy(checked = checked) ?: it }
            },
            title = item.title,
            summary = if (pending) "正在保存…" else "${item.summary} · ${item.channel.label}",
            enabled = enabled,
        )
        is CarSettingKind.Choice -> AppPopupPreference(
            title = item.title,
            summary = if (pending) "正在保存…" else "${item.summary} · ${item.channel.label}",
            options = kind.options,
            selectedIndex = kind.selectedIndex,
            onSelectedIndexChange = { index ->
                onUpdate { (it as? CarSettingKind.Choice)?.copy(selectedIndex = index) ?: it }
            },
            enabled = enabled,
        )
        is CarSettingKind.Slider -> {
            var draft by remember(item.id, kind.value) { mutableStateOf(kind.value) }
            AppSliderPreference(
                title = item.title,
                summary = if (pending) "正在保存…" else "${item.summary} · ${item.channel.label}",
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    onUpdate { (it as? CarSettingKind.Slider)?.copy(value = draft) ?: it }
                },
                valueRange = kind.range,
                steps = kind.steps,
                valueText = formatSettingValue(draft, kind.unit),
                enabled = enabled,
            )
        }
        is CarSettingKind.Info -> AppListRow(
            title = item.title,
            summary = item.summary,
            value = kind.value,
        )
    }
}

private fun formatSettingValue(value: Float, unit: String): String {
    val text = if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
    return if (unit.isBlank()) text else "$text$unit"
}
