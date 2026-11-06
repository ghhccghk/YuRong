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
