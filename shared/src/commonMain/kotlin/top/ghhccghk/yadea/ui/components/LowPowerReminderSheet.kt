package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.carsetting.CarSettingItem
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.carsetting.CarSettingsState
import top.ghhccghk.yadea.carsetting.LowPowerReminderSession
import top.ghhccghk.yadea.carsetting.editLowPowerTimes
import top.ghhccghk.yadea.carsetting.submitLowPowerDraft
import top.ghhccghk.yadea.carsetting.updateLowPowerReminder
import top.ghhccghk.yadea.carsetting.validLowPowerTimeRange
import top.ghhccghk.yadea.carsetting.selectableLowPowerTime

/** Only issued cloud entries appear; each configuration keeps its own draft and write lifecycle. */
@Composable
internal fun LowPowerReminderSheet(
    controller: CarSettingsController,
    state: CarSettingsState,
    onDismissRequest: () -> Unit,
    controlEnabled: (CarSettingItem) -> Boolean,
) {
    val session = remember(state.vin, state.loadGeneration) { LowPowerReminderSession(state.vin, state.loadGeneration) }
    val scope = rememberCoroutineScope()
    val infoItem = state.items.firstOrNull { it.id == "cloud:9" }
    val phoneItem = state.items.firstOrNull { it.id == "cloud:82" }
    var editingTimes by remember(session) { mutableStateOf(false) }
    DisposableEffect(controller, session) {
        onDispose {
            if (session.matches(controller.state.value)) {
                controller.cancelDraft("cloud:9")
                controller.cancelDraft("cloud:82")
            }
        }
    }
    AppBottomSheet(show = true, title = "低电量提醒", onDismissRequest = onDismissRequest) {
        // A bounded, locally scrolling body; the global AppBottomSheet contract stays unchanged.
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing),
            ) {
                infoItem?.let { item ->
                    val valueState = state.valueState(item.id)
                    val value = valueState.displayKind() as? CarSettingKind.LowPowerInfo
                    val enabled = controlEnabled(item)
                    AppSection {
                        AppSwitchPreference(
                            title = "信息提醒",
                            checked = value?.alarmOn == true,
                            summary = when {
                                value?.alarmOn == null -> "UNKNOWN · 未获取信息提醒开关"
                                value.alarmPower == null -> "阈值未知，不能提交缺失的配置"
                                else -> null
                            },
                            enabled = enabled && value?.alarmOn != null && value.alarmPower != null && valueState.draftValue == null,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    controller.updateLowPowerReminder(item.id, session) { current ->
                                        (current as CarSettingKind.LowPowerInfo).copy(alarmOn = checked, edit = CarSettingKind.LowPowerInfoEdit.ALARM_SWITCH)
                                    }
                                }
                            },
                        )
                        val power = value?.alarmPower
                        if (power == null) {
                            AppText("UNKNOWN · 未获取提醒阈值", modifier = Modifier.padding(AppDesign.dimensions.itemHorizontalPadding))
                        } else {
                            AppSliderPreference(
                                title = "提醒阈值",
                                value = power.toFloat(),
                                valueRange = 0f..100f,
                                valueText = "$power%",
                                enabled = enabled && value.alarmOn == true,
                                // Original slider seeks smoothly, then submits integer progress on release.
                                onValueChange = { progress ->
                                    if (session.matches(controller.state.value)) controller.editDraft(item.id) { current ->
                                        (current as CarSettingKind.LowPowerInfo).copy(alarmPower = progress.roundToInt(), edit = CarSettingKind.LowPowerInfoEdit.THRESHOLD)
                                    }
                                },
                                onValueChangeFinished = { scope.launch { controller.submitLowPowerDraft(item.id, session) } },
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = AppDesign.dimensions.itemHorizontalPadding),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            AppText("0%", style = AppDesign.typography.summary)
                            AppText("100%", style = AppDesign.typography.summary)
                        }
                        AppText(
                            "开启后，当车辆电池等于小于设定值后推送消息提示",
                            style = AppDesign.typography.summary,
                            modifier = Modifier.padding(AppDesign.dimensions.itemHorizontalPadding),
                        )
                    }
                    AppText(carSettingSummary(item.copy(summary = ""), valueState, item.id in state.pendingIds, null), style = AppDesign.typography.summary)
                }
                phoneItem?.let { item ->
                    val valueState = state.valueState(item.id)
                    val value = valueState.displayKind() as? CarSettingKind.LowPowerPhone
                    val enabled = controlEnabled(item)
                    AppSection {
                        AppSwitchPreference(
                            title = "电话告警",
                            checked = value?.enabled == true,
                            summary = when {
                                value?.enabled == null -> "UNKNOWN · 未获取电话告警开关"
                                !validLowPowerTimeRange(value.startTime, value.endTime) -> "告警时间未知或无效，请先修改时间"
                                else -> null
                            },
                            enabled = enabled && value?.enabled != null && !editingTimes &&
                                validLowPowerTimeRange(value.startTime, value.endTime),
                            onCheckedChange = { checked ->
                                scope.launch {
                                    controller.updateLowPowerReminder(item.id, session) { current ->
                                        (current as CarSettingKind.LowPowerPhone).copy(enabled = checked, edit = CarSettingKind.LowPowerPhoneEdit.SWITCH)
                                    }
                                }
                            },
                        )
                        AppText(
                            "车辆电量低于3%且24小时内未充电时，雅迪智能客服将在设定时间段打电话提醒您",
                            style = AppDesign.typography.summary,
                            modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding),
                        )
                        AppListRow(
                            title = "告警时间",
                            value = "${value?.startTime ?: "未知"} — ${value?.endTime ?: "未知"}",
                            trailing = { AppText("修改", color = if (enabled && !editingTimes) AppDesign.colors.primary else AppDesign.colors.onSurfaceVariant) },
                            enabled = enabled && !editingTimes,
                            onClick = { editingTimes = true },
                        )
                        if (editingTimes) {
                            AppTimePicker(
                                title = "开始时间", value = AppTimeOfDay.parse(value?.startTime), minuteStep = 10,
                                enabled = enabled,
                                onValueChange = { controller.editLowPowerTimes(item.id, session, startTime = it.format()) },
                            )
                            AppTimePicker(
                                title = "结束时间", value = AppTimeOfDay.parse(value?.endTime), minuteStep = 10,
                                enabled = enabled,
                                onValueChange = { controller.editLowPowerTimes(item.id, session, endTime = it.format()) },
                            )
                            AppText(
                                "按10分钟选择；结束时间须晚于开始时间，不跨午夜。",
                                style = AppDesign.typography.summary,
                                modifier = Modifier.padding(horizontal = AppDesign.dimensions.itemHorizontalPadding),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(AppDesign.dimensions.itemHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                            ) {
                                AppOutlinedButton(
                                    onClick = { controller.cancelDraft(item.id); editingTimes = false },
                                    enabled = !valueState.busy, modifier = Modifier.weight(1f),
                                ) { AppText("取消") }
                                AppButton(
                                    onClick = {
                                        scope.launch {
                                            if (controller.submitLowPowerDraft(item.id, session)) editingTimes = false
                                        }
                                    },
                                    enabled = enabled && valueState.draftValue != null &&
                                        validLowPowerTimeRange(value?.startTime, value?.endTime) &&
                                        selectableLowPowerTime(value?.startTime) && selectableLowPowerTime(value?.endTime),
                                    modifier = Modifier.weight(1f),
                                ) { AppText("确定") }
                            }
                        }
                    }
                    AppText(carSettingSummary(item.copy(summary = ""), valueState, item.id in state.pendingIds, null), style = AppDesign.typography.summary)
                }
            }
        }
    }
}
