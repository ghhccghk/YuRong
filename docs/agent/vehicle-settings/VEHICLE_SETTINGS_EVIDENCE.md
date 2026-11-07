# 车辆设置证据索引

所有 Smali 路径均相对于 `F:\android app code\YadeaHook\temp_smali`。KMP 路径均相对于 `F:\android app code\toupin`。

## 1. 清单、分组、排序、权限

| 主题 | 证据 |
| --- | --- |
| 清单来源 | `com/yadea/smartmoto/vehicle/peripherals/common/BasePeripheralsModel$getBikeConfigs$1.smali:214` 含 `vehiclebase/bike-setting/selectBikeSettingByVin` |
| 响应模型 | `com/yadea/smartmoto/common/bean/CarSettingBean.smali:23` 为 `bikeConfigVos:Ljava/util/List;` |
| 配置字段 | `com/yadea/smartmoto/common/bean/CarSettingBean$BikeConfig.smali:20-50` 含 `confId/confName/copy/briefCopy/newCopy/parentId/parentName/sort/isAuth/isIntelligence/isUserLabFun/izLab/copyType/copyStatus/icon` |
| KMP 解析 | `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt:154-170`、`586-605` |
| KMP 分组 | `CarSettingsGateway.kt:1216-1256`，`parentId/parentName` 优先，实验室由 `isUserLabFun || izLab == 0` 判定 |
| 原权限/可用性 | `com/yadea/smartmoto/vehicle/carsetting/action/CarSettingsItemAvailabilityHelper.smali:1064-1558`，有 `ItemAccess`、`BlockReason`、入口/开关分别判定 |
| KMP 权限 | `CarSettingsGateway.kt:698-703`；只有 `isAuth=false` 明确禁写，缺失按未禁止 |

## 2. 云端值和写入字段

| 证据 | 说明 |
| --- | --- |
| `y00.smali:580-585` | `y00.a()` 是 `@FormUrlEncoded @POST("vehiclebase/bike-setting/updateBikeSetting")`，返回 `YApiResult<Boolean>`。 |
| `y00.smali:336-341` | `y00.H()` 是 `@FormUrlEncoded @POST("low-freq/common-cmd/param-setting")`，返回 `YApiResult<String>`；没有名为 `updateBikeSettingParams` 的 Retrofit 路径。 |
| `MixRequest.smali:357-500` `g()/h()` | `g()` 调用 `s20.g()` 生成参数，`h()` 注入当前 VIN，再由 `MixRequest$updateBikeSetting$1` 调用 `y00.a()`。 |
| `MixRequest.smali:504-649` `j()/k()` | `j()` 同样使用 `s20.g()`；`k()` 注入当前 VIN，再由 `MixRequest$updateBikeSettingParams$1` 调用 `y00.H()`。方法名 `updateBikeSettingParams` 不等于 HTTP 路径。 |
| `jp6.smali:977-1038,1041-1110` | `jp6.o()` 用 `f84.b()` 把 `HashMap<String,Object>` 交给 `updateBikeSetting`；`jp6.p()` 使用同一参数 JSON 交给 `low-freq/common-cmd/param-setting`。 |
| `f84.smali:20-52` | `_gateway` 由 `f84.a()` 加入；`f84.b()` 使用 Gson 序列化原对象类型，因此数字参数不能无依据统一转成字符串。 |
| `s20.smali:864-1925` `f()` | 已确认分支：47 `CHARGE_WATT_SWITCH/CHARGE_WATT_VAL`，48 `CHARGE_UL_VAL`，60 `BATT_BD_SETTING/BATT_V_A_CAP_MODEL`，61 `HIGHEST_SPEED_LIMIT`，62 `SEAT_HEATING/SEAT_HEATING_LEVEL`，75 `tamperProofIotEnable`，76 `POSITIONING_FUNC`，82 `lowBatTelWarning`，91 `sosEmergencyCall` 等。 |
| `op6.smali:39-120,279-363` | `single` 生成单元素 `VehicleParamBean(sort=1, byteCount=1, value, dataType=1)`；`capacity` 生成四元素数组，sort/value/dataType 见 `VehicleParamValue`。 |
| `VehicleParamBean.smali:7-24,69-90` | 字段声明和构造顺序为 `byteCount/dataType/sort/value`，KMP `valueJson` 按该顺序编码。 |
| `CarSettingsGateway.kt:56-217,281-292,662-761` | KMP 已区分两个路径，对 47/48/60/61/62/76 生成已确认 `id/value/valueJson`，并保留值的 Number/String 类型。 |
| `CloudCarSettingsApiSemanticsTest.kt` | 使用 fake transport 离线解密网关签名，验证 POST 路径、字段和 `valueJson`；测试源码已编译但本轮未执行。 |

### BLE 写入确认边界

| 证据 | 语义 |
| --- | --- |
| `CarSettingBaseItemView.smali:977-1070` `i()` | 封装 `CommandTempBean` 后调用 `VehiclePlugin.h(CommandTempBean)`；该接口返回 `void`，没有命令 ACK 或车辆生效结果。 |
| `CarSettingsInlineOperationHelper.smali:1048-1125` | BLE 在线分支只提交 `COMMAND_CONST_CHARGING_POWER_STATE`，随后进入本地 UI/埋点流程，没有 `onSuccess/onFailure/onTimeout` 车辆回调。 |
| `CarSettingsInlineOperationHelper$performSwitch$1.smali:64-92` | `onCommitUi` 仅提交目标 UI 值，不代表车辆确认。 |
| `CarSettingsInlineOperationHelper.smali:1128-1211` | IoT 分支才注册 `CarSettingsIotPollingHelper$a`，以查询结果区分 success/failure/timeout。 |
| `CarSettingsIotPollingHelper$startPoll$1$1.smali:64-235` | 查询成功后才同步本地状态并调用 success；失败立即回滚。 |
| `TtpInfo` / `TtpInfo.extended` | 后续车辆状态快照可对支持回读的条目提供实际值匹配确认。 |

因此 BLE `sink.send()==true` 只能证明本地发送成功，必须标记 `LOCAL_UPDATED`；不能标记 `VEHICLE_CONFIRMED`。
### 状态查询与轮询

| 证据 | 语义 |
| --- | --- |
| `y00.smali:179-218` `E()` | `@GET("common-cmd/low-freq/info/param")`，QueryMap 返回 `YApiResult<CheckReportLossBean>`。 |
| `CheckReportLossBean.smali:7-65` | `result/value/vin` 都是 String，没有枚举类或常量定义。 |
| `MixRequest$checkSettingStatus$1.smali:207-239` | 参数 Map 经 `f84.a()` 注入 `_gateway=True`，请求字段为 `_gateway/id`。 |
| `MixRequest$checkSettingStatus$3.smali:72-142` | 只读取 `ObserveResult.data.result`：解析为 `0` 回调 true；非零、空值或不可解析回调 false；data/result 缺失时不回调。 |
| `MixRequest$checkSettingStatus$2.smali:72-112` | 网络/请求错误回调 false。 |
| `CarSettingsIotPollingHelper.smali:h()` | 使用 `30` 次、`1000ms` 的 countdown；每个回调终止 Job。 |
| `CarSettingsIotPollingHelper$startPoll$1$1.smali:64-235` | true 调 `onSuccess`，false 调 `onFailure`；`startPoll$2` 在倒计时结束调 `onTimeout`。 |

因此 KMP `Boolean?` 映射固定为：`true=result==0`，`false=非零/不可解析/请求失败`，`null=data.result 缺失且继续等待`。
尚未确认：`VehicleSettingViewModel.P4()` 中与主字段一同提交的当前状态字段是否必须逐项保留；`YApiResult<String>` 的业务返回值、超时和重试语义不能从 HTTP 成功推断。
## 3. BLE 命令与帧

| 证据 | 说明 |
| --- | --- |
| `com/yadea/blecontrol/data/BleBicycleCommandManager.smali` | 原始命令帧生成器，包含 `5944`、`4B4A` 外层帧、`uj.c` 位域函数和 commandId 分支 |
| `VehicleSettingCommands.kt:88-664` | confId 到 commandType/commandId/parameter 的 KMP 映射 |
| `VehicleSettingCommands.kt:666-731` | 拆分条目 ID 分流：60、69、1005、64/89 adaptive |
| `VehicleSettingFrameEncoder.kt:30-241` | 已映射 commandId 的固定后缀、位域和参数范围 |
| `CarSettingsInlineOperationHelper.smali:186-1930` | 原设置行内开关的 commandType 与 confId 分流 |
| `CarSettingsNavigation.smali` | 41/88 共用自定义音效导航，77 为特殊入口 |

### 已确认命令样例

- `2`：0x0F/0x10，边撑感应。
- `3`：0x11/0x12，携出助力。
- `17`：0x13/0x14，能量回收开关。
- `32`：0xA0/0xA1，倾倒提醒。
- `33`：0x95/0x96，模拟声浪。
- `38`：0x99/0x9A，ABS。
- `45`：0xAA/0xAB，定速巡航。
- `47`：0xC9 开关、0xCA 瓦数。
- `62`：0xD2 开关、0xD3 档位。
- `69`：0x13A/0x12D/0x12E，FCW/RCW/BSD。
- `86`：0x135 开关、0x134 亮度值。
- `1005`：0x131 短按、0x130 长按。

### BLE Golden Frame 证据

| 范围 | 证据 | 状态 |
| --- | --- | --- |
| 外层帧 | `BleBicycleCommandManager.smali:5147-5208`：`5944 + 3E + payloadLen + payload + SUM8 + 4B4A`；`getCheckResult():125-189` 为 SUM8。 | COMPLETE |
| 版本/绑定前缀 | `generateBleCommand():674-721` 使用运行时四个版本字段；KMP 保持已确认的 `01000101C8` 静态样本。 | 静态样本 |
| 0x0F/0x10/0x11/0x12/0x13/0x14 | `pswitch_data_f62` 映射到 `:4974-4985` 的固定 suffix。 | COMPLETE golden |
| 0x36 | `:4630-4660`：`FFFFFFFF + %02x(parameter) + 20*FF`。 | COMPLETE golden |
| 0x53/0x54 | `:3884-3895` 固定 suffix。 | COMPLETE golden |
| 0x62/0x63 | `sparse-switch:5473-5475` → `:3655-3665` 固定 suffix。 | COMPLETE golden |
| 0xD2/0xD3 | `:2456-2484` 调用 `uj.c(0x35, value, 4..5/6..7)`；`uj.c` 见 `uj.smali:152-264`。 | COMPLETE golden |
| 其他参数/位域命令 | 尚未逐条导出原分支参数范围、位域、字节序和完整 hex。 | UNKNOWN |
| 0x141 | 不在 `generateBleCommand` 的 packed/sparse switch；位于 `generateControlCommand(I,HashMap,String)` `:6574-6750`，使用 `uj.o/uj.g` 另一套编码。 | UNKNOWN / 不修改 |

`VehicleSettingGoldenFrameTest.kt` 固化了上述已确认样本。未取得完整证据的命令没有生成猜测帧，也没有修改 `VehicleSettingCommands`/`VehicleSettingFrameEncoder`。
## 4. TTP 回读字段

| confId | KMP 回填字段 | 原始字段来源 |
| --- | --- | --- |
| 2 | `extended.sideBraceStatus` | `TtpInfo.kt` `0x25 bit0-1` |
| 17/18 | `brakeModePowerRecoverFunctionStatus/GearStatus` | `0x26/0x27` |
| 19 | `automaticHeadlightFunctionStatus` | `0x34 bit0-1` |
| 26 | `turnSignalReturnsAutomaticallyStatus` | `0x33 bit6-7` |
| 27 | `offModeStatus` | `0x1D bit0-1` |
| 31 | `metricSystemStatus` | `0x41 bit3-4` |
| 32 | `tiltWarningStatus` | `0x1A bit4-5` |
| 33 | `simulatedSoundWaveStatus` | `0x6D bit0-1` |
| 36 | `brakePowerDownFunctionStatus` | `0xA3 bit2-3` |
| 37 | `tcsWorkMode` | `0xAA bit0-2`，0 开、1..7 关 |
| 38 | `absFunctionStatus` | `0x6D bit4-5` |
| 42 | `remainingMileageDebugStatus` | `0x35 bit0-1` |
| 43/44 | `reversingState/helpCarState` | `0x6E bit2-3/bit4-5` |
| 45 | `cruiseControl` | TtpInfo 主字段 |
| 46/49 | `steepSlopeState/rampSlopeState` | TtpExtendedInfo |
| 47 | `batteryChargingPowerStatus/Value` | `0x70 bit4-5`、powerRaw*2 |
| 48 | `batteryChargingLimitValue` | `0x70 bit3-5` |
| 53 | `templeSettingStatus` | TtpExtendedInfo |
| 54 | `voltageSettingStatus` | TtpExtendedInfo |
| 55/92 | `customizeThrottleSensitivity/EnergyLevel/EnergyState/Tcs` | TtpExtendedInfo |
| 60 | `capacitySwitchStatus/capacitySettingStatus` | `0x23 bit6-7` / capacity field |
| 62 | `seatHeatSwitchStatus/LevelStatus` | `0x75 bit2-3/bit4-5` |
| 63 | `tPowerStatus` | `0x76 bit2-3` |
| 64/89 | `helpCarState/customDriveSwitchStatus/customDriveLevelStatus` | `0x6E/0x76/0x79` |
| 68 | `quickBrakeLightStatus` | `0x88 bit2-3` |
| 72 | `longEnduranceStatus` | TtpExtendedInfo |
| 73 | `ambientStatus` | `0x23 bit0-1` |
| 77 | `sleepModeSwitch` | TtpExtendedInfo |
| 79 | `seatPowerStatus` | TtpExtendedInfo |
| 80 | `otdSwitch` | TtpExtendedInfo |
| 81 | `withGoHomeStatus/TimeStatus` | `0x1B bit3-4/bit5-6` |
| 84 | `blueLightSwitch` | TtpExtendedInfo |
| 86 | `brightnessAdjustValue/Switch` | `0x84`、`0x1D bit3-4` |
| 90 | `trackModeStatus` | TtpExtendedInfo |
| 94 | `gearMemorySettingFeedback` | TtpExtendedInfo |
| 1005 | `smartKeysLongClickStatus/ShortClickStatus` 或 `customFunctionLong/Short` | `0x6C bit0-3/bit2-5` |

## 5. 原始 Activity 入口抽样

- `DumpNoticeActivity.getSettingConfigId()` → `16`
- `DumpWarningActivity.getSettingConfigId()` → `32`
- `BackLightActivity.getSettingConfigId()` → `21`
- `RampStopsActivity.getSettingConfigId()` → `49`
- `SeatCushionSensorActivity.getSettingConfigId()` → `3`
- `BleMeterTimeSyncActivity.getSettingConfigId()` → `51`
- `SuddenBrakingActivity.getSettingConfigId()` → `68`
- `IotTamperProofActivity.getSettingConfigId()` → `75`
- `LocationSwitchActivity` → `76`
- `AssistDriveLightActivity.getSettingConfigId()` → `84`
- `GearMemoryActivity.getSettingConfigId()` → `94`

## 6. KMP 代码位置

- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`：控制器、乐观更新、TTP 回填、权限入口。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`：云端模型、字段映射、分组、排序、拆分条目。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`：BLE 命令映射与 writer。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingFrameEncoder.kt`：BLE 帧编码。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/TtpInfo.kt`：TTP 字段和位域解析。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`：云端驱动 UI、拆分控件、确认弹窗、试听和联系人入口。
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt:273-304,419-431`：writer/controller 构造、VIN 刷新和 TTP 更新。
- `androidApp/src/main/kotlin/top/ghhccghk/yadea/AndroidVehicleSettingCommandSink.kt`：Android BLE 命令 sink。