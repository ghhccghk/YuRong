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
- 当前状态：BLOCKED
- 尝试次数：1
- 阻塞原因：`observe()` 的唯一用户可见调用方是 `rememberAppAppearance()`，另一个调用方是 Android 小组件；外观写入先更新 Compose 状态再异步持久化。现有调用链无法证明会出现用户可见陈旧状态，也缺少同进程外存储写入的产品需求证据。未经证据扩大平台观察实现不符合最小修改原则。

## MIRROR-SOURCE-GATE-003

- 唯一 ID：`MIRROR-SOURCE-GATE-003`
- 原始 Smali 位置：不适用。该项是 KMP 平台能力门控差异；原始 Android App 只包含 Android 帧源路径。
- KMP 实现位置：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MirrorScreen.kt`；能力来源为 `AppViewModel.sourceCapabilities` 与各平台 `FrameSourceFactory`。
- 问题描述：iOS/Web 入口未传入 `FrameSourceFactory` 时 `sourceCapabilities` 为空。各帧源按钮已禁用，但“一键投屏”和“开始投屏”仍可点击，随后进入必然失败的流程。
- 对照证据：`AppViewModel.sourceCapabilities` 在工厂缺失时返回空列表；帧源按钮通过 `supports()` 禁用；原启动按钮没有统一门控。
- 优先级：P3
- 安全等级：L1
- 修复方案：从能力列表计算 `hasSupportedSource`；无可用源时显示明确警告，并同时禁用“一键投屏”和“开始投屏”。不修改 TCP/UDP、帧发送、BLE、车辆命令或投屏协议。
- 验证方式：`git diff --check`；共享 JVM/JS/WasmJS 编译；Desktop 构建；Android 构建按实际环境结果记录；真机未验证。
- 当前状态：COMPLETED
- 尝试次数：1
- 完成说明：已于 2026-11-07 完成最小 UI 门控修复。共享 JVM/JS/WasmJS 编译与 Desktop 构建成功；Android Debug 构建因本机 SDK build-tools 为 Windows 版而被阻塞，不能宣称 Android 构建通过。真机行为尚未验证。

## 观察项（尚未登记为缺陷）

- iOS/JS/WasmJS HTTP 能力是否以显式 `UnavailableHttpTransport` 表达，以及公共 UI 是否对不可用能力正确隐藏或禁用；需单独对照后才能决定优先级。
- 设置清理范围是否与认证、车辆会话的独立清理入口一致；`AppPreferenceKeys.all` 只覆盖外观与投屏设置，需确认调用方语义后再审议。
