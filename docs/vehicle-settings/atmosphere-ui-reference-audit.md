# 氛围灯设置截图参考审计与 UI 调整

日期：2026-11-11（任务开始于 2026-11-10，验证跨午夜）。

## 1. 基线与范围

- 分支：`master`。
- 修改前 HEAD：`ad3b214170fed52611f8a16e92b6ba6ac0b19217`。
- 开始时 staged / unstaged 均为空；原有 12 个 untracked 路径项全部保留。
- 已读取根目录 `AGENTS.md` 和 Android 逆向技能；先查看截图、Smali、现有面板及状态调用链，输出只读审计结论后才修改代码。
- 只改氛围灯共享 UI 及其隔离测试。没有修改 Gateway、Controller、设置数据模型、TTP 解析、命令 Mapper、帧编码或任何平台通信实现。

原有 untracked 路径项：

```text
desktopApp/yadea-mirror-log.txt
shared/yadea-mirror-log.txt
docs/ble-cross-platform-implementation-plan.md
docs/bluetooth-kmp-audit.md
docs/flutter/
docs/ui/ui-optimization-execution-plan.md
jadx-single-skin-generator.java
jadx-single-skin.java
jadx-toupinNew/
toupinNew.apk
yadea.apk
原app设置截图/
```

## 2. 截图结构与修改前差异

主要 UI 证据：`原app设置截图/Screenshot_2026-11-10-23-15-02-213_com.yadea.smartmoto.jpg`。
SHA256：`A8592F73CE07534E68F7DEBF4AD2C704A27DB96C306092620270C6DFF945A33B`。
截图没有作为参数、协议或车辆状态证据。

| 区域 | 原 App | 修改前 KMP | 本轮 UI |
| --- | --- | --- | --- |
| 标题/导航 | 氛围灯设置，独立页返回导航 | 氛围灯模式与颜色 BottomSheet | 改标题，保留现有双主题 BottomSheet 导航结构 |
| 自定义开关 | 顶部直接 Switch | 普通列表内独立条目 | 面板内复用同一 `cloud:73` 条目与目标值回调，列表入口不删除 |
| 灯光模式 | 常亮、闪烁、呼吸、流水，四列描边选项 | 弹出菜单，顺序常亮/呼吸/闪烁/流水 | 内联四列，按原页显示顺序，保留原数值 |
| 运行方式 | 流水时，两列三行 | 流水时弹出菜单 | 两列三行，原页顺序 |
| 单色/双色 | 颜色标题右侧互斥选择 | 单色模式 Switch | 复用原生双主题 `AppSegmentedControl` |
| 颜色 | 色样 + 内联色谱，双色出现颜色A/B | 点击颜色条目，再打开通用颜色对话框 | 内联色谱与色样，双色两组，常亮/闪烁仅预览 |
| 分组 | 截图为圆角背景分组 | 纵向偏说明性的设置条目 | 保持分组顺序、间距与分隔，采用面板内无嵌套卡片的整宽布局 |
| 应用 | 底部主按钮 | 底部描边保存按钮 | 主按钮“应用”，滚动正文与操作区分离 |
| 状态 | 原 App 有默认值及“应用成功”反馈 | KMP 车辆观察、缓存、草稿、请求状态 | 保留 KMP 来源/错误/加载信息，不照搬虚假确认 |

保留原生主题色、字形和导航，不强制复刻截图的白色按钮/绿色开关，也不复制 Android 状态栏。这是布局与交互参考实现，不是像素级复刻。

## 3. Smali 与资源证据

以下 Smali 路径均相对于 `F:\android app code\YadeaHook\temp_smali`；行号为本轮只读核对时的文件行号。

| 证据 | 核对内容 | UI 对应 |
| --- | --- | --- |
| `com/yadea/smartmoto/vehicle/view/FeelLightSettingActivity.smali:113` | 构造函数依次创建常亮/闪烁/呼吸/流水，`isSingle` / `isRiver` 标记 | 模式顺序与联动 |
| 同文件 `:896`，`initClick$lambda$8` | 显示索引 1 写入模式值 2；显示索引 2 写入模式值 1 | 不能把显示索引当命令值 |
| 同文件 `:1201`、`:1234` | 两个 GridLayoutManager 分别 span=4、span=2 | 模式四列，方向两列 |
| 同文件 `:805`，`initClick$lambda$11` | 单色隐藏颜色2、标题为颜色；双色显示颜色2、标题为颜色A | 分段控件及第二组色谱 |
| 同文件 `:1392`，`onSwClick` | 父点击监听读取旧 checked 并反转一次，发送已有开关命令 | Compose 回调直接使用目标 checked，不二次反转 |
| 同文件 `:1471`，`refreshUi`，尤其 `:1742`、`:1947` | 常亮/闪烁显示单色预览；呼吸/流水显示单双色选择、色谱；仅流水显示运行方式 | 条件可见性 |
| 同文件 `:2087`，`settingData` | 显示方向顺序转换为既有业务值 | 方向显示与业务值解耦 |
| 同文件 `:2404` | `getSettingConfigId()` 返回 73 | 入口范围不扩大 |
| `.../view/FeelLightSettingActivity$c.smali:61` | 应用监听：检查选择，组装参数，通过原有设置路径提交；不是选择时即写入 | 保持草稿编辑 + 应用提交 |
| `.../view/FeelLightSettingActivity$d.smali`、`$e.smali:61` | 色谱监听更新色样、颜色数组、页面变更标记 | 色谱拖动只调用 editDraft |
| `.../weight/HueSliderView.smali:194`、`:280`、`:737`、`:1284` | 红黄绿青蓝品红红色谱、六段各 16%、末端白色区域；触摸取色；近白归一到 254/254/254 | 仅用于色谱展示和取色几何，不修改车辆编码 |
| `.../weight/HueSliderView.smali` 的 `setColor` | 以 RGB 距离找最近位置，白色放在末端 | 现有颜色投影到滑块位置时不反写草稿 |
| `.../databinding/ActivityFeelLightSettingBindingImpl.smali` | 控件绑定与布局 tag | Activity/控件对应关系 |

补充布局证据：只读检查用户已有 `yadea.apk`，使用现有 SDK `F:\sdk\build-tools\35.0.1\aapt2.exe`。资源 `0x7f0d0075 layout/activity_feel_light_setting` 实际位于 `res/USD.xml`，tag 为 `layout/activity_feel_light_setting_0`，包含两个 HueSliderView、RadioGroup/RadioButton、应用按钮、开关关闭说明。未运行或改写 APK；不据此声称整个 APK 与 Smali 逐字节同源。

### 显示顺序表

| 原页显示项 | 现有模式值 |
| --- | --- |
| 常亮 | 0 |
| 闪烁 | 2 |
| 呼吸 | 1 |
| 流水 | 3 |

| 原页方向顺序 | 现有方向值 |
| --- | --- |
| 正序 | 0 |
| 逆序 | 1 |
| 先正序后逆序 | 4 |
| 先逆序后正序 | 5 |
| 中间到两边 | 2 |
| 两边到中间 | 3 |

这些值已在现有 Mapper 使用，本轮只改变 UI 排列，不修改协议值或数值范围。

## 4. KMP 数据流与文件职责

```text
CarSettingsGateway.load / bikeConfigVos
  -> CarSettingItem cloud:73、cloud:73:config
  -> CarSettingsController.state (StateFlow)
  -> CarSettingsScreen 收集 + 原有 VIN/Controller/loadGeneration 会话 key
  -> AtmosphereSettingsEditor 展示
  -> editDraft（模式/方向/单双色/颜色）
  -> 应用 -> 原有 controller.update -> Gateway/Writer/命令编码
  -> 原有 writeState / confirmationState / error 回到 UI
```

- `CarSettings.kt:137`：Atmosphere 模型；`:194`：观察/缓存/配置/草稿/提交状态；`:474`：编辑；`:485`：取消；`:528`：写入。全部未修改。
- `CarSettingsGateway.kt:1003`：仅当云端返回 73 时追加配置入口；权限来自 `isAuth`。未修改。
- `CarSettings.kt:928`：主开关通过 `ambientStatus` 回填。配置模式/方向/RGB 不借用主开关回读。
- `VehicleSettingCommands.kt` 的 `cloud:73:config`：沿用现有 `ATMOSPHERE_CONFIG` / 0xF7 及其 RGB 归一化。未修改。
- `VehicleSettingFrameEncoder.kt`：沿用现有氛围灯帧构造。未修改。

新增编辑器文件仅负责布局、选择组件和色谱几何。没有 ViewModel、额外 StateFlow、Repository、持久化或重复的设置配置体系；最近颜色位置使用 Compose remember 缓存，不存业务值。

### UNKNOWN 与提交

- 没有可展示值时，不选中默认模式、不高亮默认方向、不显示默认色样/滑块；应用禁用，用户可以主动选择模式建立草稿。
- 缓存/配置/提交选择仍沿用现有 displayKind 优先级，状态文字保留历史来源，不将其改写成实时车辆观察。
- 色谱原生滑块在未选择时隐藏指针、标明 UNKNOWN；首次绘制不回调编辑或提交。
- 控件只更新 Controller 草稿；应用保持 `valueKnown=false`，没有伪造车辆确认。
- 应用后保留面板，展示既有通道接受/未确认/失败信息，不弹“车辆应用成功”。错误独立着色，正文状态不重复错误。
- 编辑和应用受原有权限、刷新、pending 限制；同面板主开关与配置提交期间互相禁用，避免交互重叠。主开关没有可信展示值时保持直接 Switch + 未知/禁用，不变为菜单。
- 仍用原有会话 key 清除 VIN/车型/Controller 变化后的面板；应用协程归属于原有 settingScope，不新增状态隔离框架。

## 5. 未处理的业务差异与证据边界

1. **UNKNOWN / PARTIAL：模式、方向、RGB 的可靠车辆回读。** 原 Smali 存在 `atmosphereShowMode` 和颜色 Observer，当前 KMP 模型也包含部分同名解析字段，但尚未在现有 Controller 建立完整配置回填与确认链。本轮没有根据同名字段或主开关状态补造该链，也不把 UNKNOWN 写成“没有任何原 App 回读代码”。需要独立业务审计。
2. **PARTIAL：原页 gating。** `refreshUi` 要求 M3()/b2()/G6() 同时为 true。ViewModel 的 M3 返回 U3，U3 来自 `ambientStatus == 1`（`:24445`）；G6 返回 p2，p2 来自 `VehicleDataBean.isPowerStateOpen()`（`:23587`）；b2 返回 h，h 由 z7 的参数等于 2 更新（`:32566`）。本轮只记录原链，未更改 KMP 既有连接/电源业务或扩大写权限。离线与未知态的具体车辆可执行性仍须另行验证。
3. **PARTIAL：常亮/闪烁的既有载荷差异。** `$c.onClick` 的 `:cond_102` 分支对模式 0/2 固定 COLOR_MODE=0、RGB2=255/255/255、OPERATION_MODE=-1；KMP 当前 Mapper 保留 singleColor 和第二组 RGB。此差异不是 UI 排列修复，不在本轮修改 Mapper/编码，也未偷偷清空或归一化用户已有草稿。是否需要改变该事务必须另行确认完整链。
4. 原 App 监听 `ATMOSPHERE_CONFIG` 后显示“应用成功”（Activity `:1139` 起）；这不构成 KMP 的车辆确认依据，没有迁移该成功语义。
5. 原页关闭开关、连接/电源变化后的完整动态显示，以及真车颜色/灯效，未实际运行确认。

## 6. 修改清单

| 文件 | 修改及必要性 |
| --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt` | 替换氛围灯弹出菜单/颜色对话框组合为内联编辑器；复用主开关；使用原有会话协程；保留来源、提交、失败状态；只移除该面板不再使用的色彩辅助函数/import |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/components/AtmosphereSettingsEditor.kt` | 无业务存储的共享面板；四列/两列选择、分段颜色模式、原生双主题色谱输入、滚动正文与应用区、未知和禁用表达 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/ui/AtmosphereSettingsEditorTest.kt` | 无应用根节点的纯本地离屏测试与取色/顺序测试；没有实际 Controller/Gateway 或平台业务服务 |
| 本文档 | 记录审计、保留差异和验证边界 |

未改 BLE/Wi-Fi/投屏/OTA、密码、音效、缓存、云端动态配置、ID14、64/89、51 及其他设置状态机。

## 7. 实际验证

所有 Gradle 命令使用已有 `C:\Users\DozenOregano696\.gradle` 缓存和 `--offline`，未更换缓存、重下依赖或执行真实账号/云端/车辆操作。

| 任务 | 实际结果 |
| --- | --- |
| `:shared:compileKotlinJvm` | BUILD_VERIFIED，通过 |
| `:shared:compileKotlinJs` | BUILD_VERIFIED，通过 |
| `:shared:compileKotlinWasmJs` | BUILD_VERIFIED，通过 |
| `:shared:compileTestKotlinJvm` | TEST_SOURCE_COMPILED，通过 |
| `:androidApp:assembleDebug` | BUILD_VERIFIED，通过 |
| `:desktopApp:build` | BUILD_VERIFIED，通过；Desktop 自有 test 任务 NO-SOURCE，不算测试通过 |
| `:shared:jvmTest --tests top.ghhccghk.yadea.ui.AtmosphereSettingsEditorTest --offline` | UNIT_TESTED，实际 5 项、失败 0、跳过 0；没有执行完整 jvmTest |
| iOS 构建/模拟器 | 未执行，当前 Windows 主机不具备 macOS 验证环境 |

5 项测试覆盖：显示顺序不冒充命令序号；六段色谱和白色端点；两主题/明暗/三种尺寸/UNKNOWN、单色、双色、pending 的 48 组离屏渲染及“不编辑、不提交”断言；两主题点击/拖动取色；两主题禁用不回调。

初次隔离检查发现 Miuix 原生 Slider 不处理单击，以及 M3 空 track 槽没有正常参与测量。已在此组件内补齐 tap、非零测量轨道，并复测通过；未改通用 AppSlider。

离屏 PNG 位于忽略目录 `shared/build/reports/atmosphere-ui/`，未加入提交。已人工查看 Miuix/M3、明暗、320/480/840 宽的部分渲染，与原截图核对分组顺序、描边选择、单双色布局及颜色端点。短屏正文允许滚动，应用区独立。

**验证分层：** 布局/交互证据为 STATIC_FLOW_VERIFIED；上述测试为 UNIT_TESTED，构建为 BUILD_VERIFIED。离屏组件确实执行了 Compose，但不代表完整 App 的 RUNTIME_VERIFIED。没有启动/安装运行 Android App，没有完整 BottomSheet 宿主或真机视觉对照，没有浏览器运行、真实云端或 VEHICLE_VERIFIED。

## 8. Git

功能提交：`6b0d75c8dba19106b284b84213a47ccfde8cff84`，仅包含上表的 2 个共享 UI 文件及 1 个 JVM 测试文件。

文档独立提交，可用 `git log -1 --format=fuller -- docs/vehicle-settings/atmosphere-ui-reference-audit.md` 查询其完整身份，最终回复同时提供提交号。未修改历史、未推送远端。功能提交前后均执行 `git diff --check` / `git diff --cached --check`。

功能提交后 staged / unstaged 为空，原有 12 个 untracked 路径项保持原样。本文档单独提交后应保持相同状态，生成的构建/离屏文件不入库。
