# 双 UI 重设计规范

本规范基于 MeowUI 源码机制和当前项目的真机审查结果。目标不是复制 MeowUI 页面，而是把它的“语义统一、原生分支、状态完整、视觉连续”迁移到本项目。

## 1. 设计目标

1. 保留当前业务信息架构：连接、投屏、设备、设置。
2. 一份业务页面只维护一套状态和回调。
3. Material 3 和 Miuix 各自使用自己的原生组件、形状、动效和交互。
4. 页面层级、分组、密度、状态和反馈由共享设计系统控制。
5. 主题切换不丢失页面状态。
6. 所有 commonMain UI 在 Android、iOS、Desktop、JS、Wasm 可编译。

## 2. 核心设计语言

### 2.1 页面骨架

所有页面使用统一的 `AppPage`/`AppScaffold`：

- 一个滚动容器。
- 一个页面标题区域。
- 一个分组内容区域。
- 一个稳定的底部导航区域。
- Scaffold padding 只由 Scaffold 提供，页面不再自行猜测 inset。
- TopBar、内容、BottomBar 共享滚动上下文。

页面标题模型：

- `title`：当前页面。
- `subtitle`：可选状态/范围说明。
- `actions`：图标、文本、菜单动作。
- Miuix 使用原生 TopAppBar。
- Material 3 使用原生 TopAppBar 或页面标题，但只出现一次。

### 2.2 信息层级

每行/每组只使用以下语义：

1. Page title：页面是什么。
2. Section title：这一组控制什么。
3. Item title：具体设置/对象。
4. Summary：影响、限制、状态说明。
5. Value：当前值或连接结果。
6. Control：Switch、Slider、TextField、Button、Menu。

禁止把标题、摘要、当前值、按钮全部做成同一视觉权重。

### 2.3 分组和列表

新增 `AppSection` / `AppSectionScope`：

- section 先收集条目，再按最终条目数决定首/中/末形状。
- 每个条目有稳定 key。
- 条目动态显隐使用 `visible`，不依赖索引。
- 条目点击只保留一个语义入口。
- Material 分支使用 segmented item/list surface。
- Miuix 分支使用一张原生 Card 或原生 Preference 容器。
- 自定义内容必须能声明是否需要 section 容器。

### 2.4 色彩和形状

扩展 `AppDesign` token：

- colors：基础色、容器色、surface 层级、divider、error、success、warning。
- typography：pageTitle、sectionTitle、itemTitle、summary、value、button、debug。
- shapes：page、section、item、field、dialog、tip、navigation。
- dimensions：page horizontal/vertical、section spacing、item horizontal/vertical、control gap。
- motion：主题切换、section 变化、导航切换的标准时长/缓动。

业务页面只能使用 `AppDesign`，不能直接写 `Color.Cyan`、`Color.DarkGray` 或重复写 16.dp。

### 2.5 主题状态

统一受控状态建议为 `AppAppearance`：

- `uiStyle`：Miuix / Material 3。
- `themeMode`：System / Light / Dark。
- `dynamicColor`：是否启用动态取色。
- `seedColor` / `paletteStyle`：可选扩展。
- `blurEnabled`：可选增强。
- `floatingNavigation`：可选导航形态。

当前阶段至少实现 `uiStyle + themeMode`。两种风格必须共享同一个 `darkTheme` 布尔值。

Material 分支使用明确的 light/dark colorScheme；Miuix 分支使用同一 darkTheme 对应的 ThemeController 模式。动态取色、AMOLED 和 Blur 暂不强求，但 API 应为后续扩展保留语义位置。

## 3. 通用组件调整

### 3.1 Theme

`UiTheme.kt` 调整方向：

- 从单一 `UiThemeStyle` 扩展为 `AppAppearance` 或等价受控状态。
- `AppDesign` 增加 dimensions、motion、status colors。
- `AppStyleContent` 保持组件内部风格分发。
- `rememberUiThemeStyle` 改为 `rememberAppAppearance`，并保证切换 style 不重建页面状态。

### 3.2 Shell

`UiShell.kt` 调整方向：

- `AppScaffold` 接收 scroll behavior、inset、bottom bar effect。
- `AppTopBar` 接收 title、subtitle、navigation、actions。
- `AppBottomNavigation` 接收 `AppNavigationItem` 语义，并保留 selected/onClick。
- 未来支持 standard/floating，但第一阶段只实现现有 standard，保持功能稳定。

### 3.3 Preference/Section

新增：

- `AppPreferenceScreen`
- `AppPreferenceSection`
- `AppPreferenceRow`
- `AppSwitchPreference`
- `AppValuePreference`
- `AppActionPreference`
- `AppSliderPreference`
- `AppTextFieldPreference`

每个 preference 组件同时支持受控状态和稳定的 key 语义。业务页面不再自己写 `Card + Column + Row`。

### 3.4 Feedback

新增语义组件：

- `AppTip`：Info / Success / Warning / Error。
- `AppLoadingIndicator`。
- `AppEmptyState`。
- `AppStatusBadge`。
- `AppButton` / `AppIconButton` 的 disabled reason。

第一阶段只接入连接、投屏已有状态；不引入不必要的 Snackbar/Dialog/Blur 依赖。

### 3.5 自绘控件

`MirrorPreviewPanel` 调整：

- 预览背景、选区、手柄、标签全部从 `AppDesign` 取色。
- Slider 使用 `AppSliderPreference`/`AppSlider` 语义。
- 裁剪控件拆分为 `CropPreview` + `CropControls`。
- 不把预览逻辑和通用 Slider 混在一个页面模板中。

## 4. 可访问性和交互约束

- 图标按钮提供 `contentDescription`。
- 选择、开关、导航项提供正确 role 和 selected/toggleable state。
- 一个整行点击只绑定一次点击语义。
- 禁用控件同时提供视觉 disabled 状态和原因。
- 扫描、连接、投屏操作必须有 idle/running/success/failure 状态。
- 首次 composition 直接显示正确状态，动画只表达后续状态变化。
- 同一交互只触发一次反馈，不在外层和内层重复点击/触觉。

## 5. 跨平台边界

`commonMain`：

- AppAppearance、AppDesign、App* 组件、页面语义、状态模型。

平台源集：

- Android 系统栏、动态取色、相机/录屏权限。
- iOS 系统 inset、文件选择、平台反馈。
- Desktop 窗口尺寸、鼠标 hover、键盘焦点。
- JS/Wasm 浏览器 inset、字体和浏览器限制。

不把 Android `Activity`、`WindowCompat`、`SharedPreferences` 等 API 放入 commonMain。
