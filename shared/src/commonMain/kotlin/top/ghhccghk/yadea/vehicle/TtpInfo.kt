package top.ghhccghk.yadea.vehicle

/**
 * 车辆状态快照（完整报文）。
 *
 * 只收录**已在 smali 中确认偏移**的字段。`TtpInfo` 在原实现里有近 300 个字段，
 * 这里先做已确认的部分，其余按需增量补。
 *
 * 约定：`Int` 字段的 [Ttp.INVALID_I]（`-1`）与 `Float` 字段的 [Ttp.INVALID_F]（`-1.0f`）
 * 都表示"车辆未上报该值"，与"值为 0"不同，展示层需区分处理。
 */
data class TtpInfo(
    val raw: ByteArray,
    val hex: String,

    // ---- 协议版本 ----
    val protocolSecondaryVersionCode: Int,
    val protocolPrimaryVersionCode: Int,
    val softwareSecondaryVersionCode: Int,
    val softwarePrimaryVersionCode: Int,

    // ---- 锁与开关 ----
    /** 锁车状态：`0` 已锁，`1` 未锁。取自字节 `0x08` 的低 3 位。 */
    val parkingStatus: Int,
    /** 开关机状态。取自字节 `0x4E` 的低 5 位。 */
    val onOffStatus: Int,
    /** 速度档位。字节 `0x4D` 的 bit2-4。 */
    val speedGear: Int,
    /** 实体钥匙状态。字节 `0x4D` 的 bit0-1。 */
    val realKeyStatus: Int,
    val whistleMode: Int,
    val nfcWorkMode: Int,

    // ---- 电量 ----
    val totalBatteryVoltage: Float,
    val totalBatteryElectricity: Float,
    val totalBatterySoc: Float,
    val pBElectricitySoc: Float,
    val sBElectricitySoc: Float,
    val busbarVoltage: Float,
    val busbarCurrent: Float,

    // ---- 里程与速度 ----
    val remainingMileage: Float,
    val currentOdoValue: Float,
    val currentTripValue: Float,
    val currentSpeedValue: Float,
    val maximumSpeedDefault: Int,
    val maximumSpeedCurrent: Int,

    // ---- 档位与巡航 ----
    val drivingMemory: Int,
    val cruiseControl: Int,
    val activateX: Int,

    // ---- 故障 ----
    val faultCodeAmount: Int,
    val faultCodeProtocolVersion1: Int,
    val faultCodeProtocolVersion2: Int,
    /** 仅当协议版本 2 为 `0xF2` 且数量大于 0 时非空。使用**大端**读取。 */
    val faultCodes: List<Int>,
) {
    // ByteArray 不是值类型，用 hex 作为等价性依据。
    override fun equals(other: Any?): Boolean =
        this === other || ((other is TtpInfo) && hex == other.hex)

    override fun hashCode(): Int = hex.hashCode()
}

/**
 * 短报文（`l76.y`）承载的两个字段。
 *
 * 短报文与完整报文是**不同的报文形态**，字段布局不可互换，
 * 因此单独建模而不是复用 [TtpInfo]。
 */
data class TtpShortInfo(
    /** 电池互认状态。字节 `0x04` 的 bit6-7。 */
    val batteryMutualRecognition: Int,
    /** 限速解除状态。字节 `0x05` 的 bit0-1。 */
    val speedLimitReleased: Int,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || ((other is TtpShortInfo) && raw.contentEquals(other.raw))

    override fun hashCode(): Int = raw.contentHashCode()
}

/**
 * `l76.z([B)` / `l76.y([B)` 的等价实现。
 *
 * 字节序提醒：[Ttp.H] / [Ttp.e] 是**小端**，[Ttp.J] 是**大端**，
 * 同一份报文里混用，不能统一处理。
 */
internal object TtpInfoParser {

    /** 完整报文解析，对应 `l76.z`。 */
    fun parse(d: ByteArray): TtpInfo {
        val v2 = Ttp.m(d, 0x5A)
        val amount = Ttp.c(d, 0x59, 0)

        return TtpInfo(
            raw = d,
            hex = d.toHex(),

            protocolSecondaryVersionCode = Ttp.m(d, 0x04),
            protocolPrimaryVersionCode = Ttp.m(d, 0x05),
            softwareSecondaryVersionCode = Ttp.m(d, 0x06),
            softwarePrimaryVersionCode = Ttp.m(d, 0x07),

            parkingStatus = Ttp.C(d, 0x08, 0),
            onOffStatus = Ttp.c(d, 0x4E, 0),
            speedGear = Ttp.C(d, 0x4D, 2),
            realKeyStatus = Ttp.G(d, 0x4D, 0),
            whistleMode = Ttp.G(d, 0x4E, 2),
            nfcWorkMode = Ttp.G(d, 0x4E, 4),

            totalBatteryVoltage = Ttp.scaleTenths(Ttp.H(d, 0x50, 0x51)),
            totalBatteryElectricity = batteryElectricity(d),
            totalBatterySoc = Ttp.m(d, 0x49).toFloat(),
            pBElectricitySoc = soc(d, 0x57),
            sBElectricitySoc = soc(d, 0x58),
            busbarVoltage = Ttp.scaleTenths(Ttp.H(d, 0x28, 0x29)),
            busbarCurrent = Ttp.scaleTenths(Ttp.H(d, 0x2A, 0x2B)),

            remainingMileage = remainingMileage(d),
            currentOdoValue = Ttp.scaleTenths(Ttp.e(d, 0x45, 0x46, 0x47, 0x48)),
            currentTripValue = Ttp.scaleTenths(Ttp.H(d, 0x49, 0x4A)),
            currentSpeedValue = Ttp.scaleTenths(Ttp.H(d, 0x4B, 0x4C)),
            maximumSpeedDefault = Ttp.m(d, 0x1F),
            maximumSpeedCurrent = Ttp.m(d, 0x20),

            drivingMemory = Ttp.C(d, 0x8E, 0),
            cruiseControl = Ttp.G(d, 0x8F, 0),
            activateX = Ttp.G(d, 0x8E, 2),

            faultCodeAmount = amount,
            faultCodeProtocolVersion1 = Ttp.C(d, 0x59, 3),
            faultCodeProtocolVersion2 = v2,
            faultCodes = faultCodes(d, v2, amount),
        )
    }

    /** 短报文解析，对应 `l76.y`。 */
    fun parseShort(d: ByteArray): TtpShortInfo = TtpShortInfo(
        batteryMutualRecognition = Ttp.G(d, 0x04, 6),
        speedLimitReleased = Ttp.G(d, 0x05, 0),
        raw = d,
    )

    // ==================== 私有工具 ====================

    /**
     * 总电池电流：先乘 0.1，**再减 1000**。
     *
     * 顺序不能颠倒，否则无效值会被错误缩放。
     */
    private fun batteryElectricity(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x52, 0x53)
        return if (raw == Ttp.INVALID_I) Ttp.INVALID_F else raw * 0.1f - 1000.0f
    }

    /** 前后电池 SOC：**先判无效**（`-1` 或大于 200），再乘 0.5。 */
    private fun soc(d: ByteArray, off: Int): Float {
        val raw = Ttp.m(d, off)
        return if (raw == Ttp.INVALID_I || raw > 200) Ttp.INVALID_F else raw * 0.5f
    }

    /**
     * 剩余里程：乘 0.1 后保留 1 位小数。
     *
     * 原实现用 `BigDecimal.setScale(1, mode)`；KMP 没有 `BigDecimal`，
     * 这里用整数运算等价实现四舍五入，避免引入平台依赖。
     */
    private fun remainingMileage(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x43, 0x44)
        if (raw == Ttp.INVALID_I) return Ttp.INVALID_F
        val tenths = raw * 10 / 100
        return tenths / 10f
    }

    /**
     * 故障码序列，偏移 `0x5B`–`0x6A`，两字节一对，共 8 个。
     *
     * 使用**大端** `J`，与其它数值字段不同。
     * 仅当协议版本 2 为 `0xF2` 且数量大于 0 时读取。
     */
    private fun faultCodes(d: ByteArray, v2: Int, amount: Int): List<Int> {
        if (v2 != 0xF2 || amount <= 0) return emptyList()
        var offset = 0x5B
        return buildList {
            while (offset < 0x6B) {
                add(Ttp.J(d, offset, offset + 1))
                offset += 2
            }
        }
    }
}
