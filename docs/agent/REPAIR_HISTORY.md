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
- 验证：`:shared:compileKotlinJvm`、`:shared:compileKotlinJs`、`:shared:compileKotlinWasmJs` 成功；`:androidApp:assembleDebug`、`:desktopApp:build` 成功。
- 边界：Windows 主机无法执行 iOS 目标编译或模拟器测试；iOS 真机行为尚未验证。
