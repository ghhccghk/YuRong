# AI 协作规范

本文件适用于所有参与本仓库的 AI 编码工具。修改代码前先阅读本文件，并遵守其中的界面架构、提交和验证要求。

## 项目定位

这是 Kotlin Multiplatform Compose 项目，目标平台包括 Android、iOS、Desktop/JVM、JS 和 Wasm。

- 共享业务和 UI 代码位于 `shared/src/commonMain`。
- 平台实现位于 `androidMain`、`iosMain`、`jvmMain`、`jsMain`、`wasmJsMain`。
- 不得为了 Android 的便利破坏其他目标平台编译。
- UI 重构不得顺手修改蓝牙、Wi-Fi、投屏协议、帧发送等领域逻辑。
- 原 App 行为以 `F:\android app code\YadeaHook\temp_smali` 的 smali 为准；命令 ID、参数范围、状态位必须有 smali 或抓包依据，不得凭字段名猜测后直接写入车辆。

## 双主题架构

应用保留两套原生界面：

- `UiThemeStyle.Miuix`：HyperOS/Miuix 风格。
- `UiThemeStyle.Material3`：保留原有 Material Design 3 界面。

必须坚持以下规则：

1. **一份业务页面，两套原生实现。**
   业务层只描述状态、语义和回调；Material 3 与 Miuix 的视觉分支留在 UI 组件内部。
2. **不把 Miuix 做成 Material 换色。**
   Miuix 模式优先使用 `MiuixTheme`、`MiuixCard`、`MiuixButton`、`MiuixTextField`、`MiuixSlider`、`MiuixSwitchPreference`、`MiuixScaffold`、`MiuixTopAppBar` 和 `MiuixNavigationBar`。
3. **不把 Material 3 改造成仿 MIUI。**
   Material 3 模式保留原有 M3 控件、排版和交互。
4. **业务页面不得直接混用两套主题类型。**
   页面通过 `AppDesign`、`AppText`、`AppCard`、`AppButton`、`AppTextField`、`AppSlider`、`AppSwitchPreference`、`AppScaffold` 等语义 API 访问界面。
5. **风格切换必须即时生效。**
   入口固定在“设置”页，默认使用 Miuix，并允许切换回 Material 3。

## 代码入口

| 文件 | 职责 |
| --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/theme/UiTheme.kt` | `UiThemeStyle`、双主题、`AppDesign` 颜色/排版/形状 token、`AppStyleContent` |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/components/UiComponents.kt` | 双风格语义控件 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/components/UiShell.kt` | Scaffold、TopAppBar、底部导航 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/SettingsScreen.kt` | 界面风格设置 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt` | 应用导航与页面入口 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt` | 车辆设置页面、自定义音效二级面板与试听入口 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt` | 云端车辆设置读取/写入、`bikeConfigVos` 映射 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt` | confId/条目到车辆命令的映射 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingFrameEncoder.kt` | 已确认的 BLE 设置帧编码 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CustomSoundGateway.kt` | 自定义音效列表、应用、开关、资源管理 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/SoundPreviewPlayer.kt` | 自定义音效试听的跨平台接口 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/VehicleCloudTheme.kt` | 云端主题读取、`loveCarImgUrl` 映射、按 VIN 缓存与定制款判定 |

新增可复用 Composable 时：

- 公共参数优先使用业务语义，例如 `selected`、`checked`、`onClick`、`onValueChange`。
- 不向业务页面暴露 Material 或 Miuix 私有状态和颜色类型。
- 两种风格必须保持相同的回调时机、启用状态和提交行为。
- `modifier` 应用于最外层可布局节点。
- 图标必须提供 `contentDescription`；选择、开关和导航组件保持正确的可访问性角色。
- 避免魔法值，优先使用 `AppDesign` token。

## 组件替换约束

- 修改 UI 时先复用现有 `App*` 组件；缺少能力时扩展组件，不在页面内复制实现。
- 禁止对源码做宽泛字符串替换。曾因替换 `Button(` 产生 `AppOutlinedAppButton`、`SourceAppButton` 等错误名称。
- 使用明确 import 和 `as` 别名区分 Material 3 与 Miuix 同名组件。
- Material 3 和 Miuix 的 `LocalContentColor` 是不同类型；需要文本继承控件颜色时必须同时处理当前风格对应的 CompositionLocal。
- 页面标题在 Miuix 模式由原生 TopAppBar 承担；Material 3 模式保留页面内标题，避免重复显示。

## 车辆设置与云端配置

车辆设置遵循“云端决定有哪些项、本地只负责展示与写入”的原则。

- `vehiclebase/bike-setting/selectBikeSettingByVin` 返回的 `bikeConfigVos` 是设置项、分组、排序和权限的唯一来源。不得在页面里硬编码某车型应该出现哪些设置。
- `isAuth=false` 表示只读；云端缺少 `isAuth` 时按未禁止处理。`izLab=0`、`isUserLabFun` 和实验室入口归入实验室分组。
- 设置写入优先走 `updateBikeSetting`；只有 smali 已确认字段和帧格式时才回退到 BLE。不得发送猜测出来的命令。
- `VehicleSettingCommands` 与 `VehicleSettingFrameEncoder` 必须同步修改：新增命令时同时确认 commandType、commandId、参数范围、位域位置和帧长。
- 一个 confId 可能拆成多个语义控件（例如充电功率、感应距离、推行档位、亮度、雷达），追加项要有稳定的条目 id，例如 `cloud:64:adaptive`。
- 车辆状态能回读的项必须从 `TtpInfo.extended` 回填；没有回读字段的项不得伪造“已确认”状态。
- 写操作采用乐观更新；失败或车辆未确认时必须回滚并提示。安全相关项（TCS/ABS 等）关闭前必须二次确认。
- 已确认的特殊约束：安全限速 `0` 表示不限速，非零最低 `15 km/h`；充电功率范围 `200..1000W`；微信互联当前隐藏。

## 自定义音效

- confId `41` 与 `88` 都是“自定义音效”入口，必须共用同一套管理界面。
- 官方页面结构是两层：第一层为开关、每周设置次数、音效分类；点击分类后使用第二个 `AppBottomSheet` 展示该分类音效列表。不要把所有资源平铺在第一层。
- 分类来自 `vehiclebase/bike-model/selectConfigListByVin` 的 `zdyyx001.sonConfigs`；当前选中项来自 `resourceBike/queryResourceBikeList`；每周次数来自 `resourcesBike/queryCountSetResourceBike`（`count` 是剩余次数，`total` 是上限）。
- 应用音效使用 `resourcesBike/setResourceBike`，并按 `sonConfId` 区分分类；结果用 `resourcesBike/queryResultSetResourceBike` 轮询。
- 用户资源列表、重命名、删除、添加分别使用 `resource/queryResourceList`、`resource/renameUserResource`、`resource/deleteUserResource`、`resource/addUserResource`。
- 试听通过 `SoundPreviewPlayer`；Android 使用 `MediaPlayer`，其他平台当前明确为不可用，UI 应隐藏试听按钮，不能假装播放成功。
- 本地音频上传链路已确认为 `oss/generateSignUrl/v2`（`fileType=12`）→ `PUT signedUrl` → `resource/addUserResource`。当前 `HttpTransport` 只支持字符串请求体，未完成二进制上传前不得移除“添加已有音频 URL”的可用入口。

## 云端主题

- 用户当前主题来自 `user/theme/queryUserApplyThemeAndVariedTheme`，使用 POST、网关签名，业务字段为 `cityCode`、`vin`、`modelNo`、`seriesCode`。
- 车辆页云端背景只取 `userApplyThemeVo.themeImageVos` 中 `range1=1 && range2=2` 的 `pictureUrl`，对应 `ThemeConfigCard.loveCarImgUrl`。
- `range1` 只接受 `2/3/1/5/4`；`range2=0` 是未选中图，`range2=1` 是选中图，不能混用为车辆背景。
- 车辆本体图按原 `VehicleInfoBean.getVehicleImage()` 使用 `skinPicUrl`，为空时回退 `imgUrl`。不得再把 `skinPicUrl` 当车辆页全屏背景。
- 是否定制款以当前 VIN 的动态主题配置是否包含 `bannerVehicleBgImg` 为准；不得用 `loveCarImgUrl`、`dLinkTag` 或车型号单独推断。
- `bikeGameThemeVOList.colorType` 映射原动态主题：`1=金色/LKN`、`2=黄色/BB`、`3=粉色/AL`，背景取对应资产中的 `bannerVehicleBgImg.icon.url`，并按亮/暗主题选择变体。
- 车辆页背景优先级固定为动态主题 `bannerVehicleBgImg` → 云端 `loveCarImgUrl` → 纯色兜底；不要用装饰性渐变替代缺失背景。
- 云端主题按 VIN 缓存；切换车辆时必须先切到对应 VIN 的缓存，再异步刷新，禁止短暂沿用上一辆车背景。

## BLE 与平台适配

- 平台不支持 BLE 时必须使用明确的 `UnsupportedBleAdapter` / `BleCapabilities` / `BleErrors` 表达，不得实现假装成功的空适配器。
- Android、JVM、iOS、JS、Wasm 的适配器必须保持同一接口语义；新增能力先补 `BleCapabilities`，再接入平台实现。
- 车辆帧编码、OTA、Wi-Fi 投屏和自定义音效传输不得因 UI 重构被顺手修改。

## 主题配置与持久化

主题和设置必须保持“配置模型、存储抽象、平台后端、Compose 绑定”分离。

- `AppAppearance` 是唯一的主题配置模型，至少保持 `uiStyle`、`themeMode`、`colorSource`、`seedColorArgb`、`presetId`、`paletteStyle`、`amoledDarkEnabled`、`themeAnimationsEnabled` 的语义稳定。
- `AppPreferenceKeys` 是设置 key、类型和默认值的唯一来源。已发布的 key 不得随意改名或改变类型。
- `PreferenceStore` 只提供 `read`、`observe`、`write`、`delete`、`clear`；业务 UI 不直接访问 SharedPreferences、NSUserDefaults、localStorage 或 Java Preferences。
- 平台持久化后端放在各平台 sourceSet：
  - Android：`SharedPreferencesPreferenceStore`
  - Desktop/JVM：`JavaPreferencesStore`
  - JS/Wasm：`LocalStoragePreferenceStore`
  - iOS：`NSUserDefaultsPreferenceStore`
- `AppPreferences` 负责版本、字段编码、未知值回退和 Flow 组合。读取损坏值或未知枚举时必须返回安全默认值，不得让应用崩溃。
- Composable 中只通过 `rememberAppAppearance(AppPreferences)` 读取和更新配置；写入应异步执行并保持当前 UI 状态可用。
- schema version、默认值和迁移策略必须有测试。未知版本不能覆盖有效配置。
- 需要新主题能力时，先扩展 `AppAppearance`/`AppPreferenceKeys`，再接入 `AppTheme`，禁止在页面内临时保存颜色或模式。
- 平台后端的 `clear` 只清理本应用的设置 key，不删除无关用户数据。

## Git 提交

- 修改完一个独立功能后立即提交，不在一个提交中混放多个不相关功能。
- 推荐提交类型：`feat`、`fix`、`refactor`、`test`、`build`、`docs`。
- 提交前执行 `git diff --check`，确认没有意外空白或未完成代码。
- 工作区可能已有用户暂存的 `docs/实现方案/*.artifact.md`、未跟踪日志和构建产物。提交时必须使用明确的 pathspec，只提交本次功能涉及的文件。
- 不得把用户暂存的文档、`*.log`、崩溃日志、APK/截图或未跟踪的审计文档混入功能提交。
- 不提交构建产物、IDE 临时文件或调试日志。
- 不修改用户已经提交的历史，除非用户明确要求。

## 验证要求

默认只做编译和构建验证。用户明确要求“不要运行测试，防止 IP 被风控”时，不得执行 `jvmTest`、真实登录、车辆设置写入、OTA、音效上传或任何会触发云端/车辆请求的测试。

UI 或共享代码修改后至少运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs
```

涉及 Android 或 Desktop 时运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build
```

只有在用户明确要求且测试不发起真实网络/车辆请求时才运行：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:jvmTest
```

如果 Gradle 因 `.gradle` 缓存目录在工作区外而失败，应申请一次受限提权执行构建；不要为了绕过权限把 Gradle 缓存指到临时目录并重新下载依赖。

界面结构调整还应检查：

- Miuix 与 Material 3 两种风格。
- 浅色和深色模式。
- 首次显示、设置切换和页面切换。
- 窄屏、宽屏、长文本和禁用状态。
- Android/Desktop 实际构建；能够在本机运行时再做截图或视觉检查。

只报告真实运行过的验证，不把计划中的检查写成已通过。

## 外部参考

- Miuix 文档：[compose-miuix-ui.github.io/miuix/zh_CN](https://compose-miuix-ui.github.io/miuix/zh_CN)
- Miuix 源码：[github.com/compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)
- MeowUI：[github.com/lingqiqi5211/MeowUI](https://github.com/lingqiqi5211/MeowUI)

MeowUI 只作为“一份业务 API、两套原生实现”的架构参考。它是 Android Library，不能直接作为本项目 `commonMain` 的跨平台依赖。
