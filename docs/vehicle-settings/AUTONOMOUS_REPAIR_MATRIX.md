# 车辆设置自主修复功能地图

日期：2026-11-12。起始 HEAD：`329effd69e72a4cdf60d3d910dccde2ad1bce247`；分支 master。

## 证据与状态约定

Smali 根：`F:/android app code/YadeaHook/temp_smali`。KMP 根：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/`。下表由当前源文件逐方法索引生成，索引存在不代表链路完成；待逐批复核的项统一 PARTIAL。命令、字段仍需检查具体分支、调用者和合法值。COMPLETE 只指静态及 Fake 证明的必要链路，不包含运行/实车验证。

扫描了全部 getSettingConfigId 定义、设置基类继承者、七类动态 Registry、Navigation，以及当前 Mapper/Controller/命令映射。83 个数字 confId（含 X1 001/002/003），另有5个无 confId 的目录入口。A52重新枚举 Smali 中直接返回字符串的 `getSettingConfigId()`：72 个实现、70 个唯一 ID，均能在本矩阵找到；另单独发现3个 `getSettingConfigIdList()` 入口，其中雷达/低电量读取当前 BikeConfig ID，SOS 明确返回24/91。该枚举是页面入口交叉核对，不等于运行时车型全量或功能闭环证明。动态父子项单列，不把空 ID 基类计作设置。指定的 full-read-write-audit-2026-11-11.md 未找到；其他历史文档仅作检索入口。

KMP 读取代码位置为 CarSettings.kt 的 applyVehicleStatus；写入索引为 VehicleSettingCommands.kt 的 forConfigId。forItem 的分支、CloudCarSettingUpdateBuilder、独立 Gateway 另在批次证据中核对。所有项目共同受 bikeConfigVos/isAuth/izLab/isUserLabFun 门控；目录中的不适用不等于字段未实现。

## 全集索引

宿主补充（A58）：A42所述Android已实例化不等于UI已接通。MainDestinationContent→VehicleHubScreen漏传联系人/密码Controller，现显式转发同一实例，并移除内部参数默认null以防再次遗漏。ID24/85联系人管理、ID25密码管理、ID91联系人编辑的静态可达性已修复；非Android注入限制仍在。29项Fake及全部要求的离线构建通过，无App/车辆验证。

ID51证据更正（A58）：下表早先“status 5失败重试”仅指VehicleService的比较分支，不是可信错误码映射。`l76.G:336`是两位解析，`11532–11539`写入syncTimeResultStatus只可能0/1/2或-1，与比较5冲突。缺C0编码且本任务禁止修改帧编码，实际同步保持BLOCKED；不得据此分支实现未经证实的失败确认。

| confId / 项目 | 原 App 页面及 getSettingConfigId 位置 | TTP 字段（当前 KMP 消费） | BLE 命令索引 | 云端字段/实际链路与缺口 | 优先级/状态/批次 |
|---|---|---|---|---|---|
| 001 X1 距离单位 | `X1DistanceUnitActivity.getSettingConfigId:182–190` | `metricSystemStatus`，TTP 0/1 | `METRIC_SYSTEM_SETTING`/0x89、`BRITISH_SYSTEM_SETTING`/0x8A | KMP `cloud:001` Choice 只由同 VIN TTP 回填；云端条目只提供目录/权限，不设 configuredValue；BLE Writer 经 `forItem` 路由 | P1 / PARTIAL（STATIC/Fake/build；无运行验证）/ A37 |
| 002 X1 感应自动开关机 | `BleNearlyAutoUnlockActivity.getSettingConfigId:182–190` | `bleAutoTurnOnOffStatus`、`bleAutoTurnOnOffGearStatus`；开关负哨兵 UNKNOWN，原页正数中仅1为开、其他为关；档位0..2 ↔ UI 0/50/100% | `AUTO_TURN_ON_OFF_OPEN/CLOSE`、`AUTO_TURN_ON_OFF`；0x5C/0x5D/0x5E；档位只允许LIVE开关回读为开时提交 | KMP 稳定拆分 `cloud:002:enable/level`；TTP 分别回填，Controller/UI均要求同VIN有效LIVE开关已开启；目录不充当当前值 | P1 / PARTIAL（STATIC/Fake/build；无运行验证）/ A37 |
| 003 X1 制动能量回收 | `X1EnergyRecoveryActivity.getSettingConfigId:182–190` | `brakeModePowerRecoverFunctionStatus`、`brakeModePowerRecoverGearStatus`；开关负哨兵 UNKNOWN；档位1..3 ↔ UI 0/50/100% | `BRAKE_RECOVER_OPEN/CLOSE`、`BRAKE_RECOVER_LEVEL`；0x15/0x16/0x3D；档位参数为UI刻度逆映射 | KMP 稳定拆分 `cloud:003:enable/level`；TTP 分别回填，Slider显示百分比且不将目录值当作当前值 | P1 / PARTIAL（STATIC/Fake/build；无运行验证）/ A37 |
| 1 一键开机 | `OneKeyDistanceActivity.getSettingConfigId:779`；`OneKeyOpenActivity.getSettingConfigId:4224`；`ModeGearView$onSeekChangeListener$1.a:87–178` | `bleKeyDistanceLevel`、`bleKeyStatus` | `cloud:1:distance` → `NEARBY_BLE_UNLOCK`/0x37；开关0x0D/0x0E | A27修复UNKNOWN/非整数/越界穿透和帧编码钳位；TTP 3位全1(raw7)按协议INVALID保持UNKNOWN；写入只记LOCAL_UPDATED，仍未实车确认 | P0安全 / PARTIAL（普通0..7命令档已STATIC_FLOW_VERIFIED、UNIT_TESTED、BUILD_VERIFIED；自定义标定仍BLOCKED）/ A27 |
| 2 边撑感应 | `TempleInductionActivity.smali:42–153,209`；`VehicleDataManager.smali:10500–10634` | `sideBraceStatus`，严格0/1 | 开启`SLIDE_BRACE_ON`/0x0F；关闭通常`SLIDE_BRACE_OFF`/0x10；特定分支`BRACE_CLOSE_LOCKER_CLOSE`需额外原App条件 | A41复核：切换到关闭时仅当w2非空、H6/F1均为true且`W0()`为true才选特定分支；W0要求`VehicleDataBean.vehicleConfig`含父configId 1021且其child configId可解析为29。KMP未证明H6/F1及该车型分支条件，保持安全门控，不猜映射 | P0 / PARTIAL；特定车型分支BLOCKED / A15/A41（无运行/实车验证） |
| 3 携出助力 | `SeatCushionSensorActivity.smali:50–108,144`；`VehicleSettingViewModel.smali:20241+`（w0回填） | `pushModeStatus`，由`l76.smali:9503–9524`解析`0x25` bit4–5；原页raw 0/1，0开 | CARRY_OUT_OPEN/CLOSE，11/12 | A40新增TTP解析和同VIN有效LIVE回填；raw非0/1保持UNKNOWN；原BLE写入不变，接受后仍需新鲜回读确认 | P1 / PARTIAL（读取STATIC/Fake/build；无运行/实车验证） / A40 |
| 4 档位设置 | com/yadea/smartmoto/vehicle/view/TopSpeedActivity.smali:85 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 5 车辆音效 | com/yadea/smartmoto/vehicle/activity/OldVehicleSoundEffectsActivity.smali:506 | alarmRingThemeCurrent | ALARM_THEME | A39/A52复核：原页只提交ALARM_THEME_0/1；同VIN有效TTP raw 0/1回填两项Choice。UNKNOWN由显式“未知”入口进入选项面板，不预选成车辆值；最终命令校验known及0/1。无新缺口 | P2 / PARTIAL（静态映射及Fake已有验证；无运行验证）/ A39/A52 |
| 7 自动落锁 | com/yadea/smartmoto/vehicle/view/AutoShutDownActivity.smali:505 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | A39/A52：时间只在父开关为同VIN有效LIVE_TTP开启、且无父草稿/提交/在途写入时可编辑；原页`VehicleDataManager.W0`车型门控的等价KMP来源未证实，仍保留UNKNOWN/PARTIAL | P2 / PARTIAL / A39/A52 |
| 8 一键报失 | `LostReportModeActivity.getSettingConfigId:549`、`onSwClick:387`、`handleReportLossResult:156–324`；`SmsCodeVerifyViewModel$getMsgCode$1.invokeSuspend:191–237`、`$checkCode$1.invokeSuspend:198–257`、`Z0:297–411` | 云端 `VehicleStatusBean.lossState`；原 App `VehicleDataBean.isLoss:4309–4336` 以0为开 | 不走BLE；短信验证后 `common-cmd/low-freq/set-param/loss?vin=...&value=00/01` | KMP 独立 `LostModeGateway` + `LostModeController` + 诊断页；云端 `lossState` 0=开，非0=关；成功后刷新云端状态。A29收紧HTTP 2xx但缺少 `success=true` 的响应处理，避免虚报成功 | P1 / PARTIAL（云端配置/动作已接通；服务端接受不是车辆回读确认；无App/实车验证） / A29 |
| 9 信息提醒 | com/yadea/smartmoto/vehicle/view/LowPowerActivity.smali:997 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 10 电子围栏 | com/yadea/smartmoto/vehicle/activity/UserSelectRangeActivity.smali:846 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | A04：distanceOn/distance/centerCircleLon/Lat完整云端快照；半径200..2000m整数，缺伴随字段拒绝提交；只有结果确认，无车辆回读 | P0 / CONDITIONAL / A04 |
| 11 自动开关机 | com/yadea/smartmoto/vehicle/view/AutoOnOffActivity.smali:187 | bleAutoTurnOnOffGearStatus, bleAutoTurnOnOffStatus | AUTO_TURN_ON_OFF_CLOSE, AUTO_TURN_ON_OFF_OPEN, AUTO_TURN_ON_OFF | A39/A52：开关与距离分别从TTP字段回填；距离仅在父开关同VIN有效LIVE_TTP开启、且无父草稿/提交/在途写入时可编辑；无新增已证实缺口 | P2 / PARTIAL（字段/命令映射静态复核；无运行验证）/ A39/A52 |
| 13 报警音 | `VehicleAlarmActivity.smali:50–108,144`；`VehicleSettingViewModel.smali:20178+`（u0回填） | `alarmSoundSwitchAfterStatus`，由`l76.smali:9314–9321`解析`0x1A` bit0–1；原页raw 0/1，0开 | WARING_SOUND_OPEN/CLOSE→62/63 | A40新增TTP解析和同VIN有效LIVE回填；raw非0/1保持UNKNOWN；原BLE写入不变，接受后仍需新鲜回读确认；不消费未证实DTO云字段 | P1 / PARTIAL（读取STATIC/Fake/build；无运行/实车验证） / A40 |
| 14 车辆报警灵敏度 | VehicleAlarmSensitivityActivity.onSeekChangeListener；VehicleSettingViewModel TTP回填 | alarmSensitivityStatus，raw 0/1/2 ↔ UI 0/50/100 | ALARM_SENSITIVITY_GEAR→0x5F，参数0/1/2 | 当前HEAD已核实：实时TTP回填、显式刻度写入和已有回读确认；映射/Writer/过滤测试齐全 | P1 / COMPLETE（STATIC/Fake；无运行/实车验证） / A10 |
| 15 离车定时关机 | com/yadea/smartmoto/vehicle/view/ScheduledShutDownActivity.smali:197 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE开关0x7B/7C、时间0x7D=2..20min；DTO字段无页面绑定不作配置；原始0/1保持UNKNOWN | P1 / PARTIAL / A02 |
| 16 倾倒报警 | vehicle/view/DumpNoticeActivity.smali:71–148,234 | 无原页TTP证据 | 无BLE映射 | A13：dumpAlarm独立云端配置/更新，严格目标/稳定ID/在途刷新；结果确认与车辆值分离 | P1 / COMPLETE（云端；无运行/实车验证） / A13 |
| 17 能量回收开关 | vehicle/view/EnergyRecoveryActivity.smali:389–473,865–1037动态模式 | slideModePowerRecoverStatus | ENERGY_RECOVER_OPEN/CLOSE→13/14 | A12：独立BLE开关；resistanceOn仅DTO，移除猜测云端读写；严格UNKNOWN/稳定ID及新鲜回读 | P0 / CONDITIONAL / A12（无运行/实车验证） |
| 18 能量回收档位 | vehicle/view/EnergyRecoveryActivity.smali:389–473,865–1037动态模式 | slideModePowerRecoverCurrent | ENERGY_RECOVER_LEVEL→3C | A12：整数1/2/3；组合模式等待开关LIVE回读；最高档显式确认；独立BLE及新鲜回读 | P0 / CONDITIONAL / A12（无运行/实车验证） |
| 19 自动大灯 | com/yadea/smartmoto/vehicle/view/AutomaticLightActivity.smali:144 | automaticHeadlightFunctionStatus | AUTOMATIC_HEADLIGHT_CLOSE, AUTOMATIC_HEADLIGHT_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 20 车辆音量 | com/yadea/smartmoto/vehicle/view/VehicleVolumeActivity.smali:87 | alarmWarningVolumeLevel | VOLUME | A11：原页0/1/2三档；拒绝默认未知、取整钳位和越界观察；独立回读确认 | P0 / COMPLETE / A11（BLE；无运行/实车验证） |
| 21 伴我回家灯 | com/yadea/smartmoto/vehicle/view/BackLightActivity.smali:178 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=15/30/45/60秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 24 紧急联系人 | `EmergencyContactActivity.getSettingConfigId:964`；`SosContactActivity.getSettingConfigIdList:1083` | EmergencyContactsData云端`userName/userMobile/emergencyContactName/emergencyContactMobile` | 不走车辆BLE；VM.R6查询、s6保存 | KMP共用`YadeaEmergencyContactGateway` select/insert；A26修正VIN隔离、取消传播及旧请求保护。实际只有Android MainActivity注入Controller；Desktop虽有JVM transport但入口未接入且未提供Android等价deviceId/clientInfo，iOS/JS/Wasm transport为`UnavailableHttpTransport`，未伪造网络能力 | P1 / PARTIAL；iOS/JS/Wasm BLOCKED（transport缺失），Desktop PARTIAL（未注入） / A26/A42 |
| 25 密码解锁 | `PassWordUnlockActivity.getSettingConfigId:845`；`sendUnlockPasswordSetting:310–343`；`PassWordUnlockActivity$a.onNotifyDataCallBack:84–145`；`sendUnlockPasswordSetting$lambda$6:348–386`；`toggleSwitch:464–508`；`updateUI:628–821`；`l76.smali:9714–9725` | `passwordUnlockStatus`由TTP byte `0x31`解码；0/2关、1开、3特殊提示；设置结果仅原页`needReadStatus`有效期内且结果0确认 | KMP ID25通过 `VehicleSettingCommands.forItem` 映射BLE `0x80/0x81`；密码提交命令`0x82`由独立密码Controller发送 | A30移除无Smali依据的通用云字段映射、状态3保持UNKNOWN。A48限制同VIN、LIVE_TTP、序号晚于基线且结果0确认；A55窗口从BLE send返回后计2000ms，云接受、BLE提交、车辆回读分别记录，并以消息tone隔离切换开关反馈。独立密码云端操作与设备状态仍非原子整体事务，整体PARTIAL | P1 / PARTIAL / A30/A48/A55 |
| 26 转向灯自动回位 | com/yadea/smartmoto/vehicle/view/TurnSignalActivity.smali:283 | turnSignalReturnsAutomaticallyStatus | COMMAND_DIRECTION_LIGHT_RESET_OFF, COMMAND_DIRECTION_LIGHT_RESET_ON | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 27 设防模式 | com/yadea/smartmoto/vehicle/view/ArmedModeActivity.smali:231 | offModeStatus | FORTIFY_MODE_AUTO, FORTIFY_MODE_NO | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 31 速度单位 | com/yadea/smartmoto/vehicle/view/SpeedUnitActivity.smali:232<br>com/yadea/smartmoto/vehicle/view/DistanceUnitActivity.smali:156 | metricSystemStatus | IMPERIAL_UNIT_KM, IMPERIAL_UNIT_MILES | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 32 倾倒提醒 | com/yadea/smartmoto/vehicle/view/DumpWarningActivity.smali:144 | tiltWarningStatus | TILT_WARNING_CLOSE, TILT_WARNING_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 33 模拟声浪 | vehicle/view/SimulatedSoundActivity.smali:onSwClick207–297,onCheckChange128–205；VM21499–21709；Binding342/430/572 | simulatedSoundWaveStatus、ModeStatus、VolumeStatus；OnlineStatus=0开关门控 | 95/96开关已接通；97模式/98音量无既有帧 | A17：同VIN新鲜设备可用许可保护Controller/实际Writer/Mapper；模式0/1、音量1..16独立只读回填，缺失/非法不伪造；云端目录决定子项存在 | P0 / PARTIAL；模式/音量写入BLOCKED / A17（无运行/实车验证） |
| 36 刹车断电 | com/yadea/smartmoto/vehicle/view/BrakePowerOffActivity.smali:155 | brakePowerDownFunctionStatus | BRAKE_OUTAGE_CLOSE, BRAKE_OUTAGE_OPEN | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 37 TCS 车身稳定 | com/yadea/smartmoto/vehicle/view/BodyStabilityActivity.smali:154 | tcsWorkMode | TCS_BODY_STABILITY, TCS_BODY_STABILITY | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 38 ABS 防抱死 | com/yadea/smartmoto/vehicle/view/AntiLockActivity.smali:152 | absFunctionStatus | ABS_ANTI_LOCK_CLOSE, ABS_ANTI_LOCK_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 39 边撑自动关机 | com/yadea/smartmoto/vehicle/view/BraceAutoShutDownActivity.smali:376 | leaveVehicleAutoLockStatus, leaveVehicleAutoLockTimeStatus, sideBraceStatus | LEAVE_THE_CAR_AUTO_LOCK_OFF, LEAVE_THE_CAR_AUTO_LOCK_ON, LEAVE_THE_CAR_AUTO_LOCK_TIME | A39/A52：与ID7共享原App锁状态/时间命令并保持独立条目；时间要求同VIN有效LIVE_TTP父开关开启且无父草稿/提交/在途写入；原`VehicleDataManager.W0`车型条件的KMP等价来源未证实，不伪造开启状态 | P2 / PARTIAL / A39/A52 |
| 40 彩蛋音效 | vehicle/view/EggSoundActivity.smali:88–207,306 | 无原页TTP证据 | 无BLE映射 | A13：colorEggSoundEffect配置/开启，关闭CLEAR_SOUND_EFFECT=7（修正旧0），valueJson同7；严格UNKNOWN/稳定ID | P0 / COMPLETE（云端；无运行/实车验证） / A13 |
| 41 自定义音效 | `OldVehicleSoundEffectsActivity.getSettingConfigId:506`；入口转入自定义音效目录/启动音效流程 | KMP使用独立`CustomSoundController`云端目录/资源状态；无KMP BLE音效传输回读 | 自定义应用云端调用存在；原App启动音效BLE应用未接入KMP | 云端资源目录、开关、setResourceBike及结果轮询已在KMP；原App `BootSoundEffectActivity.applySound:644–940`另包含`BLE_CLEAR_SOUND`、`BleSoundHelper.k`与场景操作。KMP无这些调用；不得把云端接受当车辆应用确认 | P1 / PARTIAL；BLE应用链路BLOCKED待协议/平台能力专审 |
| 42 车辆显示剩余里程 | com/yadea/smartmoto/vehicle/view/ShowInstrumentEnduranceActivity.smali:58–218,260 | remainingMileageDebugStatus | DISPLAY_REMAINING_MILEAGE_CLOSE/OPEN，A5/A4 | A14：严格TTP开关；开启先读同VIN cal2资格，非空提示仅说明不发送；Controller→带本次资格的Item→实际Writer→命令；接受与回读分离 | P0 / CONDITIONAL / A14（无运行/实车验证） |
| 43 倒车模式 | com/yadea/smartmoto/vehicle/view/ReversingFunctionActivity.smali:144 | reversingState | REVERSING_CLOSE, REVERSING_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 44 婴儿车模式 | com/yadea/smartmoto/vehicle/view/HelpPromoteActivity.smali:144 | helpCarState | STROLLER_CLOSE, STROLLER_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 45 定速巡航 | com/yadea/smartmoto/vehicle/view/ConstantSpeedModeActivity.smali:144 | constSpeedState | CONST_SPEED_CLOSE, CONST_SPEED_OPEN | A10：严格已知开关/稳定ID/无效观察；45改用原页constSpeedState | P0 / COMPLETE / A10（BLE；无运行/实车验证） |
| 46 陡坡缓降 | com/yadea/smartmoto/vehicle/activity/SlopeDescentActivity.smali:154 | steepSlopeState | BLE_SLOP_DESCENT_CLOSE, BLE_SLOP_DESCENT_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 47 充电功率 | com/yadea/smartmoto/vehicle/view/ChargingPowerActivity.smali:628 | batteryChargingPowerStatus, batteryChargingPowerValue | COMMAND_CONST_CHARGING_POWER_SWITCH/0xC9, COMMAND_CONST_CHARGING_POWER_VALUE/0xCA | A43复核：页面200..1000W；l76.z读取0x71原始值并×50，KMP此前错误×2，A43修正。云端与LIVE_TTP分离；写入限制200..1000W且50W档 | P1 / PARTIAL（静态/Fake/构建；无运行及实车验证） / A01+A43 |
| 48 充电上限 | com/yadea/smartmoto/vehicle/view/ChargeCapActivity.smali:438 | batteryChargingLimitValue，TTP 0x70 bit3..5 | COMMAND_CONST_CHARGING_LIMIT_VALUE/0xCB | A43复核APK资源`layout_charging_cap.xml`明确SeekBar 75..100；`VehicleSettingViewModel`将云配置`chargeUlVal`限制75..100并回退75，TTP raw0/1→75、2..6→80..100。KMP保留UNKNOWN配置占位；云端接受75..100整数，BLE仅编码5%档，不把显示刻度标签误作强制步进 | P2 / PARTIAL（无运行及实车验证） / A01+A43 |
| 49 坡道驻停 | com/yadea/smartmoto/vehicle/view/RampStopsActivity.smali:144 | rampSlopeState | BLE_RAMP_SLOP_CLOSE, BLE_RAMP_SLOP_OPEN | A07：严格已知开关、稳定ID、失效观察降级；TCS0..7合法域；同VIN新鲜回读确认 | P0 / COMPLETE / A07（BLE；无运行/实车验证） |
| 50 仪表风格切换 | com/yadea/smartmoto/vehicle/activity/GaugeStyleHandOffActivity.smali:522 | panelThemeCurrent | COMMAND_BIKE_PANEL_THEME_TYPE | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 51 仪表时间同步 | `BleMeterTimeSyncActivity.onSwClick:67–187`；`onSwClick$lambda$1:190–209` 发 `ble_sync_gauge_time`；`VehicleService` 在 `syncTimeResultStatus==5` 时延迟1秒重试；`MainActivityLogic.W0:2037` 接事件、`q0:6464–6485` 取本地HHmmss并发命令；`DateUtils.getCurrentHMS:39–107`；`BleBicycleCommandManager.generateBleCommand:~1290–1368`；`qg:~2530` | `syncTimeResultStatus`/`syncTimeResult` 只作来源标记的上报回显；当前回显只接受已证实0/1，既不是偏好值也不是操作回执 | `COMMAND_CONST_TIME_SYNC_OPEN/CLOSE`→0xC1；手动/失败延迟事件→`COMMAND_CONST_TIME_SYNC`/0xC0。原帧为40字节FF后接时、分、秒各自 `decInt2hexString`；当前KMP缺0xC0命令构造及两个事件触发链 | A18保留独立 `TimeSyncStatusReport`、VIN与缓存/live来源，未知开关目标拒绝。复核发现KMP没有 `ble_sync_gauge_time` 触发器，也未建模服务重试的raw status 5。把0xC0直接合并到C1 Writer会混淆开关写入与同步结果/失败语义，需单独操作状态及触发调用链后才能安全实现 | P0 / PARTIAL；0xC0实际同步及失败重试 BLOCKED（A18/A56；无运行/实车验证） |
| 52 手动距离自定义 | `OneKeyOpenActivity.getSettingConfigId:4224`返回1；52只在4197+影响入口/埋点；独立`CustomPowerOnDistancesActivity` | 独立测距事务，无已证实TTP配置回填 | VM `c1/b1`发送测距开始/结束；Activity中断发送中断；结束后上报`phoneBluetoothRecord/add` | A31：不再将缺省bleDistance伪造为3；confId52距离子项UNKNOWN/只读，Mapper/Builder停止消费该字段；`CloudCarSettingWriter`最终边界拒绝confId52 Slider，BLE fallback/云端Gateway均不调用。测距结果与该云字段关联仍未证实 | P1 / BLOCKED（未知值覆盖与盲写风险已关闭；校准持久化/字段映射仍待证） / A31 |
| 53 边撑开机 | com/yadea/smartmoto/vehicle/view/BracketPoweredActivity.smali:146 | templeSettingStatus | COMMAND_CONST_TEMPLE_SETTING, COMMAND_CONST_TEMPLE_SETTING | A06：严格已知开关/稳定ID/失效观察降级；同VIN新鲜回读确认。36关闭二次确认；53无已证实云端配置，禁用猜测字段 | P0 / COMPLETE / A06（BLE；无运行/实车验证） |
| 54 电压设置 | com/yadea/smartmoto/vehicle/view/VoltagePlatformActivity.smali:100 | voltageSettingStatus | COMMAND_CONST_VOLTAGE_SETTING | A03：TTP回填已核；显式Choice→Controller→forItem→MappedWriter，最终边界拒绝未知/越界/伪造选项；无云端猜测 | P0 / COMPLETE / A03 |
| 55 FREE 自由模式 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity.smali:1452 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 58 档位设置 | com/yadea/smartmoto/vehicle/view/MaxSpeedSettingActivity.smali:220 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 59 报警锁 | com/yadea/smartmoto/vehicle/view/ScheduledShutDown2Activity.smali:210 | vehiclePowerDownStatus, vehiclePowerDownTimeStatus | VEHICLE_MANDATORY_POWER_DOWN_CLOSE, VEHICLE_MANDATORY_POWER_DOWN_OPEN, VEHICLE_MANDATORY_POWER_DOWN_TIME | A02：BLE同开关、0x7D原始0=30秒、1..20=分钟；UNKNOWN无默认；别名并发保护 | P1 / COMPLETE / A02 |
| 60 电池设置 | com/yadea/smartmoto/vehicle/activity/CapacitySettingActivity.smali:297 | capacitySettingStatus, capacitySwitchStatus | COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SWITCH_SETTING, COMMAND_CONST_CAPACITY_SETTING | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 61 安全限速 | com/yadea/smartmoto/vehicle/view/MaxSpeedLimitActivity.smali:610 | maximumSpeedCurrent | MAX_SPEED_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 62 座椅加热 | com/yadea/smartmoto/vehicle/activity/HeatedSeatsActivity.smali:2596 | seatHeatLevelStatus, seatHeatSwitchStatus | SEAT_HEAT_SWITCH, SEAT_HEAT_SWITCH, SEAT_HEAT_LEVEL | 已确认默认/钳位写入缺陷；原始参数复核后修复 | P0 / PARTIAL / A01 |
| 63 超级狂暴模式 | com/yadea/smartmoto/vehicle/view/TPowerActivity.smali:114 | tPowerStatus | COMMAND_CONST_T_POWER_SETTING, COMMAND_CONST_T_POWER_SETTING | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 64 辅助推行 | vehicle/view/HelpPromotePlusActivity:onCbClick94–122,onSwClick124–165；$a.a:40–70 | customDriveLevelStatus0..2、customDriveSwitchStatus==1、helpCarState0/1 | A8/A9、D0=0/1、D1=0..2，均独立提交 | A23：Controller/实际Writer/forItem/forConfigId/原始命令边界拒绝未知、伪造ID和非法目标；无效后续观察降级；保留原有连接/开机/主开关UI门控 | P0 / COMPLETE（BLE静态/Fake；无运行/实车验证） / A23 |
| 68 急刹爆闪 | com/yadea/smartmoto/vehicle/view/SuddenBrakingActivity.smali:147 | quickBrakeLightStatus | COMMAND_QUICK_BRAKE_LIGHT_SWITCH, COMMAND_QUICK_BRAKE_LIGHT_SWITCH | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 69 前向碰撞预警（FCW） | vehicle/radar/RadarBaseActivity.getSettingConfigIdList:548–658；VehicleSettingViewModel.b9:25055–25077 | frontCollisionWarning；仅原值1为开，0/2为关，全1无效 | COMMAND_FRONT_COLLISION→0x13A，参数0/1 | A24：按各自confId动态展示/授权；UNKNOWN不显示关闭态；同VIN新鲜TTP回填及回读确认；无云端值映射 | P1 / COMPLETE（BLE静态/Fake；无运行/实车验证） / A24 |
| 70 盲区监测（BSD） | vehicle/radar/RadarBaseActivity.getSettingConfigIdList:548–658；VehicleSettingViewModel.b9:25160–25183 | bsdRadar；仅原值1为开，0/2为关，全1无效 | COMMAND_RADAR_BSD→0x12E，参数0/1 | A24：仅目录含confId 70时展示并继承其权限；UNKNOWN保持未知；同VIN新鲜TTP回填及回读确认；无云端值映射 | P1 / COMPLETE（BLE静态/Fake；无运行/实车验证） / A24 |
| 78 后向碰撞预警（RCW） | vehicle/radar/RadarBaseActivity.getSettingConfigIdList:548–658；VehicleSettingViewModel.b9:25093–25115 | rcwRadarSwitch；仅原值1为开，0/2为关，全1无效 | COMMAND_RADAR_RCW→0x12D，参数0/1 | A24：仅目录含confId 78时展示并继承其权限；UNKNOWN保持未知；同VIN新鲜TTP回填及回读确认；无云端值映射 | P1 / COMPLETE（BLE静态/Fake；无运行/实车验证） / A24 |
| 82 电话告警 | 动态 Registry / 独立流程；待逐项绑定 | 无 Controller 分支；独立流程另查 | forItem / Cloud / 独立 Gateway 待核 | 当前HEAD已有统一低电量Sheet、云端字段及时间范围保护；不重复旧缺口 | P2 / PARTIAL / 基线复核 |
| 71 仪表设置 | GaugeSettingsActivity.getSettingConfigId:621；observe$2；syncCarInfo:525；VehicleSettingViewModel.Z6/Z8 | bikeInfoSyncName由CarSettingBean回填；无已证实TTP回读 | updateBikeSetting字段bikeInfoSyncName:Int(0/1)；独立刷新为protocol-lowfreq/bike-info-sync-name/refresh | A25补配置字段解析/回填、独立开关编辑与整数云端写入；配置复读分离于仪表执行确认。UNKNOWN保留原连接入口；独立立即同步按钮仍未接入 | P1 / PARTIAL（配置链路可写，仪表执行未确认） / A25 |
| 72 长续航模式 | 独立页`EnduranceModeActivity.setListener$lambda$2`；动态页`SimpleSwitchItemView.v`→`CarSettingsInlineOperationHelper.y/s` | `longEnduranceStatus`，raw 0=开 | 两条原App路径均写`LONG_ENDURANCE_SETTING`，但独立页checked→1，动态bikeConfigVos页checked→0（`s:1866`对目标值异或1）；KMP当前动态列表沿用后者 | 既有KMP动态映射checked→0、unchecked→1与动态页一致；原App独立页参数相反，跨入口一致性未证实，不得无条件统一两条路径 | P0 / PARTIAL（路径差异需保持显式；无运行/实车验证） / A32 |
| 73 氛围灯 | com/yadea/smartmoto/vehicle/view/FeelLightSettingActivity.smali:2404 | ambientStatus、show/color/operation、RGB1/2 | C3开关；F7完整模式组合 | A05：完整TTP回填/独立开关/不可变复合命令/新鲜回读比较；未知禁止默认补齐，完整配置才可编辑 | P0 / CONDITIONAL / A05 |
| 75 定位器防盗 | vehicle/view/IotTamperProofActivity.smali:50–158,194；VM.t4:30788→p4 | iotRemoveAlarm，严格0/1 | 原FA；本任务禁止新增帧；云端tamperProofIotEnable仅证实整数1 | A16：可靠TTP/缓存隔离；移除无读绑定的云端配置推断；阻止未证实0关闭；开启接受后等待新鲜TTP | P0 / PARTIAL，关闭及BLE帧BLOCKED / A16 |
| 76 定位开关 | vehicle/locationswitch/LocationSwitchActivity.smali:145–218,241–295,319 | 无已证实TTP；positioningFunc只进configuredValue | 原param-setting：POSITIONING_FUNC，0/1；返回ID轮询 | A16：同VIN云端onlineState检查、关闭确认、精确参数、实际Writer保留本次ID；缺ID不猜confId；结果成功仍非车辆回读 | P0 / CONDITIONAL / A16（无运行/实车验证） |
| 77 休眠模式 | HibernateModeActivity:onImmediately624–682,onAuto517–623,onModifyTime683–753,sendCommand1143–1333；VM.P4及f72 | sleepModeSwitch严格0/1；立即配置sleepMode；排程无TTP证据 | 133进入+云端标记联动；排程纯云端 | A20云端进入、A21蓝牙进入+sleepMode标记；A22独立autoSleepSwitch/dayCount配置/编辑/保存后重读，开/改1..30天，关闭明确0/1；同VIN在线/互斥/来源与确认分离 | P0 / CONDITIONAL / A20–A22（无运行/实车验证） |
| 79 座垫感应上电 | com/yadea/smartmoto/vehicle/view/SeatPowerUpActivity.smali:304 | seatPowerStatus | COMMAND_SEAT_POWER_UP, COMMAND_SEAT_POWER_UP | A08：63/68/79读写1开0关，72读写0开1关；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A08（BLE；无运行/实车验证） |
| 80 OTD 开关 | 独立`OTDActivity.onSwClick:68–108`；动态`SimpleSwitchItemView.v`→`CarSettingsInlineOperationHelper.y/s:1842–1846` | `otdSwitch`，独立页回填 raw 1=开 | 两路径均发`COMMAND_OTD_SWITCH`，独立页checked→0，动态bikeConfigVos页checked→1（`s`直接传目标布尔）；KMP当前动态列表沿用后者 | 既有KMP动态映射checked→1、unchecked→0与内联Smali一致；原App独立页参数相反，跨入口一致性未证实，不得无条件统一两条路径 | P0 / PARTIAL（路径差异需保持显式；无运行/实车验证） / A33 |
| 81 大灯延时 | com/yadea/smartmoto/vehicle/view/DelayLightActivity.smali:443 | withGoHomeStatus, withGoHomeTimeStatus | COMMAND_WITH_GO_HOME_CLOSE, COMMAND_WITH_GO_HOME_OPEN, COMMAND_WITH_GO_HOME_TIME_SETTING | A02：BLE0x83/84、0x85索引0..3=5/10/15/30秒；开关和时间独立 | P1 / COMPLETE / A02 |
| 84 安全辅助小蓝灯 | com/yadea/smartmoto/vehicle/view/AssistDriveLightActivity.smali:330 | blueLightSwitch | BLE_CONFIG_ASSIST_DRIVE_LIGHT, BLE_CONFIG_ASSIST_DRIVE_LIGHT | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 85 SOS 联系人 | `SosContactActivity.getSettingConfigId:1933`；`getSettingConfigIdList:1083`含24/91 | 与ID24共用EmergencyContactsData云端字段 | 不走车辆BLE；`addOwnerInfo`/`addContactInfo`→`insertData:1170`→VM.s6 | KMP ID85入口复用联系人BottomSheet和独立Gateway；UI受同一个可选EmergencyContactController控制，平台实例化/transport限制同ID24；ID91 SOS开关仍单独映射 | P1 / PARTIAL；平台支持限制见ID24 / A26/A42 |
| 86 仪表亮度调节 | com/yadea/smartmoto/vehicle/view/BrightnessAdjustmentActivity.smali:430 | brightnessAdjustSwitch, brightnessAdjustValue | BRIGHTNESS_ADJUST_SWITCH, BRIGHTNESS_ADJUST_VALUE | A11：开关与整数0..100独立；去除伪造80；UI连接/供电门控，Controller要求开关有效LIVE回读才调亮度；无已证实云端配置 | P0 / CONDITIONAL / A11（BLE；无运行/实车验证） |
| 88 自定义音效 | `OldVehicleSoundEffectsActivity`共享入口及动态目录项；与41共用音效管理流程 | 同ID41；KMP独立`CustomSoundController` | 同ID41 | 当前云端资源管理链路存在；原App启动音效应用还需设备侧BLE操作，KMP未接入；两ID是否在所有车型均同路径仍依赖动态目录门控 | P1 / PARTIAL；BLE应用链路BLOCKED待协议/平台能力专审 |
| 89 辅助骑行 | vehicle/view/HelpPromotePlus2Activity:onCbClick94–130,onSwClick132–173；$a.a | customDriveLevelStatus0..2、customDriveSwitchStatus==2、helpCarState0/1 | A8/A9、D0=0/2、D1=0..2，均独立提交 | A23：保持89与64不同的D0参数；严格UNKNOWN/稳定ID/目标范围/失效观察；缓存、云端、接受与车辆确认分离 | P0 / COMPLETE（BLE静态/Fake；无运行/实车验证） / A23 |
| 90 赛道模式 | com/yadea/smartmoto/vehicle/view/TrackModeActivity.smali:131 | trackModeStatus | TRACK_MODE, TRACK_MODE | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 91 SOS 紧急呼叫 | `SosContactActivity.getSettingConfigIdList:1083`包含24/91；`onSwClick:1222–1380`在提交前要求车主姓名/手机号及紧急联系人姓名/手机号均非空；`VehicleSettingViewModel.P4` confId=91写`sosEmergencyCall`，`sosEmergencyCall`以1回显开 | 原App从`CarSettingBean.sosEmergencyCall`回填云端配置，未发现TTP车辆状态绑定；开关经`d9`进入P4云端更新 | KMP `CarSettingsGateway.kt` `VALUE_KEYS`/`parseConfiguredValue`/Builder 映射 `sosEmergencyCall`；通用 CloudWriter 更新，刷新仍写configuredValue，不伪造vehicleValue | A50补齐原App联系人门槛：只有同VIN且车主姓名/手机号、紧急联系人姓名/手机号均有已加载值才允许开启；Controller最终入口重验，关闭不受限；ID91可直接打开联系人编辑Sheet。配置读取/写入静态闭环，运行态未验证 | P1 / PARTIAL（配置静态链路完整；联系人门槛UNIT_TESTED；无App运行/云端/车辆验证） / A50 |
| 92 自定义骑行 | com/yadea/smartmoto/vehicle/view/VehicleCustomizeActivity2.smali:1887 | customizeEnergyLevel, customizeEnergyState, customizeTcs, customizeThrottleSensitivity | CUSTOMIZE_SETTING | 当前 HEAD 已含759c05c完整性及来源隔离；本任务不重复实现，仍需回归 | P2 / PARTIAL / 基线复核 |
| 93 静音模式 | QuietModeActivity:sendCommand130–227,setListener303起,getSettingConfigId250；qg6019–6045 | 原页clearCheck，无可靠当前值读绑定 | COMMAND_DISABLE_OVERSPEED_ALERT→141，经独立order39控制路径 | A19阻断错误普通3E设置帧；Controller/实际Writer/Mapper/原始命令构造均拒绝，目录入口只读UNKNOWN；不猜云端字段 | P0 / BLOCKED；正确39发送须改受保护编码 / A19（无运行/实车验证） |
| 94 档位记忆 | com/yadea/smartmoto/vehicle/view/GearMemoryActivity.smali:298 | gearMemorySettingFeedback | COMMAND_GEAR_MEMORY, COMMAND_GEAR_MEMORY | A09：四项读写1开0关，修正三项参数反向；严格UNKNOWN/稳定ID/观察及最终参数保护 | P0 / COMPLETE / A09（BLE；无运行/实车验证） |
| 95 微信互联 | `CarSettingsFooterRegistry.buildAll:9–11`仅在`r20.j("95")`且主用户时加入；`i:615+`门控 | 本仓库规范要求微信互联隐藏；KMP `CarSettingsGateway.toItems` 对95返回null | 不接入通用Writer；未证明应作为车辆设置恢复 | 按项目约束不展示；不属于本轮可安全修复项 |
| 1005 自定义按键 | `CustomizeKeyActivity.getFunctionId:107+`、`getFunctionName:303+`、结果监听`590–604` | `l76.d`从byte`0x6C`解两个4-bit；`customFunctionLong/Short`，15哨兵被解析为-1；`qv:3025–3070`跳过-1并消费raw 0..14 | `forItem(cloud:1005:short/long)`→0x131/0x130→`VehicleSettingFrameEncoder` nibble 4–7/0–3 | `0..8`映射选项0..8；Smali显示名函数将其他有效raw显示“无定义”，选择该选项提交14。KMP保持UNKNOWN占位可编辑但不提交，校验确切条目和域；直接命令边界同步校验 | P1 / 写入边界完成，实际车辆回读仍PARTIAL / A35 |
| 66 自动校准 | `VehicleDataManager.V0:10180–10298`检查车辆配置父`1002`的子项`66`；`RangingStateHelper.autoCalibrationOnlyFlag:156–177`及`manualCalibrationOnlyFlag:694+`用该能力和其他范围状态选择校准流程；真正入口为`OneKeyOpenActivity.getSettingConfigId:4224`（confId=1），`showGuidePop:3871–3918`分流自动/手动引导 | 没有独立车辆值或TTP绑定；`MaxSpeedSettingActivity`是confId=58，代码中的`"66"`还用于统计埋点，不能作为ID66配置写入证据 | 校准属于一键开机/钥匙范围流程；本 Smali 片段未证明可将66作为`updateBikeSetting`或普通BLE设置值写入 | KMP `BLUETOOTH_KEY_IDS`将目录项66映射只读Info并禁止通用Writer；没有等价车辆校准事务。保留只读，避免把能力门控误当开关；需要完整RangingState/钥匙校准接口后再实现 | P1 / CONDITIONAL、PARTIAL；车辆校准事务BLOCKED / A51（静态审计；无运行/实车验证） |
| special:meter_wallpaper_skin 壁纸皮肤设置 | `CarSettingsMeterRegistry.buildDynamicEntrances:1–7`；仅有`VehicleDataManager.D()`车型子配置时生成；点击经`CarSettingsNavigation.m`检查仪表BLE连接、车辆开机/P档后进入`WallpaperSkinActivity`（`RouterCenter.g1`） | KMP有通用入口行，无等价跳转/壁纸管理流程 | 不是普通confId写入；这是需仪表连接的独立页面 | 独立功能缺失；实现前需审计 WallpaperSkinActivity 的资源、协议和平台能力，不能复用车辆主题图片配置猜测 | P2 / PARTIAL / A53；独立页面事务待审 |
| special:meter_gauge_sync 车辆信息同步 | `CarSettingsMeterRegistry.buildDynamicEntrances:8–11`；`VehicleDataManager.r()`非空时生成；点击`CarSettingsNavigation.k(title)`→`MsgAsyncActivity` | KMP有通用入口行，无等价目标页 | 已证实为标题透传的独立信息页，未证明是配置写入项 | 页面内容及是否有状态变更尚待审；当前不创建云端/BLE设置映射 | P3 / BLOCKED / A53 |
| special:footer_intelligence 用户智能识别 | `CarSettingsFooterRegistry.buildAll:1–8`由`g()`门控；点击`CarSettingsNavigation.f`→`RouterCenter.O`→`FamilyCarSettingActivity` | KMP只有通用入口行，无该页面导航 | 进入独立家庭车辆设置页，不是单一普通配置项 | 页面内能力需单独审计，禁止映射为单个confId | P2 / PARTIAL / A53 |
| special:footer_lab 实验室 | CarSettingsFooterRegistry$buildAll$1$5.a:60–77调用RouterCenter.a0:1610–1628→LabActivity，由Registry.h()门控 | KMP有实验室分类筛选，special点击仅显示通用只读说明 | 原App进入实验室聚合页，不是雷达页或单个写入字段 | 聚合目录与KMP分类覆盖需继续核对；A54纠正A53误读E0为该回调 | P2 / PARTIAL / A54 |
| footer_device_detail 设备详情 | `CarSettingsFooterRegistry.b`固定创建；点击回调取当前`VehicleInfoBean`并经`ARouterHelper.k0`打开车辆详情 | KMP通用入口只显示只读说明，无等价车辆详情导航 | 车辆信息详情入口，不是车辆设置读写项 | 属于车辆详情页面缺口，不新增车辆设置字段；后续检查现有车辆资料页是否可复用 | P3 / PARTIAL / A53 |

## 动态/独立流程补充

- EnergyRecoveryActivity：动态17/18；CustomizeKeyActivity：1005；RadarActivity/RadarBaseActivity：69/70/78；不是未发现的空 confId 设置。
- WallpaperSkinActivity、ReadyPActivity、MsgAsyncActivity：无独立固定 ID；分别审查壁纸皮肤、启动P档、信息同步流程。
- LowPowerModeInfoActivity、MultifunctionActivity、StartLightActivity、TemperatureUnitActivity、SmartBootActivity：需区分真实可写功能、说明页、外设流程；不得仅凭页面名添加车辆设置。
- ID41/88 使用 CustomSoundGateway/Controller，不能按通用命令缺失标记为未实现。
- ID51 syncTimeResultStatus 已被排除车辆实际值确认；没有关联事务证据时不建立确认。

## 批次证据

### A43：ID47/48 充电设置读取映射复核与 ID47 修正

- 开始HEAD：`473a6f44b437812e82fd4c4bf76b4a690fe08438`，分支`master`；tracked/index原先干净。保留所有预先存在的untracked文件；未暂存或清理。
- ID47证据：`ChargingPowerActivity.getSettingConfigId:628–635`为47；APK `res/layout/layout_charging_power.xml`声明`isb_min=200`、`isb_max=1000`；`VehicleSettingViewModel.smali:23388–23433`将TTP字段原值回填LiveData；`l76.smali:10299–10308`从TTP `0x71`取原始值后乘寄存器`v7=0x32`（50）写入`batteryChargingPowerValue`。云端字段`chargeWattVal`在VM配置读取分支与KMP Mapper独立映射；原始TTP状态通过`CarSettings.kt`进入vehicleValue。KMP解析器原错误乘2，导致功率实际档位转换错误并在200..1000校验时被丢弃；A43改为乘50。
- ID47写入不变：原页监听器把当前Slider整数作为目标；VM以`CHARGE_WATT_VAL`写云端参数。KMP保持已证实200..1000/50W约束，命令`0xCA`只在可表示参数范围内构造；云端配置、草稿、提交目标与LIVE_TTP仍分离。
- ID48证据：`ChargeCapActivity.getSettingConfigId:438–445`为48；APK `res/layout/layout_charging_cap.xml`声明`isb_min=75`、`isb_max=100`及6个tick label，`charge_cap_array`仅端点文字为75%/100%。`ChargeCapActivity$onSeekChangeListener$1.b:119–124`保存整数seek进度，停止拖动分支把`mCurrentProgress`交给VM；`VehicleSettingViewModel.smali:12394–12427`读取`chargeUlVal`并限定75..100，非法时回退75（KMP不采信该回退为已知值）；`23454–23570`的TTP raw0/1→75、raw2..6→80..100。`VehicleSettingViewModel.smali:10672–10706`以`CHARGE_UL_VAL`提交整数。KMP云端可提交75..100整数；原`qg.smali`及最终命令约束的BLE路径为70..100、5%档，因此非5倍数不路由BLE；保留现有范围，不擅自扩大到70。
- Fake解析测试覆盖TTP raw4→200W、raw10→500W、raw20→1000W、raw0xFF→UNKNOWN哨兵；ID48未发现需改生产逻辑的证据差异。
- 验证结果及提交记录见`AUTONOMOUS_REPAIR_HISTORY.md`的A43条目；无真实云端、BLE、车辆或App运行验证。

### A44：ID60/62 电池容量与座椅加热链路复核

| ID | 原 App 证据 | 当前 KMP 链路 | 结论与缺口 |
|---|---|---|---|
|60|`CapacitySettingActivity.getSettingConfigId:297`返回60；`VehicleSettingViewModel.smali:12282–12290`将`CarSettingBean.capacity`回填，`CarSettingBean.smali:59`声明该字段；`l76.smali:11619–11635`解码`capacitySettingStatus`和`capacitySwitchStatus`；VM `21305–21368`回填两项TTP；`s20.smali:1222、1254`构造`BATT_BD_SETTING`、`BATT_V_A_CAP_MODEL`。|Gateway将`capacity`映射到configuredValue及13/15/22/25/32/38Ah档位；`CarSettings.kt:1415–1425`仅从TTP写vehicleValue；Controller沿用同VIN、新鲜LIVE_TTP、观察序号和回读匹配确认；BatterySettings、CloudBuilder及VehicleSettingCommands校验配置与命令。|即时容量配置读写/车辆回读链路`STATIC_FLOW_VERIFIED`。原`CarSettingBean`没有容量开关字段，KMP不推测云端开关映射；开关只由TTP回填，未知时保持UNKNOWN。未做运行或车辆验证。|
|62|`HeatedSeatsActivity.getSettingConfigId:2596`返回62；`l76.smali:11140–11156`解析`seatHeatSwitchStatus`、`seatHeatLevelStatus`；VM `21201–21275、23887–24022`回填开关与原始0/1/2档；`s20.smali:1045–1064`及VM更新分支构造`seatHeating`、`seatHeatingLevel`、`SEAT_HEATING_LEVEL`；`qg.smali:1244–1263`映射命令。该Activity另有任务增删改流程。|`CarSettings.kt:1312–1323`将TTP分别回填到开关和档位；`BatterySettings`限制档位0..2；CloudBuilder与VehicleSettingCommands分别构造云端和BLE写入；Controller仅在新鲜同VIN回读匹配后确认。KMP没有加热任务CRUD/排程。|即时开关/档位`STATIC_FLOW_VERIFIED`；原页面整体`PARTIAL`，缺独立加热任务流程。本次未找到`seatHeatingLevel`对应的原App云端读取绑定证据，不将KMP字段映射视为原App读取证据。未做运行或车辆验证。|

A44 Fake复核覆盖TTP容量/开关/档位映射、缓存与LIVE隔离、VIN/序号拒绝、非法值撤销LIVE、精确命令/云端目标及写入后等待新回读；结果见`AUTONOMOUS_REPAIR_HISTORY.md`。无生产代码改动。

### A45：ID62 座椅加热配置来源与 UNKNOWN 编辑保护

| 项目 | 复核结果 |
|---|---|
| Smali证据 | `vehicle/viewmodel/VehicleSettingViewModel.smali:21201–21275、23887–24022`从TTP回填`seatHeatSwitchStatus`、`seatHeatLevelStatus`；`s20.smali:1045–1112`构造开关/档位更新字段`seatHeating`、`seatHeatingLevel`及参数`SEAT_HEATING`/`SEAT_HEATING_LEVEL`；此写入证据不证明同名字段用于`selectBikeSettingByVin`读取回填。 |
| 修正 | KMP `CloudCarSettingMapper`不再将DTO中的`seatHeating`、`seatHeatingLevel`映射成configuredValue；confId 62仍由有效TTP分别回填开关/档位，并保留已证实的云端/BLE写入构建。初始未知Toggle不再以未选中的开关呈现，未知或非法档位显示禁用“未知”；Controller的草稿和直接提交入口均要求ID62存在有效已知配置/车辆/缓存值。 |
| 状态 | 即时开关/档位保留`PARTIAL`：KMP读取以TTP为依据，写入参数有Smali证据；没有找到原App加热任务CRUD在KMP的对应实现，且同名DTO字段的原App配置读取绑定尚未证实。不得将ID62标为COMPLETE。 |

A45验证与精确提交记录见`AUTONOMOUS_REPAIR_HISTORY.md`；无真实App运行、云端写入、BLE或车辆验证。

### A46：ID62 独立座椅加热排程 CRUD

| 项目 | 审计与修复 |
|---|---|
| 原App证据 | `HeatedSeatsActivity.getSettingConfigId:2596`=62。`HeatedSeatsActivity.showTaskPop:2453–2557`限制最多3项；`insertTask:1124–1259`发送`vin`、`workTime`、`izRepeat`，仅重复任务发送`weekNumberList`；`editTask:391–461`额外带`id`。`HeatedSeatsHelper.<clinit>:105–189`确认周值`0=周日,1..6=周一至周六`；`HeatedSeatsAdapter:219–245`明确`status=0`映射开关开启，Activity点击监听`719–742`用户打开时提交0、关闭时提交1。VM `q6/n6/p6/o6/r6`对应CRUD/启停，路径常量见`p4$b:90–106`。列表字段来自`HeatedSeatSelectData`，为`id/vin/workTime/izRepeat/weekNumbers/status`。|
| KMP调用链 | ID62动态行附加定时入口 → `HeatedSeatTasksSheet` → `CarSettingsController`校验当前VIN、云端已加载、confId 62和写权限 → `CarSettingsGateway` → `YadeaCarSettingsGateway`签名HTTP GET/POST。新增/更新字段、整数星期数组、单次省略周列表均有Fake请求形状测试。任务列表状态与车辆TTP/即时加热状态分离。|
| UNKNOWN与失败保护 | 空任务仅在响应明确给出`data`数组时成立；任务时间、重复值、周值和VIN非法时拒绝解析。创建时间/重复方式由用户明确选择；取消异常继续传播，普通传输异常显示未确认。写入响应缺少`success=true`时不报成功。VIN切换期间旧请求不被标为当前车辆成功。|
| 状态与限制 | 排程CRUD为独立云端记录，并非`seatHeating`开关/档位配置，也无车辆执行回读；服务端接受仅标记为排程请求接受。UI按原App最多3个时段门控。ID62页面仍为`PARTIAL`：即时加热配置读取绑定仍未证实，且排程执行未获车辆确认。验证仅Fake与构建，无运行/实车验证。|

A46完整字段证据、测试、构建与提交见`AUTONOMOUS_REPAIR_HISTORY.md`。

### A47：ID41/88 自定义音效云端与车辆侧应用链路审计

- 原App证据：`OldVehicleSoundEffectsActivity.getSettingConfigId:506`为41入口。`BootSoundEffectActivity.applySound:644–651`先检查`BleSoundHelper.j()`；BLE分支`732–840`向`VehiclePlugin`发`BLE_CLEAR_SOUND`、随后调用`BleSoundHelper.k(sonConfId, resourceId)`；`851–940`另调用`BootSoundEffectViewModel.o1/k1`处理场景设置。不同勾选分支`668–729`会走`l1(type="4", resourceId="-1", ...)`，故不能把云端与设备侧路径合并成单一步骤。`BootSoundEffectModel.setResourceBikeResource:335–430`使用`resourcesBike/setResourceBike`，参数含`type/resourceId/parentConfId/parentConfName/sonConfId/sonConfName`，结果回调和轮询另有流程。
- KMP证据：动态ID41/88入口在`CarSettingsScreen.kt`打开同一个自定义音效Sheet；`CustomSoundController`管理请求代次、VIN、草稿和云端接受状态。`YadeaCarSettingsGateway.loadCustomSoundState`读取资源、配置目录、车辆资源映射及次数；`setCustomSoundEnabled/applyCustomSound`调用云端协议与`resourcesBike/setResourceBike`并轮询；资源新增/删除/重命名使用各自Gateway路径。`CarSettingsGateway.kt`与`CustomSoundGateway.kt`没有`BleCustomSoundControl`/BLE Writer调用。KMP云端应用成功不会生成车辆侧确认。
- 结论：云端目录/资源管理路径为PARTIAL；原App BLE设备侧应用、场景命令以及云端/设备两支的协调没有在KMP闭环。A49已确认`BleCustomSoundControl`是专用分片状态机，不是普通设置帧：服务UUID `f000ffc0-0451-4000-b000-000000000000`、特征UUID `f000ffc2-0451-4000-b000-000000000000`；有MTU/通知前置条件、握手/元数据、256字节定长音频块、末块`0xFF`填充、应答驱动续传和失败/重试状态。原App `BleSoundService.s`接收本地`uploadFilePath`，`BleSoundService$setConnectStatus$1$1$a.a`在设备握手后通过`BleSoundHelper.c`以`FileInputStream`读取整份文件字节，再调用`BleCustomSoundControl.r`。KMP目前没有此音效传输调用链；仅Android具备底层通用GATT原语，JVM/iOS/JS/Wasm使用`UnsupportedBleAdapter`。KMP尚无本地音频文件访问与车辆目录资源选择之间的已验证接入，也无专用状态机及跨平台支持，保持PARTIAL/BLOCKED，不新增协议或平台旁路。
- 验证：只读静态审计；未改业务代码，未运行测试或构建，未运行App、云端、BLE或车辆操作。等级为STATIC_FLOW_VERIFIED（所列静态分支/调用），整体PARTIAL/BLOCKED。

### A24：69/70/78 雷达开关（独立复审及验证完成）

- `vehicle/radar/RadarBaseActivity.getSettingConfigIdList:548–658`分别收集三个配置项的confId；`VehicleSettingViewModel.j1:27878–27900`对69/70/78逐项识别。此前KMP把三个开关都挂在69上，导致车型目录和`isAuth`门控不准确；现改为每个真实confId独立生成稳定条目，保留各自分组/排序/权限。
- `VehicleSettingViewModel.b9:25055–25077`将`frontCollisionWarning == 1`回填FCW；`25093–25115`将`rcwRadarSwitch == 1`回填RCW；`25160–25183`将`bsdRadar == 1`回填BSD。KMP复用已有TTP字段，但忠实采用相等于1才开：有效原始0/2回填关；2-bit全1值由既有`Ttp.G`解析为-1，保持UNKNOWN。缓存不冒充LIVE；写入接受与新鲜同VIN TTP确认分离。
- `RadarActivity$e/$c/$d`分别构造`COMMAND_FRONT_COLLISION`、`COMMAND_RADAR_RCW`、`COMMAND_RADAR_BSD`，参数为显式0/1；`qg.smali:2759–2870,3197–3218`映射0x12D/0x12E/0x13A。KMP以稳定itemId+confId绑定命令，Controller、`forItem`、实际Writer均拒绝错配/未知目标；`forConfigId`不提供绕过路径。移除了原残留的错误通用confId 69命令映射。
- 同页`RadarActivity$a.a`另有`COMMAND_RADAR_ALARM`，使用`mCurrentPosition`作为参数；`qg.smali:7118–7135`映射0x12F。A38复核确认`RadarActivity.refreshSeekbarStatus`只实现启用门控并在禁用时将UI进度设为0，`VehicleSettingViewModel`分别回填`fcwRadarMode`到独立LiveData；现有Smali证据没有证明该TTP字段与告警滑杆参数相同，也未证实滑杆完整取值范围/选项语义。因此保持BLOCKED，不新增字段映射、参数或写入入口。

- `RadarSettingsSafetyTest`最终7/7通过，命令`:shared:jvmTest --tests top.ghhccghk.yadea.carsetting.RadarSettingsSafetyTest --offline --console=plain`退出0。初次测试误把TTP全1哨兵3当开启；根据Smali的`==1`及解析器哨兵规则修正为raw1开、raw0/2关、raw3 UNKNOWN，并新增哨兵回归用例。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0。Fake-only，无真实云端、BLE或车辆操作。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

### A38：雷达告警滑杆参数证据复核

- `vehicle/radar/RadarActivity$a.smali`：监听回调读取`seekParams.e`并保存到`RadarActivity.mCurrentPosition`；提交回调在`e5()`或`F2()`任一为`true`时，构造`CommandBean("COMMAND_RADAR_ALARM", mCurrentPosition)`，延迟参数为3000ms。
- `qg.smali:7118–7135`：`COMMAND_RADAR_ALARM`分支通过`qg.e`使用命令ID `0x12F`。
- `VehicleSettingViewModel.smali:25082+`：TTP `fcwRadarMode`被单独写入LiveData；未见该值被绑定到`RadarActivity.mCurrentPosition`的证据。禁用时SeekBar显示进度0属于UI行为，不能证明0是有效业务档位或车辆当前值。
- 结论：参数域、刻度/枚举含义、告警滑杆的可信TTP回填均未证实。A38不改代码，保持BLOCKED；不运行测试/构建，不做真实网络或车辆操作。

### A25：71 仪表同步配置

- 见A25提交`3bf4645616b18f93e4db058e94d2797796d57597`及历史记录；云端配置读取/写入和复读已完成，仪表执行仍PARTIAL。

### A26：24/85 联系人跨 VIN 状态隔离

- `EmergencyContactActivity.getSettingConfigId:964`为24；`SosContactActivity.getSettingConfigId:1933`为85，`getSettingConfigIdList:1083`包含24/91。`VehicleSettingViewModel.R6:16868`请求`vehiclebase/bikeEmergencyContact/select`并以VIN查询；`Y3`提供联系人数据。
- 两页的插入构造`vin/name/mobile/type`并调用`VehicleSettingViewModel.s6:30618`至`vehiclebase/bikeEmergencyContact/insert`；Owner callback传"1"，Emergency callback传"2"。这是云端联系人，不是BLE车辆命令。
- KMP `CarSettingsScreen.kt:423–433,1353–1438`让24/85复用同一联系人BottomSheet；Android `MainActivity.kt:300`注入Gateway和当前VIN。A26修复Controller旧VIN数据暂留/误提交、取消吞噬和旧请求写回。其他平台尚未注入联系人Gateway，保持PARTIAL。
- Fake过滤测试`EmergencyContactValidationTest` 8/8、`YadeaEmergencyContactGatewayTest` 1/1；共享JVM/JS/Wasm/测试源码编译及Android/Desktop构建退出0。无真实网络、App运行或车辆验证。

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

### A20：77立即休眠云端流程（独立复审及验证完成）

- 原HibernateModeActivity.getSettingConfigId:1475=77；onImmediately:624–682先isOnline，l4=true时只展示exitPop（282–313），否则immediatelyHibernatePop（323–361）确认；$immediatelyHibernatePop$1.invoke:70起进入IMMEDIATELY_HIBERNATE。退出不是开关false写入。Binding/VM.l4:28503–28521→C4；VM12788–12806从CarSettingBean.sleepMode==1，VM24677–24704从TTP.sleepModeSwitch==1回填；本批严格接受0/1，非法值撤销live权威。
- sendCommand:1143–1333：已连接且进入确认p2=true时独立BLE133=1，再d9(77,p4=1)写sleepMode=1；未连接时g9读取立即休眠控件旧状态。VM.P4:10208–10280对旧Boolean取反构造SLEEP_MODE_SWITCH/value/valueJson，p4>0才额外带sleepMode=1。KMP接收的是目标，旧重复取反与混入元数据已修正：云端目标1且没有sleepMode字段；不实现原页不存在的直接退出0。
- $sendCommand$2.invoke:62起把本次返回字符串交countdownQuery；原Activity217起30次/1000ms，$countdownQuery$1:invoke→VM.f1。KMP专用receipt保留本次ID；缺失时CLOUD_ACCEPTED/UNKNOWN，不以77猜轮询。云端接受/结果确认均不建立车辆值；ID77结果成功后仍保留待回读关联，新鲜TTP匹配/冲突独立反馈并解除提交目标；新配置刷新也不再被旧提交盖住。
- 原页isOnline→VehicleDataBean.isOnline→VehicleStatusBean.onlineState，沿用原getVehRealStatus端点作同VIN本次在线许可，不放入配置或TTP。Controller所有通用提交入口都不能越过进入确认，真实CloudWriter和Gateway再次验证稳定ID/许可/精确字段。未知不保存；取消传播、在途刷新禁止、VIN/代次隔离及草稿保留复用原设施。
- 本批不放行不完整的连接分支；MappedWriter/forItem/forConfigId不能只发133替代双步骤。UI解释当前只支持车联网流程，AppAlertDialog保留双主题；未改编码、协议、原始TTP或通用网络。自动休眠是f72的vehicle-base/bike-sleep-appointment/get/update、autoSleepSwitch/dayCount独立流程，下一批处理；不是此Boolean，也不是当前车辆休眠状态。
- 整体PARTIAL，云端进入在在线/明确配置与确认条件下CONDITIONAL。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

### A21：77立即休眠蓝牙联动（独立复审及验证完成）

- 原HibernateModeActivity.sendCommand1143–1220：连接且确认进入先sendVehicleSetting(SLEEP_MODE_SWITCH,1)，再VM.d9(77,p4=1)；VM.P4:10208–10280在无旧Boolean时仅构造vin/sleepMode=1；c9:25564–25638→jp6.o:977–1035→vehiclebase/bike-setting/updateBikeSetting。不是重复发云端PARAM命令，也没有本次结果ID轮询。
- qg:1559–1595把SLEEP_MODE_SWITCH映射133并走普通e；BleBicycleCommandManager.generateBleCommand:674起，v5=6，133跳转sswitch_3e6:1845，uj.c(6,1,6,7)。uj.c:152–265先填FF、反序位域、补到1E，结果后缀FF7F+24字节FF，与既有编码178一致；新增Golden断言证明相同，未修改编码或协议。原页只证明进入1，原始命令入口拒绝0/2/负数。
- 实际MainScreen286–296的CloudWriter接MappedWriter专用BLE阶段；普通write/forItem不能绕过联动。稳定ID、已知目标1、同VIN在线和确认仍在Controller/Writer校验。BLE接受后独立保存云端标记；第二步失败/异常/取消保留LOCAL_UPDATED/独立metadata状态及错误，不假回滚已发送事实，不重复发送；取消传播原异常。Boolean写入不把部分接受当整体成功。
- 配置、车辆观察、草稿和两阶段receipt分别保存；车辆确认只依赖原有同VIN/新序号回读。新鲜不匹配回读仍独立反馈；标记成功不伪装车辆确认。UI复用原AppAlertDialog/双主题控件，开放完整联动、显示云端标记结果；无新增BLE、网络路径、TTP解析或平台层。
- 75/75过滤测试和JVM/JS/Wasm/测试源码、Android Debug/Desktop构建退出0；详见历史。ID77立即进入CONDITIONAL，整体因自动排程未实现仍PARTIAL；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

### A22：77自动休眠排程（独立复审及验证完成）

- f72.a:55–128 GET vehicle-base/bike-sleep-appointment/get(vin)；AutoHibernateData:9/14为nullable Integer autoSleepSwitch/dayCount，getter201/212。VM.A1:6233→$getAutoHibernateSwitch$1.a:51–65→z1:32357/D4；HibernateModeActivity.refreshMask:754–1139分别读取启用与天数，只有启用且立即休眠未生效时允许改时间。独立排程不是sleepMode或sleepModeSwitch，不建立车辆当前休眠观察。
- onAuto:517–623先isOnline；旧false打开OpenAutoHibernatePop，旧true传明确dayCount=1。OpenAutoHibernatePop$b.onClick:100–153整数范围1..30，错误文案“最少填写1天，最多填写30天”；$onAuto$1.invoke:68及$onModifyTime$1.invoke分别进入AUTO_HIBERNATE/UPDATE_HIBERNATE_TIME。sendCommand:1220–1278按启用旧值构造0/1、修改时间固定1；updateAutoHibernateInfo:1439→f72.d:225–307同时POST整数autoSleepSwitch/dayCount/vin。$updateAutoHibernateInfo$1.invoke:61–82成功后调用A1重新读取，没有事务ID轮询或TTP确认。
- KMP增加nullable AutoHibernate kind与明确编辑动作，复用现有CarSettingValueState；独立Gateway GET进入configuredValue，子项cloud:77:auto仅随目录77出现，继承权限/分组。Controller直调也检查旧开关、立即休眠有效来源/遮罩、在线许可、VIN/代次、在途兄弟互斥；时间未知不默填，关闭1是原始明确参数而不是UNKNOWN默认值。
- 目标同时携带两个有依据的整数，Writer直接调用独立Gateway，不走通用Builder/PARAM或BLE。合法范围/稳定ID/许可在实际Writer/Gateway校验；成功后重读云端配置，失败/不一致保留接受状态及错误，不建立车辆确认；无效重读不能抹去已接受目标。草稿、旧请求、VIN切换及取消均隔离。
- UI独立AutoHibernateSheet复用AppBottomSheet、AppSwitchPreference、AppTextField、AppButton/AppOutlinedButton及局部滚动。空天数不产生默认草稿，确认才提交；未知/只读/立即休眠遮罩及在途状态禁用。共享commonMain实现，两主题共同回调；没有修改全局主题或Sheet。
- 89/89 Fake-only过滤及JVM/JS/Wasm/测试源码、Android Debug/Desktop构建退出0；git diff检查退出0。独立复审重走原页/回调→目录→UI→Controller→实际Writer/Gateway→配置重读，没有协议/帧/TTP解析/网络签名/其他设置参数变更。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；ID77 CONDITIONAL，车辆排程实际执行及UI视觉交互未验证，无RUNTIME_VERIFIED/VEHICLE_VERIFIED。

## A36：ID20 三档音量回归夹具（本批验证完成）

- Smali复核：`VehicleVolumeActivity.getSettingConfigId:87–95`返回20；`setListener:108–143`从ViewModel值初始化`thumbPosition`并连接SeekBar；`VehicleVolumeActivity$a.b:86–104`把SeekBar `seekParams.e`记为thumbPosition，`$a.a:40–62`将该值原样作为`VOLUME`参数发送。`hp6.m:671–715`将0/1/2分别映射“低/中/高”，其他值无标签；`VehicleSettingViewModel:21717+`回填同样只接受0/1/2，TTP解析哨兵-1保持未定义。原页资源范围及三刻度证据见A11。
- KMP生产映射已限定为0..2；`CarSettingsScreen`用户拖动会将编辑目标标记`valueKnown=true`。本批发现两份较早测试把音量扩为3..7，并且Fake编辑没有模拟真实UI的known标记，导致测试拒绝目标，而非生产映射故障。
- 仅修正`EighthRoundValueStateTest.kt`和`SixthRoundActivityBatchTest.kt`：有效读数/提交值使用0..2，3..6与-1明确断言UNKNOWN；测试用户编辑时设置`valueKnown=true`。没有生产代码或命令映射修改。
- 验证：精准7项Fake测试通过；共享JVM/JS/Wasm/测试源码编译通过；Android Debug/Desktop离线构建通过；`git diff --check`通过。首次精准运行5/7通过，另2项因Fake未标记显式用户选择为known而失败；修正夹具后7/7通过，未删除断言或放宽校验。
- 状态：ID20原有实现STATIC_FLOW_VERIFIED、UNIT_TESTED、BUILD_VERIFIED；本批为回归夹具维护。未运行App或真实车辆，未标记RUNTIME_VERIFIED/VEHICLE_VERIFIED。精确提交`e5b9b968055a7841f1612ff06f10cfc0a3a2ff06`。

## A37：X1 设置 001/002/003 TTP 与三档 UI 映射

- 起始 HEAD：`f93c4dce1d11627fb2dc678b2e80d62b7347b84f`，`master`；tracked/index 原先干净，保留已有未跟踪内容。
- Smali：`X1DistanceUnitActivity.getSettingConfigId:182–190` 返回001，选项命令为 `METRIC_SYSTEM_SETTING`/`BRITISH_SYSTEM_SETTING`；`BleNearlyAutoUnlockActivity.getSettingConfigId:182–190` 返回002，`X1EnergyRecoveryActivity.getSettingConfigId:182–190` 返回003。`X1VehicleSettingViewModel.z1:1554–1845` 消费字段：002开关raw==1为开、非负其他值为关，负值不回填；002档位0/1/2映射滑块0/50/100；003开关仅raw==1为开、负值不回填；003档位1/2/3映射滑块0/50/100。`BleNearlyAutoUnlockActivity$a.a/b`发送滑块原始档位0..2；`X1EnergyRecoveryActivity$a.a/b`发送`thumbPosition+1`（1..3）。TTP位字段由`l76.smali`解析。
- 修改前差异：KMP已将五个值回填到稳定条目，但002/003把TTP档位raw直接呈现为“档”，与原 App 百分比滑块不一致；命令映射依赖取整/钳位；002还缺少原页“只有自动开启后才提交档位”的门控。
- 修复：新增`X1GearMapping`严格互逆转换；TTP raw映射到UI `0/50/100%`，云端目录生成的两个Slider都保持 `valueKnown=false`、0..100范围及两步间隔；最终命令入口只接受已知的精确0/50/100刻度并转换回原始BLE参数。002 UI与Controller草稿/直接更新入口均要求同VIN有效LIVE_TTP开关回读为开。非法/越界/UNKNOWN仍不生成命令。云端目录不伪装configuredValue。
- Fake测试：`FifthRoundX1MappingTest`覆盖条目拆分和未知初始值、两种滑块全部 raw/UI 方向映射、非法原始值与非刻度拒绝、命令类型/ID/原始参数、Controller的VIN/live来源回填及configuredValue隔离、负哨兵/非法值UNKNOWN，以及002开关UNKNOWN/关闭/开启时两个Controller入口的门控。
- 验证：过滤测试7/7；`:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0。均为离线/Fake；无App运行、真实网络、BLE或车辆验证。
- 状态：`STATIC_FLOW_VERIFIED`、`UNIT_TESTED`、`TEST_SOURCE_COMPILED`、`BUILD_VERIFIED`；运行态整体`PARTIAL`，无`RUNTIME_VERIFIED`/`VEHICLE_VERIFIED`。提交：`4e20f45`（`fix: complete X1 vehicle setting mappings`）。

### A55：ID25 密码云端写入与车辆确认协调

- Smali复核：`PassWordUnlockActivity.sendUnlockPasswordSetting:310–343`先调用`sendVehicleSetting`，再置`needReadStatus=true`并安排`0x7d0`ms延时；`PassWordUnlockActivity$a.onNotifyDataCallBack:84–145`仅在该门控有效且`passwordUnlockSettingResult==0`时确认成功。
- 修复：KMP将2000ms单调时钟窗口起点改为BLE发送返回后；仅同VIN、基线之后的新鲜LIVE_TTP、窗口内结果0可确认。云端接受、BLE发送和车辆确认继续独立。新增消息tone，防止密码提交状态影响独立启用开关的成功/失败颜色。
- 验证：`PasswordFlowSafetyTest` 17/17 Fake-only通过；共享JVM/JS/Wasm及测试源码编译、Android Debug、Desktop离线构建退出0；`git diff --check`通过。未运行App、真实云端、BLE或车辆操作。
- 状态：`STATIC_FLOW_VERIFIED`、`UNIT_TESTED`、`TEST_SOURCE_COMPILED`、`BUILD_VERIFIED`；ID25整体`PARTIAL`，未标记`RUNTIME_VERIFIED`或`VEHICLE_VERIFIED`。实现提交`9cdf943`。
