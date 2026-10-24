package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import top.ghhccghk.yadea.carsetting.CloudCarSettingMapper
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehicleInfoUiModel
import top.ghhccghk.yadea.vehicle.IotRenewalLinks
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleCloudTheme
import top.ghhccghk.yadea.vehicle.VehicleCloudStatus

/**
 * 车辆信息页。
 *
 * 只消费已经解析好的 [TtpInfo]；连接、订阅与解析由 VehicleStatusSource 负责。
 * 没有状态时展示明确的空态，不填充演示数据。
 */
@Composable
fun VehicleInfoScreen(
    status: TtpInfo?,
    connected: Boolean,
    contentPadding: PaddingValues,
    profile: VehicleProfile? = null,
    cloudTheme: VehicleCloudTheme? = null,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)? = null,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)? = null,
    onOpenIotRenewal: ((String) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    profileErrorMessage: String? = null,
    cloudStatus: VehicleCloudStatus? = null,
    modifier: Modifier = Modifier,
) {
    val model = VehicleInfoFormatter.format(status, connected)
    val cloudRangeLabel = cloudStatus?.remainingMileage
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.toFloatOrNull() != null) "$it km" else it }
    val rangeLabel = cloudRangeLabel ?: model.rangeLabel
    val cloudSocLabel = cloudStatus?.soc?.let { "$it%" }
    val batteryLabel = if (model.batteryLabel == "—") {
        cloudSocLabel ?: model.batteryLabel
    } else {
        model.batteryLabel
    }
    val cloudTotalLabel = cloudStatus?.totalMileage
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.endsWith("km", ignoreCase = true)) it else "$it km" }
    var knownConfigsExpanded by remember(profile?.vin, profile?.configIds) {
        mutableStateOf(false)
    }
    var unknownConfigsExpanded by remember(profile?.vin, profile?.configIds) {
        mutableStateOf(false)
    }
    val dimensions = AppDesign.dimensions
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车辆信息",
                subtitle = "实时状态来自车辆 BLE 周期报文",
            )
        }
        item {
            VehicleHeroCard(
                model = model,
                profile = profile,
                cloudTheme = cloudTheme,
                vehicleBackgroundContent = vehicleBackgroundContent,
                vehicleImageContent = vehicleImageContent,
                rangeLabel = rangeLabel,
                batteryLabel = batteryLabel,
            )
        }
        if (profile?.configIds?.isNotEmpty() == true) {
            item {
                CloudConfigIdsSection(
                    configIds = profile.configIds,
                    knownExpanded = knownConfigsExpanded,
                    unknownExpanded = unknownConfigsExpanded,
                    onKnownExpandedChange = { knownConfigsExpanded = it },
                    onUnknownExpandedChange = { unknownConfigsExpanded = it },
                )
            }
        }
        if (profile == null && (onRefresh != null || profileErrorMessage != null)) {
            item {
                AppSection(title = "云端车辆资料") {
                    AppEmptyState(
                        when {
                            refreshing -> "正在向云端查询当前账号绑定的车辆。"
                            profileErrorMessage != null -> profileErrorMessage
                            else -> "尚未获取到云端车辆资料，可以手动刷新。"
                        },
                    )
                    if (onRefresh != null) {
                        AppButton(
                            onClick = onRefresh,
                            enabled = !refreshing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText(
                                if (refreshing) "正在刷新车辆列表…" else "刷新绑定车辆",
                                style = AppDesign.typography.button,
                            )
                        }
                    }
                }
            }
        }
        if (profile?.iotService != null) {
            item {
                AppSection(title = "车联网服务") {
                    AppListRow(
                        title = "智能网联服务",
                        summary = when {
                            profile.iotService.isPermanentlyExpired ->
                                "已超过 90 天未续费，系统已自动注销"
                            profile.iotService.isExpired ->
                                "服务已到期，远程控车、定位与投屏导航暂不可用"
                            else ->
                                "到期后 90 天内可续费恢复"
                        },
                        value = if (profile.iotService.isExpired) "已到期" else "正常",
                        showDivider = true,
                    )
                    AppListRow(
                        title = "到期时间",
                        value = profile.iotService.cardExpireTime.ifBlank { "—" },
                        summary = profile.iotService.expireDays?.let { "剩余 $it 天" },
                    )
                    if (onOpenIotRenewal != null) {
                        AppButton(
                            onClick = { onOpenIotRenewal(IotRenewalLinks.build(profile)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText(
                                IotRenewalLinks.actionLabel(profile.iotService),
                                style = AppDesign.typography.button,
                            )
                        }
                    }
                }
            }
        }
        if (status == null) {
            if (cloudStatus != null) {
                item {
                    AppSection(title = "云端最近状态") {
                        AppListRow(
                            title = "剩余续航",
                            value = cloudRangeLabel ?: "—",
                            showDivider = true,
                        )
                        AppListRow(
                            title = "总行程",
                            value = cloudTotalLabel ?: "—",
                            showDivider = cloudStatus.currentOdoValue != null,
                        )
                        cloudStatus.currentOdoValue?.let { odo ->
                            AppListRow(
                                title = "当前里程",
                                value = "${formatOneDecimal(odo)} km",
                            )
                        }
                        cloudStatus.soc?.let { soc ->
                            AppListRow("云端电量", value = "$soc%")
                        }
                    }
                }
            }
            item {
                AppSection(title = "等待车辆状态") {
                    AppEmptyState(
                        message = if (connected) {
                            "车辆已连接，正在等待首个状态报文。"
                        } else {
                            "尚未连接车辆。上面的云端数据为最近一次上报；连接 BLE 后会显示实时里程。"
                        },
                    )
                }
            }
        } else {
            item {
                AppSection(title = "行驶与电池") {
                    AppListRow("车速", value = model.speedLabel, showDivider = true)
                    AppListRow(
                        title = "剩余里程",
                        value = rangeLabel,
                        summary = cloudRangeLabel?.let { "云端估算" },
                        showDivider = true,
                    )
                    AppListRow("总里程", value = model.odoLabel, showDivider = true)
                    AppListRow("单次里程", value = model.tripLabel, showDivider = true)
                    AppListRow("电池电量", value = batteryLabel, showDivider = true)
                    AppListRow("总电压", value = model.voltageLabel, showDivider = true)
                    AppListRow("总电流", value = model.currentLabel, showDivider = true)
                    AppListRow("电池温度", value = model.temperatureLabel)
                }
            }
            item {
                AppSection(title = "车辆控制状态") {
                    AppListRow("锁车状态", value = model.lockLabel, showDivider = true)
                    AppListRow("开关机状态", value = model.powerLabel, showDivider = true)
                    AppListRow("速度档位", value = model.gearLabel, showDivider = true)
                    AppListRow("定速巡航", value = model.cruiseLabel, showDivider = true)
                    AppListRow("充电状态", value = model.chargeLabel, showDivider = true)
                    AppListRow("故障状态", value = model.faultLabel)
                }
            }
            if (model.sensorLines.isNotEmpty()) {
                item {
                    AppSection(title = "扩展状态") {
                        model.sensorLines.forEachIndexed { index, (title, value) ->
                            AppListRow(
                                title = title,
                                value = value,
                                showDivider = index != model.sensorLines.lastIndex,
                            )
                        }
                    }
                }
            }
            item {
                AppSection(title = "版本与定位") {
                    AppListRow(
                        "软件版本",
                        value = VehicleInfoFormatter.softwareVersion(status),
                        showDivider = true,
                    )
                    AppListRow(
                        "协议版本",
                        value = "${status.protocolPrimaryVersionCode.versionPart()}." +
                            status.protocolSecondaryVersionCode.versionPart(),
                        showDivider = true,
                    )
                    AppListRow(
                        "经纬度",
                        value = if (status.longitude == -1.0 || status.latitude == -1.0) {
                            "—"
                        } else {
                            "${status.longitude.formatCoordinate()}, ${status.latitude.formatCoordinate()}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudConfigIdsSection(
    configIds: List<String>,
    knownExpanded: Boolean,
    unknownExpanded: Boolean,
    onKnownExpandedChange: (Boolean) -> Unit,
    onUnknownExpandedChange: (Boolean) -> Unit,
) {
    val knownConfigs = configIds.mapNotNull { configId ->
        CloudCarSettingMapper.titleForConfigId(configId)?.let { title ->
            configId to title
        }
    }
    val unknownConfigIds = configIds.filter {
        CloudCarSettingMapper.titleForConfigId(it) == null
    }

    AppSection(title = "云端已开通功能") {
        if (knownConfigs.isNotEmpty()) {
            CloudConfigGroup(
                title = "已识别功能",
                items = knownConfigs,
                expanded = knownExpanded,
                onExpandedChange = onKnownExpandedChange,
                showTrailingDivider = unknownConfigIds.isNotEmpty(),
            )
        }
        if (unknownConfigIds.isNotEmpty()) {
            CloudConfigGroup(
                title = "未知配置 ID",
                items = unknownConfigIds.map { it to null },
                expanded = unknownExpanded,
                onExpandedChange = onUnknownExpandedChange,
                showTrailingDivider = false,
            )
        }
    }
}

@Composable
private fun CloudConfigGroup(
    title: String,
    items: List<Pair<String, String?>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showTrailingDivider: Boolean,
) {
    AppListRow(
        title = title,
        summary = "共 ${items.size} 项",
        value = if (expanded) "收起" else "展开",
        onClick = { onExpandedChange(!expanded) },
        showDivider = expanded || showTrailingDivider,
    )
    if (expanded) {
        items.forEachIndexed { index, (configId, featureTitle) ->
            AppListRow(
                title = featureTitle ?: "未收录功能",
                summary = if (featureTitle == null) {
                    "该配置 ID 暂未在本地功能表中找到对应名称。"
                } else {
                    null
                },
                value = "ID $configId",
                showDivider = index != items.lastIndex || showTrailingDivider,
            )
        }
    }
}

@Composable
private fun VehicleHeroCard(
    model: VehicleInfoUiModel,
    profile: VehicleProfile?,
    cloudTheme: VehicleCloudTheme?,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)?,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)?,
    rangeLabel: String,
    batteryLabel: String,
) {
    val dimensions = AppDesign.dimensions
    val vehicleVisualTopOffset = 20.dp
    val customized = cloudTheme?.isCustomized ?: (profile?.isLinkedModel == true)
    val darkTheme = AppDesign.colors.page.luminance() < 0.5f
    val dynamicBackgroundUrl = cloudTheme
        ?.bannerVehicleBgImgUrl(darkTheme)
        .orEmpty()
    val backgroundImageUrl = dynamicBackgroundUrl.ifBlank {
        cloudTheme?.loveCarImageUrl.orEmpty()
    }
    val background = AppDesign.colors.page
    val foreground = if (dynamicBackgroundUrl.isNotBlank() && darkTheme) {
        Color.White
    } else {
        AppDesign.colors.onSurface
    }
    val subtitle = if (profile == null) {
        model.connectionLabel
    } else {
        val themeLabel = cloudTheme?.displayName
            ?: if (customized) "绝区零联动车辆" else "标准主题"
        "${model.connectionLabel} · $themeLabel"
    }
    val vehicleImageUrl = profile?.backgroundImageUrl
        ?.takeIf { it.isNotBlank() }
        ?: profile?.vehicleImageUrl.orEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 330.dp)
            .clip(AppDesign.shapes.section)
            .background(background),
    ) {
        if (backgroundImageUrl.isNotBlank() && vehicleBackgroundContent != null) {
            vehicleBackgroundContent(
                backgroundImageUrl,
                Modifier
                    .matchParentSize()
                    .padding(top = vehicleVisualTopOffset),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(AppDesign.colors.surface.copy(alpha = 0.18f)),
            )
        }
        Column(
            modifier = Modifier.padding(
                horizontal = dimensions.itemHorizontalPadding,
                vertical = dimensions.itemVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = profile?.bikeNickName?.ifBlank { null } ?: "我的车辆",
                    style = AppDesign.typography.sectionTitle,
                    color = foreground,
                )
                AppText(
                    text = subtitle,
                    style = AppDesign.typography.summary,
                    color = foreground,
                )
                AppText(
                    text = "$batteryLabel · ${model.lockLabel}",
                    style = AppDesign.typography.title,
                    color = foreground,
                    modifier = Modifier.padding(top = dimensions.compactSpacing),
                )
                AppText(
                    text = "续航 $rangeLabel",
                    style = AppDesign.typography.body,
                    color = foreground,
                )
            }
            if (vehicleImageUrl.isNotBlank() && vehicleImageContent != null) {
                vehicleImageContent(
                    vehicleImageUrl,
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(top = vehicleVisualTopOffset),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(190.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                MetricBlock("车速", model.speedLabel, foreground, Modifier.weight(1f))
                MetricBlock("电量", batteryLabel, foreground, Modifier.weight(1f))
                MetricBlock(
                    "续航",
                    rangeLabel,
                    foreground,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AppText(
            text = value,
            style = AppDesign.typography.title,
            color = color,
        )
        AppText(
            text = label,
            style = AppDesign.typography.caption,
            color = color,
        )
    }
}

private fun Double.formatCoordinate(): String {
    val scaled = kotlin.math.round(this * 1_000_000.0).toLong()
    val whole = scaled / 1_000_000
    val fraction = kotlin.math.abs(scaled % 1_000_000).toString().padStart(6, '0')
    return "$whole.$fraction"
}

private fun Int.versionPart(): String = if (this < 0) "—" else toString()

private fun formatOneDecimal(value: Float): String {
    val scaled = kotlin.math.round(value * 10f).toLong()
    val fraction = kotlin.math.abs(scaled % 10)
    return "${scaled / 10}.$fraction"
}
