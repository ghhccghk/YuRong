# 握手与鉴权

本文描述车辆 BLE 配对握手的帧结构与 token 机制。依据 `temp_smali/wi.smali`、
`temp_smali/com/yadea/blecontrol/network/NetTokenHelper.smali`、`temp_smali/ud5.smali`
的逐行核对。

## 1. 握手帧不是 `5944…4B4A` 帧

握手/鉴权帧与普通控制帧**结构完全不同**：

| 维度 | 控制帧 | 握手帧 |
| --- | --- | --- |
| 起始 | `5944` | **token** |
| 标记 | `ORDER_ID`（`3E`） | `5944FFFFFFFF` |
| 长度 | 变长 hex | **固定 32 字节** |
| 校验 | SUM8 | **XOR 折叠** |
| 结尾 | `4B4A` | 无 |

两者共用 `5944` 字符串纯属巧合，**必须是两个独立函数**。

## 2. 握手帧构造

调用链：

```text
wi.E0(device, randKey, isFactory, extParams, cb, onFail)
  → token = ud5.r(extParams.userId, device.mac)
  → token 为空时走 NetTokenHelper.d(...) 联网获取
  → 否则 wi.D0 → wi.E
```

`wi.E` 的构造：

```text
tail2 = if (wi.q0()) "FE" else "FF"
p3    = wi.b0(isFactory, wi.i)
rand  = wi.U(device, randKey, wi.i)

if (wi.s0(randKey)):
    id3  = r85.g()                 // "发送id 3"
    p4   = wi.X(r85.b())           // 偏移表
    p5   = wi.Y(id3, r85.b())
    rand = wi.V(device, randKey, wi.i)

raw = token + "5944FFFFFFFF" + p3 + p4 + wi.P0(randKey, 6, 3) + p5 + tail2 + "FFFF"
out = hexDecode(raw).copyOf(0x20)      // 强制 32 字节，不足补 0
out[31] = XOR(out, 31)                 // XOR 前 31 字节
```

辅助函数：

```text
wi.P0(data, 6, 3) = data[6..8] 各 "%02X" 拼接（越界返回 "FF"）
wi.G(data, len)   = 逐字节 XOR 折叠
```

## 3. 鉴权可绕过

`NetTokenHelper.d` 开头即有守卫：

```text
if (!extParams.isAuthToken) {         // Boolean?，null 视为 false
    callback.onError(-1, "No authentication is required")
    return                             // 完全跳过 token 获取
}
```

`ExtendedParamsEntity` 构造参数顺序：

```text
(vin, userId, mac, isAuthToken: Boolean?, connectType: ConnectType)
```

`connectType` 默认 `ConnectType.b`（`APP_TYPE_CLIENT`）。

`ConnectType` 枚举：

| 枚举名 | code |
| --- | ---: |
| `APP_TYPE_CLIENT` | 0 |
| `APP_TYPE_BUSINESS` | 1 |
| `APP_TYPE_FACTORY` | 2 |
| `APP_TYPE_WATCH` | 3 |

跳过条件：

| 检查 | 跳过条件 |
| --- | --- |
| `isAuthToken` | `!= true` 时完全跳过 token 流程 |
| `userId` 非空 | `connectType == APP_TYPE_BUSINESS` |
| `vin` 非空 | `connectType == APP_TYPE_FACTORY` |

> **`isAuthToken == false` 时握手不需要 token。**
> 若目标场景走此路径，登录体系不是连接的前置条件。

## 4. Token 存储

`ud5.smali`：

```kotlin
key   = "key_vehicle_token_${userId}_${mac}"
store = EncryptedSharedPreferences        // androidx.security.crypto
read  = ud5.r(userId, mac)                 // 失败返回 ""，不抛异常
write = ud5.A(userId, mac, token)          // commit() 同步写
```

异常处理是 `catchall` + `printStackTrace()`，**静默失败**。

> token 被视为敏感凭证。移植时若放 `commonMain`，需要抽象 `SecureTokenStore`；
> Android 侧可用 `EncryptedSharedPreferences`，其他平台需降级方案并明确告知用户。

## 5. Token 获取接口

`NetTokenHelper` 的三个路径：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `getBleSecretKey` | `iot-auth/ble/secret-key/get` | 取密钥 |
| `getVehicleBleToken` | `iot-auth/ble/token` | 取单车 token |
| `getVehicleBleTokenList` | `iot-auth/ble/token-list` | 批量 |

均通过 `YdNetRequester` 发起，带 `retryWithTimeout` 重试封装。

数据模型：

```kotlin
data class BleTokenRequest(
    val mac: String,
    val refreshToken: String,
    val version: String,
    val vin: String,
)

data class BleTokenBean(
    val mac: String,
    val vin: String,
    val dkInfo: String,        // ← 真正的密钥材料
    val dkInfoMax: Int,
)
```

> `dkInfo` 才是密钥材料，不是名为 `token` 的字段。
> `wi.E0` 读出的也是它，命名有误导性。

## 6. 失败码

| 条件 | 日志 |
| --- | --- |
| mac 为空 | `iot-auth/ble/secret-key/get_-1_蓝牙地址是空` |
| userId 为空且 `connectType != BUSINESS` | `iot-auth/ble/token_-1_用户ID是空` |
| vin 为空且 `connectType != FACTORY` | `iot-auth/ble/token_-1_车架号是空` |
| `isAuthToken == false` | `iot-auth/ble/token_-1_未配置Token鉴权` |

## 7. 待确认

以下项目 smali 中未完整读到，需继续核对或实车抓包：

| 项 | 影响 |
| --- | --- |
| `wi.b0` / `wi.U` / `wi.V` / `wi.X` / `wi.Y` 的实现 | 握手帧 `p3`/`p4`/`p5` 的具体来源 |
| `r85.g()` / `r85.b()` 的语义 | "发送id 3" 与偏移表的含义 |
| `wi.q0()` 的实现 | `tail2` 取 `FE` 还是 `FF` |
| 服务端如何校验该 32 字节帧 | 需抓包 |
| 配对密钥协商流程 | 需实车验证 |

> 握手帧的完整性依赖这些字段，在未确认前无法保证与真车通信成功。
> 建议先用 `isAuthToken = false` 路径验证基础链路，再补鉴权。
