# 执行复盘

本文件记录 UI 重设计计划的分阶段执行结果。每个阶段完成后提交一次，未使用 ADB；验证使用 Desktop build 和共享模块构建。

## P0：统一设计 token 与主题机制

提交：`84fc75c feat: unify KMP UI design tokens and theme state`

完成：

- 引入 `AppAppearance`、`AppThemeMode`，统一 System / Light / Dark 决策。
- Material 3 和 Miuix 共享同一个 `darkTheme`。
- 扩展 `AppColors`、`AppTypography`、`AppShapes`、`AppDimensions`。
- 统一 Card、Button、OutlinedButton、TextField、SwitchPreference 的 shape、contentPadding、cornerRadius 参数。
- 将 `MirrorPreviewPanel` 的背景、遮罩、选区、轮廓和字号接入 `AppDesign`。
- 将页面硬编码的主要 padding / spacing 替换为设计 token。
- 将导航状态提升到 `AppTheme` 外部，修复切换风格时跳回“连接”的状态丢失。

验证：

- `:shared:jvmTest`
- `:androidApp:assembleDebug`
- `:desktopApp:build`

复盘：

- 这一层解决了颜色、字体、间距、形状和主题切换的统一入口。
- 业务页面仍保留原有功能和页面顺序，没有在这一步批量改变信息架构。
- KMP 公共抽象只使用 Compose Multiplatform API；Android 专属实现继续留在平台源集。

## P1：ConnectionScreen 代表性页面

提交：`d67b358 feat: redesign connection screen with semantic sections`

完成：

- 新增 `AppPageHeader`、`AppSection`、`AppListRow`、`AppEmptyState`、`AppLoadingIndicator`。
- Connection 页面改为 Page Header -> Mode Selector -> WiFi/P2P/BLE Section -> Debug Section。
- 设备列表不再嵌套普通 Card，改用可点击 `AppListRow`。
- 空列表、扫描状态、连接状态和调试日志有独立状态表达。
- AP/P2P 模式改为语义化 `ConnectionMode`。

验证：

- `:shared:jvmTest`
- `:androidApp:assembleDebug`
- `:desktopApp:build`

复盘：

- 页面层级现在由 `AppSection` 控制，不再由每张 Card 手写 padding 和间距。
- 连接页仍保留原 WiFi、P2P、BLE、日志功能，没有修改连接协议和 ViewModel 领域逻辑。
- 首批代表性页面覆盖了表单、模式切换、列表、状态和诊断信息。

## P2：MirrorScreen 与投屏区域

提交：`0dc3872 feat: restructure mirror screen hierarchy`

完成：

- Mirror 页面改为仪表连接、投屏状态、投屏源、投屏区域、主操作、调试日志六层结构。
- 状态区增加连接中 loading 语义。
- 视频/导航源配置独立成条件 Section。
- `MirrorPreviewPanel` 保持跨平台 Canvas，但颜色、形状、字号和间距使用 `AppDesign` token。
- 主操作“开始/停止投屏”与诊断日志分层。

验证：

- `:shared:jvmTest`
- `:androidApp:assembleDebug`
- `:desktopApp:build`

复盘：

- 保留 MirrorEngine、FrameSender、FrameSource、协议端口等业务逻辑。
- 预览区是交互画布，不再伪装成普通设置卡。
- 当前没有做投屏运行截图验证，只做构建验证；真实设备投屏状态需后续单独验证。

## P3：Device、Permission、Settings

提交：`6e620f4 feat: unify device, permission, and settings sections`

完成：

- Device 页四个等权 Card 改为四个语义 Section。
- 参数、协议和命令使用 `AppListRow`，标题/摘要/当前值层级统一。
- PermissionPanel 改为权限状态 Section，每个权限一行，已授权/未授权状态清晰。
- Settings 页使用 `AppPageHeader`、`AppSection`、`AppSwitchPreference` 和 `AppListRow`。
- Miuix Section 标题使用 `SmallTitle`，Material 3 使用共享标题 token。

验证：

- `:shared:jvmTest`
- `:androidApp:assembleDebug`
- `:desktopApp:build`

复盘：

- 设置页不再手工拼接 `SmallTitle`、Card 和 padding。
- Device/Permission 的内容密度更接近 MeowUI 的 preference/list 模型。
- Material 3 和 Miuix 仍由 `AppStyleContent` 内部切换，不把两种控件类型暴露给业务页面。

## 当前状态

- 工作区干净。
- 当前分支：`master`。
- 最近提交：`6e620f4`。
- 未使用 ADB；验证命令为 Desktop build 和共享模块构建。
- 未完成真实截图矩阵：Miuix/Material 3 × 浅色/深色 × Android/Desktop/iOS/Web。
- 下一步应在不改业务逻辑的前提下做桌面 hot build/运行检查，再补充真实视觉验收。
