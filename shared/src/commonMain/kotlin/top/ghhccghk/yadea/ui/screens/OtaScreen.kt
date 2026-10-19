package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.ota.OtaController
import top.ghhccghk.yadea.ota.OtaMethod
import top.ghhccghk.yadea.ota.OtaStage

/** OTA 升级页。通道未注入时只展示当前版本与空态。 */
@Composable
fun OtaScreen(
    controller: OtaController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val dimensions = AppDesign.dimensions
    var showRiskConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "OTA 升级",
                subtitle = "按蓝牙、车联网、Wi-Fi 与记录仪通道分别查看云端升级任务",
            )
        }
        item {
            if (state.selectedMethod == OtaMethod.Ble) {
                AppTip(
                    kind = AppTipKind.Warning,
                    title = "BLE 本地升级存在高风险",
                    message = "该功能按反编译协议直译，未经过完整真车回归。升级中断或固件不匹配可能导致车辆停在 bootloader。强烈建议优先使用云端 OTA 或原厂软件升级。",
                )
            } else {
                AppTip(
                    kind = AppTipKind.Info,
                    title = "建议使用云端升级",
                    message = "当前 App 仅实现了 BLE 固件传输；车联网、Wi-Fi 和记录仪通道请使用云端或原厂软件升级。",
                )
            }
        }
        item {
            AppSection(title = "升级方式") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                ) {
                    AppSegmentedControl(
                        options = OtaMethod.entries.map { it.shortLabel },
                        selectedIndex = OtaMethod.entries.indexOf(state.selectedMethod),
                        onSelected = { controller.selectMethod(OtaMethod.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(
                        text = state.selectedMethod.description,
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            AppSection(title = "版本信息") {
                AppListRow("当前版本", value = state.currentVersion, showDivider = true)
                AppListRow(
                    "升级方式",
                    value = state.selectedMethod.label,
                    showDivider = true,
                )
                AppListRow(
                    "可用版本",
                    value = state.packageInfo?.version ?: "—",
                    showDivider = true,
                )
                AppListRow(
                    "安装包大小",
                    value = state.packageInfo?.sizeBytes?.let(::formatOtaSize) ?: "—",
                    showDivider = true,
                )
                AppListRow("升级状态", value = state.stage.label(), summary = state.message)
            }
        }
        item {
            AppSection(title = "${state.selectedMethod.label}任务") {
                if (state.selectedPackages.isEmpty()) {
                    AppEmptyState("当前通道没有云端升级任务")
                } else {
                    state.selectedPackages.forEachIndexed { index, pkg ->
                        AppListRow(
                            title = pkg.typeName.ifBlank { "车辆固件" },
                            summary = listOfNotNull(
                                pkg.channelLabel.takeIf { it.isNotBlank() },
                                pkg.releaseNotes.takeIf { it.isNotBlank() },
                                pkg.status.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            value = buildString {
                                append(pkg.version)
                                if (pkg.forceUpgrade) append(" · 强制")
                            },
                            showDivider = index != state.selectedPackages.lastIndex,
                        )
                    }
                    AppText(
                        text = "BLE 通道已按 smali 直译固件下载、分包和 ACK 流程；车联网、Wi-Fi 与记录仪请使用云端或原厂软件升级。",
                        style = AppDesign.typography.summary,
                        color = AppDesign.colors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                    )
                }
            }
        }
        if (state.stage == OtaStage.Unavailable) {
            item {
                AppSection(title = "升级通道") {
                    AppEmptyState("OTA 服务未接入。接入 OtaGateway 后可检查并安装固件。")
                }
            }
        } else {
            if (state.busy || state.stage == OtaStage.Completed) {
                item {
                    AppSection(title = "进度") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = dimensions.itemHorizontalPadding,
                                vertical = dimensions.itemVerticalPadding,
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
                        ) {
                            AppLinearProgressIndicator(
                                progress = if (state.stage == OtaStage.Completed) 1f
                                else state.progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AppText(
                                text = "${(state.progress * 100).toInt()}%",
                                style = AppDesign.typography.value,
                            )
                        }
                    }
                }
            }
            if (!state.packageInfo?.releaseNotes.isNullOrBlank()) {
                item {
                    AppSection(title = "更新说明") {
                        AppEmptyState(state.packageInfo?.releaseNotes.orEmpty())
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.pageHorizontalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppButton(
                        onClick = controller::checkForUpdate,
                        modifier = Modifier.weight(1f),
                        enabled = state.canCheck,
                    ) {
                        AppText("检查更新", style = AppDesign.typography.button)
                    }
                    AppButton(
                        onClick = {
                            if (state.selectedMethod == OtaMethod.Ble) {
                                showRiskConfirm = true
                            } else {
                                controller.install()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.canInstall && state.selectedMethod == OtaMethod.Ble,
                    ) {
                        AppText(
                            when {
                                state.stage == OtaStage.Failed -> "重试升级"
                                state.packageInfo?.method == OtaMethod.Iot -> "提交云端升级"
                                else -> "开始升级"
                            },
                            style = AppDesign.typography.button,
                        )
                    }
                }
            }
            if (state.canCancel) {
                item {
                    AppOutlinedButton(
                        onClick = controller::cancel,
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.pageHorizontalPadding,
                        ),
                    ) {
                        AppText("取消升级", style = AppDesign.typography.button)
                    }
                }
            }
        }
    }

    AppAlertDialog(
        show = showRiskConfirm,
        title = "确认 BLE 本地升级",
        message = "此操作会直接向车辆写入固件。若电量不足、连接中断或文件不匹配，车辆可能无法正常启动。请确认车辆电量充足并保持前台连接；更推荐使用云端或原厂软件升级。",
        onDismissRequest = { showRiskConfirm = false },
        confirmText = "仍要升级",
        onConfirm = {
            showRiskConfirm = false
            controller.install()
        },
    )
}

private fun OtaStage.label(): String = when (this) {
    OtaStage.Unavailable -> "不可用"
    OtaStage.Idle -> "待检查"
    OtaStage.Checking -> "检查中"
    OtaStage.Available -> "发现新版本"
    OtaStage.UpToDate -> "已是最新"
    OtaStage.Downloading -> "下载中"
    OtaStage.Verifying -> "校验中"
    OtaStage.Installing -> "写入中"
    OtaStage.Submitted -> "已提交云端"
    OtaStage.Completed -> "升级完成"
    OtaStage.Failed -> "升级失败"
    OtaStage.Cancelled -> "已取消"
}

private fun formatOtaSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
