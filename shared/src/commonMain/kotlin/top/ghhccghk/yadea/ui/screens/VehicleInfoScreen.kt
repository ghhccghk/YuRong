package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehicleInfoUiModel

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
    modifier: Modifier = Modifier,
) {
    val model = VehicleInfoFormatter.format(status, connected)
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
            VehicleHeroCard(model)
        }
        if (status == null) {
            item {
                AppSection(title = "等待车辆状态") {
                    AppEmptyState(
                        message = if (connected) {
                            "车辆已连接，正在等待首个状态报文。"
                        } else {
                            "尚未连接车辆。请先在“连接”页完成 BLE 连接。"
                        },
                    )
                }
            }
        } else {
            item {
                AppSection(title = "行驶与电池") {
                    AppListRow("车速", value = model.speedLabel, showDivider = true)
                    AppListRow("剩余里程", value = model.rangeLabel, showDivider = true)
                    AppListRow("总里程", value = model.odoLabel, showDivider = true)
                    AppListRow("单次里程", value = model.tripLabel, showDivider = true)
                    AppListRow("电池电量", value = model.batteryLabel, showDivider = true)
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
private fun VehicleHeroCard(model: VehicleInfoUiModel) {
    val dimensions = AppDesign.dimensions
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = AppDesign.colors.primaryContainer,
        contentColor = AppDesign.colors.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimensions.itemHorizontalPadding,
                vertical = dimensions.itemVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(dimensions.compactSpacing),
        ) {
            AppText(
                text = model.connectionLabel,
                style = AppDesign.typography.summary,
                color = AppDesign.colors.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                MetricBlock(label = "车速", value = model.speedLabel, modifier = Modifier.weight(1f))
                MetricBlock(label = "电量", value = model.batteryLabel, modifier = Modifier.weight(1f))
                MetricBlock(label = "续航", value = model.rangeLabel, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        AppText(
            text = value,
            style = AppDesign.typography.title,
            color = AppDesign.colors.onPrimaryContainer,
        )
        AppText(
            text = label,
            style = AppDesign.typography.caption,
            color = AppDesign.colors.onPrimaryContainer,
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
