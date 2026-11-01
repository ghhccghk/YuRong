package top.ghhccghk.yadea

import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleControlProtocol
import top.ghhccghk.yadea.vehicle.VehicleControlSink
import top.ghhccghk.yadea.vehicle.writeVehicleCommandFrame

/**
 * 控车写入通道。
 *
 * 原 App 在协议版本 1 下写入 `e7810bd2/bef8e821`，并在
 * `BleBicycleCommandManager` 内封装完整车辆帧；版本不匹配时才
 * 回退到旧协议特征和 3 字节裸命令。
 */
class AndroidVehicleControlSink(
    private val adapter: BleAdapter,
    private val bikeBondTypeHexProvider: () -> String = { "C8" },
) : VehicleControlSink {
    override suspend fun send(command: VehicleControlCommand): Boolean {
        return adapter.writeVehicleCommandFrame(
            protocolV1Frame = VehicleControlProtocol.encode(
                command = command,
                protocolVersion = 1,
                bikeBondTypeHex = bikeBondTypeHexProvider(),
            ),
            otherProtocolFrame = VehicleControlProtocol.payload(command),
            withResponse = false,
        )
    }
}
