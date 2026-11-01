package top.ghhccghk.yadea

import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.carsetting.VehicleSettingCommand
import top.ghhccghk.yadea.carsetting.VehicleSettingCommandSink
import top.ghhccghk.yadea.carsetting.VehicleSettingFrameEncoder
import top.ghhccghk.yadea.vehicle.writeVehicleCommandFrame

/** 把设置命令编码成 5944…4B4A 帧并写入车辆协议命令特征。 */
class AndroidVehicleSettingCommandSink(
    private val adapter: BleAdapter,
) : VehicleSettingCommandSink {
    override suspend fun send(command: VehicleSettingCommand): Boolean {
        val frame = VehicleSettingFrameEncoder.encode(command) ?: return false
        return adapter.writeVehicleCommandFrame(
            protocolV1Frame = frame,
            withResponse = false,
        )
    }
}
