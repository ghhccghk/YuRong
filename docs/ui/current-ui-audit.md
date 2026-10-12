# 当前项目 UI 问题清单

## 1. 审查方式和真实运行证据

审查覆盖源码和 Android 真机运行状态。使用当前构建产物：

`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

执行了：

```text
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n top.ghhccghk.yadea/top.ghhccghk.yadea.MainActivity
```

设备随后断开，未继续进行更多交互。已保存的截图：

- [Miuix 连接页](./audit-screenshots/miuix-connection.png)
- [Miuix 投屏页](./audit-screenshots/miuix-mirror.png)
- [Miuix 设置页](./audit-screenshots/miuix-settings.png)
- [Material 3 设置页](./audit-screenshots/material3-settings.png)
- [切换到 Material 3 后的状态](./audit-screenshots/material3-after-switch.png)

这些截图是真实 Android 界面观察，不是静态 mockup。未完成 iOS、Desktop、Web 的截图验证。

## 2. 页面结构问题

### 2.1 页面只是纵向卡片流，缺少统一的页面容器模型

依据：

- `ConnectionScreen.kt:38-40` 直接创建 `LazyColumn(fillMaxSize().padding(16.dp), spacedBy(12.dp))`。
- `MirrorScreen.kt:43-45` 使用完全相同的布局参数。
- `DeviceScreen.kt:16-18` 使用完全相同的布局参数。
- `PermissionPanel.kt:22-23` 直接把内容放进 `AppCard` 后手写 `Column.padding(16.dp)`。
- `SettingsScreen.kt:28-31` 又单独定义了 `pagePadding` 和 `spacedBy`。

运行截图中，连接页和投屏页的主要内容都是一组外观相同、边界相同的深色/浅色卡片。页面标题、分组标题、卡片标题和条目标题都靠不同 `AppText` 角色散落地表达，没有一个统一的“页面 -> 分组 -> 条目”结构。

MeowUI 的对应机制是 `MeowPreferenceScreen` + `MeowPreferenceSection`：

- `MeowPreferenceScreen` 统一 content padding、滚动和 section spacing。
- `MeowPreferenceSection` 统一收集条目、分组标题和分段容器。
- 页面不需要自行决定每张卡片如何留白。

### 2.2 分组没有使用最终条目数量和稳定 key

`ConnectionScreen.kt:119-127`、`ConnectionScreen.kt:159-169` 将设备行放进外层卡片，又为每个设备创建嵌套 `AppCard`。外层卡片和嵌套卡片形成双层容器，设备列表没有明确的首/中/末项形状或稳定 key。

`DeviceScreen.kt:25-70` 将四个信息块做成四个同尺寸 `AppCard`，每块都是 `Column.padding(16.dp) + spacedBy(8.dp)`。这在截图中表现为一串等权卡片，信息层级只由标题文字承担。

MeowUI 的 `MeowPreferenceSectionScope.kt:35-130` 会先收集完整条目，再按最终 count 计算分段圆角；条件项使用稳定 key。这比当前页面中的 `forEach` 和嵌套 Card 更能保持布局、动画和状态的一致性。

### 2.3 页面标题体系重复且风格不一致

`UiShell.kt:50-60` 的 `AppTopBar` 在 Material 分支故意为空，Miuix 分支渲染 `MiuixTopAppBar`。

同时：

- `ConnectionScreen.kt:42-44` 使用 `ScreenTitle("设备连接")`。
- `MirrorScreen.kt:47-49` 使用 `ScreenTitle("投屏控制")`。
- `DeviceScreen.kt:20-22` 使用 `ScreenTitle("设备信息")`。
- `SettingsScreen.kt:36-43` 在 Material 分支手工组合 `ScreenTitle("设置")` 和“外观”。

运行截图证实：

- Miuix 顶部显示由 `AppTopBar` 提供的“连接/投屏/设置”。
- Material 3 页面内又显示“设备连接/投屏控制/设备信息”。

这两种信息架构没有共享同一个 title/subtitle/action 模型。MeowUI 的 `MeowTopBar.kt:228-340` 让两种风格都通过同一个 `MeowTopBar` 接收 title、subtitle、navigationIcon、actionItems，并把滚动行为接进 Scaffold。

## 3. 视觉密度和层级问题

### 3.1 留白、间距和内边距散落在页面中

当前值：

- 页面外层：16.dp（Connection/Mirror/Device）。
- 页面卡片间距：12.dp。
- 卡片内部：16.dp。
- 调试日志卡片内部：12.dp。
- 条目间距：8.dp、2.dp、4.dp 不等。
- Miuix 设置页单独使用 0/12.dp 页面 padding，Material 设置页使用 16.dp。

这些值没有进入 `AppDesign`。`UiTheme.kt:85-93` 只定义 `AppShapes`，`UiTheme.kt:92-95` 的 `AppThemeTokens` 也没有 dimensions。

MeowUI 将页面水平/垂直 padding、section spacing、item padding 统一放入 `MeowDimensions`（`MeowTheme.kt:147-160`），并由 `MeowPreferenceScreen` 按风格选择不同 spacing（`MeowPreferences.kt:128-166`）。

### 3.2 信息密度没有明确主次

连接页的真实截图中：

- WiFi 卡片包含标题、状态、两个输入框、三个按钮、附近网络。
- BLE 卡片包含标题、状态、扫描按钮。
- 调试日志卡片包含标题和日志。

三块内容视觉权重接近，主要任务“连接 WiFi/BLE”与诊断日志没有明显主次。`ConnectionScreen.kt:57-99`、`144-183` 中的标题、状态、按钮和日志都使用同一套卡片外壳。

MeowUI 通过 `MeowTip`、`MeowCard`、`MeowPreferenceSection`、`MeowTheme.typography` 把主操作、状态、说明、诊断区分开。`MeowCard.kt:50-160` 还允许状态卡使用语义容器色和内容色。

### 3.3 普通 Card 被过度使用

当前项目使用 `AppCard` 的地方包括：

- Connection 的 WiFi、P2P、BLE、设备行、日志。
- Mirror 的连接、状态、视频源、日志。
- Device 的四个信息块。
- Permission 的整个权限列表。
- Settings 的两个设置块。
- `MirrorPreviewPanel` 虽然不是 AppCard，但也是同样的圆角容器+标题+内容模板。

这使不同功能看起来像同一种“信息卡集合”。MeowUI 的差异在于：

- 设置项是 segmented list/preference row。
- 内容卡只用于状态或独立内容。
- 提示、加载、刷新、底部弹层各有专门组件。

## 4. 主题系统问题

### 4.1 Material 分支没有明确深色策略

`UiTheme.kt:128-151`：

- Miuix 使用 `ThemeController(ColorSchemeMode.System)`。
- Material 分支直接使用 `MaterialTheme { ... }`，没有显式选择 light/dark colorScheme。

真机截图显示，在同一设备状态下切换风格后：

- Miuix 截图为深色。
- Material 3 设置页为浅色。

这说明两种风格并非在同一个 `darkTheme` 决策下工作。MeowUI 的 `MeowTheme` 在入口统一计算 `darkTheme`，并将同一个值传给 MaterialExpressive 和 Miuix 两个分支（`MeowTheme.kt:297-345`、`346-567`）。

### 4.2 token 只覆盖颜色、文字和少量形状

`UiTheme.kt:48-93` 的 token 缺少：

- 页面/分组/条目 spacing。
- item content padding。
- segmented list shape。
- 状态 success/warning。
- surface layering。
- dialog、tip、navigation 等专用形状。

`UiComponents.kt:47-100` 的 `AppCard` 和 `352-386` 的 `AppSwitchPreference` 直接在组件中写 16.dp padding；`MirrorPreviewPanel.kt:43-44`、`106-122` 又自己写 16.dp、12.dp、13.sp、11.sp 和 `Color.Cyan`。

结果是主题参数一部分在 `UiTheme`，一部分在页面，一部分在自绘 Canvas，无法形成统一设计语言。

### 4.3 颜色映射不是完整语义模型

MeowUI 的 `MeowColorScheme`（`MeowTheme.kt:46-115`）包含完整容器色、surface 层级、状态色和 page 色，并明确注释了 Material/Miuix 各自取什么 role。

当前 `AppColors`（`UiTheme.kt:48-70`）只有一小组角色，且 `onSurfaceContainer` 是自定义命名。`MirrorPreviewPanel.kt:58`、`87` 直接使用 `Color.DarkGray`、`Color.Cyan`，没有 `AppDesign` 语义 token。

## 5. 交互和状态反馈问题

### 5.1 状态反馈主要是文字

真机截图显示状态使用：

- `StatusChip` 的“●/○ + 未连接”。
- `AppText` 的“待命”。
- 调试日志文本。

没有统一的：

- loading 状态。
- empty 状态。
- error/success/warning tip。
- disabled reason。
- 操作成功/失败反馈。

MeowUI 通过 `MeowTip`、`MeowLoadingIndicator`、`MeowPullToRefresh`（`MeowFeedback.kt:57-250`）提供状态层。

### 5.2 交互反馈语义没有统一

`AppSwitchPreference`（`UiComponents.kt:352-386`）在 Material 分支直接触发 `Switch.onCheckedChange`；Miuix 分支调用 `MiuixSwitchPreference`。当前没有共享的 toggle haptic、错误回滚或写入状态。

MeowUI 在 `MeowHaptics.kt:24-95` 明确规定 ToggleOn/ToggleOff、ContextClick、Confirm、GestureThresholdActivate、TextHandleMove，并让两种风格在同一交互上保持相同反馈语义。

`MirrorPreviewPanel.kt:144-166` 的 Slider 也没有端点/分档/默认值反馈；MeowUI 的 `MeowSliderHaptic`（`MeowHaptics.kt:57-115`）会处理端点、分档和 key point。

### 5.3 主题切换会重置页面状态

真机切换“Miuix 界面”后，截图显示页面回到“连接”，而不是停留在设置页。代码依据：

- `MainScreen.kt:82` 在 `MainScreenContent` 内部使用 `remember { mutableStateOf(MainDestination.Connection) }`。
- `MainScreen.kt:67` 通过 `AppTheme(uiThemeState.value)` 包裹内容。
- `AppTheme` 切换时会切换整棵风格分支。

这是实际观察到的状态问题，不是推断。切换主题不应重置当前 tab、输入内容、扫描状态或投屏配置。

## 6. 与 MeowUI 的差异对照

| 设计维度 | MeowUI 源码机制 | 当前项目现状 | 影响 |
| --- | --- | --- | --- |
| 主题入口 | `MeowAppearance` + `MeowTheme` 统一 dark/dynamic/blur/scale | `AppTheme` 只切 style；Material dark 未显式统一 | 两种风格深浅色不一致 |
| 设计 token | colors/typography/shapes/dimensions/status | colors/typography/shapes，无 dimensions/status | 页面自行散落间距和状态色 |
| 页面容器 | `MeowScaffold` + scroll context + backdrop | `AppScaffold` 只包一层 Scaffold | 顶栏滚动、Blur、浮层、inset 无统一关系 |
| 分组 | 收集式 `MeowPreferenceSection` + stable key | 页面手写 `AppCard`/`forEach` | 无最终 count、分段形状、动态项一致性 |
| 条目 | Preference row/segmented list/native preference | 自定义 Row + Text + Switch/Button | 标题/摘要/值/控件层级不稳定 |
| 状态 | Tip/Loading/PullToRefresh/Haptics | 文本状态和按钮反馈 | 缺少操作和数据状态反馈 |
| 导航 | standard/floating，共享 item 语义和滚动上下文 | `AppBottomNavigation` 只有 standard 两套分支 | 无统一导航状态和扩展能力 |
| 自绘区域 | 统一 token/semantic component | Mirror Canvas 写死 Cyan/DarkGray/字号 | 视觉语言不随主题和风格变化 |
| 设置持久化 | `PreferenceKey` + `PreferenceStore` | `rememberSaveable` 管理 style | 只能保存界面状态，未形成设置契约 |

## 7. 问题优先级

### P0

1. 修复主题切换重置 `selectedDestination` 和页面状态。
2. 统一 Material/Miuix 的 darkTheme 决策。
3. 建立 dimensions/status/surface token，停止页面散写间距和语义色。

### P1

4. 引入收集式 section/row 模型，替代普通 Card + 手写列表。
5. 重做 Scaffold/TopBar 的滚动、inset、title/action 关系。
6. 为连接、投屏引入 loading/empty/error/operation feedback。

### P2

7. 统一 Slider/按钮/开关的触觉和状态反馈。
8. 重构 MirrorPreviewPanel 的 Canvas token。
9. 增加 iOS/Desktop/Web 的视觉验证矩阵。
