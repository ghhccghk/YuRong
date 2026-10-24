package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import top.ghhccghk.yadea.vehicle.VehicleControlChannel
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleControlController

/** 实车控制页。写成功只代表指令已发送，车辆状态以通知回报为准。 */
@Composable
fun VehicleControlScreen(
    controller: VehicleControlController,
    connected: Boolean,
    cloudAvailable: Boolean,
    cloudUnavailableReason: String,
    onRefreshCloudStatus: (() -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val dimensions = AppDesign.dimensions
    var pendingDanger by remember { mutableStateOf<VehicleControlCommand?>(null) }
    var channel by remember {
        mutableStateOf(
            if (!connected && cloudAvailable) VehicleControlChannel.CLOUD
            else VehicleControlChannel.BLE,
        )
    }
    LaunchedEffect(connected, cloudAvailable) {
        if (channel == VehicleControlChannel.BLE && !connected && cloudAvailable) {
            channel = VehicleControlChannel.CLOUD
        } else if (channel == VehicleControlChannel.CLOUD && !cloudAvailable && connected) {
            channel = VehicleControlChannel.BLE
        }
    }
    val channelOptions = listOf(VehicleControlChannel.BLE, VehicleControlChannel.CLOUD)
    val ready = state.busyCommand == null
    fun commandEnabled(command: VehicleControlCommand): Boolean =
        ready && when (channel) {
            VehicleControlChannel.BLE -> connected
            VehicleControlChannel.CLOUD -> cloudAvailable && command.cloudControlId != null
        }
    fun send(command: VehicleControlCommand) {
        controller.send(command, channel)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
    ) {
        item {
            AppPageHeader(
                title = "车辆控制",
                subtitle = "可选择蓝牙或车联网下发车辆控制指令",
            )
        }
        if (!connected && !cloudAvailable) {
            item {
                AppTip(
                    kind = AppTipKind.Warning,
                    title = "没有可用控制通道",
                    message = if (cloudUnavailableReason.isNotBlank()) {
                        "${cloudUnavailableReason}，请连接车辆蓝牙后再试。"
                    } else {
                        "请先连接车辆蓝牙，或等待车辆车联网状态在线。"
                    },
                )
            }
        }
        item {
            AppSection(title = "下发通道") {
                AppSegmentedControl(
                    options = channelOptions.map(VehicleControlChannel::label),
                    selectedIndex = channelOptions.indexOf(channel),
                    onSelected = { channel = channelOptions[it] },
                    enabled = { index ->
                        when (channelOptions[index]) {
                            VehicleControlChannel.BLE -> connected
                            VehicleControlChannel.CLOUD -> cloudAvailable
                        }
                    },
                    modifier = Modifier.padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.itemVerticalPadding,
                    ),
                )
                AppText(
                    text = when (channel) {
                        VehicleControlChannel.BLE ->
                            "通过手机与车辆蓝牙直接下发，需要靠近车辆并保持连接。"
                        VehicleControlChannel.CLOUD ->
                            "通过车联网云平台下发，不要求手机靠近，但车辆必须在线，且当前仅开放开锁和锁车。"
                    },
                    style = AppDesign.typography.caption,
                    modifier = Modifier.padding(
                        horizontal = dimensions.itemHorizontalPadding,
                        vertical = dimensions.compactSpacing,
                    ),
                )
                if (!cloudAvailable && cloudUnavailableReason.isNotBlank()) {
                    AppText(
                        text = cloudUnavailableReason,
                        style = AppDesign.typography.caption,
                        modifier = Modifier.padding(
                            horizontal = dimensions.itemHorizontalPadding,
                            vertical = dimensions.compactSpacing,
                        ),
                    )
                    onRefreshCloudStatus?.let { refresh ->
                        AppOutlinedButton(
                            onClick = refresh,
                            enabled = state.busyCommand == null,
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = dimensions.itemHorizontalPadding,
                            ),
                        ) {
                            AppText("刷新车联网状态")
                        }
                    }
                }
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
                        onClick = { send(VehicleControlCommand.UNLOCK) },
                        enabled = commandEnabled(VehicleControlCommand.UNLOCK),
                        modifier = Modifier.weight(1f),
                    ) { AppText("开锁") }
                    AppButton(
                        onClick = { send(VehicleControlCommand.LOCK) },
                        enabled = commandEnabled(VehicleControlCommand.LOCK),
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
                        onClick = { send(VehicleControlCommand.FIND) },
                        enabled = commandEnabled(VehicleControlCommand.FIND),
                        modifier = Modifier.weight(1f),
                    ) { AppText("寻车") }
                    AppOutlinedButton(
                        onClick = { send(VehicleControlCommand.DETECT) },
                        enabled = commandEnabled(VehicleControlCommand.DETECT),
                        modifier = Modifier.weight(1f),
                    ) { AppText("车辆检测") }
                }
                state.busyCommand?.let {
                    AppListRow(
                        title = "正在发送",
                        value = "${it.label} · ${state.busyChannel?.label.orEmpty()}",
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
                        onClick = { send(VehicleControlCommand.POWER_ON) },
                        enabled = commandEnabled(VehicleControlCommand.POWER_ON),
                        modifier = Modifier.weight(1f),
                    ) { AppText("开机") }
                    AppOutlinedButton(
                        onClick = { send(VehicleControlCommand.POWER_OFF) },
                        enabled = commandEnabled(VehicleControlCommand.POWER_OFF),
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
                        onClick = { send(VehicleControlCommand.CUSHION) },
                        enabled = commandEnabled(VehicleControlCommand.CUSHION),
                        modifier = Modifier.weight(1f),
                    ) { AppText("坐垫") }
                    AppOutlinedButton(
                        onClick = {
                            if (commandEnabled(VehicleControlCommand.ODO_RESET)) {
                                pendingDanger = VehicleControlCommand.ODO_RESET
                            }
                        },
                        enabled = commandEnabled(VehicleControlCommand.ODO_RESET),
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
            command?.let(::send)
        },
    )
}
