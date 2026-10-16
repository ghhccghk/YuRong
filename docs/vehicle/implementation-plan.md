# 车辆功能实施计划

把车辆相关功能落地到本仓库的分阶段计划。协议细节见同目录其他文档。

## 1. 跨平台边界

```text
┌─ commonMain（可单测、可编译到 JVM/JS/Wasm）──────────────────┐
│ 协议编解码：帧 / CRC / XOR / 位域                             │
│ 模型：TtpInfo 快照、VehicleCommand                            │
│ 状态机：连接 / 任务队列 / 建链                                │
│ 抽象接口：BleTransport、Clock                                 │
│ 登录：签名、校验、接口定义、Header                             │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌─ androidMain + androidApp（系统能力）───────────────────────┐
│ BluetoothGatt / Scanner / refresh()（133 规避）               │
│ 运行时权限、前台服务、广播接收器、音频播放                     │
│ PreferenceStore 的 Android 后端                               │
└──────────────────────────────────────────────────────────────┘
                          ↓
┌─ jvmMain / jsMain / wasmJsMain / iosMain ───────────────────┐
│ 安全降级实现（isAvailable = false）                           │
└──────────────────────────────────────────────────────────────┘
```

> **硬约束**：新增任何 `expect` 之前，必须补齐所有平台的 `actual`。
> 当前 `createBleAdapter()` 在 jvm / js / wasm / ios 均缺失。

## 2. 目录结构

```text
shared/src/commonMain/kotlin/top/ghhccghk/yadea/
├── vehicle/
│   ├── Hex.kt                  # toHex2 / toHex / hexToBytes / toDecHex / toCheckHex
│   ├── Ttp.kt                  # 位域与多字节读取原语
│   ├── VehicleFrame.kt         # 5944 帧构造与校验
│   ├── Checksums.kt            # SUM8 / CRC16-XMODEM / CRC32-MPEG2 / XOR
│   ├── ClassicCommands.kt      # 8 个经典命令 + 命令 ID 分派
│   ├── VehicleCommand.kt       # 命令载体 + t() 映射
│   ├── TtpInfo.kt              # 状态快照 data class
│   ├── TtpInfoParser.kt        # l76.z 的 Kotlin 移植
│   ├── NotifyDispatcher.kt     # 协议版本判定 + 分派
│   ├── VehicleTimeouts.kt      # 常量
│   ├── LinkState.kt            # 状态 / 异常 / 断开类型
│   ├── ConnectFlow.kt          # 纯函数转移表
│   ├── InitTaskQueue.kt        # 5 项初始化任务
│   ├── HandshakeFrame.kt       # 32 字节握手帧
│   ├── ExtendedParams.kt       # 鉴权参数 + ConnectType
│   ├── BindingFilter.kt        # 绑定扫描筛选
│   ├── BondType.kt             # bondType 映射 + 二进制转 hex
│   ├── BindingFlow.kt          # 绑定状态机
│   └── VehicleTransport.kt     # platform-free 传输接口
└── auth/
    ├── Crypto.kt               # 纯 Kotlin AES-ECB / MD5（不放 expect）
    ├── GatewaySigner.kt        # 签名 + JSON 规范化
    ├── AuthValidation.kt       # 正则
    ├── AuthModels.kt           # UserInfoBean / OtherLoginBean
    ├── AuthApi.kt              # 11 个接口
    ├── CommonHeaders.kt        # 15 个公共 Header
    └── AuthSession.kt          # token 生命周期

shared/src/androidMain/kotlin/top/ghhccghk/yadea/
├── vehicle/AndroidVehicleTransport.kt    # GATT 实际读写
├── vehicle/VehicleForegroundService.kt    # connectedDevice 前台服务
└── auth/HttpEngine.android.kt             # Ktor engine
```

## 3. 阶段划分

### Phase 1 — 协议层（可立即开工）

| 文件 | 内容 |
| --- | --- |
| `Hex.kt` | 两个十六进制函数（判据不同） |
| `Ttp.kt` | 位域 + 多字节原语（注意 LE/BE） |
| `Checksums.kt` | SUM8 / CRC16-XMODEM / CRC32-MPEG2 / XOR |
| `VehicleFrame.kt` | 帧构造 + 校验 + 3B 专用帧 |
| `ClassicCommands.kt` | 8 个命令逐字节 |
| `VehicleCommand.kt` | 命令载体 + `t()` 映射 |

纯函数，无平台依赖，可 100% 单测。**建议先做这个阶段。**

### Phase 2 — 状态解析

| 文件 | 内容 |
| --- | --- |
| `TtpInfo.kt` | 快照 data class |
| `TtpInfoParser.kt` | `l76.z` 移植，先做已确认 offset 的字段 |
| `NotifyDispatcher.kt` | 协议版本判定 + 分派 |

优先保证 11 个关键字段：`parkingStatus`、`onOffStatus`、`totalBatteryVoltage`、
`totalBatteryElectricity`、`pBElectricitySoc`、`currentOdoValue`、`currentSpeedValue`、
`remainingMileage`、`maximumSpeedCurrent`、`drivingMemory`、`cruiseControl`。

### Phase 3 — 连接状态机

| 文件 | 内容 |
| --- | --- |
| `LinkState.kt` | 状态 / 异常 / 断开类型枚举 |
| `ConnectFlow.kt` | 纯函数转移表 |
| `InitTaskQueue.kt` | 5 项任务，注入 Clock 与 Transport |
| `VehicleTransport.kt` | 平台无关传输接口 |

用假时钟 + 假 Transport 覆盖超时、重试、断开分类、去重。

### Phase 4 — 握手与鉴权

| 文件 | 内容 |
| --- | --- |
| `HandshakeFrame.kt` | 32 字节帧 + XOR 收尾 |
| `ExtendedParams.kt` | `isAuthToken` / `ConnectType` |

依赖 Phase 1、2。

### Phase 4.5 — 车辆绑定

| 文件 | 内容 |
| --- | --- |
| `BindingFilter.kt` | 扫描筛选条件（`YD` 前缀 / `isScooter` / `isBox` / `parkingStatus`） |
| `BondType.kt` | `setBikeBondType` 映射 + `binaryString2hexString` |
| `BindingFlow.kt` | 绑定状态机 |

筛选与映射都是纯函数，可立即单测。详见 [vehicle-binding.md](./vehicle-binding.md)。

### Phase 5 — 登录

| 文件 | 内容 |
| --- | --- |
| `AuthValidation.kt` | 正则（纯逻辑） |
| `Crypto.kt` | 纯 Kotlin AES-ECB / MD5 |
| `GatewaySigner.kt` | 签名 + JSON 规范化 |
| `AuthModels.kt` | `UserInfoBean` / `OtherLoginBean` |
| `AuthApi.kt` | 11 个接口 + 拦截器链 |
| `CommonHeaders.kt` | 15 个公共 Header |
| `AuthSession.kt` | token 生命周期 |

`AuthValidation` 与 `GatewaySigner` 同样可先做并单测。

### Phase 6 — Android 传输与前台服务

`androidMain` + `androidApp`：

- 扩展 BLE 适配器：MTU 结果回调、descriptor 写入、133 refresh、权限前置检查
- `VehicleForegroundService`（`foregroundServiceType="connectedDevice"`），
  结构可参照现有 `CaptureService`

### Phase 7 — 车辆设置（可选）

设置项 Registry + 双写链路（BLE + IoT）+ 1000ms 防抖 + 乐观切换 + 失败回滚。

### Phase 8 — 桌面小组件（可选）

`GlanceAppWidget`，只进 `androidApp`。

> 小组件**不能**自己维持 BLE 连接，按钮必须经前台服务执行 GATT 写。
> 且 AppWidget 走 `RemoteViews`，双主题无法注入，需单独设计简化视觉。

## 4. 偏好与 schema

按项目规范，新增设置项走 `AppPreferenceKeys`（只追加，不改已发布 key）：

| key | 类型 | 默认 | 引入阶段 |
| --- | --- | --- | --- |
| `vehicle_last_mac` | `String` | `""` | Phase 6 |
| `vehicle_auto_connect` | `Boolean` | `false` | Phase 6 |
| `vehicle_foreground_service` | `Boolean` | `true` | Phase 6 |
| `usr_token` | `String` | `""` | Phase 5 |
| `usr_info` | `String` | `""` | Phase 5 |
| `login_id` | `String` | `""` | Phase 5 |

`SCHEMA_VERSION` 逐次递增并补迁移测试。
`read` / `observe` / `write` / `decode` 四处必须同步。

## 5. 验证

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:jvmTest
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```

| 层 | 手段 | 可自动化 |
| --- | --- | --- |
| 帧 / CRC / XOR | 十六进制对拍向量 | 是 |
| 位域 / 字节序 | 构造字节数组 | 是 |
| 状态机 | 假时钟 + 假 Transport | 是 |
| 任务队列 | 注入失败 / 超时 | 是 |
| 状态解析 | 构造报文 | 是 |
| 网关签名 | 固定 JSON + 时间戳复算 | 是 |
| 正则校验 | 边界值 | 是 |
| 小组件 | `adb broadcast` + 桌面操作 | 否（需设备） |
| 真实 BLE | 实车 | 否 |

## 6. 关键风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 两套 `5944/4B4A` 混淆 | 帧解析互相污染 | 独立对象 + 独立单测 |
| `5944FFFx` 误当帧头 | 解析完全错误 | 命名上区隔（如 `PartsUuids`） |
| **字节序 LE/BE 混用** | 数值离谱但不崩溃 | 逐字段按原调用选 `H`/`J`，写对拍单测 |
| **条件性缩放** | 无效值变 `-0.1f` 而非 `-1.0f` | 照抄"等于哨兵则跳过" |
| `decInt2hexString` 补零规则 | 帧长度字段错误 | 区分 `toDecHex` 与 `toCheckHex` |
| `2a26` 缺陷 | 硬件版本恒等于固件版本 | 建议修正为 `2a28`/`2a27` |
| 握手帧字段来源未确认 | 鉴权路径可能发送错误帧 | 先用 `isAuthToken=false` 验证基础链路 |
| 硬编码 AES 密钥 | 合规风险 | 构建期注入或服务端代理 |
| 平台 `actual` 缺失 | 破坏多平台编译 | 新增 `expect` 前先补齐 |
| 小组件无法复用双主题 | UI 规范不一致 | 明确降级为独立简化视觉 |

## 7. 尚未确认

| 项 | 影响 |
| --- | --- |
| `l76.z` 的 0x6A 之后字段 | 状态解析完整性 |
| `remainingMileage` 的舍入模式 | KMP 无 `BigDecimal` |
| 握手帧 `b0`/`U`/`V`/`X`/`Y` 实现 | 鉴权可行性与帧正确性 |
| `r85.g()` / `r85.b()` 语义 | 同上 |
| 15 s 看门狗的 tick 值 | 超时精度 |
| AES Base64 是否带换行、Gson 转义策略 | 签名一致性 |
| 环境 URL 取值 | 决定 `_gateway` 为 True / False |
