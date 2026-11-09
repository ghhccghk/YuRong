# 车辆设置第四轮专项修复报告

## 1. 执行边界

- 原始行为依据：`F:\android app code\YadeaHook\temp_smali`。
- 本轮仅修改 VS-016 至 VS-020 涉及的车辆设置模型、映射、命令编码和双主题语义 UI。
- 数字车钥匙、OTA、Wi-Fi、投屏、车辆控制协议、已修复的 VS-013/014/015 均未修改。
- 未执行 `:shared:jvmTest`，未进行真实登录、云端写入、BLE 写入、车辆设置写入或 OTA。
- 新增测试源码仅编译，未执行。

## 2. 五项专项结果

### VS-016 密码解锁 — PARTIAL

**原 App 证据**

- `PassWordUnlockActivity.smali`：`getSettingConfigId()` 返回 `25`。
- `sendUnlockPasswordSetting(I)` 发送 `BLE_CONFIG_COMMAND_PASSWORD_SETTING`；`toggleSwitch()` 在当前状态为 1 时发送 `BLE_CONFIG_COMMAND_PASSWORD_UNLOCK`，否则发送 `COMMAND_PASSWORD_UNLOCK_OPEN`。
- `qg.smali:5583-5602`、`1010-1029`、`3158-3177` 分别确认 0x80、0x81、0x82。
- `l76.smali:9714-9725`：`passwordUnlockStatus = G(0x31, bit2)`，`passwordUnlockSettingResult = G(0x31, bit4)`。
- `PassWordUnlockActivity.updateUI()` 只把状态 `1` 作为开关开启；`passwordUnlockSettingResult == 0` 只证明密码设置结果成功，不等于车辆确认开关状态。

**KMP 修改**

- `cloud:25` 使用 TTP `passwordUnlockStatus == 1` 回填。
- 开关开启写 0x80，关闭写 0x81。
- 新增 `passwordUnlockStatus/passwordUnlockSettingResult` 解析。
- 未记录、输出或持久化任何明文密码。

**未完成**

- `SetNewPwdPopup`、`VerifyOriPwdPopup`、`SendCodeConfirmPopup` 的设置、修改、忘记密码和验证码流程尚未迁移。
- 0x82 的密码值转换只确认了命令 ID，未把敏感输入接入 KMP。
- 因此不把 VS-016 标记 COMPLETE。

### VS-017 一键开机 — COMPLETE（核心开关）

**原 App 证据**

- `OneKeyOpenActivity.getSettingConfigId()` 返回 `1`。
- `onSwClick()` 根据开关状态发送 `NEAR_UNLOCK_OPEN` / `NEAR_UNLOCK_CLOSE`。
- `qg.smali:4840-4859`、`6719-6738` 确认 commandId 0x0D / 0x0E。
- `BleBicycleCommandManager.generateBleCommand()` 中 13/14 只改变版本/绑定前缀的高 2 位，默认 suffix 为 25 字节 `FF`。
- `l76.smali:9240-9247`：`bleKeyStatus = G(0x15, bit0)`；原页面仅把 `1` 视为开启。

**KMP 修改**

- 移除 confId 1 的数字车钥匙错误分类。
- confId 1 拆为稳定条目 `cloud:1:enable` 与 `cloud:1:distance`。
- 一键开机开关使用 0x0D/0x0E，并用 `bleKeyStatus` 回填。

### VS-018 开机距离设定 — PARTIAL

**原 App 证据**

- `OneKeyDistanceActivity.getSettingConfigId()` 同样返回 `1`。
- `ModeGearView$onSeekChangeListener$1` 使用 `thumbPosition - 1` 发送 `NEARBY_BLE_UNLOCK`。
- `ch.smali:859-861` / `qg.smali:2642-2661` 映射到 commandId 55（0x37）。
- `BleBicycleCommandManager.smali:273-277`：参数钳制 0..7，suffix 首字节为 `parameter + 0xF8`。
- `l76.smali:9270-9277`：`bleKeyDistanceLevel = C(0x16, bit0)`，取值 0..7。

**KMP 修改**

- 新增 `cloud:1:distance`，单位明确为“档”，范围 0..7，不再猜测为米。
- 写入 0x37，按 `bleKeyDistanceLevel` 回填。

**未完成**

- `CustomPowerOnDistancesActivity` 的自动标定、`COMMAND_CONST_CUSTOM_BLE_RANGING`、`COMMAND_CONST_BLE_RANGING_END` 和 `phoneBluetoothRecord/add` 状态机未迁移。
- `ModeAutoView` 的手机参数上传状态未迁移。
- 因此 VS-018 保持 PARTIAL，未开放标定写入。

### VS-019 氛围灯模式选择 — PARTIAL

**原 App 证据**

- `FeelLightSettingActivity.getSettingConfigId()` 返回 `73`。
- 模式 ID 0/1/2/3 映射常亮/呼吸/闪烁/流水；流水模式另有 0..5 的运行方向。
- `FeelLightSettingActivity$c.smali:213-491` 把模式、颜色和运行方向组合成 `ATMOSPHERE_CONFIG`。
- `qg.smali:2225-2244` 映射 commandId 0xF7。
- `BleBicycleCommandManager.generateMultiBleCommand(247,...)` 明确给出 9 个参数的位置和位域。

**KMP 修改**

- confId 73 保留原有氛围灯开关，追加稳定条目 `cloud:73:config`。
- 双主题页面提供常亮/呼吸/闪烁/流水和流水方向选择。
- 保存时一次性生成 0xF7 多参数命令。
- 保留 `LOCAL_UPDATED/CLOUD_ACCEPTED/VEHICLE_CONFIRMED/FAILED/UNKNOWN` 状态语义。

**未完成**

- 当前 `atmosphereShowMode/atmosphereColorMode/atmosphereOperationMode` 的状态报文位置与 0xF7 写帧位置尚未形成闭环证据。
- 因此配置值不伪造为 `VEHICLE_CONFIRMED`，读取初值保持 UNKNOWN。

### VS-020 氛围灯颜色选择 — PARTIAL

**原 App 证据**

- `FeelLightSettingActivity.colorToRGB(I)[I]` 把 Android Color 拆成 R/G/B 三个整数。
- `FeelLightSettingActivity$c` 支持单色/双色，两组 RGB 分别写入 `ATMOSPHERE_RGB1_*` 与 `ATMOSPHERE_RGB2_*`。
- 原发送前把 RGB 分量中的 255 改为 254；0xFF 在状态报文中表示无效。
- 0xF7 将 RGB1 写到 payload 62..64，将 RGB2 写到 66..68。

**KMP 修改**

- 使用现有 `AppColorPickerDialog`，保持 Miuix 与 Material 3 双主题。
- 内部模型使用 RGB Int，不把 Compose Color 整数直接写入车辆协议。
- 支持单色/双色、颜色 A/B，并在命令映射层执行 255→254。
- 0xF7 Golden Frame 测试覆盖 RGB 字节位置。

**未完成**

- 颜色配置回读仍为 UNKNOWN，不把本地草稿伪装为车辆已确认。

## 3. 修改文件

- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingFrameEncoder.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/TtpInfo.kt`
- `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/FourthRoundVehicleSettingsTest.kt`

## 4. 测试源码

`FourthRoundVehicleSettingsTest.kt` 覆盖：

- 0x0D/0x0E/0x37/0x80/0xF7 Golden Frame。
- confId 1、25、73 的稳定 ID 和命令映射。
- RGB 255→254 与流水运行方向。
- `bleKeyStatus`、`bleKeyDistanceLevel`、`passwordUnlockStatus`、`passwordUnlockSettingResult` 的位域解析。
- 云端 confId 1 拆分和 confId 73 配置入口。

测试源码已编译，但 `jvmTest` 未执行。

## 5. 验证结果

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs`：通过。
- `:shared:compileTestKotlinJvm`：通过。
- `:androidApp:assembleDebug :desktopApp:build`：通过。
- 真实云端：未验证。
- 真实 BLE/车辆：未验证。
- 真机回读：本轮未验证；此前用户实测结论不被本轮改动重置。

## 6. Git 提交

- 代码提交：`54effb3`（VS-016 至 VS-020 共享模型、命令、编码器、UI 与测试源码；这些修改跨多个共享文件，为避免部分索引破坏编译采用一个代码提交）。
- 全量 Activity 审计文档：本报告提交中一并记录。
