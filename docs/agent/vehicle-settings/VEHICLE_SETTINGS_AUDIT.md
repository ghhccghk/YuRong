# 车辆设置专项深度审计

## 1. 审计边界

- 项目：`F:\android app code\toupin`
- 原始依据：`F:\android app code\YadeaHook\temp_smali`
- 本轮性质：只读审计。未修改业务代码，未发送 BLE 命令，未执行车辆/云端写入、OTA、测试或真实网络请求。
- 范围：车辆设置入口、`bikeConfigVos`、权限/分组/排序、设置值映射、云端写入、BLE 回退、`TtpInfo.extended` 回读、VIN/生命周期/并发状态、双主题 UI 行为。

状态定义：

- `COMPLETE`：已找到原始调用链并完成主要字段、控件、写入和回读对照。
- `PARTIAL`：主路径存在，但至少有一个明确缺口或仍有未确认参数。
- `BROKEN`：当前实现有可由代码直接证明的行为错误。
- `MISSING`：原始入口存在，但 KMP 未建立对应设置。
- `UNKNOWN`：Smali/抓包证据不足，禁止推断为正确。

优先级：P0 启动/运行阻断，P1 核心业务错误，P2 明确行为差异，P3 平台/功能缺失，P4 质量问题。安全等级：L1 UI，L2 API/数据/持久化，L3 BLE/车辆/OTA/投屏。

## 2. 已确认调用链

### 2.1 云端设置清单

1. `BasePeripheralsModel$getBikeConfigs$1.smali:214` 发起 `vehiclebase/bike-setting/selectBikeSettingByVin`。
2. 响应模型是 `CarSettingBean`，其中 `bikeConfigVos` 是设置项清单，扁平字段保存当前值。
3. `CarSettingBean$BikeConfig.smali:20-50` 确认字段：`confId`、`confName`、`copy`、`briefCopy`、`newCopy`、`parentId`、`parentName`、`sort`、`isAuth`、`isIntelligence`、`isUserLabFun`、`izLab`、`copyType`、`copyStatus`、`icon`。
4. KMP `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt:154-170` 解析 `bikeConfigVos` 与 `VALUE_KEYS`，保留 `rawJson`。

### 2.2 云端写入

1. `VehicleSettingViewModel.smali:10091-11832` 的 `P4()` 根据 `configId` 生成请求字段，例如 `alarmOn`、`distance`、`sleepMode`、`seatHeating`、`seatHeatingLevel`、`highestSpeedLimit`、`sosEmergencyCall`、`lowBatTelWarning`、`colorEggSoundEffect`、`emergencyContact`、`dumpAlarm`、`distanceOn` 等。
2. `MixRequest.smali:357-500` 的 `g()/h()` 组装 `configId`、值和当前 `vin`，通过 `updateBikeSetting` 请求适配器提交。
3. `MixRequest.smali:504-649` 的 `j()/k()` 对参数型设置使用 `updateBikeSettingParams`。
4. KMP `YadeaCarSettingsGateway.update()` 使用 `vehiclebase/bike-setting/updateBikeSetting`，字段由 `CloudCarSettingUpdateBuilder` 生成。`updateBikeSettingParams` 在 KMP 中尚未独立建模，相关项目只能通过已确认字段或 BLE 命令写入。

### 2.3 设置状态确认

1. `MixRequest.smali:188-256` 的 `checkSettingStatus()` 组装 `id` 参数。
2. `MixRequest$checkSettingStatus$3.smali:78-111` 读取 `CheckReportLossBean.result`。
3. KMP `YadeaCarSettingsGateway.checkStatus()` 使用 `common-cmd/low-freq/info/param` 并把 `result == 0` 视为成功。`result` 的完整业务枚举仍需请求返回样本确认，故状态记为 `UNKNOWN`。
4. KMP `CarSettingsController.update()` 只对 `CarSettingWriteChannel.CLOUD` 且能生成云端字段的普通条目轮询确认；BLE 写入只依赖发送返回值，没有车辆确认或回读闭环。

### 2.4 BLE 命令与帧

1. 原始命令常量来自 `com/yadea/smartmoto/common/contract/VehicleConstant.smali`，生成器是 `com/yadea/blecontrol/data/BleBicycleCommandManager.smali`。
2. KMP `VehicleSettingCommands.kt` 记录了已确认的 `commandType`/`commandId`，`VehicleSettingFrameEncoder.kt` 记录帧位域。
3. KMP `AndroidVehicleSettingCommandSink.kt` 只负责编码并写入平台命令特征；本轮不修改、不发送任何命令。

### 2.5 车辆回读

1. `TtpInfoParser` 的偏移来自原 `l76.z([B)`。
2. `TtpExtendedInfo.kt` 保留了 `0x6A` 后状态位的原始偏移、位宽和 `-1` 未上报语义。
3. `CarSettingsController.applyVehicleStatus()` 将可回读状态回填到设置条目；未回读字段保持原有值或 `UNKNOWN`，不能当作车辆确认。

## 3. 当前实现总评

- 云端清单驱动的主体架构存在：`bikeConfigVos` 负责项目、分组、排序和权限；KMP 对未知 confId 保留展示，不硬编码车型列表。
- 已有一批 BLE 命令映射和帧编码，但 `VehicleSettingCommands`、`VehicleSettingFrameEncoder` 与原 `BleBicycleCommandManager` 仍需逐条复核，尤其是位域反转、参数 0/1 反值和多字段打包。
- `CloudCarSettingMapper` 的 `kindOf()` 存在明确重复分支，导致倒车/婴儿车模式被建成 `Info`，后续 TTP 回填无法生效。
- `CarSettingsController` 会保留旧 VIN 的 items/missingCloudItems 直到新请求成功；刷新失败时不会清空旧值。`latestVehicleStatus` 也没有 VIN 隔离。
- 权限检查只在 `CarSettingsController.update()` 使用 `item.writable`；`CloudCarSettingWriter`、`MappedCarSettingWriter` 本身接受任意 `CarSettingItem`，业务写入层没有再次阻止 `isAuth=false` 或伪造条目。
- 双主题 UI 共用 `CarSettingsScreen` 的业务回调，本轮没有发现 Miuix/M3 各自复制业务写入；但视觉、长文本、窄屏和明暗模式未做截图验证。

## 4. 异常场景检查

| 场景 | 当前行为 | 判定 |
| --- | --- | --- |
| 云端 `bikeConfigVos` 为空 | `refresh()` 清空 items，显示“云端未返回可用设置项”；missingCloudItems 仍由本地目录生成 | PARTIAL |
| 云端缺少字段 | `CloudCarSettings.intValue/boolValue` 返回 null，多个 `kindOf()` 用默认值或 false | UNKNOWN，需按原默认值逐项确认 |
| confId 未知 | KMP 保留 `cloud:<confId>`，kind 为 Info/Entrance，写入能力由命令/云端字段决定 | PARTIAL |
| `isAuth=false` | `CarSettingItem.writable=false`，控制器拒绝写入 | PARTIAL，writer 层未二次校验 |
| `isAuth` 缺失 | 按未禁止处理 | COMPLETE（与规范一致） |
| API 失败/超时 | `refresh()` 保留旧 items，仅更新 message/loading | BROKEN，存在旧数据污染 |
| 快速连续同一设置 | `pendingIds` 阻止重复提交 | PARTIAL |
| 快速不同设置 | 可并发写入；同一 confId 的拆分条目可能互相影响确认 | PARTIAL |
| 修改期间切 VIN | 新请求开始时不清空旧 items/latestVehicleStatus | BROKEN |
| 车辆断开 | `CarSettingsScreen` 仅对 BLE_POWER_SETTING_IDS 禁用；其他条目仍可尝试 | PARTIAL |
| 回读延迟 | TTP `null`/`-1` 不覆盖原值 | COMPLETE（未伪造确认） |
| 页面退出后返回 | 控制器由 `remember` 持有，状态可跨页面重组保留 | PARTIAL |
| 应用重启 | 未发现设置写入结果持久化；重新进入依赖云端刷新 | UNKNOWN |

## 5. 本轮未做事项

- 未执行 `jvmTest`、Gradle 编译、真实登录、云端请求、BLE 写入、OTA 或上传。
- 未对每个 Smali Activity 的按钮文案、确认弹窗、埋点和所有动态入口逐条截图还原。
- 未把 `UNKNOWN` 参数范围、`result` 枚举、BLE 协议位域推断成已确认行为。