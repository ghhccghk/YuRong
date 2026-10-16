# 账号与登录

账号体系是车辆验证连接信息的前置条件。本文依据 `temp_smali/k05.smali`（`RegisteredModel`）、
`temp_smali/tb0.smali`（`CommonParamsInterceptor`）、`temp_smali/sb0.smali`
（`CommonHeadInterceptor`）、`temp_smali/t46.smali`（`TokenErrorInterceptor`）、
`temp_smali/o13.smali`（`LoginStateUtils`）、`temp_smali/f84.smali`
（`ParamGenerationUtil`）的逐行核对。

## 1. 接口清单

`k05` 共 11 个请求方法，全部走 `Lo71.K(path)` → `ch4`（`PostRequest`）→ `q0(callback)`。

| # | 方法 | 路径 | 业务字段 |
| ---: | --- | --- | --- |
| 1 | `p(account, password, type)` | `user/loginByPassword` | `account`, `password`, `accountType`, `appType=1`, `oppoPushId`, `registrationId` |
| 2 | `r(mobile, code, …)` | `user/loginBySms` | `mobile`, `code`, `registrationId` |
| 3 | `k(mobile, type, action0)` | `user/sendCode` | `mobile`, `sendCode`, `appType=1`, `registrationId` |
| 4 | `j(email, type, action0)` | `user/sendCodeByEmail` | `email`, `sendCode`, `appType=1`, `registrationId` |
| 5 | `o(phone, type, isBand, action1)` | `user/sendCode/third-mobile` | `mobile`, `sendCode=8`, `thirdLoginType`, `verifyBind`, `appName="YADEA"`, `yadeaAppType="1"`（仅 type==0）, `registrationId` |
| 6 | `q(email, code, password)` | `user/registerByEmail` | `email`, `code`, `appType=1`, `password`, `registrationId` |
| 7 | `l(password)` | `user/setPasswordByUserId` | `password` |
| 8 | `n(account, code, password, type)` | `user/updatePasswordByCode` | `account`, `code`, `password`, `appType=1`, `accountType` |
| 9 | `m(account, code, type, action0)` | `user/bindOrChangeEmail` | `email`, `code`, `appType=1`, `type` |
| 10 | `s(newPhone, code, type, action0)` | `user/bindOrChangeMobile` | `mobile`, `code`, `appType=1`, `type` |
| 11 | `i(mobileNo, smsCode, bean)` | `user/third-auth/openid-mobile/bind` | 见下 |

第 11 个接口的字段是**条件性写入**（非空才放）：

```text
code          ← bean.code          （非空）
mobile        ← mobileNo
msgCode       ← smsCode
thirdLoginType← bean.type          （整型）
appNameId     = 1                  （整型）
sendCode      = 8                  （整型）
openid        ← bean.openID        （非空）
thirdNickname ← bean.nickName      （非空）
figureurlQq   ← bean.qqUrl         （非空）
registrationId
yadeaAppType  = "1"                （仅当 bean.type == 0，字符串）
```

> 整型字段（`thirdLoginType`、`appNameId`、`sendCode`）与字符串字段
> （`appType`、`yadeaAppType`、`appName`）不能混用类型。

`registrationId` 的来源：优先 `vy1.c()`（推送注册 ID），
为空时回退个推 `PushManager.getClientid()`。

## 2. `_gateway` 与请求体形态

`f84`（`ParamGenerationUtil`）：

```kotlin
// f84.a(map)：注入 _gateway
fun a(map: Map<String, Any?>?): Map<String, Any?> {
    val m = map ?: HashMap()
    m["_gateway"] = GatewayFlag.value
    return m
}
// f84.b(map) = Gson().toJson(a(map))
```

`_gateway` 取值（`p4$f.b` 类初始化）：

```text
if (env.endsWith("/api/app/") && !env.contains("8076")) "True" else "False"
```

`BaseBodyRequest.g0(json)` 分支：

| 条件 | 最终 API | Wire body |
| --- | --- | --- |
| `_gateway != "False"` | `@FormUrlEncoded @POST` | 先 form，后被拦截器改为**只剩** `gatewaySign` + `gatewayTimestamp` |
| `_gateway == "False"` | JSON POST | JSON 原文 |
| JSON 解析异常 | JSON POST | JSON 原文 |

## 3. 网关签名

`tb0`（`CommonParamsInterceptor`），构造参数 `mAppSecret` = `"cIDJ9f05d1604703"`。

```text
d(TreeMap dynamicMap):
1. 若 !containsKey("_gateway") || get("_gateway") == "False" → 原样返回

2. 遍历所有 key：
     value 非空且 contains("%") → URLDecoder.decode(value, "utf-8") 后写回

3. timestamp = new Date().getTime() + mistiming
   （mistiming 来自 SharedPreferences，缺失默认 -1）

4. json = GsonUtils.toJson(dynamicMap)

5. JSON 规范化（漏掉会导致服务端签名校验失败且报错模糊）：
   5.1 contains("\\\"")            → replaceAll("\\\\\"", "\"")
   5.2 contains("essay")           → 特殊分段重组（商城接口专用）
   5.3 !essay && contains("{\"")   → replaceAll("\"\\{", "\\{") 且 replaceAll("\\}\"", "\\}")
   5.4 contains("\"[")             → replaceAll("\"\\[", "\\[") 且 replaceAll("\\]\"", "\\]")

6. plaintext = "gatewayTimestamp=" + timestamp + "&json=" + json

7. dynamicMap.clear()                                   ← 清空全部业务字段

8. dynamicMap["gatewaySign"] = URLEncoder.encode(AesUtils.encrypt(key, plaintext), "utf-8")
9. dynamicMap["gatewayTimestamp"] = timestamp.toString()
```

`AesUtils.encrypt`：16 字节 UTF-8 key、`AES/ECB/PKCS5Padding`、Base64。

> **第 5 步的 JSON 规范化是签名一致性的关键。** 第 7 步的 `clear()` 意味着
> `_gateway=True` 时最终 wire body **只有** `gatewaySign` 与 `gatewayTimestamp`。

### 3.1 字段顺序敏感

原实现用 `TreeMap` 序列化，即**按 key 字典序**。
移植时若用 `kotlinx.serialization` 序列化 `Map`，**必须保证同样的字典序**，
否则 `gatewaySign` 与服务端不一致，且报错模糊。

## 4. 公共请求头

`sb0`（`CommonHeadInterceptor`）：

| Header | 值 |
| --- | --- |
| `Authorization` | `"Bearer " + token`（若已以 `Bearer` 开头则不重复加；token 为空则不加） |
| `req-id` | `UUID.randomUUID().toString()` |
| `request-agent` | `"2"` |
| `deviceId` | `loginId`；为空则生成 UUID 并写回 |
| `os` | `"android"` |
| `app-version` | 应用版本名 |
| `bike-series-code` | 车辆系列码；为空则用默认车型 key |
| `app-name` | `"YADEA"` 或 `"FACTORY"` |
| `app-version-num` | 应用版本号 |
| `app-market` | 渠道 |
| `app-version-type` | `"2"` 或 `"1"` |
| `device-brand` | 设备品牌 |
| `phone-model` | 机型 |
| `language` | `"zh_CN"` |
| `push-operator` | `"2"` |

特例：URL 含 `maps.googleapis.com` 时**直接放行，不加任何头**。

## 5. Token 失效判定

`t46`（`TokenErrorInterceptor`）：

```text
1. bodyString 非空
2. decoded = URLDecoder.decode(bodyString, "utf-8")
3. plain   = AesUtils.decrypt("cIDJ9f05d1604703", decoded)
4. obj     = JSONObject(plain)
5. code    = obj.optString("code")        // 字符串，不是整型

6. "101000" | "100995" → post "_token_invalid"
   "405"               → post "_token_time_invalid"（携带 timestamp）
   其他                 → 不处理

7. 恒返回 false（只做副作用，不改写响应）
```

**不是 HTTP 401 驱动，而是响应体业务码驱动。**

## 6. 成功/失败判定

```text
ApiResult<T> = { success: Boolean, code: Int, msg: String, data: T }
判定 = apiResult.isOk()，即读取 success 字段

success == true  → 向下传递 data
success == false → throw ServerException(code, msg) → 转 ApiException
```

失败时各回调读取的是 `ApiException.getMessage()`（不是 `displayMessage`）。

## 7. 登录态落盘

`o13`（`LoginStateUtils`）：

```kotlin
fun a(bean: UserInfoBean?): Boolean {
    if (bean == null) return false
    b(bean, isLoginSuccess = true)
    return true
}

fun b(bean: UserInfoBean, isLoginSuccess: Boolean) {
    zm3.h2(bean.token)     // key "usr_token"
    zm3.i2(bean)           // key "usr_info"，内部先重算 authKey
    if (!isLoginSuccess) return
    LiveDataBus["_community_login"].setValue(bean)
    if (!bean.mobile.isNullOrEmpty()) AnalysysAgent.alias(ctx, bean.mobile)
    ToastUtils.showShort(R.string.login_login_succeed)
}
```

`zm3.i2(bean)` 内部：

```text
bean.authKey = md5LowerHex(bean.mobile)     // 逐 char 低 8 位变体，非 UTF-8
putObject("usr_info", bean)                  // Gson 序列化
```

> `putObject` 用 Gson，因此 `usr_token` 存的是**带双引号的 JSON 字符串字面量**，
> 不是裸 token。

## 8. 数据模型

`UserInfoBean` 字段名**即 JSON 名**（无 `@SerializedName`）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `long` | 主键 |
| `token` | `String?` | bearer token |
| `tokenExpire` | `long` | 与毫秒时间戳比较 |
| `userId` | `String?` | 后端用户 ID |
| `mobile` | `String?` | 手机号；`authKey` 由它派生 |
| `email` | `String?` | 邮箱 |
| `nickName` | `String` | 昵称 |
| `authKey` | `String?` | 保存时强制重算 |
| `loginAppType` | `int` | 登录渠道 |
| `userType` | `int` | 用户类型 |
| `picUrl` `sex` `area` `realName` `createTime` `updateTime` `remark` `provinceCity` `location` `backImage` `officialCertification` `birthDate` `userName` | — | 其余资料字段 |

`OtherLoginBean`：`type` **0 = 微信，1 = QQ**。

## 9. 输入校验

正则**不可放宽**：

| 项 | 正则 |
| --- | --- |
| 中国大陆手机号 | `^1(3\|4\|5\|6\|7\|8\|9)\d{9}$` |
| 海外手机号 | `^[0-9]{7,11}$` |
| 邮箱 | `[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,4}` |
| 密码 | `^[\"',./?!~_\-<>&*^%$@a-zA-Z0-9]{6,18}$` |
| 验证码 | `^[0-9]{4}$`（恰好 4 位） |

验证码按钮 60 秒倒计时；协议未勾选时主按钮点击后提示并抖动，
但**主按钮本身不禁用**（点击时才串行校验并短路）。

## 10. 跨平台实现要点

### 10.1 密码学原语建议放 `commonMain`

`AES-ECB`、`PKCS5Padding`、`MD5`、`Base64` 都不涉及平台 API。
若用 `expect/actual`，JS 侧的 `WebCrypto` 是**异步**的，与同步的签名流程冲突。

**建议在 `commonMain` 用纯 Kotlin 实现 AES-ECB 与 MD5**，完全避开平台差异，
也便于单测。`Base64` 可用标准库 `kotlin.io.encoding.Base64`。

### 10.2 存储

**不要照搬 MMKV。** 按项目规范走 `AppPreferenceKeys`：

| key | 类型 | 默认 |
| --- | --- | --- |
| `usr_token` | `String` | `""` |
| `usr_info` | `String` | `""` |
| `login_id` | `String` | `""` |

需要递增 `SCHEMA_VERSION` 并补迁移与回退测试。
`read` / `observe` / `write` / `decode` 四处必须同步（`observe` 用索引，最易漏）。

### 10.3 依赖

| 依赖 | 用途 |
| --- | --- |
| Ktor client（各平台 engine） | HTTP |
| `kotlinx.serialization-json` | JSON（**需保证字典序**） |
| `kotlin.io.encoding.Base64` | Base64（标准库） |

## 11. 实施顺序

1. 正则校验（纯逻辑，可立即单测）
2. 网关签名（含纯 Kotlin AES/MD5，可用固定向量单测）
3. 数据模型
4. 11 个接口 + 拦截器链
5. token 生命周期
6. UI（只依赖 `App*` 语义组件）

第 1、2 步不依赖网络也不需要真机，建议先做并写对拍单测。
特别是第 2 步，建议用「固定 JSON + 固定时间戳 → 期望 gatewaySign」构造向量，
因为签名错了服务端只会给模糊报错。

## 12. 待确认

| 项 | 影响 |
| --- | --- |
| `AesUtils.encrypt` 的 Base64 是否有换行 | 影响签名串 |
| `GsonUtils.toJson` 的转义策略 | 影响签名串 |
| 环境 URL 的来源与取值 | 决定 `_gateway` 为 True 还是 False |
| `mistiming` 的更新时机与初始值 | 影响时间戳 |
| 微信 / QQ 第三方登录 SDK 接入 | 需第三方 AppId |
| `@Login` 注解的"登录后重放原调用" | smali 中未找到实现 |
