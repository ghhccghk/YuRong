# smali 深挖报告（第二轮）

承接 [smali-verification.artifact.md](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/smali-verification.artifact.md)，
回答三个问题：token 怎么来、TtpInfo 字段怎么解析、计时器精确值。

---

## 1. Token 机制 ✅ 已完全确认

### 1.1 存储：`ud5.smali`（1522 行）

| 项 | 值 |
| --- | --- |
| 存储介质 | `androidx.security.crypto.EncryptedSharedPreferences` |
| key 格式 | `"key_vehicle_token_" + userId + "_" + mac`（分隔符 `_`，`0x5f`） |
| 读取 | `ud5.r(userId, mac) → String`（失败返回 `""`，不抛异常） |
| 写入 | `ud5.A(userId, mac, token)`（`commit()` 同步写，非 `apply()`） |
| 异常处理 | `catchall` → `printStackTrace()`，**静默失败** |

> [!NOTE]
> 用 `EncryptedSharedPreferences` 而非普通 SP，说明 token 被视为敏感凭证。
> 移植时若放 `commonMain`，需抽象 `SecureTokenStore`；Android 侧用
> `EncryptedSharedPreferences`，其他平台需降级方案（且应明确告知用户）。

### 1.2 获取：`NetTokenHelper.smali`（794 行）

```text
NetTokenHelper.d(mac, isFactory, extParams, callback)
```

**入口守卫（关键！）**：

```text
if (!extParams.isAuthToken()) {                 // ← 可绕过！
    log("No authentication is required")
    callback.onError(-1, "No authentication is required")
    return                                       // 直接返回
}
```

> [!IMPORTANT]
> **鉴权是可选的，由 `ExtendedParamsEntity.isAuthToken` 控制。**
> 上一份报告里我说"必须先登录才能控车"，需要修正为：
> **当 `isAuthToken == false` 时完全跳过 token 流程**，直接进入 `wi.D0` 的握手前流程。
> 这是"工厂模式/免鉴权"的开关，也是不依赖账号实现控车的可能路径。

后续校验链：

| 检查 | 条件 | 失败日志 |
| --- | --- | --- |
| mac | 空 | `iot-auth/ble/secret-key/get_-1_蓝牙地址是空` |
| userId | 空 **且** `connectType != ConnectType.c` | `iot-auth/ble/token_-1_用户ID是空` |
| vin | 空 **且** `connectType != ConnectType.d` | `iot-auth/ble/token_-1_车架号是空` |
| authToken | false | `iot-auth/ble/token_-1_未配置Token鉴权` |

即 `ConnectType.c` 跳过 userId 检查、`ConnectType.d` 跳过 vin 检查。

### 1.3 三个 API 路径 ✅

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `getBleSecretKey` | `iot-auth/ble/secret-key/get` | 取密钥 |
| `getVehicleBleToken` | `iot-auth/ble/token` | 取单车 token |
| `getVehicleBleTokenList` | `iot-auth/ble/token-list` | 批量取 token |

均通过 `YdNetRequester` 发起（非 `Lo71`），且有 `retryWithTimeout` 重试封装。

### 1.4 数据模型 ✅

`BleTokenRequest`（请求）：

```kotlin
data class BleTokenRequest(
    val mac: String,
    val refreshToken: String,
    val version: String,
    val vin: String,
)
```

`BleTokenBean`（响应）：

```kotlin
data class BleTokenBean(
    val mac: String,
    val vin: String,
    val dkInfo: String,
    val dkInfoMax: Int,
)
```

> [!NOTE]
> **`dkInfo` 才是真正的密钥材料**，不是 `token` 字段。
> `wi.E0` 里 `ud5.r(userId, mac)` 读出的也是这个 `dkInfo`。
> 命名上叫 "token" 但内容是 `dkInfo`（data key info）。

---

## 2. TtpInfo 解析 ✅ 框架与关键字段已确认

### 2.1 解析器入口（`l76.smali`）

| 方法 | 行号 | 产出 |
| --- | ---: | --- |
| `l76.z([B)` | 9047 | `TtpInfo` 完整报文 |
| `l76.y([B)` | 9004 | `TtpInfo` 短报文 |
| `l76.s([B)` | 1399 | `ExternalDeviceTtpInfo` |
| `l76.x([B, PartsType)` | 8343 | `PartsTtpInfo` |
| `l76.v/t/u(List)` | 3957/2725/2214 | 列表批量 |

### 2.2 位域提取原语（逐行确认）

| 方法 | 位宽 | 掩码 | 无效值 | 返回 |
| --- | ---: | --- | --- | --- |
| `m(data, off)` | 8 | `0xFF` | `0xFF` | `-1` |
| `C(data, off, shift)` | 3 | `0x07 << shift` | `0x07` | `-1` |
| `G(data, off, shift)` | 2 | `0x03 << shift` | `0x03` | `-1` |
| `d(data, off, shift)` | 4 | `0x0F << shift` | `0x0F` | `-1` |
| `c(data, off, shift)` | ❓ 未读 | — | — | — |
| `H(data, [offs])` | 多字节组装 | — | — | — |
| `e(data, [offs])` | 多字节组装（另一变体） | — | — | — |

统一模式（照抄）：

```kotlin
fun m(data: ByteArray?, off: Int): Int {
    if (data == null || data.size <= off) return -1
    val v = data[off].toInt() and 0xFF
    return if (v == 0xFF) -1 else v
}
fun C(data: ByteArray?, off: Int, shift: Int): Int {
    if (data == null || data.size <= off) return -1
    val v = (data[off].toInt() and 0xFF) shr shift and 0x07
    return if (v == 0x07) -1 else v
}
```

### 2.3 已确认的字段映射表

| 字段 | 调用 | offset | shift | 位宽 | 后处理 |
| --- | --- | ---: | ---: | ---: | --- |
| `protocolSecondaryVersionCode` | `m(data, 4)` | 4 | — | 8 | — |
| `protocolPrimaryVersionCode` | `m(data, 5)` | 5 | — | 8 | — |
| `softwareSecondaryVersionCode` | `m(data, 6)` | 6 | — | 8 | — |
| `softwarePrimaryVersionCode` | `m(data, 7)` | 7 | — | 8 | — |
| **`parkingStatus`** | `C(data, 8, 0)` | 8 | 0 | 3 | — |
| `electricScooterLightStatus` | `G(data, 8, 4)` | 8 | 4 | 2 | — |
| `accountPermissions` | `H(data, [9,0])` | 9 | — | — | — |
| `electricScooterPowerGear` | `d(data, 0xA, 4)` | 10 | 4 | 4 | — |
| `electricScooterMotorTemperature` | `m(data, 0xB)` | 11 | — | 8 | `-0x28`（减 40） |
| `electricScooterMotorPower` | `H(data, [0xC,0xD])` | 12 | — | — | — |
| `electricScooterMotorSpeed` | `H(data, [0xE,0xF])` | 14 | — | — | — |
| `bleKeyStatus` | `G(data, 0x15, 0)` | 21 | 0 | 2 | — |
| `vehicleLockBatteryPlatformStatus` | `G(data, 0x15, 2)` | 21 | 2 | 2 | — |
| `vehicleReportLossStatus` | `G(data, 0x15, 4)` | 21 | 4 | 2 | — |
| `vehicleFunctionalLimit...Status` | `G(data, 0x15, 6)` | 21 | 6 | 2 | — |
| `bleKeyDistanceLevel` | `C(data, 0x16, 0)` | 22 | 0 | 3 | — |
| `bleParingStatus` | `C(data, 0x16, 3)` | 22 | 3 | 3 | — |
| `electricScooterCyclingTotalTimeMin` | `m(data, 0x17)` | 23 | — | 8 | — |
| `electricScooterCyclingTotalTimeHour` | `H(data, [0x18])` | 24 | — | — | — |
| `alarmSoundSwitchAfterStatus` | `G(data, 0x1A, 0)` | 26 | 0 | 2 | — |
| `pushModeAfterStatus` | `G(data, 0x1A, 2)` | 26 | 2 | 2 | — |
| `brakeModePowerRecoverGearStatus` | `C(data, 0x27, 0)` | 39 | 0 | 3 | — |
| `liftSpeedLimitModeStatus` | `G(data, 0x27, 3)` | 39 | 3 | 2 | ⚠️ shift=3 |
| `busbarVoltage` | `H(data, [0x28,0x29])` | 40 | — | — | `× 0.1f` |
| `currentOdoValue` | `e(data, [0x45,0x46,0x47,0x48])` | 69 | — | 32 | `× 0.1f` |
| `currentTripValue` | `H(data, [0x49,0x4A])` | 73 | — | — | `× 0.1f` |
| **`currentSpeedValue`** | `H(data, [0x4B,0x4C])` | 75 | — | — | `× 0.1f` |
| `realKeyStatus` | `G(data, 0x4D, 0)` | 77 | 0 | 2 | — |
| `speedGear` | `C(data, 0x4D, 2)` | 77 | 2 | 3 | — |
| **`onOffStatus`** | `c(data, 0x4E, 0)` | 78 | 0 | — | — |
| `whistleMode` | `G(data, 0x4E, 2)` | 78 | 2 | 2 | — |

### 2.4 浮点缩放规则（易错点）

```text
const v11, 0x3dcccccd      // = 0.1f
cmpl-float v14, v13, v9    // 与哨兵值比较（v9 疑为 -1.0f）
if-eqz v14, :skip
mul-float/2addr v13, v11   // 仅当 != 哨兵 才 ×0.1
```

> [!CAUTION]
> **缩放是条件性的**：值等于哨兵（无效）时**不乘** 0.1。
> 若实现成"无条件 ×0.1"，无效值会变成 `-0.1` 而不是 `-1.0`，静默产生错误数据。

### 2.5 常量偏移表（`array-data`）

| 标签 | 偏移列表 | 用途 |
| --- | --- | --- |
| `array_c00` | `[0x09, 0x00]` | `accountPermissions` |
| `array_c08` | `[0x0C, 0x0D]` | 电机功率 |
| `array_c10` | `[0x0E, 0x0F]` | 电机转速 |
| `array_c20` | `[0x28, 0x29]` | `busbarVoltage` |
| `array_c28` | `[0x2A, 0x2B]` | `busbarCurrent` |
| `array_c60` | `[0x45, 0x46, 0x47, 0x48]` | `currentOdoValue`（**4 字节**） |
| `array_c6c` | `[0x49, 0x4A]` | `currentTripValue` |
| `array_c74` | `[0x4B, 0x4C]` | `currentSpeedValue` |
| `array_c7c` | `[0x50, 0x51]` | （`remainingMileage` 区域） |

### 2.6 仍未读的部分

- `l76.c(data, off, shift)` 的位宽与无效值（`onOffStatus` 依赖它）
- `remainingMileage` 的完整偏移与 `BigDecimal` 舍入逻辑（用了 `BigDecimal.floatValue()`）
- 0x4E 之后到报文末尾的全部字段（`totalBatteryVoltage`、`totalBatteryElectricity`、`pBElectricitySoc`、`maximumSpeedCurrent`、`faultCode0..N`、经纬度等）
- `l76.H` / `l76.e` 两个多字节组装器的**字节序**（LE 还是 BE）——这是最关键的未确认项

---

## 3. 计时器 ✅ 精确常量已定位

| 常量 | 十进制 | 出现位置 | 推断语义 |
| --- | ---: | --- | --- |
| `0x3a98` | **15000** | `BleBicycleConnect.smali` L994/1090/1186（`Lno$a;->f(J)`）；`connect/a` L126；`connect/i` L419/517；`connect/j` L243；`connect/o` L244 | 扫描超时 15s ✅ 与 docs 一致 |
| `0x4e20` | **20000** | `connect/l.smali` L225（`tl.g0(J)`）；`connect/n.smali` L154 | GATT 连接超时 20s ✅ |
| `0x1388` | **5000** | `connect/l.smali` L219（`tl.m0(IJ)`）、L232（`tl.j0(I)`）；`connect/n.smali` L148/161 | 5s 操作超时 ✅ |
| `0xc8` | **200** | `executeTasksSequentially$executeTask$2` L395；`toPairCar$1` L283；`p$a$a.b = 200` | 任务重试间隔 200ms ✅ |
| `0x1f4` | **500** | `BleBluetooth$a` L782（`sendMessageDelayed`）；`p$a$a.e = 500` | 连上后延迟 500ms ✅ |
| `0x1388` | 5000 | `customsound/*` | 自定义音效重试（无关） |

`p$a$a` 的两个字段：`b = 200`、`e = 500` —— 对应重试间隔与连接后延迟。

> [!NOTE]
> docs 说的"15s 看门狗 tick 1000ms"我**未在 smali 中找到 1000ms 的 tick 常量**。
> 找到的是 15s(0x3a98) / 20s(0x4e20) / 5s(0x1388) / 500ms(0x1f4) / 200ms(0xc8)。
> `0x3e8`（1000）未出现在连接相关文件中，**tick 值需进一步确认**。

---

## 4. 对实施计划的修正

### 4.1 ⚠️ 修正上一份报告的结论

| 上一份结论 | 修正后 |
| --- | --- |
| "必须先登录才能控车" | **条件性**：`isAuthToken == false` 时完全跳过 token，无需登录 |
| "token 来自 `ud5.r(userId, mac)`" | 是，但内容是 **`dkInfo`**（密钥材料），不是普通 token |
| 实施顺序必须 6→2 | 若走 `isAuthToken=false` 路径则不需要 Phase 6；否则仍需 |

### 4.2 新的关键未知项（阻塞实现）

| 项 | 影响 | 验证方式 |
| --- | --- | --- |
| `l76.H` / `l76.e` 的**字节序** | 所有多字节字段（车速、电压、ODO）都会错 | 读 `l76.H` 全文 + 实车对拍 |
| `l76.c` 的位宽/无效值 | `onOffStatus` 解析错误 | 读 `l76.c` |
| 15s 看门狗的 tick 值 | 超时精度 | 读 `BleBluetooth$b` 的 message 7 分支 |
| `isAuthToken` 何时为 false | 决定是否需要 Phase 6 | 读 `ExtendedParamsEntity` 构造点 |
| `array_c7c` 之后的偏移表 | `totalBatteryVoltage` 等字段 | 继续读 `l76.z` 0x4E 之后 |

### 4.3 建议的下一步

按**阻塞程度**排序：

1. **`l76.H` 与 `l76.e`** — 字节序是所有数值字段的基础，必须最先确认
2. **`l76.c`** — 补齐最后一个位域原语
3. **`ExtendedParamsEntity`** — 确认 `isAuthToken` 的默认值与来源，决定 Phase 6 是否必需
4. **`l76.z` 的 0x4E–末尾** — 补齐 `AGENTS.md` 提到的 11 个字段中的剩余 6 个
   （`totalBatteryVoltage`、`totalBatteryElectricity`、`pBElectricitySoc`、
   `maximumSpeedCurrent`、`drivingMemory`、`cruiseControl`）
5. **`BleBluetooth$b`** — 补齐 15s 看门狗 tick

> [!TIP]
> 建议先做第 1 项。字节序若搞反，后面所有对拍测试都会失败，且症状是"数值离谱但不崩溃"，
> 很难定位。这是整条链路里性价比最高的一个确认点。
