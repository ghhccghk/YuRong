package top.ghhccghk.yadea.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 轻量提示的类型，决定 [AppTip] 的配色与无障碍描述。
 */
enum class AppTipKind(val label: String) {
    Info("信息"),
    Success("成功"),
    Warning("警告"),
    Error("错误"),
}

/**
 * 提示左侧的类型图标。使用字符而非图标资源，避免为两套风格各维护一套图标集。
 */
private val AppTipKind.glyph: String
    get() = when (this) {
        AppTipKind.Info -> "\u24D8"
        AppTipKind.Success -> "\u2713"
        AppTipKind.Warning -> "\u26A0"
        AppTipKind.Error -> "\u2715"
    }

/**
 * 轻量状态提示：用于错误、警告、成功和普通信息反馈。
 *
 * 配色统一来自 [AppDesign] 的语义 token，两种风格下含义一致：
 * Material 3 使用原生 Card 承载，Miuix 使用原生 Card 承载，只是颜色取自各自的容器色。
 *
 * @param title 提示标题，必填。
 * @param message 补充说明，可为空。
 * @param kind 提示类型，决定容器色与图标。
 * @param onDismiss 非空时在右侧展示关闭按钮。
 */
@Composable
fun AppTip(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    kind: AppTipKind = AppTipKind.Info,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = AppDesign.colors
    // 容器色与内容色成对使用，保证浅色/深色模式下都有足够对比度。
    val containerColor: Color
    val contentColor: Color
    when (kind) {
        AppTipKind.Info -> {
            containerColor = colors.primaryContainer
            contentColor = colors.onPrimaryContainer
        }

        AppTipKind.Success -> {
            containerColor = colors.successContainer
            contentColor = colors.onSuccessContainer
        }

        AppTipKind.Warning -> {
            containerColor = colors.warningContainer
            contentColor = colors.onWarningContainer
        }

        AppTipKind.Error -> {
            containerColor = colors.errorContainer
            contentColor = colors.onErrorContainer
        }
    }

    AppCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppDesign.dimensions.itemHorizontalPadding,
                    vertical = AppDesign.dimensions.itemVerticalPadding,
                )
                // 状态提示作为 live region，内容变化时可被读屏温和播报。
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            AppText(
                text = kind.glyph,
                style = AppDesign.typography.itemTitle,
                color = contentColor,
                // 图标是装饰性字符，用类型标签替换，避免读屏读出符号本身。
                modifier = Modifier.clearAndSetSemantics { contentDescription = kind.label },
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title,
                    style = AppDesign.typography.itemTitle,
                    color = contentColor,
                )
                if (!message.isNullOrBlank()) {
                    AppText(
                        text = message,
                        style = AppDesign.typography.summary,
                        color = contentColor,
                    )
                }
            }
            if (onDismiss != null) {
                AppText(
                    text = "\u00D7",
                    style = AppDesign.typography.value,
                    color = contentColor,
                    // onClickLabel 让读屏把关闭动作读成“关闭提示”，而不是读符号本身。
                    modifier = Modifier.clickable(
                        onClickLabel = "关闭提示",
                        role = Role.Button,
                        onClick = onDismiss,
                    ),
                )
            }
        }
    }
}

/**
 * 模态加载弹窗：用于连接、投屏等需要明确“正在处理”反馈的长时间操作。
 *
 * Miuix 使用原生 WindowDialog，Material 3 使用原生 AlertDialog；
 * 两者都只展示进度指示与说明文本，是否可关闭由 [onDismissRequest] 决定。
 *
 * @param show 是否展示。
 * @param title 标题。
 * @param message 说明文本，可为空。
 * @param onDismissRequest 为空时弹窗不可通过返回键或点击外部关闭。
 */
@Composable
fun AppLoadingDialog(
    show: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onDismissRequest: (() -> Unit)? = null,
) {
    val dismissible = onDismissRequest != null
    AppStyleContent(
        material = {
            if (show) {
                AlertDialog(
                    onDismissRequest = { onDismissRequest?.invoke() },
                    // 未提供关闭回调时禁用返回键与点击外部关闭，避免操作状态与界面不一致。
                    properties = DialogProperties(
                        dismissOnBackPress = dismissible,
                        dismissOnClickOutside = dismissible,
                    ),
                    title = { AppText(text = title, style = AppDesign.typography.title) },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppLoadingIndicator()
                            if (!message.isNullOrBlank()) {
                                AppText(
                                    text = message,
                                    style = AppDesign.typography.summary,
                                    color = AppDesign.colors.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    // 加载中不提供确认按钮，保持弹窗只承载进度反馈。
                    confirmButton = {},
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDesign.dimensions.controlSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppLoadingIndicator()
                    if (!message.isNullOrBlank()) {
                        AppText(text = message, style = AppDesign.typography.summary)
                    }
                }
            }
        },
    )
}