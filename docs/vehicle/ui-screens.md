# 车辆、账号与 OTA 界面

本次实现了四个共享界面，并集中在 `MainScreen` 的“车辆”入口下：

| 子页 | 文件 | 业务入口 |
| --- | --- | --- |
| 车辆信息 | `ui/screens/VehicleInfoScreen.kt` | `VehicleStatusSource` |
| 车辆设置 | `ui/screens/CarSettingsScreen.kt` | `CarSettingsController` + `CarSettingWriter` |
| OTA | `ui/screens/OtaScreen.kt` | `OtaController` + `OtaGateway` |
| 账号 | `ui/screens/LoginScreen.kt` | `AuthController` + `AuthGateway` |

## 1. 数据边界

- UI 只消费 `StateFlow` 与语义回调，不直接访问 BLE、HTTP 或 OTA 协议。
- 未注入平台实现时，界面展示明确的“通道未接入 / 等待车辆状态”空态，**不伪造车辆数据**。
- `VehicleStatusSource` 负责把 `TtpInfoParser` 的结果提供给车辆信息页。
- 已提供 `BleVehicleStatusSource`：过滤整车 b360/b362（及通知服务）特征，
  解析 `0x2A/0x7A` 完整报文后写入 `StateFlow<TtpInfo?>`。
- `VehicleProfileSource` 提供云端资料：`dLinkTag` 判定联动款，
  `vehicleImageUrl` 对应 `VehicleInfoBean.getVehicleImage()`（`skinPicUrl` 优先，回退 `imgUrl`）。
- Android 启动时通过 `userBike/queryBindingBike` 拉取资料；登录成功后自动刷新。
- Android 端 `AndroidVehicleImage` 直接加载远程车图，不新增图片库依赖。
- `AuthGateway` 负责密码登录、短信验证码与验证码登录；本地会话通过
  `AppPreferenceKeys.usrToken/usrInfo/loginId` 持久化。
- `CarSettingWriter` 负责把设置项翻译成 BLE `CommandTempBean` 或 IoT 请求。
- `OtaGateway` 负责检查更新、下载、校验与写入。

## 2. 车辆设置

`CarSettingsRegistry` 提供六个主分类：

1. 安全守护
2. 灯光音效
3. 智慧充电
4. 骑行控制
5. 电池
6. 智能仪表

外加实验室入口。开关采用**乐观切换**：先更新 UI，写入失败后回滚并显示提示。

已映射到 Smali 命令 ID 的设置会通过 `VehicleSettingFrameEncoder` 生成
`5944…4B4A` 控制帧，再由 `AndroidVehicleSettingCommandSink` 写入整车主特征。
未映射的设置项会返回失败并回滚，不会猜测协议。

## 3. 登录

- 支持密码登录与手机验证码登录。
- 校验规则来自 `auth.md`：手机号、邮箱、密码字符集、4 位验证码。
- 验证码发送成功后 60 秒倒计时。
- 协议未勾选时登录按钮仍可点击，点击后再串行校验并提示。
- 登录成功写入 `usr_token` / `usr_info` / `login_id`；退出只清理这三个 key。

网络层实现于 `auth/YadeaAuthGateway.kt`：

- `user/loginByPassword`、`user/loginBySms`、`user/sendCode`；
- form body 只保留 `gatewaySign` + `gatewayTimestamp`；
- 响应先 URLDecode，再 AES-ECB 解密，读取 `success/code/msg/data`。

## 4. OTA

`OtaFlow` 是纯状态机，覆盖：

`Idle → Checking → Available → Downloading → Verifying → Installing → Completed`

失败与取消分别进入 `Failed` / `Cancelled`。当前仓库没有确认 OTA 帧结构，
因此界面只依赖 `OtaGateway`，不在 `commonMain` 里猜协议。

`ota/YadeaOtaGateway.kt` 已接入：

- `firmwareVersion/latestVersion` 检查更新；
- `upgradeTask/queryTaskList/V4` 查询任务；
- 固件下载/写入仍由平台 OTA 传输实现，当前不会伪报成功。

## 5. IoT 车联网续费

`VehicleIotServiceInfo` 对应 `VehicleInfoBean` 的 `iotExpireDays`、
`iotCardExpireTime` 以及 `VehicleManageDLineBean.iotServiceDetail`。

- `expireDays < 0`：已到期；
- `expireDays < -90`：原实现提示“已自动注销”，按钮文案改为“查看服务”；
- 续费入口使用 `IotWxSmartDialogHelper` 的 H5：
  `https://tspapph5.yadeaiot.com.cn/#/flow-recharge-iot-new-index?vin=...&bikeNickName=...`

入口只在车辆资料存在 `iotService` 时显示，不在所有车型上硬编码。

## 6. 验证

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:jvmTest
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```

新增业务单测覆盖登录校验、会话编解码、设置回滚、OTA 状态机和车辆状态格式化。
