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
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.ota.OtaController
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "OTA 升级",
                subtitle = "通过车辆升级通道更新固件，升级期间请保持连接",
            )
        }
        item {
            AppSection(title = "版本信息") {
                AppListRow("当前版本", value = state.currentVersion, showDivider = true)
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
                        onClick = controller::install,
                        modifier = Modifier.weight(1f),
                        enabled = state.canInstall,
                    ) {
                        AppText(
                            if (state.stage == OtaStage.Failed) "重试升级" else "开始升级",
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
