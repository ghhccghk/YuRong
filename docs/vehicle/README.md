# 车辆功能文档

本目录记录「雅迪车辆」相关功能的实现规格与逆向依据。

分析来源是 `F:/android app code/YadeaHook`（Yadea SmartMoto APK 的 smali 反编译产物），
落地目标是本仓库的 Kotlin Multiplatform Compose 结构。

## 文档

| 文档 | 内容 |
| --- | --- |
| [protocol.md](./protocol.md) | 帧格式、校验算法、字节序、位域原语、命令表 |
| [ttpinfo-fields.md](./ttpinfo-fields.md) | TtpInfo 全字段映射表（偏移/位宽/无效值/缩放） |
| [connection.md](./connection.md) | 连接状态机、超时与重连语义、初始化任务队列 |
| [vehicle-binding.md](./vehicle-binding.md) | 车辆绑定：扫描筛选、`bondType` 映射、配对流程 |
| [handshake.md](./handshake.md) | 握手/鉴权帧、token 来源与绕过条件 |
| [auth.md](./auth.md) | 登录接口、网关签名、公共 Header、会话持久化 |
| [implementation-plan.md](./implementation-plan.md) | 分阶段实施计划与跨平台边界 |

## 证据状态

所有常量、偏移、位宽与算法均来自 `temp_smali/` 的**逐行核对**，非文档转述。
每份文档都会标注哪些项已由字节码确认、哪些仍需实车或抓包验证。

已确认的关键事实：

- 车辆控制帧与投屏帧**共用** `5944/4B4A` 字符串，但协议 ID（`3E` vs `3C`）与长度编码（变长 hex vs BE32）**不同**，必须两套实现。
- `5944FFF0/FFF1/FFF2` 是**部件通道 UUID**，不是帧头。
- 解析器在 `l76.smali`，不在 `TtpInfo.smali`（后者只有字段声明）。
- 多字节读取**字节序混用**：`H`/`e` 是小端，`J` 是大端。
- 数值缩放是**条件性**的，等于哨兵时不缩放。
- 固件版本与硬件版本都读 `00002a26`（原实现缺陷，`2a27` 常量零引用）。
- 握手帧需要 token，但 `isAuthToken == false` 时可完全绕过。
- 绑定扫描有三级筛选（`isAll` / `type` / `parkingStatus`），`bondType` 由 2 位外部码映射为内部二进制串。
- `binaryString2hexString` 要求输入长度为 **8 的倍数**，否则返回 `null`，因此调用方靠拼接凑足位数。

## 验证要求

涉及共享代码的改动至少运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:jvmTest
```

涉及 Android 或 Desktop 时追加：

```powershell
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```
