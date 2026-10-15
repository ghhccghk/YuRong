# MeowUI Docs 功能查漏补缺审计

本审计对照 MeowUI `docs/components.md`、`docs/design-guidelines.md`、`docs/getting-started.md` 与当前 KMP 实现。只记录已经从源码或构建验证确认的差异，不把未运行的视觉状态写成已验证。

## 1. 当前验证结果

最近一次共享平台与 Android/Desktop 验证通过（Miuix Monet 开关、CheckboxPreference 与 ListPopup 选择型 Preference 改造、AppTip/AppLoadingDialog 反馈组件、顶栏动作与 AppAlertDialog 基础弹窗、AppTabRow 分段选择、Miuix 悬浮底栏开关、AppBottomSheet 底部面板，AppScaffold 的嵌套滚动与折叠顶栏，Miuix 侧的 AMOLED 纯黑，Miuix 颜色标准的独立设置，以及设置页顶部的实时主题预览卡片，连接页设备列表的 AppSearchBar 即时搜索，与连接页的 AppPullToRefresh 下拉刷新）：

- `:shared:compileKotlinJvm`
- `:shared:compileKotlinJs`
- `:shared:compileKotlinWasmJs`
- `:shared:jvmTest`
- `:androidApp:assembleDebug`
- `:desktopApp:build`

当前没有重新执行 Android/Desktop 截图矩阵。iOS 完整编译仍受仓库原有 `JpegEncoder.ios.kt`、`PlatformBitmap.ios.kt`、`CurrentTime.ios.kt` 编译问题影响。

## 2. 功能状态总览

| MeowUI 能力 | 当前状态 | 当前实现依据 | 结论 |
| --- | --- | --- | --- |
| 双风格 UI 分支 | 已完成 | `AppStyleContent`、`UiTheme.kt` | 业务页面使用 `App*` 语义 API |
| 主题入口 | 已完成 | `AppTheme`、`AppAppearance` | 已接入全局主题 |
| System / Light / Dark | 已完成 | `AppThemeMode` | 设置页可切换 |
| 动态取色 | 部分完成 | Android accent + `appSeedColor` | Android 有系统 accent；其他平台回退 seed |
| Seed color | 已完成 | `seedColorArgb`、`appSeedColor` | 支持持久化 |
| Palette style | 已完成 | `AppPaletteStyle`、MaterialKolor/Miuix 映射 | 支持 9 种风格 |
| Color spec | 已完成 | `AppColorSpec` + `colorSpec` 持久化 | 默认跟随 palette 推导，也可显式选择 Spec 2021 / Spec 2025；仅作用于 Miuix Monet |
| AMOLED | 已完成 | `withAmoledBackground`（Material `ColorScheme` / Miuix `Colors` 两个重载） | 暗色模式下两套风格都会压黑大面积底色 token |
| 主题动画 | 已完成 | `AppColorTransition` | Material/Miuix 颜色列表插值 |
| 设置持久化 | 已完成 | `AppPreferences` + 平台 store | Android/JVM/JS/Wasm/iOS 均有后端 |
| 设置类型和默认值 | 已完成 | `PreferenceKey`/`PreferenceType` | 类型安全 key 和默认值 |
| 设置 Flow/Compose 绑定 | 已完成 | `rememberAppAppearance` | 支持读取、观察和异步写入 |
| 预设色 | 已完成 | `AppThemePresets` | 稳定 ID、亮暗共用 seed |
| 完整取色器 | 部分完成 | `AppColorPickerDialog` | 滑条/网格/hex/预览/确认取消已有；取色过程中不会实时改写全局主题 |
| 实时主题预览 | 已完成 | `AppThemePreview` | 设置页顶部预览卡片，随风格、明暗模式与配色实时变化 |
| 设置分组 | 部分完成 | `AppSection`、`AppListRow` | 视觉分组已有；没有 MeowUI 的收集式稳定 key/分段圆角 |
| Switch preference | 已完成 | `AppSwitchPreference` | Material/Miuix 分支 |
| Checkbox preference | 已完成 | `AppCheckboxPreference` | Material Checkbox + Miuix CheckboxPreference；作为通用语义组件保留 |
| Slider preference | 部分完成 | `AppSlider` | 有通用 Slider；没有标题/摘要/值/提交语义的 Preference 行 |
| Popup / Dropdown | 已完成 | `AppPopupPreference` | Miuix `OverlaySpinnerPreference`（ListPopup）+ Material `DropdownMenu`；界面风格与调色板已改用 |
| Text input preference | 缺失 | 无 `AppTextInputPreference` | 只有取色器 hex 输入 |
| Action preference | 已完成 | `AppActionPreference` | Miuix `ArrowPreference` + Material 点击行加箭头；颜色动作行已改用 |
| Dialog | 部分完成 | `AppColorPickerDialog`、`AppLoadingDialog`、`AppAlertDialog` | 基础 Alert 已完成；Single choice、Text input 仍未实现 |
| TopBar actions/menu | 部分完成 | `AppTopBar(title, actions)`、`AppIconButton` | 支持单个图标动作；文字动作、下拉菜单仍未实现 |
| 搜索框 | 已完成 | `AppSearchBar` | 连接页 WiFi / P2P / BLE 设备列表已接入即时过滤 |
| Scaffold 滚动顶栏 | 部分完成 | `AppScaffold(scrollBehavior)`、`AppTopBar(scrollBehavior)` | 已支持共享嵌套滚动与顶栏收起/展开；Pull-to-Refresh 协作已由 `AppPullToRefresh` 补齐；Blur backdrop、overlay host 仍未实现 |
| Tab | 已完成 | `AppTabRow` | Material 3 与 Miuix 都用原生 `TabRow`；连接页 AP/P2P 与设置页明暗模式已改用 |
| 普通/悬浮底栏 | 已完成 | `AppBottomNavigation(floating)` | Miuix 可选原生 `FloatingNavigationBar` 悬浮胶囊；Material 3 保持原生 `NavigationBar` |
| 导航分页 | 缺失 | 无 pager | 当前使用四个固定 tab |
| Bottom Sheet | 已完成 | `AppBottomSheet` | Material 3 `ModalBottomSheet` / Miuix `WindowBottomSheet`；投屏调试日志已接入 |
| Snackbar | 缺失 | 无组件 | 操作反馈目前依赖 `AppTip` 与状态文本 |
| Tip | 已完成 | `AppTip`、`AppTipKind` | 信息/成功/警告/错误四种类型，两风格共用语义 token |
| Pull to refresh | 已完成 | `AppPullToRefresh` | Material 3 `PullToRefreshBox` / Miuix `PullToRefresh`；连接页已接入 WiFi 重扫与 P2P 重新发现 |
| Blur | 缺失 | 无 `MeowBlur` 对应能力 | 当前项目未启用表面模糊 |
| 自适应布局 | 缺失 | 无双栏/宽屏布局组件 | 当前仍是固定 tab 页面 |
| 触觉反馈 | 缺失 | 无统一 `AppHaptics` | Miuix 原生控件自带部分反馈，Material/自绘控件未补齐 |
| 可访问性 | 部分完成 | 色块和控件有部分 semantics | 仍需系统检查图标、选择、禁用、重复点击 |
| schema 迁移 | 已完成 | `SCHEMA_VERSION` + 增量迁移 + 未来版本保护 | v1 -> v2 -> v3 -> v4 纯增量迁移，未来版本不被 `write` 覆盖 |

## 3. 已经正常工作的部分

### 主题和颜色

- `AppAppearance` 已统一管理 UI 风格、明暗模式、颜色来源、seed、preset、palette、颜色标准、AMOLED、动画开关、Miuix Monet 开关。
- Miuix 可整体关闭 Monet（`miuixMonetEnabled`）；关闭后不再由种子色生成配色，回退到 Miuix 原生 HyperOS 配色。
- `materialKolor 5.0.1` 已声明为 KMP 依赖，Gradle metadata 包含 Android、JVM、JS、Wasm、iOS target。
- Material 使用 `dynamicColorScheme` 生成完整 `ColorScheme`。
- Miuix 使用 `ThemeController` 和 palette style 生成完整 `Colors`。
- Miuix 的颜色标准可独立于调色板选择（`AppColorSpec`）：`Auto` 沿用调色板推导结果，显式选择 Spec 2021 / Spec 2025 时交给用户决定；调色板本身不支持 Spec 2025 时 Miuix 会自动降级到 Spec 2021，不会报错。该设置项只在 Monet 开启时显示。
- AMOLED 对两套风格分别处理，语义对齐：
  - Material 侧压黑 `background/surface/surfaceDim/surfaceContainer/Low/Lowest`。
  - Miuix 侧压黑 `background/surface/surfaceVariant/surfaceContainer/High/Highest`，因为它没有 `surfaceDim` 与 `Low(est)` 档位。
  - 文字、描边、分隔线和控件色保持原值，纯黑背景上仍有可读层次；变换放在颜色过渡动画之前，开关切换会走同一套插值。
  - `MiuixAmoledSchemeTest` 覆盖"关闭时原样返回""开启时压黑底色 token""开启时保留文字与描边"三种情况。
- `AppColorTransition` 使用单进度插值，开关关闭时直接使用目标颜色。
- `AppThemePreview` 是设置页顶部的实时预览卡片，只使用 `AppDesign`/`AppText`/`AppCard`/`AppButton` 等语义 API：展示 primary、primaryContainer、secondaryContainer、surfaceContainerHighest、error 色卡与示例标题、正文、按钮、只读开关，切换风格、明暗或配色时立即反映结果，切换前可以先确认效果。

### 取色

- `AppColorPickerDialog` 同时支持：
  - HSV 滑条。
  - HSV 网格。
  - `#RRGGBB` 输入。
  - 当前颜色预览。
  - 非法 hex 时禁用确认。
  - 取消不修改正式配置。
- `AppThemePresetSwatch` 使用 `Role.RadioButton` 和 `contentDescription`。
- `AppColorPickerTest` 已覆盖 hex 解析和预设 ID 唯一性。

### 列表与搜索

- `AppSearchBar` 提供统一的搜索输入语义：Material 3 使用原生 `OutlinedTextField` 加搜索图标与按需出现的清除按钮，Miuix 使用原生 `SearchBar` + `InputField`（胶囊外形、原生清空图标，返回键收起搜索时同步清空关键字）。
- 业务页面只传 `query`、`onQueryChange`、`label`、`enabled` 与 `modifier`，不接触任何一套风格的私有类型。
- `matchesSearchQuery` 统一过滤规则：去掉首尾空白、忽略大小写、任一字段包含即命中、关键字为空时放行全部、字段为 `null` 时视为不匹配。
- 连接页已接入真实业务场景：WiFi 扫描结果按 `ssid` 过滤（先过滤再取前 5 条），P2P 设备与 BLE 设备按名称和地址过滤，无匹配时显示空状态；只改界面过滤，未触碰扫描与连接逻辑。

### 下拉刷新

- `AppPullToRefresh` 提供统一的“下拉重新加载”语义：Material 3 使用原生 `PullToRefreshBox`（M3 默认指示器与阈值），Miuix 使用原生 `PullToRefresh`（原生圆环指示器与下拉文案）。
- 业务页面只传 `isRefreshing`、`onRefresh`、`enabled`、可选的 `scrollBehavior` 与可滚动内容，不接触任何一套风格的私有类型。
- 内容必须是真正可滚动的容器（`LazyColumn` / `verticalScroll`），否则两套风格的嵌套滚动都收不到手势。
- `scrollBehavior` 传入的是脚手架创建的同一个滚动行为对象，Miuix 下让下拉手势与顶栏展开/收起协同；Material 3 的 `PullToRefreshBox` 不需要该参数。
- 连接页已接入真实业务场景：AP 模式下拉重新扫描 WiFi，P2P 模式下拉重新发起设备发现，刷新指示器直接绑定后台扫描状态，扫描进行中忽略重复触发；BLE 保留显式扫描按钮，避免与“停止扫描”状态互相干扰。

### 设置交互

- `AppPopupPreference` 统一承担“从列表中选择一项”的语义：Miuix 走原生 SpinnerPreference（ListPopup），Material 3 走 DropdownMenu，重复选中当前项不会触发写入。
- 界面风格与调色板已从开关/循环点击改为列表弹窗选择；颜色入口行使用 `AppActionPreference`，Miuix 下带原生箭头与按压反馈。
- `AppCheckboxPreference` 保留为通用组件，供后续布尔型设置项复用。

### 反馈组件

- `AppTip` 覆盖信息/成功/警告/错误四种类型，两种风格都使用原生 Card 与 `AppDesign` 语义色，带 live region 和关闭按钮无障碍语义。
- `AppLoadingDialog` 在 Miuix 使用原生 `WindowDialog`，Material 3 使用原生 `AlertDialog`，用于连接、投屏这类长时间操作的进度反馈。
- 连接页（WiFi/BLE 失败、P2P 部分设备失败）与投屏页（投屏失败、协商中）已接入上述组件。

### 顶栏与弹窗

- `AppTopBar(title, modifier, actions)` 在 Miuix 下把标题与操作都交给原生 `MiuixTopAppBar`；Material 3 下页面标题仍由页面内 `AppPageHeader` 承担，只有传入 `actions` 时才渲染一个不显示标题的原生 `TopAppBar` 承载操作，避免标题重复。
- `AppIconButton` 为顶栏等紧凑位置提供单一图标操作：Miuix 用 `MiuixIconButton` + `MiuixIcon`，Material 3 用 `IconButton` + `Icon`，两者都从各自的 `ContentColor` CompositionLocal 取默认着色。
- `AppAlertDialog` 覆盖标题、说明文字与确认/取消的基础确认场景：Miuix 用原生 `WindowDialog`，Material 3 用原生 `AlertDialog`；`dismissText` 为空时退化为单按钮告知型弹窗。
- 连接页已接入真实场景：BLE 处于连接状态时顶栏出现“断开 BLE 连接”图标动作，点击后由 `AppAlertDialog` 二次确认，确认后调用既有的 `disconnectBle()`，未新增协议层逻辑。

### 导航与面板

- `AppTabRow` 提供分段选择语义：Miuix 与 Material 3 都使用各自原生 `TabRow`，保持相同的选中索引回调与禁用行为；连接页 AP/P2P 与设置页明暗模式已改用。
- `AppBottomNavigation` 增加 `floating` 参数：Miuix 开启后使用原生 `FloatingNavigationBar` 悬浮胶囊（可在设置页关闭回到常规底栏），Material 3 始终使用原生 `NavigationBar`，两者选中与回调时机一致。
- `AppBottomSheet` 承载从底部滑出的次级内容：Material 3 使用原生 `ModalBottomSheet`，Miuix 使用原生 `WindowBottomSheet`；下滑、点击遮罩与返回键都统一回调 `onDismissRequest`。
- 投屏页的“调试日志”由页内长列表改为顶栏图标动作 + 底部面板，离开投屏页时面板自动关闭，未改动任何投屏协议逻辑。
- `AppScrollBehavior` / `rememberAppScrollBehavior()` 把两套风格的 scrollBehavior 收敛为一个业务语义对象：业务页面创建一次后同时交给 `AppScaffold`（挂载 `nestedScroll`）与 `AppTopBar`（驱动收起/展开），页面不接触任何一方主题的私有类型。
- 未传入滚动行为时 `AppScaffold` 不额外包裹容器，保持原有固定顶栏结构；主界面已接入，Miuix 顶栏随内容滚动收起为小标题，Material 3 的顶栏动作同样随滚动隐藏并在回滚时恢复。

### 持久化

- `PreferenceStore` 具备 `read/observe/write/delete/clear`。
- `AppPreferences` 将 `AppAppearance` 拆成类型化 key。
- Android、JVM、JS、Wasm、iOS 都有对应 store。
- `AppPreferencesTest` 覆盖默认值、读写回环、未知枚举、reset。
- Composable 通过 `rememberAppAppearance(AppPreferences)` 异步写入。

## 4. 重要缺口

### P0：设置 schema 迁移（已处理）

`AppPreferences.kt` 现在把当前版本定义为 4（`AppPreferenceKeys.SCHEMA_VERSION`），迁移策略明确：

- 每次版本升级都属于纯增量迁移：新增的键由 `PreferenceKey` 默认值补齐，不重置用户已有配置。
- v1 -> v2 补 `miuix_monet`（默认 `true`，升级后仍是原有 Monet 配色）。
- v2 -> v3 补 `floating_navigation_bar`（默认 `false`，升级后仍是原有常规底栏）。
- v3 -> v4 补 `color_spec`（默认 `Auto`，升级后仍按调色板推导颜色标准）。
- 版本 0/缺失值按历史配置对待，保留已有键值（历史 key 兼容），只有缺失的新键才回退默认值，不重置用户配置。
- 高于当前版本的数据禁止被 `write` 覆盖，返回 `PreferenceWriteResult.Failure`。
- 已补充默认值、回环、旧 schema、未来 schema 的测试。

后续新增设置项时：递增 `SCHEMA_VERSION`、在 `AppPreferenceKeys.all` 末尾追加 key、同步 `read`/`observe`/`write`/`decode`，并补测试。

### P1：缺少数值型/选择型 Preference 组件（进行中）

MeowUI 文档中的 Checkbox、Slider Preference、Popup/Dropdown、Text input、Action Preference 逐步补齐：

- `AppCheckboxPreference`（已完成）。
- `AppPopupPreference`（已完成）：Miuix 使用 `OverlaySpinnerPreference`（内部为 ListPopup），Material 3 使用 `DropdownMenu`。
- `AppActionPreference`（已完成）：Miuix 使用 `ArrowPreference`，Material 3 使用点击行加箭头提示。
- `AppSliderPreference`（未实现）：当前没有数值型设置项，缺少真实业务场景。
- `AppTextInputPreference`（未实现）：Miuix 偏好组件里没有对应的文本输入 Preference，取色器内的 hex 输入已覆盖现有需求。

这些组件保持两种风格的整行点击、启用状态和提交时机一致，并沿用 `AppDesign` token。

### P1：缺少反馈组件

反馈组件逐步补齐：

- `AppTip`（已完成）：信息/成功/警告/错误四种类型，Miuix 与 Material 3 都用原生 Card 承载，配色取自 `AppDesign` 语义 token；连接页与投屏页已接入错误/警告提示。
- `AppLoadingDialog`（已完成）：Miuix 使用原生 `WindowDialog`，Material 3 使用原生 `AlertDialog`；投屏协商阶段已接入，可手动关闭。
- `AppSnackbar`（未实现）：Miuix 有原生 Snackbar，但需要跨平台的 host/state 抽象，等出现明确业务场景再补。
- `AppHaptics`（未实现）：Miuix 原生控件自带部分触觉反馈，Material 与自绘控件尚未统一。

`AppColors` 同步补充了 `errorContainer`/`onErrorContainer`，由 Material 3 与 Miuix 原生错误容器色映射，供错误类提示复用。

### P1：Dialog 只覆盖基础确认场景

- `AppAlertDialog`（已完成）：标题 + 说明 + 确认/取消，支持单按钮退化，已用于 BLE 断开确认。
- 单选列表弹窗未实现：Miuix 侧可基于 `WindowDialog` + `ListPopup` 组合，Material 3 侧用 `AlertDialog` + 单选行；目前选择型设置已由 `AppPopupPreference` 覆盖，暂不重复实现。
- 文本输入弹窗未实现：现有唯一文本输入场景（取色器 hex）在 `AppColorPickerDialog` 内闭环，暂不需要独立组件。

### P1：Scaffold 只有基础能力

`AppScaffold` 已补上共享 nested scroll 与顶栏折叠/展开（`AppScrollBehavior`），仍未实现 MeowUI 的：

- top bar / bottom bar Blur backdrop。
- pull-to-refresh 协作（已由 `AppPullToRefresh` 与连接页接入补齐）。
- overlay host。

这不影响当前四个 tab 的基础布局，但会限制后续复杂页面。

### P2：设置 UI 缺少 MeowUI Appearance Page 的完整结构

当前已经有：
- 实时主题预览卡片。
- 明暗模式。
- 颜色来源。
- palette。
- 颜色标准独立选择（仅 Monet 开启时显示）。
- AMOLED。
- 动画。
- 自定义颜色。
- Miuix Monet 开关。
- 悬浮底栏开关。

缺少：
- 界面缩放。
- Blur 开关（底层缺少跨平台模糊能力，暂不提供开关）。
- Predictive Back 开关（需要各平台原生能力，暂不提供开关）。
- 自定义 preference 插槽。

这些不是当前业务必需，但会影响外观设置的完整性。

### P2：单选/输入弹窗、菜单等通用能力尚未实现

搜索框已随连接页设备列表落地，见第 3 节“列表与搜索”；单选列表弹窗、文本输入弹窗与下拉菜单仍属于通用 UI 能力，当前业务页面没有强需求，但 MeowUI 文档已经验证它们是统一 UI 体系的一部分。Bottom Sheet 已完成，见第 3 节“导航与面板”。

## 5. 不建议直接照搬的部分

- Xposed/libxposed 设置服务。
- Android-only 的系统栏控制和 `Build.VERSION` 判断。
- Material 3 Expressive 的完整 `MaterialExpressiveTheme` 体验。
- 所有 MeowUI 页面组件一次性引入。

当前项目是通用 KMP 投屏工具，不需要为了凑齐 MeowUI 文档而引入没有业务场景的组件。

## 6. 推荐补齐顺序

1. **设置 schema 迁移和未来版本保护**（已完成）。
2. **AppCheckboxPreference / AppPopupPreference / AppActionPreference（已完成）**；`AppSliderPreference`、`AppTextInputPreference` 等有真实业务设置项后再补。
3. **AppTip、AppLoadingIndicator/AppLoadingDialog、统一错误/成功反馈**（已完成；`AppSnackbar` 等有业务场景再补）。
4. **AppTopBar actions/menu 和基础 Dialog**（已完成：`AppTopBar` 支持 actions + `AppIconButton` + `AppAlertDialog`；文字动作、下拉菜单、单选/输入弹窗按业务场景再补）。
5. **AppTabRow、floating navigation、Bottom Sheet**（已完成：`AppTabRow`、`AppBottomNavigation(floating)` 及其设置开关、`AppBottomSheet` 及投屏调试日志场景）。
6. **Scaffold 滚动顶栏（已完成共享嵌套滚动 + 折叠顶栏）、Pull-to-Refresh（已完成 `AppPullToRefresh` 与连接页接入）、Blur、自适应布局**（其余项等有业务场景再补）。
7. **通用列表能力（搜索框已完成）**：`AppSearchBar` 已接入连接页的 WiFi / P2P / BLE 设备列表，统一过滤规则由 `matchesSearchQuery` 承担并配有单元测试；`AppPullToRefresh` 已补齐下拉刷新，后续按真实业务场景再评估自适应布局。

每一批都应遵守 `AGENTS.md`：一份业务 API、两套原生实现，并在完成一个独立功能后提交。
