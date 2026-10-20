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
import androidx.compose.runtime.LaunchedEffect
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
    LaunchedEffect(controller) {
        controller.loadRecords()
        controller.refreshCloudState()
    }

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
            if (state.selectedMethod == OtaMethod.Iot) {
                AppTip(
                    kind = AppTipKind.Info,
                    title = "云端任务确认",
                    message = "车联网升级由云平台向车辆下发。提交后可在车辆或原 App 查看进度，请勿同时发起其他升级。",
                )
            } else {
                AppTip(
                    kind = AppTipKind.Warning,
                    title = "${state.selectedMethod.label}存在高风险",
                    message = "该通道按反编译协议直译，未经过完整真车回归。升级中断、固件不匹配或手机休眠都可能让车辆停在 bootloader。强烈建议优先使用云端 OTA 或原厂软件升级。",
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
                state.firmwareVersions.forEach { module ->
                    AppListRow(
                        title = module.label,
                        value = module.currentVersion.ifBlank { "—" },
                        summary = module.latestVersion
                            .takeIf { it.isNotBlank() && it != module.currentVersion }
                            ?.let { "云端最新 $it" },
                        showDivider = true,
                    )
                }
                AppListRow(
                    "可用版本",
                    value = state.packageInfo?.version
                        ?: state.firmwareVersions
                            .mapNotNull { it.latestVersion.takeIf(String::isNotBlank) }
                            .maxOrNull()
                        ?: "—",
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
            AppSection(title = "云端升级状态") {
                val cloud = state.cloudUpgradeStatus
                if (state.cloudStatusLoading) {
                    AppEmptyState("正在查询云端升级任务…")
                } else if (cloud == null) {
                    AppEmptyState(state.cloudStatusError ?: "当前没有进行中的云端升级任务")
                } else {
                    AppListRow(
                        title = "任务状态",
                        value = cloud.stateLabel,
                        summary = cloud.message.takeIf { it.isNotBlank() },
                        showDivider = true,
                    )
                    if (cloud.taskName.isNotBlank()) {
                        AppListRow(
                            title = "任务",
                            value = cloud.taskName,
                            showDivider = true,
                        )
                    }
                    if (cloud.firmwareName.isNotBlank() || cloud.firmwareVersion.isNotBlank()) {
                        AppListRow(
                            title = cloud.firmwareName.ifBlank { "升级固件" },
                            value = cloud.firmwareVersion.ifBlank { "—" },
                            showDivider = cloud.alertCode != null,
                        )
                    }
                    cloud.alertCode?.let {
                        AppListRow(title = "云端告警码", value = it.toString())
                    }
                }
                AppOutlinedButton(
                    onClick = controller::refreshCloudState,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    enabled = !state.cloudStatusLoading,
                ) {
                    AppText("刷新云端状态", style = AppDesign.typography.button)
                }
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
                                if (pkg == state.packageInfo) append(" · 已选")
                            },
                            onClick = { controller.selectPackage(pkg) },
                            showDivider = index != state.selectedPackages.lastIndex,
                        )
                    }
                    AppText(
                        text = when (state.selectedMethod) {
                            OtaMethod.Ble ->
                                "BLE 通道按 smali 直译固件下载、分包和 ACK 流程。"
                            OtaMethod.Iot ->
                                "车联网通道只提交云端确认，实际安装由云平台和车辆完成。"
                            OtaMethod.Wifi, OtaMethod.WifiDvr ->
                                "Wi-Fi 通道会先连接仪表 8888 完成鉴权，再在本机 8080 提供固件文件。传输期间请保持本页在前台、屏幕常亮并避免切换网络。"
                        },
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
        item {
            AppSection(title = "升级记录") {
                when {
                    state.recordsLoading && state.records.isEmpty() ->
                        AppEmptyState("正在读取升级记录…")
                    !state.recordsError.isNullOrBlank() && state.records.isEmpty() ->
                        AppEmptyState(state.recordsError.orEmpty())
                    state.records.isEmpty() ->
                        AppEmptyState("当前车辆还没有 OTA 升级记录")
                    else -> {
                        state.records.forEachIndexed { index, record ->
                            AppListRow(
                                title = record.typeName.ifBlank { "车辆固件" },
                                summary = listOfNotNull(
                                    record.startTime.takeIf { it.isNotBlank() },
                                    record.versionDesc.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                value = record.firmwareVersion.ifBlank { "—" },
                                showDivider = index != state.records.lastIndex,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppOutlinedButton(
                        onClick = { controller.loadRecords(reset = true) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.recordsLoading,
                    ) {
                        AppText("刷新记录", style = AppDesign.typography.button)
                    }
                    if (state.recordsHasNext) {
                        AppOutlinedButton(
                            onClick = { controller.loadRecords(reset = false) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.recordsLoading,
                        ) {
                            AppText("加载更多", style = AppDesign.typography.button)
                        }
                    }
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
                        onClick = {
                            controller.refreshCloudState()
                            controller.checkForUpdate()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.canCheck,
                    ) {
                        AppText("检查更新", style = AppDesign.typography.button)
                    }
                    AppButton(
                        onClick = {
                            showRiskConfirm = true
                        },
                        modifier = Modifier.weight(1f),
                        enabled = state.canInstall,
                    ) {
                        AppText(
                            when {
                                state.stage == OtaStage.Failed -> "重试升级"
                                state.selectedMethod == OtaMethod.Iot -> "提交云端升级"
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
        title = when (state.selectedMethod) {
            OtaMethod.Iot -> "确认提交云端升级"
            else -> "确认${state.selectedMethod.label}"
        },
        message = when (state.selectedMethod) {
            OtaMethod.Iot ->
                "提交后云平台会向车辆下发升级任务。请确认车辆电量充足、网络稳定，并在升级完成前不要骑行或断电。"
            OtaMethod.Ble ->
                "此操作会直接向车辆写入固件。若电量不足、连接中断或文件不匹配，车辆可能无法正常启动。请确认车辆电量充足并保持前台连接；更推荐使用云端或原厂软件升级。"
            OtaMethod.Wifi, OtaMethod.WifiDvr ->
                "此操作会通过 P2P/TCP 直接向仪表提供固件并由车辆写入。传输中断、手机休眠、P2P 掉线或固件不匹配都可能使车辆无法启动。请保持本页在前台、屏幕常亮并连接车辆；更推荐使用云端或原厂软件升级。"
        },
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
