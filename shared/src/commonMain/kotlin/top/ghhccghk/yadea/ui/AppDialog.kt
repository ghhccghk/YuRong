package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
