package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import top.ghhccghk.yadea.vehicle.LostModeController
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleCloudStatus
import top.ghhccghk.yadea.vehicle.VehicleDiagnosticsFormatter
import top.ghhccghk.yadea.vehicle.VehicleFaultController
import top.ghhccghk.yadea.vehicle.VehicleProfile

@Composable
fun VehicleDiagnosticsScreen(
    status: TtpInfo?,
    cloudStatus: VehicleCloudStatus?,
    connected: Boolean,
    profile: VehicleProfile?,
    faultController: VehicleFaultController,
    lostModeController: LostModeController,
    accountMobile: String,
    contentPadding: PaddingValues,
    onRefreshCloudStatus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val faultState by faultController.state.collectAsState()
    val lostState by lostModeController.state.collectAsState()
    var showLostModeSheet by remember { mutableStateOf(false) }
    var lostModeTarget by remember { mutableStateOf(true) }
    var lostModePhone by remember(accountMobile) { mutableStateOf(accountMobile) }
    var lostModeCode by remember { mutableStateOf("") }
    val dimensions = AppDesign.dimensions
    LaunchedEffect(profile?.modelNo) {
        profile?.modelNo?.takeIf { it.isNotBlank() }?.let(faultController::refresh)
    }
    LaunchedEffect(lostState.message) {
        val message = lostState.message.orEmpty()
        if (message.contains("已开启") || message.contains("已关闭")) {
            showLostModeSheet = false
        }
    }
    val faultCodes = VehicleDiagnosticsFormatter.faultCodes(status)
    val cloudFaults = VehicleDiagnosticsFormatter.cloudFaultRows(faultState.report)
    val tireRows = VehicleDiagnosticsFormatter.tireRows(cloudStatus)
    val radarRows = VehicleDiagnosticsFormatter.radarRows(status)
    val lowBatteryRows = VehicleDiagnosticsFormatter.lowBatteryRows(status, cloudStatus)
    val locationRows = VehicleDiagnosticsFormatter.locationRows(status, cloudStatus)
    val hasFault = faultCodes.isNotEmpty() || faultState.report?.hasFaults == true

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
        item {
            AppPageHeader(
                title = "车辆诊断",
                subtitle = "故障码与雷达来自车辆 TTP，胎压、低电量与定位同时参考云端状态",
            )
        }
        item {
            AppTip(
                kind = if (hasFault) AppTipKind.Warning else AppTipKind.Info,
                title = if (hasFault) "检测到需要关注的项目" else "诊断数据说明",
                message = if (hasFault) {
                    "存在故障码或云端故障等级。请先停止骑行并核对故障详情，严重故障请联系售后。"
                } else {
                    "页面只展示车辆实际上报或云端返回的数据；没有数据的项目显示为“—”，不会填充演示值。"
                },
            )
        }
        if (!connected) {
            item {
                AppTip(
                    kind = AppTipKind.Info,
                    title = "BLE 未连接",
                    message = "故障码和雷达实时状态需要连接车辆 BLE；云端胎压、低电量和定位仍可显示最近一次上报。",
                )
            }
        }

        item {
            AppSection(title = "故障检测") {
                if (status == null) {
                    AppListRow(
                        title = "车辆故障码",
                        summary = "等待 BLE 状态报文",
                        value = "—",
                        showDivider = true,
                    )
                } else if (faultCodes.isEmpty()) {
                    AppListRow("车辆故障码", value = "无", showDivider = true)
                } else {
                    faultCodes.forEachIndexed { index, code ->
                        AppListRow(
                            title = if (index == 0) "车辆故障码" else "故障码 ${index + 1}",
                            value = code,
                            showDivider = true,
                        )
                    }
                }
                if (cloudFaults.isEmpty()) {
                    AppListRow(
                        title = "云端检测结果",
                        summary = faultState.error,
                        value = when {
                            faultState.loading -> "检测中"
                            profile?.modelNo.isNullOrBlank() -> "缺少车型"
                            else -> "未获取"
                        },
                    )
                } else {
                    cloudFaults.forEachIndexed { index, (title, value) ->
                        AppListRow(
                            title = title,
                            summary = value,
                            showDivider = index != cloudFaults.lastIndex,
                        )
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
                        onClick = {
                            profile?.modelNo?.takeIf { it.isNotBlank() }
                                ?.let(faultController::refresh)
                            onRefreshCloudStatus?.invoke()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !faultState.loading,
                    ) {
                        AppText(
                            if (faultState.loading) "正在检测…" else "刷新诊断数据",
                            style = AppDesign.typography.button,
                        )
                    }
                }
            }
        }

        item {
            AppSection(title = "胎压") {
                if (tireRows.all { it.second == "—" }) {
                    AppEmptyState("云端尚未返回前后轮胎压数据")
                } else {
                    tireRows.forEachIndexed { index, (title, value) ->
                        AppListRow(
                            title = title,
                            value = value,
                            showDivider = index != tireRows.lastIndex,
                        )
                    }
                }
                AppText(
                    text = "前后轮胎压来自云端最近一次上报，通常由车辆绑定的外部胎压传感器采集；没有外设数据时不会伪造胎压值。",
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                )
            }
        }

        item {
            AppSection(title = "雷达") {
                if (radarRows.isEmpty()) {
                    AppEmptyState("连接车辆并收到 TTP 状态后显示 FCM/FCW/RCW/BSD 雷达数据")
                } else {
                    radarRows.forEachIndexed { index, (title, value) ->
                        AppListRow(
                            title = title,
                            value = value,
                            showDivider = index != radarRows.lastIndex,
                        )
                    }
                }
            }
        }

        item {
            AppSection(title = "低电量与亏电保护") {
                lowBatteryRows.forEachIndexed { index, (title, value) ->
                    AppListRow(
                        title = title,
                        value = value,
                        showDivider = index != lowBatteryRows.lastIndex,
                    )
                }
                AppText(
                    text = "提醒开关和提醒时段来自云端车辆设置；可在车辆设置的“低电量预警”中修改。",
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                )
            }
        }

        item {
            AppSection(title = "定位与报失") {
                locationRows.forEachIndexed { index, (title, value) ->
                    AppListRow(
                        title = title,
                        value = value,
                        showDivider = index != locationRows.lastIndex,
                    )
                }
                AppTip(
                    kind = AppTipKind.Warning,
                    title = "报失模式需要短信验证",
                    message = "开启或关闭前会向车主手机发送验证码，云端校验通过后才会写入车辆状态。请勿频繁请求验证码；原 App 对单位时间内的发送次数有限制。",
                )
                AppOutlinedButton(
                    onClick = {
                        lostModeTarget = cloudStatus?.lossState != 1
                        lostModePhone = accountMobile
                        lostModeCode = ""
                        showLostModeSheet = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    enabled = lostModeController.available && !lostState.busy,
                ) {
                    AppText(
                        if (cloudStatus?.lossState == 1) "关闭报失模式" else "开启报失模式",
                        style = AppDesign.typography.button,
                    )
                }
                lostState.message?.let { message ->
                    AppText(
                        text = message,
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
        }
        AppBottomSheet(
            show = showLostModeSheet,
            onDismissRequest = {
                if (!lostState.busy) showLostModeSheet = false
            },
            title = if (lostModeTarget) "开启报失模式" else "关闭报失模式",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
            ) {
                AppText(
                    text = "验证码会发送到车主手机；通过校验后才会写入车辆报失状态。",
                    style = AppDesign.typography.summary,
                    color = AppDesign.colors.onSurfaceVariant,
                )
                AppTextField(
                    value = lostModePhone,
                    onValueChange = { lostModePhone = it.filter(Char::isDigit).take(11) },
                    label = "车主手机号",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !lostState.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppTextField(
                        value = lostModeCode,
                        onValueChange = { lostModeCode = it.filter(Char::isDigit).take(6) },
                        label = "验证码",
                        modifier = Modifier.weight(1f),
                        enabled = !lostState.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    AppOutlinedButton(
                        onClick = { lostModeController.sendCode(lostModePhone) },
                        enabled = !lostState.busy && lostModePhone.isNotBlank(),
                    ) {
                        AppText(
                            if (lostState.busy) "请稍候" else "发送验证码",
                            style = AppDesign.typography.button,
                        )
                    }
                }
                AppButton(
                    onClick = {
                        profile?.vin?.takeIf { it.isNotBlank() }?.let { vin ->
                            lostModeController.apply(
                                vin = vin,
                                mobile = lostModePhone,
                                code = lostModeCode,
                                enabled = lostModeTarget,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !lostState.busy &&
                        lostModePhone.isNotBlank() &&
                        lostModeCode.isNotBlank(),
                ) {
                    AppText(
                        if (lostModeTarget) "验证并开启" else "验证并关闭",
                        style = AppDesign.typography.button,
                    )
                }
            }
        }
    }
}
