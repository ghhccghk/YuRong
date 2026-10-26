# 车辆 BLE 协议层

本文描述车辆控制帧、校验算法、字节序与位域读取原语。所有内容来自
`temp_smali/BleBicycleCommandManager.smali`、`temp_smali/wi.smali`、`temp_smali/l76.smali`、
`temp_smali/b72.smali` 的逐行核对。

## 1. 两套 `5944/4B4A` 必须隔离

车辆控制帧与投屏帧（[YadeaProtocol.kt](../../shared/src/commonMain/kotlin/top/ghhccghk/yadea/protocol/YadeaProtocol.kt)）
共用 `5944`（"YD"）与 `4B4A`（"KJ"）字符串，但**结构完全不同**：

| 维度 | 车辆控制帧 | 投屏帧 |
| --- | --- | --- |
| 协议 ID | `3E` / `3A` / `3B` | `3C` |
| 长度编码 | 变长 hex 字符串 | BE32 固定 4 字节 |
| 校验范围 | `ORDER_ID + LEN + PAYLOAD` | `3C + INNER_LEN + INNER_PACKET` |
| 内部结构 | 无内层包 | `TYPE + PAYLOAD_LEN + PAYLOAD` |

**两者必须各留一套构建函数，禁止复用。**

另外 `5944FFF0-A000-B000-C000-000000000000`、`5944FFF1-...`、`5944FFF2-...` 是
**部件（parts）通道的 Service/Notify/Write UUID**，与帧头同名前缀纯属巧合。命名时需区隔，
例如放在 `PartsUuids` 对象下。

## 2. 帧格式

### 2.1 控制帧

```text
frame = "5944" + ORDER_ID + intToHex2(payloadByteLen)
      + payloadHex + CHECK + "4B4A"
CHECK = checkHex(SUM8(ORDER_ID + intToHex2(payloadByteLen) + payloadHex))
最终整体 toUpperCase(Locale.ROOT)
```

常量（`BleBicycleCommandManager.<init>`）：

| 常量 | 值 |
| --- | --- |
| `ORDER_HEAD` | `"5944"` |
| `ORDER_TAIL` | `"4B4A"` |
| `ORDER_ID` | `"3E"` |
| `EXTERNAL_ORDER_ID` | `"3A"` |
| `ORDER_ID_3B` | `"3B"` |
| `ORDER_LENGTH` | `"1E"` |
| `bleCommBigVersion` | `"00"` |
| `bleCommSmartVersion` | `"01"` |

### 2.2 两个十六进制转换函数规则不同

这是最容易踩的坑。原实现有**两个**函数，判据不同：

```kotlin
/** BleBicycleCommandManager.decInt2hexString */
fun Int.toDecHex(): String {
    val s = toString(16)
    return if (s.length % 2 == 1) "0$s".uppercase() else s.uppercase()
}

/** BleBicycleCommandManager.getCheckResult 内部 */
fun Int.toCheckHex(): String {
    val s = toString(16)
    return if (s.length == 1) "0$s" else s      // 判据是 ==1，且不转大写
}
```

| 输入 | `toDecHex` | `toCheckHex` |
| ---: | --- | --- |
| `1` | `"01"` | `"01"` |
| `15` | `"0F"` | `"0f"` |
| `16` | `"10"` | `"10"` |
| `256` | `"0100"` | `"100"` |

对 SUM8（最大 2 位）而言两者结果等价，但**必须分开实现**，
否则长度超过 255 时会出现不一致。外层统一 `toUpperCase`。

### 2.3 长度字段取字节数

原实现中 `payload` 是 **hex 字符串**（每字节 2 个字符），因此长度字段是 `payload.length / 2`。

> 移植时若直接传 `ByteArray.size` 作为长度，会**翻倍**。
> 建议接口接收 hex 字符串，或接收 `ByteArray` 后自行换算并写注释说明。

### 2.4 3B 专用帧

```text
hexArg 每 2 字符反向拼接
  + "FFFFFFFFFF"
  + getIncreaseByteHexStr()
```

反向拼接示例：`"AABBCCDD"` → `"DDCCBBAA"`。

若 `hexArg.length` 为奇数，`substring` 抛异常被 `catch(Exception)` 吞掉，载荷静默为空。

### 2.5 填充字节

```text
increaseControlByte(n):
    k = min(n, 10)
    ORDER_LENGTH = toHexString(0x1E + k)      // 原实现用裸转换，非 toDecHex
    increaseByteNum = k

getIncreaseByteHexStr():
    "FF".repeat(max(0, increaseByteNum))
```

`FF` 是**填充**，不是分包序号。

## 3. 校验算法

```kotlin
object Checksums {
    /** 8 位算术和 */
    fun sum8(bytes: ByteArray): Int = bytes.sumOf { it.toInt() and 0xFF } and 0xFF

    /** CRC16-XMODEM：poly 0x1021，init 0x0000，不反转，final 0x0000 */
    fun crc16Xmodem(data: ByteArray): Int

    /** CRC32-MPEG-2：poly 0x04C11DB7，init 0xFFFFFFFF，MSB-first，final 0 */
    fun crc32Mpeg2(data: ByteArray): Long

    /** out[i] = frame[i] xor ((i + 10) and 0xFF)；CRC 字节也参与 */
    fun scooterEncrypt(frame: ByteArray): ByteArray

    /** 逐字节 XOR 折叠，握手帧收尾用 */
    fun xorFold(data: ByteArray, len: Int): Byte
}
```

`crc16XmodemHex` 的后处理：奇数长度补 `0`；长度为 2 时前补 `"00"`。
`crc32Mpeg2Hex`：奇数长度补 `0`；长度为 6 时前补 `"00"`。

## 4. 字节序（易错）

`b72.smali` 的组装函数：

```text
b72.q(b0,b1)    = p(b0) + (p(b1) shl 8)           → 16 位小端
b72.s(b0..b3)   = r(b0,b1,b2) + (p(b3) shl 24)   → 32 位小端
b72.t(b0,b1)    = (p(b0) shl 8) + p(b1)          → 16 位大端
b72.u(b0,b1,b2) = (p(b0) shl 16) + t(b1,b2)      → 24 位大端
```

对应的解析器：

| 解析器 | 使用的组装 | 字节序 | 无效判据 |
| --- | --- | --- | --- |
| `l76.H` | `b72.q` | **小端** | 结果 `== 0xFFFF` |
| `l76.e` | `b72.s` | **小端** | 结果 `== -1` |
| `l76.J` | 手写 | **大端** | 无 |

**同一份报文里两种字节序混用**，不能统一处理。误用会让车速、电压、里程出现
"数值离谱但不崩溃"的现象，极难定位。

## 5. 位域读取原语

`l76.smali` 中的提取器，越界或命中无效值时返回 `-1`：

| 方法 | 掩码 | 位宽 | 无效值 |
| --- | --- | ---: | --- |
| `m(d, off)` | `0xFF` | 8 | `0xFF` |
| `a(d, off, shift)` | `0xFF << shift` | 8-shift | 全 1 |
| `C(d, off, shift)` | `0x07 << shift` | 3 | `0x07` |
| `G(d, off, shift)` | `0x03 << shift` | 2 | `0x03` |
| `d(d, off, shift)` | `0x0F << shift` | 4 | `0x0F` |
| `c(d, off, shift)` | `0x1F << shift` | 5 | `0x1F` |
| `l(d, off, shift)` | `0x01 << shift` | 1 | `0x01` |

统一实现：

```kotlin
private fun bits(d: ByteArray, off: Int, shift: Int, mask: Int): Int {
    if (off < 0 || d.size <= off) return -1
    val v = ((d[off].toInt() and 0xFF) ushr shift) and mask
    return if (v == mask) -1 else v      // 无效值就是掩码本身
}
```

> [!WARNING]
> **[`l76.l`]（1 位提取器）的行为很反直觉。**
> 它的掩码是 `0x01`，而“无效值”判定也是 `0x01`，因此**位值为 1 时会被判为无效**。
> 结果是这个提取器只能表达两种状态：
>
> | 位值 | 返回 |
> | ---: | ---: |
> | `0` | `0` |
> | `1` | `-1`（无效） |
>
> 换句话说它**无法表达“已置位”**。这是原实现的忠实行为，
> 移植时保留即可，但不要把它当成常规布尔读取使用，也不要“顺手修正”。
> 测试见 `TtpInfoParserTest.l_treatsSetBitAsInvalid`。

## 6. 条件性缩放

```text
const v11, 0x3dcccccd          // 0.1f
cmpl-float v14, v13, v9        // v9 是哨兵
if-eqz v14, :skip              // 相等则跳过
mul-float/2addr v13, v11
:skip
```

**等于哨兵时不缩放。** 若实现成无条件乘，无效值 `-1.0f` 会变成 `-0.1f`，
静默产生错误数据。

各字段的缩放与偏移见 [ttpinfo-fields.md](./ttpinfo-fields.md)。

## 7. 命令表

### 7.1 自行车经典命令

未经 `5944` 包封，直接写特征值。逐字节来自 `ClassicCommands.<clinit>`：

| 常量 | Hex | 语义 |
| --- | --- | --- |
| `CAR_COMMAND_LOCK` | `07 01 01` | 锁车 |
| `CAR_COMMAND_UNLOCK` | `07 01 02` | 开锁 |
| `CAR_COMMAND_WHISTLE` | `07 01 03` | 寻车 |
| `CAR_COMMAND_ON` | `07 01 04` | 开机 |
| `CAR_COMMAND_CUSHION` | `07 01 09` | 坐垫 |
| `CAR_COMMAND_ODO_RESET` | `07 01 0A` | ODO 清零 |
| `CAR_COMMAND_DETECT` | `07 01 0D` | 检测 |
| `CAR_COMMAND_OFF` | `07 01 0E` | 关机 |

命令 ID 分派：`0x08`→ON、`0x07`→OFF、`0x06`→LOCK、`0x04`→UNLOCK、
`0x03`→WHISTLE、`0x05`→DETECT、`0x31`→ODO_RESET、`0x09`→CUSHION、`0x4C`→氛围灯。

### 7.2 滑板车命令映射

`t()` 映射表，仅 X1/EiBike 生效：

| 原始 `commandType` | 映射后 | param |
| --- | --- | ---: |
| `SCOOTER_GEAR_1..6` | `DRIVING_MODE_SWITCH` | 0..5 |
| `SCOOTER_GEAR_X` | `DRIVING_MODE_SWITCH` | 3 |
| `SCOOTER_ENERGY_RECOVERY_WEAK/MEDIUM/STRONG` | `BRAKE_RECOVER_LEVEL` | 1/2/3 |
| `SCOOTER_CLOSE_CRUISE_CONTROL` | `CRUISE_CONTROL_CLOSE` | 不变 |
| `SCOOTER_OPEN_CRUISE_CONTROL` | `CRUISE_CONTROL_OPEN` | 不变 |
| `SCOOTER_UNLOCK` | `RELEASE_FORTIFY` | 不变 |
| `SCOOTER_LOCK` | `FORTIFY` | 不变 |

已知 numeric ID：`DRIVING_MODE_SWITCH=0x8F`、`BRAKE_RECOVER_LEVEL=0x3D`、
`CRUISE_CONTROL_CLOSE=0x93`、`FORTIFY=3`。

滑板车 payload 表（发送前加 CRC16-XMODEM 与 XOR）见原分析中的
`SCOOTER_COMMAND_*` 常量，实现时逐条录入。

### 7.3 写入成功不等于执行成功

`onControlSuccess` **只代表 GATT 写成功**，不是车辆执行 ACK。

UI 上只能显示"指令已发送"，不能显示"开锁成功"。真实状态需要等车辆状态通知回报。

### 7.4 自动选择 BLE / 车联网

原 App 的 `VehicleService` 先判断 BLE 是否连接、车辆 `onlineState` 是否为真，
再由 `BicycleControlHelper` 决定本地下发还是调用
`protocol/user/control-vehicle`。BLE 不可用且车辆在线时自动使用云端；
BLE 写失败时也可以安全地切换到云端。

云端 `clientId=1`、`controlMode=11`，已确认的控制 ID：

| 操作 | BLE commandId | 云端 `id` |
| --- | ---: | --- |
| 开机 | `0x08` | `START` |
| 关机 | `0x07` | `STOP` |
| 寻车 | `0x03` | `FIND` |
| 锁车 | `0x06` | `FORTIFY` |
| 开锁 | `0x04` | `RELEASE_FORTIFY` |

## 8. UUID

### 8.1 车辆主链路

| 名称 | 值 |
| --- | --- |
| Service | `0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc` |
| Characteristic | `0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc`（同时具备 NOTIFY 与 WRITE） |

### 8.2 设备信息

| 名称 | 值 |
| --- | --- |
| Service | `0000180a-0000-1000-8000-00805f9b34fb` |
| Manufacturer Name | `00002a29-0000-1000-8000-00805f9b34fb` |
| Firmware / Hardware Revision | `00002a26-0000-1000-8000-00805f9b34fb` |
| Software Revision | `00002a28-0000-1000-8000-00805f9b34fb` |
| Hardware Revision | `00002a27-0000-1000-8000-00805f9b34fb` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` |

> `2a27` 常量存在但全项目**零引用**。

### 8.3 状态通知（按协议版本分支）

| 版本 | Service | Notify | 状态查询写 |
| --- | --- | --- | --- |
| `== 1` | `e7810bd2-73ae-499d-8c15-faa9aef0c3f2` | `bef8e820-9c21-4c9e-b632-bd58c1009f9f` | `bef8e821-9c21-4c9e-b632-bd58c1009f9f` |
| 其他 | `e7810b82-73ae-499d-8c15-faa9aef0c3f2` | `bef8e7db-9c21-4c9e-b632-bd58c1009f9f` | `bef8e7da-9c21-4c9e-b632-bd58c1009f9f` |

### 8.4 部件通道（易与帧头混淆）

| 值 | 角色 |
| --- | --- |
| `5944FFF0-A000-B000-C000-000000000000` | Service |
| `5944FFF1-A000-B000-C000-000000000000` | Notify |
| `5944FFF2-A000-B000-C000-000000000000` | Write |

### 8.5 滑板车

| 角色 | 值 |
| --- | --- |
| Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| Write | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |

## 9. 协议版本判定与通知分派

```text
if (data.size > 10):
    if (data.size == 14 && data[9] == 0x41 && data[10] == 0x05) -> 0xF1
    elif (data[9] < 0xFF && data[10] < 0xFF)                     -> 1
    else                                                          -> 0xF1
else:
    -> 0xF1
```

**顺序敏感。** 协议版本 1 时按 `data[2]` 分派：

| `data[2]` | 解析为 |
| --- | --- |
| `0x2A` / `0x7A` | `TtpInfo`（完整） |
| `0x29` | `TtpInfo`（短） |
| `0x2B` | `ExternalDeviceTtpInfo` |
| `0xEA` / `0xE9` | `AlipayNfcInfo` |
| `0xB0`–`0xB4` / `0xB8` | `PartsTtpInfo` |
| 以 `0x5A` 开头 | `ScooterStatusInfo` |

旧协议按 hex 空格拆分，`tokens[0] == "04"` 时 `tokens[1] == "01"` 为 `PanelInfo`、
`"02"` 为 `FaultInfo`。

## 10. 单测向量

| 用例 | 输入 | 期望 |
| --- | --- | --- |
| 锁车帧 | `orderId="3E"`, payload `"070101"` | `59443E030701014A4B4A` |
| `toDecHex` | `1` / `15` / `16` / `256` | `"01"` / `"0F"` / `"10"` / `"0100"` |
| `toCheckHex` | `5` / `0x2A` | `"05"` / `"2a"` |
| `sum8` | `[0x3E,0x03,0x07,0x01,0x01]` | `0x4A` |
| `crc16Xmodem` | `"123456789"` | `0x31C3` |
| `scooterEncrypt` | `[0x5A]` | `[0x5A xor 10]` |
| 反向拼接 | `"AABBCCDD"` | `"DDCCBBAA"` |
| `getIncreaseByteHexStr` | `3` | `"FFFFFF"` |
| 小端 `H` | `[0x34,0x12]` | `0x1234` |
| 大端 `J` | `[0x12,0x34]` | `0x1234` |
| 位域无效 | `C(d,8,0)`，`d[8]=0x07` | `-1` |

> 锁车帧的校验值按 `(0x3E + 0x03 + 0x07 + 0x01 + 0x01) and 0xFF = 0x4A` 推算。
> 实现后以单测实跑结果为准。
