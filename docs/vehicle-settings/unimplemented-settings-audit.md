# 当前车辆设置未实现选项审计（第七轮）

## 1. 审计口径

本报告以当前 KMP 设置 UI 实际生成逻辑为入口：

- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingFrameEncoder.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/TtpInfo.kt`

原始行为只采信 `F:\android app code\YadeaHook\temp_smali` 中可追踪的 Activity、Popup、ViewModel、命令常量和 TTP 字段。截图只用于 UI 参考。未找到闭环证据的内容保持 `UNKNOWN/BLOCKED`。

状态定义：

- `COMPLETE`：当前已有读取/写入/回读链路，且没有已知的单项缺口；不代表真机验证通过。
- `PARTIAL`：主链路存在，但仍有子流程、初值、回读或状态确认缺口。
- `WRITE_ONLY`：可发起写入或保存，但没有可靠车辆回读/确认。
- `READ_ONLY`：可读取或管理数据，但没有安全的设置写入链路。
- `UI_ONLY`：当前固定清单中没有仅显示且完全无业务的已知项；云端未知 confId 动态落入此项。
- `CONDITIONAL`：依赖硬件、账号、车型/权限，或按约束隐藏/跳转到其他模块。
- `BLOCKED`：关键协议或业务证据不足，不允许猜测迁移。

## 2. 数量统计

| 指标 | 数量 | 说明 |
| --- | ---: | --- |
| 当前数字 confId 总数 | 81 | `TITLES` 中数字 ID；`95` 当前隐藏但保留映射 |
| 当前固定特殊入口 | 5 | meter/footer 本地入口 |
| `COMPLETE` | 45 | 静态链路完整，未做真机确认 |
| `PARTIAL` | 17 | 有实现但仍有缺口 |
| `WRITE_ONLY` | 12 | 可写但无可靠车辆回读 |
| `READ_ONLY` | 2 | 联系人/信息管理类 |
| `UI_ONLY` | 0 个固定项 | 云端未知 confId 会动态生成 `Info` 占位，数量取决于车辆配置 |
| `CONDITIONAL` | 5 | 8/66/71/95 及 5 个特殊入口按不同口径条件展示 |
| `BLOCKED`（原 App 功能） | 5 | LowPower、Multifunction、StartLight、TemperatureUnit、CustomPowerOnDistance 标定 |
| 原 App 存在但 KMP 无独立入口 | 5 | ConfigSetting 已由 19/31 覆盖；SmartBoot 属外设绑定，不计车辆设置缺口 |
| 非 Activity 设置承载 | 至少 10 | Popup/Dialog/Custom View，见第四轮审计 |

说明：`CONDITIONAL` 与特殊入口不是数字 confId 的互斥子集；上表的 81 个数字 ID 主分类为 45+17+12+2+5=81。

## 3. 当前设置完整清单

| ID | 当前名称 | 控件/入口 | 读取 | 写入 | 车辆回读 | 状态 | 主要缺口或备注 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 001 | X1 距离单位 | Choice km/h、mph | TTP | BLE 0x89/0x8A | `metricSystemStatus` | COMPLETE | 与 ID 31 独立，不能合并 |
| 002 | X1 感应自动开关机 | 拆分 Toggle + 0..2 Slider | TTP | BLE 0x5C/0x5D/0x5E | `bleAutoTurnOnOffStatus/GearStatus` | COMPLETE | 保持 `002` 字符串 ID |
| 003 | X1 制动能量回收 | 拆分 Toggle + 1..3 Slider | TTP | BLE 0x15/0x16/0x3D | `brakeModePowerRecover*` | COMPLETE | 与普通能量回收 17/18 字段不同 |
| 1 | 一键开机 | 拆分 Toggle + 0..7 Slider | TTP | BLE 0x0D/0x0E/0x37 | `bleKeyStatus/DistanceLevel` | PARTIAL | 自动/自定义距离标定未迁移，继续只读取证 |
| 2 | 边撑感应 | Toggle | TTP | BLE | `sideBraceStatus` | COMPLETE | 已有实车基线保护 |
| 3 | 携出助力 | Toggle | 无独立 TTP 证据 | BLE 0x11/0x12 | 无 | WRITE_ONLY | 不得伪造关闭/开启确认 |
| 4 | 档位设置/最高车速 | Slider | 云端 + TTP | BLE 0x36 | `maximumSpeedCurrent` | COMPLETE | 保持现有范围 |
| 5 | 车辆音效 | Choice 2 项 | 云端 | BLE 0x2E | 未闭环 | PARTIAL | 主题值与车辆确认仍需真机验证 |
| 7 | 自动落锁 | Toggle + 时间 Choice | 云端 | BLE 0x53/0x54/0x55 | 时间字段未闭环 | PARTIAL | 时间初值当前固定 3 秒，不能视为车辆值 |
| 8 | 一键报失 | 跳转诊断/报失 | 独立网关 | 独立流程 | 不适用 | CONDITIONAL | 不与普通 Toggle 合并 |
| 9 | 信息提醒 | Toggle | 云端 | `updateBikeSetting`/IoT | 无车辆回读 | WRITE_ONLY | 云端成功不等于车辆确认 |
| 10 | 电子围栏 | Toggle + 半径 Slider | 云端 | IoT | 无车辆回读 | PARTIAL | 半径云端字段可用，确认能力未知 |
| 11 | 自动开关机 | Toggle + 近/中/远 | 云端/TTP | BLE | 部分字段 | PARTIAL | 开关与距离需继续核对状态机 |
| 13 | 报警音 | Toggle | 云端 | BLE/云端 | 无可靠回读 | WRITE_ONLY | `alarmSoundEnable` 是配置值，不是车辆确认 |
| 14 | 车辆报警灵敏度 | Slider 0..7 | TTP | BLE 0x5F | `alarmWarningMode` | COMPLETE | 本轮修正初值未知状态 |
| 15 | 离车定时关机 | Toggle + Slider 0..20 | TTP | BLE 0x7B/0x7C/0x7D | `vehiclePowerDown*` | COMPLETE | 保护上一轮修复 |
| 16 | 倾倒报警 | Toggle | 云端 | `updateBikeSetting` | 无车辆回读 | WRITE_ONLY | 只表达云端接受 |
| 17 | 能量回收开关 | Toggle | TTP | BLE | `slideModePowerRecoverStatus` | COMPLETE | VS-013 保护项 |
| 18 | 能量回收档位 | Slider 1..3 | TTP | BLE 0x3D | `slideModePowerRecoverCurrent` | COMPLETE | VS-013 保护项 |
| 19 | 自动大灯 | Toggle | TTP | BLE | `automaticHeadlightFunctionStatus` | COMPLETE | ConfigSettingActivity 的开关部分已覆盖 |
| 20 | 车辆音量 | Slider 0..7 | TTP | BLE 0x46 | `alarmWarningVolumeLevel` | COMPLETE | 本轮修正初值未知状态 |
| 21 | 伴我回家灯 | Toggle + 15/30/45/60s | TTP | BLE 0x83/0x84/0x85 | `withGoHome*` | PARTIAL | 本轮修正时间初值；完整帧仍不扩大 |
| 24 | 紧急联系人 | 联系人编辑入口 | 联系人网关 | 联系人网关 | 联系人查询 | READ_ONLY | 不是普通布尔设置 |
| 25 | 密码解锁 | Toggle + 设置/修改/忘记密码 | 云端 + TTP | 云端后 BLE 0x82 | `passwordUnlockStatus/SettingResult` | COMPLETE | 第五/六轮保护项；不记录密码 |
| 26 | 转向灯自动回位 | Toggle | TTP | BLE 动态位域 | TTP 字段 | COMPLETE | 已有 READBACK/WRITE 证据 |
| 27 | 设防模式 | Choice | TTP | BLE 0x8D/0x8E | `offModeStatus` | COMPLETE | 关机不设防/自动设防 |
| 31 | 速度单位 | Choice | TTP | BLE 0x89/0x8A | `metricSystemStatus` | COMPLETE | ConfigSettingActivity 的单位部分已覆盖 |
| 32 | 倾倒提醒 | Toggle | TTP | BLE | `tiltWarningStatus` | COMPLETE | |
| 33 | 模拟声浪 | Toggle | TTP | BLE | `simulatedSoundWaveStatus` | COMPLETE | 模式子项仍需后续审计 |
| 36 | 刹车断电 | Toggle | TTP | BLE 动态位域 | `brakePowerDownFunctionStatus` | COMPLETE | |
| 37 | TCS 车身稳定 | Toggle + 二次确认 | TTP | BLE 0x9D | `tcsWorkMode` | COMPLETE | 0 才是开启，保护现有语义 |
| 38 | ABS 防抱死 | Toggle + 二次确认 | TTP | BLE 0x99/0x9A | `absFunctionStatus` | COMPLETE | |
| 39 | 边撑自动关机 | Toggle + 时间 Choice | 云端/TTP | BLE | 时间字段未闭环 | PARTIAL | 初值固定，未视为车辆确认 |
| 40 | 彩蛋音效 | Toggle | 云端 | BLE/云端 | 无可靠回读 | WRITE_ONLY | |
| 41 | 自定义音效 | 两层子页面 | 云端资源 | 资源网关 | 应用结果轮询 | PARTIAL | 入口不是空实现，但平台能力不一致 |
| 42 | 车辆显示剩余里程 | Toggle | TTP | BLE | `remainingMileageDebugStatus` | COMPLETE | |
| 43 | 倒车模式 | Toggle | TTP | BLE | `reversingState` | COMPLETE | kindOf 映射已修正 |
| 44 | 婴儿车模式 | Toggle | TTP | BLE | `helpCarState` | COMPLETE | kindOf 映射已修正 |
| 45 | 定速巡航 | Toggle | TTP | BLE | `cruiseControl` | COMPLETE | |
| 46 | 陡坡缓降 | Toggle | TTP | BLE | `steepSlopeState` | COMPLETE | |
| 47 | 充电功率 | 拆分 Toggle + 200..1000W | TTP/云端 | BLE 0xC9/0xCA | `batteryChargingPower*` | COMPLETE | 固定约束 200..1000W |
| 48 | 充电上限 | Slider 70..100% | 云端 | BLE/云端 | 无车辆回读 | WRITE_ONLY | 云端值不能伪装车辆确认 |
| 49 | 坡道驻停 | Toggle | TTP | BLE | `rampSlopeState` | COMPLETE | |
| 50 | 仪表风格切换 | Choice 3 项 | 云端默认值 | BLE 0x3F | 无 TTP 字段 | WRITE_ONLY | 本轮修正初值未知；回读 BLOCKED |
| 51 | 仪表时间同步 | Toggle/操作入口 | TTP 结果位 | BLE | `syncTimeResultStatus` | PARTIAL | 结果位不等于当前设置值 |
| 52 | 手动距离自定义 | Toggle + 1..12m Slider | 云端 | BLE/云端 | 无车辆回读 | PARTIAL | 自定义距离标定不开放 |
| 53 | 边撑开机 | Toggle | TTP | BLE 0xC7 | `templeSettingStatus` | COMPLETE | |
| 54 | 电压设置 | Choice 48/60/70V | TTP | BLE | `voltageSettingStatus` | COMPLETE | |
| 55 | FREE 自由模式 | Customize 四字段 | TTP | BLE 0xC8 | `customize*` | PARTIAL | 四字段整体写入，异常/并发仍需复核 |
| 58 | 档位设置/最高车速 | Slider | 云端 + TTP | BLE 0x36 | `maximumSpeedCurrent` | COMPLETE | 与 4 同类但 ID 独立 |
| 59 | 定时关机 | Toggle + 时间 Choice | TTP | BLE 0x7B/0x7C/0x7D | `vehiclePowerDown*` | COMPLETE | 与 15 共用命令但 ID 独立 |
| 60 | 电池设置 | 拆分 Toggle + 容量 Choice | TTP/云端 | BLE 0xD4/0xCD | `capacitySwitch/Setting` | COMPLETE | 稳定 ID `cloud:60:*` |
| 61 | 安全限速 | Slider | 云端 + TTP | BLE 0x36/参数写入 | `maximumSpeedCurrent` | COMPLETE | 0=不限速，非零最低 15 |
| 62 | 座椅加热 | Toggle + 1..3 档 | TTP/云端 | BLE/云端 | `seatHeat*` | COMPLETE | |
| 63 | 超级狂暴模式 | Toggle | TTP | BLE | `tPowerStatus` | COMPLETE | |
| 64 | 辅助推行 | 拆分自适应 Toggle + 0..2 Slider | TTP | BLE 0xD0/0xD1 | `customDrive*` | PARTIAL | VS-014/015 保护项；当前读取范围与既有证据存在回归风险，本轮不修改 |
| 66 | 自动校准 | 车钥匙入口 | 车钥匙模块 | 车钥匙模块 | 不适用 | CONDITIONAL | 数字车钥匙范围外 |
| 68 | 急刹爆闪 | Toggle | TTP | BLE | `quickBrakeLightStatus` | COMPLETE | |
| 69 | 雷达 | 拆分 FCW/RCW/BSD | TTP | BLE 0x13A/0x12D/0x12E | `frontCollision/rcw/bsd` | COMPLETE | 稳定 ID `cloud:69:*` |
| 71 | 仪表设置 | 连接页入口 | 无 | 不直接写 | 不适用 | CONDITIONAL | 配对入口，不是参数项 |
| 72 | 长续航模式 | Toggle | TTP | BLE | `longEnduranceStatus` | COMPLETE | |
| 73 | 氛围灯 | Toggle + 模式/颜色子页 | 部分 TTP | BLE 0xC3/0xF7 | 模式/RGB 回读 UNKNOWN | PARTIAL | 本地草稿不冒充车辆状态，未知字段不修改 |
| 75 | 定位器防盗 | Toggle | 云端 | IoT | 无车辆回读 | WRITE_ONLY | |
| 76 | 定位开关 | Toggle | 云端 | `param-setting` | 无车辆回读 | WRITE_ONLY | |
| 77 | 休眠模式 | Toggle | TTP | BLE | `sleepModeSwitch` | COMPLETE | |
| 79 | 座垫感应上电 | Toggle | TTP | BLE | `seatPowerStatus` | COMPLETE | |
| 80 | OTD 开关 | Toggle（反值参数） | TTP | BLE | `otdSwitch` | COMPLETE | 0=开、1=关 |
| 81 | 大灯延时 | Toggle + 5/10/15/30 | TTP | BLE 0x83/0x84/0x85 | `withGoHome*` | PARTIAL | 本轮修正时间初值 |
| 82 | 电话告警 | Toggle | 云端 | IoT | 无车辆回读 | WRITE_ONLY | |
| 84 | 安全辅助小蓝灯 | Toggle | TTP | BLE | `blueLightSwitch` | COMPLETE | |
| 85 | SOS 联系人 | 联系人编辑入口 | 联系人网关 | 联系人网关 | 联系人查询 | READ_ONLY | 与 24 共用编辑器 |
| 86 | 仪表亮度调节 | Toggle + 0..100 Slider | TTP | BLE 0x135/0x134 | `brightnessAdjust*` | COMPLETE | |
| 88 | 自定义音效 | 两层子页面 | 云端资源 | 资源网关 | 应用结果轮询 | PARTIAL | 与 41 共用同一套管理界面 |
| 89 | 辅助推行变体 | 拆分自适应 Toggle + 0..2 Slider | TTP | BLE 0xD0/0xD1 | `customDrive*` | PARTIAL | 与 64 取消值不同，禁止合并 |
| 90 | 赛道模式 | Toggle | TTP | BLE | `trackModeStatus` | COMPLETE | |
| 91 | SOS 紧急呼叫 | Toggle | 云端 | IoT | 无车辆回读 | WRITE_ONLY | |
| 92 | 自定义骑行 | Customize 四字段 | TTP | BLE 0xC8 | `customize*` | PARTIAL | 与 55 共用类型但 ID 独立 |
| 93 | 静音模式 | Toggle | TTP 无可靠语义 | BLE 0x141 新旧字段 | UNKNOWN | WRITE_ONLY | 新旧字段方向未闭环 |
| 94 | 档位记忆 | Toggle | TTP | BLE | `gearMemorySettingFeedback` | COMPLETE | |
| 95 | 微信互联 | 当前隐藏 | 云端 | 未接入 | UNKNOWN | CONDITIONAL | 按既有约束保持隐藏 |
| 1005 | 自定义按键 | 拆分短按/长按 Choice | TTP | BLE 0x131/0x130 | `customFunctionShort/Long` | PARTIAL | “辅助推行”名称到函数 ID 仍 UNKNOWN，不猜测 |

### 3.1 稳定拆分条目

| 条目 ID | 来源 confId | 业务 | 状态 |
| --- | ---: | --- | --- |
| `cloud:1:enable` / `cloud:1:distance` | 1 | 一键开机开关 / 0..7 距离档 | PARTIAL |
| `cloud:002:enable` / `cloud:002:level` | 002 | X1 感应自动开关机 / 0..2 档 | COMPLETE |
| `cloud:003:enable` / `cloud:003:level` | 003 | X1 制动能量回收 / 1..3 档 | COMPLETE |
| `cloud:47:enable` | 47 | 充电功率开关 | COMPLETE |
| `cloud:60:enable` / `cloud:60:level` | 60 | 电池容量开关 / 13/15/22/25/32/38Ah | COMPLETE |
| `cloud:62:level` | 62 | 座椅加热档位 | COMPLETE |
| `cloud:64:adaptive` / `cloud:64:level` | 64 | 自适应推行 / 0..2 速度 | PARTIAL |
| `cloud:89:adaptive` / `cloud:89:level` | 89 | 自适应推行变体 / 0..2 速度 | PARTIAL |
| `cloud:69:fcw` / `:rcw` / `:bsd` | 69 | 雷达三项 | COMPLETE |
| `cloud:1005:short` / `:long` | 1005 | 短按/长按功能 | PARTIAL |
| `cloud:21:time` / `cloud:81:time` | 21/81 | 延时灯时间 | PARTIAL |
| `cloud:73:config` | 73 | 氛围灯模式与颜色 | PARTIAL / readback UNKNOWN |
| `local:1005` | 1005 | 云端未下发时固定入口 | PARTIAL |

## 4. 未实现和不安全迁移清单

| 功能 | 原 App 承载 | Smali 证据 | KMP 当前状态 | 缺失内容 | 是否允许迁移 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| 自定义距离标定 | `CustomPowerOnDistancesActivity`、`ModeAutoView`、`ModeCustomView` | `customBleDistances()`、`COMMAND_CONST_CUSTOM_BLE_RANGING`、`COMMAND_CONST_BLE_RANGING_END`、`phoneBluetoothRecord/add` | 只有 0..7 档基础值 | 完整帧、参数范围、10 秒状态机、取消/断连、结果保存 | 否，继续只读 | P1/L3 |
| 低功耗模式 | `LowPowerModeInfoActivity` + `LowPowerViewModel` | `LowPowerViewModel.W0()` → `protocol/control-vehicle/low-power`，body `{vin}`；确认弹窗后才调用 | 无入口 | 云端请求结果语义、进入后不可手动退出状态、与当前 VIN/车辆状态联动 | 暂不迁移，属于真实车辆控制 | P2/L3 |
| 多功能键短按/长按 | `MultifunctionActivity` + `ShortSettingsPopup` | Popup 单选“双闪/里程/报警音”，`c0()` 只返回显示字符串，没有找到命令/接口 | 无对应设置项 | 明确保存通道、车辆命令/云端字段、回读 | 否，证据未闭环 | P2/L3 |
| 启动灯 | `StartLightActivity` + Binding | Binding 绑定 `VehicleSettingViewModel.q3()` Boolean；Activity 本身无写入调用 | 无入口 | q3 对应业务字段、SeekBar 参数、写入命令和回读 | 否，证据未闭环 | P2/L3 |
| 温度单位 | `TemperatureUnitActivity` | `onCheckChange()` 只更新文本，ViewModel 为空；Binding 不绑定 LiveData | 无入口 | 是否仅本地 UI、持久化位置、是否影响车辆显示 | 暂不迁移 | P3 |
| 智能启动外设 | `SmartBootActivity` / `SmartBootViewModel` | 外设绑定/解绑、`upload2Cloud`，属于 peripherals/boot | 不属于车辆设置 | 外设配对模型，不是当前车辆参数 | 不计入车辆设置迁移 | NOT_APPLICABLE |
| ConfigSettingActivity | 自动大灯 + 单位组合页 | Switch → `AUTOMATIC_HEADLIGHT_OPEN/CLOSE`；RadioGroup → `IMPERIAL_UNIT_KM/MILES`；TTP `automaticHeadlightFunctionStatus` | 已由 `cloud:19`、`cloud:31` 覆盖 | 无 | 已覆盖，不重复创建 | 已完成映射 |
| 云端未知 confId | 动态 `bikeConfigVos` | 只能确认配置 ID 存在，不能由名称推断命令 | `Info("云端设置项，暂未接入写入")` | 业务字段、写入、回读 | 不允许按名称猜测 | 动态 BLOCKED |

## 5. 本轮确定的可修复问题

### P0：回读-backed Slider 初值伪装为已知

位置：`CarSettingsGateway.kindOf("20", "14")`。

当前 `CarSettingKind.Slider(value = 0f, range = 0f..7f)` 默认 `valueKnown=true`，但真实值由 `TtpInfo.extended.alarmWarningVolumeLevel/alarmWarningMode` 回填。TTP 未到达时 UI 会把 0 显示为车辆已知值。

### P0：延时灯时间 Choice 初值伪装为已知

位置：`toItems()` 中 `cloud:21:time`、`cloud:81:time`。

两项都有 TTP 回填（`withGoHomeTimeStatus`），但构造 Choice 时默认 `valueKnown=true`，会把选项 0 当作车辆当前值。

### P1：仪表风格 50 是写入型但没有回读

位置：`kindOf("50")`、`VehicleSettingCommands.forConfigId("50")`。

`50` 可编码 0x3F，但 `CarSettingsController.applyVehicleStatus()` 没有 `50` 分支，`TtpInfo` 也没有确认的当前风格字段。本地选择只能是草稿/提交状态，不能显示为车辆已确认。

## 6. 保护范围

本轮不修改 VS-013、VS-014、VS-015、密码解锁、X1 `001/002/003`、普通一键距离档、氛围灯未知协议字段、定时关机、自定义音效等已保护实现。未发现证据充分且风险可控的“完全未实现业务”可直接迁移；后续修复只处理上节的本地状态语义问题。
