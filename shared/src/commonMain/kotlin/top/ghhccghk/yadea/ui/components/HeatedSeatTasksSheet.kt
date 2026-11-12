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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.carsetting.CarSettingsState
import top.ghhccghk.yadea.carsetting.HeatedSeatTask
import top.ghhccghk.yadea.carsetting.HeatedSeatTaskDraft
import top.ghhccghk.yadea.carsetting.HeatedSeatTaskRules

private data class HeatedSeatTaskEditor(
    val taskId: String?,
    val time: AppTimeOfDay?,
    val repeat: Int?,
    val weekdays: Set<Int>,
)

@Composable
internal fun HeatedSeatTasksSheet(
    controller: CarSettingsController,
    state: CarSettingsState,
    onDismissRequest: () -> Unit,
) {
    val vin = state.vin
    val generation = state.loadGeneration
    val scope = rememberCoroutineScope()
    var tasks by remember(vin, generation) { mutableStateOf<List<HeatedSeatTask>?>(null) }
    var loading by remember(vin, generation) { mutableStateOf(true) }
    var busy by remember(vin, generation) { mutableStateOf(false) }
    var message by remember(vin, generation) { mutableStateOf<String?>(null) }
    var editor by remember(vin, generation) { mutableStateOf<HeatedSeatTaskEditor?>(null) }
    var deleteTarget by remember(vin, generation) { mutableStateOf<HeatedSeatTask?>(null) }

    suspend fun refresh() {
        loading = true
        tasks = controller.loadHeatedSeatTasks(vin)
        loading = false
        message = if (tasks == null) "读取未确认，请检查连接后重试" else null
    }

    LaunchedEffect(controller, vin, generation) { refresh() }

    fun submit(editorState: HeatedSeatTaskEditor) {
        val time = editorState.time?.format()
        val repeat = editorState.repeat
        if (time == null || repeat == null) {
            message = "请选择时间和重复方式"
            return
        }
        val draft = HeatedSeatTaskDraft(time, repeat, editorState.weekdays)
        if (!HeatedSeatTaskRules.valid(draft)) {
            message = if (repeat == 0) "重复任务至少选择一天" else "任务设置无效"
            return
        }
        scope.launch {
            busy = true
            val accepted = controller.saveHeatedSeatTask(vin, draft, editorState.taskId)
            busy = false
            if (accepted) {
                editor = null
                message = "云端已接受任务变更；不代表车辆已执行"
                refresh()
            } else {
                message = "请求未确认；请刷新任务列表核对结果"
            }
        }
    }

    AppBottomSheet(
        show = true,
        title = if (editor == null) "座椅加热定时" else if (editor?.taskId == null) "新增加热时段" else "编辑加热时段",
        onDismissRequest = onDismissRequest,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.sectionSpacing),
            ) {
                if (editor == null) {
                    AppText(
                        "任务记录属于云端排程；车辆执行状态需以车辆回读为准。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                    when {
                        loading -> AppEmptyState("正在读取加热时段…")
                        tasks == null -> AppOutlinedButton(
                            onClick = { scope.launch { refresh() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { AppText("重试") }
                        tasks.isNullOrEmpty() -> AppEmptyState("暂无定时任务")
                        else -> tasks.orEmpty().forEach { task ->
                            HeatedSeatTaskRow(
                                task = task,
                                enabled = !busy && !loading,
                                onEdit = {
                                    editor = HeatedSeatTaskEditor(
                                        taskId = task.id,
                                        time = AppTimeOfDay.parse(task.workTime),
                                        repeat = task.repeat,
                                        weekdays = task.weekdayPositions.orEmpty(),
                                    )
                                },
                                onStatusChange = { checked -> scope.launch {
                                    busy = true
                                    val accepted = controller.setHeatedSeatTaskStatus(
                                        vin, task.id, HeatedSeatTaskRules.statusForEnabled(checked),
                                    )
                                    busy = false
                                    message = if (accepted) "云端已接受状态变更；不代表车辆已执行" else "状态变更未确认，请刷新核对"
                                    refresh()
                                } },
                                onDelete = { deleteTarget = task },
                            )
                        }
                    }
                    if (tasks.orEmpty().size >= 3) {
                        AppText("最多可设置 3 个时段", style = AppDesign.typography.summary,
                            color = AppDesign.colors.onSurfaceVariant)
                    }
                    AppOutlinedButton(
                        onClick = {
                            if (tasks.orEmpty().size >= 3) message = "最多可设置 3 个时段"
                            else editor = HeatedSeatTaskEditor(null, null, null, emptySet())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && !loading && tasks != null && tasks.orEmpty().size < 3,
                    ) { AppText("新增时段") }
                } else {
                    val current = editor ?: return@Column
                    AppTimePicker(
                        title = "加热时间",
                        value = current.time,
                        onValueChange = { editor = current.copy(time = it) },
                    )
                    AppText("重复方式", style = AppDesign.typography.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
                        AppFilterChip(
                            selected = current.repeat == 1,
                            onClick = { editor = current.copy(repeat = 1, weekdays = emptySet()) },
                            label = "仅一次",
                        )
                        AppFilterChip(
                            selected = current.repeat == 0,
                            onClick = { editor = current.copy(repeat = 0) },
                            label = "每周重复",
                        )
                    }
                    if (current.repeat == 0) {
                        AppText("重复日期", style = AppDesign.typography.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
                            listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { day, label ->
                                AppFilterChip(
                                    selected = day in current.weekdays,
                                    onClick = {
                                        editor = current.copy(weekdays = current.weekdays.toMutableSet().apply {
                                            if (!add(day)) remove(day)
                                        })
                                    },
                                    label = label,
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
                        AppOutlinedButton(onClick = { editor = null }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            AppText("取消")
                        }
                        AppButton(
                            onClick = { submit(current) },
                            enabled = !busy && current.time != null && current.repeat != null &&
                                (current.repeat != 0 || current.weekdays.isNotEmpty()),
                            modifier = Modifier.weight(1f),
                        ) { AppText(if (busy) "提交中…" else "保存") }
                    }
                }
                message?.let {
                    AppText(it, style = AppDesign.typography.summary, color = AppDesign.colors.onSurfaceVariant)
                }
            }
        }
    }

    AppAlertDialog(
        show = deleteTarget != null,
        title = "删除加热时段",
        message = "确定删除此云端排程吗？",
        onConfirm = {
            val target = deleteTarget
            deleteTarget = null
            target?.let { task -> scope.launch {
                busy = true
                val accepted = controller.deleteHeatedSeatTask(vin, task.id)
                busy = false
                message = if (accepted) "云端已接受删除；车辆执行状态未确认" else "删除未确认，请刷新核对"
                refresh()
            } }
        },
        onDismissRequest = { deleteTarget = null },
    )
}

@Composable
private fun HeatedSeatTaskRow(
    task: HeatedSeatTask,
    enabled: Boolean,
    onEdit: () -> Unit,
    onStatusChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    AppSection(title = task.workTime) {
        AppText(
            when (task.repeat) {
                1 -> "仅一次"
                0 -> task.weekdayPositions?.sorted()?.joinToString("、") { listOf("日", "一", "二", "三", "四", "五", "六")[it] }
                    ?.let { "每周 $it" } ?: "重复日期未知"
                else -> "重复方式未知"
            },
            style = AppDesign.typography.summary,
            color = AppDesign.colors.onSurfaceVariant,
        )
        HeatedSeatTaskRules.enabledFromStatus(task.status)?.let { checked -> AppSwitchPreference(
                checked = checked,
                onCheckedChange = onStatusChange,
                title = "任务启用",
                enabled = enabled,
            )
        } ?: AppActionPreference(title = "任务状态", value = "未知", enabled = false, onClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing)) {
            AppActionPreference(title = "编辑", value = "修改", enabled = enabled, onClick = onEdit, modifier = Modifier.weight(1f))
            AppActionPreference(title = "删除", value = "删除", enabled = enabled, onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}
