# AI 协作规范

本文件适用于所有参与本仓库的 AI 编码工具。修改代码前先阅读本文件，并遵守其中的界面架构、提交和验证要求。

## 项目定位

这是 Kotlin Multiplatform Compose 项目，目标平台包括 Android、iOS、Desktop/JVM、JS 和 Wasm。

- 共享业务和 UI 代码位于 `shared/src/commonMain`。
- 平台实现位于 `androidMain`、`iosMain`、`jvmMain`、`jsMain`、`wasmJsMain`。
- 不得为了 Android 的便利破坏其他目标平台编译。
- UI 重构不得顺手修改蓝牙、Wi-Fi、投屏协议、帧发送等领域逻辑。

## 双主题架构

应用保留两套原生界面：

- `UiThemeStyle.Miuix`：HyperOS/Miuix 风格。
- `UiThemeStyle.Material3`：保留原有 Material Design 3 界面。

必须坚持以下规则：

1. **一份业务页面，两套原生实现。**
   业务层只描述状态、语义和回调；Material 3 与 Miuix 的视觉分支留在 UI 组件内部。
2. **不把 Miuix 做成 Material 换色。**
   Miuix 模式优先使用 `MiuixTheme`、`MiuixCard`、`MiuixButton`、`MiuixTextField`、`MiuixSlider`、`MiuixSwitchPreference`、`MiuixScaffold`、`MiuixTopAppBar` 和 `MiuixNavigationBar`。
3. **不把 Material 3 改造成仿 MIUI。**
   Material 3 模式保留原有 M3 控件、排版和交互。
4. **业务页面不得直接混用两套主题类型。**
   页面通过 `AppDesign`、`AppText`、`AppCard`、`AppButton`、`AppTextField`、`AppSlider`、`AppSwitchPreference`、`AppScaffold` 等语义 API 访问界面。
5. **风格切换必须即时生效。**
   入口固定在“设置”页，默认使用 Miuix，并允许切换回 Material 3。

## 代码入口

| 文件 | 职责 |
| --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/UiTheme.kt` | `UiThemeStyle`、双主题、`AppDesign` 颜色/排版/形状 token、`AppStyleContent` |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/UiComponents.kt` | 双风格语义控件 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/UiShell.kt` | Scaffold、TopAppBar、底部导航 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/SettingsScreen.kt` | 界面风格设置 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/MainScreen.kt` | 应用导航与页面入口 |

新增可复用 Composable 时：

- 公共参数优先使用业务语义，例如 `selected`、`checked`、`onClick`、`onValueChange`。
- 不向业务页面暴露 Material 或 Miuix 私有状态和颜色类型。
- 两种风格必须保持相同的回调时机、启用状态和提交行为。
- `modifier` 应用于最外层可布局节点。
- 图标必须提供 `contentDescription`；选择、开关和导航组件保持正确的可访问性角色。
- 避免魔法值，优先使用 `AppDesign` token。

## 组件替换约束

- 修改 UI 时先复用现有 `App*` 组件；缺少能力时扩展组件，不在页面内复制实现。
- 禁止对源码做宽泛字符串替换。曾因替换 `Button(` 产生 `AppOutlinedAppButton`、`SourceAppButton` 等错误名称。
- 使用明确 import 和 `as` 别名区分 Material 3 与 Miuix 同名组件。
- Material 3 和 Miuix 的 `LocalContentColor` 是不同类型；需要文本继承控件颜色时必须同时处理当前风格对应的 CompositionLocal。
- 页面标题在 Miuix 模式由原生 TopAppBar 承担；Material 3 模式保留页面内标题，避免重复显示。

## Git 提交

- 修改完一个独立功能后立即提交，不在一个提交中混放多个不相关功能。
- 推荐提交类型：`feat`、`fix`、`refactor`、`test`、`build`、`docs`。
- 提交前执行 `git diff --check`，确认没有意外空白或未完成代码。
- 不提交构建产物、IDE 临时文件或调试日志。
- 不修改用户已经提交的历史，除非用户明确要求。

## 验证要求

UI 或共享代码修改后至少运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:jvmTest
```

涉及 Android 或 Desktop 时运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```

界面结构调整还应检查：

- Miuix 与 Material 3 两种风格。
- 浅色和深色模式。
- 首次显示、设置切换和页面切换。
- 窄屏、宽屏、长文本和禁用状态。
- Android/Desktop 实际构建；能够在本机运行时再做截图或视觉检查。

只报告真实运行过的验证，不把计划中的检查写成已通过。

## 外部参考

- Miuix 文档：[compose-miuix-ui.github.io/miuix/zh_CN](https://compose-miuix-ui.github.io/miuix/zh_CN)
- Miuix 源码：[github.com/compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- MeowUI：[github.com/lingqiqi5211/MeowUI](https://github.com/lingqiqi5211/MeowUI)

MeowUI 只作为“一份业务 API、两套原生实现”的架构参考。它是 Android Library，不能直接作为本项目 `commonMain` 的跨平台依赖。
