# 修复队列

每项最多尝试三次。涉及真实车辆、云端写入或重大架构调整的问题必须暂停，不得以未经验证的逻辑替代原始行为。

## IOS-PREF-001

- 唯一 ID：`IOS-PREF-001`
- 原始 Smali 位置：不适用。该项是 iOS KMP 平台适配缺失；原始 Android App 没有 `NSUserDefaults` 对应实现。
- KMP 实现位置：`shared/src/iosMain/kotlin/top/ghhccghk/yadea/settings/NSUserDefaultsPreferenceStore.kt`
- 问题描述：iOS 后端对缺失键直接调用 `boolForKey`、`integerForKey`、`doubleForKey`。`NSUserDefaults` 对不存在的标量键返回 `false` 或 `0`，导致 `themeAnimations=true`、`miuixMonet=true`、`vehicleForegroundService=true` 等默认值在首次启动时被错误读成关闭值。
- 对照证据：`PreferenceStore` 的调用方依赖 `PreferenceKey.defaultValue`；Android、JVM、JS、WasmJS 后端均在缺失键时返回默认值。
- 优先级：P3
- 安全等级：L2
- 修复方案：在读取标量前检查 `objectForKey(name)`；键不存在时返回 `key.defaultValue`。已存储的 `false`/`0` 因对象存在而继续按真实值读取。
- 验证方式：执行共享 JVM、JS、WasmJS 编译；检查 diff 仅包含 iOS 存储后端和审计记录。iOS 真机运行未验证。
- 当前状态：COMPLETED
- 尝试次数：1
- 完成说明：已于 2026-11-07 应用缺失键默认值修复。共享 JVM/JS/WasmJS 编译任务成功，但本次 iOS 文件不在这些目标中，因此 iOS 编译与真机行为尚未验证。

## PREF-OBSERVE-002

- 唯一 ID：`PREF-OBSERVE-002`
- 原始 Smali 位置：待核验，可能属于跨平台适配差异而非 Android 原逻辑差异。
- KMP 实现位置：`shared/src/{jvmMain,jsMain,wasmJsMain,iosMain}/.../settings/*PreferenceStore.kt`
- 问题描述：非 Android 后端的 `observe()` 当前只发射一次快照；Android 使用 `callbackFlow` 监听变更。是否需要响应同一进程外或存储层外部写入，尚无调用链证据。
- 优先级：P4（候选）
- 安全等级：L2
- 修复方案：先审计 `rememberAppAppearance()`、其他 `AppPreferences.observe()` 调用方和各平台写入路径；若所有用户可见写入都经过本地状态，则不应擅自扩大改动。若确认存在陈旧状态，再采用各平台原生变更通知或统一封装修复。
- 验证方式：静态调用链审计；仅在无外部请求风险时增加不触发真实服务的测试。
- 当前状态：ANALYZING
- 尝试次数：0

## 观察项（尚未登记为缺陷）

- iOS/JS/WasmJS HTTP 能力是否以显式 `UnavailableHttpTransport` 表达，以及公共 UI 是否对不可用能力正确隐藏或禁用；需单独对照后才能决定优先级。
- 设置清理范围是否与认证、车辆会话的独立清理入口一致；`AppPreferenceKeys.all` 只覆盖外观与投屏设置，需确认调用方语义后再审议。
