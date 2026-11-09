# 车辆设置全量独立 Activity 审计

审计基线为 `F:\android app code\YadeaHook\temp_smali\com\yadea\smartmoto\vehicle`。当前 Smali 快照不含 AndroidManifest.xml，因此清单通过 Activity 类、`SettingBaseActivity` 继承、`getSettingConfigId()`、ARouter path 和调用关系交叉发现；未把 `*Activity` 文件名本身当作业务证据。

## 覆盖率

| 指标 | 数量 | 口径 |
| --- | ---: | --- |
| 扫描到的 vehicle 模块 Activity 类 | 117 | *Activity.smali 类文件 |
| 车辆设置相关 Activity | 81 | 有 getSettingConfigId()、继承 SettingBaseActivity，或属于一键距离/智能启动等明确设置子页 |
| 已完成结构与 KMP 映射审计 | 81 | 每页均给出 Smali 入口、KMP 对应和完整度 |
| 已完成本轮深入调用链审计 | 6 | 密码解锁、一键开机、开机距离/自定义距离、氛围灯入口、氛围灯配置、氛围灯开关 |
| 部分审计 | 75 | 已定位入口和 KMP 映射，但未在本轮逐条复核全部监听器/请求/回读 |
| 非车辆设置或不属于本轮范围 | 36 | 外设、家庭账号、故障检测、设备信息、OTA/升级容器等 |
| 非 Activity 独立设置页面 | 10 | Popup、Dialog、View 见下表 |
| KMP 明确未实现的范围内功能 | 7 | 自定义距离标定、单位页变体、智能启动、启动灯、多功能入口、低电量信息页、实验室入口 |
| 存在映射问题的功能 | 5 | confId 001/002/003 缺失；confId 1 分类错误；密码流程不完整；氛围灯配置回读 UNKNOWN |

## 重点专项结论

| VS | 功能 | 原 Activity/承载 | Smali 证据 | KMP 结果 | 状态 |
| --- | --- | --- | --- | --- | --- |
| VS-016 | 密码解锁 | `PassWordUnlockActivity` + 3 个 Popup | `PassWordUnlockActivity.smali:63-105,138-170`；`PassWordUnlockActivity$a.smali`；`l76.smali:9714-9725` | `cloud:25` 开关、0x80/0x81、`passwordUnlockStatus/SettingResult` 已接入；设置/修改/忘记密码流程未迁移 | PARTIAL |
| VS-017 | 一键开机 | `OneKeyOpenActivity` | `OneKeyOpenActivity.smali:345-358,390-392`；`qg.smali:4840-4859,6719-6738`；`l76.smali:9240-9247` | `cloud:1:enable`、0x0D/0x0E、`bleKeyStatus` 回填 | COMPLETE（核心开关） |
| VS-018 | 开机距离设定 | `OneKeyDistanceActivity`、`ModeGearView`、`CustomPowerOnDistancesActivity` | `ModeGearView$onSeekChangeListener$1.smali:31-75`；`BleBicycleCommandManager.smali:273-277`；`l76.smali:9270-9277` | `cloud:1:distance` 0..7 档、0x37、`bleKeyDistanceLevel` 回填；自动标定/自定义距离标定未迁移 | PARTIAL |
| VS-019 | 氛围灯模式 | `FeelLightSettingActivity` | `FeelLightSettingActivity.smali:120-310,2087-2280`；`FeelLightSettingActivity$c.smali:213-491`；`BleBicycleCommandManager.smali:7383-7775` | `cloud:73:config` 提供常亮/呼吸/闪烁/流水与流水方向，0xF7 一次下发；配置回读仍 UNKNOWN | PARTIAL |
| VS-020 | 氛围灯颜色 | `FeelLightSettingActivity`、`HueSliderView` | `FeelLightSettingActivity.colorToRGB()`；`FeelLightSettingActivity$c.smali:115-205,303-466` | 双色 RGB 选择、255→254、RGB1/RGB2 与 0xF7 编码已接入；颜色回读仍 UNKNOWN | PARTIAL |

## 非 Activity 设置页面

| 承载 | 页面/组件 | 业务 | KMP 对应 | 状态 |
| --- | --- | --- | --- | --- |
| Popup | SetNewPwdPopup | 设置新密码，含验证码与输入校验 | 未迁移 | NOT_IMPLEMENTED |
| Popup | VerifyOriPwdPopup | 验证原密码并进入修改流程 | 未迁移 | NOT_IMPLEMENTED |
| Popup | SendCodeConfirmPopup | 忘记密码验证码确认 | 未迁移 | NOT_IMPLEMENTED |
| Dialog | OneKeyOpenBottomDialog | 一键开机引导 | 未迁移 | NOT_IMPLEMENTED |
| Popup | OneKeyOpenGuidePop / OneKeyOpenGuide1Pop | 一键开机分步引导 | 未迁移 | NOT_IMPLEMENTED |
| Custom View | ModeAutoView | 自动模式与手机参数上传状态 | 仅迁移 0..7 档基础值 | PARTIAL |
| Custom View | ModeGearView | 档位距离，写 `NEARBY_BLE_UNLOCK` | cloud:1:distance | PARTIAL |
| Custom View | ModeCustomView | 跳转自定义距离标定 | 未迁移 | NOT_IMPLEMENTED |
| Custom View | LightItemView | 灯光设置列表项容器 | AppListRow/AppSwitchPreference | PARTIAL |
| Dialog/View | HueSliderView + 颜色列表 | 氛围灯 RGB 选择 | AppColorPickerDialog | PARTIAL |

## Activity 完整清单

| 原 App Activity | 类型 | confId | 业务功能 | Smali 证据 | KMP 对应 | 完整度 | 发现的问题 | 本轮修改 |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| AddFamilyCountActivity | 相关 Activity（范围外） |  | AddFamilyCountActivity | `AddFamilyCountActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| CarSettingsHelperActivity | 相关 Activity（范围外） |  | CarSettingsHelperActivity | `CarSettingsHelperActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ConfigSettingActivity | 独立设置 Activity |  | ConfigSettingActivity | `ConfigSettingActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| CustomizeKeyActivity | SettingBase Activity |  | CustomizeKeyActivity | `CustomizeKeyActivity.smali:类/Route/调用关系` | cloud:1005:short/long | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| CustomPowerOnDistancesActivity | 独立设置 Activity |  | CustomPowerOnDistancesActivity | `CustomPowerOnDistancesActivity.smali:类/Route/调用关系` | cloud:1:distance + 标定流程 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 是 |
| EnergyRecoveryActivity | SettingBase Activity |  | EnergyRecoveryActivity | `EnergyRecoveryActivity.smali:类/Route/调用关系` | cloud:17, cloud:18 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ExternalTirePressureActivity | 相关 Activity（范围外） |  | ExternalTirePressureActivity | `ExternalTirePressureActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FamilyAccountMangerActivity | 相关 Activity（范围外） |  | FamilyAccountMangerActivity | `FamilyAccountMangerActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FamilyCarSettingActivity | 相关 Activity（范围外） |  | FamilyCarSettingActivity | `FamilyCarSettingActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FamilyOpenIntelligenceActivity | 相关 Activity（范围外） |  | FamilyOpenIntelligenceActivity | `FamilyOpenIntelligenceActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FamilyPermissionActivity | 相关 Activity（范围外） |  | FamilyPermissionActivity | `FamilyPermissionActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FaultCheckActivity | 相关 Activity（范围外） |  | FaultCheckActivity | `FaultCheckActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FaultCheckDetailActivity | 相关 Activity（范围外） |  | FaultCheckDetailActivity | `FaultCheckDetailActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| FaultCheckRouterActivity | 相关 Activity（范围外） |  | FaultCheckRouterActivity | `FaultCheckRouterActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| LabExplorationActivity | 相关 Activity（范围外） |  | LabExplorationActivity | `LabExplorationActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| LoveCarMangerActivity | 相关 Activity（范围外） |  | LoveCarMangerActivity | `LoveCarMangerActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| LoveCarMangerVirtualActivity | 相关 Activity（范围外） |  | LoveCarMangerVirtualActivity | `LoveCarMangerVirtualActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| LowPowerModeInfoActivity | 独立设置 Activity |  | LowPowerModeInfoActivity | `LowPowerModeInfoActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| MsgAsyncActivity | SettingBase Activity |  | MsgAsyncActivity | `MsgAsyncActivity.smali:类/Route/调用关系` | special:meter_gauge_sync | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| MultifunctionActivity | 独立设置 Activity |  | MultifunctionActivity | `MultifunctionActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| NewTireInfoActivity | 相关 Activity（范围外） |  | NewTireInfoActivity | `NewTireInfoActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsBleKeyDetailActivity | 相关 Activity（范围外） |  | PeripheralsBleKeyDetailActivity | `PeripheralsBleKeyDetailActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsCameraActivity | 相关 Activity（范围外） |  | PeripheralsCameraActivity | `PeripheralsCameraActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsChargerActivity | 相关 Activity（范围外） |  | PeripheralsChargerActivity | `PeripheralsChargerActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsElectronicHandleLockActivity | 相关 Activity（范围外） |  | PeripheralsElectronicHandleLockActivity | `PeripheralsElectronicHandleLockActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsHomeActivity | 相关 Activity（范围外） |  | PeripheralsHomeActivity | `PeripheralsHomeActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsScanQrCodeActivity | 相关 Activity（范围外） |  | PeripheralsScanQrCodeActivity | `PeripheralsScanQrCodeActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsSelectCarActivity | 相关 Activity（范围外） |  | PeripheralsSelectCarActivity | `PeripheralsSelectCarActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| PeripheralsSmartRadarDetailActivity | 相关 Activity（范围外） |  | PeripheralsSmartRadarDetailActivity | `PeripheralsSmartRadarDetailActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| RadarActivity | 相关 Activity（范围外） |  | RadarActivity | `RadarActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ReadyPActivity | SettingBase Activity |  | ReadyPActivity | `ReadyPActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| RealTimeTirePressureActivity | 相关 Activity（范围外） |  | RealTimeTirePressureActivity | `RealTimeTirePressureActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| RetrofitIoTQRScanActivity | 相关 Activity（范围外） |  | RetrofitIoTQRScanActivity | `RetrofitIoTQRScanActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ScooterControlActivity | 相关 Activity（范围外） |  | ScooterControlActivity | `ScooterControlActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ScooterDeviceInfoActivity | 相关 Activity（范围外） |  | ScooterDeviceInfoActivity | `ScooterDeviceInfoActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| ScooterDeviceUpdateActivity | 相关 Activity（范围外） |  | ScooterDeviceUpdateActivity | `ScooterDeviceUpdateActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| SmartBootActivity | 独立设置 Activity |  | SmartBootActivity | `SmartBootActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| StartLightActivity | 独立设置 Activity |  | StartLightActivity | `StartLightActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| TemperatureUnitActivity | 独立设置 Activity |  | TemperatureUnitActivity | `TemperatureUnitActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_IMPLEMENTED | KMP 缺少完整页面/业务流程 | 否 |
| TransferAccountPermissionActivity | 相关 Activity（范围外） |  | TransferAccountPermissionActivity | `TransferAccountPermissionActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| VehicleControlActivity | 相关 Activity（范围外） |  | VehicleControlActivity | `VehicleControlActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| VehicleModelSelectColorActivity | 相关 Activity（范围外） |  | VehicleModelSelectColorActivity | `VehicleModelSelectColorActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| VirtualTrialActivity | 相关 Activity（范围外） |  | VirtualTrialActivity | `VirtualTrialActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| WallpaperSkinActivity | SettingBase Activity |  | WallpaperSkinActivity | `WallpaperSkinActivity.smali:类/Route/调用关系` | special:meter_wallpaper_skin | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| WallpaperUploadActivity | 相关 Activity（范围外） |  | WallpaperUploadActivity | `WallpaperUploadActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| WxSmartActivity | 相关 Activity（范围外） |  | WxSmartActivity | `WxSmartActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| X1VehicleSettingActivity | 相关 Activity（范围外） |  | X1VehicleSettingActivity | `X1VehicleSettingActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| YdCarSettingActivity | 相关 Activity（范围外） |  | YdCarSettingActivity | `YdCarSettingActivity.smali:类/Route/调用关系` | 未找到一一对应实现 | NOT_APPLICABLE | 不属于车辆设置写入范围 | 否 |
| X1DistanceUnitActivity | SettingBase Activity | 001 | 一键开机 | `X1DistanceUnitActivity.smali:getSettingConfigId()="001"` | CloudCarSettingMapper confId 1 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BleNearlyAutoUnlockActivity | SettingBase Activity | 002 | 边撑感应 | `BleNearlyAutoUnlockActivity.smali:getSettingConfigId()="002"` | CloudCarSettingMapper confId 2 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| X1EnergyRecoveryActivity | SettingBase Activity | 003 | 携出助力 | `X1EnergyRecoveryActivity.smali:getSettingConfigId()="003"` | CloudCarSettingMapper confId 3 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| OneKeyDistanceActivity | SettingBase Activity | 1 | 一键开机 | `OneKeyDistanceActivity.smali:getSettingConfigId()="1"` | CloudCarSettingMapper confId 1 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 是 |
| AutoOnOffActivity | SettingBase Activity | 11 | 自动开关机 | `AutoOnOffActivity.smali:getSettingConfigId()="11"` | CloudCarSettingMapper confId 11 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| VehicleAlarmActivity | SettingBase Activity | 13 | 报警音 | `VehicleAlarmActivity.smali:getSettingConfigId()="13"` | CloudCarSettingMapper confId 13 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| VehicleAlarmSensitivityActivity | SettingBase Activity | 14 | 车辆报警灵敏度 | `VehicleAlarmSensitivityActivity.smali:getSettingConfigId()="14"` | CloudCarSettingMapper confId 14 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ScheduledShutDownActivity | SettingBase Activity | 15 | 离车定时关机 | `ScheduledShutDownActivity.smali:getSettingConfigId()="15"` | CloudCarSettingMapper confId 15 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| DumpNoticeActivity | SettingBase Activity | 16 | 倾倒报警 | `DumpNoticeActivity.smali:getSettingConfigId()="16"` | CloudCarSettingMapper confId 16 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| AutomaticLightActivity | SettingBase Activity | 19 | 自动大灯 | `AutomaticLightActivity.smali:getSettingConfigId()="19"` | CloudCarSettingMapper confId 19 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| TempleInductionActivity | SettingBase Activity | 2 | 边撑感应 | `TempleInductionActivity.smali:getSettingConfigId()="2"` | CloudCarSettingMapper confId 2 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| VehicleVolumeActivity | SettingBase Activity | 20 | 车辆音量 | `VehicleVolumeActivity.smali:getSettingConfigId()="20"` | CloudCarSettingMapper confId 20 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BackLightActivity | SettingBase Activity | 21 | 伴我回家灯 | `BackLightActivity.smali:getSettingConfigId()="21"` | CloudCarSettingMapper confId 21 | PARTIAL | 既有矩阵早于 VS-007 修复；当前有 0x83/0x84/0x85 Golden Frame，但真实车辆未复测 | 否 |
| EmergencyContactActivity | SettingBase Activity | 24 | 紧急联系人 | `EmergencyContactActivity.smali:getSettingConfigId()="24"` | CloudCarSettingMapper confId 24 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| PassWordUnlockActivity | SettingBase Activity | 25 | 密码解锁 | `PassWordUnlockActivity.smali:getSettingConfigId()="25"` | CloudCarSettingMapper confId 25 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 是 |
| TurnSignalActivity | SettingBase Activity | 26 | 转向灯自动回位 | `TurnSignalActivity.smali:getSettingConfigId()="26"` | CloudCarSettingMapper confId 26 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ArmedModeActivity | SettingBase Activity | 27 | 设防模式 | `ArmedModeActivity.smali:getSettingConfigId()="27"` | CloudCarSettingMapper confId 27 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| SeatCushionSensorActivity | SettingBase Activity | 3 | 携出助力 | `SeatCushionSensorActivity.smali:getSettingConfigId()="3"` | CloudCarSettingMapper confId 3 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| DistanceUnitActivity | SettingBase Activity | 31 | 速度单位 | `DistanceUnitActivity.smali:getSettingConfigId()="31"` | CloudCarSettingMapper confId 31 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| SpeedUnitActivity | SettingBase Activity | 31 | 速度单位 | `SpeedUnitActivity.smali:getSettingConfigId()="31"` | CloudCarSettingMapper confId 31 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| DumpWarningActivity | SettingBase Activity | 32 | 倾倒提醒 | `DumpWarningActivity.smali:getSettingConfigId()="32"` | CloudCarSettingMapper confId 32 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| SimulatedSoundActivity | SettingBase Activity | 33 | 模拟声浪 | `SimulatedSoundActivity.smali:getSettingConfigId()="33"` | CloudCarSettingMapper confId 33 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| BrakePowerOffActivity | SettingBase Activity | 36 | 刹车断电 | `BrakePowerOffActivity.smali:getSettingConfigId()="36"` | CloudCarSettingMapper confId 36 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BodyStabilityActivity | SettingBase Activity | 37 | TCS 车身稳定 | `BodyStabilityActivity.smali:getSettingConfigId()="37"` | CloudCarSettingMapper confId 37 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| AntiLockActivity | SettingBase Activity | 38 | ABS 防抱死 | `AntiLockActivity.smali:getSettingConfigId()="38"` | CloudCarSettingMapper confId 38 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| BraceAutoShutDownActivity | SettingBase Activity | 39 | 边撑自动关机 | `BraceAutoShutDownActivity.smali:getSettingConfigId()="39"` | CloudCarSettingMapper confId 39 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| TopSpeedActivity | SettingBase Activity | 4 | 档位设置 | `TopSpeedActivity.smali:getSettingConfigId()="4"` | CloudCarSettingMapper confId 4 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| EggSoundActivity | SettingBase Activity | 40 | 彩蛋音效 | `EggSoundActivity.smali:getSettingConfigId()="40"` | CloudCarSettingMapper confId 40 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ShowInstrumentEnduranceActivity | SettingBase Activity | 42 | 车辆显示剩余里程 | `ShowInstrumentEnduranceActivity.smali:getSettingConfigId()="42"` | CloudCarSettingMapper confId 42 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| ReversingFunctionActivity | SettingBase Activity | 43 | 倒车模式 | `ReversingFunctionActivity.smali:getSettingConfigId()="43"` | CloudCarSettingMapper confId 43 | BROKEN | 既有矩阵标记存在映射缺口 | 否 |
| HelpPromoteActivity | SettingBase Activity | 44 | 婴儿车模式 | `HelpPromoteActivity.smali:getSettingConfigId()="44"` | CloudCarSettingMapper confId 44 | BROKEN | 既有矩阵标记存在映射缺口 | 否 |
| ConstantSpeedModeActivity | SettingBase Activity | 45 | 定速巡航 | `ConstantSpeedModeActivity.smali:getSettingConfigId()="45"` | CloudCarSettingMapper confId 45 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| SlopeDescentActivity | SettingBase Activity | 46 | 陡坡缓降 | `SlopeDescentActivity.smali:getSettingConfigId()="46"` | CloudCarSettingMapper confId 46 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ChargingPowerActivity | SettingBase Activity | 47 | 充电功率 | `ChargingPowerActivity.smali:getSettingConfigId()="47"` | CloudCarSettingMapper confId 47 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ChargeCapActivity | SettingBase Activity | 48 | 充电上限 | `ChargeCapActivity.smali:getSettingConfigId()="48"` | CloudCarSettingMapper confId 48 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| RampStopsActivity | SettingBase Activity | 49 | 坡道驻停 | `RampStopsActivity.smali:getSettingConfigId()="49"` | CloudCarSettingMapper confId 49 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| OldVehicleSoundEffectsActivity | SettingBase Activity | 5 | 车辆音效 | `OldVehicleSoundEffectsActivity.smali:getSettingConfigId()="5"` | CloudCarSettingMapper confId 5 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| GaugeStyleHandOffActivity | SettingBase Activity | 50 | 仪表风格切换 | `GaugeStyleHandOffActivity.smali:getSettingConfigId()="50"` | CloudCarSettingMapper confId 50 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BleMeterTimeSyncActivity | SettingBase Activity | 51 | 仪表时间同步 | `BleMeterTimeSyncActivity.smali:getSettingConfigId()="51"` | CloudCarSettingMapper confId 51 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BracketPoweredActivity | SettingBase Activity | 53 | 边撑开机 | `BracketPoweredActivity.smali:getSettingConfigId()="53"` | CloudCarSettingMapper confId 53 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| VoltagePlatformActivity | SettingBase Activity | 54 | 电压设置 | `VoltagePlatformActivity.smali:getSettingConfigId()="54"` | CloudCarSettingMapper confId 54 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| VehicleCustomizeActivity | SettingBase Activity | 55 | FREE 自由模式 | `VehicleCustomizeActivity.smali:getSettingConfigId()="55"` | CloudCarSettingMapper confId 55 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| MaxSpeedSettingActivity | SettingBase Activity | 58 | 档位设置 | `MaxSpeedSettingActivity.smali:getSettingConfigId()="58"` | CloudCarSettingMapper confId 58 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| ScheduledShutDown2Activity | SettingBase Activity | 59 | 报警锁 | `ScheduledShutDown2Activity.smali:getSettingConfigId()="59"` | CloudCarSettingMapper confId 59 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| CapacitySettingActivity | SettingBase Activity | 60 | 电池设置 | `CapacitySettingActivity.smali:getSettingConfigId()="60"` | CloudCarSettingMapper confId 60 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| MaxSpeedLimitActivity | SettingBase Activity | 61 | 安全限速 | `MaxSpeedLimitActivity.smali:getSettingConfigId()="61"` | CloudCarSettingMapper confId 61 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| HeatedSeatsActivity | SettingBase Activity | 62 | 座椅加热 | `HeatedSeatsActivity.smali:getSettingConfigId()="62"` | CloudCarSettingMapper confId 62 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| TPowerActivity | SettingBase Activity | 63 | 超级狂暴模式 | `TPowerActivity.smali:getSettingConfigId()="63"` | CloudCarSettingMapper confId 63 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| HelpPromotePlusActivity | SettingBase Activity | 64 | 辅助推行 | `HelpPromotePlusActivity.smali:getSettingConfigId()="64"` | CloudCarSettingMapper confId 64 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| SuddenBrakingActivity | SettingBase Activity | 68 | 急刹爆闪 | `SuddenBrakingActivity.smali:getSettingConfigId()="68"` | CloudCarSettingMapper confId 68 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| AutoShutDownActivity | SettingBase Activity | 7 | 自动落锁 | `AutoShutDownActivity.smali:getSettingConfigId()="7"` | CloudCarSettingMapper confId 7 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| GaugeSettingsActivity | SettingBase Activity | 71 | 仪表设置 | `GaugeSettingsActivity.smali:getSettingConfigId()="71"` | CloudCarSettingMapper confId 71 | COMPLETE | 本轮未发现需修改的缺口 | 否 |
| EnduranceModeActivity | SettingBase Activity | 72 | 长续航模式 | `EnduranceModeActivity.smali:getSettingConfigId()="72"` | CloudCarSettingMapper confId 72 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| FeelLightSettingActivity | SettingBase Activity | 73 | 氛围灯 | `FeelLightSettingActivity.smali:getSettingConfigId()="73"` | CloudCarSettingMapper confId 73 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 是 |
| IotTamperProofActivity | SettingBase Activity | 75 | 定位器防盗 | `IotTamperProofActivity.smali:getSettingConfigId()="75"` | CloudCarSettingMapper confId 75 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| LocationSwitchActivity | SettingBase Activity | 76 | 定位开关 | `LocationSwitchActivity.smali:getSettingConfigId()="76"` | CloudCarSettingMapper confId 76 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| HibernateModeActivity | SettingBase Activity | 77 | 休眠模式 | `HibernateModeActivity.smali:getSettingConfigId()="77"` | CloudCarSettingMapper confId 77 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| SeatPowerUpActivity | SettingBase Activity | 79 | 座垫感应上电 | `SeatPowerUpActivity.smali:getSettingConfigId()="79"` | CloudCarSettingMapper confId 79 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| LostReportModeActivity | SettingBase Activity | 8 | 一键报失 | `LostReportModeActivity.smali:getSettingConfigId()="8"` | CloudCarSettingMapper confId 8 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| OTDActivity | SettingBase Activity | 80 | OTD 开关 | `OTDActivity.smali:getSettingConfigId()="80"` | CloudCarSettingMapper confId 80 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| DelayLightActivity | SettingBase Activity | 81 | 大灯延时 | `DelayLightActivity.smali:getSettingConfigId()="81"` | CloudCarSettingMapper confId 81 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| AssistDriveLightActivity | SettingBase Activity | 84 | 安全辅助小蓝灯 | `AssistDriveLightActivity.smali:getSettingConfigId()="84"` | CloudCarSettingMapper confId 84 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| SosContactActivity | SettingBase Activity | 85 | SOS 联系人 | `SosContactActivity.smali:getSettingConfigId()="85"` | CloudCarSettingMapper confId 85 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| BrightnessAdjustmentActivity | SettingBase Activity | 86 | 仪表亮度调节 | `BrightnessAdjustmentActivity.smali:getSettingConfigId()="86"` | CloudCarSettingMapper confId 86 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| HelpPromotePlus2Activity | SettingBase Activity | 89 | 辅助骑行 | `HelpPromotePlus2Activity.smali:getSettingConfigId()="89"` | CloudCarSettingMapper confId 89 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| LowPowerActivity | 设置 Activity | 9 | 信息提醒 | `LowPowerActivity.smali:getSettingConfigId()="9"` | CloudCarSettingMapper confId 9 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| TrackModeActivity | SettingBase Activity | 90 | 赛道模式 | `TrackModeActivity.smali:getSettingConfigId()="90"` | CloudCarSettingMapper confId 90 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| QuietModeActivity | SettingBase Activity | 93 | 静音模式 | `QuietModeActivity.smali:getSettingConfigId()="93"` | CloudCarSettingMapper confId 93 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |
| GearMemoryActivity | SettingBase Activity | 94 | 档位记忆 | `GearMemoryActivity.smali:getSettingConfigId()="94"` | CloudCarSettingMapper confId 94 | PARTIAL | 入口已映射；边界值/异常/回读仍需逐项复核 | 否 |

## 后续优先级

1. P1/L3：补全 `CustomPowerOnDistancesActivity` 的 `COMMAND_CONST_CUSTOM_BLE_RANGING`、`COMMAND_CONST_BLE_RANGING_END` 与 `phoneBluetoothRecord/add` 完整链路；在完整帧和状态机证据闭环前不得开放标定写入。
2. P1/L2：迁移密码设置、修改、忘记密码和验证码流程；日志与状态模型不得保存或输出明文密码。
3. P2/L3：建立氛围灯 0xF7 的车辆回读/确认映射；当前 `atmosphereShowMode/ColorMode/RGB*` 与写帧字段的语义尚未闭环，继续 UNKNOWN。
4. P2/L2：补齐 X1 `001/002/003`、`StartLightActivity`、`SmartBootActivity` 等页面映射。
5. P3：按本表的 PARTIAL 页面逐项复核异常处理、权限、并发和车辆切换。

## Git

- 代码提交：`54effb3`。
- 本文档与第四轮报告：随文档提交。
