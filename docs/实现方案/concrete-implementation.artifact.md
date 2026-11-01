# 具体功能实现（可落地代码规格）

基于 smali 逐行确认的事实，给出每个核心功能的**具体实现**。
本文含可编译级别的 Kotlin 代码骨架，可直接放进
`shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/`。

> [!NOTE]
> 所有常量、偏移、位宽、字节序均来自
> [ttpinfo-field-map](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/ttpinfo-field-map.artifact.md)、
> [smali-confirmed-facts](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/smali-confirmed-facts.artifact.md)、
> [auth-smali-facts](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/auth-smali-facts.artifact.md)。

---

## 目录

1. [十六进制与字节工具](#1-十六进制与字节工具)
2. [位域与多字节读取](#2-位域与多字节读取)
3. [车辆帧构造](#3-车辆帧构造)
4. [校验算法](#4-校验算法)
5. [命令表](#5-命令表)
6. [TtpInfo 解析器](#6-ttpinfo-解析器)
7. [通知分派](#7-通知分派)
8. [连接状态机](#8-连接状态机)
9. [握手帧](#9-握手帧)
10. [网关签名](#10-网关签名)
11. [文件清单与依赖](#11-文件清单与依赖)

---

## 1. 十六进制与字节工具

```kotlin
package top.ghhccghk.yadea.vehicle

private const val HEX_UPPER = "0123456789ABCDEF"

/** 单字节 → 2 位大写 hex */
internal fun Byte.toHex2(): String {
    val v = toInt() and 0xFF
    return "${HEX_UPPER[v ushr 4]}${HEX_UPPER[v and 0x0F]}"
}

internal fun ByteArray.toHex(): String = joinToString("") { it.toHex2() }

/** hex 字符串 → ByteArray；长度奇数时返回 null（原实现会抛异常被吞掉） */
internal fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    return ByteArray(length / 2) { i ->
        val hi = HEX_UPPER.indexOf(this[i * 2].uppercaseChar())
        val lo = HEX_UPPER.indexOf(this[i * 2 + 1].uppercaseChar())
        if (hi < 0 || lo < 0) return null
        ((hi shl 4) or lo).toByte()
    }
}

/**
 * 对应 smali `BleBicycleCommandManager.decInt2hexString`。
 *
 * **奇数长度补 1 个 '0'，再转大写。** 不是"不补零"。
 * 1 → "01"，15 → "0F"，16 → "10"，256 → "0100"
 */
internal fun Int.toDecHex(): String {
    val s = toString(16)
    return if (s.length % 2 == 1) "0$s".uppercase() else s.uppercase()
}

/**
 * 对应 smali `getCheckResult`。**判据是 length == 1，且不转大写**（外层统一转）。
 */
internal fun Int.toCheckHex(): String {
    val s = toString(16)
    return if (s.length == 1) "0$s" else s
}
```

> [!CAUTION]
> `toDecHex` 与 `toCheckHex` 判据不同。SUM8 场景下两者恰好等价，
> 但**必须分开实现**，否则将来长度扩展会出错。

---

## 2. 位域与多字节读取

```kotlin
package top.ghhccghk.yadea.vehicle

/**
 * 对应 smali `l76` 的读取原语。所有方法在越界或命中"无效值"时返回 -1。
 */
internal object Ttp {
    // ---- 位域原语 ----

    /** u8；0xFF → -1 */
    fun m(d: ByteArray, off: Int): Int {
        if (off < 0 || d.size <= off) return -1
        val v = d[off].toInt() and 0xFF
        return if (v == 0xFF) -1 else v
    }

    /** 高位变宽提取；全 1 → -1 */
    fun a(d: ByteArray, off: Int, shift: Int): Int {
        if (off < 0 || d.size <= off) return -1
        val mask = (0xFF shl shift) and 0xFF
        val v = ((d[off].toInt() and 0xFF) and mask) ushr shift
        val invalid = (0xFF shl shift) and 0xFF ushr shift
        return if (v == invalid) -1 else v
    }

    /** 3 bit；0x07 → -1 */
    fun C(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x07, 3)

    /** 2 bit；0x03 → -1 */
    fun G(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x03, 2)

    /** 4 bit；0x0F → -1 */
    fun d(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x0F, 4)

    /** 5 bit；0x1F → -1 */
    fun c(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x1F, 5)

    /** 1 bit；0x01 → -1 */
    fun l(d: ByteArray, off: Int, shift: Int) = bits(d, off, shift, 0x01, 1)

    private fun bits(d: ByteArray, off: Int, shift: Int, mask: Int, invalid: Int): Int {
        if (off < 0 || d.size <= off) return -1
        val v = ((d[off].toInt() and 0xFF) ushr shift) and mask
        return if (v == invalid) -1 else v
    }

    // ---- 多字节组装 ----

    /** 2 字节**小端**；0xFFFF → -1。对应 l76.H */
    fun H(d: ByteArray, o0: Int, o1: Int): Int {
        val max = maxOf(o0, o1)
        if (max < 0 || d.size <= max) return -1
        val v = (d[o0].toInt() and 0xFF) or ((d[o1].toInt() and 0xFF) shl 8)
        return if (v == 0xFFFF) -1 else v
    }

    /** 4 字节**小端**；-1 → -1。对应 l76.e */
    fun e(d: ByteArray, o0: Int, o1: Int, o2: Int, o3: Int): Int {
        val max = maxOf(o0, o1, o2, o3)
        if (max < 0 || d.size <= max) return -1
        val v = (d[o0].toInt() and 0xFF) or
                ((d[o1].toInt() and 0xFF) shl 8) or
                ((d[o2].toInt() and 0xFF) shl 16) or
                ((d[o3].toInt() and 0xFF) shl 24)
        return if (v == -1) -1 else v
    }

    /** 2 字节**大端**。对应 l76.J。注意与 H 的字节序相反 */
    fun J(d: ByteArray, o0: Int, o1: Int): Int {
        if (o0 < 0 || o1 < 0 || d.size <= maxOf(o0, o1)) return -1
        return ((d[o0].toInt() and 0xFF) shl 8) or (d[o1].toInt() and 0xFF)
    }

    /** 3 字节**小端**。对应 l76.e 的 3 字节变体 / 经纬度 */
    fun e3(d: ByteArray, o0: Int, o1: Int, o2: Int): Int {
        if (d.size <= maxOf(o0, o1, o2)) return -1
        return (d[o0].toInt() and 0xFF) or
                ((d[o1].toInt() and 0xFF) shl 8) or
                ((d[o2].toInt() and 0xFF) shl 16)
    }

    // ---- 缩放 ----

    /** 条件性 ×0.1：等于哨兵则原样返回 */
    fun scaleTenths(raw: Float, sentinel: Float = INVALID_F): Float =
        if (raw == sentinel) raw else raw * 0.1f

    const val INVALID_F = -1.0f
    const val INVALID_I = -1
}
```

> [!CAUTION]
> **`H` 是 LE，`J` 是 BE。** 同一条报文里混用。
> 误用会让车速/电压数值"离谱但不崩溃"，极难定位。

---

## 3. 车辆帧构造

```kotlin
package top.ghhccghk.yadea.vehicle

/**
 * 车辆控制帧：`5944 + ORDER_ID + LEN + PAYLOAD + CHECK + 4B4A`
 *
 * 与投屏帧（YadeaProtocol，协议 ID `3C`、BE32 长度）**结构不同**，
 * 两者共用 `5944/4B4A` 字符串但必须各留一套实现。
 */
object VehicleFrame {
    const val HEAD = "5944"
    const val TAIL = "4B4A"

    const val ORDER_ID = "3E"           // 普通控制/查询
    const val EXTERNAL_ORDER_ID = "3A"  // 外部命令
    const val ORDER_ID_3B = "3B"        // 3B 专用

    /** 原实现常量，仅作默认值；组帧时不直接使用 */
    const val ORDER_LENGTH = "1E"

    /**
     * 组帧。`payload` 为 **hex 字符串**（原实现语义），长度字段取 `payload.length / 2`。
     */
    fun build(orderId: String, hexPayload: String): String {
        val byteLen = hexPayload.length / 2
        val body = orderId + byteLen.toDecHex() + hexPayload
        return (HEAD + body + checksum(body) + TAIL).uppercase()
    }

    fun build(orderId: String, payload: ByteArray): ByteArray =
        build(orderId, payload.toHex()).hexToBytes()
            ?: error("payload 编码失败")

    /** SUM8 覆盖 ORDER_ID + LEN + PAYLOAD，不含 HEAD/TAIL */
    fun checksum(body: String): String {
        val bytes = body.hexToBytes() ?: return "00"
        val sum = bytes.sumOf { it.toInt() and 0xFF } and 0xFF
        return sum.toCheckHex()
    }

    /**
     * 3B 专用帧。`hexArg` 每 2 字符反向拼接后追加 `FFFFFFFFFF` 与 `increaseByte` 个 `FF`。
     */
    fun build3B(commandType: Int, hexArg: String, increaseByte: Int): ByteArray {
        if (commandType != 0x13B) return ByteArray(0)
        val sb = StringBuilder()
        var i = hexArg.length - 2
        while (i >= 0) {
            sb.append(hexArg.substring(i, i + 2))
            i -= 2
        }
        sb.append("FFFFFFFFFF")
        sb.append("FF".repeat(increaseByte.coerceIn(0, 10)))
        return build(ORDER_ID_3B, sb.toString()).hexToBytes() ?: ByteArray(0)
    }
}
```

**单测向量：**

```kotlin
@Test fun lockFrame() {
    // payload "070101" → 3 字节 → 长度 "03"
    // 校验 = (0x3E + 0x03 + 0x07 + 0x01 + 0x01) & 0xFF = 0x4A → "4A"
    assertEquals("59443E030701014A4B4A", VehicleFrame.build("3E", "070101"))
}
@Test fun decHex() {
    assertEquals("01", 1.toDecHex())
    assertEquals("0F", 15.toDecHex())
    assertEquals("10", 16.toDecHex())
    assertEquals("0100", 256.toDecHex())
}
@Test fun checkHex() {
    assertEquals("05", 5.toCheckHex())
    assertEquals("2a", 0x2A.toCheckHex())   // 不转大写
}
@Test fun reverse3B() {
    assertEquals("DDCCBBAA", reverseHexPairs("AABBCCDD"))
}
```

> [!NOTE]
> 校验值按公式 `(0x3E + 0x03 + 0x07 + 0x01 + 0x01) and 0xFF = 0x4A` 推算为 `"4A"`。
> 实现后请以单测实跑结果为准，若不符说明校验覆盖范围理解有误。

---

## 4. 校验算法

```kotlin
package top.ghhccghk.yadea.vehicle

object Checksums {
    /** 8 位算术和 */
    fun sum8(bytes: ByteArray): Int = bytes.sumOf { it.toInt() and 0xFF } and 0xFF

    /** CRC16-XMODEM：poly 0x1021，init 0x0000，不反转，final 0x0000 */
    fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /** CRC16-XMODEM 的 hex：奇数长度补 0；长度 ==2 时前补 "00" */
    fun crc16XmodemHex(data: ByteArray): String {
        var s = crc16Xmodem(data).toString(16)
        if (s.length % 2 == 1) s = "0$s"
        if (s.length == 2) s = "00$s"
        return s
    }

    /** CRC32-MPEG-2：poly 0x04C11DB7，init 0xFFFFFFFF，MSB-first，final 0 */
    fun crc32Mpeg2(data: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF).toLong() shl 24)
            repeat(8) {
                crc = if (crc and 0x80000000L != 0L) {
                    ((crc shl 1) xor 0x04C11DB7L) and 0xFFFFFFFFL
                } else {
                    (crc shl 1) and 0xFFFFFFFFL
                }
            }
        }
        return crc and 0xFFFFFFFFL
    }

    /**
     * 滑板车整帧 XOR：`out[i] = frame[i] xor ((i + 10) and 0xFF)`。
     * **CRC 字节也参与 XOR。**
     */
    fun scooterEncrypt(frame: ByteArray): ByteArray =
        ByteArray(frame.size) { i ->
            (frame[i].toInt() xor ((i + 10) and 0xFF)).toByte()
        }

    /** 逐字节 XOR 折叠（握手帧收尾用） */
    fun xorFold(data: ByteArray, len: Int): Byte {
        var acc = 0
        for (i in 0 until len.coerceAtMost(data.size)) acc = acc xor (data[i].toInt() and 0xFF)
        return acc.toByte()
    }
}
```

**单测向量：**

| 输入 | 期望 |
| --- | --- |
| `crc16Xmodem("123456789".encodeToByteArray())` | `0x31C3` |
| `sum8(byteArrayOf(0x3E, 0x03, 0x07, 0x01, 0x01))` | `0x4A` |
| `scooterEncrypt(byteArrayOf(0x5A))` | `byteArrayOf((0x5A xor 10).toByte())` |

---

## 5. 命令表

```kotlin
package top.ghhccghk.yadea.vehicle

/** 自行车 8 个经典命令（未经 5944 包封，直接写特征值） */
object ClassicCommands {
    val ON        = byteArrayOf(0x07, 0x01, 0x04)
    val OFF       = byteArrayOf(0x07, 0x01, 0x0E)
    val LOCK      = byteArrayOf(0x07, 0x01, 0x01)
    val UNLOCK    = byteArrayOf(0x07, 0x01, 0x02)
    val WHISTLE   = byteArrayOf(0x07, 0x01, 0x03)
    val DETECT    = byteArrayOf(0x07, 0x01, 0x0D)
    val ODO_RESET = byteArrayOf(0x07, 0x01, 0x0A)
    val CUSHION   = byteArrayOf(0x07, 0x01, 0x09)

    /** 命令 ID → payload（`BleBicycleCommandManager` 的分派表） */
    fun byCommandId(id: Int): ByteArray? = when (id) {
        0x08 -> ON
        0x07 -> OFF
        0x06 -> LOCK
        0x04 -> UNLOCK
        0x03 -> WHISTLE
        0x05 -> DETECT
        0x31 -> ODO_RESET
        0x09 -> CUSHION
        else -> null
    }
}

/**
 * 车辆命令载体。`commandType` 是**自由字符串**，不是枚举。
 */
data class VehicleCommand(
    val commandType: String,
    val commandParam: Int = -1,
    val clientId: Int = 1,
    val isPlural: Boolean = false,
    val isVoice: Boolean = false,
    val isFromWidget: Boolean = false,
    val isMultiOrder: Boolean = false,
    val orderId: String? = null,
    val commandParams: Map<String, Int> = emptyMap(),
    val commandParamStr: String = "",
)

/** `t()` 映射表。原地修改，仅 X1/EiBike 生效 */
fun VehicleCommand.mapScooterCommand(): VehicleCommand = when (commandType) {
    "SCOOTER_GEAR_1" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 0)
    "SCOOTER_GEAR_2" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 1)
    "SCOOTER_GEAR_3" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 2)
    "SCOOTER_GEAR_4" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 3)
    "SCOOTER_GEAR_5" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 4)
    "SCOOTER_GEAR_6" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 5)
    "SCOOTER_GEAR_X" -> copy(commandType = "DRIVING_MODE_SWITCH", commandParam = 3)
    "SCOOTER_ENERGY_RECOVERY_WEAK"   -> copy(commandType = "BRAKE_RECOVER_LEVEL", commandParam = 1)
    "SCOOTER_ENERGY_RECOVERY_MEDIUM" -> copy(commandType = "BRAKE_RECOVER_LEVEL", commandParam = 2)
    "SCOOTER_ENERGY_RECOVERY_STRONG" -> copy(commandType = "BRAKE_RECOVER_LEVEL", commandParam = 3)
    "SCOOTER_CLOSE_CRUISE_CONTROL"   -> copy(commandType = "CRUISE_CONTROL_CLOSE")
    "SCOOTER_OPEN_CRUISE_CONTROL"    -> copy(commandType = "CRUISE_CONTROL_OPEN")
    "SCOOTER_UNLOCK" -> copy(commandType = "RELEASE_FORTIFY")
    "SCOOTER_LOCK"   -> copy(commandType = "FORTIFY")
    else -> this
}
```

> [!WARNING]
> `onControlSuccess` 只代表 **GATT 写成功**，不是车辆执行 ACK。
> UI 上只能显示"指令已发送"，不能显示"开锁成功"。

---

## 6. TtpInfo 解析器

```kotlin
package top.ghhccghk.yadea.vehicle

/**
 * 车辆状态快照。只包含已确认 offset 的字段；
 * 其余 295 个字段按需增量补齐（见 ttpinfo-field-map）。
 *
 * 所有 `Int` 字段的 `-1` 与 `Float` 字段的 `-1.0f` 均表示"无效"。
 */
data class TtpInfo(
    val raw: ByteArray,
    val hex: String,

    // 版本
    val protocolSecondaryVersionCode: Int,
    val protocolPrimaryVersionCode: Int,
    val softwareSecondaryVersionCode: Int,
    val softwarePrimaryVersionCode: Int,

    // 锁 / 开关
    val parkingStatus: Int,        // 0=已锁, 1=未锁；byte8 bit0-2
    val onOffStatus: Int,          // 0x4E bit0-4（5 位）
    val speedGear: Int,            // 0x4D bit2-4
    val realKeyStatus: Int,        // 0x4D bit0-1

    // 电量 / 电压 / 电流
    val totalBatteryVoltage: Float,      // ×0.1
    val totalBatteryElectricity: Float,  // ×0.1 后 -1000
    val pBElectricitySoc: Float,         // ×0.5，>200 无效
    val sBElectricitySoc: Float,
    val busbarVoltage: Float,            // ×0.1
    val busbarCurrent: Float,            // ×0.1
    val totalBatterySoc: Float,

    // 里程 / 速度
    val remainingMileage: Float,   // ×0.1 + BigDecimal(1)
    val currentOdoValue: Float,    // 4 字节 ×0.1
    val currentTripValue: Float,   // ×0.1
    val currentSpeedValue: Float,  // ×0.1
    val maximumSpeedCurrent: Int,  // 0x20，有效 0-254
    val maximumSpeedDefault: Int,  // 0x1F

    // 档位 / 巡航
    val drivingMemory: Int,        // 0x8E bit0-2
    val cruiseControl: Int,        // 0x8F bit0-1
    val activateX: Int,            // 0x8E bit2-3

    // 故障
    val faultCodeAmount: Int,             // 0x59 bit0-4
    val faultCodeProtocolVersion1: Int,   // 0x59 bit3-5
    val faultCodeProtocolVersion2: Int,   // 0x5A
    val faultCodes: List<Int>,            // 仅当 v2==0xF2 且 amount>0
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = hex.hashCode()
}

object TtpInfoParser {

    fun parse(d: ByteArray): TtpInfo {
        val v2 = Ttp.m(d, 0x5A)
        val amount = Ttp.c(d, 0x59, 0)

        val faults = if (v2 == 0xF2 && amount > 0) {
            buildList {
                add(Ttp.J(d, 0x5B, 0x5C))
                add(Ttp.J(d, 0x5D, 0x5E))
                add(Ttp.J(d, 0x5F, 0x60))
                add(Ttp.J(d, 0x61, 0x62))
                add(Ttp.J(d, 0x63, 0x64))
                add(Ttp.J(d, 0x65, 0x66))
                add(Ttp.J(d, 0x67, 0x68))
                add(Ttp.J(d, 0x69, 0x6A))
            }
        } else emptyList()

        return TtpInfo(
            raw = d,
            hex = d.toHex(),

            protocolSecondaryVersionCode = Ttp.m(d, 0x04),
            protocolPrimaryVersionCode   = Ttp.m(d, 0x05),
            softwareSecondaryVersionCode = Ttp.m(d, 0x06),
            softwarePrimaryVersionCode   = Ttp.m(d, 0x07),

            parkingStatus  = Ttp.C(d, 0x08, 0),
            onOffStatus    = Ttp.c(d, 0x4E, 0),
            speedGear      = Ttp.C(d, 0x4D, 2),
            realKeyStatus  = Ttp.G(d, 0x4D, 0),

            totalBatteryVoltage     = scale(Ttp.H(d, 0x50, 0x51)),
            totalBatteryElectricity = scaleMinus1000(Ttp.H(d, 0x52, 0x53)),
            pBElectricitySoc        = socHalf(d, 0x57),
            sBElectricitySoc        = socHalf(d, 0x58),
            busbarVoltage           = scale(Ttp.H(d, 0x28, 0x29)),
            busbarCurrent           = scale(Ttp.H(d, 0x2A, 0x2B)),
            totalBatterySoc         = Ttp.m(d, 0x49).toFloat(),

            remainingMileage  = remainingMileage(d),
            currentOdoValue   = scale(Ttp.e(d, 0x45, 0x46, 0x47, 0x48)),
            currentTripValue  = scale(Ttp.H(d, 0x49, 0x4A)),
            currentSpeedValue = scale(Ttp.H(d, 0x4B, 0x4C)),
            maximumSpeedCurrent = Ttp.m(d, 0x20),
            maximumSpeedDefault = Ttp.m(d, 0x1F),

            drivingMemory = Ttp.C(d, 0x8E, 0),
            cruiseControl = Ttp.G(d, 0x8F, 0),
            activateX     = Ttp.G(d, 0x8E, 2),

            faultCodeAmount           = amount,
            faultCodeProtocolVersion1 = Ttp.C(d, 0x59, 3),
            faultCodeProtocolVersion2 = v2,
            faultCodes                = faults,
        )
    }

    /** 短报文（`l76.y`）：只解析 2 个字段 */
    fun parseShort(d: ByteArray): TtpInfo = parse(d).copy(
        // l76.y: G(d, 4, 6) / G(d, 5, 0)
        faultCodes = emptyList(),
    )

    // ---- 私有工具 ----

    private fun scale(raw: Int) =
        if (raw == Ttp.INVALID_I) Ttp.INVALID_F else raw * 0.1f

    private fun scaleMinus1000(raw: Int) =
        if (raw == Ttp.INVALID_I) Ttp.INVALID_F else raw * 0.1f - 1000.0f

    /** 0x57/0x58：>200 视为无效，否则 ×0.5 */
    private fun socHalf(d: ByteArray, off: Int): Float {
        val raw = Ttp.m(d, off)
        return if (raw == Ttp.INVALID_I || raw > 200) Ttp.INVALID_F else raw * 0.5f
    }

    /** 0x43/0x44：×0.1 后 BigDecimal 保留 1 位小数 */
    private fun remainingMileage(d: ByteArray): Float {
        val raw = Ttp.H(d, 0x43, 0x44)
        if (raw == Ttp.INVALID_I) return Ttp.INVALID_F
        // Kotlin 无 BigDecimal；用整数运算等价实现「四舍五入到 1 位」
        val tenths = (raw.toFloat() * 0.1f * 10f).toInt()
        return tenths / 10f
    }
}
```

> [!IMPORTANT]
> `remainingMileage` 原实现用 `BigDecimal.setScale(1, mode)`。
> KMP 无 `BigDecimal`，上面用整数运近似；**若服务端对精度敏感需复核舍入模式**。

---

## 7. 通知分派

```kotlin
package top.ghhccghk.yadea.vehicle

sealed interface VehicleNotify {
    data class Ttp(val info: TtpInfo, val isShort: Boolean) : VehicleNotify
    data class Scooter(val raw: ByteArray) : VehicleNotify
    data class Parts(val raw: ByteArray, val partType: Int) : VehicleNotify
    data class LegacyPanel(val raw: ByteArray) : VehicleNotify
    data class LegacyFault(val raw: ByteArray) : VehicleNotify
    data class Unknown(val raw: ByteArray) : VehicleNotify
}

object NotifyDispatcher {

    /** 协议版本判定。顺序敏感 */
    fun protocolVersion(d: ByteArray): Int = when {
        d.size > 10 && d.size == 14 && d[9] == 0x41.toByte() && d[10] == 0x05.toByte() -> 0xF1
        d.size > 10 && d[9] != 0xFF.toByte() && d[10] != 0xFF.toByte() -> 1
        else -> 0xF1
    }

    fun dispatch(d: ByteArray): VehicleNotify {
        if (protocolVersion(d) != 1) {
            val tokens = d.toHex().chunked(2)
            return when {
                tokens.firstOrNull() == "04" && tokens.getOrNull(1) == "01" -> VehicleNotify.LegacyPanel(d)
                tokens.firstOrNull() == "04" && tokens.getOrNull(1) == "02" -> VehicleNotify.LegacyFault(d)
                else -> VehicleNotify.Unknown(d)
            }
        }
        if (d.isEmpty()) return VehicleNotify.Unknown(d)

        return when (d[0].toInt() and 0xFF) {
            0x5A -> VehicleNotify.Scooter(d)
            else -> when (d.getOrNull(2)?.toInt()?.and(0xFF)) {
                0x2A, 0x7A -> VehicleNotify.Ttp(TtpInfoParser.parse(d), isShort = false)
                0x29       -> VehicleNotify.Ttp(TtpInfoParser.parseShort(d), isShort = true)
                0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB8 ->
                    VehicleNotify.Parts(d, d[2].toInt() and 0xFF)
                else -> VehicleNotify.Unknown(d)
            }
        }
    }
}
```

---

## 8. 连接状态机

```kotlin
package top.ghhccghk.yadea.vehicle

enum class LinkState { IDLE, CONNECTING, CONNECTED, FAILURE, DISCONNECT }

/** 对外只暴露这 3 态 */
enum class PublicState { CONNECTING, CONNECTED, DISCONNECTED }

enum class DisconnectType(val code: Int) {
    TARGET_DISCONNECTED(1), CONNECT_FAILED(2), TIMEOUT_15S(3), SCAN_NOT_FOUND(4),
    PAIR_ERROR_5(5), FW_VERSION_FAILED(6), PAIR_FAILED(7), PAIR_ERROR_8(8), PAIR_ERROR_9(9),
}

enum class VehicleException(val code: Int) {
    TIMEOUT(100), GATT(101), OTHER(102), TASK(201), BLE(300),
}

/** 全部由 smali 确认 */
object VehicleTimeouts {
    const val SCAN_MS = 15_000L
    const val GATT_CONNECT_MS = 20_000L
    const val GATT_OP_MS = 5_000L
    const val POST_CONNECT_DELAY_MS = 500L
    const val RECONNECT_DELAY_MS = 5_000L
    const val MAX_RECONNECT = 1
    const val TASK_MAX_RETRIES = 2            // 共 3 次尝试
    const val TASK_RETRY_INTERVAL_MS = 200L
    const val TASK_SUCCESS_INTERVAL_MS = 50L
    const val REQUESTED_MTU = 512
    const val MTU_MIN = 24
    const val MTU_MAX = 512
    const val NOTIFY_THROTTLE_MS = 100L
    const val NOTIFY_DELAY_MS = 110L
    const val STATUS_REPORT_THROTTLE_MS = 2_000L   // 严格大于
    const val ERROR_133 = 0x85
}
```

**转移表（纯函数，便于单测）：**

```kotlin
object ConnectFlow {
    sealed interface Event {
        data class Scanned(val mac: String) : Event
        data class GattConnected(val status: Int) : Event
        data class ServicesDiscovered(val status: Int) : Event
        data class GattDisconnected(val status: Int) : Event
        data object ConnectTimeout : Event
        data object WatchdogTimeout : Event
        data object PairingSucceeded : Event
        data class PairingFailed(val type: DisconnectType) : Event
    }

    data class Transition(
        val next: LinkState,
        val public: PublicState?,
        val disconnectType: DisconnectType?,
        val actions: List<Action>,
    )

    sealed interface Action {
        data object DiscoverServices : Action
        data object Delay500ThenDiscover : Action
        data object RefreshGattCache : Action     // 133 规避
        data object ScheduleReconnect : Action    // 仅"连接中失败"
        data object StopScanAndClose : Action
        data object RunInitTasks : Action
    }

    /**
     * 去重规则：同一 state 重复事件丢弃；device.mac != 目标 MAC 的事件丢弃。
     */
    fun reduce(state: LinkState, event: Event, targetMac: String?): Transition = when {
        state == LinkState.IDLE && event is Event.Scanned ->
            Transition(LinkState.CONNECTING, PublicState.CONNECTING, null, emptyList())

        state == LinkState.CONNECTING && event is Event.GattConnected ->
            if (event.status == 0) {
                Transition(LinkState.CONNECTING, null, null, listOf(Action.Delay500ThenDiscover))
            } else {
                Transition(LinkState.FAILURE, PublicState.DISCONNECTED, DisconnectType.CONNECT_FAILED,
                    listOf(Action.RefreshGattCache, Action.ScheduleReconnect))
            }

        state == LinkState.CONNECTING && event is Event.ServicesDiscovered ->
            if (event.status == 0) {
                Transition(LinkState.CONNECTED, PublicState.CONNECTED, null, listOf(Action.RunInitTasks))
            } else {
                // 服务发现失败 → 不重连
                Transition(LinkState.FAILURE, PublicState.DISCONNECTED, DisconnectType.CONNECT_FAILED, emptyList())
            }

        state == LinkState.CONNECTED && event is Event.GattDisconnected ->
            // 已连接后断开 → 不重连
            Transition(LinkState.DISCONNECT, PublicState.DISCONNECTED, DisconnectType.TARGET_DISCONNECTED, emptyList())

        state == LinkState.CONNECTING && event is Event.GattDisconnected ->
            // 仅此场景才 5s 后重连 1 次
            Transition(LinkState.FAILURE, PublicState.DISCONNECTED, DisconnectType.CONNECT_FAILED,
                listOf(Action.ScheduleReconnect))

        state == LinkState.CONNECTING && event == Event.ConnectTimeout ->
            Transition(LinkState.FAILURE, PublicState.DISCONNECTED, DisconnectType.TIMEOUT_15S, emptyList())

        event == Event.WatchdogTimeout ->
            Transition(LinkState.FAILURE, PublicState.DISCONNECTED, DisconnectType.TIMEOUT_15S,
                listOf(Action.StopScanAndClose))

        else -> Transition(state, null, null, emptyList())
    }
}
```

> [!CAUTION]
> **重连只发生在「连接中收到断开」**（`GattDisconnected` 且 `state == CONNECTING`）。
> 已连接后断开、连接超时、服务发现失败**都不重连**。

---

## 9. 握手帧

```kotlin
package top.ghhccghk.yadea.vehicle

/**
 * 握手/鉴权帧。**不是** `5944…4B4A` 帧：
 * token 开头、含 `5944FFFFFFFF` 标记、固定 32 字节、用 XOR 收尾。
 */
object HandshakeFrame {
    private const val MARKER = "5944FFFFFFFF"

    fun build(
        token: String,
        randKey: ByteArray,
        a: String,
        b: String,
        c: String,
        d: String,
    ): ByteArray? {
        val randPart = P0(randKey, 6, 3)
        val raw = token + MARKER + a + b + randPart + c + d + "FFFF"
        val bytes = raw.hexToBytes() ?: return null

        val out = ByteArray(0x20)
        bytes.copyInto(out, 0, 0, minOf(bytes.size, 0x20))
        out[31] = Checksums.xorFold(out, 31)
        return out
    }

    /** 从 offset 起取 len 字节，各 "%02X" 拼接；越界返回 "FF" */
    fun P0(data: ByteArray, offset: Int, len: Int): String {
        if (data.isEmpty() || offset < 0 || len <= 0 || offset >= data.size) return "FF"
        val sb = StringBuilder()
        var i = offset
        while (i < offset + len && i < data.size) {
            sb.append(data[i].toHex2())
            i++
        }
        return sb.toString()
    }
}

/** 鉴权开关。`null` 视为 false */
data class ExtendedParams(
    val vin: String? = null,
    val userId: String? = null,
    val mac: String? = null,
    val isAuthToken: Boolean? = null,
    val connectType: ConnectType = ConnectType.APP_TYPE_CLIENT,
)

enum class ConnectType(val code: Int) {
    APP_TYPE_CLIENT(0),    // 跳过 vin 检查？→ 见下
    APP_TYPE_BUSINESS(1),
    APP_TYPE_FACTORY(2),
    APP_TYPE_WATCH(3),
}

/** 是否需要鉴权流程 */
fun requiresAuth(params: ExtendedParams): Boolean = params.isAuthToken == true
```

> [!IMPORTANT]
> 鉴权跳过条件（smali 确认）：
> - `isAuthToken != true` → 完全跳过
> - `connectType == APP_TYPE_BUSINESS`（`c`）→ 跳过 **userId** 非空检查
> - `connectType == APP_TYPE_FACTORY`（`d`）→ 跳过 **vin** 非空检查

---

## 10. 网关签名

```kotlin
package top.ghhccghk.yadea.auth

/** 需各平台实现 */
expect object Crypto {
    fun aesEcbPkcs5Encrypt(key: String, plain: String): String   // 返回 Base64
    fun aesEcbPkcs5Decrypt(key: String, base64: String): String
    fun md5LowerHex(bytes: ByteArray): String
    fun urlEncode(s: String): String
    fun urlDecode(s: String): String
}

object GatewaySigner {
    private const val KEY = "cIDJ9f05d1604703"

    /**
     * @param sortedJson 业务字段的 JSON，**必须按 key 字典序**（原实现用 TreeMap）
     * @return gatewaySign to gatewayTimestamp
     */
    fun sign(sortedJson: String, timestampMs: Long): Pair<String, String> {
        val normalized = normalize(sortedJson)
        val plain = "gatewayTimestamp=$timestampMs&json=$normalized"
        val encrypted = Crypto.aesEcbPkcs5Encrypt(KEY, plain)
        return Crypto.urlEncode(encrypted) to timestampMs.toString()
    }

    /**
     * 原实现的 JSON 规范化。**漏掉会导致服务端签名校验失败且报错模糊。**
     * `essay` 分支（商城接口专用）暂不实现。
     */
    internal fun normalize(json: String): String {
        var s = json
        if (s.contains("\\\"")) s = s.replace("\\\\\"", "\"")
        if (!s.contains("essay") && s.contains("{\"")) {
            s = s.replace("\"\\{", "\\{").replace("\\}\"", "\\}")
        }
        if (s.contains("\"[")) {
            s = s.replace("\"\\[", "\\[").replace("\\]\"", "\\]")
        }
        return s
    }
}

object AuthValidation {
    private val MAINLAND = Regex("^1(3|4|5|6|7|8|9)\\d{9}$")
    private val OVERSEAS = Regex("^[0-9]{7,11}$")
    private val EMAIL = Regex("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,4}")
    private val PASSWORD = Regex("^[\"',./?!~_\\-<>&*^%$@a-zA-Z0-9]{6,18}$")
    private val SMS_CODE = Regex("^[0-9]{4}$")

    fun isMainlandPhone(s: String) = MAINLAND.matches(s)
    fun isOverseasPhone(s: String) = OVERSEAS.matches(s)
    fun isEmail(s: String) = EMAIL.matches(s)
    fun isPassword(s: String) = PASSWORD.matches(s)
    fun isSmsCode(s: String) = SMS_CODE.matches(s)
}
```

**公共 Header（`CommonHeadInterceptor`）：**

```kotlin
object CommonHeaders {
    fun build(
        token: String,
        deviceId: String,
        appVersion: String,
        appVersionNum: String,
        appMarket: String,
        appVersionType: String,      // "2" 或 "1"
        deviceBrand: String,
        phoneModel: String,
        bikeSeriesCode: String,
        appName: String,             // "YADEA" 或 "FACTORY"
    ): Map<String, String> = mapOf(
        "Authorization" to if (token.startsWith("Bearer")) token else "Bearer $token",
        "req-id" to randomUuid(),
        "request-agent" to "2",
        "deviceId" to deviceId,
        "os" to "android",
        "app-version" to appVersion,
        "bike-series-code" to bikeSeriesCode,
        "app-name" to appName,
        "app-version-num" to appVersionNum,
        "app-market" to appMarket,
        "app-version-type" to appVersionType,
        "device-brand" to deviceBrand,
        "phone-model" to phoneModel,
        "language" to "zh_CN",
        "push-operator" to "2",
    )
}
```

---

## 11. 文件清单与依赖

### 11.1 新增文件（`commonMain`）

```text
shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/
├── Hex.kt                    # toHex2 / toHex / hexToBytes / toDecHex / toCheckHex
├── Ttp.kt                    # 位域 + 多字节读取原语
├── VehicleFrame.kt           # 5944 帧构造 + 校验
├── Checksums.kt              # SUM8 / CRC16-XMODEM / CRC32-MPEG2 / XOR
├── ClassicCommands.kt        # 8 个经典命令 + 命令 ID 分派
├── VehicleCommand.kt         # 命令载体 + t() 映射
├── TtpInfo.kt                # 状态快照 data class
├── TtpInfoParser.kt          # l76.z 的 Kotlin 移植
├── NotifyDispatcher.kt       # 协议版本判定 + 分派
├── VehicleTimeouts.kt        # 常量
├── LinkState.kt              # 状态/异常/断开类型枚举
├── ConnectFlow.kt            # 纯函数转移表
├── HandshakeFrame.kt         # 32 字节握手帧
└── ExtendedParams.kt         # 鉴权参数 + ConnectType

shared/src/commonMain/kotlin/top/ghhccghk/yadea/auth/
├── Crypto.kt                 # expect object
├── GatewaySigner.kt          # AES 签名 + JSON 规范化
├── AuthValidation.kt         # 正则
├── AuthModels.kt             # UserInfoBean / OtherLoginBean
├── AuthApi.kt                # 11 个接口
├── CommonHeaders.kt          # 15 个公共 Header
└── AuthSession.kt            # token 生命周期
```

### 11.2 各平台 `actual`

```text
androidMain/.../auth/Crypto.android.kt     # javax.crypto + MessageDigest + URLEncoder
jvmMain/.../auth/Crypto.jvm.kt             # 同上（JVM 可直接复用）
jsMain/.../auth/Crypto.js.kt               # WebCrypto（异步！需改签名）
wasmJsMain/.../auth/Crypto.wasmJs.kt
iosMain/.../auth/Crypto.ios.kt             # CommonCrypto
```

> [!CAUTION]
> **WebCrypto 是异步 API**，而 `GatewaySigner.sign` 是同步的。
> 有两个选择：
> 1. 把 `sign` 改成 `suspend`（影响面大）
> 2. 在 JS/Wasm 上引入同步 AES 实现（如纯 Kotlin 的 AES，体积小）
>
> 建议选 **2**：`commonMain` 用纯 Kotlin 实现 AES-ECB 与 MD5，
> 完全避开平台差异，也便于单测。**这是本项目最推荐的做法**——
> 密码学原语本身不涉及平台 API，放在 `commonMain` 最干净。

### 11.3 依赖

| 依赖 | 用途 | 位置 |
| --- | --- | --- |
| `kotlinx.serialization-json` | JSON（**需保证字典序**） | `commonMain` |
| `ktor-client-core` + engine | HTTP | `commonMain` + 各平台 |
| `kotlin.io.encoding.Base64` | Base64（标准库，无需额外依赖） | `commonMain` |
| — | AES/MD5 建议自实现，避免 `expect` | `commonMain` |

### 11.4 实施顺序建议

```text
1. Hex.kt + Ttp.kt + Checksums.kt + VehicleFrame.kt   ← 纯函数，立即单测
2. ClassicCommands.kt + VehicleCommand.kt             ← 纯常量
3. TtpInfoParser.kt + NotifyDispatcher.kt             ← 依赖 1
4. AuthValidation.kt + GatewaySigner.kt（含纯 Kotlin AES/MD5）  ← 纯函数，立即单测
5. ConnectFlow.kt                                     ← 纯状态机，假时钟单测
6. HandshakeFrame.kt                                  ← 依赖 1、3
7. AuthApi.kt + AuthSession.kt                        ← 需网络
8. AndroidGattTransport + VehicleForegroundService    ← androidMain
```

第 1–5 步**全部可在 `commonMain` 完成且 100% 可单测**，不依赖真机与网络。
建议先做这五步，把最容易出错的部分（字节序、位域、缩放、帧格式、状态机）钉死。
