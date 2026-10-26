package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VehicleControlCommand(
    val label: String,
    val description: String,
    val commandId: Int,
    val cloudControlId: String? = null,
    val dangerous: Boolean = false,
) {
    UNLOCK("开锁", "解除车辆防盗锁", 0x04, cloudControlId = "RELEASE_FORTIFY"),
    LOCK("锁车", "开启防盗锁", 0x06, cloudControlId = "FORTIFY"),
    POWER_ON("开机", "接通车辆电源", 0x08, cloudControlId = "START"),
    POWER_OFF("关机", "关闭车辆电源", 0x07, cloudControlId = "STOP"),
    FIND("寻车", "鸣笛提示车辆位置", 0x03, cloudControlId = "FIND"),
    DETECT("检测", "触发车辆自检", 0x05),
    ODO_RESET("ODO 清零", "清零总里程，不可撤销", 0x31, dangerous = true),
    CUSHION("坐垫", "控制坐垫开关", 0x09),
}

/**
 * 车辆经典命令编码。
 *
 * 协议版本 1 使用 `5944 + 3E + LEN + PAYLOAD + CHECK + 4B4A` 完整帧；
 * 其他协议版本保留 smali 返回的 3 字节裸命令。
 */
object VehicleControlProtocol {
    private const val VERSION_PREFIX = VehicleBleProtocolVersion.ORIGINAL_APP_PREFIX

    fun payload(command: VehicleControlCommand): ByteArray {
        if (command == VehicleControlCommand.DETECT) {
            error("车辆自检应走云端接口，不能编码为 BLE 控车命令")
        }
        return ClassicCommands.byCommandId(command.commandId)?.copyOf()
            ?: error("Unsupported vehicle command: ${command.commandId}")
    }

    /**
     * 按 `BleBicycleCommandManager.generateBleCommand(commandId, -1, protocolVersion)`
     * 的已确认分支编码。
     */
    fun encode(
        command: VehicleControlCommand,
        protocolVersion: Int,
        bikeBondTypeHex: String = "C8",
    ): ByteArray {
        if (protocolVersion != 1) return payload(command)
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX +
                normalizeBondType(bikeBondTypeHex) +
                protocolV1Suffix(command),
        )?.hexToBytes() ?: error("Failed to encode vehicle command: ${command.commandId}")
    }

    private fun normalizeBondType(value: String): String =
        value.trim().uppercase().takeIf { it.length == 2 } ?: "C8"

    private fun protocolV1Suffix(command: VehicleControlCommand): String = when (command) {
        VehicleControlCommand.UNLOCK ->
            "FFFF01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.LOCK ->
            "FFFF04FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.POWER_ON ->
            // 原 App `BicycleControlHelper` 的 `START`：command=1。
            "FFFF02FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.POWER_OFF ->
            // 原 App `BicycleControlHelper` 的 `STOP`：command=2。
            "FFFF03FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.FIND ->
            // 原 App `BicycleControlHelper` 的 `FIND`：command=5。
            "FFFDFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.DETECT ->
            // 原 App 的 DETECT 走 `bikeFaultSingle/selfInspection`，不是 BLE 控车帧。
            error("车辆自检应走云端接口，不能编码为 BLE 控车命令")
        VehicleControlCommand.ODO_RESET ->
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFDFFFFFFFFFFFFFFFFFFFFF"
        VehicleControlCommand.CUSHION ->
            "FFFF07FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
    }
}

interface VehicleControlSink {
    suspend fun send(command: VehicleControlCommand): Boolean
}

enum class VehicleControlChannel(val label: String) {
    BLE("蓝牙"),
    CLOUD("车联网"),
}

data class VehicleCloudControlResult(
    val success: Boolean,
    val message: String = "",
)

interface VehicleCloudControlSink {
    suspend fun send(
        vin: String,
        command: VehicleControlCommand,
    ): VehicleCloudControlResult
}

data class VehicleControlState(
    val busyCommand: VehicleControlCommand? = null,
    val busyChannel: VehicleControlChannel? = null,
    val message: String? = null,
)

/**
 * 车辆控制操作状态。
 *
 * 写入成功只代表 GATT 写完成，不代表车辆已经执行；最终状态以 TTP 通知为准。
 */
class VehicleControlController(
    private val sink: VehicleControlSink?,
    private val connectedProvider: () -> Boolean,
    private val scope: CoroutineScope,
    private val cloudSink: VehicleCloudControlSink? = null,
    private val cloudOnlineProvider: () -> Boolean = { false },
    private val vinProvider: () -> String = { "" },
    private val preferCloudProvider: (VehicleControlCommand) -> Boolean = { false },
    private val onCloudCommandSent: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(VehicleControlState())
    val state: StateFlow<VehicleControlState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * 自动路由：配置为车联网优先的命令只走云端；其余命令在蓝牙可用时优先
     * 蓝牙，蓝牙不可用时再使用在线车辆的车联网通道。
     */
    fun resolveChannel(command: VehicleControlCommand): VehicleControlChannel? {
        if (preferCloudProvider(command)) {
            return VehicleControlChannel.CLOUD.takeIf { cloudAvailable(command) }
        }
        if (bleAvailable()) return VehicleControlChannel.BLE
        return VehicleControlChannel.CLOUD.takeIf { cloudAvailable(command) }
    }

    fun canSend(command: VehicleControlCommand): Boolean = resolveChannel(command) != null

    fun send(
        command: VehicleControlCommand,
        requestedChannel: VehicleControlChannel? = null,
    ) {
        if (job?.isActive == true) return
        val channel = requestedChannel ?: resolveChannel(command)
        if (channel == null) {
            _state.value = VehicleControlState(
                message = autoUnavailableMessage(command),
            )
            return
        }
        if (requestedChannel != null) {
            when (channel) {
                VehicleControlChannel.BLE -> if (!bleAvailable()) {
                    _state.value = VehicleControlState(
                        message = if (sink == null) "蓝牙控制通道未接入" else "请先连接车辆蓝牙",
                    )
                    return
                }

                VehicleControlChannel.CLOUD -> if (!cloudAvailable(command)) {
                    _state.value = VehicleControlState(message = cloudUnavailableMessage(command))
                    return
                }
            }
        }
        val allowCloudFallback = requestedChannel == null &&
            !preferCloudProvider(command)

        job = scope.launch {
            when (channel) {
                VehicleControlChannel.BLE ->
                    sendViaBle(command, allowCloudFallback)
                VehicleControlChannel.CLOUD ->
                    sendViaCloud(command)
            }
        }
    }

    private suspend fun sendViaBle(
        command: VehicleControlCommand,
        allowCloudFallback: Boolean,
    ) {
        _state.value = VehicleControlState(
            busyCommand = command,
            busyChannel = VehicleControlChannel.BLE,
        )
        val success = runCatching {
            requireNotNull(sink).send(command)
        }.getOrDefault(false)
        if (success) {
            _state.value = VehicleControlState(
                message = "${command.label}指令已通过蓝牙发送，等待车辆状态回报",
            )
            return
        }
        if (allowCloudFallback && cloudAvailable(command)) {
            sendViaCloud(command, failurePrefix = "蓝牙发送失败，已自动切换车联网；")
            return
        }
        _state.value = VehicleControlState(
            message = "${command.label}蓝牙发送失败，且当前无法切换车联网",
        )
    }

    private suspend fun sendViaCloud(
        command: VehicleControlCommand,
        failurePrefix: String = "",
    ) {
        _state.value = VehicleControlState(
            busyCommand = command,
            busyChannel = VehicleControlChannel.CLOUD,
        )
        val vin = vinProvider().trim()
        val result = runCatching {
            requireNotNull(cloudSink).send(vin, command)
        }.getOrElse {
            VehicleCloudControlResult(false, it.message.orEmpty())
        }
        _state.value = VehicleControlState(
            message = if (result.success) {
                onCloudCommandSent(vin)
                "${failurePrefix}${command.label}指令已通过车联网下发，等待车辆状态更新"
            } else {
                failurePrefix +
                    result.message.ifBlank { "${command.label}车联网下发失败" }
            },
        )
    }

    private fun bleAvailable(): Boolean =
        sink != null && connectedProvider()

    private fun cloudAvailable(command: VehicleControlCommand): Boolean =
        cloudSink != null &&
            command.cloudControlId != null &&
            cloudOnlineProvider() &&
            vinProvider().isNotBlank()

    private fun autoUnavailableMessage(command: VehicleControlCommand): String {
        if (preferCloudProvider(command)) {
            return "${command.label}配置为车联网控制，${cloudUnavailableMessage(command)}"
        }
        if (command.cloudControlId == null && !bleAvailable()) {
            return "${command.label}仅支持蓝牙控制，请先连接车辆蓝牙"
        }
        if (sink == null && cloudSink == null) {
            return "蓝牙和车联网控制通道均未接入"
        }
        if (!connectedProvider() && !cloudOnlineProvider()) {
            return "请先连接车辆蓝牙，或等待车辆车联网在线"
        }
        return "${command.label}当前没有可用控制通道"
    }

    private fun cloudUnavailableMessage(command: VehicleControlCommand): String = when {
        cloudSink == null -> "车联网控制通道未接入"
        command.cloudControlId == null -> "${command.label}暂不支持车联网下发"
        vinProvider().isBlank() -> "缺少车辆 VIN"
        !cloudOnlineProvider() -> "车辆当前不在线，无法使用车联网"
        else -> "${command.label}当前无法使用车联网"
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dispose() {
        job?.cancel()
        job = null
    }
}
