package top.ghhccghk.yadea.vehicle

/**
 * 车辆配对时写入组帧载荷的"绑定类型"二进制串。
 *
 * 原实现里这些字段是**二进制字符串**（如 `"010"`），不是整数，
 * 参与组帧前要经 [String.binaryToHex] 转成 hex。
 *
 * 三组字段的语义：
 *
 * | 字段 | 位数 | 来源 | 作用 |
 * | --- | ---: | --- | --- |
 * | `bikeBondType` | 3 | [bikeBondType] | 车辆绑定类型 |
 * | `bikePairBondType` | 4 | [bikeBondType] | 车辆配对类型 |
 * | `appType` | 3 | [appType] | 应用类型 |
 * | `appPairType` | 4 | [appType] | 应用配对类型 |
 */
internal object BondType {

    /** 未绑定。 */
    const val UNBOUND = "00"

    /** 配对流程使用的外部码。`BleBicycleBinding` 进入配对时传入。 */
    const val PAIRING = "30"

    /** 应用启动时写入的外部码，`BaseApplication` 中硬编码。 */
    const val APP_DEFAULT = "00"

    /** 未知外部码时的兜底：车辆绑定类型。 */
    const val FALLBACK_BIKE = "111"

    /** 未知外部码时的兜底：车辆配对类型。 */
    const val FALLBACK_BIKE_PAIR = "1111"

    /** 未知外部码时的兜底：应用类型。 */
    const val FALLBACK_APP = "111"

    /**
     * 未知外部码时的兜底：应用配对类型。
     *
     * 注意**不是** `"1111"` —— 原实现在 `setAppType` 的默认分支里
     * 复用了 `"00"` 分支的常量，因此这里是 `"0000"`。
     */
    const val FALLBACK_APP_PAIR = "0000"

    /** 组帧时拼在 `bikeBondType` 前的固定前缀。 */
    const val BIKE_BOND_PREFIX = "11"

    /**
     * 外部码 → (`bikeBondType`, `bikePairBondType`)。
     *
     * `"20"` 与 `"30"` 的绑定类型相同，只有配对类型不同。
     */
    fun bikeBondType(external: String): Pair<String, String> = when (external) {
        "00" -> "000" to "0000"
        "10" -> "001" to "0001"
        "20" -> "010" to "0010"
        "30" -> "010" to "0011"
        "40" -> "100" to "0100"
        else -> FALLBACK_BIKE to FALLBACK_BIKE_PAIR
    }

    /** 外部码 → (`appType`, `appPairType`)。 */
    fun appType(external: String): Pair<String, String> = when (external) {
        "00" -> "000" to "0000"
        "01" -> "001" to "0001"
        "10" -> "010" to "0010"
        "11" -> "011" to "0011"
        "12" -> "100" to "0100"
        "13" -> "101" to "0101"
        else -> FALLBACK_APP to FALLBACK_APP_PAIR
    }
}

/**
 * 配对命令载荷：`bikePairBondType`(4 位) + `appPairType`(4 位)，整体转 hex。
 *
 * 拼接后恰好 8 位，满足 [String.binaryToHex] 的倍数要求。
 */
internal fun buildPairCommandPayload(
    externalBondType: String,
    externalAppType: String = BondType.APP_DEFAULT,
): String? {
    val (_, bikePair) = BondType.bikeBondType(externalBondType)
    val (_, appPair) = BondType.appType(externalAppType)
    return (bikePair + appPair).binaryToHex()
}

/**
 * 绑定命令载荷：`"11"`(2 位) + `bikeBondType`(3 位) + `appType`(3 位)，整体转 hex。
 *
 * 前缀 `"11"` 是原实现里固定的字面量，用于把总位数凑到 8。
 */
internal fun buildBondCommandPayload(
    externalBondType: String,
    externalAppType: String = BondType.APP_DEFAULT,
): String? {
    val (bikeBond, _) = BondType.bikeBondType(externalBondType)
    val (app, _) = BondType.appType(externalAppType)
    return (BondType.BIKE_BOND_PREFIX + bikeBond + app).binaryToHex()
}
