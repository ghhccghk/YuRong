# 页面级改造方案

## 1. 改造策略

不批量替换所有页面。先选择连接页作为代表性页面，因为它同时包含：

- 页面级标题。
- 模式切换（AP 热点 / P2P）。
- 表单输入。
- 主操作按钮。
- 设备列表。
- 状态、空状态和调试信息。

它能检验新的页面容器、分组、表单、状态和反馈系统。完成第一批后，再按同一套组件迁移投屏、设备、设置、权限和裁剪区域。

## 2. 第一批：ConnectionScreen

### 2.1 页面结构

目标结构：

```text
ConnectionPage
  PageHeader
    title: 设备连接
    subtitle: 当前连接模式/连接状态
  ModeSelector
  AppPreferenceSection("WiFi")
    WiFiStatusRow
    SSIDTextField
    PasswordTextField
    ConnectionActions
    NearbyNetworks
  AppPreferenceSection("P2P")
    P2PStatusRow
    ScanAction
    P2PDeviceList
  AppPreferenceSection("BLE")
    BLEStatusRow
    ScanAction
    BLEDeviceList
  DiagnosticSection
    DebugLog
```

保留现有业务逻辑和字段，不把连接页改成设置页。

### 2.2 组件映射

| 当前实现 | 新组件 | 说明 |
| --- | --- | --- |
| `LazyColumn.padding(16.dp)` | `AppPreferenceScreen` | 统一 scroll、padding、section spacing |
| `ScreenTitle` | `AppPageHeader` | Miuix TopAppBar / Material 页面标题只出现一次 |
| `AppFilterChip` | `AppModeSelector` | 两种风格使用各自原生 tab/toggle 语义 |
| `AppCard + Column` | `AppPreferenceSection` | 统一分组和条目形状 |
| `AppTextField` | `AppTextFieldPreference` | 增加 summary/error/disabled reason |
| `AppButton` 行 | `AppActionPreference` / `AppButton` | 主操作、次级操作和危险操作分层 |
| `StatusChip` | `AppStatusBadge` | 连接状态使用语义状态色和图标 |
| `AppCard` 设备行 | `AppDeviceRow` | 一行一个点击语义，避免嵌套卡片 |
| 调试日志卡片 | `AppDiagnosticSection` | 诊断信息与操作区分层 |

### 2.3 视觉和交互验收

- 页面标题只出现一次。
- AP/P2P 选择在两种风格下均有 selected 语义。
- WiFi 表单字段、按钮和附近网络有清晰主次。
- P2P/BLE 设备列表不再嵌套 Card。
- 空列表显示 `AppEmptyState`，扫描中显示 `AppLoadingIndicator`。
- 连接失败显示 `AppTip(Error)`，成功显示 `AppTip(Success)`。
- 浅色/深色下状态色、输入框、按钮和分组均来自 AppDesign。
- 主题切换保持当前 tab、输入值、扫描状态。

## 3. 第二批：MirrorScreen

保留投屏协议和状态机，不改 `AppViewModel` 的领域逻辑。

页面分层：

1. 仪表连接：IP、TCP、UDP、P2P GroupOwner、WiFi IP。
2. 投屏状态：状态、帧数、字节数、帧率。
3. 投屏源：摄像头、屏幕、视频、导航。
4. 条件配置：视频/导航 URI 和选择器。
5. 投屏区域：`MirrorPreviewPanel`。
6. 主操作：开始/停止投屏。
7. 诊断：日志。

重点：

- SourceButton 改为 `AppSelectionCard`，状态和 disabled reason 由统一组件表达。
- “开始投屏”是页面主操作，不能和日志卡同权重。
- 投屏区域预览是交互画布，不是普通 Card。
- 启动中、运行中、失败、停止均有明确状态。

## 4. 第三批：DeviceScreen / PermissionPanel

### DeviceScreen

把四个等权 Card 改为：

- “仪表配置”主要信息区。
- “BLE 协议”参数分组。
- “应用信息”轻量分组。
- “协议命令”可折叠/次要分组。

使用 `AppInfoRow`，标题、值和说明分别使用 itemTitle/value/summary token。

### PermissionPanel

将权限列表改成 `AppPreferenceSection`：

- 每个权限是一个 row。
- 已授权使用状态值，不使用不可点击 AssistChip。
- 未授权行有明确 action。
- 顶部“全部授权”只在有缺失权限时成为主动作。

## 5. SettingsScreen

设置页优先使用 `AppPreferenceScreen` 和 `AppPreferenceSection`：

- “外观” section。
- `AppSwitchPreference("Miuix 界面")`。
- `AppValuePreference("当前样式")`。
- 后续扩展主题模式、动态取色等。

当前 Material 3 设置页的 padding/标题修复保留，但后续不再在 `SettingsScreen` 中手工分支 `SmallTitle`、`pagePadding` 和卡片间距。

## 6. 共用组件和主题系统调整顺序

### 第 0 批（基础设施，先完成再改页面）

1. `AppAppearance` / 统一 darkTheme。
2. `AppDimensions`、`AppMotion`、status colors。
3. `AppPage` / `AppPageHeader`。
4. `AppPreferenceScreen`、`AppPreferenceSection`、`AppPreferenceRow`。
5. `AppTip`、`AppEmptyState`、`AppStatusBadge`。
6. 保持 `AppCard`、`AppButton`、`AppTextField`、`AppSlider` 的兼容 API。

### 第 1 批（Connection）

完成 ConnectionScreen 后提交一次，只改 UI 组件和页面布局，不改连接业务逻辑。

### 第 2 批（Mirror + Preview）

完成 MirrorScreen 和 MirrorPreviewPanel 后提交一次。

### 第 3 批（Device + Permission + Settings）

统一信息分组和设置语义后提交一次。

## 7. 验收矩阵

每个批次至少检查：

- Miuix / Material 3。
- System / Light / Dark。
- 首次显示、空数据、加载中、成功、失败、禁用。
- 窄屏、宽屏、长文本。
- 页面切换、主题切换、输入保持。
- Android 真机截图；iOS/Desktop/Web 至少完成编译。
- 不修改蓝牙、Wi-Fi、投屏协议、帧源和发送逻辑。

第一批完成前不迁移其他页面。
