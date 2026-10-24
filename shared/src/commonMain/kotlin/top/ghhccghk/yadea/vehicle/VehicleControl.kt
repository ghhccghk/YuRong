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
    val dangerous: Boolean = false,
) {
    UNLOCK("开锁", "解除车辆防盗锁", 0x04),
    LOCK("锁车", "开启防盗锁", 0x06),
    POWER_ON("开机", "接通车辆电源", 0x08),
    POWER_OFF("关机", "关闭车辆电源", 0x07),
    FIND("寻车", "鸣笛提示车辆位置", 0x03),
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

data class VehicleControlState(
    val busyCommand: VehicleControlCommand? = null,
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
) {
    private val _state = MutableStateFlow(VehicleControlState())
    val state: StateFlow<VehicleControlState> = _state.asStateFlow()

    private var job: Job? = null

    fun send(command: VehicleControlCommand) {
        if (job?.isActive == true) return
        val activeSink = sink
        if (activeSink == null) {
            _state.value = VehicleControlState(message = "车辆控制通道未接入")
            return
        }
        if (!connectedProvider()) {
            _state.value = VehicleControlState(message = "请先连接车辆")
            return
        }
        job = scope.launch {
            _state.value = VehicleControlState(busyCommand = command)
            val success = runCatching { activeSink.send(command) }.getOrDefault(false)
            _state.value = VehicleControlState(
                message = if (success) {
                    "${command.label}指令已发送，等待车辆状态回报"
                } else {
                    "${command.label}发送失败"
                },
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dispose() {
        job?.cancel()
        job = null
    }
}
