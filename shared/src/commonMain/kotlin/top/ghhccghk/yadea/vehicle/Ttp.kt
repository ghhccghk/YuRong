package top.ghhccghk.yadea.vehicle

/**
 * 车辆状态报文（TtpInfo）的读取原语，对应 `l76.smali` 里的静态提取方法。
 *
 * 两类规则必须分开对待：
 *
 * 1. **位域提取**：从单个字节里取若干位，命中"全 1"表示无效。
 * 2. **多字节组装**：**字节序不统一** —— [H] / [e] 是小端，[J] 是大端。
 *
 * 所有方法在越界或命中无效值时返回 [INVALID_I]（`-1`）。
 * 原实现在越界时同样返回 `-1`，不做抛异常处理。
 */
internal object Ttp {

    /** 无效标记。`Int` 字段用 -1，`Float` 字段用 -1.0f。 */
    const val INVALID_I = -1
    const val INVALID_F = -1.0f

    // ==================== 位域提取 ====================

    /** u8；`0xFF` → 无效。对应 `l76.m`。 */
    fun m(d: ByteArray, off: Int): Int {
        if (off < 0 || d.size <= off) return INVALID_I
        val v = d[off].toInt() and 0xFF
        return if (v == 0xFF) INVALID_I else v
    }

    /** 高位变宽提取，位宽为 `8 - shift`；全 1 → 无效。对应 `l76.a`。 */
    fun a(d: ByteArray, off: Int, shift: Int): Int {
        if (off < 0 || d.size <= off) return INVALID_I
        if (shift <= 0 || shift >= 8) return INVALID_I
        val mask = (0xFF ushr shift)
        val v = ((d[off].toInt() and 0xFF) ushr shift) and mask
        return if (v == mask) INVALID_I else v
    }

    /** 3 bit；`0x07` → 无效。对应 `l76.C`。 */
    fun C(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x07)

    /** 2 bit；`0x03` → 无效。对应 `l76.G`。 */
    fun G(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x03)

    /** 4 bit；`0x0F` → 无效。对应 `l76.d`。 */
    fun d(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x0F)

    /** 5 bit；`0x1F` → 无效。对应 `l76.c`。 */
    fun c(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x1F)

    /** 1 bit；`0x01` → 无效。对应 `l76.l`。 */
    fun l(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x01)

    /**
     * 位域提取。
     *
     * **无效值就是掩码本身**（字段全 1），因此不需要额外参数。
     */
    private fun bits(d: ByteArray, off: Int, shift: Int, mask: Int): Int {
        if (off < 0 || d.size <= off) return INVALID_I
        if (shift < 0 || shift > 7) return INVALID_I
        val v = ((d[off].toInt() and 0xFF) ushr shift) and mask
        return if (v == mask) INVALID_I else v
    }

    // ==================== 多字节组装 ====================

    /**
     * 2 字节**小端**；结果 `0xFFFF` → 无效。对应 `l76.H`。
     *
     * 原实现会先取两个偏移的最大值做边界检查，这里保留同样语义。
     */
    fun H(d: ByteArray, o0: Int, o1: Int): Int {
        val max = maxOf(o0, o1)
        if (minOf(o0, o1) < 0 || d.size <= max) return INVALID_I
        val v = (d[o0].toInt() and 0xFF) or ((d[o1].toInt() and 0xFF) shl 8)
        return if (v == 0xFFFF) INVALID_I else v
    }

    /** 4 字节**小端**；结果全 1 → 无效。对应 `l76.e`。 */
    fun e(d: ByteArray, o0: Int, o1: Int, o2: Int, o3: Int): Int {
        val max = maxOf(o0, o1, o2, o3)
        if (minOf(o0, o1, o2, o3) < 0 || d.size <= max) return INVALID_I
        val v = (d[o0].toInt() and 0xFF) or
            ((d[o1].toInt() and 0xFF) shl 8) or
            ((d[o2].toInt() and 0xFF) shl 16) or
            ((d[o3].toInt() and 0xFF) shl 24)
        return if (v == -1) INVALID_I else v
    }

    /** 3 字节**小端**。经纬度等字段使用。对应 `l76.e` 的 3 字节变体。 */
    fun e3(d: ByteArray, o0: Int, o1: Int, o2: Int): Int {
        val max = maxOf(o0, o1, o2)
        if (minOf(o0, o1, o2) < 0 || d.size <= max) return INVALID_I
        return (d[o0].toInt() and 0xFF) or
            ((d[o1].toInt() and 0xFF) shl 8) or
            ((d[o2].toInt() and 0xFF) shl 16)
    }

    /**
     * 2 字节**大端**。对应 `l76.J`。
     *
     * **与 [H] 的字节序相反。** 故障码序列用的是这个。
     */
    fun J(d: ByteArray, o0: Int, o1: Int): Int {
        if (minOf(o0, o1) < 0 || d.size <= maxOf(o0, o1)) return INVALID_I
        return ((d[o0].toInt() and 0xFF) shl 8) or (d[o1].toInt() and 0xFF)
    }

    // ==================== 缩放 ====================

    /**
     * 条件性乘 0.1。
     *
     * 原实现先与哨兵比较，相等则**跳过缩放**。若写成无条件相乘，
     * 无效值会从 `-1.0f` 变成 `-0.1f`，静默产生错误数据。
     */
    fun scaleTenths(raw: Int): Float =
        if (raw == INVALID_I) INVALID_F else raw * 0.1f
}
