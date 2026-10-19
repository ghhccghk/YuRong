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
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleControlController

/** 实车控制页。写成功只代表指令已发送，车辆状态以通知回报为准。 */
@Composable
fun VehicleControlScreen(
    controller: VehicleControlController,
    connected: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val dimensions = AppDesign.dimensions
    var pendingDanger by remember { mutableStateOf<VehicleControlCommand?>(null) }
    val enabled = connected && state.busyCommand == null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车辆控制",
                subtitle = "通过 BLE 发送车辆经典控制指令",
            )
        }
        if (!connected) {
            item {
                AppTip(
                    kind = AppTipKind.Warning,
                    title = "车辆未连接",
                    message = "请先在“连接”页完成车辆鉴权和初始化。",
                )
            }
        }
        item {
            AppSection(title = "常用操作") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppButton(
                        onClick = { controller.send(VehicleControlCommand.UNLOCK) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("开锁") }
                    AppButton(
                        onClick = { controller.send(VehicleControlCommand.LOCK) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("锁车") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.compactSpacing,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppOutlinedButton(
                        onClick = { controller.send(VehicleControlCommand.FIND) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("寻车") }
                    AppOutlinedButton(
                        onClick = { controller.send(VehicleControlCommand.DETECT) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("车辆检测") }
                }
                state.busyCommand?.let {
                    AppListRow(
                        title = "正在发送",
                        value = it.label,
                    )
                }
            }
        }
        item {
            AppSection(title = "电源与更多") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppOutlinedButton(
                        onClick = { controller.send(VehicleControlCommand.POWER_ON) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("开机") }
                    AppOutlinedButton(
                        onClick = { controller.send(VehicleControlCommand.POWER_OFF) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("关机") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.compactSpacing,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.controlSpacing),
                ) {
                    AppOutlinedButton(
                        onClick = { controller.send(VehicleControlCommand.CUSHION) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("坐垫") }
                    AppOutlinedButton(
                        onClick = {
                            if (connected && state.busyCommand == null) {
                                pendingDanger = VehicleControlCommand.ODO_RESET
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { AppText("ODO 清零") }
                }
            }
        }
        state.message?.let { message ->
            item {
                AppTip(
                    kind = if (message.contains("失败")) AppTipKind.Error else AppTipKind.Info,
                    title = "控制结果",
                    message = message,
                )
            }
        }
    }

    AppAlertDialog(
        show = pendingDanger != null,
        title = "确认清零 ODO",
        message = "该操作会清零车辆总里程，且无法撤销。",
        onDismissRequest = { pendingDanger = null },
        confirmText = "确认清零",
        onConfirm = {
            val command = pendingDanger
            pendingDanger = null
            command?.let(controller::send)
        },
    )
}
