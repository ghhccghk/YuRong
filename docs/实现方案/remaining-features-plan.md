# 剩余功能实施计划

本计划基于当前仓库代码与 `F:\android app code\YadeaHook\temp_smali` 的对照结果。
目标不是一次性复刻原 App，而是按依赖关系逐步补齐可验证、可回退的功能。

## 约束

- 不运行单元测试；只运行编译和 APK 构建，避免触发风控。
- 每个独立功能单独提交，使用显式文件列表，不混入用户已有文档和日志。
- UI 继续使用 `App*` 语义组件，同时保持 Miuix 与 Material 3 两套实现。
- OTA、车钥匙、解绑、ODO 清零等高风险操作必须有二次确认和风险提示。
- 所有新增开关优先使用已确认的 TTP 状态字段与 BLE 命令；没有字节级证据的项保持只读或隐藏。

## 当前基线

已完成：

- Token、密码、短信、邮箱、原始参数登录；手机号/邮箱绑定与换绑。
- BLE 扫描、连接、鉴权、初始化、自动连接、前台服务。
- 经典车辆控制：开锁、锁车、开关机、寻车、检测、坐垫、ODO 清零。
- 车辆信息、云端车辆资料、云端车辆状态、续航/里程回退。
- 云端设置清单与大部分开关/滑杆：`9/13/14/17/18/19/20/24/25/26/33/36/37/38/41/46/47/48/49/53/61/68/75/76/77/82/84/86/91/94`。
- BLE 本地 OTA 传输与高风险确认。
- IoT OTA 云端任务确认。
- Wi-Fi/Wi-Fi DVR OTA：P2P、TCP 鉴权、8080 文件服务、Range 分片。
- OTA 升级记录与分部件选择。
- 手动投屏：屏幕/摄像头/视频源、P2P、TCP/UDP 帧传输。

暂未完成：

- 云端绑定/解绑、车主码、多车辆管理。
- 蓝牙车钥匙/NFC/手机厂车钥匙。
- 投屏自动建链。
- 设置项第二批与联系人/二级页。
- 故障检测、胎压、雷达、低电量、定位/报失。
- 家人共享、骑行轨迹、周边设备、语音、DVR、推送、小组件等。

## 阶段总览

| 阶段 | 内容 | 优先级 | 依赖 |
| --- | --- | --- | --- |
| Phase 1 | 云端车辆绑定/解绑/车主码/多车辆管理 | P0 | Token 登录、车辆资料网关 |
| Phase 2 | 投屏自动建链 | P0 | BLE 连接、P2P、TTP `wifiStatusNew` |
| Phase 3 | OTA Wi-Fi/IoT/DVR 通道与记录 | P0 | OTA 任务接口、Wi-Fi/P2P |
| Phase 4 | 设置项第二批 | P1 | 云端设置清单、BLE 命令表 |
| Phase 5 | 故障检测/胎压/雷达/低电量/定位/报失 | P1 | TTP 状态字段 |
| Phase 6 | 账号安全、第三方登录、推送、家人共享 | P2 | 登录网关、推送 SDK |
| Phase 7 | 周边设备/DVR/语音/小组件/商城社区 | P3 | 独立模块 |

## 执行进度

- [x] Phase 1.1：已提取绑定/解绑/车主验证码接口。
- [x] Phase 1.2：已实现 `VehicleBindingGateway`、`VehicleBindingController`，
  并在车库页加入“添加车辆”表单、“获取车主验证码”、绑定和解绑入口。
- [ ] Phase 1.3：真机验证绑定/解绑错误码与多车辆切换；需要实际账号数据。
- [x] Phase 2.1：已确认 `COMMAND_OPEN_WIFI_P2P_NEW`（0xFD）→
  `wifiStatusNew == 2` → P2P → TCP 的原流程。
- [x] Phase 2.2：已实现 `MirrorConnectFlow` 状态机、BLE 0xFD 帧和
  `AppViewModel.startAutoMirror()`，投屏页加入“一键投屏”入口。
- [ ] Phase 2.3：真机验证 P2P 设备 MAC 转换、GroupOwner IP 和 TCP 建链；
  失败时保留手动 IP 兜底。
- [x] Phase 3.1：已提取 OTA 任务、确认、日志、版本和分部件接口。
- [x] Phase 3.2a：已补 IoT `userConfirm/V4`、任务字段
  （`taskId/behaviorId/firmwareModuleCode`）和“提交云端升级”入口。
- [x] Phase 3.2b：Wi-Fi/Wi-Fi DVR OTA 已完成协议实现：
  BLE `0xFD`、P2P 目标匹配、TCP `8888` 鉴权、HTTP `8080`
  文件服务、Range 分片、`finish=1` 完成信号和高风险确认。
- [x] Phase 3.2c：OTA 记录页接入
  `upgradeLog/queryUpgradeLogByVin/V3`；任务列表支持选择具体固件部件。
- [ ] Phase 3.3：真车验证 Wi-Fi/Wi-Fi DVR 下载、记录分页和分部件升级；
  需要实际车辆与仪表固件，失败时优先回退云端或原厂升级。
- [x] Phase 4.1：边撑感应、倾倒提醒、T 动力、座垫上电、赛道模式已接入
  `5944...4B4A` BLE 设置帧；座椅加热开关和档位接入云端字段。
- [x] Phase 4.2：电子围栏开关/半径、感应解锁开关/距离改为可交互控件，
  并由云端 `distanceOn/distance/bleUnlockOn/bleDistance` 回填和写入。
- [x] Phase 4.3：未找到完整读写协议的云端项改为可点开的只读说明，
  不再静默无响应；不会发送猜测命令。
- [x] Phase 5.1：新增“车辆诊断”页，展示 TTP 故障码、云端故障等级、
  云端胎压、FCM/FCW/RCW/BSD 雷达、低电量提醒和定位/报失状态。
- [x] Phase 5.2：报失模式已接入车主短信验证、云端校验和
  `set-param/loss` 写入；外部胎压使用车辆绑定的外部胎压传感器云端最近值。
- [ ] Phase 5.3：外部胎压设备的独立 BLE 绑定、阈值和实时外设诊断仍需
  `BlePartsConnect` 外设链路；当前不伪造外设实时状态。

## Phase 1：云端车辆绑定与多车辆管理

目标：补上“新增车辆”的完整入口，不再只能看到 Token 账号下已经存在的车辆。

任务：

1. 从 smali 提取以下流程的接口、参数和错误码：
   - `AddCarActivity`
   - `CarOwnerCodeInputActivity`
   - `BindingSettingActivity`
   - `UserCarManageListActivity`
   - `UserCarManageNewActivity`
2. 新增 `VehicleBindingGateway`：
   - 绑定车辆
   - 解绑车辆
   - 车主码校验
   - 绑定状态查询
3. 车库页新增“添加车辆”入口：
   - VIN / 车主码输入
   - 绑定结果反馈
   - 解绑二次确认
4. 绑定成功后刷新 `queryBindingBike`，并保留当前选中 VIN。
5. 错误码映射：重复绑定、VIN 不存在、车主码错误、无权限、车辆已被绑定。

验收：

- 输入正确 VIN/车主码可以新增车辆并在车库看到。
- 解绑后车辆从车库移除，当前 VIN 回退到剩余车辆。
- 错误码有明确中文提示，不出现“网络不可用”一类模糊文案。
- 编译 `shared` 三平台与 `androidApp:assembleDebug` 通过。

### Phase 1.1 已提取的 smali 事实

绑定入口（`CarOwnerCodeInputModel.bindBike` / `BikeBindCheckModel` /
`QRCodeScanModel.bindVehicle`）：

| 用途 | 接口 | 关键参数 |
| --- | --- | --- |
| 手动/扫码绑定车辆 | `userBike/manualOrQrBindVehicle` | `userId`、`vin`、`vinPwd`、`smsCode`、`primaryUserMobile`、`primaryUserEmail` |
| 绑定下一步 | `userBike/manualOrQrBindVehicleNext` | `vin`、`vinPwd` |
| 给原车主发验证码 | `userBike/sendMsgToCarOwner` | `vin`、`vinPwd` |

解绑入口（`UserCarManageNewViewModel`）：

| 用途 | 接口 | 关键参数 |
| --- | --- | --- |
| 子账号解绑 | `userBike/childUnbundlingchild` | `bikeId` |
| 主账号解绑 | `userBike/mainUnbundlingMain` | `bikeId` |

后续实现时先做“VIN + 车主码 + 短信验证码”的 `manualOrQrBindVehicle`
分支，再补 `manualOrQrBindVehicleNext` 的二次确认流程。

## Phase 2：投屏自动建链

目标：把当前“手动填写仪表 IP”改为原 App 的一键投屏流程。

任务：

1. 从 smali 确认：
   - `NaviConnectType`
   - `COMMAND_OPEN_WIFI_P2P_NEW`
   - `COMMAND_DVR_WIFI_P2P`
   - `wifiStatusNew == 2` 的轮询条件
2. 新增 `MirrorConnectFlow` 纯状态机：
   - `Idle → BleUnlock → WaitWifi → P2pDiscover → P2pConnect → TcpConnect → Running`
   - 失败分支、超时、取消。
3. 用假时钟单测状态机（只写测试，不运行）。
4. 接入 `MirrorScreen`：
   - BLE 已连接时显示“一键投屏”
   - 自动获取 GroupOwner IP
   - 手动 IP 作为兜底入口
5. 保留现有 TCP/UDP 帧协议，不改投屏帧逻辑。

验收：

- BLE 连接后点一键投屏，能自动完成 Wi-Fi/P2P/TCP 建链。
- 超时和失败有可读提示。
- 手动 IP 模式仍可用。

## Phase 3：OTA 多通道

目标：当前只能检查并安装 BLE 包；补齐 Wi-Fi、IoT、DVR 通道和记录。

任务：

1. 从 smali 提取：
   - `OTAUpgradeActivity`
   - `X1BleOTAActivity`
   - `BleOTAUpgradeViewModel`
   - `WifiStatusMonitor`
   - `OTAPartsUpgradeActivity`
   - `BleOTARecordActivity`
2. Wi-Fi OTA：
   - 建链、任务参数、分片、进度、失败重试。
3. IoT OTA：
   - 任务查询、下发、状态轮询。
4. OTA 记录与分部件升级。
5. 所有非 BLE 安装入口继续显示高风险提示，并建议优先云端/原厂升级。

验收：

- 四种通道都能列出任务。
- Wi-Fi 通道能完成下载与安装。
- IoT 通道能触发并轮询任务状态。
- 原有 BLE 通道不回归。

### Phase 3.1 已提取的 smali 事实

任务与升级接口（`nn.smali`）：

| 用途 | 接口 | 关键参数 |
| --- | --- | --- |
| 查询任务列表 | `upgradeTask/queryTaskList/V4` | 现有 `modelNo`、`vin`、`izSkip`、`experimental` |
| 查询进行中任务 | `upgradeTask/queryUpgradingTask` | `vin`、`experimental` |
| 用户确认升级 | `upgradeTask/userConfirm/V4` | `vin`、`os=1`、`modelNo`、`modelName`、`experimental`、`confirmUpgradeType`、`firmwareModuleCode` |
| 查询/清除任务标记 | `upgradeTask/userCheckFlag` | `behaviorId`、`experimental` |
| 固件版本 | `ota/upgrade/version` | `vinList` |
| 固件信息上报 | `vehicle/bikeFirmwareUpload` | 固件/部件 map |
| 升级日志 | `ota/upgradeLog/addUpgradeLog` | 升级结果记录 |
| 车型配置 | `vehiclebase/bike-model/selectConfigListByModelNo` | `modelNo`、`vin` |

`confirmUpgradeType`：`IOT_UPGRADE` 传 `"1"`，其他通道传 `"2"`。

Wi-Fi OTA（`OTAUpgradeActivity` / `wifi/server/a`）：

1. P2P 建链（复用 Phase 2 的 BLE/P2P 链路）。
2. 连接仪表 TCP，开启文件服务。
3. 鉴权成功后主动关闭 TCP。
4. 通过文件服务下载/传输固件，监听下载开始、进度、完成事件。
5. 失败分支包括 Socket 超时、连接失败、非目标 P2P 设备、无效 GroupOwner。

IoT OTA：

- 云平台通过推送/任务接口反馈升级信息。
- 使用 `queryUpgradingTask` 轮询状态，`userConfirm/V4` 提交确认。
- 结果通过 `j_push_iot_ota_upgrade_message`、`j_push_iot_ota_upgrade_result_message` 回流。

记录与分部件：

- `BleOTARecordActivity`：OTA 记录页。
- `OTAPartsUpgradeActivity`：分部件升级。
- `ota/upgradeLog/addUpgradeLog`：写入升级结果日志。

已确认的记录查询：

| 用途 | 接口 | 关键参数 |
| --- | --- | --- |
| 按 VIN 查询升级记录 | `upgradeLog/queryUpgradeLogByVin/V3` | `vin`、`current`、`size=50` |

响应使用通用分页结构 `data.records` 与 `data.hasNext`。记录字段为
`typeName`、`firmwareVersion`、`versionDesc`、`startTime`。

分部件升级不是独立协议：主 OTA 任务返回 `firmwareInfoList`，每个元素是
一个部件。当前 UI 允许点选具体部件后再发起升级，避免总是安装列表第一项。

### Phase 3.2 实施拆分

1. 网关层：补齐 `userConfirm/V4`、`queryUpgradingTask`、`userCheckFlag`、`upgrade/version`、`bikeFirmwareUpload`、`addUpgradeLog`。
2. Wi-Fi 通道：在现有 P2P/TCP 基础上接入文件服务，补齐下载/传输/断线重连。
3. IoT 通道：确认任务、轮询状态、推送结果。
4. 记录/分部件：OTA 记录页与部件升级入口。
5. 验收：BLE 通道不回归；Wi-Fi/IoT 通道有明确状态与失败原因。

### Phase 3.2b 已确认的 Wi-Fi OTA 协议

以下内容来自 `OTAUpgradeActivity`、`BleOTAUpgradeViewModel`、
`wifi/server/FileServer`、`rl1`（`FileServerFileResponder`）和 `e27`
（`WifiOtaUtils`）。

建链顺序：

1. BLE 已连接时发送 `COMMAND_OPEN_WIFI_P2P_NEW`（命令 ID `0xFD`）。
2. 轮询 TTP，直到 `wifiStatusNew == 2`。
3. P2P 扫描并连接目标设备；GroupOwner 必须为 `192.168.7.1` 或
   `192.168.6.1`，`0.0.0.0` 和空地址直接丢弃。
4. 连接仪表 TCP `8888`（`0x22B8`），连接超时 `3000ms`。
5. 发送 9 字节鉴权包后等待仪表连接关闭。
6. 手机启动 HTTP 文件服务，监听 `8080`（`0x1F90`）。

鉴权包（`e27.a`）：

| 偏移 | 值 |
| --- | --- |
| `0` | `0x59` (`Y`) |
| `1` | `0x44` (`D`) |
| `2` | `0xF0` |
| `3` | `0x02` |
| `4` | 固件版本字节 |
| `5` | 手机 Wi-Fi IPv4 地址末字节 |
| `6` | `(0xF2 + 版本字节 + IP 末字节) and 0xFF` |
| `7` | `0x4B` (`K`) |
| `8` | `0x4A` (`J`) |

固件版本字节来自固件头（`g17`，至少 64 字节）：

- 文件格式：`[0,3)`
- 产品类型：`[3,6)`
- CRC32：`[8,12)`
- 文件长度：`[12,16)`
- 文件版本：`[16,20)`
- 厂商代码：`[24,26)`
- 文件类型：`[26,27)`
- 鉴权包使用的版本字节：`[26,27)` 的十六进制值（`Integer.parseInt(..., 16)`）

HTTP 文件服务（`rl1`）：

- 只接受 `GET`；其他方法返回 `405`。
- 请求路径不参与文件选择；服务始终返回构造时传入的同一个固件文件。
- 文件不存在或不是普通文件时返回 `404`。
- 无 `Range` 时返回 `200 OK`，`Content-Type:
  application/octet-stream`。
- `Range: bytes=start-end` 时返回 `206 Partial Content`，并带
  `Content-Range: bytes start-end/total`。
- 支持 `end` 缺省（读到文件末尾）。
- 分块大小为 `8192` 字节。
- 进度按已发送字节 / 文件总字节计算，只有进度增加时才回调。
- 查询参数 `finish=1` 表示仪表已完成下载，服务随即停止。
- `Connection: close`，每个请求一个连接。

实施约束：

- `jvmMain`/`androidMain` 提供真实文件服务和 TCP 鉴权。
- JS/Wasm/iOS 暂不提供本地监听能力，入口返回明确“不支持”错误。
- Wi-Fi/Wi-Fi DVR 入口继续展示高风险提示，并建议优先云端/原厂升级。

## Phase 4：设置项第二批

目标：把剩余云端设置项按“开关 → 选项/滑杆 → 联系人”三批接完。

### 4.1 已确认命令的开关

`2` 边撑感应、`32` 倾倒提醒、`42` 显示剩余里程、`43` 倒车模式、`44/64/89` 辅助推行、`51` 仪表时间同步、`81` 大灯延时。

### 4.2 选项/滑杆/二级页

`10` 电子围栏、`15` 离车定时关机、`27` 设防模式、`31` 速度单位、`50` 仪表风格、`52` 手动距离、`54` 电压平台、`58` 档位设置、`59` 报警锁、`60` 电池容量、`62` 座椅加热、`63` 超级狂暴、`72` 长续航、`73` 氛围灯、`79` 座垫上电、`80` OTD、`90` 赛道模式、`1005` 自定义按键。

### 4.3 联系人/安全

`24` 紧急联系人、`85` SOS 联系人、`91` 紧急呼叫。

### Phase 4 已确认的命令与字段

BLE 开关命令来自 `CarSettingsPowerRegistry`、
`CarSettingsSecurityRegistry`、`CarSettingsRideControlRegistry` 和
`BleBicycleCommandManager`：

| confId | 命令 | 帧参数 |
| --- | --- | --- |
| `2` 边撑感应 | `0x131` | `0x1F` bit4..7，开 `1`、关 `0` |
| `32` 倾倒提醒 | `0x12D` | `0x41` bit2..3，开 `1`、关 `0` |
| `63` T 动力 | 开 `0x6A`、关 `0x69` | 固定位域 |
| `79` 座垫上电 | `0x130` | `0x1F` bit0..3，开 `1`、关 `0` |
| `90` 赛道模式 | 开 `0x90`、关 `0x91` | 固定位域 |

云端直接字段：

| confId | 控件 | 字段 |
| --- | --- | --- |
| `10` | 电子围栏开关 | `distanceOn` |
| `10` | 电子围栏半径 | `distance` |
| `52` | 感应解锁开关 | `bleUnlockOn` |
| `52` | 感应解锁距离 | `bleDistance` |
| `62` | 座椅加热开关 | `seatHeating` |
| `62` | 座椅加热档位 | `seatHeatingLevel` |

TTP 回填：

| confId | TTP 字段 |
| --- | --- |
| `62` | `seatHeatSwitchStatus`、`seatHeatLevelStatus` |
| `63` | `tPowerStatus` |
| `72` | `longEnduranceStatus` |
| `79` | `seatPowerStatus` |
| `80` | `otdSwitch` |
| `90` | `trackModeStatus` |

仍保持只读的项：

- `15/59` 离车定时关机/报警锁：原 App 使用独立子页面和额外参数。
- `27` 设防模式、`31` 速度单位、`50` 仪表风格、`54` 电压平台、
  `58` 档位设置、`60` 电池设置、`73` 氛围灯、`81` 大灯延时、
  `1005` 自定义按键：尚未提取完整的二级页写入协议。
- `42` 剩余里程显示、`43` 倒车模式、`44/64/89` 辅助推行、
  `51` 仪表时间同步：云端状态字段或开关帧参数尚不完整，继续只读。

验收：

- 每项能从云端清单出现，并能从 TTP 回填状态。
- 可写项有二次确认或明确摘要；未确认项保持只读。
- `1/66` 保持蓝牙车钥匙只读，`95` 保持隐藏。

## Phase 5：诊断与车辆状态

目标：补齐故障、胎压、雷达、低电量、定位/报失。

任务：

1. 故障检测页与故障详情：`FaultCheckActivity`、`FaultCheckDetailActivity`。
2. 胎压：外部胎压与实时胎压。
3. 雷达：BSD/FCW/RCW。
4. 低电量/亏电模式。
5. 定位开关与报失模式。

### Phase 5 已确认的数据来源

故障等级：

- 接口：`bikeFault/queryBikeFaultStatus`
- 参数：`modelNo`
- 响应：`data.faultLevelMap[]`
- 字段：`faultLevel`、`faultLevelDesc`、`faultName`、`fualtShowName`
- `faultLevel == 1` 表示检测正常；`-1` 表示部件故障，`-2` 表示网络异常。
- 车辆 TTP 故障码位于 `0x5B..0x6A`，两字节大端，协议版本 `0x5A == 0xF2`。

云端 `bikeRealStatus/getVehRealStatus` 的 `VehicleStatusBean` 字段：

| 用途 | 字段 |
| --- | --- |
| 前/后轮胎压 | `leftFrontPressure`、`leftRearPressure` |
| 前/后轮温度 | `leftFrontTireTemp`、`leftRearTireTemp` |
| 胎压告警 | `leftFrontPressureWarning`、`leftRearPressureWarning` |
| 低电量电话提醒 | `lowBatTelWarning` |
| 低电量提醒时段 | `lowBatWarningStartTime`、`lowBatWarningEndTime` |
| 定位开关 | `positioningFunc` |
| 报失状态 | `lossState` |

雷达来自 TTP：

| 用途 | 偏移/位域 |
| --- | --- |
| FCM 使能/开关 | `0x7F` bit2 / bit6 |
| FCW 模式/工作 | `0x80` bit4 / `0x81` bit0 |
| RCW 使能/开关/模式/工作 | `0x7F` bit4 / `0x80` bit0 / `0x80` bit6 / `0x81` bit2 |
| BSD | `0x80` bit2 |

未接入项：

- 外部胎压设备使用独立 BLE 外设 TTP；当前 App 使用云端
  `leftFrontPressure/leftRearPressure` 最近值，尚未实现
  `BlePartsConnect` 的配对、阈值和实时外设诊断。
- 报失模式已按原 App 顺序接入：
  `user/sendCode(sendCode=10)` →
  `user/verifySmsCode(smsScene=10)` →
  `common-cmd/low-freq/set-param/loss`；
  `value=00` 开启、`value=01` 关闭。

验收：

- 故障码有解释和严重级别。
- 胎压/雷达数据来自 TTP 或云端，不填演示值。
- 高风险功能有确认。

## Phase 6：账号、推送与共享

任务：

1. 微信/QQ/一键登录。
2. Token 安全存储（Android Keystore / 平台安全存储）。
3. 推送：极光/Getui/OPPO Push。
4. 家人共享、权限、车辆过户。
5. VIP、勋章、城市生活、手表。
6. 应用设置：通知、权限、隐私、关于、缓存。

验收：

- 第三方登录能创建/恢复会话。
- Token 不以明文落入普通偏好存储。
- 共享权限与车辆操作权限一致。

## Phase 7：扩展模块

- 周边设备：相机、充电器、电子把手锁、智能尾箱、BLE 钥匙。
- 行车记录仪、视频、语音助手、微信互联。
- 车辆模型/颜色、壁纸皮肤、仪表皮肤。
- 桌面小组件、快捷方式。
- 商城、社区、保养服务。

## 提交与验证策略

每个阶段拆成 1–3 个独立提交：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :androidApp:assembleDebug
```

只报告实际执行过的验证。高风险阶段（OTA、车钥匙、解绑、ODO）在提交说明中标注风险。

## 建议执行顺序

1. Phase 1.1：提取云端绑定 smali 事实（只读）。
2. Phase 1.2：实现网关与车库入口。
3. Phase 2.1：实现投屏自动建链状态机。
4. Phase 3.1：确认 Wi-Fi OTA 协议。
5. Phase 4.1：接设置开关批次。
6. Phase 5：故障/胎压/雷达。

