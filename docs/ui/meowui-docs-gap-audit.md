# MeowUI Docs 功能查漏补缺审计

本审计对照 MeowUI `docs/components.md`、`docs/design-guidelines.md`、`docs/getting-started.md` 与当前 KMP 实现。只记录已经从源码或构建验证确认的差异，不把未运行的视觉状态写成已验证。

## 1. 当前验证结果

最近一次共享平台与 Android/Desktop 验证通过（Miuix Monet 开关与 CheckboxPreference 改造）：

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
| Checkbox preference | 已完成 | `AppCheckboxPreference` | Material Checkbox + Miuix CheckboxPreference；界面风格切换已改用 |
| Slider preference | 部分完成 | `AppSlider` | 有通用 Slider；没有标题/摘要/值/提交语义的 Preference 行 |
| Popup / Dropdown | 缺失 | 无对应 `App*` 组件 | 设置里目前使用 chips/循环切换 |
| Text input preference | 缺失 | 无 `AppTextInputPreference` | 只有取色器 hex 输入 |
| Action preference | 部分完成 | `AppListRow(onClick)` | 可点击行已有；没有 value + navigation 语义 |
| Dialog | 缺失 | 只有取色器 Dialog | Alert、Single choice、Text input、Loading 未实现 |
| TopBar actions/menu | 缺失 | `AppTopBar(title)` | 没有 icon/text/menu actions |
| 搜索框 | 缺失 | 无 `AppSearchBar` | 当前项目暂无搜索场景 |
| Scaffold 滚动顶栏 | 部分完成 | `AppScaffold` | 基础 Scaffold 已有；没有共享 nested scroll、折叠顶栏、Blur backdrop |
| Tab | 缺失 | 无 `AppTabRow` | 设置里的模式选择使用 `AppFilterChip` |
| 普通/悬浮底栏 | 部分完成 | `AppBottomNavigation` | 只有普通底栏；没有 floating navigation |
| 导航分页 | 缺失 | 无 pager | 当前使用四个固定 tab |
| Bottom Sheet | 缺失 | 无组件 | 尚无对应业务场景 |
| Snackbar | 缺失 | 无组件 | 操作反馈目前依赖状态文本 |
| Tip | 缺失 | 无 `AppTip` | 错误/成功状态没有轻量提示组件 |
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

### P1：缺少数值型/选择型 Preference 组件

MeowUI 文档中的 Checkbox、Slider Preference、Popup/Dropdown、Text input、Action Preference 尚未形成完整语义组件。

建议优先补：

- `AppCheckboxPreference`
- `AppSliderPreference`
- `AppPopupPreference`
- `AppTextInputPreference`
- `AppActionPreference`

这些组件应复用 `AppListRow`，并保持两种风格的提交时机一致。

### P1：缺少反馈组件

当前页面主要依赖状态文字和按钮，缺少：

- `AppTip`
- `AppSnackbar`
- `AppLoadingDialog`
- `AppHaptics`

对于连接、扫描、投屏、保存设置这类操作，建议先补 `AppTip` 和统一 loading/错误状态。

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
2. **AppCheckboxPreference（已完成）、AppSliderPreference、AppPopupPreference、AppTextInputPreference、AppActionPreference**。
3. **AppTip、AppLoadingIndicator/AppLoadingDialog、统一错误/成功反馈**。
4. **AppTopBar actions/menu 和基础 Dialog**。
5. **AppTabRow、floating navigation、Bottom Sheet**。
6. **Scaffold 滚动顶栏、Pull-to-Refresh、Blur、自适应布局**。

每一批都应遵守 `AGENTS.md`：一份业务 API、两套原生实现，并在完成一个独立功能后提交。
