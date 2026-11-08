# 原 App 设置截图与 Smali/KMP 映射

本文件是第二轮修复前的只读审计基线。截图仅确认 UI 展示；业务字段、值转换和命令仍以 Smali 为准。用户真实车辆反馈只作为问题基线，不视为 Agent 已独立复现。

## 1. 截图清单

目录 `原app设置截图` 共 18 张 JPG、无子目录。

| 文件 | 页面 | 主要内容 |
| --- | --- | --- |
| `Screenshot_2026-10-23-21-40-25-786_com.yadea.smartmoto.jpg` | 极致省电智能休眠 | 立即休眠、自动休眠、连续 4 天关机后休眠 |
| `Screenshot_2026-10-23-21-40-36-482_com.yadea.smartmoto.jpg` | 设防模式 | 关机不设防 / 关机自动设防单选 |
| `Screenshot_2026-10-23-21-40-42-956_com.yadea.smartmoto.jpg` | 车辆设置-安全守护 | 电子围栏、ABS、TCS、休眠、报警音、灵敏度、设防、一键报失、定位器防盗 |
| `Screenshot_2026-10-23-21-40-50-492_com.yadea.smartmoto.jpg` | 车辆设置-灯光音效 | 自动大灯、氛围灯、小蓝灯、急刹爆闪、转向回位、车辆音量/音效、自定义音效、边撑开机/感应 |
| `Screenshot_2026-10-23-21-40-53-099_com.yadea.smartmoto.jpg` | 氛围灯设置 | 氛围灯自定义 Switch |
| `Screenshot_2026-10-23-21-40-57-249_com.yadea.smartmoto.jpg` | 车辆音效 | 音效一 / 音效二单选 |
| `Screenshot_2026-10-23-21-41-01-541_com.yadea.smartmoto.jpg` | 车辆音量 | 低 / 中 / 高三点 Slider |
| `Screenshot_2026-10-23-21-41-07-744_com.yadea.smartmoto.jpg` | 车辆设置-感应解锁/骑行控制 | 边撑开机、边撑感应、密码解锁、边撑自动关机、离车定时关机；FREE、狂暴、辅助推行 |
| `Screenshot_2026-10-23-21-41-10-450_com.yadea.smartmoto.jpg` | 密码解锁 | 开关、修改密码、忘记密码 |
| `Screenshot_2026-10-23-21-41-16-952_com.yadea.smartmoto.jpg` | 边撑自动关机 | Switch + 3/5/10/15 秒 |
| `Screenshot_2026-10-23-21-41-20-628_com.yadea.smartmoto.jpg` | 离车定时关机 | Switch + 2..20 分钟 Slider |
| `Screenshot_2026-10-23-21-41-38-714_com.yadea.smartmoto.jpg` | FREE 自由模式 | 动力曲线、TCS、能量回收 Switch + 低/中/高 |
| `Screenshot_2026-10-23-21-41-50-914_com.yadea.smartmoto.jpg` | 辅助推行 | 辅助推行 Switch、自适应模式、低/中/高速度 |
| `Screenshot_2026-10-23-21-41-55-459_com.yadea.smartmoto.jpg` | 能量回收 | 能量回收 Switch + 低/中/高 |
| `Screenshot_2026-10-23-21-41-58-170_com.yadea.smartmoto.jpg` | 车辆设置-骑行控制 | FREE、狂暴、辅助推行、陡坡缓降、坡道驻停、能量回收、安全限速、自定义按键 |
| `Screenshot_2026-10-23-21-42-10-675_com.yadea.smartmoto.jpg` | 仪表亮度调节 | Switch + 0..100% Slider |
| `Screenshot_2026-10-23-21-42-17-934_com.yadea.smartmoto.jpg` | 实验室 Beta | SOS 求救入口 |
| `Screenshot_2026-10-23-21-42-22-133_com.yadea.smartmoto.jpg` | SOS 求救 | 自动短信、自动电话、车主信息、紧急联系人 |

## 2. 设置项映射

| 原 App 分组 | 设置名称 | 控件 | Smali Activity/类 | Smali 字段/命令 | KMP Setting ID | TTP 回读字段 | 写入路径 | 证据状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 安全守护 | 电子围栏 | 入口 + Switch | Registry/云端动态项 | `distanceOn`, `distance` | `cloud:10`, `cloud:10:distance` | UNKNOWN | `updateBikeSetting` / IoT | PARTIAL |
| 安全守护 | ABS 防抱死 | Switch | `AntiLockActivity` | `ABS_ANTI_LOCK_OPEN/CLOSE` | `cloud:38` | `absFunctionStatus` | BLE 0x99/0x9A | COMPLETE-static |
| 安全守护 | TCS 车身稳定 | Switch | `BodyStabilityActivity` | `TCS_BODY_STABILITY` | `cloud:37` | `tcsWorkMode` | BLE 0x9D | COMPLETE-static |
| 安全守护 | 休眠模式 | 入口 | `HibernateModeActivity` | config 77、`SLEEP_MODE_SWITCH` | `cloud:77` | `sleepModeSwitch` | BLE / cloud | PARTIAL |
| 安全守护 | 报警音 | Switch | `VehicleAlarmActivity` | config 13、`alarmSoundEnable` | `cloud:13` | UNKNOWN | BLE 0x62/0x63 / cloud | PARTIAL |
| 安全守护 | 车辆报警灵敏度 | 单选入口 | `VehicleAlarmSensitivityActivity` | config 14 | `cloud:14` | `alarmWarningMode` | BLE 0x5F | PARTIAL |
| 安全守护 | 设防模式 | 二选一 | `ArmedModeActivity` | config 27 | `cloud:27` | `offModeStatus` | BLE 0x8D/0x8E | COMPLETE-static |
| 安全守护 | 一键报失 | 入口 | `LostReportModeActivity` | config 8 | `cloud:8` | UNKNOWN | 诊断/云端流程 | PARTIAL |
| 安全守护 | 定位器防盗 | Switch | `IotTamperProofActivity` | config 75、`tamperProofIotEnable` | `cloud:75` | `iotRemoveAlarm` | cloud/IoT | PARTIAL |
| 灯光音效 | 自动大灯 | Switch | `AutomaticLightActivity` | config 19 | `cloud:19` | `automaticHeadlightFunctionStatus` | BLE 0x25/0x26 | PARTIAL |
| 灯光音效 | 氛围灯自定义 | Switch | `FeelLightSettingActivity` | config 73 | `cloud:73` | `ambientStatus` | BLE 0xC3 | PARTIAL |
| 灯光音效 | 安全辅助小蓝灯 | Switch | `AssistDriveLightActivity` | config 84 | `cloud:84` | `blueLightSwitch` | BLE command | PARTIAL |
| 灯光音效 | 急刹爆闪 | Switch | `SuddenBrakingActivity` | config 68 | `cloud:68` | `quickBrakeLightStatus` | BLE 动态位域 | PARTIAL |
| 灯光音效 | 转向灯自动回位 | Switch | `TurnSignalActivity` | config 26 | `cloud:26` | `turnSignalReturnsAutomaticallyStatus` | BLE 动态位域 | PARTIAL |
| 灯光音效 | 车辆音量 | 三点 Slider | `VehicleVolumeActivity` | config 20 | `cloud:20` | `alarmWarningVolumeLevel` | BLE 0x46 | COMPLETE-static |
| 灯光音效 | 车辆音效 | 二选一 | `OldVehicleSoundEffectsActivity` | config 5 | `cloud:5` | UNKNOWN | BLE 0x2E | PARTIAL |
| 灯光音效 | 自定义音效 | 二级入口 | `CarSettingsNavigation` | config 41/88 | `cloud:41`, `cloud:88` | 资源查询 | `resourcesBike/*` | COMPLETE-cloud |
| 感应解锁 | 边撑开机 | Switch | `BracketPoweredActivity` | config 53、`COMMAND_CONST_TEMPLE_SETTING` | `cloud:53` | `templeSettingStatus` | BLE 0xC7 | PARTIAL |
| 感应解锁 | 边撑感应 | Switch | `TempleInductionActivity` | config 2 | `cloud:2` | `sideBraceStatus` | BLE 0x0F/0x10 | COMPLETE-golden |
| 感应解锁 | 密码解锁 | 入口 + Switch | `PassWordUnlockActivity` | config 25 | `cloud:25` | `passwordUnlockStatus` | cloud/BLE | PARTIAL |
| 感应解锁 | 边撑自动关机 | Switch + 四档 | `BraceAutoShutDownActivity` | config 7/39 | `cloud:7`, `cloud:39:time` | `sideBraceStatus`, `leaveVehicleAutoLockTimeStatus` | BLE 0x53/54/55 | PARTIAL |
| 感应解锁 | 离车定时关机 | Switch + Slider | `ScheduledShutDown2Activity` | config 59 | `cloud:59`, `cloud:59:time` | `vehiclePowerDownStatus/TimeStatus` | BLE/cloud | PARTIAL |
| 骑行控制 | FREE 自由模式 | 二级面板 | `VehicleCustomizeActivity/2` | config 55/92、`CUSTOMIZE_SETTING` | `cloud:55`, `cloud:92` | `customizeThrottleSensitivity`, `customizeEnergyLevel`, `customizeEnergyState`, `customizeTcs` | BLE 0xC8 | PARTIAL |
| 骑行控制 | 超级狂暴模式 | Switch | `TPowerActivity` | config 63 | `cloud:63` | `tPowerStatus` | BLE 0xCF | COMPLETE-static |
| 骑行控制 | 辅助推行总开关 | Switch | `HelpPromotePlusActivity/2` | config 64/89、`STROLLER_OPEN/CLOSE` | `cloud:64`, `cloud:89` | `pushModeStatus` / `helpCarState`（需按版本确认） | BLE 0xA8/0xA9 | UNKNOWN-readback |
| 骑行控制 | 自适应推行 | CheckBox | `HelpPromotePlusActivity/2` | `COMMAND_CONST_CUSTOM_DRIVE_SWITCH_SETTING` | `cloud:64:adaptive`, `cloud:89:adaptive` | `customDriveSwitchStatus`；64 映射 ==1，89 映射 ==2 | BLE 0xD0 | BROKEN-89 |
| 骑行控制 | 推行速度 | 三点 Slider | `HelpPromotePlusActivity/2` | `COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING` | `cloud:64:level`, `cloud:89:level` | `customDriveLevelStatus` 0..2 | BLE 0xD1 | BROKEN-offset |
| 骑行控制 | 陡坡缓降 | Switch | `SlopeDescentActivity` | config 46 | `cloud:46` | `steepSlopeState` | BLE 动态位域 | PARTIAL |
| 骑行控制 | 坡道驻停 | Switch | Ride Registry | config 49 | `cloud:49` | `rampSlopeState` | BLE 动态位域 | PARTIAL |
| 骑行控制 | 能量回收 | Switch + 三点 Slider | `EnergyRecoveryActivity` | config 17/18、`ENERGY_RECOVER_*` | `cloud:17`, `cloud:18` | `slideModePowerRecoverStatus/Current` | BLE 0x13/0x14、0x3D | BROKEN-field |
| 骑行控制 | 安全限速 | Slider/入口 | `MaxSpeedLimitActivity` | config 61 | `cloud:61` | `maximumSpeedCurrent` | BLE 0x36 / param-setting | COMPLETE-golden |
| 骑行控制 | 自定义按键 | 短按/长按 Choice | `CustomizeKeyActivity` | config 1005、`CUSTOMIZE_KEYS_CONFIG_*` | `cloud:1005:short/long` | `customFunctionShort/Long` | BLE 0x131/0x130 | BROKEN-readback |
| 智慧充电 | 充电功率 | 二级入口 | `ChargingPowerActivity` | config 47、`CHARGE_WATT_*` | `cloud:47`, `cloud:47:enable` | `batteryChargingPowerStatus/Value` | BLE 0xC9/0xCA / param-setting | PARTIAL |
| 智慧充电 | 低电量提醒 | 入口 | `LowPowerActivity` | config 82/9 | `cloud:82`, `cloud:9` | `lowBatTelWarning` | cloud/IoT | PARTIAL |
| 智能仪表 | 仪表亮度调节 | Switch + Slider | `BrightnessAdjustmentActivity` | config 86 | `cloud:86`, `cloud:86:value` | `brightnessAdjustSwitch/Value` | BLE 0x135/0x134 | PARTIAL |
| 实验室 | SOS 求救 | 二级入口 | `SosContactActivity` | config 91/24 | `cloud:91`, `cloud:24` | `sosEmergencyCall`；联系人独立网关 | cloud/IoT | PARTIAL |

## 3. 三个异常项定位

### VS-013 能量回收

- 原 Activity：`EnergyRecoveryActivity.getSettingConfigId()` 返回 `17`，同一页面按 `UiMode` 使用 `17/18`。
- 原回读：`VehicleSettingViewModel` 使用 `slideModePowerRecoverStatus` 与 `slideModePowerRecoverCurrent`。
- 原解析：
  - `slideModePowerRecoverStatus = l76.G(data, 0x26, 0)`。
  - `slideModePowerRecoverCurrent = l76.C(data, 0x26, 2)`。
  - 原始档位 `1/2/3` 转成 UI 进度 `0/50/100`；写入为 `ENERGY_RECOVER_LEVEL`，参数 `1/2/3`。
- 当前 KMP：读取 `brakeModePowerRecoverFunctionStatus/GearStatus`，其中 gear 还使用 `Ttp.C(d, 0x27, 3)`，与 `l76.C(d, 0x27, 0)` 不一致。
- 根因：使用了 X1/刹车回馈字段而非普通 EnergyRecoveryActivity 的滑行回馈字段，且附加位域偏移错误。

### VS-014 自定义按键中的辅助推行

- 原入口：`CustomizeKeyActivity`，config `1005`。
- 原写入：`CUSTOMIZE_KEYS_CONFIG_SHORT/LONG`，函数 ID 由 `getFunctionId()` 映射。
- 原回读：`customFunctionShort/customFunctionLong`，均来自 `0x6C`；短按 bit4-7、长按 bit0-3。
- 当前 KMP：TTP 已解析这些字段，但 `CarSettingsController.applyVehicleStatus()` 完全没有 `1005` 分支，短按/长按永远不回填。
- 证据缺口：当前 Smali 的 `getFunctionName()` 只包含 ABS/TCS/雷达/记录仪/长续航/氛围灯/坐垫加热/AI/无定义，没有“辅助推行”字符串。因此可以修复 1005 通用回读，但不能凭名称把“辅助推行”绑定到某个函数 ID；该名称到 ID 的关系继续标为 UNKNOWN。

### VS-015 自适应推行

- 原入口：`HelpPromotePlusActivity` config `64`、`HelpPromotePlus2Activity` config `89`。
- 原写入：
  - `64`：CheckBox 勾选 → `0`，取消 → `1`。
  - `89`：CheckBox 勾选 → `0`，取消 → `2`。
  - 速度 Slider 的 `thumbPosition` 直接作为 `COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING` 参数，即 0..2。
- 原回读：
  - `64` CheckBox：`customDriveSwitchStatus == 1`。
  - `89` CheckBox：`customDriveSwitchStatus == 2`。
  - 速度：`customDriveLevelStatus` 原值直接作为 0..2 进度。
- 当前 KMP：
  - 64/89 都使用 `flagOrNull()`，89 的 `2` 被映射为 false。
  - 89 取消仍写 `1`，不是 `2`。
  - 速度模型使用 1..3，导致 0 值被过滤、写入整体偏移 1。
- 根因：两个车型变体的状态值和写入值不同，KMP 统一按 Boolean 0/1 和 1..3 处理。

## 4. 保护范围

除上述三个异常项的字段读取/值转换和 1005 回填外，不修改其他已由用户实测正常的 TTP 映射。VS-007/011/012 的写入安全复核若缺少完整帧证据，只调整证据/权限表达，不扩大命令写入能力。
