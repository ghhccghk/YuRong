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

    private const val VERSION_PREFIX = "01000101"
    private const val QUERY_DEFAULT_FILL_BYTES = 25
    private const val ACTIVATION_0X34_SUFFIX =
        "FFFF0DFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"

    fun encode(protocolVersion: Int, bikeBondTypeHex: String = "C8"): ByteArray? {
        if (protocolVersion != 1) return byteArrayOf(0xFF.toByte())
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX +
                normalizeBondType(bikeBondTypeHex) +
                "FF".repeat(QUERY_DEFAULT_FILL_BYTES),
        )?.hexToBytes()
    }

    /**
     * 对应握手成功后的 `BleControlManager.E(device, 0x34)`。
     *
     * `wi.M(device, 0x34, -1)` 最终调用
     * `generateBleCommand(0x34, -1, 1)`，命中 `pswitch_d22`，
     * 追加 25 字节固定掩码，组帧后为 37 字节。
     */
    fun encodeActivation(protocolVersion: Int, bikeBondTypeHex: String = "C8"): ByteArray? {
        if (protocolVersion != 1) return null
        return VehicleFrame.build(
            orderId = VehicleFrame.ORDER_ID,
            hexPayload = VERSION_PREFIX +
                normalizeBondType(bikeBondTypeHex) +
                ACTIVATION_0X34_SUFFIX,
        )?.hexToBytes()
    }

    private fun normalizeBondType(value: String): String =
        value.trim().uppercase().takeIf { it.length == 2 } ?: "C8"
}
