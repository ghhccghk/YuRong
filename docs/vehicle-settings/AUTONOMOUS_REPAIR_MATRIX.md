# 车辆设置自主修复功能地图

日期：2026-11-12。起始 HEAD：`329effd69e72a4cdf60d3d910dccde2ad1bce247`；分支 master。

## 证据与状态约定

Smali 根：`F:/android app code/YadeaHook/temp_smali`。KMP 根：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/`。下表由当前源文件逐方法索引生成，索引存在不代表链路完成；待逐批复核的项统一 PARTIAL。命令、字段仍需检查具体分支、调用者和合法值。COMPLETE 只指静态及 Fake 证明的必要链路，不包含运行/实车验证。

扫描了全部 getSettingConfigId 定义、设置基类继承者、七类动态 Registry、Navigation，以及当前 Mapper/Controller/命令映射。81 个数字 confId（含 X1 001/002/003），另有5个无 confId 的目录入口。动态父子项单列，不把空 ID 基类计作设置。指定的 full-read-write-audit-2026-11-11.md 未找到；其他历史文档仅作检索入口。

KMP 读取代码位置为 CarSettings.kt 的 applyVehicleStatus；写入索引为 VehicleSettingCommands.kt 的 forConfigId。forItem 的分支、CloudCarSettingUpdateBuilder、独立 Gateway 另在批次证据中核对。所有项目共同受 bikeConfigVos/isAuth/izLab/isUserLabFun 门控；目录中的不适用不等于字段未实现。

## 全集索引

| confId / 项目 | 原 App 页面及 getSettingConfigId 位置 | TTP 字段（当前 KMP 消费） | BLE 命令索引 | 云端字段/实际链路与缺口 | 优先级/状态/批次 |
|---|---|---|---|---|---|
| 001 X1 距离单位 | com/yadea/smartmoto/vehicle/view/X1DistanceUnitActivity.smali:187 | metricSystemStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 002 X1 感应自动开关机 | com/yadea/smartmoto/vehicle/view/BleNearlyAutoUnlockActivity.smali:182 | bleAutoTurnOnOffGearStatus, bleAutoTurnOnOffStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 003 X1 制动能量回收 | com/yadea/smartmoto/vehicle/view/X1EnergyRecoveryActivity.smali:182 | brakeModePowerRecoverFunctionStatus, brakeModePowerRecoverGearStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 1 一键开机 | com/yadea/smartmoto/vehicle/onekey/OneKeyDistanceActivity.smali:779<br>com/yadea/smartmoto/vehicle/onekey/OneKeyOpenActivity.smali:4224 | bleKeyDistanceLevel, bleKeyStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 2 边撑感应 | com/yadea/smartmoto/vehicle/view/TempleInductionActivity.smali:209 | sideBraceStatus | SLIDE_BRACE_OFF, SLIDE_BRACE_ON | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 3 携出助力 | com/yadea/smartmoto/vehicle/view/SeatCushionSensorActivity.smali:144 | 无 Controller 分支；独立流程另查 | CARRY_OUT_CLOSE, CARRY_OUT_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 4 档位设置 | com/yadea/smartmoto/vehicle/view/TopSpeedActivity.smali:85 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 5 车辆音效 | com/yadea/smartmoto/vehicle/activity/OldVehicleSoundEffectsActivity.smali:506 | alarmRingThemeCurrent | ALARM_THEME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 7 自动落锁 | com/yadea/smartmoto/vehicle/view/AutoShutDownActivity.smali:505 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 8 一键报失 | com/yadea/smartmoto/vehicle/lostmode/LostReportModeActivity.smali:549 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 9 信息提醒 | com/yadea/smartmoto/vehicle/view/LowPowerActivity.smali:997 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 10 电子围栏 | com/yadea/smartmoto/vehicle/activity/UserSelectRangeActivity.smali:846 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | A04：distanceOn/distance/centerCircleLon/Lat完整云端快照；半径200..2000m整数，缺伴随字段拒绝提交；只有结果确认，无车辆回读 | P0 / CONDITIONAL / A04 |
| 11 自动开关机 | com/yadea/smartmoto/vehicle/view/AutoOnOffActivity.smali:187 | bleAutoTurnOnOffGearStatus, bleAutoTurnOnOffStatus | AUTO_TURN_ON_OFF_CLOSE, AUTO_TURN_ON_OFF_OPEN, AUTO_TURN_ON_OFF | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 13 报警音 | com/yadea/smartmoto/vehicle/view/VehicleAlarmActivity.smali:144 | 无 Controller 分支；独立流程另查 | WARING_SOUND_CLOSE, WARING_SOUND_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 14 车辆报警灵敏度 | com/yadea/smartmoto/vehicle/view/VehicleAlarmSensitivityActivity.smali:87 | alarmSensitivityStatus | ALARM_SENSITIVITY_GEAR | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 15 离车定时关机 | com/yadea/smartmoto/vehicle/view/ScheduledShutDownActivity.smali:197 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE开关0x7B/7C、时间0x7D=2..20min；DTO字段无页面绑定不作配置；原始0/1保持UNKNOWN | P1 / PARTIAL / A02 |
| 16 倾倒报警 | com/yadea/smartmoto/vehicle/view/DumpNoticeActivity.smali:234 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 17 能量回收开关 | 动态 Registry / 独立流程；待逐项绑定 | slideModePowerRecoverStatus | ENERGY_RECOVER_CLOSE, ENERGY_RECOVER_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 18 能量回收档位 | 动态 Registry / 独立流程；待逐项绑定 | slideModePowerRecoverCurrent | ENERGY_RECOVER_LEVEL | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 19 自动大灯 | com/yadea/smartmoto/vehicle/view/AutomaticLightActivity.smali:144 | automaticHeadlightFunctionStatus | AUTOMATIC_HEADLIGHT_CLOSE, AUTOMATIC_HEADLIGHT_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 20 车辆音量 | com/yadea/smartmoto/vehicle/view/VehicleVolumeActivity.smali:87 | alarmWarningVolumeLevel | VOLUME | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 21 伴我回家灯 | com/yadea/smartmoto/vehicle/view/BackLightActivity.smali:178 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=15/30/45/60秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 24 紧急联系人 | com/yadea/smartmoto/vehicle/view/EmergencyContactActivity.smali:964 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 25 密码解锁 | com/yadea/smartmoto/vehicle/pwdunlock/PassWordUnlockActivity.smali:845 | passwordUnlockStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 26 转向灯自动回位 | com/yadea/smartmoto/vehicle/view/TurnSignalActivity.smali:283 | turnSignalReturnsAutomaticallyStatus | COMMAND_DIRECTION_LIGHT_RESET_OFF, COMMAND_DIRECTION_LIGHT_RESET_ON | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 27 设防模式 | com/yadea/smartmoto/vehicle/view/ArmedModeActivity.smali:231 | offModeStatus | FORTIFY_MODE_AUTO, FORTIFY_MODE_NO | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 31 速度单位 | com/yadea/smartmoto/vehicle/view/SpeedUnitActivity.smali:232<br>com/yadea/smartmoto/vehicle/view/DistanceUnitActivity.smali:156 | metricSystemStatus | IMPERIAL_UNIT_KM, IMPERIAL_UNIT_MILES | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 32 倾倒提醒 | com/yadea/smartmoto/vehicle/view/DumpWarningActivity.smali:144 | tiltWarningStatus | TILT_WARNING_CLOSE, TILT_WARNING_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 33 模拟声浪 | com/yadea/smartmoto/vehicle/view/SimulatedSoundActivity.smali:333 | simulatedSoundWaveStatus | SIMULATED_SOUND_WAVE_CLOSE, SIMULATED_SOUND_WAVE_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 36 刹车断电 | com/yadea/smartmoto/vehicle/view/BrakePowerOffActivity.smali:155 | brakePowerDownFunctionStatus | BRAKE_OUTAGE_CLOSE, BRAKE_OUTAGE_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 37 TCS 车身稳定 | com/yadea/smartmoto/vehicle/view/BodyStabilityActivity.smali:154 | tcsWorkMode | TCS_BODY_STABILITY, TCS_BODY_STABILITY | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 38 ABS 防抱死 | com/yadea/smartmoto/vehicle/view/AntiLockActivity.smali:152 | absFunctionStatus | ABS_ANTI_LOCK_CLOSE, ABS_ANTI_LOCK_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 39 边撑自动关机 | com/yadea/smartmoto/vehicle/view/BraceAutoShutDownActivity.smali:376 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 40 彩蛋音效 | com/yadea/smartmoto/vehicle/view/EggSoundActivity.smali:306 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 41 自定义音效 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 42 车辆显示剩余里程 | com/yadea/smartmoto/vehicle/view/ShowInstrumentEnduranceActivity.smali:260 | remainingMileageDebugStatus | DISPLAY_REMAINING_MILEAGE_CLOSE, DISPLAY_REMAINING_MILEAGE_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 43 倒车模式 | com/yadea/smartmoto/vehicle/view/ReversingFunctionActivity.smali:144 | reversingState | REVERSING_CLOSE, REVERSING_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 44 婴儿车模式 | com/yadea/smartmoto/vehicle/view/HelpPromoteActivity.smali:144 | helpCarState | STROLLER_CLOSE, STROLLER_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 45 定速巡航 | com/yadea/smartmoto/vehicle/view/ConstantSpeedModeActivity.smali:144 | cruiseControl | CONST_SPEED_CLOSE, CONST_SPEED_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 46 陡坡缓降 | com/yadea/smartmoto/vehicle/activity/SlopeDescentActivity.smali:154 | steepSlopeState | BLE_SLOP_DESCENT_CLOSE, BLE_SLOP_DESCENT_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 47 充电功率 | com/yadea/smartmoto/vehicle/view/ChargingPowerActivity.smali:628 | batteryChargingPowerStatus, batteryChargingPowerValue | COMMAND_CONST_CHARGING_POWER_STATE, COMMAND_CONST_CHARGING_POWER_STATE, COMMAND_CONST_CHARGING_POWER_VALUE | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 48 充电上限 | com/yadea/smartmoto/vehicle/view/ChargeCapActivity.smali:438 | 无 Controller 分支；独立流程另查 | COMMAND_CONST_CHARGING_LIMIT_VALUE | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 49 坡道驻停 | com/yadea/smartmoto/vehicle/view/RampStopsActivity.smali:144 | rampSlopeState | BLE_RAMP_SLOP_CLOSE, BLE_RAMP_SLOP_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 50 仪表风格切换 | com/yadea/smartmoto/vehicle/activity/GaugeStyleHandOffActivity.smali:522 | panelThemeCurrent | COMMAND_BIKE_PANEL_THEME_TYPE | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 51 仪表时间同步 | com/yadea/smartmoto/vehicle/view/BleMeterTimeSyncActivity.smali:245 | syncTimeResultStatus | COMMAND_CONST_TIME_SYNC_CLOSE, COMMAND_CONST_TIME_SYNC_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 52 手动距离自定义 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 53 边撑开机 | com/yadea/smartmoto/vehicle/view/BracketPoweredActivity.smali:146 | templeSettingStatus | COMMAND_CONST_TEMPLE_SETTING, COMMAND_CONST_TEMPLE_SETTING | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 54 电压设置 | com/yadea/smartmoto/vehicle/view/VoltagePlatformActivity.smali:100 | voltageSettingStatus | COMMAND_CONST_VOLTAGE_SETTING | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 55 FREE 自由模式 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity.smali:1452 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 58 档位设置 | com/yadea/smartmoto/vehicle/view/MaxSpeedSettingActivity.smali:220 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 59 报警锁 | com/yadea/smartmoto/vehicle/view/ScheduledShutDown2Activity.smali:210 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE同开关、0x7D原始0=30秒、1..20=分钟；UNKNOWN无默认；别名并发保护 | P1 / COMPLETE / A02 |
| 60 电池设置 | com/yadea/smartmoto/vehicle/activity/CapacitySettingActivity.smali:297 | capacitySettingStatus, capacitySwitchStatus | COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SETTING | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 61 安全限速 | com/yadea/smartmoto/vehicle/view/MaxSpeedLimitActivity.smali:610 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 62 座椅加热 | com/yadea/smartmoto/vehicle/activity/HeatedSeatsActivity.smali:2596 | seatHeatLevelStatus, seatHeatSwitchStatus | SEAT_HEAT_SWITCH, SEAT_HEAT_SWITCH, SEAT_HEAT_LEVEL | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 63 超级狂暴模式 | com/yadea/smartmoto/vehicle/view/TPowerActivity.smali:114 | tPowerStatus | COMMAND_CONST_T_POWER_SETTING, COMMAND_CONST_T_POWER_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 64 辅助推行 | com/yadea/smartmoto/vehicle/view/HelpPromotePlusActivity.smali:188 | customDriveLevelStatus, customDriveSwitchStatus, helpCarState | STROLLER_CLOSE, STROLLER_OPEN, COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 68 急刹爆闪 | com/yadea/smartmoto/vehicle/view/SuddenBrakingActivity.smali:147 | quickBrakeLightStatus | COMMAND_QUICK_BRAKE_LIGHT_SWITCH, COMMAND_QUICK_BRAKE_LIGHT_SWITCH | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 69 雷达 | 动态 Registry / 独立流程；待逐项绑定 | bsdRadar, frontCollisionWarning, rcwRadarSwitch | COMMAND_FRONT_COLLISION_CLOSE, COMMAND_FRONT_COLLISION_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 82 电话告警 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 71 仪表设置 | com/yadea/smartmoto/vehicle/view/GaugeSettingsActivity.smali:621 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 72 长续航模式 | com/yadea/smartmoto/vehicle/view/EnduranceModeActivity.smali:303 | longEnduranceStatus | LONG_ENDURANCE_SETTING, LONG_ENDURANCE_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 73 氛围灯 | com/yadea/smartmoto/vehicle/view/FeelLightSettingActivity.smali:2404 | ambientStatus、show/color/operation、RGB1/2 | C3开关；F7完整模式组合 | A05：完整TTP回填/独立开关/不可变复合命令/新鲜回读比较；未知禁止默认补齐，完整配置才可编辑 | P0 / CONDITIONAL / A05 |
| 75 定位器防盗 | com/yadea/smartmoto/vehicle/view/IotTamperProofActivity.smali:194 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 76 定位开关 | com/yadea/smartmoto/vehicle/locationswitch/LocationSwitchActivity.smali:319 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 77 休眠模式 | com/yadea/smartmoto/vehicle/view/HibernateModeActivity.smali:1475 | sleepModeSwitch | SLEEP_MODE_SWITCH, SLEEP_MODE_SWITCH | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 79 座垫感应上电 | com/yadea/smartmoto/vehicle/view/SeatPowerUpActivity.smali:304 | seatPowerStatus | COMMAND_SEAT_POWER_UP, COMMAND_SEAT_POWER_UP | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 80 OTD 开关 | com/yadea/smartmoto/vehicle/view/OTDActivity.smali:296 | otdSwitch | COMMAND_OTD_SWITCH, COMMAND_OTD_SWITCH | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 81 大灯延时 | com/yadea/smartmoto/vehicle/view/DelayLightActivity.smali:443 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=5/10/15/30秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 84 安全辅助小蓝灯 | com/yadea/smartmoto/vehicle/view/AssistDriveLightActivity.smali:330 | blueLightSwitch | BLE_CONFIG_ASSIST_DRIVE_LIGHT, BLE_CONFIG_ASSIST_DRIVE_LIGHT | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 85 SOS 联系人 | com/yadea/smartmoto/vehicle/view/SosContactActivity.smali:1933 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 86 仪表亮度调节 | com/yadea/smartmoto/vehicle/view/BrightnessAdjustmentActivity.smali:430 | brightnessAdjustSwitch, brightnessAdjustValue | BRIGHTNESS_ADJUST_SWITCH, BRIGHTNESS_ADJUST_SWITCH, BRIGHTNESS_ADJUST_VALUE | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 88 自定义音效 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 89 辅助骑行 | com/yadea/smartmoto/vehicle/view/HelpPromotePlus2Activity.smali:196 | customDriveLevelStatus, customDriveSwitchStatus, helpCarState | STROLLER_CLOSE, STROLLER_OPEN, COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 90 赛道模式 | com/yadea/smartmoto/vehicle/view/TrackModeActivity.smali:131 | trackModeStatus | TRACK_MODE, TRACK_MODE | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 91 SOS 紧急呼叫 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 92 自定义骑行 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity2.smali:1887 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 93 静音模式 | com/yadea/smartmoto/vehicle/view/QuietModeActivity.smali:250 | 无 Controller 分支；独立流程另查 | COMMAND_DISABLE_OVERSPEED_ALERT | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 94 档位记忆 | com/yadea/smartmoto/vehicle/view/GearMemoryActivity.smali:298 | gearMemorySettingFeedback | COMMAND_GEAR_MEMORY, COMMAND_GEAR_MEMORY | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 95 微信互联 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 1005 自定义按键 | 动态 Registry / 独立流程；待逐项绑定 | customFunctionLong, customFunctionShort | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 66 自动校准 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| special:meter_wallpaper_skin 壁纸皮肤设置 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| special:meter_gauge_sync 车辆信息同步 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| special:footer_intelligence 用户智能识别 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| special:footer_lab 实验室 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| footer_device_detail 设备详情 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |

## 动态/独立流程补充

- EnergyRecoveryActivity：动态17/18；CustomizeKeyActivity：1005；RadarActivity/RadarBaseActivity：69；不是未发现的空 confId 设置。
- WallpaperSkinActivity、ReadyPActivity、MsgAsyncActivity：无独立固定 ID；分别审查壁纸皮肤、启动P档、信息同步流程。
- LowPowerModeInfoActivity、MultifunctionActivity、StartLightActivity、TemperatureUnitActivity、SmartBootActivity：需区分真实可写功能、说明页、外设流程；不得仅凭页面名添加车辆设置。
- ID41/88 使用 CustomSoundGateway/Controller，不能按通用命令缺失标记为未实现。
- ID51 syncTimeResultStatus 已被排除车辆实际值确认；没有关联事务证据时不建立确认。

## 批次证据

### A01：47/48/60/62（独立复审完成）

| ID | 重新核实的原 App 证据（Smali根下） | 当前完整调用链/修复 | 剩余限制 |
|---|---|---|---|
|47|view/ChargingPowerActivity.smali:getSettingConfigId:628；$onSeekChangeListener$1.b:122–136 以50W取整；VehicleSettingViewModel:12380–12388 原样读取chargeWattVal；s20:1471起独立CHARGE_WATT_SWITCH/VAL；qg:3020–3075；BleBicycleCommandManager:2639后0xCA|云端chargeWattSwitch/chargeWattVal分别configuredValue；TTP batteryChargingPowerStatus/Value分别vehicle/cached；值不再钳位/取整。Controller→BatterySettings→CloudBuilder或forItem→forConfigId→已有0xC9/CA→Writer。直接命令也限制0/1、200..1000且50W倍数。|真实值可为非50倍数并如实显示；发送目标必须按原UI50W。未运行/未实车。|
|48|view/ChargeCapActivity$onSeekChangeListener$1.b:122–124 原整数进度；VM:12394–12404 明确75<=值<101；VM:23454–23570 raw0/1→75、2..6→80..100；qg:1808–1826；BleBicycleCommandManager:2548–2648仅70/75/80/85/90/95/100|UI/云端合法75..100整数；新接原始TTP枚举回填。可编码的5倍数走已有BLE，76等云端原样提交，不再默改75。配置与回读分离。|原App UI与BLE离散域不同：非5倍数只有Cloud路径，BLE不可用时仍不伪造发送。raw0原App显示75，保持此映射。|
|60|activity/CapacitySettingActivity:297 ID60、setListener$2:76–96；dialog/CapacityChooseDialog.I:196–216及b..g点击为13/15/22/25/32/38；s20:1254 BATT_V_A_CAP_MODEL；VM:21305、21352容量值/开关|未知开关、未知容量都保持UNKNOWN；取消“最后一个=38Ah”回退；严格整数解析；独立BATT_BD_SETTING和BATT_V_A_CAP_MODEL，不发送兄弟字段；最终0xCD只接受六个枚举。|开关云端bean未证实读取字段，继续只由TTP回填。未增加猜测字段。|
|62|activity/HeatedSeatsActivity.setCheckChange:2303–2365、d/e/f点击:108–110，档位0/1/2；onCheckChange:1830–1975原样发送；VM:23979–24022原样回填；s20:1061、1109原样seatHeatingLevel；qg:1244–1263；BleBicycleCommandManager:2456–2470|三档显示1/2/3，索引和实际请求均0/1/2，修正旧+1/-1。seatHeating开关独立；云端、TTP、缓存分别保存；最终0xD3拒绝3/负数。|原App另有加热排程：本批未扩展，单列独立复杂流程待审。|

附证：yadea.apk R$layout.smali:1171–1173与资源表ID一致，layout_charging_power(res/kUJ.xml) min200/max1000；layout_charging_cap(res/XOZ.xml) min75/max100。只读aapt2；参数依据同时有上述Smali，未以截图推断。

A01状态：47、48、60基本读写路径STATIC_FLOW_VERIFIED；62即时开关/档位STATIC_FLOW_VERIFIED，完整页面排程PARTIAL。全部UNIT_TESTED、TEST_SOURCE_COMPILED；BUILD_VERIFIED（JVM/JS/Wasm及Android/Desktop构建退出0）。无RUNTIME_VERIFIED/VEHICLE_VERIFIED。UNKNOWN字段继续保留。

独立复审覆盖UI→Controller→CloudBuilder/forItem→forConfigId→Writer及直接VehicleSettingCommand构造；split-id不再绕过保护。刷新不打断本批在途写入，缺失/非法TTP撤销live新鲜度，VIN和请求代次沿用原设施。未改VehicleSettingFrameEncoder、TTP原始解析、网络路径/签名/平台和主题。

回归维护：旧Controller测试曾把乐观目标当作items实际值、无VIN回填、invalid TCS当on；旧云端测试仍期待已移除的ID7 DTO猜测，且把未知充电功率默认350当回读。只更正Fake夹具/来源断言，增加未知/回读断言，没有放宽生产代码。曾执行134条失败8条、151条失败1条；定位修复后151条全通过。

### A02：15/59/21/81（独立复审完成）

- Smali位置均相对根目录。`vehicle/view/ScheduledShutDownActivity:getSettingConfigId:197`=15；`ScheduledShutDown2Activity:210`=59；各自`$a.smali:a:54–81`将progress原值送VEHICLE_MANDATORY_POWER_DOWN_TIME(0x7D)，开关单独0x7B/7C。15的`$a:b:127–173`将<=2显示2min，59的`$a:a:87–110`将0显示30秒。VM21886/21911分别switch/time，E8:8070把非正数置0，但KMP不将UNKNOWN按此填默认。
- 读取只采用有绑定证据的TTP `vehiclePowerDownStatus/vehiclePowerDownTimeStatus`。`shutDownTime/vehiclePowerDownStatus`仅在CarSettingBean DTO声明/访问器出现，无页面/请求绑定，移除原KMP猜测配置。缺失不造false或0。
- `BackLightActivity:getSettingConfigId:178`=21；`DelayLightActivity:443`=81。各自`$a`读取wd5.e刻度索引原值送0x85，开关0x83/84独立；VM24107/24131回填switch/index。21标签15/30/45/60秒；81标签5/10/15/30秒。
- APK只读资源交叉核验（aapt2）：15布局0x7f0d00c6→res/G5L.xml(min2/max20)；59布局0x7f0d00c7→res/a_X.xml(min0/max20)；light_time_array(0x7f030009)、delay_light_time_array(0x7f030006)确认不同标签，布局res/vGb.xml与res/JYC.xml确认控件绑定。Smali回调为行为证据。
- BLE管理器`BleBicycleCommandManager:3367:sswitch_8d7`=0x7D、`:3167:sswitch_82e`=0x85；只读核对既有编码，不修改协议或帧。
- 新TimerSettings集中已证实类型/范围/选项、稳定split ID、共享底层命令writeKey。Controller编辑/提交→forItem/forConfigId→MappedWriter最终构造均校验，最终VehicleSettingCommand拒绝0x7D范围外、0x85范围外。Cloud无原绑定，继续不构建请求。写入接受只WAITING_READBACK。
- TTP缺失/非法使旧观察live=false，缓存不冒充车辆观察。刷新不覆盖草稿，活动写入时阻止刷新；15/59、21/81各共享维度阻止并发别名写入；VIN和序号复用原保护。
- 15原始0/1只有显示2min证据，没有可逆的实际计时语义，因此保持UNKNOWN，明确PARTIAL；不推测为2min。15可编辑2..20，59可编辑0..20。
- 初轮过滤测试退出1：新Fake CloudCarSettings缺values实参，已修夹具；复跑Timer12+Battery17+Cloud30=59/59，退出0。未放宽生产校验。

A02验证：Timer12、Battery17、Cloud30过滤59/59退出0；加前两阶段、低电量、GoldenFrame、权限和Controller共163/163退出0。JVM/JS/Wasm/测试源码编译退出0，Android Debug/Desktop构建退出0，diff检查退出0。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

### A03：27/31/50/54（独立复审完成）

- 起始HEAD：68c7baaccbcfc04c72f2bddfbfc5cc5da860536d，无外部提交，tracked/index干净，12既有untracked未动。
- 27：ArmedModeActivity.onCheckChange:60–90，rb_1→FORTIFY_MODE_NO(0x8D)，rb_2→FORTIFY_MODE_AUTO(0x8E)；VM22629–22743以offModeStatus=0/1回填，文字分别关机不设防/关机自动设防。
- 31：SpeedUnitActivity.onCheckChange:127–147和DistanceUnitActivity:51–71，rb_unit1→IMPERIAL_UNIT_KM(0x89)，另一项→IMPERIAL_UNIT_MILES(0x8A)；VM22749+ metricSystemStatus原值0/1。两个页面共confId31，X1的001不在本批。
- 50：GaugeStyleHandOffActivity.onCheckChange:60+，rb_1→COMMAND_BIKE_PANEL_THEME_TYPE参数0，rb_2→1，VM23174–23217 panelThemeCurrent原值；qg:6859–6876映射0x3F。BLE管理器4358–4398原始协议0..2，页面只暴露0/1。
- 54：VoltagePlatformActivity.onCheckChange:51–61原样传入COMMAND_CONST_VOLTAGE_SETTING；$a/$b/$c.onClick:90分别1/2/3；hp6.r:914–946明确48V/60V/70V；VM23223–23266 voltageSettingStatus为1/2/3，KMP显示索引减1，提交加1。BLE管理器977–1046原始命令0xC6定义0..6，保持协议域独立。
- 原缺陷：未知Choice直调forConfigId仍发命令；50/54非法索引被钳位；forItem可通过其他split ID短路。SimpleChoiceSettings固定已证实选项与stable ID，Controller、Mapper、Writer拒绝未知/越界/伪造。最终命令构造新增0x3F/0xC6原始域校验，不改帧编码。
- 初始selectedIndex=-1/valueKnown=false，不生成草稿；显式选择才能发送。TTP失效撤销旧live，VIN/序号/缓存隔离及失败取消沿用原设施；刷新保留编辑，活动写入禁止刷新。
- 独立复审重新从UI未知显示/Choice Sheet→Controller编辑/提交→forItem/forConfigId→Cloud/BLE Writer及原始构造审查。未修改原业务字段、其他设置/双主题/协议/网络；四项Cloud构建仍为空，读取仅可靠TTP。
- 初次过滤退出1：新测试CloudCarSettings位置实参顺序错误，已改命名实参；复跑SimpleChoice9+Timer12+Controller17+Golden3=41/41，退出0。

A03验证：4类41/41 Fake过滤退出0；JVM/JS/Wasm/测试源码编译退出0，Android Debug/Desktop构建退出0，diff检查退出0。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；没有App运行/车辆验证。

### A04：10电子围栏（独立复审完成）

- 原始证据：UserSelectRangeActivity.<init>:77–101的MINE_RADIUS=0xC8(200)、MAX_RADIUS=0x7D0(2000)；initView:243–328明确设置min/max。$b.a:61–67读取getProgress整数，$b.b显示m；setResult:508–538回传CAR_RANGE。p20.h:419–449读CAR_RANGE→s20.e→MixRequest.h(confId10)。
- s20.e:716–868更改distance后一起发送distanceOn(String 0/1)、distance(String整数)、centerCircleLon/Lat(Double)。开关路径UserSelectRangeActivity.onSwClick:418–469→VM.d9→P4:11465–11645，同样携带半径和两个圆心值。Q6:12242+读取云端distanceOn；不是TTP字段。证据足以确认组合，不假定服务端支持省略字段。
- 原KMP半径50..1000、18steps且单字段更新与原App不符。现在200..2000整数(1799内间隔)，不截断/钳位；缺失时placeholder200/valueKnown=false。
- 新GeofenceConfiguration承载同VIN的已解析云端兄弟字段，个别字段UNKNOWN仍能独立显示配置，但完整性建立前不提交。经纬度必须显式存在且有限；显式0保留，不把缺失变0。无Smali地理范围校验则不发明额外业务范围。
- 读取：YadeaGateway顶层标量解析新增两个已证实圆心字段→Mapper.toItems(cloud,vin)的只读快照；配置仍进configuredValue，不创建vehicleValue。
- 写入：UI原有独立开关/Slider→Controller目标校验/完整快照/同VIN→CloudWriter→Builder四字段组合→原update路径。Builder无快照也拒绝，MappedWriter对confId10始终无BLE命令，伪造split ID不回退BLE；未改URL、签名、通用HTTP。
- 同一复合配置在途时阻止刷新/兄弟提交；接受后，另一维度需刷新到包含前次目标的配置才可提交，避免旧快照覆盖已接受目标。草稿与configured/submitted分离；失败回滚、取消传播、旧VIN结果隔离沿用原机制。
- 初次41测试失败4：新Fake缺少结果回调且pollAttempts=0，正确触发超时；补Fake结果成功并断言CONFIRMED_BY_RESULT而非车辆确认。第二次53测试失败1：旧接口测试不带兄弟配置；改为完整Fake快照并加强四字段请求断言。最终5类53/53退出0，没有放宽生产校验。

A04最终验证：Geofence11+CloudConfigured30+CloudWriter4+WriterAccess5+ApiSemantics3=53/53，退出0。补充复审后允许准备兄弟草稿，但未刷新前阻止提交并明确提示，复跑53/53退出0。最后一次JVM/JS/Wasm/测试源码编译退出0、Android Debug/Desktop构建退出0、diff检查退出0。未运行App/车辆；云端结果CONFIRMED_BY_RESULT不等于车辆回读。配置字段完整且同VIN才可写(CONDITIONAL)，缺圆心需刷新/补接口证据。

## A05：ID73 氛围灯（验证中）

- 原页 `com/yadea/smartmoto/vehicle/view/FeelLightSettingActivity.smali`：构造89–349及settingData2087–2329：显示顺序常亮0、闪烁2、呼吸1、流水3；运行顺序0/1/4/5/2/3。`refreshUi:1471–2069`：仅呼吸/流水可选单双色与色条，流水有运行方式。
- `FeelLightSettingActivity$c.smali:onClick:88–491`：模式必须选择；255→254(129–204)；常亮/闪烁组合RGB2=255/255/255、color=0、operation=-1(365–399)；呼吸/流水携带RGB2和颜色模式，流水需要运行0..5(253–360)；所有模式带RGB1(402–466)。不是为未知字段发明默认值。
- `qg.smali:2224–2244`：ATMOSPHERE_CONFIG仅连接时走0xF7；`BleBicycleCommandManager.generateMultiBleCommand:7380–7700`既有9字段编码；本批不改帧。
- `VehicleSettingViewModel.smali:24445–24639`：ambientStatus、show、两组RGB、operation、color；Activity initObserver:1061–1177的操作回调仅提示应用结果，不等于车辆回读。
- KMP原缺口：Controller只读开关；Atmosphere默认0/254且validTarget恒true；forItem钳位；无最终不可变参数保护；无比较回读。
- 修复：AtmosphereSettings按模式校验/映射TTP和提交；默认模式/RGB=-1未知；Controller要求可靠同VIN已知配置后编辑（证据不完整时保守阻止，PARTIAL）；配置最终命令必须使用私有不可变参数；保留原始观察，255只在提交时规范化；按有效参数比较新鲜回读。开关独立、严格0/1。Writer/稳定ID和在途刷新校验。
- UI沿用原双主题语义组件和同一Sheet，配置不完整明确说明，调用submitDraft；不把本地选择标成车辆值。
- 当前状态：CONDITIONAL/PARTIAL（需要完整车辆配置；没有真实App/车型/车辆验证，不声明运行完整）。

## A06：ID19/26/36/53（验证中）

- 原页 `AutomaticLightActivity.onSwClick:50–108`→AUTOMATIC_HEADLIGHT_OPEN/CLOSE；`TurnSignalActivity.onSwClick:69–155`→COMMAND_DIRECTION_LIGHT_RESET_ON/OFF；`BrakePowerOffActivity.onSwClick:40–119`及其onSwClick$1→BRAKE_OUTAGE_OPEN/CLOSE，关闭先确认；`BracketPoweredActivity.onSwClick:50–110`→COMMAND_CONST_TEMPLE_SETTING 1/0。
- `qg.smali`分别5346/3680→0x25/26、4254/2981→0x23/24、1205/5140→0x9B/9C、1049→0xC7；均为原有BLE路径。没有新增命令或改编码。
- `VehicleSettingViewModel.smali`回填分别21918–21973、24892–24942、22820–22870、23272–23323；每项1为开、0为关、负值未上报。KMP严格拒绝其他值，失效观察不维持LIVE。
- 53的`bleApproachPowerOnEnable`全Smali引用仅DTO(两Bean声明、getter/setter、copy/equals/toString)；没有页面/VM/P4/s20绑定；Q6:13000仅bikeConfigVos能力门控，不能作为值证据。本批移除53猜测的configured/初值/UpdateBuilder字段，保留原始响应键供审计；不更改其他ID映射。
- SimpleToggleSettings约束稳定条目ID/Toggle/valueKnown；Controller、forConfigId、forItem、CloudWriter校验；能力判断独立已知性。保留乐观目标、来源/回执/回读分离；36复用现有AppAlertDialog与双主题确认行为。
- 同时只读发现2的`TempleInductionActivity.onSwClick:54–140`在W0车型且其他依赖状态成立时改用BRACE_CLOSE_LOCKER_CLOSE；KMP缺可靠车型门控上下文，留作CONDITIONAL/PARTIAL后续审查，未在A06改动。3原页仅看到CARRY_OUT写入，读取绑定仍需独立核对。
