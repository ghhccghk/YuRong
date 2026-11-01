# smali 源码验证报告

对 `F:/android app code/YadeaHook/temp_smali/` 的逐行核对结果，用于修正
[implementation-spec.artifact.md](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/implementation-spec.artifact.md)
中来自 docs 的二手结论。

> [!NOTE]
> 本报告只记录**已在 smali 中读到字节码证据**的内容。
> 每项标注 ✅ 已证实 / ⚠️ 已修正文档 / ❓ 仍不确定。

---

## 1. 关键文件索引

| 文件 | 行数 | 职责 |
| --- | ---: | --- |
| `wi.smali` | 7101 | **协议核心**：帧构造、握手、校验、读写封装 |
| `l76.smali` | 12724 | **TtpInfo 解析器**：位域提取原语 + 字段映射 |
| `com/yadea/blecontrol/data/BleBicycleCommandManager.smali` | 9269 | 命令帧组装 + 命令 ID 分派 |
| `com/yadea/blecontrol/data/BleBicycleControlCommands$ClassicCommands.smali` | 157 | 8 个经典命令的字节数组 |
| `com/yadea/blecontrol/connect/BleBicycleConnect.smali` | 1708 | 连接状态机 + 任务链 |
| `com/yadea/blecontrol/control/BleControlManager.smali` | 3280 | 命令并发锁 + 分发 |
| `com/yadea/blecontrol/entity/TtpInfo.smali` | 1860 | **纯字段声明**，无解析逻辑 |
| `yi.smali` | 835 | UUID / 常量池 |

---

## 2. ⚠️ 修正：UUID 常量（docs 未完整记录）

`yi.smali` 中存在一批 docs 完全没提的 UUID：

| 常量索引 | 值 |
| --- | --- |
| `w3/w4/w5` | `5944FFF0-A000-B000-C000-000000000000` |
| `x3/x4/x5` | `5944FFF1-A000-B000-C000-000000000000` |
| `y3/y4/y5` | `5944FFF2-A000-B000-C000-000000000000` |
| `t5` | `00002a26-0000-1000-8000-00805f9b34fb` |
| `s5` | `00002a27-0000-1000-8000-00805f9b34fb` |
| `u5` | `00002a29-0000-1000-8000-00805f9b34fb` |

> [!IMPORTANT]
> **`5944FFF0/FFF1/FFF2` 是 UUID，不是帧头。**
> 它们是"部件(parts)"通道的 Service/Characteristic，配合 `BlePartsControl` 使用。
> 而 `5944`（"YD"）作为帧头只出现在 `BleBicycleCommandManager` / `wi` 的字符串拼接里。
> 两者字符串前缀相同纯属巧合（`5944` 既是 ASCII "YD" 也是 UUID 的一段），
> **实现时必须严格区分**，不要因为看到 `5944` 就当成帧。

---

## 3. ✅ 证实：`00002a26` 的"bug"确实存在

docs 说固件版本和硬件版本任务都读 `2a26`，并说"按 smali 原样复刻，勿擅自修正"。

**smali 证据（两份独立实现都指向同一结论）：**

`wi.smali`：

| 方法 | 行号 | Service | Characteristic |
| --- | ---: | --- | --- |
| `wi.R0`（固件，被 `createFirmwareRevisionTask` 调用） | 3528 | `0000180a-…` | **`00002a26-…`** |
| `wi.S0`（硬件，被 `createHardwareRevisionTask` 调用） | 3621 | `0000180a-…` | **`00002a26-…`** |
| （制造商名） | 3804 | `0000180a-…` | `00002a29-…` |

`BlePartsControl` / `control/a` 中同样是 `180a` + `2a26`。

调用点确认：

```text
BleBicycleConnect$createFirmwareRevisionTask$1.b() → wi.R0(device, callback, onFailure)
BleBicycleConnect$createHardwareRevisionTask$1.b() → wi.S0(device, callback, onFailure)
```

两者的异常文案也都是 `"read firmware revision error"`（`S0` 的 catch 分支复制粘贴自 `R0`）。

> [!WARNING]
> **这不是文档误读，是原实现的真实缺陷。**
> 制造商名用 `2a29`（正确），而固件与硬件都用 `2a26`（按 GATT 规范 `2a26` = Firmware Revision，
> `2a27` = Hardware Revision，`2a28` = Software Revision）。
> `yi.smali` 里 `2a27` 常量**存在但从未被引用**——这是"本该用 2a27 却漏改"的强证据。
>
> 决策建议：**修正为 `2a28`/`2a27`**，不要复刻缺陷。理由：这是本机读取、不影响车端，
> 且复刻会让"硬件版本"永远等于"固件版本"，属于无意义的数据污染。
> 若担心兼容性，可先读 `2a27`、失败再回退 `2a26`。

---

## 4. ✅ 证实：命令帧格式（含 docs 未明确的细节）

`BleBicycleCommandManager.<init>` 常量初始化：

```text
ORDER_HEAD        = "5944"
ORDER_TAIL        = "4B4A"
ORDER_ID          = "3E"
EXTERNAL_ORDER_ID = "3A"
ORDER_ID_3B       = "3B"
ORDER_LENGTH      = "1E"
bleCommBigVersion = "00"
bleCommSmartVersion = "01"
```

帧组装（以 `EXTERNAL_ORDER_ID` 路径为完整样本，行 5695–5770）：

```text
frame = ORDER_HEAD + ORDER_ID + decInt2hexString(payload.length) + payload + CHECK + ORDER_TAIL
CHECK = getCheckResult(ORDER_ID + decInt2hexString(payload.length) + payload)
最终整体 toUpperCase(Locale.ROOT)
```

**校验范围确认**：`CHECK` 覆盖 `ORDER_ID + LEN + PAYLOAD`，
**不含** `ORDER_HEAD`（`5944`）与 `ORDER_TAIL`（`4B4A`）。✅ 与 docs 一致。

`getCheckResult`（行 125）：

```text
sum = Σ u8(byte) & 0xFF
hex = Integer.toHexString(sum)
if (hex.length == 1) hex = "0" + hex     // 补齐到 2 位
```

> [!NOTE]
> **校验字节会补零到 2 位，但长度字节不会。**
> 长度来自 `decInt2hexString(len)` = 纯 `Integer.toHexString`，无补零。
> 例：payload 长度 1 → 长度字段 `"1"`（1 个字符，不是 `"01"`）。
> 文档只说了"不强制固定两位"，这里补充：**只有校验字节补零**。

### 4.1 `increaseControlByte` / `getIncreaseByteHexStr` ✅

```text
increaseControlByte(n):
    if (n > 10) n = 10
    ORDER_LENGTH = toHexString(0x1E + n)     // 0x1E=30，n=1 → "1f"
    increaseByteNum = n

getIncreaseByteHexStr():
    if (increaseByteNum < 0) increaseByteNum = 0
    return "FF" × increaseByteNum            // 追加 FF，确认是填充
```

### 4.2 与投屏帧的差异（再次确认必须隔离）

| 维度 | 车辆控制帧 | 投屏帧 |
| --- | --- | --- |
| 协议 ID | `3E` / `3A` / `3B` | `3C` |
| 长度编码 | `decInt2hexString`，**变长、不补零** | BE32，固定 4 字节 |
| 校验 | SUM8，**补零到 2 位** | SUM8，固定 2 字符 |
| 校验范围 | `ORDER_ID + LEN + PAYLOAD` | `3C + INNER_LEN + INNER_PACKET` |
| 大小写 | 整体 `toUpperCase` | 原样 |

---

## 5. ⚠️ 新发现：握手/鉴权帧是**另一套格式**

这是 docs 完全没有描述的部分（docs 只说"配对握手细节 smali 无法证明"）。

`wi.D0`（"进行Token鉴权流程"）→ `wi.E` → `wi.F`：

```text
E0(device, randKey, isFactory, extParams, cb, onFail):
    token = ud5.r(userId, mac)              // 本地取 token
    if (token.isEmpty()):
        NetTokenHelper.d(mac, false, extParams, callback)   // 走网络取 token
    else:
        D0(device, token, randKey, isFactory, cb, onFail)

D0(device, token, randKey, isFactory, cb, onFail):
    M0(device, randKey)                     // 预处理
    E(device, token, randKey, isFactory, <field i>) → byte[32]
```

`wi.F` 的字符串拼接：

```text
F(token, randKeyBytes, p3, p4, p5, p6):
    sb = token
       + "5944FFFFFFFF"                     // 6 字节：59 44 FF FF FF FF
       + p3 + p4
       + P0(randKeyBytes, 6, 3)             // 从 offset 6 起取 3 字节，"%02X"
       + p5 + p6
       + "FFFF"
```

`wi.E` 的收尾（关键）：

```text
bytes = hexDecode(F(...))
frame = Arrays.copyOf(bytes, 0x20)          // 强制 32 字节，不足补 0
frame[31] = G(frame, 31)                    // XOR 前 31 字节
```

`wi.G(data, len)` = 逐字节 XOR 折叠：

```text
acc = 0
for (i in 0 until len) acc = (acc xor data[i]).toByte()
return acc
```

> [!CAUTION]
> **握手帧不是 `5944 + ORDER_ID + LEN + PAYLOAD + SUM8 + 4B4A` 格式。**
> 它是以 **token 开头**、含 `5944FFFFFFFF` 标记、固定 32 字节、用 **XOR（非 SUM8）** 收尾的独立结构。
> 且需要先有 token（本地 `ud5.r(userId, mac)`，或走 `NetTokenHelper` 联网获取）。
>
> 这意味着：**握手依赖账号体系（Phase 6 登录）**。
> 如果先做 Phase 1–4 而不做 Phase 6，连接会在握手阶段失败。
> 这是实施顺序上的硬依赖，docs 没有指出。

### 5.1 ❓ 仍需实车/抓包确认

| 项 | 状态 |
| --- | --- |
| `ud5.r(userId, mac)` 返回的 token 内容与长度 | 未定位实现 |
| `P0(randKey, 6, 3)` 取的是随机数哪 3 字节 | 需看 randKey 生成处 |
| `p3`/`p4`/`p5`/`p6` 的业务含义 | 涉及 `wi.X([I)` / `wi.Y(I,[I)` 与 `r85` 常量表 |
| `r85.g()` 返回的"发送id 3" | 未定位 |
| 服务端如何校验该 32 字节帧 | 需抓包 |

---

## 6. ✅ 证实：8 个自行车经典命令逐字节

`BleBicycleControlCommands$ClassicCommands.<clinit>` 的 `fill-array-data` 全部读到：

| 常量 | 字节 | Hex | 语义 |
| --- | --- | --- | --- |
| `CAR_COMMAND_ON` | `0x7, 0x1, 0x4` | `07 01 04` | 开机 |
| `CAR_COMMAND_OFF` | `0x7, 0x1, 0xE` | `07 01 0E` | 关机 |
| `CAR_COMMAND_LOCK` | `0x7, 0x1, 0x1` | `07 01 01` | **锁车** |
| `CAR_COMMAND_UNLOCK` | `0x7, 0x1, 0x2` | `07 01 02` | **开锁** |
| `CAR_COMMAND_WHISTLE` | `0x7, 0x1, 0x3` | `07 01 03` | **寻车** |
| `CAR_COMMAND_DETECT` | `0x7, 0x1, 0xD` | `07 01 0D` | 检测 |
| `CAR_COMMAND_ODO_RESET` | `0x7, 0x1, 0xA` | `07 01 0A` | ODO 清零 |
| `CAR_COMMAND_CUSHION` | `0x7, 0x1, 0x9` | `07 01 09` | **坐垫** |

✅ 与 docs 完全一致。数组长度固定 3 字节。

---

## 7. ⚠️ 重大修正：TtpInfo 解析不在 TtpInfo.smali

docs 隐含"TtpInfo 是带解析逻辑的实体"，实际：

- `com/yadea/blecontrol/entity/TtpInfo.smali`（1860 行）**只有字段声明 + 无参构造 + toString**，
  共 2 个方法：`<init>()V`、`toString()Ljava/lang/String;`
- **真正的解析器是 `l76.smali`**：
  - `l76.z([B) → TtpInfo`：完整报文解析（行 9047）
  - `l76.y([B) → TtpInfo`：短报文解析（行 9004）

### 7.1 位域提取原语（这是移植的核心）

| 方法 | 语义 | 无效值判定 |
| --- | --- | --- |
| `l76.m(data, offset)` | u8 | `0xFF` → `-1` |
| `l76.C(data, offset, shift)` | `(u8 >> shift) & 0x07`（3 bit） | `0x07` → `-1` |
| `l76.G(data, offset, shift)` | `(u8 >> shift) & 0x03`（2 bit） | `0x03` → `-1` |
| `l76.d(data, offset, shift)` | `(u8 >> shift) & 0x0F`（4 bit） | `0x0F` → `-1` |
| `l76.H(data, offsets)` | 多字节组装 | — |
| `l76.f(data, offsets)` | 字节转字符串 | — |

**所有提取器统一模式**：

```kotlin
// 越界或无效一律返回 -1
fun m(data: ByteArray?, offset: Int): Int {
    if (data == null || data.size <= offset) return -1
    val v = data[offset].toInt() and 0xFF
    return if (v == 0xFF) -1 else v
}

fun C(data: ByteArray?, offset: Int, shift: Int): Int {
    if (data == null || data.size <= offset) return -1
    val v = (data[offset].toInt() and 0xFF shr shift) and 0x07
    return if (v == 0x07) -1 else v
}
```

### 7.2 `parkingStatus` 的真实读取方式（验证示例）

```text
l76.z(data):
  TtpInfo.info = hex(data)
  protocolSecondaryVersionCode = l76.m(data, 4)
  protocolPrimaryVersionCode   = l76.m(data, 5)
  softwareSecondaryVersionCode = l76.m(data, 6)
  softwarePrimaryVersionCode   = l76.m(data, 7)
  parkingStatus                = l76.C(data, 8, 0)     // byte8 bit0-2, 3bit, 0x07=无效
  electricScooterLightStatus   = l76.G(data, 8, 4)     // byte8 bit4-5, 2bit
  accountPermissions           = l76.H(data, [..])     // 多字节
  electricScooterPowerGear     = l76.d(data, 0xA, 4)   // byte10 bit4-7, 4bit
  electricScooterMotorTemperature = l76.m(data, 0xB)   // byte11, 若 != -1 则 -0x28 (减40)
  ...
```

> [!IMPORTANT]
> `parkingStatus` 是 **byte8 的低 3 位**，不是整字节。
> docs 只说"0=已锁,1=未锁"，没说位宽与无效值 `0x07`。
> **移植时必须逐字段照抄 offset + shift + 位宽 + 无效值 + 缩放**，
> 任何一项错了都会静默产生错误数据。

### 7.3 缩放示例

```text
electricScooterMotorTemperature:
    raw = l76.m(data, 0xB)
    if (raw != -1) raw -= 0x28        // 减 40 偏移
```

`TtpInfo` 中还有 `-1.0f`、`-0x40800000` 等浮点无效值判断，需逐个核对。

---

## 8. ✅ 证实：命令并发锁

`BleControlManager.Q0()` 在 `E`（发命令）和 `L` 入口都做前置检查：

```text
E(device, commandId, callback):
    if (!Q0()) { callback(TaskException()) ; return }
    ...

L(device, callback):
    if (!Q0()) { callback(TaskException()) ; return }
    ...
```

✅ 与 docs 的"`AtomicInteger` 0→1 才放行、忙碌时回调 `TaskException`"一致。

---

## 9. 对实施计划的影响

### 9.1 ⚠️ 新增硬依赖：Phase 6 必须先于 Phase 2 的握手

握手帧需要 token，token 来自 `ud5.r(userId, mac)` 或网络 `NetTokenHelper`。

```mermaid
flowchart LR
  A["Phase 6 登录/账号<br/>（拿 token）"] --> B["Phase 2 配对握手<br/>（32 字节鉴权帧）"]
  B --> C["Phase 3/4 数据通信"]
```

> [!CAUTION]
> 原计划把登录排在最后、且标注"可并行"。**实际不可并行**：
> 没有 token 就无法完成 BLE 握手，也就无法进入数据通信阶段。
> 建议调整顺序：**Phase 1（协议）→ Phase 6（登录/取 token）→ Phase 2（握手+状态机）→ Phase 3/4**。
>
> 替代方案：若目标是"不依赖账号也能控车"，需确认是否存在工厂模式
> （`E0` 的 `isFactory` 参数暗示有绕过 token 的路径），这需要进一步挖 `wi.i` 字段与
> `NetTokenHelper` 的分支。

### 9.2 ⚠️ Phase 1 的解析层工作量大增

`l76.smali` 有 12724 行、`TtpInfo` 有 295 字段。
每个字段都要抄 `offset + shift + 位宽 + 无效值 + 缩放`。
**这不能再当成"顺手实现"**，应单独立项（建议叫 Phase 1.5），并建立对拍测试。

### 9.3 ✅ 可简化的部分

- 8 个经典命令已逐字节确认，可直接硬编码 + 单测。
- 帧组装模式已确认（`head + orderId + len + payload + check + tail`，整体大写）。
- 校验算法确认：SUM8 后 `toHexString`，仅当长度 1 时补零。

### 9.4 需要用户决策

| 问题 | 选项 |
| --- | --- |
| `2a26` 缺陷是否复刻 | (a) 复刻 (b) 修正为 `2a28`/`2a27`（推荐）(c) 先试 `2a27` 再回退 `2a26` |
| 是否接受"必须先登录才能控车" | (a) 接受，调整实施顺序 (b) 先挖 `isFactory` 分支找绕过路径 |
| `l76` 295 字段的移植范围 | (a) 全量 (b) 先做 AGENTS.md 已确认的 11 个字段 |
| 握手帧细节验证方式 | (a) 实车抓包 (b) 继续挖 `ud5`/`NetTokenHelper`/`r85` smali |

---

## 10. 下一步建议

继续挖 smali 的优先级（按信息价值排序）：

1. **`ud5.smali`** — token 生成/存储（决定握手可行性）
2. **`NetTokenHelper.smali`** — token 网络获取（`iot-auth/ble/token*`）
3. **`wi.M0` / `wi.U` / `wi.V` / `wi.X` / `wi.Y`** — 握手帧各字段来源
4. **`r85.smali`** — 常量表（`g()`/`b()`）
5. **`l76.z` 全文** — 295 字段完整映射（体量大，可分批）
6. **`BleBicycleConnect.smali`** — 15s/20s/5s 计时器与重连语义的精确行号
