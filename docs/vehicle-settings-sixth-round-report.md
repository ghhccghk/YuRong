# 车辆设置第六轮复核与审计报告

## 1. Git 基线

开始时实际状态：

- 分支：`master`
- HEAD：`e4d7f5f docs(vehicle-settings): finalize fifth-round report`
- staged/unstaged：无
- 第五轮提交 `be87730`、`bc0ff4a`、`afae7c1`、`09796ef`、`b5d0226` 均存在且完整。
- 未跟踪的 APK、日志、jadx 输出、截图目录和用户文档未触碰。

## 2. 第五轮代码复核结果

### 2.1 密码解锁

复核发现并修复三类问题：

1. **旧 VIN 异步响应污染**：`runCatching` 会吞掉协程取消，VIN 切换后旧响应可能覆盖新状态。现改为 `requestCatching`，取消异常继续抛出；所有响应写入前检查 VIN。
2. **状态混用**：云端接受、BLE 已提交和车辆确认此前都显示为 `CLOUD_ACCEPTED`。现使用 `cloudAccepted`、`bleSent`、`writeState` 分离：
   - `CLOUD_ACCEPTED`：仅云端成功。
   - `LOCAL_UPDATED`：云端成功且 BLE sink 已接收。
   - `VEHICLE_CONFIRMED`：仅在 `bleSent=true` 且 `passwordUnlockSettingResult==0` 后进入。
3. **页面/车辆切换清理**：新增 `onVinChanged()`，取消旧任务、清空验证码和密码流程状态。

### 2.2 X1 001/002/003

- `001/002/003` 与 `1/2/3` 保持字符串独立。
- 云端缺失 X1 配置时不再生成可写通用条目；002/003 仍保留开关和档位展示，但全部只读。
- TTP 回填继续绑定当前 VIN；旧 VIN 状态由既有 `CarSettingsController` 代次和 VIN 校验阻断。
- 命令 ID 未改变：001→0x89/0x8A，002→0x5C/0x5D/0x5E，003→0x15/0x16/0x3D。

### 2.3 氛围灯状态模型

修复了本地保存后将 `Atmosphere.valueKnown` 设为 true 的问题。现在：

- 本地选择草稿：可编辑。
- 云端请求状态：当前 0xF7 只走 BLE，无独立云端确认。
- BLE 发送状态：sink 成功只进入 `LOCAL_UPDATED`。
- 车辆确认：当前没有可靠确认链。
- 车辆回读：保持 `UNKNOWN`。

0xF7 写帧与 `atmosphere*` TTP 字段位置仍不一致，未修改任何未知协议字段。

## 3. 密码解锁完整链路证据表

| 步骤 | 原 App 证据 | KMP 对应 | 状态 |
| --- | --- | --- | --- |
| 读取密码/开关状态 | `VehicleSettingViewModel.S4()` → `unlock/password/enable/get`；`S6()` → `unlock/password/exists` | `getEnableState()` / `exists()` | READBACK_VERIFIED |
| 设置密码 | `SetNewPwdPopup.d0()` → `I8()` → `unlock/password/set` | `set()` | WRITE_VERIFIED |
| 修改密码 | `VerifyOriPwdPopup.V()` → `o9()` → `unlock/password/verify`；通过后 `SetNewPwdPopup` → `unlock/password/set` | `verify()` / `set()` | WRITE_VERIFIED |
| 忘记密码 | `SendCodeConfirmPopup` → `e7()` 发码；`VerifyCodePopup` → `e1()` 验证；`SetNewPwdPopup` status=3 → `d7()` reset | `sendSmsCode()` / `verifySmsCode()` / `reset()` | WRITE_VERIFIED |
| 密码校验 | `am4.a()`：4 位数字；拒绝全重复、连续递增、连续递减 | `isValidPassword()` | VERIFIED |
| 密码编码 | `am4.b()`：`:: + AES/ECB/PKCS5Padding/Base64`，key 来自 `AesUtils` | `Crypto.aesEcbPkcs5Encrypt()` + `::` | VERIFIED |
| 密码参数 | `vv5.i()` 将 4 位数字转 Int；0x82 使用 `%04x` | `toIntOrNull()` + `hex4()` | VERIFIED |
| 车辆结果 | `PassWordUnlockActivity$a`：`passwordUnlockSettingResult == 0` | `onVehiclePasswordResult()` | READBACK_VERIFIED |
| 敏感信息 | Popup 仅在内存中持有；日志只记录 path | 不记录密码/验证码/validCode | VERIFIED |

## 4. 0x82 帧编码证据表

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| 命令 ID | `qg.smali:3158-3177`、`ch.smali` 映射 `COMMAND_PASSWORD_UNLOCK_SETTING=130` | 0x82 已确认 |
| 发送条件 | `PassWordUnlockActivity$showUpdatePwdDialog$3$1`：云端 set/reset 回调成功后才调用 `sendUnlockPasswordSetting` | 云端成功后发送 |
| 参数 | `vv5.i(password)`；`BleBicycleCommandManager.smali:570-574` 限制 0..9999，`%04x` | 数字密码转 4 hex 位 |
| Payload | `generateBleCommand(130,...)`：29 字节 `FF` + 2 字节参数 | 已确认 |
| 版本/绑定前缀 | `01000101C8` | 与其他已验证帧一致 |
| 帧长度 | payload 36 字节，frame length `0x24` | Golden Frame 已固定 |
| 校验 | `getCheckResult`：`ORDER_ID + LEN + PAYLOAD` 的 SUM8 | 与现有 `VehicleFrame` 一致 |
| 成功判定 | BLE sink 成功仅代表提交；只有 TTP `passwordUnlockSettingResult==0` 进入 `VEHICLE_CONFIRMED` | 已分离 |

测试源码固定 0x82 Golden Frame，但 `jvmTest` 未执行。

## 5. X1 VIN 隔离审计

| 检查项 | 结果 |
| --- | --- |
| `001/002/003` 是否等于 `1/2/3` | 否。`X1VehicleSettingViewModel.X0()` 使用字符串 `equals` 分支 |
| Mapper 是否转整数 | 否。ID 全程保留字符串 |
| VIN 切换 | `CarSettingsController` 已按 VIN/加载代次隔离；密码控制器本轮补上同类隔离 |
| 车型切换/配置重载 | 云端清单重新生成 ID；X1 缺失项只读 |
| TTP 延迟 | 旧 VIN 状态不回填 |
| 页面重进 | `CarSettingsScreen` 的 reset key 清空临时面板 |
| 命令映射 | 与 X1 Activity/`X1VehicleSettingViewModel` 一致 |

## 6. 氛围灯状态模型调整结果

- 本地 RGB/模式选择与车辆实际状态分离。
- 保存后 `valueKnown=false`，避免本地草稿伪装成回读。
- 0xF7 sink 成功仍只表示 `LOCAL_UPDATED`。
- `atmosphereShowMode/ColorMode/OperationMode/RGB*` 不参与车辆确认。
- 未知回读保持 UNKNOWN，没有修改协议字段。

## 7. 第二批 Activity 审计

| Activity | confId | 数据来源 | 写入 | 回读 | 状态 | 本轮 |
| --- | ---: | --- | --- | --- | --- | --- |
| SeatCushionSensorActivity | 3 | 云端/无独立 TTP | `CARRY_OUT_OPEN/CLOSE`，0x11/0x12 | UNKNOWN | WRITE_VERIFIED | 无修改 |
| TopSpeedActivity | 4 | `maximumSpeedCurrent` | `MAX_SPEED_SETTING`，0x36 | TTP `maximumSpeedCurrent` | READBACK_VERIFIED + WRITE_VERIFIED | 无修改 |
| ScheduledShutDownActivity | 15 | TTP `vehiclePowerDownStatus/TimeStatus` | 0x7B/0x7C/0x7D；时间参数为 0..20 | TTP | READBACK_VERIFIED + WRITE_VERIFIED | **已修复**：Choice 改为 Slider 0..20，保留 0..2→2min 显示语义 |
| VehicleVolumeActivity | 20 | TTP `alarmWarningVolumeLevel` | `VOLUME`，0x46 | TTP 0..7 | READBACK_VERIFIED + WRITE_VERIFIED | 无修改 |
| BrakePowerOffActivity | 36 | TTP `brakePowerDownFunctionStatus` | 动态位域命令 | TTP | READBACK_VERIFIED + WRITE_VERIFIED | 无修改 |
| BracketPoweredActivity | 53 | TTP `templeSettingStatus` | `COMMAND_CONST_TEMPLE_SETTING`，0xC7，参数 1/0 | TTP | READBACK_VERIFIED + WRITE_VERIFIED | 无修改 |

未标记 `BEHAVIOR_VERIFIED`：完整 ViewModel 状态机、异常、并发、车辆切换仍需后续批次。

## 8. 自定义距离标定未解决问题

保持只读。已确认：

- `COMMAND_CONST_CUSTOM_BLE_RANGING`
- `COMMAND_CONST_BLE_RANGING_END`
- `phoneBluetoothRecord/add`
- 10 秒倒计时、成功/失败弹窗、`sendSceneType(2)`

仍缺少完整帧格式、参数范围、取消/异常状态机和结果回读，因此未开放写入。

## 9. 修改文件

- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/VehiclePasswordGateway.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`
- `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
- `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/SixthRoundReviewTest.kt`
- `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/SixthRoundActivityBatchTest.kt`

## 10. 验证结果

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs`：通过。
- `:shared:compileTestKotlinJvm`：通过。
- `:androidApp:assembleDebug :desktopApp:build`：通过。
- 测试源码：已编写并编译。
- `jvmTest`：未执行。
- 真机、云端、BLE、车辆：未验证。

## 11. Git

- `fb7c54b`：密码请求状态与 VIN 隔离。
- `4a5c94b`：氛围灯本地值不冒充车辆回读。
- `69d9990`：X1 缺失配置与第二批设置映射修复。
- 第六轮文档提交：`3af3006`、`4535ef2`；本行最终校对提交不自引用。
