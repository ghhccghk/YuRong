# 修复历史

本文件只记录已实际完成并验证的修复。

## 2026-11-07 基线

- 完成只读仓库状态、模块、Source Set、构建配置和 Smali 目录检查。
- 未修改源码，未运行测试、真实登录、云端请求或车辆操作。
- 建立审计、功能矩阵、修复队列和修复报告。

## 2026-11-07 IOS-PREF-001

- 问题：iOS `NSUserDefaultsPreferenceStore` 对缺失标量键返回平台零值，未遵守 `PreferenceKey.defaultValue` 契约。
- 修改：读取前检查 `objectForKey`，只有键不存在时返回声明默认值；已存储的 `false`/`0` 行为不变。
- 实际修改文件：`shared/src/iosMain/kotlin/top/ghhccghk/yadea/settings/NSUserDefaultsPreferenceStore.kt`
- 验证：`:shared:compileKotlinJvm`、`:shared:compileKotlinJs`、`:shared:compileKotlinWasmJs` 成功；`:desktopApp:build` 成功。Android 构建在本轮复核中因本机 SDK build-tools 环境被阻塞，未取得可重复验证结果。
- 边界：Windows 主机无法执行 iOS 目标编译或模拟器测试；iOS 真机行为尚未验证。

## 2026-11-07 MIRROR-SOURCE-GATE-003

- 问题：无 `FrameSourceFactory` 的 iOS/Web 环境得到空的 `sourceCapabilities`，帧源按钮虽已禁用，但“一键投屏”和“开始投屏”仍可操作，最终只能失败。
- 对照：`AppViewModel` 在工厂缺失时返回空能力列表；`MirrorScreen` 的启动入口没有统一检查是否有任一受支持帧源。
- 修改：计算 `hasSupportedSource`，无可用源时显示警告，并禁用“一键投屏”和“开始投屏”。未修改投屏协议、TCP/UDP、帧发送、BLE 或车辆命令。
- 实际修改文件：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MirrorScreen.kt`
- 静态检查：`git diff --check` 通过；diff 为 `+16/-2`，仅包含入口门控与提示。
- 验证：JVM/JS/WasmJS 共享编译成功；Desktop 构建成功。
- Android 验证：Android Debug 构建未完成。当前 WSL/Linux 环境读取 `F:\sdk` 的 Windows build-tools（只有 `.exe`），Linux AGP 报缺少 `aapt`；属于本机构建环境阻塞，不是本次源码编译错误。
- 边界：未运行测试、真实登录、云端请求、车辆写入、OTA、上传或真机验证。

## 2026-11-07 NETWORK-PLATFORM-004

- 问题：iOS/JS/WasmJS 的 TCP/UDP 工厂直接抛 `UnsupportedOperationException`；JS/WasmJS 文案错误地引用 iOS；投屏启动协程在后续 try/catch 之前调用工厂。
- 对照：三个平台 actual 工厂无可用传输实现；`AppViewModel` 的调用位置位于 TCP 连接异常处理范围之外。
- 处理：按投屏协议边界标记 `BLOCKED`，未修改源码。错误文案也不单独修改，避免把协议能力缺失伪装成文案问题。
- 验证：仅完成静态调用链审计；未运行网络测试、真实投屏或真机验证。

## 2026-11-07 SETTINGS-FUTURE-SCHEMA-005

- 问题：未来 schema 保护未覆盖经 `writeTyped()` 写入的车辆、缓存、主题和画质设置，可能把更高版本配置降回当前版本。
- 修改：在 `writeTyped()` 单一写入入口统一检查未来 schema；删除 `write()`、`writeMirrorQuality()` 的重复检查；`writeAuth()` 保留安全 token 写入前检查。
- 实际修改文件：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/settings/AppPreferences.kt`、`shared/src/jvmTest/kotlin/top/ghhccghk/yadea/settings/AppPreferencesTest.kt`
- 回归测试：新增 `newerSchemaBlocksEveryPreferenceWriteDomain`。`writeVehicleCloudTheme` 是无返回值接口方法，测试改为调用后断言缓存键未变化；测试源码编译通过，测试本身未执行。
- 验证：共享 JVM/JS/WasmJS 编译成功；Desktop 构建成功；`:shared:compileTestKotlinJvm` 成功。
- 边界：未运行 `jvmTest`；未执行真实登录、云端请求、车辆写入、OTA、上传或真机验证。

## 2026-11-07 AUTH-CONTACT-CLEAR-006

- 问题：`AuthController.logout()` 与手机号/邮箱换绑后的本地清理忽略 `PreferenceWriteResult`，即使 `usr_token` 或用户资料清理失败仍显示成功提示。
- Smali 对照：`BindTypeActivity.loginOut()` 设置内存登出标记并发出事件；`MainActivityLogic.l1()` 分别删除 `usr_token`、`usr_info` 并清除标记，未发现“清理失败仍提示成功”的原行为。
- 修改：退出登录和换绑检查 `session.clear()`/`session.save()` 结果；失败时重新读取真实会话快照，清除成功提示并展示本地清理错误。已成功的远端换绑不回滚。
- 实际修改文件：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/auth/AuthSession.kt`、`shared/src/jvmTest/kotlin/top/ghhccghk/yadea/auth/AuthControllerTest.kt`
- 回归测试：新增换绑清理失败、换绑清理成功、登出清理失败 3 个测试；测试源码编译通过，测试本身未执行。
- 验证：共享 JVM/JS/WasmJS 编译成功；`:shared:compileTestKotlinJvm` 成功；`:desktopApp:build` 成功；`:androidApp:assembleDebug` 成功。
- 边界：未运行 `jvmTest`、真实登录、云端请求、车辆写入、OTA、上传或真机验证。

## 2026-11-07 CONTACT-VALIDATE-007

- 问题：紧急联系人保存入口只检查非空，缺少原弹窗的手机号格式和车主/紧急联系人重复号码校验。
- 对照：`UpdateContactInfoPopup.X()` 依次要求姓名非空、手机号非空、手机号首字符为 `1` 且长度为 11、号码不得等于另一联系人号码。
- 修改：在 `EmergencyContactController.save()` 的公共业务入口恢复相同顺序和文案；无效输入不调用网关。保存按钮不再以非空条件阻止点击，使空值也能显示原校验提示。
- 回归测试：新增 `EmergencyContactValidationTest.kt`，覆盖姓名、手机号格式、重复号码、有效输入及无效输入不触发网关；测试源码编译通过，测试本身未执行。
- 实际修改文件：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/EmergencyContactGateway.kt`、`shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`、`shared/src/jvmTest/kotlin/top/ghhccghk/yadea/vehicle/EmergencyContactValidationTest.kt`
- 验证：共享 JVM/JS/WasmJS 编译成功；`:shared:compileTestKotlinJvm` 成功；`:desktopApp:build` 成功；`:androidApp:assembleDebug` 成功。
- 边界：未运行 `jvmTest`、真实登录、云端请求、车辆写入、OTA、上传或真机验证。

## 2026-11-07 CONTACT-OWNER-FALLBACK-009

- 问题：云端 `userMobile` 为空时，KMP 紧急联系人面板没有像原页面一样预填当前登录用户手机号。
- Smali 对照：`EmergencyContactActivity.getOwnerInfo()` 与 `SosContactActivity.getOwnerInfo()` 在 `EmergencyContactsData.userMobile` 为 null 或空字符串时读取 `UserInfoBean.mobile`，非空云端值保持优先。
- 修改：`EmergencyContactController` 在初次加载和保存后刷新时统一应用车主手机号回退；Android 入口通过 `ownerMobileFallbackProvider` 提供认证会话手机号，公共 UI 不直接访问平台存储。
- 回归测试：新增空云端值使用登录手机号、非空云端值覆盖登录手机号 2 个测试；测试源码编译通过，测试本身未执行。
- 实际修改文件：`shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/EmergencyContactGateway.kt`、`androidApp/src/main/kotlin/top/ghhccghk/yadea/MainActivity.kt`、`shared/src/jvmTest/kotlin/top/ghhccghk/yadea/vehicle/EmergencyContactValidationTest.kt`
- 验证：共享 JVM/JS/WasmJS 与 `:shared:compileTestKotlinJvm` 编译成功；`:desktopApp:build` 与 `:androidApp:assembleDebug` 成功。
- 边界：未运行 `jvmTest`、真实登录、云端请求、车辆写入、OTA、上传或真机验证。
