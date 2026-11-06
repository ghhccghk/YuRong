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

## NETWORK-PLATFORM-004

- 唯一 ID：`NETWORK-PLATFORM-004`
- 原始 Smali 位置：不适用。该项是 KMP 非 Android 平台的 TCP/UDP 实现缺失；原始 Android App 只包含 Android 传输路径。
- KMP 实现位置：`shared/src/iosMain/.../network/Network.ios.kt`、`shared/src/jsMain/.../network/Network.js.kt`、`shared/src/wasmJsMain/.../network/Network.wasmJs.kt`；调用入口为 `shared/src/commonMain/.../ui/state/AppViewModel.kt` 投屏启动协程。
- 问题描述：三个平台的 `createTcpTransport()` / `createUdpTransport()` 直接抛 `UnsupportedOperationException`。JS/WasmJS 的错误文案错误地写成 "iOS TCP/UDP not implemented"。`AppViewModel` 在帧源准备完成后、TCP 连接 try/catch 之前调用工厂，因此该异常不在该段异常处理内，可能使投屏启动协程异常结束而非进入 `MirrorState.Failed`。
- 对照证据：三个平台文件均只有抛异常的单行 actual 实现；`AppViewModel` 约 784-785 行的工厂调用位于后续 try 范围之外。
- 优先级：P3
- 安全等级：L3
- 修复方案：需要先取得明确的投屏协议/平台传输授权和设计证据，再决定实现原生传输，或在公共能力层统一声明不可用并让 UI 在启动前门控。不得只改错误文案扩大协议边界，也不得使用假成功传输替代。
- 验证方式：若获授权，需按平台补充能力契约测试和静态调用链检查；JVM/JS/WasmJS 编译；真实设备网络能力需单独验证。当前仅完成静态审计。
- 当前状态：BLOCKED
- 尝试次数：1
- 阻塞原因：TCP/UDP 属于投屏协议边界，项目规范默认只读；平台能力缺失与异常边界修复需要架构决策和明确授权，不能由字段或错误文案推断后直接修改。

## SETTINGS-FUTURE-SCHEMA-005

- 唯一 ID：`SETTINGS-FUTURE-SCHEMA-005`
- 原始 Smali 位置：不适用。该项是 KMP 设置持久化的跨平台契约问题；要求来自项目规范“未知版本不能覆盖有效配置”。
- KMP 实现位置：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/settings/AppPreferences.kt`；回归测试位于 `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/settings/AppPreferencesTest.kt`。
- 问题描述：未来 schema 检查原先只覆盖 `write()`、`writeAuth()`、`writeMirrorQuality()`。车辆连接、车辆状态缓存、云主题缓存、小组件缓存等通过 `writeTyped()` 写入时仍可能把更高版本 schema 降回当前版本，违反“未知版本不能覆盖有效配置”。
- 对照证据：`writeTyped()` 是所有 typed 设置和缓存写入的单一入口；读取路径不会清除或覆盖未来配置。`writeAuth()` 的安全 token 不在 typed 写入漏斗内，需保留独立检查。
- 优先级：P3
- 安全等级：L2
- 修复方案：在 `writeTyped()` 内统一调用 `futureSchemaFailure()`，存储 schema 高于 `AppPreferenceKeys.SCHEMA_VERSION` 时返回 `Failure`；删除 `write()`、`writeMirrorQuality()` 的重复检查；`writeAuth()` 保留安全存储前置检查。新增测试验证车辆连接、小组件缓存、云主题、投屏画质和外观写入均失败且原数据不变。
- 验证方式：`git diff --check`；共享 JVM/JS/WasmJS 编译；`:desktopApp:build`；`:shared:compileTestKotlinJvm` 仅编译测试源码。未运行 `jvmTest` 或任何真实外部请求。
- 当前状态：COMPLETED
- 尝试次数：1
- 完成说明：已于 2026-11-07 完成统一写入保护。测试源码首次编译发现 `writeVehicleCloudTheme` 为无返回值的接口方法，测试中的 `is Failure` 断言不可能成立；改为调用该方法后用存储快照断言确认未覆盖，随后测试源码编译通过。测试本身未运行。

## AUTH-CONTACT-CLEAR-006

- 唯一 ID：`AUTH-CONTACT-CLEAR-006`
- 原始 Smali 位置：`com/yadea/smartmoto/login/view/BindTypeActivity.smali` 的 `loginOut()`（约 1177-1227）；`com/yadea/smartmoto/main/view/MainActivityLogic.smali` 的登出监听与 `l1()`（约 3626-3637、1334-1340、6169-6218）；`zm3.smali` 的 `d()`、`e()`、`C2(Z)`、`X0()`。
- KMP 实现位置：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/auth/AuthSession.kt`；回归测试位于 `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/auth/AuthControllerTest.kt`。
- 问题描述：`AuthController.logout()` 和手机号/邮箱换绑后的 `applyContactChange()` 忽略 `AuthSession.clear()` 或 `AuthSession.save()` 的失败结果，仍在 UI 显示“已退出登录”或“已更换，请重新登录”。本地清理失败时用户可能被误导为已安全退出。
- 对照证据：Smali `loginOut()` 先设置登出标记，再发事件并关闭页面；`MainActivityLogic` 收到事件后调用 `l1()`，分别删除 `usr_token`、`usr_info` 并清除内存登录标记。原实现没有“清理失败仍宣称成功”的分支；KMP `PreferenceWriteResult` 已显式表达失败，但调用方此前丢弃返回值。
- 优先级：P2
- 安全等级：L2
- 修复方案：检查 `session.clear()`/`session.save()` 返回值；成功才显示原成功提示；失败时重新读取真实存储快照，保留可恢复的会话状态并显示失败原因。不修改远端换绑请求、车辆、BLE、OTA 或投屏逻辑。
- 验证方式：`git diff --check`；共享 JVM/JS/WasmJS 编译；`:shared:compileTestKotlinJvm` 仅编译测试源码；Desktop 与 Android Debug 构建。测试本身不运行，避免未经允许的业务测试。
- 当前状态：COMPLETED
- 尝试次数：1
- 完成说明：已于 2026-11-07 修复换绑和退出登录的本地清理失败反馈。新增 3 个回归测试源码，覆盖清理失败、成功以及登出失败路径；测试源码编译通过，测试未执行。共享 JVM/JS/WasmJS、Desktop 和 Android Debug 构建均成功；真机行为尚未验证。

## 观察项（尚未登记为缺陷）

- iOS/JS/WasmJS HTTP 能力是否以显式 `UnavailableHttpTransport` 表达，以及公共 UI 是否对不可用能力正确隐藏或禁用；需单独对照后才能决定优先级。
- 设置清理范围是否与认证、车辆会话的独立清理入口一致；`AppPreferenceKeys.all` 只覆盖外观与投屏设置，需确认调用方语义后再审议。
- 非 Android TCP/UDP 的能力声明与异常边界已登记为 `NETWORK-PLATFORM-004`，在明确投屏协议授权前不再拆分处理错误文案。
