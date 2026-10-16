package top.ghhccghk.yadea.vehicle

/**
 * 车辆 BLE 控制帧。
 *
 * ```text
 * frame = "5944" + ORDER_ID + LEN + PAYLOAD + CHECK + "4B4A"
 * CHECK = SUM8(ORDER_ID + LEN + PAYLOAD)
 * ```
 *
 * 三点与原实现保持一致：
 *
 * 1. **长度字段是 payload 的字节数**。原实现里 payload 是 hex 字符串，
 *    因此取 `payload.length / 2`。这里对外暴露 hex 字符串入口，
 *    同时提供 `ByteArray` 重载自动换算。
 * 2. **校验覆盖 `ORDER_ID + LEN + PAYLOAD`**，不含 `5944` 与 `4B4A`。
 * 3. **最终整体转大写**。
 *
 * > 投屏帧（`YadeaProtocol`）同样使用 `5944` / `4B4A`，但协议 ID 是 `3C`、
 * > 长度是 BE32 定长 4 字节、校验覆盖范围也不同。**两套实现必须彼此独立。**
 */
internal object VehicleFrame {

    const val HEAD = "5944"
    const val TAIL = "4B4A"

    /** 普通控制 / 查询。 */
    const val ORDER_ID = "3E"

    /** 外部命令。 */
    const val EXTERNAL_ORDER_ID = "3A"

    /** 3B 专用帧。 */
    const val ORDER_ID_3B = "3B"

    /** 原实现里的默认长度字段，组帧时并不直接使用。 */
    const val ORDER_LENGTH = "1E"

    /** 3B 专用帧的固定中段。 */
    private const val ORDER_3B_MARKER = "FFFFFFFFFF"

    /** 填充字节上限，对应 `increaseControlByte` 的钳制值。 */
    const val MAX_INCREASE_BYTES = 10

    /**
     * 用 hex 字符串载荷组帧。
     *
     * @param orderId `ORDER_ID` / `EXTERNAL_ORDER_ID` / `ORDER_ID_3B` 之一
     * @param hexPayload 大写或小写 hex 字符串，长度须为偶数
     * @return 大写 hex 帧；载荷非法时返回 `null`
     */
    fun build(orderId: String, hexPayload: String): String? {
        if (hexPayload.length % 2 != 0) return null
        if (hexPayload.hexToBytes() == null) return null

        val byteLen = hexPayload.length / 2
        val body = orderId + byteLen.toDecHex() + hexPayload
        val check = checksum(body) ?: return null
        return (HEAD + body + check + TAIL).uppercase()
    }

    /** 用字节数组载荷组帧。 */
    fun build(orderId: String, payload: ByteArray): ByteArray? =
        build(orderId, payload.toHex())?.hexToBytes()

    /**
     * 计算校验字段。
     *
     * @param body `ORDER_ID + LEN + PAYLOAD` 的 hex 字符串
     */
    fun checksum(body: String): String? {
        val bytes = body.hexToBytes() ?: return null
        return Checksums.sum8(bytes).toCheckHex()
    }

    /**
     * 3B 专用帧。
     *
     * 载荷构造顺序：`hexArg` 每 2 字符**反向**拼接 → 固定 `FFFFFFFFFF` → `increaseByte` 个 `FF`。
     *
     * @param increaseByte 追加的填充字节数，钳制到 [MAX_INCREASE_BYTES]
     */
    fun build3B(commandType: Int, hexArg: String, increaseByte: Int): ByteArray? {
        if (commandType != 0x13B) return null
        if (hexArg.length % 2 != 0) return null

        val reversed = reverseHexPairs(hexArg) ?: return null
        val payload = reversed + ORDER_3B_MARKER + increaseBytesHex(increaseByte)
        return build(ORDER_ID_3B, payload)?.hexToBytes()
    }

    /** 每 2 个字符一组，按组反向拼接。`"AABBCCDD"` → `"DDCCBBAA"`。 */
    internal fun reverseHexPairs(hex: String): String? {
        if (hex.length % 2 != 0) return null
        val sb = StringBuilder(hex.length)
        var i = hex.length - 2
        while (i >= 0) {
            sb.append(hex, i, i + 2)
            i -= 2
        }
        return sb.toString()
    }

    /**
     * 对应 `increaseControlByte` + `getIncreaseByteHexStr` 的组合结果。
     *
     * `n` 先钳制到 `[0, MAX_INCREASE_BYTES]`，再产出同样数量的 `FF`。
     */
    internal fun increaseBytesHex(n: Int): String =
        "FF".repeat(n.coerceIn(0, MAX_INCREASE_BYTES))
}
