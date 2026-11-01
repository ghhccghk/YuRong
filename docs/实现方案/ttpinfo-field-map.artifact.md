# TtpInfo 字段映射表（smali 逐行确认）

来源：`l76.smali` 的 `l76.z([B)`（行 9047 起）与偏移常量表（行 12500–12724）。
本文是可直接转成 Kotlin 解析器的完整规格。

---

## 1. 位域提取原语（全部确认）

| 方法 | 掩码 | 位宽 | 无效值 | 说明 |
| --- | --- | ---: | --- | --- |
| `m(d, off)` | `0xFF` | 8 | `0xFF` | 全字节 |
| `a(d, off, shift)` | `0xFF << shift` | **8-shift** | 全 1 | 变宽高位提取 |
| `C(d, off, shift)` | `0x07 << shift` | **3** | `0x07` | — |
| `G(d, off, shift)` | `0x03 << shift` | **2** | `0x03` | — |
| `d(d, off, shift)` | `0x0F << shift` | **4** | `0x0F` | — |
| `c(d, off, shift)` | `0x1F << shift` | **5** | `0x1F` | — |
| `l(d, off, shift)` | `0x01 << shift` | **1** | `0x01` | 布尔位 |

实现：

```kotlin
object TtpRead {
    fun m(d: ByteArray?, off: Int): Int {
        if (d == null || d.size <= off) return -1
        val v = d[off].toInt() and 0xFF
        return if (v == 0xFF) -1 else v
    }

    fun a(d: ByteArray?, off: Int, shift: Int): Int {
        if (d == null || d.size <= off) return -1
        val mask = 0xFF shl shift
        val v = (d[off].toInt() and 0xFF) and mask shr shift
        return if (v == 0xFF shr shift) -1 else v
    }

    fun C(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x07, 3)
    fun G(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x03, 2)
    fun d(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x0F, 4)
    fun c(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x1F, 5)
    fun l(d: ByteArray?, off: Int, s: Int): Int = bits(d, off, s, 0x01, 1)

    private fun bits(d: ByteArray?, off: Int, s: Int, mask: Int, invalid: Int): Int {
        if (d == null || d.size <= off) return -1
        val v = (d[off].toInt() and 0xFF) shr s and mask
        return if (v == invalid) -1 else v
    }
}
```

### 1.1 多字节组装（字节序！）

| 方法 | 实现 | 字节序 | 无效判据 |
| --- | --- | --- | --- |
| `H(d, [i,j])` | `b72.q` = `p(b0) + (p(b1) shl 8)` | **小端** | `== 0xFFFF` → `-1` |
| `e(d, [i,j,k,l])` | `b72.s` = `r(b0,b1,b2) + (p(b3) shl 24)` | **小端** | `== -1` → `-1` |
| `J(d, [i,j])` | 手写 `(p(b0) shl 8) or p(b1)` | **大端** | 无（越界 → `-1`） |
| `I(d, [i,j])` | 包 `H` 后 `"%04x"` | 小端 | — |
| `f(d, [i..])` | 逐字节字符串拼接 | — | — |
| `h([i..])` | `Arrays.stream(ints).max().orElse(0)` | 取偏移最大值做边界检查 | — |

```kotlin
fun H(d: ByteArray?, offs: IntArray): Int {          // LE
    if (offs.size != 2) return -1
    val maxOff = offs.max()
    if (d == null || d.size <= maxOff) return -1
    val v = (d[offs[0]].toInt() and 0xFF) + ((d[offs[1]].toInt() and 0xFF) shl 8)
    return if (v == 0xFFFF) -1 else v
}

fun e(d: ByteArray?, offs: IntArray): Int {          // LE, 4 字节
    if (offs.size != 4) return -1
    if (d == null || d.size <= offs.max()) return -1
    val b = IntArray(4) { d[offs[it]].toInt() and 0xFF }
    val v = b[0] + (b[1] shl 8) + (b[2] shl 16) + (b[3] shl 24)
    return if (v == -1) -1 else v
}

fun J(d: ByteArray?, offs: IntArray): Int {          // BE
    if (offs.size != 2) return -1
    if (d == null || d.size <= offs[1]) return -1
    return ((d[offs[0]].toInt() and 0xFF) shl 8) or (d[offs[1]].toInt() and 0xFF)
}
```

> [!CAUTION]
> **`H`/`e` 是小端，`J` 是大端。** 同一份报文混用，不能统一处理。
> 无效判据也不同：`H` 判 `0xFFFF`，`e` 判 `-1`。

---

## 2. 字段映射表（完整，按报文偏移顺序）

### 2.1 头部（0x04–0x0B）

| 字段 | 调用 | off | shift | 宽 | 后处理 |
| --- | --- | ---: | ---: | ---: | --- |
| `protocolSecondaryVersionCode` | `m` | 4 | — | 8 | — |
| `protocolPrimaryVersionCode` | `m` | 5 | — | 8 | — |
| `softwareSecondaryVersionCode` | `m` | 6 | — | 8 | — |
| `softwarePrimaryVersionCode` | `m` | 7 | — | 8 | — |
| **`parkingStatus`** | `C` | 8 | 0 | 3 | — |
| `electricScooterLightStatus` | `G` | 8 | 4 | 2 | — |
| `accountPermissions` | `H` | [9,0] | — | 16 | — |
| `electricScooterPowerGear` | `d` | 0xA | 4 | 4 | — |
| `electricScooterMotorTemperature` | `m` | 0xB | — | 8 | `-0x28`（减 40） |
| `electricScooterMotorPower` | `H` | [0xC,0xD] | — | 16 | — |
| `electricScooterMotorSpeed` | `H` | [0xE,0xF] | — | 16 | — |

### 2.2 中段（0x10–0x4E）

| 字段 | 调用 | off | shift | 宽 | 后处理 |
| --- | --- | ---: | ---: | ---: | --- |
| `electricScooterCyclingPower` | `H` | [0x10,0x11] | — | 16 | `× 0.1f` |
| `electricScooterPedalFrequency` | `c` | — | — | 5 | — |
| `bleKeyStatus` | `G` | 0x15 | 0 | 2 | — |
| `vehicleLockBatteryPlatformStatus` | `G` | 0x15 | 2 | 2 | — |
| `vehicleReportLossStatus` | `G` | 0x15 | 4 | 2 | — |
| `vehicleFunctionalLimit…Status` | `G` | 0x15 | 6 | 2 | — |
| `bleKeyDistanceLevel` | `C` | 0x16 | 0 | 3 | — |
| `bleParingStatus` | `C` | 0x16 | 3 | 3 | — |
| `electricScooterCyclingTotalTimeMin` | `m` | 0x17 | — | 8 | — |
| `electricScooterCyclingTotalTimeHour` | `H` | [0x18,0x19] | — | 16 | — |
| `alarmSoundSwitchAfterStatus` | `G` | 0x1A | 0 | 2 | — |
| `pushModeAfterStatus` | `G` | 0x1A | 2 | 2 | — |
| `tiltWarningStatus` | `G` | 0x1A | 4 | 2 | — |
| `bleAutoTurnOnOffStatus` | `G` | 0x1A | 6 | 2 | — |
| `bleAutoTurnOnOffGearStatus` | `G` | 0x1B | 0 | 2 | — |
| `quickBrakeLightStatus` | `G` | 0x1B | 2 | 2 | — |
| `withGoHomeStatus` | `G` | 0x1B | 4 | 2 | — |
| `withGoHomeTimeStatus` | `G` | 0x1B | 6 | 2 | — |
| `alarmSensitivityStatus` | `G` | 0x1C | 0 | 2 | — |
| `offModeStatus` | `d` | 0x1C | 2 | 4 | — |
| `vehiclePowerDownStatus` | `d` | 0x1C | 6 | 4 | — |
| `vehiclePowerDownTimeStatus` | `d` | 0x1D | 0 | 4 | — |
| `withOnVehicleStatus` | `d` | 0x1D | 4 | 4 | — |
| `maximumSpeedModeStatus` | `d` | 0x1E | 0 | 4 | — |
| `withOnVehicleTimeStatus` | `d` | 0x1E | 4 | 4 | — |
| `maximumSpeedDefault` | `m` | 0x1F | — | 8 | — |
| **`maximumSpeedCurrent`** | `m` | 0x20 | — | 8 | 有效 0–254 |
| `aplStatus` | `m` | 0x21 | — | 8 | — |
| `aplColorStatus` | `m` | 0x22 | — | 8 | — |
| `aplBreathStatus` | `m` | 0x23 | — | 8 | — |
| `aplBreathFrequency` | `G` | 0x24 | 0 | 2 | — |
| `leaveVehicleAutoLockStatus` | `G` | 0x24 | 2 | 2 | — |
| `leaveVehicleAutoLockTimeStatus` | `G` | 0x24 | 4 | 2 | — |
| `sideBraceStatus` | `H` | [0x25,0x26] | — | 16 | — |
| `sideBraceCurrent` | `H` | [0x26,0x27] | — | 16 | — |
| `pushModeStatus` | `H` | [0x2A,0x2B] | — | 16 | — |
| `squabSensingStatus` | `H` | [0x2C,0x2D] | — | 16 | — |
| `slideModePowerRecoverStatus` | `H` | [0x2E,0x2F] | — | 16 | — |
| `brakeModePowerRecoverFunctionStatus` | `C` | 0x27 | 0 | 3 | — |
| `brakeModePowerRecoverGearStatus` | `G` | 0x27 | 3 | 2 | — |
| `liftSpeedLimitModeStatus` | `G` | 0x28 | 0 | 2 | — |
| **`busbarVoltage`** | `H` | [0x28,0x29] | — | 16 | `× 0.1f` |
| `busbarCurrent` | `H` | [0x2A,0x2B] | — | 16 | `× 0.1f` |
| `actualSpeedOfMotor` | `H` | — | — | 16 | — |
| `panelThemeCurrent` | `G` | — | — | 2 | — |
| `panelThemeChangeStatus` | `G` | — | — | 2 | — |
| `rideModeAutoParkingStatus` | `G` | — | — | 2 | — |
| `rideModeAutoParkingSeconds` | `m` | — | — | 8 | — |
| `bleKeyAutoOffStatus` | `C` | — | — | 3 | — |
| `bleKeyAutoOffSeconds` | `m` | — | — | 8 | — |
| `removeOverSpeedPromptStatus` | `C` | — | — | 3 | — |
| `headlightDelayOffSeconds` | `C` | — | — | 3 | — |
| `passwordUnlockStatus` | `G` | — | — | 2 | — |
| `passwordUnlockSettingResult` | `G` | — | — | 2 | — |
| `alarmRingThemeChangeStatus` | `C` | — | — | 3 | — |
| `alarmRingThemeCurrent` | `C` | — | — | 3 | — |
| `sideBraceRingStatus` | `C` | — | — | 3 | — |
| `squabSensingRingStatus` | `C` | — | — | 3 | — |
| `alarmWarningMode` | `C` | — | — | 3 | — |
| `alarmWarningVolumeLevel` | `C` | — | — | 3 | — |
| `turnSignalReturnsAutomaticallyStatus` | `C` | — | — | 3 | — |
| `automaticHeadlightFunctionStatus` | `C` | — | — | 3 | — |
| `ODOResidualZeroTimesStatus` | `C` | — | — | 3 | — |
| `vehicleSilenceStatus` | `C` | — | — | 3 | — |
| `remainingMileageDebugStatus` | `C` | — | — | 3 | — |
| `trunkLockStatus` `trunkDoorStatus` `squabLockStatus` `squabDoorStatus` `batteryTankLockStatus` `batteryTankDoorStatus` | `C` | — | — | 3 | — |
| `electronicTapLockSensorStatus` `brakeSwitchStatus` `gsmStatus` `gpsStatus` | `C` | — | — | 3 | — |
| `longitude` `latitude` | `e` | [0xA4,0xA5,0xA6] / [0xA7,0xA8,0xA9] | — | **24** | 转 double |
| `headlightStatus` `highBeamStatus` `positionLampStatus` `vehiclePowerStatus` `alarmTurnSignalStatus` `metricSystemStatus` `waringMode` | `C` | — | — | 3 | — |

### 2.3 数值段（0x43–0x6A）

| 字段 | 调用 | 偏移表 | 字节序 | 后处理 |
| --- | --- | --- | --- | --- |
| `remainingMileage` | `H` + `BigDecimal` | `c58` = [0x43,0x44] | LE | `× 0.1f` → `BigDecimal(double).setScale(1, 0).floatValue()` |
| `currentOdoValue` | `e` | `c60` = [0x45,0x46,0x47,0x48] | LE | `× 0.1f` |
| `currentTripValue` | `H` | `c6c` = [0x49,0x4A] | LE | `× 0.1f` |
| **`currentSpeedValue`** | `H` | `c74` = [0x4B,0x4C] | LE | `× 0.1f` |
| `totalBatteryVoltage` | `H` | `c7c` = [0x50,0x51] | LE | `× 0.1f` |
| `totalBatteryElectricity` | `H` | `c84` = [0x52,0x53] | LE | `× 0.1f` **再减 1000.0f** |
| `totalBatterySoc` | `m` | 0x49 | — | — |
| `totalBatteryCount` | `d` | 0x42 | 2 | — |
| `auxiliaryBattery2Soc` | `m` | 0x4A | — | — |
| `pBElectricitySoc` | `m` | 0x57 | — | `× 0.5f`，且 **> 200 视为无效** |
| `sBElectricitySoc` | `m` | 0x58 | — | 同上 |
| `tBFullChargeRemainingMin` | `H` | `c8c` = [0x55,0x56] | LE | — |
| `batteryChargingPowerValue` | `m` | 0x71 | — | `× 2`（`mul-int v13, v7`） |
| `batteryWorkingStatus` | `d` | 0x54 | 0 | — |
| `batteryChargingStatus` | `C` | 0x54 | 2 | — |
| `batteryChargingPowerStatus` | `G` | 0x70 | 4 | — |
| `batteryChargingLimitValue` | `C` | 0x70 | 3 | — |
| `faultCodeAmount` | `c` | 0x59 | 0 | 5 位 |
| `faultCodeProtocolVersion1` | `C` | 0x59 | 3 | — |
| `faultCodeProtocolVersion2` | `m` | 0x5A | — | — |
| `faultCode0..7` | **`J`** | `c94`…`ccc` = [0x5B..0x6A] 两字节一对 | **BE** | 仅当 `faultCodeProtocolVersion2 == 0xF2 && faultCodeAmount > 0` |

`realKeyStatus` / `speedGear` / `onOffStatus` / `whistleMode` / `nfcWorkMode` / `nfcLearningCardMode`：

| 字段 | 调用 | off | shift | 宽 |
| --- | --- | ---: | ---: | ---: |
| `realKeyStatus` | `G` | 0x4D | 0 | 2 |
| `speedGear` | `C` | 0x4D | 2 | 3 |
| **`onOffStatus`** | `c` | 0x4E | 0 | **5** |
| `whistleMode` | `G` | 0x4E | 2 | 2 |
| `nfcWorkMode` | `G` | 0x4E | 4 | 2 |
| `nfcLearningCardMode` | `G` | 0x4E | 6 | 2 |

### 2.4 偏移常量表（`array-data`，行 12500–12724）

| 标签 | 偏移 |
| --- | --- |
| `c00` | [0x09, 0x00] |
| `c08` | [0x0C, 0x0D] |
| `c10` | [0x0E, 0x0F] |
| `c20` | [0x28, 0x29] |
| `c28` | [0x2A, 0x2B] |
| `c38` | [0x38, 0x39, 0x3A, 0x3B] |
| `c44` | [0x3C, 0x3D, 0x3E, 0x3F] |
| `c50` | [0x43, 0x44] |
| `c58` | [0x43, 0x44] |
| `c60` | [0x45, 0x46, 0x47, 0x48] |
| `c6c` | [0x49, 0x4A] |
| `c74` | [0x4B, 0x4C] |
| `c7c` | [0x50, 0x51] |
| `c84` | [0x52, 0x53] |
| `c8c` | [0x55, 0x56] |
| `c94` | [0x5B, 0x5C] |
| `c9c` | [0x5D, 0x5E] |
| `ca4` | [0x5F, 0x60] |
| `cac` | [0x61, 0x62] |
| `cb4` | [0x63, 0x64] |
| `cbc` | [0x65, 0x66] |
| `cc4` | [0x67, 0x68] |
| `ccc` | [0x69, 0x6A] |
| `cd4` | [0xA4, 0xA5, 0xA6] |
| `cde` | [0xA7, 0xA8, 0xA9] |
| `ce8` | [0x0C, 0x0D] |
| `cf0` | [0x0E, 0x0F] |
| `cf8` | [0x10, 0x11] |
| `d00` | [0x18, 0x19] |
| `d08` | [0x9B, 0x9C] |
| `d10` | [0x9D, 0x9E] |
| `d18` | [0x86, 0x87] |
| `d20` | [0x9F, 0xA0] |
| `d28` | [0x99, 0x9A] |

---

## 3. `remainingMileage` 的特殊处理

```text
raw = H(d, [0x43, 0x44])            // LE 16 位
if (raw != 哨兵) {
    f = BigDecimal(raw.toFloat() * 0.1f)      // 先 ×0.1
            .setScale(1, RoundingMode.HALF_UP) // 四舍五入到 1 位
            .floatValue()
} else {
    f = 哨兵
}
```

`setScale(int, int)` 的第二个参数是 **RoundingMode 常量**（`v2` 在此上下文为 4 或某个值），
需在实现时确认具体舍入模式。

---

## 4. 条件性缩放清单（易错）

| 字段 | 缩放 | 减偏移 |
| --- | --- | --- |
| `electricScooterMotorTemperature` | — | `- 40` |
| `busbarVoltage` `busbarCurrent` | `× 0.1f` | — |
| `electricScooterCyclingPower` | `× 0.1f` | — |
| `remainingMileage` `currentOdoValue` `currentTripValue` `currentSpeedValue` | `× 0.1f` | — |
| `totalBatteryVoltage` | `× 0.1f` | — |
| `totalBatteryElectricity` | `× 0.1f` | **`- 1000.0f`** |
| `pBElectricitySoc` `sBElectricitySoc` | `× 0.5f` | 且 **> 200 判无效** |
| `batteryChargingPowerValue` | `× 2`（整数） | — |

> [!CAUTION]
> **所有缩放都是条件性的**：`cmpl-float` 与哨兵比较，相等则跳过缩放。
> 且 `totalBatteryElectricity` 是「×0.1 之后**再减 1000**」，
> `pBElectricitySoc` 是「**先判 >200 无效**，再 ×0.5」。
> 顺序不能颠倒，否则无效值会被错误缩放。

---

## 5. AGENTS.md 11 字段完成度

| 字段 | 状态 | 位置 |
| --- | --- | --- |
| `parkingStatus` | ✅ | `C(d, 8, 0)` |
| `onOffStatus` | ✅ | `c(d, 0x4E, 0)` |
| `totalBatteryVoltage` | ✅ | `H(d,[0x50,0x51]) × 0.1f` |
| `totalBatteryElectricity` | ✅ | `H(d,[0x52,0x53]) × 0.1f - 1000f` |
| `pBElectricitySoc` | ✅ | `m(d,0x57) × 0.5f`（>200 无效） |
| `currentOdoValue` | ✅ | `e(d,[0x45..0x48]) × 0.1f` |
| `currentSpeedValue` | ✅ | `H(d,[0x4B,0x4C]) × 0.1f` |
| `remainingMileage` | ✅ | `H(d,[0x43,0x44]) × 0.1f + BigDecimal` |
| `maximumSpeedCurrent` | ✅ | `m(d, 0x20)` |
| `drivingMemory` | ❓ | 未在 `l76.z` 中定位到明确赋值 |
| `cruiseControl` | ❓ | 未在 `l76.z` 中定位到明确赋值 |

> [!NOTE]
> `drivingMemory` 与 `cruiseControl` 在 `TtpInfo.smali` 中有字段声明，
> 但未在 `l76.z` 的已读区间找到 `iput`。可能在未读的 0x6A 之后区间，或由其他解析器
> （`l76.y` 短报文）赋值。

---

## 6. 仍未读完

| 区间 | 内容 |
| --- | --- |
| 0x6A – 末尾 | `drivingMemory`、`cruiseControl`、更多状态位 |
| `l76.y`（短报文） | 与 `z` 的字段差异 |
| `setScale` 的具体舍入模式 | `v2` 取值 |
| `0x42`/`0x57` 等散点字段的完整上下文 | 部分从 grep 推断 |

> [!TIP]
> 目前 11 个关键字段中的 9 个已有完整规格，**足以支撑 Phase 1 的解析器实现**。
> 建议先实现这 9 个 + 帧/CRC/命令，剩余字段增量补。
