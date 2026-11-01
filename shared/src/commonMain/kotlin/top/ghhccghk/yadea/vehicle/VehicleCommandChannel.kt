package top.ghhccghk.yadea.vehicle

import top.ghhccghk.yadea.ble.BleAdapter

/**
 * 向车辆协议命令通道写入一帧。
 *
 * 原 App 的 `BleControlManager.t/E -> wi.M` 路径会按
 * `bleProtocolVersionName` 选择状态服务的写特征：
 * - 协议 1：`e7810bd2 / bef8e821`
 * - 其他：`e7810b82 / bef8e7da`
 *
 * V1 密码帧和旧协议裸命令可能不同，因此两个版本分别传入帧；大多数设置
 * 帧在两种协议下格式相同，可以省略 [otherProtocolFrame]。
 */
suspend fun BleAdapter.writeVehicleCommandFrame(
    protocolV1Frame: ByteArray,
    otherProtocolFrame: ByteArray = protocolV1Frame,
    withResponse: Boolean = false,
): Boolean {
    val protocolV1Sent = runCatching {
        writeCharacteristic(
            serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_V1,
            characteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_V1,
            data = protocolV1Frame,
            withResponse = withResponse,
        )
    }.getOrDefault(false)
    if (protocolV1Sent) return true

    return runCatching {
        writeCharacteristic(
            serviceUuid = VehicleBleConstants.SERVICE_NOTIFY_OTHER,
            characteristicUuid = VehicleBleConstants.CHAR_STATUS_QUERY_OTHER,
            data = otherProtocolFrame,
            withResponse = withResponse,
        )
    }.getOrDefault(false)
}
