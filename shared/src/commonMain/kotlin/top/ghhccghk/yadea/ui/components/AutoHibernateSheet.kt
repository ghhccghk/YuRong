package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.carsetting.AutoHibernateSession
import top.ghhccghk.yadea.carsetting.AutoHibernateSettings
import top.ghhccghk.yadea.carsetting.CarSettingItemAccess
import top.ghhccghk.yadea.carsetting.CarSettingKind
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.carsetting.CarSettingsState
import top.ghhccghk.yadea.carsetting.updateAutoHibernate

@Composable
internal fun AutoHibernateSheet(controller: CarSettingsController, state: CarSettingsState, onDismissRequest: () -> Unit) {
    val session = remember(state.vin, state.loadGeneration) { AutoHibernateSession(state.vin, state.loadGeneration) }
    val scope = rememberCoroutineScope()
    val item = state.items.firstOrNull { it.id == AutoHibernateSettings.ID } ?: return
    val valueState = state.valueState(item.id)
    val configured = valueState.configuredValue as? CarSettingKind.AutoHibernate
    var edit by remember(session) { mutableStateOf<CarSettingKind.AutoHibernateEdit?>(null) }
    var daysText by remember(session) { mutableStateOf("") }
    val days = daysText.toIntOrNull()?.takeIf { it in 1..30 }
    val enabled = !state.loading && !valueState.busy && item.id !in state.pendingIds &&
        valueState.submittedValue == null && CarSettingItemAccess.canWrite(item) && AutoHibernateSettings.available(state)
    DisposableEffect(controller, session) {
        onDispose { if (session.matches(controller.state.value)) controller.cancelDraft(AutoHibernateSettings.ID) }
    }
    AppBottomSheet(show = true, title = "自动休眠", onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)) {
            AppSwitchPreference(title = "自动休眠", checked = configured?.enabled == true,
                summary = if (configured?.enabled == null) "UNKNOWN · 未获取云端开关配置" else "云端配置；不代表车辆已休眠",
                enabled = enabled && configured?.enabled != null && edit == null,
                onCheckedChange = { checked ->
                    if (checked) {
                        daysText = configured?.dayCount?.toString().orEmpty()
                        edit = CarSettingKind.AutoHibernateEdit.ENABLE
                    } else scope.launch {
                        // Original onAuto explicitly sends dayCount=1 on close, even if the prior day is unknown.
                        controller.updateAutoHibernate(session, CarSettingKind.AutoHibernate(false, 1, CarSettingKind.AutoHibernateEdit.DISABLE))
                    }
                })
            AppActionPreference(title = "自动休眠时间", value = configured?.dayCount?.let { "$it 天" } ?: "UNKNOWN",
                summary = "最少填写1天，最多填写30天", enabled = enabled && configured?.enabled == true && edit == null,
                onClick = { daysText = configured?.dayCount?.toString().orEmpty(); edit = CarSettingKind.AutoHibernateEdit.DAYS })
            if (!AutoHibernateSettings.available(state)) AppText("立即休眠已生效或状态未知，请先唤醒车辆并刷新配置")
            edit?.let { editing ->
                AppTextField(value = daysText, onValueChange = { input ->
                    daysText = input
                    if (session.matches(controller.state.value)) {
                        input.toIntOrNull()?.takeIf { it in 1..30 }?.let { day ->
                            controller.editDraft(item.id) { CarSettingKind.AutoHibernate(true, day, editing) }
                        } ?: controller.cancelDraft(item.id)
                    }
                }, label = "天数（1～30）", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing)) {
                    AppOutlinedButton(onClick = {
                        if (session.matches(controller.state.value)) controller.cancelDraft(item.id)
                        edit = null
                    }) { AppText("取消") }
                    AppButton(enabled = enabled && days != null, onClick = {
                        val day = days ?: return@AppButton
                        scope.launch {
                            if (controller.updateAutoHibernate(session, CarSettingKind.AutoHibernate(true, day, editing))) edit = null
                        }
                    }) { AppText("确定") }
                }
            }
            AppText(carSettingSummary(item.copy(summary = ""), valueState, false, null), style = AppDesign.typography.summary)
        }
    }
}
