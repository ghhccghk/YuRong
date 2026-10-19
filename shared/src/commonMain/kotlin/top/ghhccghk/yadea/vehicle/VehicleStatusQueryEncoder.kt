package top.ghhccghk.yadea.vehicle

/**
 * 初始化任务里的整车状态查询命令。
 *
 * 协议版本 1 走 `BleBicycleCommandManager.generateBleCommand(-1, 0x34, 1)`：
 * 版本前缀使用原命令管理器的默认值 `01000101F8`，0x34 分支追加固定掩码。
 * 其他协议版本在 smali 中直接返回裸字节 `FF`。
 */
internal object VehicleStatusQueryEncoder {

    private const val VERSION_PREFIX = "01000101F8"
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
