# smali 修正（第三轮）：帧构造细节

承接 [smali-confirmed-facts.artifact.md](file:///C:/Users/DozenOregano696/AppData/Local/Google/AndroidStudio2026.2.1/projects/toupin.4c1ef0d2/.artifacts/b8e65a89-faca-48bd-bab3-80ebc22cbb4c/smali-confirmed-facts.artifact.md)。
本文修正其中关于长度字段编码的**错误结论**，并补齐帧构造的完整细节。

---

## 1. ⚠️ 重大修正：`decInt2hexString` 补零规则

我此前写的是：

> ~~长度字段 `Integer.toHexString(len)` 原样，不补零 → len=1 得到 `"1"`~~

**这是错的。** `BleBicycleCommandManager.decInt2hexString` 的实际实现（行 388）：

```text
decInt2hexString(i):
    s = Integer.toHexString(i)
    if (s.length() % 2 == 1) s = "0" + s      // ← 奇数长度补一个 0
    return s.toUpperCase()                     // ← 还要转大写
```

正确语义：

| 输入 | `toHexString` | `%2==1` | 输出 |
| ---: | --- | --- | --- |
| `1` | `"1"` | 是 | `"01"` |
| `3` | `"3"` | 是 | `"03"` |
| `15` | `"f"` | 是 | `"0F"` |
| `16` | `"10"` | 否 | `"10"` |
| `0x1E`(30) | `"1e"` | 否 | `"1E"` |
| `0x1F`(31) | `"1f"` | 否 | `"1F"` |
| `255` | `"ff"` | 否 | `"FF"` |
| `256` | `"100"` | 是 | `"0100"` |

> [!IMPORTANT]
> **三个要点，缺一不可：**
> 1. **奇数长度才补零**（补 1 个，不是固定补到 N 位）
> 2. **结果转大写**
> 3. 偶数长度**不动**
>
> 所以 payload 长度 3 → 长度字段是 `"03"`，**不是 `"3"`**。
> 我上一轮基于 `Integer.toHexString` 直接推断，漏看了 `%2` 判断与 `toUpperCase`。

### 1.1 与校验字符的关系

`getCheckResult`（行 125）**独立**做类似处理：

```text
sum = Σ u8(byte) & 0xFF
s = Integer.toHexString(sum)
if (s.length() == 1) s = "0" + s      // ← 判据是 ==1，不是 %2
return s                               // ← 不转大写（外层统一转）
```

| 方法 | 判据 | 补零数 | 大写 |
| --- | --- | --- | --- |
| `decInt2hexString` | `length % 2 == 1` | 补 1 个 | ✅ 就地转 |
| `getCheckResult` | `length == 1` | 补 1 个 | ❌ 靠外层 |

对 SUM8 而言两者结果**等价**（最大 2 位，`%2==1` 与 `==1` 同义），但**实现时要分开写**，
否则将来扩展（长度 >255）会出错。

---

## 2. ✅ 帧构造完整确认

以 `EXTERNAL_ORDER_ID` 路径（行 5163 附近）为完整样本：

```text
sb = ""
sb += ORDER_HEAD                       // "5944"
sb += EXTERNAL_ORDER_ID                // "3A"
sb += decInt2hexString(payload.size / 2)   // ← 注意是 size/2！
sb += payload
sb += getCheckResult(EXTERNAL_ORDER_ID + decInt2hexString(payload.size / 2) + payload)
sb += ORDER_TAIL                       // "4B4A"
result = sb.toUpperCase(Locale.ROOT)
```

> [!IMPORTANT]
> **长度字段是 `payload.size / 2`，不是 `payload.size`。**
> 因为 `payload` 在此路径是 **hex 字符串**（每个字节占 2 个字符），
> 除以 2 才得到字节数。这是 docs 完全没有提到的细节。
>
> 若移植时直接传 `ByteArray.size`，长度会**翻倍**。

---

## 3. `generate3BCommand` ✅ 完整逻辑

```text
generate3BCommand(commandType: Int, hexArg: String, param: Int): ByteArray
  if (commandType != 0x13B) → 返回空字符串路径（原样）

  s = ""
  // 1. 每 2 字符反向拼接
  i = hexArg.length - 2
  while (i >= 0) {
      s += hexArg.substring(i, i + 2)
      i -= 2
  }
  // 2. 追加固定 5 字节
  s += "FFFFFFFFFF"
  // 3. 追加填充
  s += getIncreaseByteHexStr()
  // 4. 组帧
  → ORDER_HEAD + ORDER_ID_3B + decInt2hexString(s.length / 2) + s + CHECK + ORDER_TAIL
```

反向拼接示例：

```text
hexArg = "AABBCCDD"
           ↑i=6  →  "DD"
           ↑i=4  →  "CC"
           ↑i=2  →  "BB"
           ↑i=0  →  "AA"
结果 = "DDCCBBAA"
```

若 `hexArg.length` 为奇数，`substring` 会抛异常，被 `catch(Exception)` 吞掉，
**`s` 保持为空字符串**（`try` 块在拼接之前开始，异常后 `s` 未被赋值）。

---

## 4. `increaseControlByte` 再确认

```kotlin
fun increaseControlByte(n: Int) {
    val k = if (n > 0x0A) 0x0A else n     // 钳制到 10
    ORDER_LENGTH = "1E"
    increaseByteNum = k
    ORDER_LENGTH = Integer.toHexString(0x1E + k)   // ← 这里没有补零/大写！
}
```

> [!WARNING]
> `increaseControlByte` 里用的是**裸 `Integer.toHexString`**，**不是** `decInt2hexString`。
> `0x1E + 10 = 40 = 0x28` → `"28"`（偶数长度，两者结果恰好相同）。
> 但若 `k` 使结果落在奇数长度区间就会不一致。当前 `k ∈ [0,10]`，`0x1E+k ∈ [30,40]`，
> 结果 `"1e"`..`"28"` 恒为 2 位，**实际无差异**。实现时用 `decInt2hexString` 亦可，
> 但要在注释里标明原实现是裸转换。

`getIncreaseByteHexStr`：

```kotlin
fun getIncreaseByteHexStr(): String {
    if (increaseByteNum < 0) increaseByteNum = 0
    return "FF".repeat(increaseByteNum)
}
```

---

## 5. 修正后的单测向量

| 用例 | 输入 | 期望 |
| --- | --- | --- |
| `decInt2hexString` 奇数 | `1` | `"01"` |
| `decInt2hexString` 奇数 | `15` | `"0F"` |
| `decInt2hexString` 偶数 | `16` | `"10"` |
| `decInt2hexString` 偶数 | `0x1E` | `"1E"` |
| `decInt2hexString` 三位 | `256` | `"0100"` |
| `getCheckResult` | sum=5 | `"05"` |
| `getCheckResult` | sum=0x2A | `"2A"`（不转大写） |
| 锁车帧 | payload=`"070101"`（hex 字符串，3 字节） | `5944` + `3E` + `decInt2hexString(6/2)`=`"03"` + `070101` + CHECK + `4B4A` |
| `generate3BCommand` 反向 | `"AABBCCDD"` | `"DDCCBBAA"` |
| `generate3BCommand` 奇数长度 | `"AABBCCD"` | 异常被吞，`s` 为空 |
| `getIncreaseByteHexStr` | `increaseByteNum=3` | `"FFFFFF"` |

注意「锁车帧」用例：payload 传的是 **hex 字符串** `"070101"`，长度字段是 `"6/2"` = `"03"`。

> [!CAUTION]
> 上一轮我给的期望 `5944 3E 3 070101 ... 4B4A`（长度 `"3"`）是**错的**，
> 正确应为 `5944 3E 03 070101 ... 4B4A`。
> 如果按错误向量写单测，会实现出错误的长度编码。

---

## 6. 对实现的影响

### 6.1 接口设计调整

```kotlin
object VehicleFrame {
    // payload 是 hex 字符串（原实现语义），长度取 length/2
    fun build(orderId: String, hexPayload: String): String

    // 便捷重载：传入 ByteArray 时自动转 hex 字符串
    fun build(orderId: String, payload: ByteArray): ByteArray

    // 奇数长度补 0 + 大写
    fun intToHex2(value: Int): String

    // 仅当长度 ==1 时补 0，不转大写
    fun checkResult(hex: String): String
}
```

建议保留**两个**入口并在注释里写明差异，避免后续误用。

### 6.2 仍需注意

| 项 | 说明 |
| --- | --- |
| payload 表示形式 | 原实现用 hex **字符串**拼接，不是 `ByteArray`；长度按 `length/2` |
| `generate3BCommand` 的异常吞掉 | 输入奇数长度时静默产出空载荷，无日志 |
| `increaseControlByte` 的裸转换 | 当前无差异，但语义上不等于 `decInt2hexString` |

---

## 7. 待确认（顺延）

| 项 | 影响 |
| --- | --- |
| `getCheckResult` 的输入是 hex 字符串还是原始字节 | 已确认：输入 hex 字符串，内部 `b72.m` 转 bytes 再求和 |
| `ORDER_LENGTH` 字段在何处被读取 | 已确认：构造器默认 `"1E"`，`increaseControlByte` 会覆盖，组帧时**不直接使用**（组帧用 `decInt2hexString` 重新算） |
