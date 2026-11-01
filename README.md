# 御戎（touping）

基于 Kotlin Multiplatform 与 Compose Multiplatform 的雅迪车辆连接、控制、诊断与投屏应用。

项目将车辆协议、业务状态和主要 UI 放入 `shared`，并分别提供 Android、Desktop/JVM、iOS、JS 与 WasmJS 宿主。车辆相关协议依据原雅迪 SmartMoto APK 的反编译产物逐项核对；未经 smali 或抓包确认的命令不会直接发送到车辆。

> 本项目不是雅迪官方应用。车辆控制、设置写入与 OTA 操作可能存在风险，请在车辆静止、具备安全条件时使用，并先核对命令依据与参数范围。

## 主要功能

- 蓝牙设备扫描、绑定、连接、鉴权与自动重连。
- 车辆信息、实时状态、控制、诊断与失窃模式入口。
- 云端车辆设置、OTA、账号与多车辆管理。
- 自定义音效、仪表壁纸与联动主题背景。
- 车辆投屏，以及摄像头、本地视频等平台可用视频帧源。
- 双界面风格：
  - Miuix / HyperOS 风格；
  - 原有 Material 3 风格。
- 跟随系统、浅色与深色模式，以及主题色、车辆背景取色和界面缩放设置。

功能入口主要位于：

- “连接”：设备连接、投屏；
- “车辆”：信息、控制、诊断、车库、设置、仪表、OTA、账号；
- “设置”：界面风格、主题颜色、显示模式与连接偏好。

## 工程结构

```text
androidApp/     Android 应用入口、权限、前台服务与平台能力实现
desktopApp/     Desktop/JVM 应用入口与桌面平台能力
iosApp/         iOS / SwiftUI 宿主
webApp/         JS 与 WasmJS 浏览器宿主
shared/         共享业务逻辑、车辆协议、Compose UI 与各平台实现
docs/           UI 设计与车辆协议文档
```

共享代码遵循以下边界：

- `shared/src/commonMain` 保存跨平台业务、状态和 Compose UI。
- `androidMain`、`iosMain`、`jvmMain`、`jsMain`、`wasmJsMain` 保存平台实现。
- 平台不支持的能力必须通过明确的 capability / unsupported 实现表达，不使用会假装成功的空适配器。
- 页面只使用 `App*` 语义组件访问界面，不在业务页面混用 Material 3 与 Miuix 类型。
- 车辆命令、帧编码、OTA、投屏和音效传输等协议逻辑不得因 UI 调整被顺手修改。

## 技术栈与环境

当前 `gradle/libs.versions.toml` 中的主要版本：

| 组件 | 版本 |
| --- | --- |
| Kotlin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| Gradle Wrapper | 9.8.0 |
| Android Gradle Plugin | 9.4.1 |
| Android compileSdk / targetSdk | 37 |
| Android minSdk | 24 |
| Miuix UI | 0.9.4 |

构建前请准备：

- JDK 与项目所用 AGP/Gradle 版本兼容；
- Android SDK 37；
- Android Studio 或 IntelliJ IDEA（推荐）；
- 构建 iOS 需要 macOS、Xcode 与 iOS Framework 产物；
- 构建 Web 时由 Gradle 管理所需 Kotlin/JS 与 WasmJS 工具链。

首次构建需要联网下载 Gradle、Kotlin、Android 与 Compose 依赖。

## 运行与构建

### Android

生成 Debug APK：

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

产物位于 `androidApp/build/outputs/apk/debug/`。

### Desktop/JVM

标准运行：

```powershell
.\gradlew.bat :desktopApp:run
```

构建桌面发行包：

```powershell
.\gradlew.bat :desktopApp:build
```

当前 Desktop 支持共享 UI 与视频帧源相关能力，但不提供可用的车辆 BLE 适配器。

### Web

WasmJS（推荐的现代浏览器目标）：

```powershell
.\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
```

传统 JS 目标：

```powershell
.\gradlew.bat :webApp:jsBrowserDevelopmentRun
```

### iOS

在 macOS 上打开 `iosApp` 目录中的 Xcode 工程，选择目标设备或模拟器后运行。Windows 环境无法直接完成 iOS 构建。

## 常用验证

共享代码修改后，至少编译 JVM、JS 与 WasmJS 目标：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs
```

涉及 Android 或 Desktop 时追加：

```powershell
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```

涉及车辆或网络逻辑时，不要把会触发真实登录、车辆写入、OTA、音效上传或云端请求的测试当作普通单元测试执行。单元测试仅在明确需要且不会访问真实服务时运行：

```powershell
.\gradlew.bat :shared:jvmTest
```

## 平台能力边界

| 平台 | 当前状态 |
| --- | --- |
| Android | 主目标平台，包含 BLE、Wi-Fi、权限、前台服务、投屏与媒体来源实现 |
| Desktop/JVM | 可运行共享 UI，并提供部分视频帧源；BLE 明确为不支持 |
| iOS | 提供 SwiftUI 宿主与共享框架入口；部分硬件、仪表渲染和网络能力尚未完成 |
| JS / WasmJS | 提供浏览器入口与本地设置持久化；BLE、TCP/UDP 等能力明确为不支持或未完成 |

具体能力以平台实现和运行时 UI 提示为准，不根据目标平台名称推断功能已经可用。

## 设计与协议文档

- [UI 重设计分析](./docs/ui/README.md)
- [车辆功能与协议文档](./docs/vehicle/README.md)
- [AI 协作规范](./AGENTS.md)

车辆协议文档记录帧格式、校验算法、字段映射、连接流程和逆向证据状态。修改车辆相关代码前应先阅读对应文档，并保持命令映射、参数范围与证据一致。

## 贡献约定

- 修改共享 UI 时同时检查 Miuix、Material 3、浅色和深色模式。
- 新增平台能力时先扩展公共接口/capability，再实现对应平台后端。
- 不提交 `local.properties`、IDE 配置、构建产物、APK、截图、崩溃日志或调试日志。
- 使用明确的文件 pathspec 提交，不把无关文件混入功能提交。
- 提交前运行 `git diff --check`，并只报告真实执行过的构建或测试结果。
