# MeowUI Docs 功能查漏补缺审计

本审计对照 MeowUI `docs/components.md`、`docs/design-guidelines.md`、`docs/getting-started.md` 与当前 KMP 实现。只记录已经从源码或构建验证确认的差异，不把未运行的视觉状态写成已验证。

## 1. 当前验证结果

最近一次共享平台与 Android/Desktop 验证通过（Miuix Monet 开关、CheckboxPreference 与 ListPopup 选择型 Preference 改造，以及 AppTip/AppLoadingDialog 反馈组件）：

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
| Color spec | 部分完成 | `toMiuixColorSpec` | 当前只由 palette 推导；没有独立设置项 |
| AMOLED | 部分完成 | `withAmoledBackground` | 只处理 Material 底层 surface；Miuix 不处理 |
| 主题动画 | 已完成 | `AppColorTransition` | Material/Miuix 颜色列表插值 |
| 设置持久化 | 已完成 | `AppPreferences` + 平台 store | Android/JVM/JS/Wasm/iOS 均有后端 |
| 设置类型和默认值 | 已完成 | `PreferenceKey`/`PreferenceType` | 类型安全 key 和默认值 |
| 设置 Flow/Compose 绑定 | 已完成 | `rememberAppAppearance` | 支持读取、观察和异步写入 |
| 预设色 | 已完成 | `AppThemePresets` | 稳定 ID、亮暗共用 seed |
| 完整取色器 | 部分完成 | `AppColorPickerDialog` | 滑条/网格/hex/预览/确认取消已有；没有实时全局主题预览 |
| 设置分组 | 部分完成 | `AppSection`、`AppListRow` | 视觉分组已有；没有 MeowUI 的收集式稳定 key/分段圆角 |
| Switch preference | 已完成 | `AppSwitchPreference` | Material/Miuix 分支 |
| Checkbox preference | 已完成 | `AppCheckboxPreference` | Material Checkbox + Miuix CheckboxPreference；作为通用语义组件保留 |
| Slider preference | 部分完成 | `AppSlider` | 有通用 Slider；没有标题/摘要/值/提交语义的 Preference 行 |
| Popup / Dropdown | 已完成 | `AppPopupPreference` | Miuix `OverlaySpinnerPreference`（ListPopup）+ Material `DropdownMenu`；界面风格与调色板已改用 |
| Text input preference | 缺失 | 无 `AppTextInputPreference` | 只有取色器 hex 输入 |
| Action preference | 已完成 | `AppActionPreference` | Miuix `ArrowPreference` + Material 点击行加箭头；颜色动作行已改用 |
| Dialog | 部分完成 | `AppColorPickerDialog`、`AppLoadingDialog` | Alert、Single choice、Text input 仍未实现 |
| TopBar actions/menu | 缺失 | `AppTopBar(title)` | 没有 icon/text/menu actions |
| 搜索框 | 缺失 | 无 `AppSearchBar` | 当前项目暂无搜索场景 |
| Scaffold 滚动顶栏 | 部分完成 | `AppScaffold` | 基础 Scaffold 已有；没有共享 nested scroll、折叠顶栏、Blur backdrop |
| Tab | 缺失 | 无 `AppTabRow` | 设置里的模式选择使用 `AppFilterChip` |
| 普通/悬浮底栏 | 部分完成 | `AppBottomNavigation` | 只有普通底栏；没有 floating navigation |
| 导航分页 | 缺失 | 无 pager | 当前使用四个固定 tab |
| Bottom Sheet | 缺失 | 无组件 | 尚无对应业务场景 |
| Snackbar | 缺失 | 无组件 | 操作反馈目前依赖 `AppTip` 与状态文本 |
| Tip | 已完成 | `AppTip`、`AppTipKind` | 信息/成功/警告/错误四种类型，两风格共用语义 token |
| Pull to refresh | 缺失 | 无组件 | 当前页面无刷新需求 |
| Blur | 缺失 | 无 `MeowBlur` 对应能力 | 当前项目未启用表面模糊 |
| 自适应布局 | 缺失 | 无双栏/宽屏布局组件 | 当前仍是固定 tab 页面 |
| 触觉反馈 | 缺失 | 无统一 `AppHaptics` | Miuix 原生控件自带部分反馈，Material/自绘控件未补齐 |
| 可访问性 | 部分完成 | 色块和控件有部分 semantics | 仍需系统检查图标、选择、禁用、重复点击 |
| schema 迁移 | 已完成 | `SCHEMA_VERSION` + 增量迁移 + 未来版本保护 | v1 -> v2 纯增量迁移，未来版本不被 `write` 覆盖 |

## 3. 已经正常工作的部分

### 主题和颜色

- `AppAppearance` 已统一管理 UI 风格、明暗模式、颜色来源、seed、preset、palette、AMOLED、动画开关、Miuix Monet 开关。
- Miuix 可整体关闭 Monet（`miuixMonetEnabled`）；关闭后不再由种子色生成配色，回退到 Miuix 原生 HyperOS 配色。
- `materialKolor 5.0.1` 已声明为 KMP 依赖，Gradle metadata 包含 Android、JVM、JS、Wasm、iOS target。
- Material 使用 `dynamicColorScheme` 生成完整 `ColorScheme`。
- Miuix 使用 `ThemeController` 和 palette style 生成完整 `Colors`。
- AMOLED 会压黑 `background/surface/surfaceDim/surfaceContainer*`，保留卡片高层 surface。
- `AppColorTransition` 使用单进度插值，开关关闭时直接使用目标颜色。

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

### 设置交互

- `AppPopupPreference` 统一承担“从列表中选择一项”的语义：Miuix 走原生 SpinnerPreference（ListPopup），Material 3 走 DropdownMenu，重复选中当前项不会触发写入。
- 界面风格与调色板已从开关/循环点击改为列表弹窗选择；颜色入口行使用 `AppActionPreference`，Miuix 下带原生箭头与按压反馈。
- `AppCheckboxPreference` 保留为通用组件，供后续布尔型设置项复用。

### 反馈组件

- `AppTip` 覆盖信息/成功/警告/错误四种类型，两种风格都使用原生 Card 与 `AppDesign` 语义色，带 live region 和关闭按钮无障碍语义。
- `AppLoadingDialog` 在 Miuix 使用原生 `WindowDialog`，Material 3 使用原生 `AlertDialog`，用于连接、投屏这类长时间操作的进度反馈。
- 连接页（WiFi/BLE 失败、P2P 部分设备失败）与投屏页（投屏失败、协商中）已接入上述组件。

### 持久化

- `PreferenceStore` 具备 `read/observe/write/delete/clear`。
- `AppPreferences` 将 `AppAppearance` 拆成类型化 key。
- Android、JVM、JS、Wasm、iOS 都有对应 store。
- `AppPreferencesTest` 覆盖默认值、读写回环、未知枚举、reset。
- Composable 通过 `rememberAppAppearance(AppPreferences)` 异步写入。

## 4. 重要缺口

### P0：设置 schema 迁移（已处理）

`AppPreferences.kt` 现在把当前版本定义为 2（`AppPreferenceKeys.SCHEMA_VERSION`），迁移策略明确：

- v1 -> v2 属于纯增量迁移：新增的 `miuix_monet` 由 `PreferenceKey` 默认值补齐为 `true`。
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

### P1：Scaffold 只有基础能力

当前 `AppScaffold` 没有 MeowUI 的：

- 共享 nested scroll。
- 顶栏折叠/展开。
- top bar / bottom bar Blur backdrop。
- pull-to-refresh 协作。
- overlay host。

这不影响当前四个 tab 的基础布局，但会限制后续复杂页面。

### P2：设置 UI 缺少 MeowUI Appearance Page 的完整结构

当前已经有：
- 明暗模式。
- 颜色来源。
- palette。
- AMOLED。
- 动画。
- 自定义颜色。

缺少：
- 实时主题预览头图。
- 颜色标准独立选择。
- Miuix Monet 开关（已实现）。
- 界面缩放。
- 悬浮底栏开关。
- Blur 开关。
- Predictive Back 开关。
- 自定义 preference 插槽。

这些不是当前业务必需，但会影响外观设置的完整性。

### P2：弹窗、菜单、搜索、Bottom Sheet 尚未实现

这些属于通用 UI 能力，当前业务页面没有强需求，但 MeowUI 文档已经验证它们是统一 UI 体系的一部分。

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
4. **AppTopBar actions/menu 和基础 Dialog**。
5. **AppTabRow、floating navigation、Bottom Sheet**。
6. **Scaffold 滚动顶栏、Pull-to-Refresh、Blur、自适应布局**。

每一批都应遵守 `AGENTS.md`：一份业务 API、两套原生实现，并在完成一个独立功能后提交。
