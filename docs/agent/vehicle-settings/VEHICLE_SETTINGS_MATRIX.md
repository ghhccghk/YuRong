# 车辆设置完整矩阵

本文件按 `confId` 列出原始 Activity/Registry 入口、KMP 项目和条目 ID。`条目 ID` 是 KMP 为同一 confId 拆分出的稳定 ID；未拆分时使用 `cloud:<confId>`。`Smali 来源` 只记录本轮实际检索到的入口或命令来源，未找到完整调用链的标为 `UNKNOWN`。

## A. 入口与实现状态

| confId | 设置名称 | 条目 ID | Smali 来源 | KMP 来源 | 当前状态 | 验证状态 |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | 一键开机/开机距离 | cloud:1:enable, cloud:1:distance | `OneKeyOpenActivity/OneKeyDistanceActivity.getSettingConfigId()`；`ModeGearView` | 0x0D/0x0E、0x37；TTP `bleKeyStatus/bleKeyDistanceLevel` | PARTIAL | 测试源码已编译，未执行 |
| 2 | 边撑感应 | cloud:2 | `TempleInductionActivity.getSettingConfigId()`；`CarSettingsPowerRegistry` | `VehicleSettingCommands.forConfigId("2")`；TTP `sideBraceStatus` | COMPLETE | 静态 |
| 3 | 携出助力 | cloud:3 | `SeatCushionSensorActivity.getSettingConfigId()` | `VehicleSettingCommands.forConfigId("3")` | PARTIAL | 静态 |
| 4 | 档位设置/最高车速 | cloud:4 | `TopSpeedActivity.getSettingConfigId()` | `VehicleSettingCommands` 0x36；`TtpInfo.maximumSpeedCurrent` | PARTIAL | 静态 |
| 5 | 车辆音效主题 | cloud:5 | `OldVehicleSoundEffectsActivity` 相关 Registry | `VehicleSettingCommands` 0x2E | PARTIAL | 静态 |
| 7 | 自动落锁 | cloud:7, cloud:7:time | `AutoShutDownActivity.getSettingConfigId()` | 0x53/0x54/0x55；TTP 无完整时间回读 | PARTIAL | 静态 |
| 8 | 一键报失 | cloud:8 | `VehicleControl`/诊断入口；KMP 备注短信验证 | `CarSettingsGateway.kindOf("8")` | PARTIAL | 静态 |
| 9 | 信息提醒/报警开关 | cloud:9 | `LowPowerActivity.getSettingConfigId()`；`VehicleSettingViewModel.P4` `alarmOn` | `TOGGLE_FIELDS["9"]`；TTP 无字段 | PARTIAL | 静态 |
| 10 | 电子围栏 | cloud:10, cloud:10:distance | Registry/云端动态项 | `distanceOn` + `distance`；IOT | PARTIAL | 静态 |
| 11 | 自动开关机 | cloud:11, cloud:11:distance | `AutoOnOffActivity.getSettingConfigId()` | 0x5C/0x5D/0x5E | PARTIAL | 静态 |
| 13 | 报警音 | cloud:13 | `VehicleAlarmActivity.getSettingConfigId()` | `alarmSoundEnable`；0x62/0x63 | PARTIAL | 静态 |
| 14 | 车辆报警灵敏度 | cloud:14 | `VehicleAlarmSensitivityActivity.getSettingConfigId()` | 0x5F；TTP `alarmWarningMode` | PARTIAL | 静态 |
| 15 | 离车定时关机 | cloud:15, cloud:15:time | `ScheduledShutDownActivity.getSettingConfigId()` | 0x7D；TTP `vehiclePowerDownStatus/TimeStatus` | PARTIAL | 静态 |
| 16 | 倾倒报警 | cloud:16 | `DumpNoticeActivity.getSettingConfigId()` | `dumpAlarm` | PARTIAL | 静态 |
| 17 | 能量回收开关 | cloud:17 | `EnergyRecoveryActivity` | 0x13/0x14；TTP `brakeModePowerRecoverFunctionStatus` | COMPLETE | 静态 |
| 18 | 能量回收档位 | cloud:18 | `EnergyRecoveryActivity` | 0x3C；TTP `brakeModePowerRecoverGearStatus` | PARTIAL | 静态 |
| 19 | 自动大灯 | cloud:19 | `AutomaticLightActivity.getSettingConfigId()` | 0x25/0x26；TTP `automaticHeadlightFunctionStatus` | COMPLETE | 静态 |
| 20 | 车辆音量 | cloud:20 | `VehicleVolumeActivity.getSettingConfigId()` | 0x46；TTP `alarmWarningVolumeLevel` | PARTIAL | 静态 |
| 21 | 背光/延时背光入口 | cloud:21 | `BackLightActivity.getSettingConfigId()` | KMP 无标题、kind、写入映射 | MISSING | 静态 |
| 24 | 紧急联系人 | cloud:24 | `EmergencyContactActivity.getSettingConfigId()` | `emergencyContact` + 联系人网关 | PARTIAL | 静态 |
| 25 | 密码解锁 | cloud:25 | `PassWordUnlockActivity.getSettingConfigId()` | 0x80/0x81；TTP `passwordUnlockStatus/SettingResult` | PARTIAL | 测试源码已编译，未执行 |
| 26 | 转向灯自动回位 | cloud:26 | `TurnSignalActivity.getSettingConfigId()` | `VehicleSettingCommands` 动态位域；TTP `turnSignalReturnsAutomaticallyStatus` | PARTIAL | 静态 |
| 27 | 设防模式 | cloud:27 | `ArmedModeActivity.getSettingConfigId()` | 0x8D/0x8E；TTP `offModeStatus` | PARTIAL | 静态 |
| 31 | 速度单位 | cloud:31 | `SpeedUnitActivity` / `DistanceUnitActivity` | 0x89/0x8A；TTP `metricSystemStatus` | PARTIAL | 静态 |
| 32 | 倾倒提醒 | cloud:32 | `DumpWarningActivity.getSettingConfigId()` | 0xA0/0xA1；TTP `tiltWarningStatus` | COMPLETE | 静态 |
| 33 | 模拟声浪 | cloud:33 | `SimulatedSoundActivity.getSettingConfigId()` | 0x95/0x96；TTP `simulatedSoundWaveStatus` | COMPLETE | 静态 |
| 36 | 刹车断电 | cloud:36 | `BrakePowerOffActivity.getSettingConfigId()` | 动态位域；TTP `brakePowerDownFunctionStatus` | PARTIAL | 静态 |
| 37 | TCS 车身稳定 | cloud:37 | `BodyStabilityActivity.getSettingConfigId()` | 0x9D；TTP `tcsWorkMode` | PARTIAL | 静态 |
| 38 | ABS 防抱死 | cloud:38 | `AntiLockActivity.getSettingConfigId()` | 0x99/0x9A；TTP `absFunctionStatus` | COMPLETE | 静态 |
| 39 | 边撑自动关机 | cloud:39, cloud:39:time | `BraceAutoShutDownActivity.getSettingConfigId()` | 0x53/0x54/0x55；TTP `sideBraceStatus` | PARTIAL | 静态 |
| 40 | 彩蛋音效 | cloud:40 | `EggSoundActivity.getSettingConfigId()` | `colorEggSoundEffect` | PARTIAL | 静态 |
| 41 | 自定义音效入口 | cloud:41 | `CustomSoundEffectListActivity` / `CarSettingsNavigation` | `CustomSoundGateway` 二级面板 | PARTIAL | 静态 |
| 42 | 显示剩余里程 | cloud:42 | `ShowInstrumentEnduranceActivity.getSettingConfigId()` | `DISPLAY_REMAINING_MILEAGE_*`；TTP `remainingMileageDebugStatus` | COMPLETE | 静态 |
| 43 | 倒车模式 | cloud:43 | `ReversingFunctionActivity.getSettingConfigId()` | `VehicleSettingCommands` 0x?；TTP `reversingState` | BROKEN | 静态 |
| 44 | 婴儿车/辅助推行模式 | cloud:44 | `HelpPromoteActivity.getSettingConfigId()` | `VehicleSettingCommands` 0x?；TTP `helpCarState` | BROKEN | 静态 |
| 45 | 定速巡航 | cloud:45 | `ConstantSpeedModeActivity.getSettingConfigId()` | 0xAA/0xAB；TTP `cruiseControl` | COMPLETE | 静态 |
| 46 | 陡坡缓降 | cloud:46 | Registry/`RampStops`/动态项 | 动态位域；TTP `steepSlopeState` | PARTIAL | 静态 |
| 47 | 充电功率 | cloud:47, cloud:47:enable | `ChargingPowerActivity.getSettingConfigId()` | `chargeWattSwitch` + 0xC9/0xCA；TTP charging power | PARTIAL | 静态 |
| 48 | 充电上限 | cloud:48 | `ChargeCapActivity.getSettingConfigId()` | 0xCB；TTP `batteryChargingLimitValue` | PARTIAL | 静态 |
| 49 | 坡道驻停 | cloud:49 | `RampStopsActivity.getSettingConfigId()` | 动态位域；TTP `rampSlopeState` | PARTIAL | 静态 |
| 50 | 仪表风格切换 | cloud:50 | `GaugeStyleHandOffActivity` | 0x3F | PARTIAL | 静态 |
| 51 | 仪表时间同步 | cloud:51 | `BleMeterTimeSyncActivity.getSettingConfigId()` | 0xC1；TTP `syncTimeResultStatus` | PARTIAL | 静态 |
| 52 | 感应解锁/手动距离 | cloud:52, cloud:52:distance | Registry/动态项 | `bleUnlockOn`、`bleDistance` | PARTIAL | 静态 |
| 53 | 边撑开机 | cloud:53 | `BracketPoweredActivity.getSettingConfigId()` | 0xC7；TTP `templeSettingStatus` | PARTIAL | 静态 |
| 54 | 电压平台 | cloud:54 | `VoltagePlatformActivity.getSettingConfigId()` | 0xC6；TTP `voltageSettingStatus` | PARTIAL | 静态 |
| 55 | FREE 自由模式 | cloud:55 | `VehicleCustomizeActivity.getSettingConfigId()` | 0xC8 多参数；TTP customize* | PARTIAL | 静态 |
| 58 | 最高车速 | cloud:58 | `MaxSpeedSettingActivity.getSettingConfigId()` | 0x36 | PARTIAL | 静态 |
| 59 | 报警锁/定时关机 | cloud:59, cloud:59:time | `ScheduledShutDown2Activity.getSettingConfigId()` | 0x7B/0x7C/0x7D；TTP powerDown | PARTIAL | 静态 |
| 60 | 电池设置 | cloud:60, cloud:60:enable, cloud:60:level | `CapacitySettingActivity` | 0xD4/0xCD；TTP capacity* | PARTIAL | 静态 |
| 61 | 安全限速 | cloud:61 | `MaxSpeedLimitActivity.getSettingConfigId()` | 0x36；0 不限速、非零最低 15 | PARTIAL | 静态 |
| 62 | 座椅加热 | cloud:62, cloud:62:level | `HeatedSeatsActivity` | `seatHeating`、`seatHeatingLevel`；TTP seatHeat* | PARTIAL | 静态 |
| 63 | 超级狂暴/T Power | cloud:63 | `TPowerActivity.getSettingConfigId()` | 0xCF；TTP `tPowerStatus` | PARTIAL | 静态 |
| 64 | 辅助推行 | cloud:64, cloud:64:adaptive, cloud:64:level | `HelpPromotePlusActivity.getSettingConfigId()` | 0xD0/0xD1；TTP helpCar/customDrive | PARTIAL | 静态 |
| 66 | 自动校准/蓝牙钥匙 | cloud:66 | `CarSettingsPowerRegistry`/钥匙流程 | BLUETOOTH_KEY_IDS，只读 | PARTIAL | 静态 |
| 68 | 急刹爆闪 | cloud:68 | `SuddenBrakingActivity.getSettingConfigId()` | 0x? 动态位域；TTP `quickBrakeLightStatus` | PARTIAL | 静态 |
| 69 | 雷达 | cloud:69, cloud:69:fcw, cloud:69:rcw, cloud:69:bsd | `RadarActivity` | 0x13A/0x12D/0x12E；TTP radar* | PARTIAL | 静态 |
| 71 | 仪表设置 | cloud:71 | `GaugeSettingsActivity.getSettingConfigId()` | 连接页入口，无设置值写入 | COMPLETE | 静态 |
| 72 | 长续航模式 | cloud:72 | `EnduranceModeActivity.getSettingConfigId()` | 0xDD；TTP `longEnduranceStatus` | PARTIAL | 静态 |
| 73 | 氛围灯开关/模式颜色 | cloud:73, cloud:73:config | `FeelLightSettingActivity.getSettingConfigId()`；`FeelLightSettingActivity$c` | 0xC3；0xF7；TTP `ambientStatus` | PARTIAL | 0xF7 测试源码已编译，配置回读 UNKNOWN |
| 75 | 定位器防盗 | cloud:75 | `IotTamperProofActivity.getSettingConfigId()` | `tamperProofIotEnable`、`IOT_REMOVE_ALARM` | PARTIAL | 静态 |
| 76 | 定位开关 | cloud:76 | `LocationSwitchActivity` | `positioningFunc`；IOT | PARTIAL | 静态 |
| 77 | 休眠模式 | cloud:77 | `HibernateModeActivity.getSettingConfigId()` | 0x133；TTP `sleepModeSwitch` | PARTIAL | 静态 |
| 79 | 座垫感应上电 | cloud:79 | `SeatPowerUpActivity.getSettingConfigId()` | 0xFB1；TTP `seatPowerStatus` | PARTIAL | 静态 |
| 80 | OTD 开关 | cloud:80 | `OTDActivity.getSettingConfigId()` | 0x132；TTP `otdSwitch` | PARTIAL | 静态 |
| 81 | 大灯延时 | cloud:81, cloud:81:time | `DelayLightActivity.getSettingConfigId()` | 0x83/0x84/0x85；TTP withGoHome* | PARTIAL | 静态 |
| 82 | 电话告警 | cloud:82 | Registry/动态项 | `lowBatTelWarning` | PARTIAL | 静态 |
| 84 | 安全辅助小蓝灯 | cloud:84 | `AssistDriveLightActivity.getSettingConfigId()` | `BLE_CONFIG_ASSIST_DRIVE_LIGHT`；TTP `blueLightSwitch` | PARTIAL | 静态 |
| 85 | SOS 联系人 | cloud:85 | `SosContactActivity.getSettingConfigId()` | 复用紧急联系人编辑器 | PARTIAL | 静态 |
| 86 | 仪表亮度调节 | cloud:86, cloud:86:value | `BrightnessAdjustmentActivity.getSettingConfigId()` | 0x134/0x135；TTP brightness* | PARTIAL | 静态 |
| 88 | 自定义音效入口 | cloud:88 | `CustomSoundEffectListActivity` / `CarSettingsNavigation` | `CustomSoundGateway` 二级面板 | PARTIAL | 静态 |
| 89 | 辅助推行 2 | cloud:89, cloud:89:adaptive, cloud:89:level | `HelpPromotePlus2Activity.getSettingConfigId()` | 0xD0/0xD1；TTP helpCar/customDrive | PARTIAL | 静态 |
| 90 | 赛道模式 | cloud:90 | `TrackModeActivity.getSettingConfigId()` | 0x13F；TTP `trackModeStatus` | PARTIAL | 静态 |
| 91 | SOS 紧急呼叫 | cloud:91 | `SosContactActivity` 附属配置 | `sosEmergencyCall` | PARTIAL | 静态 |
| 92 | 自定义骑行 | cloud:92 | `VehicleCustomizeActivity2` | 0xC8 多参数；TTP customize* | PARTIAL | 静态 |
| 93 | 静音模式 | cloud:93 | `QuietModeActivity.getSettingConfigId()` | 0x141 双字段；TTP 超速提示状态需核验 | PARTIAL | 静态 |
| 94 | 档位记忆 | cloud:94 | `GearMemoryActivity.getSettingConfigId()` | 0x? 动态位域；TTP `gearMemorySettingFeedback` | PARTIAL | 静态 |
| 95 | 微信互联 | cloud:95 | `CarSettingsFooterRegistry` | KMP 当前隐藏；`wxhlBind` | PARTIAL | 静态 |
| 1005 | 自定义按键 | cloud:1005, cloud:1005:short, cloud:1005:long | `CustomizeKeyActivity` / `CarSettingsRideControlRegistry` | 0x131/0x130；TTP smartKeys/customFunction | PARTIAL | 静态 |

## B. 值、权限、写入和回读

统一约定：云端布尔值按 `1/true=开`、`0/false=关`；KMP `CloudCarSettings.intValue()` 会把可解析数字转为整数。没有明确 Smali/抓包范围的值标为 `UNKNOWN`，不得据此写入。

| confId | 云端字段 | 参数类型/范围 | 显示值 → 提交值 | 权限 | 写入方式 | 回读字段 | 备注 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | 云端 confId 1 | Toggle；Slider 0..7 档 | 0x0D/0x0E；0x37 参数 0..7 | `isAuth` | BLE | `bleKeyStatus/bleKeyDistanceLevel` | 不属于数字车钥匙 |
| 2 | 无已确认值 | Bool | Toggle → 0x0F/0x10 | isAuth | BLE | `sideBraceStatus` | 0/1 位域 |
| 3 | 无已确认值 | Bool | Toggle → 0x11/0x12 | isAuth | BLE | UNKNOWN | 云端无回读字段 |
| 4/58 | `bikeSpeedMax`、`bikeModelTopSpeedMax` | 15..120 | Slider int → 0x36 | isAuth | BLE | `maximumSpeedCurrent` | 范围仍需抓包确认 |
| 5 | 无已确认值 | Choice 0..1 | index → 0x2E | isAuth | BLE | UNKNOWN | encoder 允许 0..2，需复核 |
| 7/39 | `bleLeavePowerOffEnable`、`shutDownTime` | Bool；Choice 0..3 | Toggle 0x53/0x54；time index → 0x55 | isAuth | BLE | 部分 TTP | 39 依赖边撑感应 |
| 8 | 无 | 入口/短信验证 | 不直接写 | isAuth | 诊断流程 | UNKNOWN | KMP 只读 |
| 9 | `alarmOn` | Bool | 0/1 → cloud field | isAuth | IoT | UNKNOWN | P4 有字段 |
| 10 | `distanceOn`, `distance` | Bool；50..1000 m | distance int → cloud field | isAuth | IoT | UNKNOWN | 拆分项 cloud:10:distance |
| 11 | `bleApproachPowerOnDistance` | Bool；Choice 0..2 | index → 0x5E；Toggle 0x5C/0x5D | isAuth | BLE | UNKNOWN | 距离映射需核验 |
| 13 | `alarmSoundEnable` | Bool | 0/1 → cloud field / 0x62/0x63 | isAuth | BLE/IOT | UNKNOWN | P4 有字段 |
| 14 | `alarmPower` | 0..7 | Slider int → 0x5F | isAuth | BLE | `alarmWarningMode` | 范围来自 KMP，需抓包 |
| 15/59 | `vehiclePowerDownStatus`, `shutDownTime` | Bool；0..20 | 0x7D；Choice index | isAuth | BLE | `vehiclePowerDownStatus/TimeStatus` | 0..20 的单位需核验 |
| 16 | `dumpAlarm` | Bool | 0/1 → cloud field | isAuth | IoT | UNKNOWN | P4 有字段 |
| 17 | `resistanceOn` | Bool | 0x13/0x14 | isAuth | BLE | `brakeModePowerRecoverFunctionStatus` | 0x13/14 位域 |
| 18 | 无云端值 | 1..3 | Slider int → 0x3C | isAuth | BLE | `brakeModePowerRecoverGearStatus` | 初值 KMP 固定 1，需确认默认 |
| 19 | 无云端值 | Bool | 0x25/0x26 | isAuth | BLE | `automaticHeadlightFunctionStatus` | |
| 20 | 无云端值 | 0..7 | Slider int → 0x46 | isAuth | BLE | `alarmWarningVolumeLevel` | |
| 21 | 无 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | 原 BackLightActivity 缺失 |
| 24 | `emergencyContact` | Bool | 0/1 → cloud field | isAuth | IoT | 联系人查询 | 联系人编辑器为独立网关 |
| 25 | `passwordUnlockEnable` + TTP | Bool | 0x80/0x81 | `isAuth` | BLE/IoT | `passwordUnlockStatus/SettingResult` | 密码设置流程仍 PARTIAL |
| 26 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `turnSignalReturnsAutomaticallyStatus` | |
| 27 | 无云端值 | Choice 0..1 | 0x8D/0x8E | isAuth | BLE | `offModeStatus` | |
| 31 | 无云端值 | Choice 0..1 | 0x89/0x8A | isAuth | BLE | `metricSystemStatus` | |
| 32 | 无云端值 | Bool | 0xA0/0xA1 | isAuth | BLE | `tiltWarningStatus` | |
| 33 | 无云端值 | Bool | 0x95/0x96 | isAuth | BLE | `simulatedSoundWaveStatus` | |
| 36 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `brakePowerDownFunctionStatus` | |
| 37 | 无云端值 | TCS 0..7/Bool | on=0、off=1..7 → 0x9D | isAuth | BLE | `tcsWorkMode` | 0 表示开启，需保持 |
| 38 | 无云端值 | Bool | 0x99/0x9A | isAuth | BLE | `absFunctionStatus` | |
| 40 | `colorEggSoundEffect` | Bool | 0/1 → cloud field | isAuth | IoT | UNKNOWN | |
| 41/88 | `userDefinedSoundEffect` | 入口 | 不直接写入设置 | isAuth | 音效网关 | 音效资源状态 | 两个入口共用面板 |
| 42 | 无云端值 | Bool | 0x? / 0x? | isAuth | BLE | `remainingMileageDebugStatus` | 命令 ID 需补证 |
| 43 | 无云端值 | Bool | KMP 当前 Info，原 Toggle | isAuth | BLE | `reversingState` | 映射分支被提前 Info 覆盖 |
| 44 | 无云端值 | Bool | KMP 当前 Info，原 Toggle | isAuth | BLE | `helpCarState` | 映射分支被提前 Info 覆盖 |
| 45 | 无云端值 | Bool | 0xAA/0xAB | isAuth | BLE | `cruiseControl` | |
| 46 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `steepSlopeState` | |
| 47 | `CHARGE_WATT_SWITCH`, `CHARGE_WATT_VAL` | Bool；200..1000 W，50 W 步进 | Integer 0/1、watt → `id/value` | isAuth | BLE / `low-freq/common-cmd/param-setting` | charging power status/value | 云端请求已确认；BLE Golden Frame 属 VS-012 |
| 48 | `CHARGE_UL_VAL` | 70..100%，5% 步进 | Integer percent → `id/value` | isAuth | BLE / `low-freq/common-cmd/param-setting` | `batteryChargingLimitValue` | 云端请求已确认 |
| 49 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `rampSlopeState` | |
| 50 | 无云端值 | Choice 0..2 | index → 0x3F | isAuth | BLE | UNKNOWN | |
| 51 | 无云端值 | Bool | 0xC1 参数 0/1 | isAuth | BLE | `syncTimeResultStatus` | 参数方向需核验 |
| 52 | `bleUnlockOn`, `bleDistance` | Bool；1..12 m | cloud field / distance int | isAuth | BLE/IOT | UNKNOWN | |
| 53 | `bleApproachPowerOnEnable` | Bool | 0xC7 参数 0/1 | isAuth | BLE | `templeSettingStatus` | |
| 54 | 无云端值 | Choice 48/60/70V | index+1 → 0xC6 | isAuth | BLE | `voltageSettingStatus-1` | |
| 55/92 | customize* | 4 个字段，范围 UNKNOWN | 0xC8 打包 | isAuth | BLE | customizeThrottle/Energy/Tcs | 必须整组写入 |
| 59 | `vehiclePowerDownStatus`, `shutDownTime` | Bool；0..20 | 0x7B/0x7C/0x7D | isAuth | BLE | powerDown | |
| 60 | `BATT_BD_SETTING`, `BATT_V_A_CAP_MODEL` | Bool；13/15/22/25/32/38 Ah | Integer + single/capacity `valueJson` | isAuth | BLE / `low-freq/common-cmd/param-setting` | capacitySwitch/capacitySetting | 拆分项；云端请求已确认 |
| 61 | `HIGHEST_SPEED_LIMIT`, `bikeSpeedMax` | 0 或 15..max | Integer + single `valueJson`；0=不限速 | isAuth | BLE / `low-freq/common-cmd/param-setting` | `maximumSpeedCurrent` | 约束固定 |
| 62 | `SEAT_HEATING`, `SEAT_HEATING_LEVEL` | Bool；1..3 | Integer + single `valueJson` | isAuth | BLE / `low-freq/common-cmd/param-setting` | seatHeatSwitch/Level | 拆分项；云端请求已确认 |
| 63 | 无云端值 | Bool | 0xCF 参数 0/1 | isAuth | BLE | `tPowerStatus` | |
| 64/89 | customDrive* | Bool；1..3 | 0xD0/0xD1 | isAuth | BLE | helpCar/customDrive | adaptive 与 level 拆分 |
| 66 | 无 | 蓝牙钥匙 | 只读 | BLUETOOTH_KEY | 原钥匙流程 | UNKNOWN | |
| 68 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `quickBrakeLightStatus` | |
| 69 | radar* | Bool | 0x13A/0x12D/0x12E | isAuth | BLE | frontCollision/rcw/bsd | 三个稳定子 ID |
| 71 | 无 | 入口 | 不直接写 | isAuth | 连接页 | UNKNOWN | |
| 72 | 无云端值 | Bool | 0xDD 参数 0/1 | isAuth | BLE | `longEnduranceStatus` | |
| 73 | 无云端配置值 | Toggle；Atmosphere | 0xC3；0xF7 多参数 | `isAuth` | BLE | `ambientStatus`；配置回读 UNKNOWN | `cloud:73:config` |
| 75 | `tamperProofIotEnable` | Bool | IOT_REMOVE_ALARM | isAuth | IOT/BLE | UNKNOWN | |
| 76 | `POSITIONING_FUNC` | Bool | Integer 0/1 + single `valueJson` | isAuth | `low-freq/common-cmd/param-setting` | UNKNOWN | 云端请求已确认；回读 UNKNOWN |
| 77 | `sleepMode` | Bool | 0x133 参数 0/1 | isAuth | BLE | `sleepModeSwitch` | |
| 79 | 无云端值 | Bool | 0xFB1 参数 0/1 | isAuth | BLE | `seatPowerStatus` | |
| 80 | 无云端值 | Bool | 0x132 反值参数 | isAuth | BLE | `otdSwitch` | 0=开、1=关 |
| 81 | 无云端值 | Bool；Choice 5/10/15/30 | 0x83/0x84/0x85 | isAuth | BLE | `withGoHomeStatus/TimeStatus` | |
| 82 | `lowBatTelWarning` | Bool | cloud field | isAuth | IOT | UNKNOWN | |
| 84 | 无云端值 | Bool | 0x? 反值参数 | isAuth | BLE | `blueLightSwitch` | |
| 85 | 无 | 入口 | 联系人网关 | isAuth | IoT | 联系人查询 | 与 24 共用 |
| 86 | `brightnessAdjustValue` | 0..100 | 0x134；开关 0x135 | isAuth | BLE | brightnessAdjustValue/Switch | 拆分项 |
| 89 | customDrive* | 同 64 | 0xD0/0xD1 | isAuth | BLE | helpCar/customDrive | |
| 90 | 无云端值 | Bool | 0x13F 参数 0/1 | isAuth | BLE | `trackModeStatus` | |
| 91 | `sosEmergencyCall` | Bool | cloud field | isAuth | IOT | UNKNOWN | |
| 92 | customize* | 同 55 | 0xC8 | isAuth | BLE | customize* | |
| 93 | 无云端值 | Bool | 0x141 双字段 | isAuth | BLE | UNKNOWN | 新旧字段方向需核验 |
| 94 | 无云端值 | Bool | 动态位域 | isAuth | BLE | `gearMemorySettingFeedback` | |
| 95 | `wxhlBind` | Bool | KMP 隐藏 | isAuth | UNKNOWN | UNKNOWN | 当前禁止展示 |
| 1005 | custom key | Choice 0..9，函数 ID 0/1/2/3/4/5/6/7/8/14 | 0x131/0x130 | isAuth | BLE | smartKeys/customFunction | 短按/长按拆分 |

## C. 特殊条目和拆分 ID

| 条目 ID | confId | 说明 | 写入 | 回读 | 状态 |
| --- | --- | --- | --- | --- | --- |
| cloud:7:time / cloud:39:time | 7/39 | 自动锁定时间 | 0x55 | UNKNOWN | PARTIAL |
| cloud:10:distance | 10 | 电子围栏半径 | cloud `distance` | UNKNOWN | PARTIAL |
| cloud:47:enable | 47 | 充电功率开关 | 0xC9 | charging power status | PARTIAL |
| cloud:52:distance | 52 | 感应解锁距离 | cloud `bleDistance` | UNKNOWN | PARTIAL |
| cloud:60:enable / cloud:60:level | 60 | 电池容量开关/档位 | 0xD4 / 0xCD | capacitySwitch/setting | PARTIAL |
| cloud:62:level | 62 | 座椅加热档位 | 0xD3 | seatHeatLevel | PARTIAL |
| cloud:15:time / cloud:59:time | 15/59 | 定时关机时间 | 0x7D | powerDownTime | PARTIAL |
| cloud:81:time | 81 | 大灯延时时间 | 0x85 | withGoHomeTime | PARTIAL |
| cloud:86:value | 86 | 仪表亮度值 | 0x134 | brightnessAdjustValue | PARTIAL |
| cloud:64:adaptive / cloud:64:level | 64 | 自适应推行/速度 | 0xD0 / 0xD1 | customDriveSwitch/Level | PARTIAL |
| cloud:89:adaptive / cloud:89:level | 89 | 自适应推行/速度 | 0xD0 / 0xD1 | customDriveSwitch/Level | PARTIAL |
| cloud:69:fcw / :rcw / :bsd | 69 | 雷达分项 | 0x13A / 0x12D / 0x12E | 对应 radar 字段 | PARTIAL |
| cloud:1005:short / :long | 1005 | 自定义按键 | 0x131 / 0x130 | smartKeys/customFunction | PARTIAL |
| local:1005 | 1005 | 云端未下发时的固定入口 | 不写 | UNKNOWN | PARTIAL |
| special:meter_wallpaper_skin | — | 壁纸皮肤入口 | 本地/子页面 | UNKNOWN | UNKNOWN |
| special:meter_gauge_sync | — | 车辆信息同步入口 | 本地/子页面 | UNKNOWN | UNKNOWN |
| special:footer_intelligence | — | 用户智能识别入口 | 本地/子页面 | UNKNOWN | UNKNOWN |
| special:footer_lab | — | 实验室入口 | 本地/子页面 | UNKNOWN | UNKNOWN |
| footer_device_detail | — | 设备详情入口 | 本地/子页面 | UNKNOWN | UNKNOWN |