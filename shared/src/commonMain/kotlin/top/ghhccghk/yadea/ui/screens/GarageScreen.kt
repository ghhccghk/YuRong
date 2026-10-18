package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.vehicle.VehicleProfile

/** 云端绑定车辆列表；点击即可切换当前车辆。 */
@Composable
fun GarageScreen(
    profiles: List<VehicleProfile>,
    selectedVin: String,
    contentPadding: PaddingValues,
    onSelectVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = AppDesign.dimensions
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车库",
                subtitle = "来自账号绑定车辆的云端资料；切换后车辆信息与云端设置会按 VIN 更新",
            )
        }
        if (profiles.isEmpty()) {
            item {
                AppSection {
                    AppEmptyState("登录并绑定车辆后，车库会显示云端返回的车辆列表。")
                }
            }
        } else {
            item {
                AppSection(title = "绑定车辆") {
                    profiles.forEachIndexed { index, profile ->
                        AppListRow(
                            title = profile.bikeNickName.ifBlank {
                                profile.modelName.ifBlank { "未命名车辆" }
                            },
                            summary = listOfNotNull(
                                profile.vin.takeIf { it.isNotBlank() }?.let { "VIN $it" },
                                profile.modelNo.takeIf { it.isNotBlank() },
                                if (profile.isLinkedModel) "联动款" else null,
                            ).joinToString(" · "),
                            value = if (profile.vin == selectedVin) "当前车辆" else "切换",
                            showDivider = index != profiles.lastIndex,
                            onClick = { onSelectVehicle(profile.vin) },
                        )
                    }
                }
            }
            val selected = profiles.firstOrNull { it.vin == selectedVin } ?: profiles.first()
            item {
                AppSection(title = "当前车辆") {
                    AppListRow(
                        title = "车型",
                        value = selected.modelName.ifBlank { selected.modelNo.ifBlank { "—" } },
                        showDivider = true,
                    )
                    AppListRow(
                        title = "VIN",
                        value = selected.vin.ifBlank { "—" },
                        showDivider = true,
                    )
                    AppListRow(
                        title = "车联网服务",
                        value = when {
                            selected.iotService == null -> "—"
                            selected.iotService.isExpired -> "已到期"
                            else -> "正常"
                        },
                        summary = selected.iotService?.cardExpireTime
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "到期时间 $it" },
                    )
                }
            }
        }
    }
}
