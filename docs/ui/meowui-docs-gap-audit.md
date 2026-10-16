# MeowUI Docs 功能查漏补缺审计

本审计对照 MeowUI `docs/components.md`、`docs/design-guidelines.md`、`docs/getting-started.md` 与当前 KMP 实现。只记录已经从源码或构建验证确认的差异，不把未运行的视觉状态写成已验证。

## 1. 当前验证结果

最近一次共享平台与 Android/Desktop 验证通过（Miuix Monet 开关、CheckboxPreference 与 ListPopup 选择型 Preference 改造、AppTip/AppLoadingDialog 反馈组件、顶栏动作与 AppAlertDialog 基础弹窗、AppSegmentedControl 分段选择、Miuix 悬浮底栏开关、AppBottomSheet 底部面板，AppScaffold 的嵌套滚动与折叠顶栏，Miuix 侧的 AMOLED 纯黑，Miuix 颜色标准的独立设置，以及设置页顶部的实时主题预览卡片，连接页设备列表的 AppSearchBar 即时搜索，连接页的 AppPullToRefresh 下拉刷新，主界面的 AppAdaptiveScaffold 宽屏侧边导航，设置页的界面缩放（AppSliderPreference 滑条 + LocalDensity 全局缩放），以及反馈组件的 AppSnackbar（一份业务状态桥接两套原生 SnackbarHostState，并接入 BLE 断开 / Wi-Fi 断开 / 停止投屏三个真实场景），宽屏内容双栏 `AppAdaptiveTwoColumn`（设备信息页首个接入），以及 `TabRow` 全面替换为分段控件、动态配色同步修复（AppDesign token 缓存键改为颜色快照，修掉 Miuix 换色时"只有一部分组件跟着切"）、Android 预测性返回、主题预览卡片重做、连接与投屏合并、Material 3 无顶栏动作时嵌套滚动吞掉内容滑动的修复）：

- `:shared:compileKotlinJvm`
- `:shared:compileKotlinJs`
- `:shared:compileKotlinWasmJs`
- `:shared:jvmTest`（194 个用例全部通过）
- `:androidApp:assembleDebug`
- `:desktopApp:build`
- `:webApp:jsBrowserDistribution`

最近一次视觉回归在 Android 14 模拟器（1080x2200 / density 420）上执行：设置页在 Miuix 与 Material 3 两种风格、浅色与深色两种模式、预设色与动态取色两种来源下各截取一次，确认「Miuix 切回 Material 3 后仍可上下拖动」「切成动态取色后分组标题、滑条与分段控件同步换色」「连接页的投屏子页与底栏导航点击均可达」。未重新执行 Desktop/Web 截图矩阵。`:webApp:assemble` 的 wasmJs 生产优化步骤在本机以 `wasm-opt.exe`（binaryen 130）崩溃退出（NTSTATUS 0xC0000409）而失败，属于本机工具链问题，`:shared:compileKotlinWasmJs` 与 `:webApp:jsBrowserDistribution` 均通过。iOS 完整编译仍受仓库原有 `JpegEncoder.ios.kt`、`PlatformBitmap.ios.kt`、`CurrentTime.ios.kt` 编译问题影响。

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
| 实时主题预览 | 已完成 | `AppThemePreview` | 设置页顶部的手机缩略图预览，随风格、明暗模式、配色与 Miuix Monet 开关实时变化 |
| 设置分组 | 部分完成 | `AppSection`、`AppListRow` | 视觉分组已有；没有 MeowUI 的收集式稳定 key/分段圆角 |
| Switch preference | 已完成 | `AppSwitchPreference` | Material/Miuix 分支 |
| Checkbox preference | 已完成 | `AppCheckboxPreference` | Material Checkbox + Miuix CheckboxPreference；作为通用语义组件保留 |
| Slider preference | 已完成 | `AppSliderPreference` | Material 3 原生 `Slider` / Miuix 原生 `SliderPreference`（标题、摘要、当前值文本、拖动中预览 + 松手落库）；设置页“界面缩放”已接入 |
| Popup / Dropdown | 已完成 | `AppPopupPreference` | Miuix `OverlaySpinnerPreference`（ListPopup）+ Material `DropdownMenu`；界面风格与调色板已改用 |
| Text input preference | 暂不实现 | 无 `AppTextInputPreference` | 本轮决定不做：现有唯一文本输入场景（取色器 hex）已在 `AppColorPickerDialog` 内闭环 |
| Action preference | 已完成 | `AppActionPreference` | Miuix `ArrowPreference` + Material 点击行加箭头；颜色动作行已改用 |
| Dialog | 部分完成 | `AppColorPickerDialog`、`AppLoadingDialog`、`AppAlertDialog` | 基础 Alert 已完成；Single choice、Text input 本轮决定不做 |
| TopBar actions/menu | 部分完成 | `AppTopBar(title, actions)`、`AppIconButton` | 支持单个图标动作；文字动作、下拉菜单仍未实现 |
| 搜索框 | 已完成 | `AppSearchBar` | 连接页 WiFi / P2P / BLE 设备列表已接入即时过滤 |
| Scaffold 滚动顶栏 | 部分完成 | `AppScaffold(scrollBehavior)`、`AppTopBar(scrollBehavior)` | 已支持共享嵌套滚动与顶栏收起/展开；无顶栏动作时不再吞掉内容滑动；Pull-to-Refresh 协作已由 `AppPullToRefresh` 补齐；Blur backdrop、overlay host 仍未实现 |
| 分段选择 | 已完成 | `AppSegmentedControl` | Material 3 `SingleChoiceSegmentedButtonRow` / Miuix 自绘分段控件；已替换全部 `TabRow` 用法（连接页 AP/P2P、设置页明暗模式与颜色来源、连接页设备连接/投屏子页） |
| 普通/悬浮底栏 | 已完成 | `AppBottomNavigation(floating)` | Miuix 可选原生 `FloatingNavigationBar` 悬浮胶囊；Material 3 保持原生 `NavigationBar` |
| 导航分页 | 缺失 | 无 pager | 当前使用三个固定入口（连接 / 设备 / 设置），投屏已并入连接页 |
| 预测性返回 | 已完成 | `AppBackHandler` + `android:enableOnBackInvokedCallback` | Android 13+ 走 `OnBackInvokedCallback` 由系统播放返回动画；其余平台由 Compose 宿主调度，无系统返回手势时不触发 |
| Bottom Sheet | 已完成 | `AppBottomSheet` | Material 3 `ModalBottomSheet` / Miuix `WindowBottomSheet`；投屏调试日志已接入 |
| Snackbar | 已完成 | `AppSnackbar`、`AppSnackbarHost`、`rememberAppSnackbarHostState` | Miuix 与 Material 3 各自使用原生 `SnackbarHost`；BLE 断开、Wi-Fi 断开、停止投屏已接入 |
| Tip | 已完成 | `AppTip`、`AppTipKind` | 信息/成功/警告/错误四种类型，两风格共用语义 token |
| Pull to refresh | 已完成 | `AppPullToRefresh` | Material 3 `PullToRefreshBox` / Miuix `PullToRefresh`；连接页已接入 WiFi 重扫与 P2P 重新发现 |
| Blur | 缺失 | 无 `MeowBlur` 对应能力 | 当前项目未启用表面模糊；跨平台模糊要先确认 Miuix / Compose 版本能否复用，本轮暂缓 |
| 自适应布局 | 已完成 | `AppAdaptiveScaffold`、`AppAdaptiveTwoColumn` | 宽度达到 600dp 后把底部导航换成侧边导航栏；内容区达到 840dp 后由 `AppAdaptiveTwoColumn` 拆成左右双栏 |
| 触觉反馈 | 暂不实现 | 无统一 `AppHaptics` | 本轮决定不做：等有真实业务场景再补；Miuix 原生控件已自带部分反馈 |
| 可访问性 | 部分完成 | 色块和控件有部分 semantics | 仍需系统检查图标、选择、禁用、重复点击 |
| schema 迁移 | 已完成 | `SCHEMA_VERSION` + 增量迁移 + 未来版本保护 | v1 -> v2 -> v3 -> v4 -> v5 纯增量迁移，未来版本不被 `write` 覆盖 |

## 3. 已经正常工作的部分

### 主题和颜色

- `AppAppearance` 已统一管理 UI 风格、明暗模式、颜色来源、seed、preset、palette、颜色标准、AMOLED、动画开关、Miuix Monet 开关与界面缩放。
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
- Miuix 的 `Colors` 是字段为 `mutableStateOf` 的可变 `@Stable` 类：`MiuixTheme(colors = ...)` 只会对同一个实例就地更新字段（`updateColorsFrom`），对象引用不变、也没有重写 `equals`。因此 `rememberMiuixAppThemeTokens` 不能把实例本身当 `remember` 的 key，否则 `AppDesign.colors` 会永远停留在首次组合的配色——读取 `AppDesign` 的组件不换色，而直接读 `MiuixTheme.colorScheme` 的原生组件会换色，表现为"只有一部分组件跟着切"。现在改用颜色快照 `toColorList()` 作为缓存键，任意字段变化都会触发重组并重算 token。
  - `MiuixColors.toColorList()` 同时是颜色过渡动画和 token 缓存的唯一来源，Miuix 新增颜色字段时必须同步补充。
  - `MiuixColorSnapshotTest` 用反射逐个就地改写颜色字段，锁定"快照覆盖全部颜色字段"与"快照往返顺序一致"；`MiuixDynamicColorSwitchTest` 用 `ImageComposeScene` 离屏渲染真实跑一遍 `AppTheme`，断言换种子色后 `AppDesign.colors.primary` 已经变化（修复前该用例必然失败）。
- Miuix 分支在 `MiuixTheme(colors = ...)` 内层显式提供 `MiuixLocalContentColor`（跟随动画后的 `onBackground`）：`MiuixTheme` 只重新提供颜色、排版与 Indication，不会更新 Miuix 自己的内容色，否则 `MiuixText`、`MiuixTextField`、`MiuixIcon` 等组件会一直沿用外层 controller 的静态内容色，切换动态配色时不同步。
- `AppThemePreview` 是设置页顶部的实时预览卡片，参考 KernelSU 的设置页改成一台手机缩略图：外框、应用名、顶部高亮卡片、正文行与底部胶囊导航全部取自 `AppDesign` 语义 token，底部导航入口数量与真实导航一致（`navigationItemCount`）。
- 缩略图下方用一行 caption 说明当前生效的界面风格与明暗模式；切换风格、明暗模式、配色或 Miuix Monet 开关时预览立即跟随变化，缩略图只表达层级关系、不承载任何交互。

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
- `AppSliderPreference` 承担数值型设置行：整行含标题、摘要、当前值文本与滑条，Material 3 使用原生 `Slider`，Miuix 使用原生 `SliderPreference`（滑条排在标题下方），两者回调时机一致。
- 设置页“界面缩放”是第一个真实数值型设置项，拖动中只更新本地草稿做即时预览，松手后才吸附档位并写入设置。

### 反馈组件

- `AppTip` 覆盖信息/成功/警告/错误四种类型，两种风格都使用原生 Card 与 `AppDesign` 语义色，带 live region 和关闭按钮无障碍语义。
- `AppLoadingDialog` 在 Miuix 使用原生 `WindowDialog`，Material 3 使用原生 `AlertDialog`，用于连接、投屏这类长时间操作的进度反馈。
- `AppSnackbar` 承担一次性操作的轻量确认：`AppSnackbarHostState` 同时持有 Miuix 与 Material 3 两套原生 `SnackbarHostState`，按当前风格转发 `showSnackbar`，业务页面只持有同一个状态对象，不感知风格类型。
- 提示宿主由 `AppScaffold` / `AppAdaptiveScaffold` 的 `snackbarHost` 槽位挂载，定位与底部导航避让交给各自原生 Scaffold；切换风格时会主动清掉另一种风格上残留的提示，避免对应的 `showSnackbar` 协程永远挂起。
- 连接页（WiFi/BLE 失败、P2P 部分设备失败）与投屏页（投屏失败、协商中）已接入上述组件；BLE 断开、Wi-Fi 断开、停止投屏改用轻量提示回报结果。

### 顶栏与弹窗

- `AppTopBar(title, modifier, actions)` 在 Miuix 下把标题与操作都交给原生 `MiuixTopAppBar`；Material 3 下页面标题仍由页面内 `AppPageHeader` 承担，只有传入 `actions` 时才渲染一个不显示标题的原生 `TopAppBar` 承载操作，避免标题重复。
- `AppIconButton` 为顶栏等紧凑位置提供单一图标操作：Miuix 用 `MiuixIconButton` + `MiuixIcon`，Material 3 用 `IconButton` + `Icon`，两者都从各自的 `ContentColor` CompositionLocal 取默认着色。
- `AppAlertDialog` 覆盖标题、说明文字与确认/取消的基础确认场景：Miuix 用原生 `WindowDialog`，Material 3 用原生 `AlertDialog`；`dismissText` 为空时退化为单按钮告知型弹窗。
- 连接页已接入真实场景：BLE 处于连接状态时顶栏出现“断开 BLE 连接”图标动作，点击后由 `AppAlertDialog` 二次确认，确认后调用既有的 `disconnectBle()`，未新增协议层逻辑。
- Material 3 在未传 `actions` 时不渲染原生 `TopAppBar`，但 `AppScaffold` 仍然挂了 `nestedScroll`；原生 `EnterAlwaysScrollBehavior.onPreScroll` 只要 `heightOffset` 变化就会消费纵向位移，而顶栏缺席时 `heightOffsetLimit` 仍是初始哨兵值，于是所有上滑都被吃掉，页面看起来“无法触摸”。
- 修法：`AppScrollBehavior` 用 `setMaterialTopBarPresent` 记录 Material 顶栏是否真的渲染，只有存在时才把 `onPreScroll/onPostScroll/onPreFling/onPostFling` 转发给 Material 的 `nestedScrollConnection`，否则返回 `Offset.Zero` / `Velocity.Zero`；`AppTopBar` 在 Material 分支用 `DisposableEffect` 登记与注销该状态。Miuix 分支始终渲染原生顶栏，行为不变。
- 回归测试 `AppTopBarScrollTest` 用无窗口的 `ImageComposeScene` 播放真实触摸拖动（`PointerType.Touch` + 按下状态的多步 Move），分别覆盖 Miuix 对照组、Material 3 无顶栏动作、以及 Material 3 有顶栏动作时顶栏仍能正常收起，避免过度修复；另有 `UiStyleSwitchInteractivityTest` 覆盖 ListPopup 展开时切回 Material 3 后点击仍可达。

### 导航与面板

- `AppSegmentedControl` 提供分段选择语义：Material 3 使用原生 `SingleChoiceSegmentedButtonRow`，Miuix 使用自绘分段控件，两者保持相同的选中索引回调与禁用行为，并且都不注册 `nestedScroll`，切换分段不会影响页面上下拖动。
- 它替换了原先基于 `TabRow` 的 `AppTabRow`：`TabRow` 在 Miuix 下会卷走整页的嵌套滚动，切换明暗模式后页面无法继续上下拖动，选中态视觉也不符合设置页语义。
- 连接页把「设备连接」与「投屏」合并为一个入口：顶层导航由四个入口收敛为「连接 / 设备 / 设置」，投屏成为连接页下的子功能，与设备连接共用同一个脚手架、顶栏动作与滚动宿主；两个子页面各自保留原有实现，用 `weight` 占位而不是嵌套 `LazyColumn`。
- `AppBottomNavigation` 增加 `floating` 参数：Miuix 开启后使用原生 `FloatingNavigationBar` 悬浮胶囊（可在设置页关闭回到常规底栏），Material 3 始终使用原生 `NavigationBar`，两者选中与回调时机一致。
- `AppBottomSheet` 承载从底部滑出的次级内容：Material 3 使用原生 `ModalBottomSheet`，Miuix 使用原生 `WindowBottomSheet`；下滑、点击遮罩与返回键都统一回调 `onDismissRequest`。
- 投屏页的“调试日志”由页内长列表改为顶栏图标动作 + 底部面板，离开投屏页时面板自动关闭，未改动任何投屏协议逻辑。
- `AppScrollBehavior` / `rememberAppScrollBehavior()` 把两套风格的 scrollBehavior 收敛为一个业务语义对象：业务页面创建一次后同时交给 `AppScaffold`（挂载 `nestedScroll`）与 `AppTopBar`（驱动收起/展开），页面不接触任何一方主题的私有类型。
- 未传入滚动行为时 `AppScaffold` 不额外包裹容器，保持原有固定顶栏结构；主界面已接入，Miuix 顶栏随内容滚动收起为小标题，Material 3 的顶栏动作同样随滚动隐藏并在回滚时恢复。
- `AppAdaptiveScaffold` 承担宽屏自适应导航：可用宽度达到 600dp（`AppNavigationRailBreakpoint`）时把底部导航换成左侧导航栏，窄屏完全保持原有底部导航结构；断点判断抽成纯函数 `appNavigationLayoutMode()`，由 `AppNavigationLayoutModeTest` 覆盖 360dp / 599dp / 600dp / 1280dp 四个宽度。
- 侧边导航栏同样是一份业务 API、两套原生实现：Miuix 使用原生 `NavigationRail` + `NavigationRailItem`（自带系统栏内边距与选中指示器），Material 3 使用原生 `NavigationRail` + `NavigationRailItem`，两者共用同一份 `AppNavigationItem` 与选中回调。
- `AppAdaptiveTwoColumn` 承担内容区自适应分栏：可用宽度达到 840dp（`AppContentTwoColumnBreakpoint`）时把两组内容并排展示，窄屏按原顺序上下堆叠；断点判断同样抽成纯函数 `appContentLayoutMode()`，由 `AppContentLayoutModeTest` 覆盖 360dp / 839dp / 840dp / 1280dp 四个宽度。
- 设备信息页是首个接入页面：左栏为仪表配置与 BLE 协议，右栏为应用信息与协议命令；页面只把分组拆成两段交给组件，不接触断点、窗口尺寸或任何一方主题类型。
- 宽屏布局只替换导航形态，内容区仍复用 `AppScaffold`，因此顶栏折叠、嵌套滚动与下拉刷新在两种布局下行为一致；`floatingNavigation` 只影响窄屏的 Miuix 分支。

### 返回手势

- `AppBackHandler` 封装系统返回语义，业务页面只描述“返回时该做什么”，不直接依赖平台 API。
- Android 在清单里声明 `android:enableOnBackInvokedCallback="true"` 后走 `OnBackInvokedCallback`，Android 13+ 由系统播放预测性返回动画；Desktop / Web / iOS 由 Compose 宿主提供返回事件调度器，没有系统返回手势时不会触发。
- `enabled = false` 时不注册回调，事件继续交给系统处理（例如在根页面退出应用）。

### 持久化

- `PreferenceStore` 具备 `read/observe/write/delete/clear`。
- `AppPreferences` 将 `AppAppearance` 拆成类型化 key。
- Android、JVM、JS、Wasm、iOS 都有对应 store。
- `AppPreferencesTest` 覆盖默认值、读写回环、未知枚举、reset。
- Composable 通过 `rememberAppAppearance(AppPreferences)` 异步写入。

## 4. 重要缺口

### P0：设置 schema 迁移（已处理）

`AppPreferences.kt` 现在把当前版本定义为 5（`AppPreferenceKeys.SCHEMA_VERSION`），迁移策略明确：

- 每次版本升级都属于纯增量迁移：新增的键由 `PreferenceKey` 默认值补齐，不重置用户已有配置。
- v1 -> v2 补 `miuix_monet`（默认 `true`，升级后仍是原有 Monet 配色）。
- v2 -> v3 补 `floating_navigation_bar`（默认 `false`，升级后仍是原有常规底栏）。
- v3 -> v4 补 `color_spec`（默认 `Auto`，升级后仍按调色板推导颜色标准）。
- v4 -> v5 补 `ui_scale_percent`（默认 `100`，升级后界面尺寸保持不变）。
- 版本 0/缺失值按历史配置对待，保留已有键值（历史 key 兼容），只有缺失的新键才回退默认值，不重置用户配置。
- 高于当前版本的数据禁止被 `write` 覆盖，返回 `PreferenceWriteResult.Failure`。
- 已补充默认值、回环、旧 schema、未来 schema 的测试。

后续新增设置项时：递增 `SCHEMA_VERSION`、在 `AppPreferenceKeys.all` 末尾追加 key、同步 `read`/`observe`/`write`/`decode`，并补测试。

### P1：缺少数值型/选择型 Preference 组件（已完成，`AppTextInputPreference` 本轮决定不做）

MeowUI 文档中的 Checkbox、Slider Preference、Popup/Dropdown、Text input、Action Preference 逐步补齐：

- `AppCheckboxPreference`（已完成）。
- `AppPopupPreference`（已完成）：Miuix 使用 `OverlaySpinnerPreference`（内部为 ListPopup），Material 3 使用 `DropdownMenu`。
- `AppActionPreference`（已完成）：Miuix 使用 `ArrowPreference`，Material 3 使用点击行加箭头提示。
- `AppSliderPreference`（已完成）：Material 3 使用原生 `Slider`，Miuix 使用原生 `SliderPreference`；设置页“界面缩放”是第一个真实数值型设置项，拖动中即时预览、松手吸附档位再落库。
- `AppTextInputPreference`（本轮决定不做）：Miuix 偏好组件里没有对应的文本输入 Preference，取色器内的 hex 输入已覆盖现有需求；等出现真实的文本型设置项再补。

这些组件保持两种风格的整行点击、启用状态和提交时机一致，并沿用 `AppDesign` token。

### P1：缺少反馈组件

反馈组件逐步补齐：

- `AppTip`（已完成）：信息/成功/警告/错误四种类型，Miuix 与 Material 3 都用原生 Card 承载，配色取自 `AppDesign` 语义 token；连接页与投屏页已接入错误/警告提示。
- `AppLoadingDialog`（已完成）：Miuix 使用原生 `WindowDialog`，Material 3 使用原生 `AlertDialog`；投屏协商阶段已接入，可手动关闭。
- `AppSnackbar`（已完成）：`AppSnackbarHostState` 把两套原生 host/state 收敛成一份业务状态，时长与结果由 `AppSnackbarDuration` / `AppSnackbarResult` 表达；已接入 BLE 断开、Wi-Fi 断开、停止投屏三个真实场景，映射关系由 `AppSnackbarTest` 覆盖。
- `AppHaptics`（本轮决定不做）：Miuix 原生控件自带部分触觉反馈，Material 与自绘控件尚未统一；等有真实业务场景再补，不提前抽象。

`AppColors` 同步补充了 `errorContainer`/`onErrorContainer`，由 Material 3 与 Miuix 原生错误容器色映射，供错误类提示复用。

### P1：Dialog 只覆盖基础确认场景

- `AppAlertDialog`（已完成）：标题 + 说明 + 确认/取消，支持单按钮退化，已用于 BLE 断开确认。
- 单选列表弹窗（本轮决定不做）：Miuix 侧可基于 `WindowDialog` + `ListPopup` 组合，Material 3 侧用 `AlertDialog` + 单选行；目前选择型设置已由 `AppPopupPreference` 覆盖，暂不重复实现。
- 文本输入弹窗（本轮决定不做）：现有唯一文本输入场景（取色器 hex）在 `AppColorPickerDialog` 内闭环，暂不需要独立组件。

### P1：Scaffold 只有基础能力

`AppScaffold` 已补上共享 nested scroll 与顶栏折叠/展开（`AppScrollBehavior`），`AppAdaptiveScaffold` 已补上宽屏侧边导航，`AppAdaptiveTwoColumn` 已补上宽屏内容双栏（见第 3 节“导航与面板”），仍未实现 MeowUI 的：

- top bar / bottom bar Blur backdrop。
- overlay host。

这两项需要跨平台模糊与浮层宿主能力，本轮按计划暂缓，等确认 Miuix 与 Compose 版本可复用后再评估。

这不影响当前三个入口的基础布局，但会限制后续复杂页面。

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
- 界面缩放（80% ~ 130%，步进 10%）。
- 系统返回手势（`AppBackHandler`，Android 13+ 播放预测性返回动画）。

缺少：
- Blur 开关（底层缺少跨平台模糊能力，暂不提供开关）。
- 自定义 preference 插槽。

这些不是当前业务必需，但会影响外观设置的完整性。

### P2：单选/输入弹窗、菜单等通用能力尚未实现

搜索框已随连接页设备列表落地，见第 3 节“列表与搜索”；单选列表弹窗、文本输入弹窗与下拉菜单属于通用 UI 能力，当前业务页面没有强需求，本轮决定不做。MeowUI 文档已经验证它们是统一 UI 体系的一部分，等出现真实使用场景时再按“一份业务 API、两套原生实现”补齐。Bottom Sheet 已完成，见第 3 节“导航与面板”。

### 本轮明确暂缓：等有真实业务场景再补

以下两类能力本轮**决定不实现**，避免为凑齐 MeowUI 文档提前抽象没有业务场景的组件：

1. **`AppHaptics`（统一触觉反馈）**：Miuix 原生控件已自带部分反馈，当前没有需要主动触发振动的业务动作，等出现真实场景（例如长按确认、拖动吸附）再补。
2. **`AppTextInputPreference`、单选列表弹窗、文本输入弹窗、下拉菜单**：现有文本输入（取色器 hex）已在 `AppColorPickerDialog` 内闭环，选择型设置已由 `AppPopupPreference` 覆盖，当前业务页面没有强需求。

后续如果出现真实使用场景，再按“一份业务 API、两套原生实现”的规则补齐，并同步更新第 2 节的状态表。

## 5. 不建议直接照搬的部分

- Xposed/libxposed 设置服务。
- Android-only 的系统栏控制和 `Build.VERSION` 判断。
- Material 3 Expressive 的完整 `MaterialExpressiveTheme` 体验。
- 所有 MeowUI 页面组件一次性引入。

当前项目是通用 KMP 投屏工具，不需要为了凑齐 MeowUI 文档而引入没有业务场景的组件。

## 6. 推荐补齐顺序

1. **设置 schema 迁移和未来版本保护**（已完成）。
2. **AppCheckboxPreference / AppPopupPreference / AppActionPreference / AppSliderPreference（已完成）**；`AppTextInputPreference` 本轮决定不做，等出现真实的文本型设置项再补。
3. **AppTip、AppLoadingIndicator/AppLoadingDialog、AppSnackbar 统一错误/成功反馈**（已完成：`AppTip`、`AppLoadingDialog`、`AppSnackbar` 及其 BLE 断开 / Wi-Fi 断开 / 停止投屏三个场景；`AppHaptics` 本轮决定不做，等有真实业务场景再补）。
4. **AppTopBar actions/menu 和基础 Dialog**（已完成：`AppTopBar` 支持 actions + `AppIconButton` + `AppAlertDialog`；文字动作、下拉菜单、单选/输入弹窗本轮决定不做，等有业务场景再补）。
5. **分段选择（AppSegmentedControl）、floating navigation、Bottom Sheet**（已完成：`AppSegmentedControl`、`AppBottomNavigation(floating)` 及其设置开关、`AppBottomSheet` 及投屏调试日志场景）。
6. **Scaffold 滚动顶栏（已完成共享嵌套滚动 + 折叠顶栏）、Pull-to-Refresh（已完成 `AppPullToRefresh` 与连接页接入）、自适应布局（已完成 `AppAdaptiveScaffold` 宽屏侧边导航与 `AppAdaptiveTwoColumn` 宽屏内容双栏）、Blur**（其余项等有业务场景再补）。
7. **通用列表与自适应布局（搜索框、下拉刷新、宽屏侧栏均已完成）**：`AppSearchBar` 已接入连接页的 WiFi / P2P / BLE 设备列表，统一过滤规则由 `matchesSearchQuery` 承担并配有单元测试；`AppPullToRefresh` 已补齐下拉刷新；`AppAdaptiveScaffold` 按 600dp 断点接入宽屏侧边导航，断点行为由 `AppNavigationLayoutModeTest` 覆盖；内容区再按 840dp 断点由 `AppAdaptiveTwoColumn` 拆成双栏，设备信息页已接入，断点行为由 `AppContentLayoutModeTest` 覆盖。

每一批都应遵守 `AGENTS.md`：一份业务 API、两套原生实现，并在完成一个独立功能后提交。
