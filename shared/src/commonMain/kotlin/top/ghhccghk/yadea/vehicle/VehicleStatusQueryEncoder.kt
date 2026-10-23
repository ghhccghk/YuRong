package top.ghhccghk.yadea.vehicle

/**
 * 初始化任务里的整车状态查询命令。
 *
 * 协议版本 1 走 `BleBicycleCommandManager.generateBleCommand(-1, 0x34, 1)`。
 * 第一个参数是命令类型，`-1` 不命中任何专用分支，因此走默认的
 * 25 字节全 `FF` 掩码；第二个参数 `0x34` 只作为参数传入。
 *
 * 前 8 位是 BLE/App 版本 `01000101`，随后是
 * `"11" + bikeBondType("001") + appType("000")` 转出的 `C8`。
 * 其他协议版本在 smali 中直接返回裸字节 `FF`。
 */
internal object VehicleStatusQueryEncoder {

    private const val VERSION_PREFIX = "01000101C8"
    private const val QUERY_DEFAULT_FILL_BYTES = 25
    private const val ACTIVATION_COMMAND_FILL_BYTES = 52

    fun encode(protocolVersion: Int): ByteArray? {
        if (protocolVersion != 1) return byteArrayOf(0xFF.toByte())
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX + "FF".repeat(QUERY_DEFAULT_FILL_BYTES),
        )?.hexToBytes()
    }

    /**
     * 对应握手成功后的 `BleControlManager.E(device, 0x34)`。
     *
     * `wi.M(device, 0x34, -1)` 最终调用
     * `generateBleCommand(0x34, -1, 1)`，命中 `uj.c(0x34, -1, 6, 7)`，
     * 生成 57 字节载荷和 64 字节完整帧。
     */
    fun encodeActivation(protocolVersion: Int): ByteArray? {
        if (protocolVersion != 1) return null
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX + "FF".repeat(ACTIVATION_COMMAND_FILL_BYTES),
        )?.hexToBytes()
    }
}
