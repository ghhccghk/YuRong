package top.ghhccghk.yadea

import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.carsetting.VehicleSettingCommand
import top.ghhccghk.yadea.carsetting.VehicleSettingCommandSink
import top.ghhccghk.yadea.carsetting.VehicleSettingFrameEncoder
import top.ghhccghk.yadea.vehicle.VehicleBleConstants

/** 把设置命令编码成 5944…4B4A 帧并写入整车主特征。 */
class AndroidVehicleSettingCommandSink(
    private val adapter: BleAdapter,
) : VehicleSettingCommandSink {
    override suspend fun send(command: VehicleSettingCommand): Boolean {
        val frame = VehicleSettingFrameEncoder.encode(command) ?: return false
        return adapter.writeCharacteristic(
            serviceUuid = VehicleBleConstants.SERVICE_MAIN,
            characteristicUuid = VehicleBleConstants.CHAR_MAIN,
            data = frame,
            withResponse = true,
        )
    }
}
