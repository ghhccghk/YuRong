# 车辆 BLE 连接

本文描述连接状态机、超时与重连语义、初始化任务队列。依据
`temp_smali/com/yadea/blecontrol/connect/`、`temp_smali/wi.smali`、
`temp_smali/com/yadea/blecontrol/control/BleControlManager.smali` 的逐行核对。

## 1. 状态枚举

```kotlin
/** 底层连接器状态 */
enum class LinkState { IDLE, CONNECTING, CONNECTED, FAILURE, DISCONNECT }

/** 对外只暴露这 3 态 */
enum class PublicState { CONNECTING, CONNECTED, DISCONNECTED }

enum class DisconnectType(val code: Int) {
    TARGET_DISCONNECTED(1), CONNECT_FAILED(2), TIMEOUT_15S(3), SCAN_NOT_FOUND(4),
    PAIR_ERROR_5(5), FW_VERSION_FAILED(6), PAIR_FAILED(7), PAIR_ERROR_8(8), PAIR_ERROR_9(9),
}

enum class VehicleException(val code: Int) {
    TIMEOUT(100), GATT(101), OTHER(102), TASK(201), BLE(300),
}
```

**配对握手成功后**才上报 `CONNECTED`，不是 GATT 连上就算。

## 2. 去重规则

```text
只处理 device == null 或 device.mac == 当前目标 MAC 的事件
同一 state 的重复事件直接丢弃
state == CONNECTED  → 记录"当前连接设备"
state == DISCONNECTED → 清空
```

## 3. 转移表

| 当前 | 事件 | 下一状态 | 动作 |
| --- | --- | --- | --- |
| `IDLE` | 扫描命中 MAC / 直连 | `CONNECTING` | 上报 `(device, 1, 0)` |
| `CONNECTING` | `onConnectionStateChange(newState=2)` | `CONNECTING` | 延迟 500ms → `discoverServices()` |
| `CONNECTING` | `onServicesDiscovered(status=0)` | `CONNECTED` | 配对握手 → 上报 `(device, 2, 0)` → 启动任务队列 |
| `CONNECTING` | `status==0x85 && newState==0` | `FAILURE` | 反射 `BluetoothGatt.refresh()` 清缓存 |
| `CONNECTING` | `discoverServices()` 返回 false 或 status≠0 | `FAILURE` | `OtherException("GATT discover services exception occurred!")` |
| `CONNECTING` | 20s 超时 | `FAILURE` | `TimeoutException("Timeout Exception Occurred!")` |
| `CONNECTED` | `newState==0` | `DISCONNECT` | 清空当前设备，**不重连** |
| 其他 | `newState==0` | 不变 | 不发状态消息 |

### 3.1 133 错误规避

`status == 0x85`（133）且 `newState == 0` 时，反射调用 `BluetoothGatt.refresh()` 清设备缓存。
这是已知的 133 规避手段，**必须保留**。

## 4. 超时与重试

所有值均由 smali 常量确认：

| 计时器 | 值 | smali 常量 | 触发后果 |
| --- | ---: | --- | --- |
| 扫描超时 | **15 s** | `0x3a98` | 停扫描 + 断开 GATT + 上报 `(null, 0, TIMEOUT_15S)` |
| GATT 连接超时 | **20 s** | `0x4e20` | `CONNECT_FAILURE` + `TimeoutException` |
| GATT 操作超时 | **5 s** | `0x1388` | 单次读写/通知/MTU 失败 |
| 连上后延迟 | **500 ms** | `0x1f4` | 延迟后 `discoverServices()` |
| 任务重试间隔 | **200 ms** | `0xc8` | 失败任务重试前等待 |
| 重连间隔 | **5 s** | `0x1388` | 仅连接失败场景 |
| 最大重连次数 | **1** | — | 共 2 次尝试 |

> 原分析文档提到的"看门狗 tick 1000ms"**未在 smali 中找到 `0x3e8` 常量**。
> 已确认的只有上表五档。

### 4.1 重连语义（反直觉，必须复刻）

- **只有「连接中收到断开」**才在 5 s 后重连 1 次
- **已连接后断开不重连**
- **连接超时、服务发现失败也不重连**

### 4.2 任务重试语义

`maxRetries = 2` 表示**最多 3 次尝试**。

- 成功 → 50 ms 后执行下一项
- 失败 → 200 ms 后重试
- 超过重试次数 → **跳过该任务继续下一项，不终止整个队列**
- 仅「强制任务 + 连接看门狗已过期」才以外层失败终止

## 5. 初始化任务队列

固定 5 项，顺序不可变：

| 顺序 | 任务 | 动作 |
| ---: | --- | --- |
| 1 | `READ_MANUFACTURER_NAME` | 读 `180a` / `2a29` |
| 2 | `READ_FIRMWARE_VERSION` | 读 `180a` / `2a26` |
| 3 | `READ_HARDWARE_VERSION` | 读 `180a` / `2a26` |
| 4 | `ENABLE_BLUETOOTH_NOTIFICATION` | 按协议版本选 UUID 订阅 |
| 5 | `QUERY_VEHICLE_STATUS` | 写整车状态查询命令 |

### 5.1 固件与硬件版本读取同一特征

`wi.R0`（固件）与 `wi.S0`（硬件）都传 `00002a26`，
且 `S0` 的异常文案也是复制自 `R0` 的 `"read firmware revision error"`。
`2a27`（Hardware Revision）常量在 `yi.smali` 中存在但**全项目零引用**。

**这是原实现的真实缺陷。** 处理建议：

1. **修正为 `2a28` / `2a27`**（推荐）：这是本机读取、不影响车端，
   复刻只会让"硬件版本"永远等于"固件版本"，属无意义的数据污染
2. 先读 `2a27`，失败再回退 `2a26`：兼顾兼容性
3. 照 smali 原样复刻：仅当需要与官方 App 行为完全一致时

## 6. 通知节流

```text
距上次通知 > 100 ms → 立即发送
否则               → 延迟 110 ms
```

状态上报的全局节流是**严格大于 2000 ms**，且是**全局字段**（非 per-device）。

## 7. 命令并发锁

`BleControlManager.Q0()` 用 `AtomicInteger`：`0 → 1` 才放行。
忙碌时回调 `TaskException`。`CommandBean.isPlural == true` 可绕过。

## 8. 平台边界

### 8.1 必须放 `androidMain`

- `BluetoothGatt` / `BluetoothLeScanner` / `connectGatt` / `writeDescriptor` / `requestMtu`
- `BluetoothGatt.refresh()` 反射调用（133 规避）
- 运行时权限检查与请求
- `BluetoothGattCallback` 与主线程 Handler 消息状态机
- 前台服务（保持 BLE 连接与扫描存活）
- 广播接收器、音频播放
- `PreferenceStore` 的 Android 后端

### 8.2 放 `commonMain`

- 状态枚举、`DisconnectType` 分类、事件定义
- 转移表（纯函数，可单测）
- 超时/重试/任务队列编排（注入 `Clock` 与 `Transport` 接口）
- 协议编解码（见 [protocol.md](./protocol.md)）
- 状态解析（见 [ttpinfo-fields.md](./ttpinfo-fields.md)）

### 8.3 Desktop / Web / iOS

标准 JVM 无跨平台 BLE API，各平台需提供 `isAvailable = false` 的安全实现，
保证 `commonMain` 编译不被破坏。

> 当前 `shared/src/commonMain/.../ble/BleAdapter.kt` 的 `expect fun createBleAdapter()`
> 在 jvm / js / wasm / ios 缺少 `actual`。**新增任何 `expect` 之前必须先补齐这些平台的实现。**

## 9. 权限

| 权限 | 适用 | 说明 |
| --- | --- | --- |
| `BLUETOOTH_SCAN` | API 31+ | 若确实不用于定位可加 `neverForLocation` |
| `BLUETOOTH_CONNECT` | API 31+ | 连接与读设备信息必需 |
| `ACCESS_FINE_LOCATION` | API ≤ 30 | 旧版扫描必需 |
| `POST_NOTIFICATIONS` | API 33+ | 前台服务通知 |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API 34+ | BLE 长连接前台服务类型 |

当前 `androidApp/src/main/AndroidManifest.xml` 已声明前四项，
`FOREGROUND_SERVICE_CONNECTED_DEVICE` 也已声明，但尚未有对应服务。

## 10. 单测设计

状态机、超时与任务队列全部可用假时钟驱动：

```kotlin
interface VehicleTransport {              // 平台无关
    suspend fun discoverServices(): Int
    suspend fun read(service: String, char: String): String?
    suspend fun write(service: String, char: String, data: ByteArray): Boolean
    suspend fun setNotification(service: String, char: String, enable: Boolean): Boolean
    suspend fun requestMtu(mtu: Int): Int
    fun close()
}

class FakeClock(var now: Long = 0L) : Clock {
    override fun nowMs() = now
    fun advance(ms: Long) { now += ms }
}
```

覆盖用例：

| 场景 | 断言 |
| --- | --- |
| 扫描命中 → 连接成功 → 服务发现 → 任务队列 | 最终 `CONNECTED` |
| `newState=2` 后 500 ms 才 discover | 假时钟推进验证时序 |
| `status=0x85` | 触发 `refresh()` 且进入 `FAILURE` |
| 20 s 无响应 | `TimeoutException`，**不重连** |
| 服务发现失败 | `FAILURE`，**不重连** |
| 连接中收到断开 | 5 s 后**重连 1 次** |
| 已连接后断开 | `DISCONNECT`，**不重连** |
| 任务失败 3 次 | 跳过，继续下一项 |
| 同一 state 重复事件 | 被丢弃 |
| 非目标 MAC 事件 | 被丢弃 |

## 11. 待确认

| 项 | 说明 |
| --- | --- |
| 15 s 看门狗的 tick 值 | 未找到 `0x3e8` 常量 |
| 配对握手的字段来源 | 见 [handshake.md](./handshake.md) |
| 多设备 LRU 容量 7 的实际用途 | 原实现 `l.P(mac)` 恒返回 false，属缺陷 |
