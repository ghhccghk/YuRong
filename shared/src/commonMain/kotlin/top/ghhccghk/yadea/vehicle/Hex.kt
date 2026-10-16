package top.ghhccghk.yadea.vehicle

private const val HEX_UPPER = "0123456789ABCDEF"

/**
 * 车辆协议用到的十六进制工具。
 *
 * 这里刻意提供**三个**转换函数，因为原实现（`BleBicycleCommandManager`）里
 * 它们的补零判据各不相同，混用会让帧长度字段算错：
 *
 * | 函数 | 判据 | 补零 | 大写 |
 * | --- | --- | --- | --- |
 * | [toDecHex] | 长度奇数 | 补 1 个 | 是 |
 * | [toCheckHex] | 长度等于 1 | 补 1 个 | 否（外层统一转） |
 * | [binaryToHex] | 输入须为 8 的倍数 | 不补 | 否 |
 */

/** 单字节 → 2 位大写 hex。 */
internal fun Byte.toHex2(): String {
    val v = toInt() and 0xFF
    return charArrayOf(HEX_UPPER[v ushr 4], HEX_UPPER[v and 0x0F]).concatToString()
}

/** 字节数组 → 大写 hex 字符串。 */
internal fun ByteArray.toHex(): String = buildString(size * 2) {
    for (b in this@toHex) append(b.toHex2())
}

/**
 * hex 字符串 → 字节数组。
 *
 * 长度奇数或含非法字符时返回 `null`。原实现在这种情况下会抛异常并被
 * `catch (Exception)` 吞掉，这里改成显式返回 `null`，便于调用方决定如何处理。
 */
internal fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = HEX_UPPER.indexOf(this[i * 2].uppercaseChar())
        val lo = HEX_UPPER.indexOf(this[i * 2 + 1].uppercaseChar())
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

/**
 * 对应原实现的 `decInt2hexString`。
 *
 * **长度为奇数时补一个 `0`，然后转大写。** 不是"原样输出"。
 * `1` → `"01"`，`15` → `"0F"`，`16` → `"10"`，`256` → `"0100"`。
 */
internal fun Int.toDecHex(): String {
    val s = toString(16)
    return if (s.length % 2 == 1) "0$s".uppercase() else s.uppercase()
}

/**
 * 对应原实现的 `getCheckResult` 内部转换。
 *
 * **判据是长度等于 1**（不是奇数），且**不转大写**（由组帧时统一处理）。
 * 对 SUM8（最多 2 位）而言与 [toDecHex] 结果等价，但两者语义不同，必须分开。
 */
internal fun Int.toCheckHex(): String {
    val s = toString(16)
    return if (s.length == 1) "0$s" else s
}

/**
 * 对应原实现的 `binaryString2hexString`。
 *
 * 每 4 位二进制转 1 个 hex 字符，**不补零、不转大写**。
 * 输入长度必须是 8 的倍数，否则返回 `null`（原实现同样返回 `null`）。
 */
internal fun String.binaryToHex(): String? {
    if (isEmpty() || length % 8 != 0) return null
    val sb = StringBuilder(length / 4)
    var i = 0
    while (i < length) {
        var v = 0
        for (j in 0 until 4) {
            val bit = this[i + j]
            if (bit != '0' && bit != '1') return null
            v += (bit - '0') shl (3 - j)
        }
        sb.append(v.toString(16))
        i += 4
    }
    return sb.toString()
}
