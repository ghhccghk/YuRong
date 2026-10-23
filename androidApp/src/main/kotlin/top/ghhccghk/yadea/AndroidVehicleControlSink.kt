package top.ghhccghk.yadea

import top.ghhccghk.yadea.ble.BleAdapter
import top.ghhccghk.yadea.vehicle.VehicleBleConstants
import top.ghhccghk.yadea.vehicle.VehicleControlCommand
import top.ghhccghk.yadea.vehicle.VehicleControlProtocol
import top.ghhccghk.yadea.vehicle.VehicleControlSink

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
        val protocolV1 = runCatching {
            adapter.writeCharacteristic(
                serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_V1,
                characteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_V1,
                data = VehicleControlProtocol.encode(
                    command = command,
                    protocolVersion = 1,
                    bikeBondTypeHex = bikeBondTypeHexProvider(),
                ),
                withResponse = true,
            )
        }.getOrDefault(false)
        if (protocolV1) return true

        return runCatching {
            adapter.writeCharacteristic(
                serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_OTHER,
                characteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_OTHER,
                data = VehicleControlProtocol.payload(command),
                withResponse = true,
            )
        }.getOrDefault(false)
    }
}
