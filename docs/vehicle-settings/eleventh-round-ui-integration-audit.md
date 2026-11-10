# 第十一轮：车辆设置 UI 集成与交互状态回归

日期：2026-11-10。仓库：`F:\android app code\toupin`。
Smali 根：`F:\android app code\YadeaHook\temp_smali`。

## 1. 基线与安全边界

- 开始时实际分支 `master`，HEAD `6aa719a177cbdad92881d6379428038ddd9b8a04`；staged、unstaged 均为空，不假定 HEAD 与任务文字相同。
- 已读取根 AGENTS.md、Android 逆向技能、第八至十轮报告、两个第十轮映射专项文档及当前实现；重新追踪实际 UI/Controller/Binding，不仅依据报告判断。
- 第十轮两个功能提交均存在并已接入当前调用链：`d7db57078836a15a52f385194f46b66365bc96be`、`b8060c605f560cb8b5b6d524b0c51c7fd6844b40`；实际文档提交为上述 `6aa719a177cbdad92881d6379428038ddd9b8a04`。
- 第八/九轮的普通状态模型和独立音效隔离继续保留。第九轮功能提交 `0877b4a2b9e31ea8faa575522fcaaac7e674670d`、文档提交 `c52b2f7120f4b856faae13fa917b40cb5573549f` 已在历史中核对。
- 不修改 Mapper、TTP 解析、命令映射、帧编码或 ID51。没有真实登录、云端/BLE/车辆写入、OTA、上传、删除资源或推送远端。
- 没有运行正常 App，避免应用启动时读取本机既有账号并触发自动刷新。本轮没有 Compose UI 运行、截图或实车验证。

初始 12 个 untracked 状态条目全部保留，不移动、删除或提交：

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

## 2. 结论与分层状态

| 项目 | 发现 | 处理与结论 |
| --- | --- | --- |
| ID14 观察到 UI | 第十轮正确字段与三个进度已进入真实 Controller/UI 链 | `STATIC_FLOW_VERIFIED`、`UNIT_TESTED`、`BUILD_VERIFIED`；未改映射 |
| 64/89 独立变体 | helper 确实被回填和命令层使用，没有通用 Boolean 绕过自适应分支 | 同上；没有合并变体或改变 raw 极性 |
| 64/89 门控 | 原 KMP 仅对 37/38 做连接/电源限制，64/89 子控件也不依赖实际主开关 | 修复 UI 门控；原 App 布尔组合有证据；电源来源完全等价仍 `PARTIAL` |
| UI 会话 | 重置键没有 Controller 身份；普通 Controller 未因同 VIN 车型变化重建 | 最小增加车型 key、Controller key 和普通提交 scope 边界；无业务状态机重构 |
| Slider 无障碍 | Miuix 原生 setProgress 不吸附档位且不调用完成回调；原 Sheet 没有显式保存 | 仅对三个已审计 Slider 规范化用户输入并增加保存；不改变车辆观察或命令合法范围 |
| 历史回归断言 | 一项旧测试假定新车设置列表只有 cloud:31，与既有附加按键不符 | 改为旧响应前后新车完整状态相等，另断言有31、无旧车2；独立测试提交 |
| 双主题视觉/生命周期运行 | 静态代码及本地状态测试不能证明真实布局/触摸/无障碍运行 | `RUNTIME_VERIFIED`、`VEHICLE_VERIFIED` 均未取得；整体不标 COMPLETE |

## 3. 当前链路与状态职责

代码根 K：`F:\android app code\toupin\shared\src\commonMain\kotlin\top\ghhccghk\yadea`。
下表 K 路径及 Smali 根下路径均由此明确展开，行号为物理文件行号。

```text
MainScreen.profile(VIN / model identity)
 -> remember ordinary CarSettingsController
 -> onVinChanged + refresh -> Gateway.load(VIN) -> cloud items
MainScreen.vehicleObservation(VIN / source sequence / live)
 -> updateVehicleObservation -> applyVehicleStatus
 -> valueStates.vehicleValue (actual observation)
 -> collectAsState -> CarSettingRow
 -> Sheet / native AppSwitchPreference / AppSliderPreference
 -> editDraft (user input only)
 -> submitDraft / update (fixed VIN / generation / operationId)
 -> existing Writer (no protocol changes)
 -> receipt / optional result / fresh readback, independently recorded
```

| 层 | 实际实现与职责 | 隔离与异常 |
| --- | --- | --- |
| 装配/配置 | K/ui/screens/MainScreen.kt:299,432，按车型身份重建普通 Controller，VIN/登录 effect 加载云端项 | 同 VIN 车型变化现能触发新 Controller/new load；未硬编码车型设置清单 |
| 数据接收 | 同文件444，调用 updateVehicleObservation；K/carsetting/CarSettings.kt:669 起接收 VIN/sequence/live | 保留第八轮已存在的来源与序号检查，不改 BLE 数据源 |
| 回填 | K/carsetting/CarSettings.kt:707，applyVehicleStatus | 正确字段有效时建立 Known；无效不伪造新观察；旧 VIN 被拒绝 |
| 真实/编辑 | 同文件 CarSettingValueState/vehicleValue/draftValue/submittedValue；451 editDraft、461 cancelDraft | 草稿不写入 vehicleValue；编辑取 draft→submitted→Known→控件起点，起点不是观察 |
| 提交/结果 | 同文件504 update、437 isCurrentOperation、611 catch/finally | VIN/loadGeneration/opId 固定；取消继续传播；旧 finally 不能清理新操作 |
| UI 展示 | K/ui/screens/CarSettingsScreen.kt:1427 CarSettingRow、1540 vehicleValueLabel | 显式区分实际值、草稿、本次选择、提交阶段、确认阶段和错误 |
| UI 会话 | 同文件80,1381；MainScreen.kt:99 carSettingsModelKey | UI key=Controller 身份+VIN/loadGeneration/loadedFromCloud；不随普通回读/拖动重建会话 |

本轮普通 Controller、Writer、Gateway 公共接口和 ValueState 文件均未改动。普通直接提交及 Slider 使用会话绑定 settingScope；刷新仍使用原页面 scope，避免 refresh 自己改变 generation 时被自己的新 key 取消。密码、音效、联系人等独立 scope/Controller 不被替换成此 settingScope。

## 4. ID14 原始值到 UI

| 环节 | 重新核对的原始证据 | 当前 KMP |
| --- | --- | --- |
| 页面入口 | com/yadea/smartmoto/vehicle/view/VehicleAlarmSensitivityActivity.smali:87 getSettingConfigId=14 | cloud:14 独立条目 |
| 绑定 | com/yadea/smartmoto/vehicle/databinding/ActivityVehicleAlarmSensitivityBindingImpl.smali:446 executeBindings→r1；664 a0(F) | CarSettings.kt:912 只取 alarmSensitivityStatus |
| 属性生成 | com/yadea/smartmoto/vehicle/viewmodel/VehicleSettingViewModel.smali:22495 b9→l2；30245 r1 | AlarmSensitivityMapping，CarSettings.kt:66 |
| 字段解析 | l76.smali:9387，C(data,0x1C,2)，7无效；不是任务早期文字中的G | TtpInfo.kt:570 同偏移原语；此轮不改 |
| 三位置 | 原布局0x7f0d00ed→res/EbG.xml第84源行 ticks_count=3；默认0..100；IndicatorSeekBar.smali:616,5162 | CarSettingsGateway.kt:1742 range0..100、steps1（一个内部点+两个端点） |
| 用户目标 | VehicleAlarmSensitivityActivity$a.smali:48 停止拖动发 ALARM_SENSITIVITY_GEAR；90 b读取wd5.e | VehicleSettingCommands.kt:610 将进度转tick/参数，不把50/100直接写车辆 |

| raw | Slider进度 | UI位置 | 提交参数 |
| --- | --- | --- | --- |
| 0 | 0 | 第一个 | 0 |
| 1 | 50 | 中间 | 1 |
| 2 | 100 | 最后一个 | 2 |
| 3..6、缺失、解析无效 | UNKNOWN；不建立有效新观察 | 不强制指定 | 不生成对应命令 |

布局交叉证据来自已存在 yadea.apk，SHA256 `329BA5ECF012E71E70A1496F139C21822E5D64CBB96DDFAA59A9896E474AE685`，本地 aapt2 只读核对资源 ID/布局/Binding；不是截图证据，也不声称整个 APK dex 与 Smali 已做逐字节同源证明。完整既有证据见第十轮两个专项文档，本轮不据布局推断新的固件范围。

**与ID20隔离**：K/vehicle/TtpInfo.kt:569 的 alarmWarningMode 保持独立，未重命名或拿来填14。ID20仍通过 alarmWarningVolumeLevel（CarSettings.kt:909）回填，CarSettingsGateway.kt:1735为0..7、steps6，VehicleSettingCommands.kt:601 保持原参数。状态 key 分别 cloud:14 / cloud:20。实际测试覆盖字段兄弟位、八档命令、14草稿与20观察互不替代。

## 5. ID64/89 双变体

### 5.1 原始值、目标值与实际调用

- 原入口分别为 com/yadea/smartmoto/vehicle/view/HelpPromotePlusActivity.smali:188、HelpPromotePlus2Activity.smali:196。
- Binding 分别 ActivityHelpPromotePlusBindingImpl.smali:1135→W2/F、ActivityHelpPromotePlus2BindingImpl.smali:1140→X2/G；均位于 com/yadea/smartmoto/vehicle/databinding。
- VehicleSettingViewModel.smali:23662,23698 的 b9 分别比较 customDriveSwitchStatus==1 / ==2；23712 直接转换 customDriveLevelStatus，无+1/-1。
- K/carsetting/CarSettings.kt:770 真正调用 adaptivePushOrNull(item.configId)，943起分派到64/89独立 Mapper；通用 flagOrNull 只用于各自主开关helpCarState，不处理自适应字段。
- K/carsetting/VehicleSettingCommands.kt:818 按稳定条目id识别adaptive后调用两个独立parameterForTarget；没有被共用forConfigId提前吞掉。

| 原始自适应字段 | 64 UI | 89 UI |
| --- | --- | --- |
| 0 | 关 | 关 |
| 1 | 开 | 关 |
| 2 | 关 | 开 |
| 3解析无效、缺失、域外值 | UNKNOWN | UNKNOWN |

raw1/2在另一变体显示关是原App两个比较表达式和第十轮已确认映射，不在本轮擅自重释。UNKNOWN不显示为已知关闭。

| 当前checked | 用户目标 | 64参数 | 89参数 |
| --- | --- | --- | --- |
| false | true | 1 | 2 |
| true | false | 0 | 0 |

原父FrameLayout点击调用onCbClick，子CheckBox clickable=false；两Activity onCbClick:94 与$d.smali:61证实读的是旧checked。Compose AppSwitchPreference传入的已经是**目标**checked（CarSettingsScreen.kt:1487附近），不再反转一次。UNKNOWN入口则提供“关闭/开启”显式目标选择，没有通过“假定当前false”求反。

### 5.2 档位与参数

原资源0x7f0d007d→res/uAX.xml、0x7f0d007e→res/4h4.xml，第140源行均min0/max2/ticks3；两页$a.smali:40起使用wd5.e/停止拖动发送。KMP两条cloud:64:level/cloud:89:level均range0..2、steps1；raw0/1/2→UI0/1/2→D1参数0/1/2，无额外偏移。

D0/D1、qg.smali:1625,5280与BleBicycleCommandManager.smali:2486,2501仅保护性核对，未修改。页面范围不证明所有固件完整合法域；各档物理速度及保留值仍UNKNOWN，未开放额外档位。

## 6. 连接、电源与主开关 gating

### 6.1 原 App 证据

| 事实 | Smali定位 |
| --- | --- |
| 连接初始化 | VehicleSettingViewModel.smali:27831 initModel，h取com/yadea/blecontrol/connect/l.Q() |
| 连接含义 | com/yadea/blecontrol/connect/l.smali:989 Q，内部连接状态==2返回true |
| 断开更新 | com/yadea/smartmoto/vehicle/view/SettingBaseActivity.smali:547 observe$lambda$1→VM.z7；VM:32565 z7仅状态2为true |
| 连接属性 | VM:19146 b2返回h |
| 电源初始化/更新 | VM:27850 initModel、23587 b9，p2取VehicleDataManager.G().isPowerStateOpen() |
| 电源属性 | VM:16801 R4、8360 G6均取p2 |
| 主开关属性 | VM:9307 L5取K1；b9:22195 helpCarState==1 |
| 64实际控件 | ActivityHelpPromotePlusBindingImpl.smali executeBindings：1278 actionCustom clickable、1315主开关enabled、1380 SeekBar enabled |
| 89实际控件 | ActivityHelpPromotePlus2BindingImpl.smali executeBindings：1289 clickable、1331主开关enabled、1394 SeekBar enabled |

追踪Binding布尔表达式，而非只看到setEnabled就推断条件：主开关enabled=连接&&电源；自适应父容器及档位enabled=连接&&电源&&主开关。Binding还会把断开后的checked/进度掩成false/0；KMP不把这种展示掩码当作车辆新值。

### 6.2 本轮 UI 策略

K/ui/screens/CarSettingsScreen.kt:190,1407 的 adaptivePushUiDisabledReason仅作用64/89，叠加原有云端权限、可用writer和pending规则：

| 条件 | 主开关 | adaptive/level |
| --- | --- | --- |
| 未连接，任意电源 | 禁用 | 禁用 |
| 已连接，电源false/null | 禁用 | 禁用 |
| 已连接/开机，主开关UNKNOWN | 允许显式目标编辑 | 禁用，等待主开关回读 |
| 已连接/开机，当前VIN实时有效主开关关 | 允许 | 禁用，提示先开启主开关 |
| 已连接/开机，实时有效主开关开 | 允许 | 允许（自身UNKNOWN可显式编辑） |
| 主开关正在提交 | 自身pending禁用 | 禁用，不能用待提交目标解锁子项 |

主开关必须是同configId、同VIN、live、readback VALID的实际Toggle观察。草稿/本次选择/历史值不解锁子项。断开或掉电后打开着的Sheet也重新计算enabled，三个审计Slider的编辑/结束/保存入口会复核门控。

**PARTIAL边界**：K/ui/screens/DeviceScreen.kt:107,170 使用VehiclePowerStateResolver；K/vehicle/VehicleControl.kt:129 在连接且TTP电源缺失/无效时仍可能回退云端powerState。本轮没有证明这种来源与原VehicleDataManager完全等价，因此不宣称电源来源和新鲜度已闭环；未修改共享电源解析、TCS/ABS或BLE连接逻辑。新门控严格拒绝disconnected和Boolean未知，但不伪称传入true必然是实时TTP。

## 7. Slider实际交互与双主题

1. K/ui/screens/CarSettingsScreen.kt:512按稳定id重新取得当前Slider；不是一直使用打开Sheet时的旧值。
2. K/ui/components/UiComponents.kt:758 AppSliderPreference调用resolveSliderState，统一范围/布局值；steps、enabled、onValueChange、onValueChangeFinished分别传到原生M3 Slider及Miuix SliderPreference；Modifier未添加第二个独立拖动写入逻辑。
3. 实际缓存Miuix0.9.4源码：`C:\Users\DozenOregano696\.gradle\caches\modules-2\files-2.1\top.yukonga.miuix.kmp\miuix-ui-desktop\0.9.4\a316f12d27c4e546b6b1a106dff71dbe7d0fe178\miuix-ui-desktop-0.9.4-sources.jar`，entry `commonMain/top/yukonga/miuix/kmp/basic/Slider.kt`。1252的stepsToTickFractions生成steps+2个点；1264起触摸路径按steps+1个区间取整。所以steps1确为三个位置，不能把steps1理解为两个位置。
4. 同源码245起无障碍setProgress只clamp并调用onValueChange，不吸附也不调用finished。因此只传valueRange/steps不足以保证所有入口到命令的离散值正确。
5. 修复K/ui/screens/CarSettingsScreen.kt:1394,1398：仅cloud:14、cloud:64:level、cloud:89:level的**用户输入**吸附到最近三个已证实UI位置；检查预期range、steps、有限且域内。到达该函数的域外/NaN/无穷不写草稿；不规范化TTP、不扩大命令域，也不改底层拒绝小数的Mapper。AppSliderPreference原有submitValue在此之前仍可能把原生域外事件钳到UI边界，这是已有控件行为，不声称本轮端到端捕获了钳位前事件；纯函数非法值测试也不等于原生事件运行测试。
6. 触摸拖动继续onValueChange→editDraft，结束→submitDraft。新增“保存”只给三个Slider，在无障碍无finished和UNKNOWN用户明确选择当前编辑起点时可提交。打开页面或仅显示起点不会自动创建草稿或提交。
7. 显式保存先成功建立独立草稿才提交；pending时禁用，Controller仍阻止重复操作。写入失败只撤销本次目标，保留期间的新观察/错误。取消编辑调用cancelDraft。已发请求不能被UI取消声明为服务端撤销。

两套主题使用同一业务回调/门控/保存按钮，视觉仍由App*内部实现。列表UNKNOWN显示“状态未知/未知”，Sheet另显示“车辆当前值：UNKNOWN”；已知但无效/非实时观察标历史，不让默认Slider值或草稿成为实际值。错误在行summary和页面message中保留；发送回执、结果接口确认与回读一致继续分开。

**未运行**：Miuix/M3浅深色、窄宽屏、长文本、屏幕阅读器、触摸动画、BottomSheet dismiss/重开截图检查。源码检查不替代实际视觉验证；本轮没有新的UI自动化运行框架。

## 8. VIN、车型与生命周期

| 场景 | 处理/实际验证层级 |
| --- | --- |
| VIN切换 | 既有onVinChanged清理generation/操作/草稿；本地Fake验证旧观察/写入结果不能进入新状态 |
| A→B→A | 新测试固定两个异步响应；旧A结果/旧finally不能清理新A同id pending；generation而非只比较VIN |
| 同VIN车型变化 | MainScreen新增key=(bikeModelId,modelNo,seriesCode)，重建普通Controller，配置加载和观察effect随之重启；只由云端清单提供项 |
| Controller替换 | UI key包含对象身份，即使VIN/generation相等也不沿用旧Sheet；Fake验证旧Controller响应不能进入replacement |
| 回读到达编辑中 | vehicleValue更新，draftValue不被覆盖；失败保留最新观察，不用提交前整state回滚 |
| 页面退出 | rememberCoroutineScope离开composition时取消；新settingScope还随普通会话key改变取消；取消传播/状态恢复经本地模拟，未做Compose运行测试 |
| 退出后重进同Controller | 未提交草稿可在Controller内保留，仍标draft，不当作实际值；显式取消会清除。不新增持久化/不声称进程重启恢复草稿 |
| Controller/进程重建 | 新Controller没有旧draft/receipt；新云端配置起点UNKNOWN，不能制造成功。实际进程生命周期未运行 |

旧任务已发服务端的操作不能凭本地取消撤回。车型key保护UI/Controller状态，不是对真实云端响应或真实BLE来源做车型身份认证；底层来源认证、全平台运行仍未验证。

## 9. 修改文件和独立提交

| 文件（仓库根下精确路径） | 原因与改动 | 依据 |
| --- | --- | --- |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt | 64/89门控；Controller会话key/普通提交scope；三个Slider UI输入规范化与显式保存 | 两Binding/VM；实际Compose调用和Miuix源码 |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt | 普通Controller remember增加车型身份 | 同VIN车型变化缺少重建/加载依赖的直接代码证据 |
| shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/EleventhRoundUiGatingTest.kt | 8项纯策略测试 | 连接/电源/实时同VIN主开关真值表 |
| shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/EleventhRoundUiSessionTest.kt | 7项纯生命周期边界测试 | Controller/generation/操作身份与取消；不声称Composition运行 |
| shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/EleventhRoundSliderIntegrationTest.kt | 10项UI策略→草稿→Fake Writer测试 | 已证实位置到原命令的真实调用 |
| shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsStateIsolationTest.kt | 修正旧列表独占断言，增加旧响应前后完整state相等和无旧车项 | 既有附加按键项，不改产品逻辑 |
| docs/vehicle-settings/eleventh-round-ui-integration-audit.md | 本报告，与代码分开提交 | 本轮证据与实际验证记录 |

生产代码仅两个UI装配/页面文件。协议、Mapper、普通状态模型、平台业务实现、独立音效/密码Controller全部零diff。

| 完整提交哈希 | 实际文件范围 |
| --- | --- |
| c8aad6e35aa4d539056384aa8846be5a992b4795 | CarSettingsScreen.kt + EleventhRoundUiGatingTest.kt |
| 0c1fa69c4884fe01df806ce1906ef3a226e26503 | CarSettingsScreen.kt + MainScreen.kt + EleventhRoundUiSessionTest.kt |
| d673ee38690e65807a9d33cf4064682463de8185 | CarSettingsScreen.kt + EleventhRoundSliderIntegrationTest.kt |
| 9a0233b8e5ac34a474266ac981159cadb3e6d3c8 | 仅CarSettingsStateIsolationTest.kt |

文档独立提交，准确哈希在最终交付及`git log -1 --format=%H -- docs/vehicle-settings/eleventh-round-ui-integration-audit.md`中取得，避免文件哈希自引用。每个提交前已运行git diff --check，使用精确pathspec，没有全量git add。

## 10. 实际测试与失败记录

先编译测试源码，再执行过滤的本地测试；没有运行完整jvmTest。所有Gateway/Writer/状态源均为内存Fake或FakeTransport；无真实账号/网络/BLE。

| 实际执行的测试组 | 数量 | 最终失败/错误/跳过 |
| --- | --- | --- |
| EleventhRoundUiGatingTest | 8 | 0/0/0 |
| EleventhRoundUiSessionTest | 7 | 0/0/0 |
| EleventhRoundSliderIntegrationTest | 10 | 0/0/0 |
| TenthRoundAlarmSensitivityTest / TenthRoundAdaptivePushTest | 10+13 | 0/0/0 |
| EighthRoundValueStateTest / EighthRoundStateIsolationTest | 11+7 | 0/0/0 |
| NinthRoundCustomSoundTest / NinthRoundCustomSoundGatewayTest | 29+11 | 0/0/0 |
| FifthRoundX1MappingTest / FifthRoundDistanceRegressionTest | 3+1 | 0/0/0 |
| SixthRoundReviewTest / VehicleSettingGoldenFrameTest | 4+3 | 0/0/0 |
| CarSettingsUiResetKeyTest / ResolvedSliderStateTest / CarSettingsStateIsolationTest | 3+4+3 | 0/0/0 |
| CarSettingsControllerTest的三个方法（见下） | 3 | 0/0/0 |
| SixthRoundActivityBatchTest.scheduledShutdownTimeMatchesSeekBarSemantics | 1 | 0/0/0 |
| TtpInfoParserTest的两个方法（见下） | 2 | 0/0/0 |
| 合计（19个XML suite，最终一次执行） | **133** | **0/0/0** |

数量通过读取shared/build/test-results/jvmTest/TEST-*.xml的tests/failures/errors/skipped属性求和，不凭测试源码方法数推断。新测试共25项；没有把重复运行累计为额外用例。

CarSettingsControllerTest只选择adaptivePushUsesVariantSpecificSwitchValuesAndZeroBasedLevel、adaptivePushCommandsUseVariantSpecificParameters、customKeyFunctionsReadBackFromCustomFunctionBits。TtpInfoParserTest只选择parse_energyRecoveryUsesSlideModeFields、parse_chargingDriveAndNavi_0x70_to_0x7A。

透明记录中间失败：

- 初次门控+第十轮过滤31项通过；会话+相关历史过滤40项通过。
- 加入Slider组的48项运行曾有1失败：夹具未加载当前VIN，却传VIN-A观察，门控正确拒绝。修正Fake初始化走refresh/固定VIN后，该测试通过，未放宽生产VIN规则。
- 首次扩大至133项曾有1失败：历史CarSettingsStateIsolationTest断言列表只能有cloud:31，实际既有附加项local:1005/cloud:1005:short/cloud:1005:long。对应生产Controller/Mapper在本轮未改。改为完整新车状态前后相等并验证31存在/旧2不存在；重编译后原样重跑133项全部通过。没有排除失败测试、修改按键逻辑或靠改业务返回满足断言。

最终测试命令是`:shared:jvmTest --offline`并对上述每个完整测试类（或明确列出的单独方法）逐一传`--tests '*名称'`；未对CarSettingsControllerTest、TtpInfoParserTest、SixthRoundActivityBatchTest传整类过滤。

## 11. 实际构建

全程使用已有缓存`GRADLE_USER_HOME=C:\Users\DozenOregano696\.gradle`，没有更换缓存或下载依赖：

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\DozenOregano696\.gradle'
.\gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline
.\gradlew.bat :androidApp:assembleDebug :desktopApp:build --offline
```

| 验证 | 实际结果 |
| --- | --- |
| shared JVM / JS / Wasm主源码 | BUILD_VERIFIED；三个功能修复各阶段均实际通过 |
| JVM测试源码 | TEST_SOURCE_COMPILED；新增测试与两次夹具调整后重新编译通过 |
| Android assembleDebug | BUILD_VERIFIED；每个功能阶段均通过，未安装/运行APK |
| Desktop build | BUILD_VERIFIED；test任务NO-SOURCE，不当作Desktop测试执行 |
| 过滤的133项本地JVM测试 | UNIT_TESTED，最终0失败/错误/跳过 |
| iOS | 未构建；Windows主机不能据共享编译声称iOS通过 |
| Compose实际运行、截图、双主题视觉 | 未验证，不标RUNTIME_VERIFIED |
| 真实云端/车辆/真机/BLE | 未执行，不标VEHICLE_VERIFIED |

编译仅有既有expect/actual Beta、unchecked cast、弃用API与Gradle配置告警，未顺手修改无关模块。两个中间测试失败不描述为“所有运行首次通过”。

## 12. 保护性回归与未闭环内容

- VS-013/014/015、密码、X1独立ID、普通距离、氛围灯UNKNOWN、普通定时关机、第八/九/十轮状态保护：相关纯本地用例实际通过；对应协议/模型/独立Controller均未改。
- ID51仍排除在实际观察/普通等值确认之外（CarSettings.kt:919）；syncTimeResultStatus只是RESULT_ONLY。没有因本地发送、旧值相等、UI延时或result true构造车辆真实值；第八轮相关用例通过，真实事务归属仍BLOCKED。
- 64/89门控的布尔依赖已静态还原并局部接入；云端电源回退与原VehicleDataManager的来源/实时性完全等价未证实，保持PARTIAL。
- 底层连接身份、TTP来源、实际车辆响应、固件保留值、各推行档实际速度均未运行验证，不能凭Fake证明；不开放新档位。
- UI生命周期机制为源码核对+纯本地模拟，不是实际Compose销毁/重建验证。触摸/无障碍设备、主题/分辨率/文本布局、Sheet动画残留需后续无真实服务的UI运行环境验证。
- 未修改数字钥匙、自定义标定、OTA、投屏、Wi-Fi、镜像帧发送、音效协议及密码状态机。

## 13. 收尾状态

代码与测试提交完成时，tracked staged/unstaged均为空，仅保留第1节原有12个untracked条目。新增本报告单独提交后再核对HEAD及工作区；不纳入已有未跟踪审计文档、APK、日志或反编译输出。最终准确HEAD在交付消息中记录；没有推送。
