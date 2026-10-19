package top.ghhccghk.yadea

import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.vehicle.VehicleBleConstants
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleControlProtocol
import top.ghhccghk.yadea.vehicle.VehicleControlSink

/** 把车辆经典命令的裸载荷写入 b360/b362。 */
class AndroidVehicleControlSink(
    private val adapter: BleAdapter,
) : VehicleControlSink {
    override suspend fun send(command: VehicleControlCommand): Boolean =
        adapter.writeCharacteristic(
            serviceUuid = VehicleBleConstants.SERVICE_MAIN,
            characteristicUuid = VehicleBleConstants.CHAR_MAIN,
            data = VehicleControlProtocol.payload(command),
            withResponse = true,
        )
}
