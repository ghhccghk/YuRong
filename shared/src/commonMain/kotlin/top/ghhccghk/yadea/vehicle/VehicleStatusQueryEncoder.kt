package top.ghhccghk.yadea.vehicle

/**
 * 初始化任务里的整车状态查询命令。
 *
 * 协议版本 1 走 `BleBicycleCommandManager.generateBleCommand(-1, 0x34, 1)`：
 * 前 8 位是 BLE/App 版本 `01000101`，随后是
 * `"11" + bikeBondType("001") + appType("000")` 转出的 `C8`。
 * 其他协议版本在 smali 中直接返回裸字节 `FF`。
 */
internal object VehicleStatusQueryEncoder {

    private const val VERSION_PREFIX = "01000101C8"
    private const val QUERY_0X34_SUFFIX =
        "FFFF0DFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"

    fun encode(protocolVersion: Int): ByteArray? {
        if (protocolVersion != 1) return byteArrayOf(0xFF.toByte())
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX + QUERY_0X34_SUFFIX,
        )?.hexToBytes()
    }
}
