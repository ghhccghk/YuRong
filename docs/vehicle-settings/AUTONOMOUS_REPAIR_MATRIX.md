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
| 2 边撑感应 | vehicle/view/TempleInductionActivity.smali:40–173,209 | sideBraceStatus，严格0/1 | 0F开启；10独立关闭；原页条件分支AC | A15：关闭仅允许已有证据证明10安全的上下文；Controller/Writer/Mapper均保护，7/39共用锁开关互斥；没有猜选AC | P0 / PARTIAL / A15（不确定车型关闭分支BLOCKED，无运行/实车验证） |
| 3 携出助力 | vehicle/view/SeatCushionSensorActivity.smali:50–108,144 | 原pushModeStatus，KMP原始模型/解析缺失 | CARRY_OUT_OPEN/CLOSE，11/12 | A15：独立BLE写入严格已知值/稳定ID；无可靠回填时UI保持UNKNOWN禁用；没有借用seatPowerStatus等字段 | P1 / WRITE_ONLY，读取BLOCKED / A15（无运行/实车验证） |
| 4 档位设置 | com/yadea/smartmoto/vehicle/view/TopSpeedActivity.smali:85 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 5 车辆音效 | com/yadea/smartmoto/vehicle/activity/OldVehicleSoundEffectsActivity.smali:506 | alarmRingThemeCurrent | ALARM_THEME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 7 自动落锁 | com/yadea/smartmoto/vehicle/view/AutoShutDownActivity.smali:505 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 8 一键报失 | com/yadea/smartmoto/vehicle/lostmode/LostReportModeActivity.smali:549 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 9 信息提醒 | com/yadea/smartmoto/vehicle/view/LowPowerActivity.smali:997 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 10 电子围栏 | com/yadea/smartmoto/vehicle/activity/UserSelectRangeActivity.smali:846 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | A04：distanceOn/distance/centerCircleLon/Lat完整云端快照；半径200..2000m整数，缺伴随字段拒绝提交；只有结果确认，无车辆回读 | P0 / CONDITIONAL / A04 |
| 11 自动开关机 | com/yadea/smartmoto/vehicle/view/AutoOnOffActivity.smali:187 | bleAutoTurnOnOffGearStatus, bleAutoTurnOnOffStatus | AUTO_TURN_ON_OFF_CLOSE, AUTO_TURN_ON_OFF_OPEN, AUTO_TURN_ON_OFF | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 13 报警音 | vehicle/view/VehicleAlarmActivity.smali:50–108,144 | 原App alarmSoundSwitchAfterStatus；KMP原始模型缺失 | WARING_SOUND_OPEN/CLOSE→62/63 | A13：DTO-only alarmSoundEnable不再伪造配置/云端写入；严格UNKNOWN/稳定ID，读链路触及受保护原始解析 | P1 / WRITE_ONLY（底层）；读取BLOCKED、UI未知禁用 / A13 |
| 14 车辆报警灵敏度 | com/yadea/smartmoto/vehicle/view/VehicleAlarmSensitivityActivity.smali:87 | alarmSensitivityStatus | ALARM_SENSITIVITY_GEAR | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 15 离车定时关机 | com/yadea/smartmoto/vehicle/view/ScheduledShutDownActivity.smali:197 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE开关0x7B/7C、时间0x7D=2..20min；DTO字段无页面绑定不作配置；原始0/1保持UNKNOWN | P1 / PARTIAL / A02 |
| 16 倾倒报警 | vehicle/view/DumpNoticeActivity.smali:71–148,234 | 无原页TTP证据 | 无BLE映射 | A13：dumpAlarm独立云端配置/更新，严格目标/稳定ID/在途刷新；结果确认与车辆值分离 | P1 / COMPLETE（云端；无运行/实车验证） / A13 |
| 17 能量回收开关 | vehicle/view/EnergyRecoveryActivity.smali:389–473,865–1037动态模式 | slideModePowerRecoverStatus | ENERGY_RECOVER_OPEN/CLOSE→13/14 | A12：独立BLE开关；resistanceOn仅DTO，移除猜测云端读写；严格UNKNOWN/稳定ID及新鲜回读 | P0 / CONDITIONAL / A12（无运行/实车验证） |
| 18 能量回收档位 | vehicle/view/EnergyRecoveryActivity.smali:389–473,865–1037动态模式 | slideModePowerRecoverCurrent | ENERGY_RECOVER_LEVEL→3C | A12：整数1/2/3；组合模式等待开关LIVE回读；最高档显式确认；独立BLE及新鲜回读 | P0 / CONDITIONAL / A12（无运行/实车验证） |
| 19 自动大灯 | com/yadea/smartmoto/vehicle/view/AutomaticLightActivity.smali:144 | automaticHeadlightFunctionStatus | AUTOMATIC_HEADLIGHT_CLOSE, AUTOMATIC_HEADLIGHT_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 20 车辆音量 | com/yadea/smartmoto/vehicle/view/VehicleVolumeActivity.smali:87 | alarmWarningVolumeLevel | VOLUME | A11：原页0/1/2三档；拒绝默认未知、取整钳位和越界观察；独立回读确认 | P0 / COMPLETE / A11（BLE；无运行/实车验证） |
| 21 伴我回家灯 | com/yadea/smartmoto/vehicle/view/BackLightActivity.smali:178 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=15/30/45/60秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 24 紧急联系人 | com/yadea/smartmoto/vehicle/view/EmergencyContactActivity.smali:964 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 25 密码解锁 | com/yadea/smartmoto/vehicle/pwdunlock/PassWordUnlockActivity.smali:845 | passwordUnlockStatus | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 26 转向灯自动回位 | com/yadea/smartmoto/vehicle/view/TurnSignalActivity.smali:283 | turnSignalReturnsAutomaticallyStatus | COMMAND_DIRECTION_LIGHT_RESET_OFF, COMMAND_DIRECTION_LIGHT_RESET_ON | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 27 设防模式 | com/yadea/smartmoto/vehicle/view/ArmedModeActivity.smali:231 | offModeStatus | FORTIFY_MODE_AUTO, FORTIFY_MODE_NO | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 31 速度单位 | com/yadea/smartmoto/vehicle/view/SpeedUnitActivity.smali:232<br>com/yadea/smartmoto/vehicle/view/DistanceUnitActivity.smali:156 | metricSystemStatus | IMPERIAL_UNIT_KM, IMPERIAL_UNIT_MILES | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 32 倾倒提醒 | com/yadea/smartmoto/vehicle/view/DumpWarningActivity.smali:144 | tiltWarningStatus | TILT_WARNING_CLOSE, TILT_WARNING_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 33 模拟声浪 | vehicle/view/SimulatedSoundActivity.smali:onSwClick207–297,onCheckChange128–205；VM21499–21709；Binding342/430/572 | simulatedSoundWaveStatus、ModeStatus、VolumeStatus；OnlineStatus=0开关门控 | 95/96开关已接通；97模式/98音量无既有帧 | A17：同VIN新鲜设备可用许可保护Controller/实际Writer/Mapper；模式0/1、音量1..16独立只读回填，缺失/非法不伪造；云端目录决定子项存在 | P0 / PARTIAL；模式/音量写入BLOCKED / A17（无运行/实车验证） |
| 36 刹车断电 | com/yadea/smartmoto/vehicle/view/BrakePowerOffActivity.smali:155 | brakePowerDownFunctionStatus | BRAKE_OUTAGE_CLOSE, BRAKE_OUTAGE_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 37 TCS 车身稳定 | com/yadea/smartmoto/vehicle/view/BodyStabilityActivity.smali:154 | tcsWorkMode | TCS_BODY_STABILITY, TCS_BODY_STABILITY | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 38 ABS 防抱死 | com/yadea/smartmoto/vehicle/view/AntiLockActivity.smali:152 | absFunctionStatus | ABS_ANTI_LOCK_CLOSE, ABS_ANTI_LOCK_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 39 边撑自动关机 | com/yadea/smartmoto/vehicle/view/BraceAutoShutDownActivity.smali:376 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | 当前 HEAD329effd已修TTP/独立参数/取消/VIN；7/39车型门控证据限制保留UNKNOWN | P2 / PARTIAL / 基线复核 |
| 40 彩蛋音效 | vehicle/view/EggSoundActivity.smali:88–207,306 | 无原页TTP证据 | 无BLE映射 | A13：colorEggSoundEffect配置/开启，关闭CLEAR_SOUND_EFFECT=7（修正旧0），valueJson同7；严格UNKNOWN/稳定ID | P0 / COMPLETE（云端；无运行/实车验证） / A13 |
| 41 自定义音效 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 42 车辆显示剩余里程 | com/yadea/smartmoto/vehicle/view/ShowInstrumentEnduranceActivity.smali:58–218,260 | remainingMileageDebugStatus | DISPLAY_REMAINING_MILEAGE_CLOSE/OPEN，A5/A4 | A14：严格TTP开关；开启先读同VIN cal2资格，非空提示仅说明不发送；Controller→带本次资格的Item→实际Writer→命令；接受与回读分离 | P0 / CONDITIONAL / A14（无运行/实车验证） |
| 43 倒车模式 | com/yadea/smartmoto/vehicle/view/ReversingFunctionActivity.smali:144 | reversingState | REVERSING_CLOSE, REVERSING_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 44 婴儿车模式 | com/yadea/smartmoto/vehicle/view/HelpPromoteActivity.smali:144 | helpCarState | STROLLER_CLOSE, STROLLER_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 45 定速巡航 | com/yadea/smartmoto/vehicle/view/ConstantSpeedModeActivity.smali:144 | constSpeedState | CONST_SPEED_CLOSE, CONST_SPEED_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 46 陡坡缓降 | com/yadea/smartmoto/vehicle/activity/SlopeDescentActivity.smali:154 | steepSlopeState | BLE_SLOP_DESCENT_CLOSE, BLE_SLOP_DESCENT_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 47 充电功率 | com/yadea/smartmoto/vehicle/view/ChargingPowerActivity.smali:628 | batteryChargingPowerStatus, batteryChargingPowerValue | COMMAND_CONST_CHARGING_POWER_STATE, COMMAND_CONST_CHARGING_POWER_STATE, COMMAND_CONST_CHARGING_POWER_VALUE | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 48 充电上限 | com/yadea/smartmoto/vehicle/view/ChargeCapActivity.smali:438 | 无 Controller 分支；独立流程另查 | COMMAND_CONST_CHARGING_LIMIT_VALUE | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 49 坡道驻停 | com/yadea/smartmoto/vehicle/view/RampStopsActivity.smali:144 | rampSlopeState | BLE_RAMP_SLOP_CLOSE, BLE_RAMP_SLOP_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 50 仪表风格切换 | com/yadea/smartmoto/vehicle/activity/GaugeStyleHandOffActivity.smali:522 | panelThemeCurrent | COMMAND_BIKE_PANEL_THEME_TYPE | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 51 仪表时间同步 | BleMeterTimeSyncActivity:onSwClick67–189；$setListener$2:60–85；VM23115–23165,k2:28292；MainActivityLogic.W0:2037,q0:6464 | syncTimeResultStatus仅原页回显，不作持续设置/事务回执 | 0xC1开关0/1；延迟事件→0xC0实际校时，当前无编码 | A18：独立TimeSyncStatusReport保留原页回显及VIN/缓存/live来源；Controller/实际Writer/Mapper拒绝未知或伪造目标；UI明确本次校时未确认，开关真实值保持UNKNOWN | P0 / PARTIAL；实际0xC0校时及事务确认BLOCKED / A18（无运行/实车验证） |
| 52 手动距离自定义 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 53 边撑开机 | com/yadea/smartmoto/vehicle/view/BracketPoweredActivity.smali:146 | templeSettingStatus | COMMAND_CONST_TEMPLE_SETTING, COMMAND_CONST_TEMPLE_SETTING | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 54 电压设置 | com/yadea/smartmoto/vehicle/view/VoltagePlatformActivity.smali:100 | voltageSettingStatus | COMMAND_CONST_VOLTAGE_SETTING | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 55 FREE 自由模式 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity.smali:1452 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 58 档位设置 | com/yadea/smartmoto/vehicle/view/MaxSpeedSettingActivity.smali:220 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 59 报警锁 | com/yadea/smartmoto/vehicle/view/ScheduledShutDown2Activity.smali:210 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE同开关、0x7D原始0=30秒、1..20=分钟；UNKNOWN无默认；别名并发保护 | P1 / COMPLETE / A02 |
| 60 电池设置 | com/yadea/smartmoto/vehicle/activity/CapacitySettingActivity.smali:297 | capacitySettingStatus, capacitySwitchStatus | COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SETTING | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 61 安全限速 | com/yadea/smartmoto/vehicle/view/MaxSpeedLimitActivity.smali:610 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 62 座椅加热 | com/yadea/smartmoto/vehicle/activity/HeatedSeatsActivity.smali:2596 | seatHeatLevelStatus, seatHeatSwitchStatus | SEAT_HEAT_SWITCH, SEAT_HEAT_SWITCH, SEAT_HEAT_LEVEL | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 63 超级狂暴模式 | com/yadea/smartmoto/vehicle/view/TPowerActivity.smali:114 | tPowerStatus | COMMAND_CONST_T_POWER_SETTING, COMMAND_CONST_T_POWER_SETTING | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 64 辅助推行 | com/yadea/smartmoto/vehicle/view/HelpPromotePlusActivity.smali:188 | customDriveLevelStatus, customDriveSwitchStatus, helpCarState | STROLLER_CLOSE, STROLLER_OPEN, COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 68 急刹爆闪 | com/yadea/smartmoto/vehicle/view/SuddenBrakingActivity.smali:147 | quickBrakeLightStatus | COMMAND_QUICK_BRAKE_LIGHT_SWITCH, COMMAND_QUICK_BRAKE_LIGHT_SWITCH | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 69 雷达 | 动态 Registry / 独立流程；待逐项绑定 | bsdRadar, frontCollisionWarning, rcwRadarSwitch | COMMAND_FRONT_COLLISION_CLOSE, COMMAND_FRONT_COLLISION_OPEN | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 82 电话告警 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 71 仪表设置 | com/yadea/smartmoto/vehicle/view/GaugeSettingsActivity.smali:621 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 72 长续航模式 | com/yadea/smartmoto/vehicle/view/EnduranceModeActivity.smali:303 | longEnduranceStatus | LONG_ENDURANCE_SETTING, LONG_ENDURANCE_SETTING | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 73 氛围灯 | com/yadea/smartmoto/vehicle/view/FeelLightSettingActivity.smali:2404 | ambientStatus、show/color/operation、RGB1/2 | C3开关；F7完整模式组合 | A05：完整TTP回填/独立开关/不可变复合命令/新鲜回读比较；未知禁止默认补齐，完整配置才可编辑 | P0 / CONDITIONAL / A05 |
| 75 定位器防盗 | vehicle/view/IotTamperProofActivity.smali:50–158,194；VM.t4:30788→p4 | iotRemoveAlarm，严格0/1 | 原FA；本任务禁止新增帧；云端tamperProofIotEnable仅证实整数1 | A16：可靠TTP/缓存隔离；移除无读绑定的云端配置推断；阻止未证实0关闭；开启接受后等待新鲜TTP | P0 / PARTIAL，关闭及BLE帧BLOCKED / A16 |
| 76 定位开关 | vehicle/locationswitch/LocationSwitchActivity.smali:145–218,241–295,319 | 无已证实TTP；positioningFunc只进configuredValue | 原param-setting：POSITIONING_FUNC，0/1；返回ID轮询 | A16：同VIN云端onlineState检查、关闭确认、精确参数、实际Writer保留本次ID；缺ID不猜confId；结果成功仍非车辆回读 | P0 / CONDITIONAL / A16（无运行/实车验证） |
| 77 休眠模式 | com/yadea/smartmoto/vehicle/view/HibernateModeActivity.smali:1475 | sleepModeSwitch | SLEEP_MODE_SWITCH, SLEEP_MODE_SWITCH | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 79 座垫感应上电 | com/yadea/smartmoto/vehicle/view/SeatPowerUpActivity.smali:304 | seatPowerStatus | COMMAND_SEAT_POWER_UP, COMMAND_SEAT_POWER_UP | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 80 OTD 开关 | com/yadea/smartmoto/vehicle/view/OTDActivity.smali:296 | otdSwitch | COMMAND_OTD_SWITCH, COMMAND_OTD_SWITCH | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 81 大灯延时 | com/yadea/smartmoto/vehicle/view/DelayLightActivity.smali:443 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=5/10/15/30秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 84 安全辅助小蓝灯 | com/yadea/smartmoto/vehicle/view/AssistDriveLightActivity.smali:330 | blueLightSwitch | BLE_CONFIG_ASSIST_DRIVE_LIGHT, BLE_CONFIG_ASSIST_DRIVE_LIGHT | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 85 SOS 联系人 | com/yadea/smartmoto/vehicle/view/SosContactActivity.smali:1933 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 86 仪表亮度调节 | com/yadea/smartmoto/vehicle/view/BrightnessAdjustmentActivity.smali:430 | brightnessAdjustSwitch, brightnessAdjustValue | BRIGHTNESS_ADJUST_SWITCH, BRIGHTNESS_ADJUST_VALUE | A11：开关与整数0..100独立；去除伪造80；UI连接/供电门控，Controller要求开关有效LIVE回读才调亮度；无已证实云端配置 | P0 / CONDITIONAL / A11（BLE；无运行/实车验证） |
| 88 自定义音效 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 89 辅助骑行 | com/yadea/smartmoto/vehicle/view/HelpPromotePlus2Activity.smali:196 | customDriveLevelStatus, customDriveSwitchStatus, helpCarState | STROLLER_CLOSE, STROLLER_OPEN, COMMAND_CONST_CUSTOM_DRIVE_LEVEL_SETTING | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 90 赛道模式 | com/yadea/smartmoto/vehicle/view/TrackModeActivity.smali:131 | trackModeStatus | TRACK_MODE, TRACK_MODE | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 91 SOS 紧急呼叫 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | UI 已有目录类型；云端字段、Writer 与确认待复核 | P1 / PARTIAL / 待审 |
| 92 自定义骑行 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity2.smali:1887 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 93 静音模式 | QuietModeActivity:sendCommand130–227,setListener303起,getSettingConfigId250；qg6019–6045 | 原页clearCheck，无可靠当前值读绑定 | COMMAND_DISABLE_OVERSPEED_ALERT→141，经独立order39控制路径 | A19阻断错误普通3E设置帧；Controller/实际Writer/Mapper/原始命令构造均拒绝，目录入口只读UNKNOWN；不猜云端字段 | P0 / BLOCKED；正确39发送须改受保护编码 / A19（无运行/实车验证） |
| 94 档位记忆 | com/yadea/smartmoto/vehicle/view/GearMemoryActivity.smali:298 | gearMemorySettingFeedback | COMMAND_GEAR_MEMORY, COMMAND_GEAR_MEMORY | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
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

## A07：ID37/38/46/49（独立复审及验证完成）

- `BodyStabilityActivity.onSwClick:40–118`开启TCS参数0，关闭先确认，再由`$onSwClick$1.invoke:61–89`发送1；`AntiLockActivity.onSwClick:40–116`与`$onSwClick$1.invoke:61–87`开/关独立命令，关闭确认。
- `vehicle/activity/SlopeDescentActivity.onSwClick:50–109`与`vehicle/view/RampStopsActivity.onSwClick:50–108`分别BLE_SLOP_DESCENT_OPEN/CLOSE、BLE_RAMP_SLOP_OPEN/CLOSE。
- `qg.smali:1088/2108/5980/2420/6388/4110/5319`既有映射9D、99/9A、AE/AF、B0/B1。`BleBicycleCommandManager:2741–2749`明确TCS合法0..7，非法会默认7；KMP在最终命令构造拒绝非法值，未改编码，不把非法值钳位发送。
- `VehicleSettingViewModel.smali:22879–22930`TCS0开、其他合法模式关；22938–22989 ABS、22997–23048陡坡、23056–23107驻停：1开/0关。TCS仅0..7可观察，其他三项只0/1；负数/越界保持未知或过期。
- 复用SimpleToggleSettings的Controller/Writer/forConfigId/forItem稳定ID及valueKnown保护，能力独立未知值；保留原App命令及UI确认、原有VIN/序号/草稿/回执与回读分离。
- 四项必要BLE链路COMPLETE（仅静态/Fake范围）；30/30过滤测试及JVM/JS/Wasm/测试源码、Android/Desktop构建退出0。无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

## A08：ID63/68/72/79（独立复审及验证完成）

- `com/yadea/widget/switchItem/YedeaOldSwitch.smali:onTouchEvent:255–291`只调用自定义listener，没有调用父类触摸/切换；因此回调isChecked是旧值，必须逐分支求目标，不能把既有KMP注释当证据。
- `vehicle/view/TPowerActivity.onSwClick:40–65`旧值取反，COMMAND_CONST_T_POWER_SETTING 1开/0关；ID63:114。VM:23637–23659 tPowerStatus==1开；qg:6757–6777→CF。
- `SuddenBrakingActivity.onSwClick:50–111`旧值取反，经nullable判断得到1开/0关；ID68:147。VM:24214–24235 quickBrakeLightStatus==1开；qg:3313–3333→D9。修正旧KMP误写0开/1关。
- `EnduranceModeActivity.refreshCheckBox:59–197`读取x3原始0时setChecked(true)，其setListener$lambda$2:200–280目标再取反得到0开/1关；ID72:303。VM:24238–24249 longEnduranceStatus原值→Q3，x3 getter对应Q3；qg:7080–7098→DD。修正旧KMP读取/写入反向。
- `SeatPowerUpActivity.onSwClick:107–189`旧值取反1开/0关，ID79:304；VM:24334–24389 seatPowerStatus==1开；qg:1126–1146→FB1。无复合兄弟字段。
- 原页面sendVehicleSetting→qg仅连接BLE发送；s20无这四个命令云端分支，不创建猜测云端键。既有KMP Controller→CloudWriter BLE分流→MappedWriter→forItem/forConfigId→既有编码。四项严格已知性/稳定ID/失效观察降级，最终CF/D9/DD/FB1只接受0/1。

A08验证：31/31精确过滤测试及全部离线构建退出0；STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/车辆验证。

## A09：ID80/84/90/94（独立复审及验证完成）

- 原始触摸语义沿用A08 `YedeaOldSwitch.onTouchEvent:255–291`：回调读旧checked，未自动toggle。
- `vehicle/view/OTDActivity.onSwClick:68–112`，ID80:296；`AssistDriveLightActivity.onSwClick:248–294`，ID84:330；`TrackModeActivity.onSwClick:50–95`，ID90:131；`GearMemoryActivity.onSwClick:68–130`，ID94:298。四项均旧checked取反作为目标，1开/0关；无复合兄弟。
- `VehicleSettingViewModel.smali`：trackModeStatus:22012–22034、blueLightSwitch:24190–24211、otdSwitch:24706–24727、gearMemorySettingFeedback:25279–25295，均==1开；KMP只接受0/1，其他UNKNOWN。
- `qg.smali:1348–1368` TRACK_MODE→13F；1480–1500 OTD→132；2341–2361 ASSIST_DRIVE_LIGHT→136；6237–6257 GEAR_MEMORY→144。均BLE连接门控，无s20云端分支。Controller→CloudWriter→MappedWriter→forItem/forConfigId→既有编码；不创建云端字段。
- 修正80/84/94原KMP注释及0开1关错误参数；90方向原本正确。四项复用严格known/稳定ID/失效观察/在途刷新，最终132/136/13F/144拒绝非0/1。

A09：32/32精确过滤测试及全部离线构建退出0；STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/车辆验证。

## A10：ID32/43/44/45（独立复审及验证完成）

- `vehicle/view/DumpWarningActivity`、`ReversingFunctionActivity`、`HelpPromoteActivity`、`ConstantSpeedModeActivity`的onSwClick均50–108、getSettingConfigId均144，分别32/43/44/45；原触摸回调取旧checked反值，单独开/关命令，无兄弟参数。
- `qg.smali`：TILT_WARNING_OPEN/CLOSE:2680/2941→A0/A1；REVERSING:4448/3747→A6/A7；STROLLER:6414/3453→A8/A9；CONST_SPEED:4136/5940→AA/AB。原s20无这些命令云端分支。
- `VehicleSettingViewModel.smali`：tiltWarningStatus21142–21201、reversingState22099–22156、helpCarState22160–22217、constSpeedState22221–22277，均1开/0关。`ActivityConstantSpeedModeBindingImpl:631–661,754–756`→U2；`VehicleSettingViewModel.U2:17583–17601`→V1；V1由constSpeedState回填，原KMP误用cruiseControl，现修正。既有TTP解析已提供constSpeedState，无原始解析修改。
- UI→Controller→CloudWriter BLE分流→MappedWriter→forItem/forConfigId→既有固定命令/编码；扩展严格已知性/稳定ID/无效观察与在途保护，未改变其他设置。没有云端猜测字段。

A10：33/33精确过滤测试、全部离线构建和diff检查退出0；STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/车辆验证。

## A11：ID20/86（独立复审及验证完成）

- ID20：`VehicleVolumeActivity.getSettingConfigId:87`→20；`$a.b:86–102`记录thumbPosition、`a:40–62`直接提交VOLUME；原APK `res/fYa.xml`（activity_vehicle_volume）max2/min0/ticks3，volume_array为低/中/高。`VehicleSettingViewModel:21717–21846`将alarmWarningVolumeLevel的0/1/2映射低/中/高和进度。`ch:4288–4308`→0x46；`BleBicycleCommandManager:4134–4140`原始命令域0..7，保留该底层域，设置页严格0..2。
- ID86：`BrightnessAdjustmentActivity.onSwClick:83–167`旧checked取反→BRIGHTNESS_ADJUST_SWITCH；`refreshUiStatus:172–403`连接+供电、C2开关共同控制拖动；`$a.a:40–86`以getProgress整数单独提交BRIGHTNESS_ADJUST_VALUE。原APK `res/cWg.xml` min0/max100；`VehicleSettingViewModel.C2:6809/D2:7373`→y4/z4，分别由brightnessAdjustSwitch:24951–25006、brightnessAdjustValue:24287–24330回填。`qg:3384–3403/6160–6179`→135/134。全Smali字段引用仅TTP/解析/VM/qv，无页面云端绑定，移除KMP凭空80和同名云端读取；不新增云端映射。
- 实际链：原有通用AppSliderPreference/AppSwitchPreference（双主题）→Controller校验/草稿→CloudWriter无云端构建时BLE→MappedWriter→forItem/forConfigId严格值/稳定ID→既有命令及编码。亮度Controller只依赖有效同VIN实时开关；UI另外保持原App连接+供电门控，低层数值命令不表达连接/供电状态。不改协议、原始TTP、编码或通用网络。
- 新增12项Fake-only测试；修正两份旧测试的八档音量假设与超出原页面范围的夹具。首次52测试退出1（5失败：夹具/遍历范围问题），修正后52/52退出0，未删除测试或放宽生产校验。JVM/JS/Wasm/测试源码编译、Android Debug/Desktop构建及diff检查退出0。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；没有App运行/车辆验证。

## A12：ID17/18（独立复审及验证完成）

- `vehicle/view/EnergyRecoveryActivity.smali:389–473,865–1037`以isAuth的17/18组合选择开关/档位/组合模式；`$a:35–72`核对UiMode分支。`isSeekBarActive:476–595`连接和供电门控；组合模式另要求C3开关已开，单档位模式不要求17。`onSwClick:598–662`读旧checked取反，单独ENERGY_RECOVER_OPEN/CLOSE。
- `$onSeekChangeListener$1.a:40–166,b:169–185`以thumbPosition+1提交1/2/3；新选最高档且原观察不为3时弹提示。`$onStopTrackingTouch$1.invoke:61–106`确认发送，`$onStopTrackingTouch$2.invoke:61起`取消恢复；`$dialog$2`使用user_car_energy_recovery_hi_tip，原文“开启高档位能量回收可能导致刹车有顿挫感，建议在习惯中档位后开启”。APK布局res/ZR_.xml三档、低/中/高；不把默认进度当配置。
- `VehicleSettingViewModel.C3:6829–6847`→e2，`22282–22339`由slideModePowerRecoverStatus回填0关1开；`22343起`slideModePowerRecoverCurrent原值→g2，1/2/3→进度0/50/100。`G6:8360–8378`→p2，`23587–23605`来自VehicleDataBean.isPowerStateOpen。
- `qg:2069–2088,5688–5707,5727–5746`分别OPEN→13、LEVEL→3C、CLOSE→14；ch也有独立BLE分支，s20无对应云端分支。`BleBicycleCommandManager:4446–4452`原始3C域0..7，保留既有底层域，页面严格1..3。全目录resistanceOn/ResistanceOn只在CarSettingBean和VehicleSetting两个DTO出现，没有页面/VM/s20绑定，撤销KMP未经证实的云端17映射。
- 实际链：原App*组件→Controller严格目标与组合门控/最高档确认→CloudWriter无云端构建时BLE→MappedWriter→forItem/forConfigId→已有命令/编码。普通update/submitDraft不能跳过最高档确认，显式确认入口仍重验目标/VIN。低层数值命令不表达供电与UI确认，连接/供电门控由UI沿用，未新增协议。观察只来自既有同VIN/序号管线，非法字段降级旧LIVE，发送接受不代表车辆确认。
- 36/36精确Fake过滤测试及全部离线编译/Android/Desktop构建退出0；STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED。模式/连接条件下可用，CONDITIONAL；没有运行/实车验证。

## A13：ID13/16/40（独立复审及验证完成）

- 13：`vehicle/view/VehicleAlarmActivity.onSwClick:50–108,getSettingConfigId:144`旧checked取反→WARING_SOUND_OPEN/CLOSE；`qg:2381–2400,4032–4051`→63/62，已有编码支持。`ActivityVehicleAlarmBindingImpl:410–440`→t1，`VehicleSettingViewModel.t1:30728–30745`→u0，`20174–20236`由TTP alarmSoundSwitchAfterStatus==1回填。全Smali alarmSoundEnable只出现于CarSettingBean/VehicleSetting DTO，无页面/请求绑定，撤销KMP猜测配置/云端更新。KMP TtpInfo及原始解析没有原字段，本任务明确保护原始解析，读取BLOCKED；低层已证实写入WRITE_ONLY，UI仍UNKNOWN禁用。
- 16：`DumpNoticeActivity.onSwClick:71–148,getSettingConfigId:234`单独d9("16",!oldChecked)；`VehicleSettingViewModel.P4:11348–11449`dumpAlarm转0/1字符串入Map，`12261–12279`同字段==1回填o2；`ActivityDumpNoticeBindingImpl:520`绑定。`s20:1740起`再次确认16→dumpAlarm。没有对应BLE/TTP页面证据，保持纯云端。
- 40：`EggSoundActivity.onSwClick:88–207,getSettingConfigId:306`开启d9、关闭g9带错误回调；`$onSwClick$2.invoke`关闭失败时恢复checked。`VehicleSettingViewModel.P4:11149–11222`开启colorEggSoundEffect，关闭oper=SET_PARAM/id=CLEAR_SOUND_EFFECT/value=7并调用op6.d(7)构造valueJson；7在P4:10171赋值，非默认0。`op6.d:201–237`明确byteCount/dataType/sort均1；`s20:1584–1662`独立确认关闭7。`VM:12325–12343`云端字段==1回填y2，`ActivityEggSoundBindingImpl:524`绑定。KMP修正旧关闭0为7及相同valueJson，不改URL/签名/通用网络。
- 当前链：既有AppSwitchPreference（双主题）→Controller草稿/提交完整已知目标→CloudWriter稳定ID校验→16/40 Builder→既有Gateway；13→MappedWriter→forItem/forConfigId→已有62/63。直接Builder拒绝未知/错误类型，直接Writer拒绝伪造split ID。刷新保护本批在途操作，取消/VIN/代次沿原设施。结果确认只CONFIRMED_BY_RESULT，不建立车辆观察；16/40重新刷新回填configuredValue，不伪造TTP或车辆执行。
- 34/34精确Fake过滤测试、JVM/JS/Wasm/测试源码编译、Android Debug/Desktop构建及diff退出0。首次1条失败是新测试忽略已有同VIN加载去重，修正为断言去重和A→B→A代次隔离；未放宽生产校验。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无App运行/车辆验证。
- 同时只读跟踪42：`ShowInstrumentEnduranceActivity.onSwClick:58–218`在k5非空且旧开关off时只显示提示，不发送开启；k5 getter `VehicleSettingViewModel:28352–28370`→q2，`x(RemainingMileageData):31877–32030`使用formula/count/estimateMileage/prompt。独立资格链后续批次处理，不能直接等同于普通开关。


### A14：42剩余里程显示（独立复审）

- 原页`com/yadea/smartmoto/vehicle/view/ShowInstrumentEnduranceActivity.smali:getSettingConfigId:260`=42；`onSwClick:58–218`读取VM.k5，旧开关关闭且提示非空时仅显示“温馨提示/我知道了”；`:220–224`按钮回调为空，不能把确认按钮理解为允许强制开启。其他情况独立OPEN/CLOSE，`qg:1742–1762/4879–4898`→A5/A4。
- 原TTP `VehicleSettingViewModel:22040–22095`→remainingMileageDebugStatus，==1为开；页面Binding→Z0。没有已证实云端开关配置字段；资格不是开关配置。
- `jp6.h:247–329` GET bikeRealStatus/getVehRealStatus，`jp6.i:331–557`从VehicleStatusBean.soc1/soc2构造GET vehiclebase/estimateMileage/cal2，空SOC仅作为请求字符串0回退；`jp6.l:880–887`调用h/i。`jp6$d.e:131–183`成功通过b32.x传RemainingMileageData；失败/null经s提示错误。VM.s:30481只显示错误，不代表资格成立。
- `RemainingMileageData:9–18`原字段formula:Int、count:Int、estimateMileage:Double、prompt:String?。`VM.x:31877–32030`：formula1/count>0显示剩余次数，否则固定等待提示；formula2/count>0同前，否则estimate<=0固定提示、>0用prompt；其他formula用prompt。KMP逐分支保留，所需字段缺失/非法/非有限值保留UNKNOWN；没有默认primitive字段，更不由count单独推算资格。
- 最小新增RemainingMileageEligibility只承载同VIN的本次请求提示；不进入configured/vehicle/cache/draft。Gateway用现有transport、签名及headers执行上述两个已有GET；没有更改通用网络和原有路径。Controller开启时新读资格，在途禁止重复/刷新；VIN/代次改变后丢弃结果。关闭无需资格。
- 资格仅附在本次submitted Item，Controller不持久化。CloudWriter校验VIN及资格，MappedWriter/forItem要求完整资格；直接forConfigId无法绕过开启门控。原始A4/A5协议、无参数帧和编码器不变；原始协议命令本身不负责云端资格业务。UI复用双主题AppAlertDialog，提示确认只清提示。
- TTP只接受0/1；非法/缺失撤销live资格，缓存不作为LIVE。发送接受仍WAITING_READBACK；资格请求期间收到的TTP不能确认后续写入，只有写入后新鲜同VIN观察建立READBACK_MATCHED。
- CONDITIONAL：依赖云端资格响应与BLE连接。STATIC_FLOW_VERIFIED；Fake及构建结果见历史。无运行或车辆验证。


### A15：2边撑感应/3携出助力（独立复审）

- ID2 `TempleInductionActivity.onSwClick:40–173/getSettingConfigId:209`：旧on准备关闭；w2非空（confId39）、H6主用户true、F1关联锁true、VehicleDataManager.W0=true时发BRACE_CLOSE_LOCKER_CLOSE，否则SLIDE_BRACE_OFF；开启独立SLIDE_BRACE_ON。原YedeaOldSwitch回调读取旧值，方向没有推测。
- VM.w2:31727→e0，VM13420–13436明确confId39→e0；H6:8530→i，构造器3442–3462明确VehicleDataBean.isPrimaryUser→i（不是连接状态）；F1:8090→h0。`VehicleDataManager.W0:10500–10623`检查VehicleConfig父1021的child29；KMP当前设置Controller没有该车型/主用户快照，不以isAuth或是否存在普通39控件替代这两个标志。
- ID2读链：`ActivityTempleInductionBindingImpl:631/756`→VM.w5:31787→A0；VM20304–20325从sideBraceStatus==1回填。KMP已有字段，收紧0/1，非法撤销live；云端没有已证实值字段。
- 原VM20331–20540：W0分支F1=lock&&sideBrace，另一分支F1=lock。故云端本次确实无39（w2为空），或同VIN有效LIVE lock=0时，两种车型分支均选择独立10，可安全保持。39在清单中而锁未知/on、缓存、待确认目标时不构造关闭命令。qg1166–1185/5241–5260→10/0F，6199–6218→AC。当前编码器无AC且本任务禁止改帧；需要AC的分支明确BLOCKED。
- Controller每次关闭临时构造SideBraceCloseAuthorization，同VIN实际Writer和forItem再校验；直接forConfigId不能跳过关闭分支保护。没有把许可放进车辆观察、配置、草稿或后续目录快照。2/7/39开关在途互斥，防止校验后共用锁状态被另一个写入改变；不改变7/39参数映射或时间写入。
- ID3 `SeatCushionSensorActivity.onSwClick:50–108/getSettingConfigId:144`：开/关独立CARRY_OUT_OPEN/CLOSE；qg3954–3973/1310–1329→11/12。`ActivitySeatCushionSensorBindingImpl:613/754`→VM.H5:8510→w0；VM20241–20296从pushModeStatus==1填w0。KMP TtpInfo/原始解析无pushModeStatus，受保护范围不得补解析，因此读取BLOCKED，底层WRITE_ONLY；UI无可靠当前值仍禁用。不能借seatPowerStatus等名字相近字段。
- 原App两项没有已证明云端字段/请求绑定；没有新增猜测字段、云端写入或命令。既有AppSwitchPreference两主题无视觉改动。关闭错误通过现有error/message回报；接受仍WAITING_READBACK，只有2的新鲜回读可确认，3无回读不确认。
- 58/58精确Fake过滤测试通过，测试与构建实际结果见历史；2保持PARTIAL，3读取BLOCKED/底层WRITE_ONLY。未运行App，未操作车辆。

### A16：75定位器防盗/76定位开关（独立复审）

- ID75原页`vehicle/view/IotTamperProofActivity.onSwClick:50–158/getSettingConfigId:194`同时发送IOT_REMOVE_ALARM和调用VM.d9。BindingImpl613/757→VM.t4:30788→p4；VM24641–24665以TTP.iotRemoveAlarm==1回填。原`qg.smali:1925–1956`映射FA；当前编码器无对应已接通路径，本任务禁止新增帧，BLE部分BLOCKED。注意不是qg1.smali。
- 云端`VehicleSettingViewModel.P4:10157–10159,10342–10372`对非null Boolean调用booleanValue但未使用返回值，实际固定tamperProofIotEnable=1；`s20:1350–1370`同样固定1。没有证据把false转换成0；开启沿用整数1，关闭在Controller/Builder/Writer/实际Gateway均拒绝，不复制未知值，也不复现把关闭误写成1。若需恢复关闭，必须补抓包或其他可靠Smali路径证据。
- 未找到tamperProofIotEnable→原页状态的读绑定；原绑定明确来自TTP。因此移除KMP仅凭字段名建立的75 configuredValue，目录初始UNKNOWN，接入既有TtpInfo.extended.iotRemoveAlarm，严格0/1；缓存只进cachedValue，非法回读撤销live，同VIN/观察序号沿用现有机制。75云端接受后不按confId猜事务结果，仅新鲜TTP可建立READBACK_MATCHED。
- ID76原页`locationswitch/LocationSwitchActivity.onSwClick:145–218`先网络及CarSettingsIotOnlineGuard再操作，关闭有LocationSwitchCloseDialog，确认/取消分别在`$b.onClick/$a.onClick`；无SMS证据。`toggleSwitch:241–295`→VM.g9/f9:26479→P4:10301–10331，param-setting字段oper=SET_PARAM/id=POSITIONING_FUNC/value=0或1/valueJson单字节结构，无复合兄弟字段。
- `LocationSwitchActivity$toggleSwitch$1.invoke`把本次返回字符串传countdownQuery；原页114–143为30次/1000ms，`$countdownQuery$1:invoke`→VM.f1:26289→jp6.f→checkSettingStatus。本次ID来自`jp6$j.onSuccess:156–185`的响应字符串，不能用confId76代替。KMP新增专用receipt保留本次ID，缺ID保留CLOUD_ACCEPTED及UNKNOWN确认，不发猜测轮询。
- 读绑定`ActivityLocationSwitchBindingImpl237/514`→VM.y4:32245→K0；VM12635–12655从CarSettingBean.positioningFunc回填。仅作为configuredValue；没有TTP车辆观察映射。`CarSettingsIotOnlineGuard.a`→VehicleDataBean.isOnline:4674–4698→VehicleStatusBean.onlineState:Boolean；`jp6.h:247–329`的getVehRealStatus由`jp6$c`按VehicleStatusBean解码。KMP只读同VIN该端点onlineState=true作为本次提交许可，UNKNOWN/false/异常均阻止，不当作LIVE_TTP或定位配置。
- 关闭确认沿用AppAlertDialog，两主题保持共同回调；Controller直接入口也要求确认，Writer/Gateway校验同VIN许可、关闭确认和精确参数。许可不进入持久配置、观察或后续目录。实际Writer对76走原param-setting，不尝试以BLE可用性代替云端在线。
- 新TrackerSettingsSafetyTest17项，CloudConfiguredValueRefreshTest30项，CarSettingsControllerTest17项，共64/64；离线JVM/JS/Wasm/测试源码及Android/Desktop构建退出0。独立从UI→Controller→Mapper→Writer→Gateway→事务/回读重走；无原始TTP、帧、协议、通用网络签名或其他参数变更。75 PARTIAL/关闭与BLE帧BLOCKED，76 CONDITIONAL；无App运行/车辆验证。

### A19：93静音模式（独立复审及验证完成）

- 原QuietModeActivity.sendCommand:130–227分别传old_overspeed_alert=1/new_overspeed_alert=2及old=0/new=1，orderId明确为39；setListener:303起clearCheck后两按钮独立发送，没有已证实的当前值回填。ActivityQuietModeBindingImpl:265/336/543起只绑定目录/遮罩，不代表静音状态；不采用同名DTO推断读取。
- qg:6019–6045→f:487起→BleControlManager.k:2377–2470→wi.L:2691/2773→BleBicycleCommandManager.generateControlCommand(HashMap):6574起，8字节参数、old在byte1/new在byte4，使用显式order39，没有普通设置版本前缀。当前KMP的141普通分支会被版本前缀+3E包裹，属于错误发送路径。
- 本批不改受保护编码/协议：forConfigId/forItem、Controller目标校验及实际Cloud/MappedWriter阻断93；VehicleSettingCommand构造器拒绝141和命令类型别名，copy同样不能绕过。目录保留只读UNKNOWN，UI解释暂不可操作，不新增默认选中或虚假回读。
- 正确39发送及可靠当前值读取BLOCKED。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；没有RUNTIME_VERIFIED/VEHICLE_VERIFIED。测试及构建详见历史。
