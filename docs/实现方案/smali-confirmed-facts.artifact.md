# smali 已确认事实清单（可直接用于实现）

只收录**逐行读到字节码证据**的内容。所有条目可直接转成 Kotlin 实现 + 单测向量。
来源：`F:/android app code/YadeaHook/temp_smali/`。

---

## 1. 字节序 ✅ 已确认（最关键的未知项已解决）

`b72.smali` 的字节组装函数：

```text
b72.p(B)      → b and 0xFF                       // 单字节无符号
b72.q(B,B)    → p(b0) + (p(b1) shl 8)            // 16 位，低位在前
b72.r(B,B,B)  → q(b0,b1) + (p(b2) shl 16)        // 24 位
b72.s(BBBB)   → r(b0,b1,b2) + (p(b3) shl 24)     // 32 位
b72.t(B,B)    → (p(b0) shl 8) + p(b1)            // 16 位，高位在前（大端变体）
b72.u(B,B,B)  → (p(b0) shl 16) + t(b1,b2)        // 24 位，大端变体
```

**结论：**

| 解析器 | 使用的组装函数 | 字节序 |
| --- | --- | --- |
| `l76.H(data, [i, j])` | `b72.q` | **小端 (LE)** |
| `l76.e(data, [i,j,k,l])` | `b72.s` | **小端 (LE)** |
| `l76.J(data, [i, j])` | 手写 `(b0 and 0xFF) shl 8 or (b1 and 0xFF)` | **大端 (BE)** |
| `l76.I(data, [i,j])` | 包 `H` 再 `"%04x"` | LE |

> [!IMPORTANT]
> **`l76.H`/`l76.e` 是 LE，`l76.J` 是 BE。**
> 同一份报文里两种字节序**混用**——`H` 用于车速/电压/ODO 等业务量，`J` 用于少数 16 位字段。
> 实现时**不能统一按一种字节序处理**，必须逐字段按原调用选择。

### 1.1 无效值判定

| 组装函数 | 无效条件 | 返回 |
| --- | --- | --- |
| `b72.q` 结果 == `0xFFFF` | `l76.H` 判 `if-ne p0, 0xffff` | `-1` |
| `b72.s` 结果 == `-1` | `l76.e` 判 `if-ne p0, v0`（v0 = -1） | `-1` |

注意两者判据**形式不同**：`H` 判 `0xFFFF`（16 位全 1），`e` 判 `-1`（32 位全 1）。

---

## 2. 位域提取原语 ✅ 全部确认

| 方法 | 掩码常量 | 位宽 | 无效值 | 源码 |
| --- | --- | ---: | --- | --- |
| `l76.m(data, off)` | `0xFF` | 8 | `0xFF` | 全字节 |
| `l76.C(data, off, shift)` | `0x07` | **3** | `0x07` | — |
| `l76.G(data, off, shift)` | `0x03` | **2** | `0x03` | — |
| `l76.d(data, off, shift)` | `0x0F` | **4** | `0x0F` | — |
| `l76.c(data, off, shift)` | `0x1F` | **5** | `0x1F` | 上一轮未确认，现已确认 |
| `l76.a(data, off, shift)` | ❓ 未读 | — | — | — |
| `l76.l(data, off, shift)` | ❓ 未读 | — | — | — |

统一实现：

```kotlin
object Ttp {
    fun m(d: ByteArray?, off: Int): Int {
        if (d == null || d.size <= off) return -1
        val v = d[off].toInt() and 0xFF
        return if (v == 0xFF) -1 else v
    }
    fun C(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x07, 3)
    fun G(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x03, 2)
    fun d(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x0F, 4)
    fun c(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x1F, 5)

    private fun bits(d: ByteArray?, off: Int, s: Int, mask: Int, invalid: Int): Int {
        if (d == null || d.size <= off) return -1
        val v = (d[off].toInt() and 0xFF) shr s and mask
        return if (v == invalid) -1 else v
    }
}
```

### 2.1 `l76.H` 的守卫顺序（照抄，勿简化）

```text
1. h(p1) → 取偏移数组最大值 maxOff
2. p0 == null || p1 == null           → -1
3. p1.size != 2                        → -1
4. p0.size <= p1[maxOff]               → -1
5. v = b72.q(p0[p1[0]], p0[p1[1]])
6. v == 0xFFFF                         → -1
7. 否则返回 v
```

整段包在 `try/catchall` 里，异常 → `-1`。

---

## 3. 浮点缩放 ✅ 规则确认

```text
const v11, 0x3dcccccd        // = 0.1f
const v9  = 无效哨兵（-1.0f 或 0.0f，按字段不同）
cmpl-float v14, v13, v9
if-eqz v14, :skip            // 等于哨兵 → 跳过缩放
mul-float/2addr v13, v11     // × 0.1f
:skip
```

**适用字段**（已确认）：`electricScooterCyclingPower`、`busbarVoltage`、
`busbarCurrent`、`currentOdoValue`、`currentTripValue`、`currentSpeedValue`。

```kotlin
fun scaleTenths(raw: Float): Float = if (raw == INVALID_F) raw else raw * 0.1f
```

> [!CAUTION]
> 写成无条件 `raw * 0.1f` 会让无效值 `-1.0f` 变成 `-0.1f`，静默产生错误数据。

`remainingMileage` 额外用 `BigDecimal(...).floatValue()` 做舍入，需单独核对。

---

## 4. 帧格式 ✅ 完整确认

### 4.1 普通控制帧

```text
frame = "5944" + ORDER_ID + intToHex2(payload.size)
      + payload + CHECK + "4B4A"
CHECK = getCheckResult(ORDER_ID + intToHex2(payload.size) + payload)
整体 toUpperCase(Locale.ROOT)
```

| 常量 | 值 |
| --- | --- |
| `ORDER_HEAD` | `"5944"` |
| `ORDER_TAIL` | `"4B4A"` |
| `ORDER_ID` | `"3E"` |
| `EXTERNAL_ORDER_ID` | `"3A"` |
| `ORDER_ID_3B` | `"3B"` |
| `ORDER_LENGTH` | `"1E"` |
| `bleCommBigVersion` | `"00"` |
| `bleCommSmartVersion` | `"01"` |

> [!CAUTION]
> **修正（第三轮）**：`decInt2hexString` 不是"不补零"！
> 实际实现（`BleBicycleCommandManager` L388）：**奇数长度补 1 个 `0`，然后 `toUpperCase`**。
> 例：`1`→`"01"`、`15`→`"0F"`、`0x1E`→`"1E"`、`256`→`"0100"`。
> 详见 [smali-corrections-round3.artifact.md](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/smali-corrections-round3.artifact.md)。
>
> **另：原实现中 payload 是 hex 字符串，长度字段取 `payload.length / 2`（字节数）。**
> 移植时若直接传 `ByteArray.size`，长度会翻倍。

`getCheckResult`（与上面**不同**的判据）：

```kotlin
val sum = hexToBytes(input).sumOf { it.toInt() and 0xFF } and 0xFF
val s = Integer.toHexString(sum)
return if (s.length == 1) "0$s" else s      // 判据是 ==1，且不转大写
```

| 方法 | 判据 | 补零 | 大写 |
| --- | --- | --- | --- |
| `decInt2hexString` | `length % 2 == 1` | 补 1 个 | ✅ |
| `getCheckResult` | `length == 1` | 补 1 个 | ❌（外层统一转） |

### 4.2 `increaseControlByte`

```kotlin
fun increaseControlByte(n: Int) {
    val k = if (n > 10) 10 else n
    ORDER_LENGTH = Integer.toHexString(0x1E + k)   // 0x1E=30
    increaseByteNum = k
}
fun getIncreaseByteHexStr(): String =
    "FF".repeat(if (increaseByteNum < 0) 0 else increaseByteNum)
```

### 4.3 3B 专用帧

```text
commandType == 0x13B:
    payload = 入参 hex 每 2 字符反向拼接 + "FFFFFFFFFF" + getIncreaseByteHexStr()
```

---

## 5. 8 个经典命令 ✅ 逐字节

```kotlin
object ClassicCommands {
    val CAR_COMMAND_ON        = byteArrayOf(0x07, 0x01, 0x04)  // 开机
    val CAR_COMMAND_OFF       = byteArrayOf(0x07, 0x01, 0x0E)  // 关机
    val CAR_COMMAND_LOCK      = byteArrayOf(0x07, 0x01, 0x01)  // 锁车
    val CAR_COMMAND_UNLOCK    = byteArrayOf(0x07, 0x01, 0x02)  // 开锁
    val CAR_COMMAND_WHISTLE   = byteArrayOf(0x07, 0x01, 0x03)  // 寻车
    val CAR_COMMAND_DETECT    = byteArrayOf(0x07, 0x01, 0x0D)  // 检测
    val CAR_COMMAND_ODO_RESET = byteArrayOf(0x07, 0x01, 0x0A)  // ODO 清零
    val CAR_COMMAND_CUSHION   = byteArrayOf(0x07, 0x01, 0x09)  // 坐垫
}
```

命令 ID 分派（`BleBicycleCommandManager`）：`0x08`→ON、`0x07`→OFF、`0x06`→LOCK、
`0x04`→UNLOCK、`0x03`→WHISTLE、`0x05`→DETECT、`0x31`→ODO_RESET、`0x09`→CUSHION、`0x4C`→氛围灯。

---

## 6. 握手/鉴权帧 ✅ 结构确认

```text
wi.E0(device, randKey, isFactory, extParams, cb, onFail):
    token = ud5.r(extParams.userId, device.mac)
    if (token.isEmpty()) NetTokenHelper.d(mac, false, extParams, cb)
    else                 wi.D0(device, token, randKey, isFactory, cb, onFail)

wi.D0 → wi.M0(device, randKey); wi.E(device, token, randKey, isFactory, wi.i)

wi.E:
    tail2 = if (wi.q0()) "FE" else "FF"
    p3    = wi.b0(isFactory, wi.i)
    rand  = wi.U(device, randKey, wi.i)
    if (wi.s0(randKey)):
        id3 = r85.g()                    // "发送id 3"
        p4  = wi.X(r85.b())              // 偏移表
        p5  = wi.Y(id3, r85.b())
        rand = wi.V(device, randKey, wi.i)
    raw = wi.F(token, randKey, p3, p4, p5, tail2)
    out = hexDecode(raw).copyOf(0x20)    // 强制 32 字节
    out[31] = wi.G(out, 31)              // XOR 前 31 字节

wi.F(token, randKey, a, b, c, d):
    token + "5944FFFFFFFF" + a + b + wi.P0(randKey, 6, 3) + c + d + "FFFF"

wi.P0(data, 6, 3) = data[6..8] 各 "%02X" 拼接（越界返回 "FF"）
wi.G(data, len)   = 逐字节 XOR 折叠
```

> [!CAUTION]
> **握手帧不是 `5944…4B4A` 帧**，而是 `token` 开头、含 `5944FFFFFFFF` 标记、
> 固定 32 字节、用 **XOR**（非 SUM8）收尾。
> 两者共用 `5944` 字符串但结构完全不同，实现时必须是两个独立函数。

### 6.1 鉴权可绕过 ✅

```text
NetTokenHelper.d(...):
    if (!extParams.isAuthToken) {         // Boolean?，null 视为 false
        callback.onError(-1, "No authentication is required")
        return
    }
```

`ExtendedParamsEntity` 构造函数参数顺序：

```text
(vin, userId, mac, isAuthToken: Boolean?, connectType: ConnectType)
```

`connectType` 默认值（合成构造器 `p6 and 0x10`）→ `ConnectType.b`。

| 检查 | 跳过条件 |
| --- | --- |
| userId 非空 | `connectType == ConnectType.c` |
| vin 非空 | `connectType == ConnectType.d` |

> [!IMPORTANT]
> `isAuthToken == false` 时**完全跳过 token 获取**，直接进入 `wi.D0`。
> 这是"免登录控车"的开关。若走此路径，Phase 6 登录不是 Phase 2 的前置。

### 6.2 Token 存储 ✅

```kotlin
// ud5.smali
key      = "key_vehicle_token_${userId}_${mac}"
store    = EncryptedSharedPreferences
read     = ud5.r(userId, mac)   // 失败返回 ""，不抛
write    = ud5.A(userId, mac, token)  // commit() 同步
```

`BleTokenBean` 的真正密钥字段是 **`dkInfo`**（非 `token`）。

### 6.3 Token API ✅

| 路径 | 用途 |
| --- | --- |
| `iot-auth/ble/secret-key/get` | 取密钥 |
| `iot-auth/ble/token` | 取单车 token |
| `iot-auth/ble/token-list` | 批量 |

请求体 `BleTokenRequest(mac, refreshToken, version, vin)`。

---

## 7. 计时器 ✅ 精确常量

| 值 | 常量 | 位置 |
| ---: | --- | --- |
| **15000 ms** | `0x3a98` | `BleBicycleConnect` L994/1090/1186、`connect/a` L126、`connect/i` L419/517、`connect/j` L243、`connect/o` L244 |
| **20000 ms** | `0x4e20` | `connect/l` L225、`connect/n` L154 |
| **5000 ms** | `0x1388` | `connect/l` L219/232、`connect/n` L148/161 |
| **500 ms** | `0x1f4` | `BleBluetooth$a` L782、`p$a$a.e` |
| **200 ms** | `0xc8` | `executeTasksSequentially$executeTask$2` L395、`toPairCar$1` L283、`p$a$a.b` |

`p$a$a`：`b = 200`（重试间隔）、`e = 500`（连接后延迟）。

> [!NOTE]
> docs 提到的"看门狗 tick 1000ms"**未找到 `0x3e8` 常量**。
> 已确认的是 15s / 20s / 5s / 500ms / 200ms 五档。

---

## 8. UUID ✅ 全量

### 8.1 车辆主链路

| 名称 | 值 |
| --- | --- |
| Service | `0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc` |
| Characteristic（NOTIFY + WRITE） | `0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc` |

### 8.2 设备信息

| 名称 | 值 |
| --- | --- |
| Service | `0000180a-0000-1000-8000-00805f9b34fb` |
| Manufacturer | `00002a29-0000-1000-8000-00805f9b34fb` |
| **固件 + 硬件（同一个）** | `00002a26-0000-1000-8000-00805f9b34fb` |
| Software Revision | `00002a28-0000-1000-8000-00805f9b34fb` |
| Hardware Revision（**定义但未引用**） | `00002a27-0000-1000-8000-00805f9b34fb` |

### 8.3 状态通知（按 `bleProtocolVersionName` 分支）

| 版本 | Service | Notify | 状态查询写 |
| --- | --- | --- | --- |
| `== 1` | `e7810bd2-73ae-499d-8c15-faa9aef0c3f2` | `bef8e820-9c21-4c9e-b632-bd58c1009f9f` | `bef8e821-9c21-4c9e-b632-bd58c1009f9f` |
| 其他 | `e7810b82-73ae-499d-8c15-faa9aef0c3f2` | `bef8e7db-9c21-4c9e-b632-bd58c1009f9f` | `bef8e7da-9c21-4c9e-b632-bd58c1009f9f` |

### 8.4 ⚠️ `5944FFFx` 是部件通道 UUID，不是帧头

| 值 | 角色 |
| --- | --- |
| `5944FFF0-A000-B000-C000-000000000000` | 部件 Service |
| `5944FFF1-A000-B000-C000-000000000000` | 部件 Notify |
| `5944FFF2-A000-B000-C000-000000000000` | 部件 Write |

`yi.smali` 中还有索引常量：`w3=0x12e/w4=0xc`、`x3=0x12f/x4=0xd`、`y3=0x130/x4=0xe`。

---

## 9. TtpInfo 字段映射（已确认部分）

| 字段 | 调用 | off | shift | 位宽 | 后处理 |
| --- | --- | ---: | ---: | ---: | --- |
| `protocolSecondaryVersionCode` | `m(d, 4)` | 4 | — | 8 | — |
| `protocolPrimaryVersionCode` | `m(d, 5)` | 5 | — | 8 | — |
| `softwareSecondaryVersionCode` | `m(d, 6)` | 6 | — | 8 | — |
| `softwarePrimaryVersionCode` | `m(d, 7)` | 7 | — | 8 | — |
| `parkingStatus` | `C(d, 8, 0)` | 8 | 0 | 3 | — |
| `electricScooterLightStatus` | `G(d, 8, 4)` | 8 | 4 | 2 | — |
| `accountPermissions` | `H(d, [9,0])` | 9 | — | 16 | — |
| `electricScooterPowerGear` | `d(d, 0xA, 4)` | 10 | 4 | 4 | — |
| `electricScooterMotorTemperature` | `m(d, 0xB)` | 11 | — | 8 | `-0x28` |
| `electricScooterMotorPower` | `H(d, [0xC,0xD])` | 12 | — | 16 | — |
| `electricScooterMotorSpeed` | `H(d, [0xE,0xF])` | 14 | — | 16 | — |
| `bleKeyStatus` | `G(d, 0x15, 0)` | 21 | 0 | 2 | — |
| `vehicleLockBatteryPlatformStatus` | `G(d, 0x15, 2)` | 21 | 2 | 2 | — |
| `vehicleReportLossStatus` | `G(d, 0x15, 4)` | 21 | 4 | 2 | — |
| `vehicleFunctionalLimit…Status` | `G(d, 0x15, 6)` | 21 | 6 | 2 | — |
| `bleKeyDistanceLevel` | `C(d, 0x16, 0)` | 22 | 0 | 3 | — |
| `bleParingStatus` | `C(d, 0x16, 3)` | 22 | 3 | 3 | — |
| `electricScooterCyclingTotalTimeMin` | `m(d, 0x17)` | 23 | — | 8 | — |
| `electricScooterCyclingTotalTimeHour` | `H(d, [0x18])` | 24 | — | — | — |
| `alarmSoundSwitchAfterStatus` | `G(d, 0x1A, 0)` | 26 | 0 | 2 | — |
| `pushModeAfterStatus` | `G(d, 0x1A, 2)` | 26 | 2 | 2 | — |
| `brakeModePowerRecoverGearStatus` | `C(d, 0x27, 0)` | 39 | 0 | 3 | — |
| `liftSpeedLimitModeStatus` | `G(d, 0x27, 3)` | 39 | 3 | 2 | — |
| `busbarVoltage` | `H(d, [0x28,0x29])` | 40 | — | 16 | `× 0.1f` |
| `busbarCurrent` | `H(d, [0x2A,0x2B])` | 42 | — | 16 | `× 0.1f` |
| `currentOdoValue` | `e(d, [0x45..0x48])` | 69 | — | **32** | `× 0.1f` |
| `currentTripValue` | `H(d, [0x49,0x4A])` | 73 | — | 16 | `× 0.1f` |
| `currentSpeedValue` | `H(d, [0x4B,0x4C])` | 75 | — | 16 | `× 0.1f` |
| `realKeyStatus` | `G(d, 0x4D, 0)` | 77 | 0 | 2 | — |
| `speedGear` | `C(d, 0x4D, 2)` | 77 | 2 | 3 | — |
| `onOffStatus` | `c(d, 0x4E, 0)` | 78 | 0 | **5** | — |
| `whistleMode` | `G(d, 0x4E, 2)` | 78 | 2 | 2 | — |

偏移表：`c00=[9,0]`、`c08=[0xC,0xD]`、`c10=[0xE,0xF]`、`c20=[0x28,0x29]`、
`c28=[0x2A,0x2B]`、`c60=[0x45,0x46,0x47,0x48]`、`c6c=[0x49,0x4A]`、`c74=[0x4B,0x4C]`、`c7c=[0x50,0x51]`。

---

## 10. 仍未确认（阻塞项已大幅减少）

| 项 | 阻塞程度 | 说明 |
| --- | --- | --- |
| `l76.a` / `l76.l` 的位宽与无效值 | 中 | 少数字段用 |
| `remainingMileage` 完整偏移 + `BigDecimal` 舍入 | 中 | 已定位在 `c7c` 附近 |
| 0x4E 之后到报文末尾的字段 | 中 | `totalBatteryVoltage` 等 6 个 |
| `wi.b0` / `wi.U` / `wi.V` / `wi.X` / `wi.Y` 实现 | 中 | 握手帧的 a/b/c/d 字段来源 |
| `r85.g()` / `r85.b()` 的语义 | 中 | "发送id 3" 与偏移表 |
| `ConnectType` 枚举全量（`b`/`c`/`d`） | 低 | 影响鉴权跳过条件 |
| 15s 看门狗的 tick | 低 | 未找到 `0x3e8` |

> [!TIP]
> 阻塞项已从"必须先确认字节序"降级为"补齐边缘字段"。
> **Phase 1 的协议层现在可以开工**：帧格式、校验、8 个命令、位域原语、字节序、缩放规则
> 全部有字节码证据，可先实现 + 写对拍单测。
