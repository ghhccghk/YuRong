# 车辆设置第五轮专项修复报告

## A. 修改清单

| 文件 | 修改原因 | 修改内容 | Smali 证据 | KMP 原问题 | 协议/业务 |
| --- | --- | --- | --- | --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/VehiclePasswordGateway.kt` | VS-016 密码流程不完整 | 新增设置、修改、忘记密码、短信验证、状态查询、开关更新网关和控制器 | `VehicleSettingViewModel.smali:1841-1851,2911-2932,3554-3587,3615-3625,4122-4129,4234-4242`；`am4.smali` | KMP 只有布尔开关，无密码管理流程 | 云端业务 + 密码加密 |
| `VehicleSettingFrameEncoder.kt` | 密码云端设置成功后需要车辆写入 | 新增 `COMMAND_PASSWORD_UNLOCK_SETTING` 0x82 Golden Frame 编码 | `BleBicycleCommandManager.smali:570-574`、`PassWordUnlockActivity.smali:63-66` | 0x82 未编码 | BLE 设置协议 |
| `CarSettingsScreen.kt`、`DeviceScreen.kt`、`MainScreen.kt` | VS-016 UI | 新增双主题密码管理面板，覆盖设置、修改、忘记密码和失败反馈 | `PassWordUnlockActivity`、`SetNewPwdPopup`、`VerifyOriPwdPopup`、`SendCodeConfirmPopup` | 无密码业务 UI | UI/业务 |
| `androidApp/.../MainActivity.kt` | Android 网关接入 | 创建 `YadeaVehiclePasswordGateway` 与 `VehiclePasswordController` | 同上 | 未接线 | 平台装配 |
| `TtpInfo.kt` | X1 002 回读 | 新增 `bleAutoTurnOnOffStatus/GearStatus` | `l76.smali:9338-9351` | X1 002 被错误合并到普通 confId | 状态解析 |
| `CarSettingsGateway.kt` | X1 `001/002/003` 映射 | 保留字符串 ID，不转整数；001 为单位选择，002/003 拆开关和档位 | `X1DistanceUnitActivity`、`BleNearlyAutoUnlockActivity`、`X1EnergyRecoveryActivity`；`X1VehicleSettingViewModel.smali:666-795` | 第四轮误写为普通 1/2/3 | Mapper |
| `VehicleSettingCommands.kt` | X1 命令映射 | 001→0x89/0x8A；002→0x5C/0x5D/0x5E；003→0x15/0x16/0x3D | `ch.smali` 对应 commandType；`qg.smali` | X1 命令混入普通车型命令 | BLE 命令映射 |
| `VehicleSettingFrameEncoder.kt` | X1 003 帧 | 新增 0x15/0x16 suffix | `BleBicycleCommandManager.smali:189-193` | encoder 不支持 X1 制动回收开关 | BLE Golden Frame |
| 测试源码 | 回归保护 | 新增密码规则/0x82、X1 映射、距离 VIN 隔离测试 | 上述 Smali | 测试缺失 | 仅编译，不执行 |

## B. 功能状态表

| VS | 原 App 功能 | 原 App 证据 | KMP 修改 | 当前状态 | 未解决问题 |
| --- | --- | --- | --- | --- | --- |
| VS-016 | 密码解锁设置、修改、忘记密码 | `PassWordUnlockActivity`、3 个 Popup、`VehicleSettingViewModel` 密码 API、`am4` 密码校验/AES | 完整云端流程、敏感输入 UI、错误状态、0x82 后续车辆确认 | PARTIAL | 真机未验证；验证码/密码接口响应需实测；未保存明文 |
| X1-001 | X1 距离单位 | `X1DistanceUnitActivity.getSettingConfigId()="001"`；`METRIC_SYSTEM_SETTING`/`BRITISH_SYSTEM_SETTING` | 独立 `cloud:001` Choice，不再映射到普通 confId 1 | COMPLETE（静态） | 真机未验证 |
| X1-002 | X1 感应自动开关机 | `BleNearlyAutoUnlockActivity.getSettingConfigId()="002"`；`AUTO_TURN_ON_OFF_*` | `cloud:002:enable/level`，TTP `bleAutoTurnOnOff*` | COMPLETE（静态） | 真机未验证 |
| X1-003 | X1 制动能量回收 | `X1EnergyRecoveryActivity.getSettingConfigId()="003"`；`BRAKE_RECOVER_*` | `cloud:003:enable/level`，0x15/0x16/0x3D | COMPLETE（静态） | 真机未验证 |
| VS-018 | 普通一键开机距离 | `ModeGearView`、`OneKeyDistanceActivity`、`bleKeyDistanceLevel` | 保持 0..7 档；新增 VIN 隔离回归测试 | COMPLETE（基础档位） | 自定义距离标定仍未迁移 |
| VS-019 | 氛围灯模式 | `FeelLightSettingActivity`、`FeelLightSettingActivity$c`、0xF7 | 本轮未修改未知回读字段 | BLOCKED（回读） | 见下方调查 |
| VS-020 | 氛围灯颜色 | 同上，RGB1/RGB2、255→254 | 本轮未修改未知回读字段 | BLOCKED（回读） | 见下方调查 |

## C. 自定义距离标定只读审计

已确认调用链：

1. `CustomPowerOnDistancesActivity.customBleDistances()` → `COMMAND_CONST_CUSTOM_BLE_RANGING`。
2. ViewModel `c1()` 设置倒计时状态；`b1()` → `COMMAND_CONST_BLE_RANGING_END`。
3. `phoneBluetoothRecord/add` 上传 `phoneModel`、`os=1`，另有 `CustomPowerOnDistancesViewModel.d1()` 上传手机号、蓝牙信号值、MAC、VIN、`operationType=1`。
4. `showResultDialog()` 根据 `BleRangingState.rangingStart/isFail/isRangingFinish` 控制 10 秒倒计时、成功和失败弹窗。
5. 成功后 `RangingStateHelper.sendSceneType(2)`。

仍缺少：两条标定命令的完整帧生成分支、准确参数范围、开始/结束状态机、取消/异常退出后车辆状态保存和回读闭环。故本轮没有实现或开放标定写入。

## D. 氛围灯回读调查结论

### 已确认写入

- `FeelLightSettingActivity$c.smali:213-491` 一次性发送 `ATMOSPHERE_CONFIG`。
- `qg.smali:2225-2244` 映射 commandId `0xF7`。
- `BleBicycleCommandManager.generateMultiBleCommand(247,...)`：
  - colorMode：payload `0x15` bit0-1
  - RGB1：payload `0x3E..0x40`
  - RGB2：payload `0x42..0x44`
  - showMode：payload `0x45` bit0-2
  - operationMode：payload `0x45` bit3-5

### 状态报文字段

`l76.smali` 解析：

- `atmosphereShowMode`：`C(0x09,5)`
- `atmosphereRgb1*`：`0x0A..0x0C`
- `atmosphereRgb2*`：`0x0D..0x0F`
- `atmosphereOperationMode`：`C(0x1C,5)`
- `atmosphereColorMode`：`G(0x15,2)`

### 结论

写帧位置和状态报文位置不一致；`FeelLightSettingActivity` 的三个 LiveData 只观察连接/能力和 `ATMOSPHERE_CONFIG` 操作结果，没有把这些 TTP 字段回填到模式、RGB1、RGB2。没有找到“写入后由这些字段确认车辆已应用”的调用链。

因此：

- 不把 `atmosphereShowMode/ColorMode/OperationMode/RGB*` 当作 0xF7 的确认字段。
- 不把本地选择值标记为 `VEHICLE_CONFIRMED`。
- 本轮不修改未知回读协议字段。
- `cloud:73:config` 继续保持配置值 `UNKNOWN`，只保留已证实的写入能力。

## E. 剩余 Activity 分批审计

本轮深入检查 5 个 PARTIAL 页面：

| Activity | confId | 初始化/数据 | 写入 | 回读 | 状态 | 结论 |
| --- | ---: | --- | --- | --- | --- | --- |
| VehicleAlarmActivity | 13 | 云端 `alarmSoundEnable` | `WARING_SOUND_OPEN/CLOSE`，0x62/0x63 | 云端字段，无独立 TTP 字段 | WRITE_VERIFIED | KMP 映射一致，无修改 |
| VehicleAlarmSensitivityActivity | 14 | TTP `alarmWarningMode` | `ALARM_SENSITIVITY_GEAR`，0x5F，0..7 | `C(0x33,0)` | READBACK_VERIFIED + WRITE_VERIFIED | KMP 映射一致，无修改 |
| DumpNoticeActivity | 16 | 云端 `dumpAlarm` | `updateBikeSetting`，`dumpAlarm` | 云端字段，无 TTP | WRITE_VERIFIED | KMP 映射一致，无修改 |
| TurnSignalActivity | 26 | TTP `turnSignalReturnsAutomaticallyStatus` | `COMMAND_DIRECTION_LIGHT_RESET_ON/OFF`，0x23/0x24 | `G(0x33,6)` | READBACK_VERIFIED + WRITE_VERIFIED | KMP 开关方向一致，无修改 |
| ArmedModeActivity | 27 | TTP `offModeStatus` | `FORTIFY_MODE_NO/AUTO`，0x8D/0x8E | `G(0x1D,0)` | READBACK_VERIFIED + WRITE_VERIFIED | KMP 选项映射一致，无修改 |

这 5 项没有发现需要在本轮修改的代码问题。异常处理、并发和车辆切换仍需后续批次结合完整 ViewModel 状态机复核，因此不标记 `BEHAVIOR_VERIFIED`。

## F. 验证结果

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs`：通过。
- `:shared:compileTestKotlinJvm`：通过。
- `:androidApp:assembleDebug :desktopApp:build`：通过。
- 测试源码：已编写并编译。
- `jvmTest`：未执行。
- 真机：未验证。
- 云端：未验证。
- 车辆/BLE：未验证。

## G. Git

- `be87730`：密码解锁完整流程。
- `bc0ff4a`：X1 `001/002/003` 独立映射。
- `afae7c1`：一键开机距离 VIN 隔离回归测试。
- 文档提交：待提交。
