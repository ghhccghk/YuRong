# TtpInfo 字段映射

`TtpInfo` 是车辆状态报文解析结果。**解析逻辑不在 `TtpInfo.smali`**（它只有字段声明，
共 `<init>` 与 `toString` 两个方法），真正的解析器是 `temp_smali/l76.smali`：

| 方法 | 行号 | 产出 |
| --- | ---: | --- |
| `l76.z([B)` | 9047 | `TtpInfo` 完整报文 |
| `l76.y([B)` | 9004 | `TtpInfo` 短报文 |
| `l76.s([B)` | 1399 | `ExternalDeviceTtpInfo` |
| `l76.x([B, PartsType)` | 8343 | `PartsTtpInfo` |

## 1. 读取原语

见 [protocol.md](./protocol.md) 第 5 节。要点：

- 所有提取器越界或命中无效值时返回 `-1`
- `H` / `e` 是**小端**，`J` 是**大端**
- `H` 的无效判据是 `0xFFFF`，`e` 的是 `-1`

## 2. 偏移常量表

`l76.smali` 行 12500–12724 的 `array-data`：

| 标签 | 偏移 |
| --- | --- |
| `c00` | `0x09, 0x00` |
| `c08` | `0x0C, 0x0D` |
| `c10` | `0x0E, 0x0F` |
| `c20` | `0x28, 0x29` |
| `c28` | `0x2A, 0x2B` |
| `c38` | `0x38, 0x39, 0x3A, 0x3B` |
| `c44` | `0x3C, 0x3D, 0x3E, 0x3F` |
| `c50` | `0x43, 0x44` |
| `c58` | `0x43, 0x44` |
| `c60` | `0x45, 0x46, 0x47, 0x48` |
| `c6c` | `0x49, 0x4A` |
| `c74` | `0x4B, 0x4C` |
| `c7c` | `0x50, 0x51` |
| `c84` | `0x52, 0x53` |
| `c8c` | `0x55, 0x56` |
| `c94`–`ccc` | `0x5B,0x5C` … `0x69,0x6A`（故障码，成对） |
| `cd4` | `0xA4, 0xA5, 0xA6` |
| `cde` | `0xA7, 0xA8, 0xA9` |
| `d00` | `0x18, 0x19` |
| `d08` | `0x9B, 0x9C` |
| `d10` | `0x9D, 0x9E` |
| `d18` | `0x86, 0x87` |
| `d20` | `0x9F, 0xA0` |
| `d28` | `0x99, 0x9A` |

## 3. 头部字段（0x04–0x0F）

| 字段 | 调用 | off | shift | 位宽 | 后处理 |
| --- | --- | ---: | ---: | ---: | --- |
| `protocolSecondaryVersionCode` | `m` | 0x04 | — | 8 | — |
| `protocolPrimaryVersionCode` | `m` | 0x05 | — | 8 | — |
| `softwareSecondaryVersionCode` | `m` | 0x06 | — | 8 | — |
| `softwarePrimaryVersionCode` | `m` | 0x07 | — | 8 | — |
| `parkingStatus` | `C` | 0x08 | 0 | 3 | 0=已锁，1=未锁 |
| `electricScooterLightStatus` | `G` | 0x08 | 4 | 2 | — |
| `accountPermissions` | `H` | `[0x09,0x00]` | — | 16 | — |
| `electricScooterPowerGear` | `d` | 0x0A | 4 | 4 | — |
| `electricScooterMotorTemperature` | `m` | 0x0B | — | 8 | `- 40` |
| `electricScooterMotorPower` | `H` | `[0x0C,0x0D]` | — | 16 | — |
| `electricScooterMotorSpeed` | `H` | `[0x0E,0x0F]` | — | 16 | — |
| `electricScooterCyclingPower` | `H` | `[0x10,0x11]` | — | 16 | `× 0.1f` |

## 4. 状态位段（0x15–0x2F）

| 字段 | 调用 | off | shift | 位宽 |
| --- | --- | ---: | ---: | ---: |
| `bleKeyStatus` | `G` | 0x15 | 0 | 2 |
| `vehicleLockBatteryPlatformStatus` | `G` | 0x15 | 2 | 2 |
| `vehicleReportLossStatus` | `G` | 0x15 | 4 | 2 |
| `vehicleFunctionalLimitBatteryStringModificationStatus` | `G` | 0x15 | 6 | 2 |
| `bleKeyDistanceLevel` | `C` | 0x16 | 0 | 3 |
| `bleParingStatus` | `C` | 0x16 | 3 | 3 |
| `electricScooterCyclingTotalTimeMin` | `m` | 0x17 | — | 8 |
| `electricScooterCyclingTotalTimeHour` | `H` | `[0x18,0x19]` | — | 16 |
| `alarmSoundSwitchAfterStatus` | `G` | 0x1A | 0 | 2 |
| `pushModeAfterStatus` | `G` | 0x1A | 2 | 2 |
| `tiltWarningStatus` | `G` | 0x1A | 4 | 2 |
| `bleAutoTurnOnOffStatus` | `G` | 0x1A | 6 | 2 |
| `bleAutoTurnOnOffGearStatus` | `G` | 0x1B | 0 | 2 |
| `quickBrakeLightStatus` | `G` | 0x1B | 2 | 2 |
| `withGoHomeStatus` | `G` | 0x1B | 4 | 2 |
| `withGoHomeTimeStatus` | `G` | 0x1B | 6 | 2 |
| `alarmSensitivityStatus` | `G` | 0x1C | 0 | 2 |
| `offModeStatus` | `d` | 0x1C | 2 | 4 |
| `vehiclePowerDownStatus` | `d` | 0x1C | 6 | 4 |
| `vehiclePowerDownTimeStatus` | `d` | 0x1D | 0 | 4 |
| `withOnVehicleStatus` | `d` | 0x1D | 4 | 4 |
| `maximumSpeedModeStatus` | `d` | 0x1E | 0 | 4 |
| `withOnVehicleTimeStatus` | `d` | 0x1E | 4 | 4 |
| `maximumSpeedDefault` | `m` | 0x1F | — | 8 |
| `maximumSpeedCurrent` | `m` | 0x20 | — | 8（有效 0–254） |
| `aplStatus` | `m` | 0x21 | — | 8 |
| `aplColorStatus` | `m` | 0x22 | — | 8 |
| `aplBreathStatus` | `m` | 0x23 | — | 8 |
| `aplBreathFrequency` | `G` | 0x24 | 0 | 2 |
| `leaveVehicleAutoLockStatus` | `G` | 0x24 | 2 | 2 |
| `leaveVehicleAutoLockTimeStatus` | `G` | 0x24 | 4 | 2 |
| `sideBraceStatus` | `H` | `[0x25,0x26]` | — | 16 |
| `sideBraceCurrent` | `H` | `[0x26,0x27]` | — | 16 |
| `brakeModePowerRecoverFunctionStatus` | `C` | 0x27 | 0 | 3 |
| `brakeModePowerRecoverGearStatus` | `G` | 0x27 | 3 | 2 |
| `liftSpeedLimitModeStatus` | `G` | 0x28 | 0 | 2 |
| `busbarVoltage` | `H` | `[0x28,0x29]` | — | 16（`× 0.1f`） |
| `busbarCurrent` | `H` | `[0x2A,0x2B]` | — | 16（`× 0.1f`） |
| `pushModeStatus` | `H` | `[0x2A,0x2B]` | — | 16 |
| `squabSensingStatus` | `H` | `[0x2C,0x2D]` | — | 16 |
| `slideModePowerRecoverStatus` | `H` | `[0x2E,0x2F]` | — | 16 |

## 5. 关键数值字段（0x42–0x8F）

| 字段 | 调用 | 偏移 | 字节序 | 后处理 |
| --- | --- | --- | --- | --- |
| `totalBatteryCount` | `d` | 0x42 | 2 | — |
| `remainingMileage` | `H` | `[0x43,0x44]` | 小端 | `× 0.1f` + 保留 1 位小数 |
| `currentOdoValue` | `e` | `[0x45..0x48]` | 小端 | `× 0.1f` |
| `totalBatterySoc` | `m` | 0x49 | — | — |
| `currentTripValue` | `H` | `[0x49,0x4A]` | 小端 | `× 0.1f` |
| `auxiliaryBattery2Soc` | `m` | 0x4A | — | — |
| `currentSpeedValue` | `H` | `[0x4B,0x4C]` | 小端 | `× 0.1f` |
| `realKeyStatus` | `G` | 0x4D | 0 | — |
| `speedGear` | `C` | 0x4D | 2 | — |
| `onOffStatus` | `c` | 0x4E | 0 | 5 位 |
| `whistleMode` | `G` | 0x4E | 2 | — |
| `nfcWorkMode` | `G` | 0x4E | 4 | — |
| `nfcLearningCardMode` | `G` | 0x4E | 6 | — |
| `totalBatteryVoltage` | `H` | `[0x50,0x51]` | 小端 | `× 0.1f` |
| `totalBatteryElectricity` | `H` | `[0x52,0x53]` | 小端 | `× 0.1f` **再 `- 1000.0f`** |
| `batteryWorkingStatus` | `d` | 0x54 | 0 | — |
| `batteryChargingStatus` | `C` | 0x54 | 2 | — |
| `tBFullChargeRemainingMin` | `H` | `[0x55,0x56]` | 小端 | — |
| `pBElectricitySoc` | `m` | 0x57 | — | `× 0.5f`，**> 200 判无效** |
| `sBElectricitySoc` | `m` | 0x58 | — | 同上 |
| `faultCodeAmount` | `c` | 0x59 | 0 | 5 位 |
| `faultCodeProtocolVersion1` | `C` | 0x59 | 3 | — |
| `faultCodeProtocolVersion2` | `m` | 0x5A | — | — |
| `faultCode0..7` | `J` | `[0x5B..0x6A]` | **大端** | 仅当 v2==`0xF2` 且 amount>0 |
| `batteryChargingPowerStatus` | `G` | 0x70 | 4 | — |
| `batteryChargingLimitValue` | `C` | 0x70 | 3 | — |
| `batteryChargingPowerValue` | `m` | 0x71 | — | `× 2`（整数） |
| `drivingMemory` | `C` | 0x8E | 0 | — |
| `activateX` | `G` | 0x8E | 2 | — |
| `cruiseControl` | `G` | 0x8F | 0 | — |

## 6. 缩放规则汇总

| 字段 | 缩放 | 附加 |
| --- | --- | --- |
| `electricScooterMotorTemperature` | — | `- 40` |
| `electricScooterCyclingPower` | `× 0.1f` | — |
| `busbarVoltage` `busbarCurrent` | `× 0.1f` | — |
| `remainingMileage` `currentOdoValue` `currentTripValue` `currentSpeedValue` | `× 0.1f` | `remainingMileage` 额外保留 1 位小数 |
| `totalBatteryVoltage` | `× 0.1f` | — |
| `totalBatteryElectricity` | `× 0.1f` | `- 1000.0f` |
| `pBElectricitySoc` `sBElectricitySoc` | `× 0.5f` | **先判 > 200 无效** |
| `batteryChargingPowerValue` | `× 2` | 整数 |
| `batteryTemperature` | `× 0.1f` | `- 100.0f` |

**所有缩放都是条件性的**：等于哨兵值时跳过。

顺序很重要：`pBElectricitySoc` 是"先判无效、再缩放"，
`totalBatteryElectricity` 是"先缩放、再减 1000"。颠倒会产生错误数据。

## 7. 故障码

```text
v2 = m(d, 0x5A)
amount = c(d, 0x59, 0)

if (v2 == 0xF2 && amount > 0):
    faultCode0 = J(d, 0x5B, 0x5C)
    faultCode1 = J(d, 0x5D, 0x5E)
    faultCode2 = J(d, 0x5F, 0x60)
    faultCode3 = J(d, 0x61, 0x62)
    faultCode4 = J(d, 0x63, 0x64)
    faultCode5 = J(d, 0x65, 0x66)
    faultCode6 = J(d, 0x67, 0x68)
    faultCode7 = J(d, 0x69, 0x6A)
```

注意用的是**大端** `J`，与其他数值字段不同。

故障上报时 `faults` 编码为：每字节 `%02x` 后倒序、前后加 `/`、转大写。
例：`[1, 2]` → `"/02/01/"`。

## 8. 短报文 `l76.y`

只解析两个字段：

```text
batteryMutualRecognition = G(d, 4, 6)
speedLimitReleased       = G(d, 5, 0)
```

其余字段保持默认值。分派入口是 `data[2] == 0x29`。

## 9. 实现策略

`TtpInfo` 有 295 个字段，一次性录入易错且难验证。建议：

1. **先做已确认 offset 的字段**（本文档第 3–5 节，约 90 个）
2. **优先保证 AGENTS.md 列出的 11 个关键字段**：

   | 字段 | 状态 |
   | --- | --- |
   | `parkingStatus` | 已确认 |
   | `onOffStatus` | 已确认 |
   | `totalBatteryVoltage` | 已确认 |
   | `totalBatteryElectricity` | 已确认 |
   | `pBElectricitySoc` | 已确认 |
   | `currentOdoValue` | 已确认 |
   | `currentSpeedValue` | 已确认 |
   | `remainingMileage` | 已确认 |
   | `maximumSpeedCurrent` | 已确认 |
   | `drivingMemory` | 已确认（0x8E bit0-2） |
   | `cruiseControl` | 已确认（0x8F bit0-1） |

3. 其余字段按需增量补齐

## 10. 待确认

| 项 | 说明 |
| --- | --- |
| `remainingMileage` 的舍入模式 | 原实现用 `BigDecimal.setScale(1, mode)`，KMP 无 `BigDecimal` |
| 0x6A 之后到报文末尾的字段 | 部分状态位与配置字段未逐一核对 |
| `l76.y` 与 `l76.z` 的完整差异 | 目前只确认 2 个字段 |
| 经纬度的 double 转换 | 3 字节小端后的换算系数未确认 |
