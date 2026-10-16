package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.preference.RadioButtonPreference as MiuixRadioButtonPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 基础确认弹窗：标题、说明文字，以及确认/取消按钮。
 *
 * Miuix 使用原生 WindowDialog，Material 3 使用原生 AlertDialog；
 * 两套风格的按钮顺序、启用状态和回调时机保持一致，文案由调用方给出。
 *
 * @param show 是否展示。
 * @param title 标题。
 * @param message 说明文字。
 * @param onDismissRequest 取消、点击外部或返回键时的回调。
 * @param confirmText 确认按钮文案。
 * @param dismissText 取消按钮文案；为空时只显示确认按钮。
 * @param onConfirm 确认回调。
 */
@Composable
fun AppAlertDialog(
    show: Boolean,
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确定",
    dismissText: String? = "取消",
    onConfirm: () -> Unit,
) {
    AppStyleContent(
        material = {
            if (show) {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    modifier = modifier,
                    title = { AppText(text = title, style = AppDesign.typography.title) },
                    text = {
                        AppText(
                            text = message,
                            style = AppDesign.typography.body,
                            color = AppDesign.colors.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        AppButton(onClick = onConfirm) { AppText(confirmText) }
                    },
                    // 未提供取消文案时只保留确认按钮，例如纯告知型弹窗。
                    dismissButton = if (dismissText != null) {
                        {
                            AppOutlinedButton(onClick = onDismissRequest) {
                                AppText(dismissText)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        },
        miuix = {
            WindowDialog(
                show = show,
                title = title,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                ) {
                    AppText(
                        text = message,
                        style = AppDesign.typography.body,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    ) {
                        if (dismissText != null) {
                            AppOutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(dismissText)
                            }
                        }
                        AppButton(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(confirmText)
                        }
                    }
                }
            }
        },
    )
}

/** 单选/输入类弹窗里列表与输入区域的最大高度，超出后内部滚动，避免长内容把弹窗撑爆。 */
private val DialogContentMaxHeight = 320.dp

/**
 * 把外部的选中索引归一化成弹窗内部的待选索引。
 *
 * 越界（例如选项列表变化后原索引失效）时返回 -1，表示「未选中任何项」，
 * 与 [AppSingleChoiceDialog] 只展示列表、不预选的行为一致。
 */
internal fun resolvePendingSelection(options: List<String>, selectedIndex: Int): Int =
    if (selectedIndex in options.indices) selectedIndex else -1

/**
 * 单选列表弹窗：标题、可滚动的选项列表，以及确认/取消按钮。
 *
 * 弹窗内部维护「待选索引」：点击选项只改本地状态，点击确认才通过 [onConfirm] 提交，
 * 取消、点击外部或返回键都不会改动调用方持有的配置。两套风格（Miuix `WindowDialog` +
 * `RadioButtonPreference`，Material 3 `AlertDialog` + `RadioButton`）的交互与回调时机保持一致。
 *
 * @param show 是否展示。
 * @param title 标题。
 * @param options 选项文本列表，索引与 [selectedIndex] 一一对应。
 * @param selectedIndex 当前已生效的选中项索引；越界时视为未选中。
 * @param onConfirm 点击确认时回调最终选中的索引；未选中任何选项时为 -1。
 * @param onDismissRequest 取消、点击外部或返回键时的回调。
 * @param confirmText 确认按钮文案。
 * @param dismissText 取消按钮文案；为空时只显示确认按钮。
 */
@Composable
fun AppSingleChoiceDialog(
    show: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "确定",
    dismissText: String? = "取消",
) {
    // 待选状态：弹窗开关、外部选中项或选项数量变化时重新对齐，避免残留上一次的临时选择。
    var pendingIndex by remember(show, selectedIndex, options.size) {
        mutableStateOf(resolvePendingSelection(options, selectedIndex))
    }

    AppStyleContent(
        material = {
            if (show) {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    modifier = modifier,
                    title = { AppText(text = title, style = AppDesign.typography.title) },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = DialogContentMaxHeight)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            options.forEachIndexed { index, option ->
                                val selected = index == pendingIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = { pendingIndex = index },
                                        )
                                        .padding(vertical = AppDesign.dimensions.compactSpacing),
                                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // RadioButton 自身不处理点击，统一交给整行的 selectable，避免重复的点击语义。
                                    RadioButton(selected = selected, onClick = null)
                                    AppText(
                                        text = option,
                                        style = AppDesign.typography.body,
                                        color = if (selected) AppDesign.colors.primary
                                        else AppDesign.colors.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        AppButton(onClick = { onConfirm(pendingIndex) }) { AppText(confirmText) }
                    },
                    // 未提供取消文案时只保留确认按钮，例如纯告知型弹窗。
                    dismissButton = if (dismissText != null) {
                        {
                            AppOutlinedButton(onClick = onDismissRequest) {
                                AppText(dismissText)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        },
        miuix = {
            WindowDialog(
                show = show,
                title = title,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = DialogContentMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        options.forEachIndexed { index, option ->
                            MiuixRadioButtonPreference(
                                title = option,
                                selected = index == pendingIndex,
                                // Miuix 的 preference 自带整行点击与触觉反馈，这里只负责状态流转。
                                onClick = { pendingIndex = index },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    ) {
                        if (dismissText != null) {
                            AppOutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(dismissText)
                            }
                        }
                        AppButton(
                            onClick = { onConfirm(pendingIndex) },
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(confirmText)
                        }
                    }
                }
            }
        },
    )
}

/**
 * 弹窗内「说明文案 + 输入框」的公共内容块，供 [AppTextInputDialog] 的两套风格复用。
 *
 * 单独抽出来是为了让 Miuix 与 Material 3 的说明文字层级、间距和输入框宽度完全一致，
 * 避免两套原生实现各自排版时产生视觉差。
 */
@Composable
private fun DialogInputContent(
    value: String,
    onValueChange: (String) -> Unit,
    summary: String?,
    label: String,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.compactSpacing),
    ) {
        if (summary != null) {
            AppText(
                text = summary,
                style = AppDesign.typography.summary,
                color = AppDesign.colors.onSurfaceVariant,
            )
        }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )
    }
}

/**
 * 文本输入弹窗：标题、可选说明、输入框，以及确认/取消按钮。
 *
 * 输入内容由调用方持有（受控组件）：确认之前不会写入配置，取消时调用方可以直接丢弃草稿，
 * 因此「输入中」不会污染已持久化的设置。两套风格的按钮顺序、启用状态与回调时机一致。
 *
 * @param show 是否展示。
 * @param title 标题。
 * @param value 当前输入内容。
 * @param onValueChange 输入内容变化回调。
 * @param onConfirm 点击确认时的回调。
 * @param onDismissRequest 取消、点击外部或返回键时的回调。
 * @param summary 输入框上方的说明文案；为空时不渲染。
 * @param label 输入框标签文案；为空时输入框不显示标签。
 * @param confirmText 确认按钮文案。
 * @param dismissText 取消按钮文案；为空时只显示确认按钮。
 * @param confirmEnabled 确认按钮是否可用，内容校验不通过时由调用方置为 false。
 * @param singleLine 是否单行输入。
 */
@Composable
fun AppTextInputDialog(
    show: Boolean,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String = "",
    confirmText: String = "确定",
    dismissText: String? = "取消",
    confirmEnabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    AppStyleContent(
        material = {
            if (show) {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    modifier = modifier,
                    title = { AppText(text = title, style = AppDesign.typography.title) },
                    text = {
                        DialogInputContent(
                            value = value,
                            onValueChange = onValueChange,
                            summary = summary,
                            label = label,
                            singleLine = singleLine,
                            keyboardOptions = keyboardOptions,
                            keyboardActions = keyboardActions,
                        )
                    },
                    confirmButton = {
                        AppButton(onClick = onConfirm, enabled = confirmEnabled) {
                            AppText(confirmText)
                        }
                    },
                    // 未提供取消文案时只保留确认按钮。
                    dismissButton = if (dismissText != null) {
                        {
                            AppOutlinedButton(onClick = onDismissRequest) {
                                AppText(dismissText)
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        },
        miuix = {
            WindowDialog(
                show = show,
                title = title,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                ) {
                    DialogInputContent(
                        value = value,
                        onValueChange = onValueChange,
                        summary = summary,
                        label = label,
                        singleLine = singleLine,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    ) {
                        if (dismissText != null) {
                            AppOutlinedButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                            ) {
                                AppText(dismissText)
                            }
                        }
                        AppButton(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            enabled = confirmEnabled,
                        ) {
                            AppText(confirmText)
                        }
                    }
                }
            }
        },
    )
}
