package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.auth.AuthUser
import top.ghhccghk.yadea.vehicle.VehicleBindRequest
import top.ghhccghk.yadea.vehicle.VehicleBindingController
import top.ghhccghk.yadea.vehicle.VehicleProfile

/** 云端绑定车辆列表；点击即可切换当前车辆。 */
@Composable
fun GarageScreen(
    profiles: List<VehicleProfile>,
    selectedVin: String,
    contentPadding: PaddingValues,
    onSelectVehicle: (String) -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    errorMessage: String? = null,
    bindingController: VehicleBindingController? = null,
    currentUser: AuthUser? = null,
    modifier: Modifier = Modifier,
) {
    val dimensions = AppDesign.dimensions
    val bindingState = bindingController?.state?.collectAsState()?.value
    var vin by remember { mutableStateOf("") }
    var vinPwd by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var contactMobile by remember(currentUser?.mobile) {
        mutableStateOf(currentUser?.mobile.orEmpty())
    }
    var contactEmail by remember(currentUser?.email) {
        mutableStateOf(currentUser?.email.orEmpty())
    }
    var mainAccountUnbind by remember { mutableStateOf(true) }
    var confirmUnbind by remember { mutableStateOf(false) }
    val selectedProfile = profiles.firstOrNull { it.vin == selectedVin } ?: profiles.firstOrNull()
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
        item {
            AppSection(title = "云端同步") {
                AppListRow(
                    title = "绑定车辆",
                    summary = errorMessage,
                    value = when {
                        refreshing -> "刷新中…"
                        profiles.isEmpty() -> "暂无"
                        else -> "${profiles.size} 辆"
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
        if (bindingController != null) {
            item {
                AppSection(title = "添加车辆") {
                    AppTextField(
                        value = vin,
                        onValueChange = { vin = it.trim() },
                        label = "VIN",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                        enabled = bindingState?.busy != true,
                        singleLine = true,
                    )
                    AppTextField(
                        value = vinPwd,
                        onValueChange = { vinPwd = it.trim() },
                        label = "车主码 / vinPwd",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                        enabled = bindingState?.busy != true,
                        singleLine = true,
                    )
                    AppTextField(
                        value = smsCode,
                        onValueChange = { smsCode = it.trim() },
                        label = "原车主验证码",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                        enabled = bindingState?.busy != true,
                        singleLine = true,
                    )
                    AppTextField(
                        value = contactMobile,
                        onValueChange = { contactMobile = it.trim() },
                        label = "联系手机号",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                        enabled = bindingState?.busy != true,
                        singleLine = true,
                    )
                    AppTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it.trim() },
                        label = "联系邮箱",
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                        enabled = bindingState?.busy != true,
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.itemVerticalPadding,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                    ) {
                        AppOutlinedButton(
                            onClick = {
                                bindingController.sendOwnerCode(vin, vinPwd)
                            },
                            enabled = bindingState?.busy != true &&
                                vin.isNotBlank() && vinPwd.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText("获取车主验证码")
                        }
                        AppButton(
                            onClick = {
                                bindingController.bind(
                                    VehicleBindRequest(
                                        userId = currentUser?.id ?: 0L,
                                        vin = vin,
                                        vinPwd = vinPwd,
                                        smsCode = smsCode,
                                        primaryUserMobile = contactMobile,
                                        primaryUserEmail = contactEmail,
                                    ),
                                )
                            },
                            enabled = bindingState?.busy != true &&
                                vin.isNotBlank() && vinPwd.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(if (bindingState?.busy == true) "处理中…" else "绑定车辆")
                        }
                    }
                    bindingState?.message?.let { message ->
                        AppText(
                            text = message,
                            style = AppDesign.typography.summary,
                            color = if (bindingState.success) {
                                AppDesign.colors.primary
                            } else {
                                AppDesign.colors.error
                            },
                            modifier = Modifier.padding(
                                horizontal = dimensions.itemHorizontalPadding,
                                vertical = dimensions.compactSpacing,
                            ),
                        )
                    }
                }
            }
        }
        if (profiles.isEmpty()) {
            item {
                AppSection {
                    AppEmptyState(
                        if (refreshing) {
                            "正在向云端查询当前账号绑定的车辆。"
                        } else {
                            "用 Token 登录后点“刷新绑定车辆”；仍为空时请检查 Token 是否完整、是否已过期。"
                        },
                    )
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
                    AppSwitchPreference(
                        checked = mainAccountUnbind,
                        onCheckedChange = { mainAccountUnbind = it },
                        title = "按主账号解绑",
                        summary = "子账号解绑请关闭；解绑后车辆会从当前账号车库移除",
                        enabled = bindingState?.busy != true,
                    )
                    if (bindingController != null && selected.bikeId > 0L) {
                        AppButton(
                            onClick = { confirmUnbind = true },
                            enabled = bindingState?.busy != true,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText("解绑当前车辆")
                        }
                    }
                }
            }
        }
    }

    AppAlertDialog(
        show = confirmUnbind,
        title = "解绑车辆",
        message = "解绑后该车辆会从当前账号的车库移除；重新绑定需要 VIN、车主码和原车主验证码。",
        confirmText = "确认解绑",
        onConfirm = {
            confirmUnbind = false
            selectedProfile?.let { profile ->
                bindingController?.unbind(profile.bikeId, mainAccountUnbind)
            }
        },
        onDismissRequest = { confirmUnbind = false },
    )
}
