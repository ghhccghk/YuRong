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
- 手动投屏：屏幕/摄像头/视频源、P2P、TCP/UDP 帧传输。

暂未完成：

- 云端绑定/解绑、车主码、多车辆管理。
- 蓝牙车钥匙/NFC/手机厂车钥匙。
- 投屏自动建链。
- OTA Wi-Fi/IoT/DVR 通道。
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

## Phase 4：设置项第二批

目标：把剩余云端设置项按“开关 → 选项/滑杆 → 联系人”三批接完。

### 4.1 已确认命令的开关

`2` 边撑感应、`32` 倾倒提醒、`42` 显示剩余里程、`43` 倒车模式、`44/64/89` 辅助推行、`51` 仪表时间同步、`81` 大灯延时。

### 4.2 选项/滑杆/二级页

`10` 电子围栏、`15` 离车定时关机、`27` 设防模式、`31` 速度单位、`50` 仪表风格、`52` 手动距离、`54` 电压平台、`58` 档位设置、`59` 报警锁、`60` 电池容量、`62` 座椅加热、`63` 超级狂暴、`72` 长续航、`73` 氛围灯、`79` 座垫上电、`80` OTD、`90` 赛道模式、`1005` 自定义按键。

### 4.3 联系人/安全

`24` 紧急联系人、`85` SOS 联系人、`91` 紧急呼叫。

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

