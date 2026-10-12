# MeowUI 设计语言分析

## 1. 研究范围和证据

本分析没有只依据 README。源码快照来自：

`C:\Users\DozenOregano696\AppData\Local\Temp\MeowUI-source`

对应仓库提交：`60bb989`（`lingqiqi5211/MeowUI`）。源码中的路径均按 MeowUI 仓库内相对路径记录。

MeowUI 是 Android Library，包含 `meowui`、`meowui-xposed` 和 `sample` 三层。对本项目真正可借鉴的是 `meowui` 的主题/组件架构，不是它的 Xposed 连接代码。

## 2. 主题入口和状态管理

### 2.1 统一主题入口

入口在 `meowui/src/main/kotlin/io/github/lingqiqi5211/meowui/theme/MeowTheme.kt`。

关键结构：

- `MeowTheme.invoke`（约 297 行起）接收 `style`、`darkTheme`、动态取色、种子色和页面尺寸。
- `MeowTheme.invoke(appearance)`（约 345 行起）接收统一的 `MeowAppearance`，同时管理主题模式、动态色、Monet、AMOLED、Blur、底栏和界面缩放。
- `MaterialExpressiveContent`（约 346 行起）和 `MiuixContent`（约 439 行起）分别构造两套原生主题。
- `MeowStyleContent`（约 603 行起）是组件级风格分发器。

`MeowAppearance` 在 `theme/MeowAppearance.kt:1-95` 定义，主题状态不只包含 UI 风格，还包含：

- `MeowThemeMode`：System / Light / Dark。
- 动态取色、seed color、palette style、color spec。
- Miuix Monet、AMOLED、Blur、悬浮底栏、预测式返回、界面缩放。

`MeowAppearancePage`（`component/MeowAppearancePage.kt:133-220`）让设置页与主题应用共用同一份受控状态，而不是让页面自行拼装主题参数。

### 2.2 设计 token

`theme/MeowTheme.kt:124-180` 定义了跨风格 token：

- `MeowColorScheme`：基础色、容器色、surface 层级、divider、error、success、warning。
- `MeowTypography`：pageTitle、sectionTitle、title、summary、value、button。
- `MeowShapes`：section、item、dialog。
- `MeowDimensions`：页面水平/垂直 padding、section spacing、item padding。

默认 Miuix 形状为：

- section：18.dp
- item：14.dp
- dialog：24.dp

默认页面尺寸为：

- 水平 padding：16.dp
- 垂直 padding：12.dp
- section spacing：18.dp
- item 水平/垂直 padding：18.dp / 14.dp

这说明 MeowUI 的统一不是“统一颜色”，而是统一语义角色和尺寸节奏。

### 2.3 颜色过渡

`theme/MeowColorTransition.kt:1-80` 用一条 `Animatable` 进度插值整套颜色，而不是每个颜色各跑一条动画。首帧直接取目标值，避免从错误颜色播放过渡。

`theme/MeowMiuixColors.kt:1-130` 对 Miuix `Colors` 的 50 多个角色做完整映射。`MeowMaterialColorScheme.kt` 对 Material ColorScheme 的全部角色做同样的动画映射。

## 3. 页面容器和信息组织

### 3.1 Scaffold 是页面系统，不只是布局

`component/MeowScaffold.kt:52-153` 定义：

- `MeowScaffoldEffect`：top bar、bottom bar、floating bottom bar 的 modifier 和容器色。
- `MeowScrollContext`：Material/Miuix top bar 的滚动行为和 nested scroll connection。
- `LocalMeowBackdrop`：顶栏、悬浮底栏可消费的内容层快照。
- overlay host：把搜索展开态等浮层绘制在 scaffold 窗口内。

`MeowScaffold`（约 154 行起）在 Material 和 Miuix 分支分别创建原生 Scaffold，但共享同一套：

- 页面背景。
- 顶栏滚动上下文。
- 系统 inset。
- 底栏插槽和 snackbar/overlay 关系。
- backdrop/Blur 能力。

`MeowScaffoldContent` 会把页面底色画入 backdrop 图层，Blur 才不会混入透明底。这是视觉连续性设计，不是单独控件的美化。

### 3.2 顶栏

`component/MeowTopBar.kt:108-180` 定义 `MeowTopBarAction`：

- Icon
- Text
- Menu（分组菜单、级联菜单、稳定 key）

`MeowTopBar`（约 228 行起）：

- Material 使用 large flexible top app bar。
- Miuix 使用 `MiuixTopAppBar`。
- 两者都接入 `MeowScrollContext`。
- Blur 可用时顶栏透明，由 backdrop 和半透明底色共同完成。
- 返回按钮、动作按钮由各自风格原生实现。

### 3.3 设置分组

`component/MeowPreferences.kt:128-223`：

- `MeowPreferenceScreen` 合并 scaffold padding 与 content padding。
- Material section spacing 为 13.dp，Miuix 为 12.dp。
- Material 使用 overscroll/scroll-end feedback；Miuix 还使用自己的 `overScrollVertical`。
- 页面只有一个主滚动容器。

`component/MeowPreferenceSectionScope.kt:35-130` 是关键 DSL：

- 先收集整组条目，再根据最终 count 计算分段圆角。
- 条目使用稳定 key。
- 条目隐藏时使用 `visible` 声明，而不是从 DOM/布局中静默消失。
- 发生局部 recomposition 时通过 `collectionEpoch` 整组重收，防止旧条目残留。

`component/MeowPreferences.kt:239-322` 对同一组条目做两种绘制：

- Material：`ListItemDefaults.segmentedShapes`，每项独立 Surface，首/中/末项圆角不同。
- Miuix：一个 `MiuixCard` 包含所有条目，使用 `SmallTitle` 和 Miuix Card 颜色。

`component/MeowCard.kt:50-160` 进一步证明了这一点：

- Material 可以通过 `index/count` 拼接成分段列表。
- Miuix 保持独立 Card，不强行做 Material segmented list。
- `containerColor/contentColor` 同时提供给文本和图标，状态卡在两种风格下都可读。
- 单击、长按、最小触达尺寸和点击裁切均在组件内部处理。

## 4. 组件如何共同形成统一风格

### 4.1 信息层级

从 `MeowTypography`、`MeowPreferences`、`MeowTip` 可以看到固定层级：

1. 页面标题：大标题/TopAppBar。
2. 分组标题：Material titleSmall 或 Miuix SmallTitle。
3. 条目标题：headline/title。
4. 条目摘要：summary/supporting text。
5. 当前值：value/label，位于行尾。
6. 次要操作：trailing、箭头、Switch、菜单。

同一行内不把标题、摘要、当前值和控件做成同一权重。

### 4.2 留白和密度

`MeowDimensions` 和 `MeowPreferenceScreenDefaults.contentPadding` 共同控制页面留白。分组之间大于组内条目间距；卡片内部 padding 由 `MeowCard`/`ListItemDefaults` 控制，不在每个页面手写一套。

### 4.3 动效和反馈

`component/MeowFeedback.kt:57-250`：

- `MeowTip`：Info/Success/Warning/Error 的语义提示卡。
- `MeowPullToRefresh`：Material 和 Miuix 使用各自刷新控件，但共享 top bar 滚动优先级。
- `MeowLoadingIndicator`：Material Expressive 形变指示器 / Miuix 圆环，尺寸统一为 40.dp。

`component/MeowHaptics.kt:24-95`：

- 开关、复选、单选切换：ToggleOn/ToggleOff。
- 菜单展开：ContextClick。
- 弹窗选择：Confirm。
- 滑条到端/下拉阈值：GestureThresholdActivate。
- 分档、列表到尽头：TextHandleMove。

Miuix 原生组件已经自带反馈；Material 和自绘组件补齐同样的语义。

### 4.4 导航

`component/MeowNavigation.kt:123-224` 定义 `MeowNavigationItem`（label、icon、badge、enabled、modifier）。

`MeowNavigationBar`（约 233 行起）：

- Material 可用 standard/floating。
- Miuix 可用 standard/floating。
- floating bar 使用统一语义和稳定 item。
- standard/floating 切换有淡入淡出，但首帧直接显示目标状态。
- floating bar 支持拖动指示器、Blur 回退和阴影余量。

## 5. Material 3 Expressive 与 Miuix 的差异

| 维度 | Material 3 Expressive | Miuix |
| --- | --- | --- |
| 页面容器 | `MaterialExpressiveTheme`、`MotionScheme.expressive()` | `MiuixTheme` / `ThemeController` |
| 分组列表 | `SegmentedListItem` + `ListItemDefaults.segmentedShapes` | 一张 `MiuixCard` 包含条目 |
| 形状 | `MeowShapes(section=extraLarge,item=large)` | 18/14/24.dp Miuix squircle/rounded |
| 顶栏 | LargeFlexibleTopAppBar，支持折叠 | MiuixTopAppBar，原生滚动行为 |
| 控件 | Material Switch/Checkbox/Slider/Button | Miuix Switch/Checkbox/Slider/Button |
| 菜单 | 自定义 Material anchored menu，分组圆角 | Miuix DropdownEntry/Overlay cascading |
| 刷新/加载 | Expressive pull-to-refresh/loading | Miuix PullToRefresh/CircularProgressIndicator |
| 图标 | Material Icons rounded | Miuix 自己的 icon set |
| 反馈 | 补齐 Miuix 语义的 Haptics | 原生组件自带反馈 |

源码没有把两套风格强行做成相同外观；它统一的是语义、状态、token、反馈和回调时机。

## 6. 可跨平台复用与平台适配

### 可跨平台复用

- `MeowUiStyle` 和 `MeowStyleContent` 的风格分发。
- 颜色/排版/形状/间距语义 token。
- 页面、分组、条目、状态、按钮、输入、滑条、开关的业务 API。
- 稳定 key、受控状态、提交时机和可访问性语义。
- 普通 scroll、列表密度、卡片/分组层级和空/错误/加载状态模型。

### 需要平台适配

- Android 动态取色、AMOLED、系统栏前景、预测式返回。
- Blur/RuntimeShader 可用性。
- HapticFeedback 实际实现。
- 文件选择、权限、系统 inset、窗口尺寸。
- Material/Miuix 在不同平台的原生控件和动效实现。

## 7. 对当前 KMP 项目的含义

MeowUI 不能直接作为本项目 `commonMain` 依赖，因为 `meowui` 是 Android Library。应该抽取它验证过的设计机制：

1. 统一语义组件 API。
2. 组件内部按风格调用各自原生控件。
3. 统一 theme token 与 design dimensions。
4. 页面容器统一 scroll/inset/顶栏/底栏关系。
5. 设置分组使用收集式、稳定 key 的 section 模型。
6. 颜色/主题切换、状态、反馈和可访问性由统一层管理。
