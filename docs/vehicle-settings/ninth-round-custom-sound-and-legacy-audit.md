# 第九轮：自定义音效隔离与遗留设置证据审计

日期：2026-11-10。仓库：`F:\android app code\toupin`。
Smali 根目录：`F:\android app code\YadeaHook\temp_smali`。
基线：`master / 6843110`。第八轮报告、根目录 AGENTS.md 和 Android 逆向技能已阅读。

## 1. 安全基线与范围

- 开始时 staged / unstaged 为空；已有 untracked 的日志、APK、截图、反编译文件和用户文档全部保留。
- 阶段 A 仅修改 41/88 自定义音效的 Controller、音效请求边界、查询有效性、UI 生命周期及状态表达；不合并普通设置 Controller，不修改音效请求路径、参数、写接口布尔返回规则、上传、下载或 BLE 协议。
- 阶段 B 对 14/64/89/51 只读取证，不改 Mapper、TTP 字段、命令或参数。
- 本轮未真实登录、联网请求云端、试听下载、上传、写车辆、发送 BLE、OTA。编译使用既有 Gradle 缓存和 `--offline`；测试严格过滤到已检查的 fake 测试。

## 2. 实际调用链和生命周期

```text
MainScreen.remember(gateway, profileSource)
  -> CustomSoundController(featureScope, vinProvider, modelIdProvider)
  -> CarSettingsScreen: confId 41/88 同一个两层 AppBottomSheet
  -> refresh / setEnabled / apply / delete / rename / addFromUrl
  -> CustomSoundGateway（生产：YadeaCarSettingsGateway）
  -> 音效专用 postSoundFields -> 原 postFields -> HttpTransport
  -> 原解析及 queryResultSetResourceBike 轮询
  -> 当前请求身份检查 -> StateFlow -> App* 双主题语义控件
```

原 Controller 在 MainScreen 中跨设置页面、VIN 复用，featureScope 随 MainScreen 生命周期；原设置页退出不取消音效请求。VIN/车型由 profile StateFlow 的当前值提供。原 refresh 固定 VIN 但在协程执行时才读取 modelId；add 也存在同样的车型快照问题。

## 3. CS-01 至 CS-08 源码复核

| 编号 | 原实现的具体缺陷 | 本轮处理 |
| --- | --- | --- |
| CS-01 refresh | 无返回身份检查，多 refresh 乱序可覆盖；整份 loaded 替换会清除忙碌状态；取消吞掉 | 最新 refresh 身份生效；只合并云端数据字段；VIN/车型/代次校验 |
| CS-02 setEnabled | 直接改 enabled；失败用 previous 整对象回滚；返回未校验 VIN | draftEnabled/submittedEnabled 独立，失败不回滚整个状态 |
| CS-03 apply | 成功用旧 current 重建 enabled/categories/resources，直接标“已应用” | 不改云端查询选中值；保留本次提交目标与返回事实；车辆 UNKNOWN |
| CS-04 delete | busy 在 launch 内才设置，可重复进入；旧响应直接删当前资源 | 启动前同步预留写身份；仅删除当前资源列表对应项 |
| CS-05 rename | busy 预留晚，旧返回可改新车；分类名称没有同步更新 | 当前身份才能局部更新资源及对应分类名称，不覆盖其他分类 |
| CS-06 addFromUrl | 无 busy 入口检查；modelId 晚读；成功后刷新不可靠地结束 busy | 与其他写操作串行；固定 VIN/车型；一次受保护的后续刷新，独立清理 busy |
| CS-07 异常 | 六类异步路径均 runCatching 吞取消 | CancellationException 继续传播；CANCELLED 与普通 FAILED 分开；旧 finally 不清新状态 |
| CS-08 状态 | 云端查询值、本地选择、发送和“已应用”混合 | 原云端数据、本次草稿/目标、请求链路结果、服务端原始码分离；不构造车辆确认 |

## 4. 增量设计与更新规则

全部模型保留在既有 `CustomSoundGateway.kt`，没有增加配置系统。

| 字段/机制 | 职责与限制 |
| --- | --- |
| vin/modelId/generation | Controller 状态归属；车辆或车型变化清空状态并失效旧操作；A→B→A 也不是同一会话 |
| Request.id / loadRequest / writeRequest | 两类独立操作身份；每个返回与 finally 必须拥有对应槽位 |
| loading/loadStatus | 仅查询过程，不代表提交或车辆确认 |
| busy/operationStatus | 写操作同步预留，状态为 IDLE/PENDING/SUCCESS/FAILED/CANCELLED；SUCCESS 只表示 Gateway 返回 true |
| enabled/categories/selectedResourceId | 既有 Gateway 查询解析值，不是车辆实时观察；apply/enable 不再直接改写 |
| draftEnabled/applyingSelection | 正在提交的用户选择；完成后清除，不冒充云端值 |
| submittedEnabled/submittedSelection | 本次请求目标，与云端查询值分开；失败撤销目标，取消不宣称服务端已撤销 |
| requestAccepted | 仅原响应明确提供 success 时记录，缺失为 null；不凭 Gateway 布尔 true 补造 |
| serverStatus | 原结果响应 status 原始数值，缺失为 null；不是 TTP，也不是车辆确认 |
| gatewaySucceeded | 原 Gateway 返回事实，与 requestAccepted/serverStatus 分开 |
| loadedFields | 本次查询成功且有数据容器的字段集合；部分失败保留历史值但不宣称其仍有效，ENABLED 缺字段时 UNKNOWN |
| inFlightWrites / reconciliationRequired | 取消后实际 Job 结束前仍保留同 VIN 写占用；再次提交前需完成当前会话的资源/分类/选中查询 |
| StaleCustomSoundRequest | 内部 STALE_RESPONSE，以取消异常表达；过期请求不允许把自己的状态/消息写到当前车辆 |

- 同 VIN 查询采用 latest-wins。写操作串行化，避免 delete/rename/apply 相互覆盖；不是新增并行任务框架。取消但尚未结束的底层请求仍持有该 VIN 的占用，其他 VIN 不被它阻塞；返回原 VIN 时也不能绕过占用。
- refresh 遇到写操作只记录一次后续刷新；开始写操作会失效旧快照请求；写入期间云端查询值不被旧 refresh 替换。
- 所有公开入口、每次 Gateway 返回和音效子请求前后校验 VIN/车型/代次/操作；生产 Gateway 的四段列表查询、提交、轮询也使用同一协程上下文身份。
- 页面退出或关闭主管理面板调用 cancelPending；MainScreen 替换 Controller 或退出时 dispose；VIN/车型 collector 提前清理，返回校验作为第二层保护。
- 取消继续向父协程传播；完成清理含协程尚未开始就取消的情形。旧请求回调无权清理新 loading/busy。
- 已发出的请求无法撤回；记录过的接受事实在取消/失败后保留。不承诺协程取消能撤销服务端操作。
- ENABLE/APPLY 成功后发起一次受保护的云端刷新，不把目标写进云端快照；刷新未赶上时 UI 明确显示本次选择，允许反向选择，不把旧快照误用为本地目标。
- 直接取消返回的 Job、Gateway 自身抛取消和页面取消均要求重新查询。查询失败不能解除 reconciliationRequired；取消前已发出的服务端异步工作仍可能继续，这个标记不构成远端事务终止证明。
- UI 继续复用 AppBottomSheet/AppSwitchPreference/AppText/AppListRow，两层结构和试听/管理入口保留；“使用中/已应用”改为“云端记录/请求返回/车辆未确认”。

## 5. 音效原始行为证据

下列路径均相对于 Smali 根目录。行号为本轮文件实查位置，方法为稳定检索键。

| 业务 | Smali 证据 | 本轮边界 |
| --- | --- | --- |
| 41/88 共用入口 | `com/yadea/smartmoto/vehicle/carsetting/common/CarSettingsNavigation.smali:93` 的 c，先查 41 再查 88 | 不新增入口或推测车型 |
| 分类/当前资源/次数 | `com/yadea/smartmoto/sound/effect/model/BootSoundEffectModel.smali:289`；`SoundEffectModel.smali:202,345`；根 AGENTS 的 zdyyx001 约束 | 原解析和请求保持不变 |
| 应用 | `BootSoundEffectModel.smali:335` setResourceBikeResource，字段 vin/type/resourceId/parentConfId/parentConfName/sonConfId/sonConfName，接口在 430 行 | 仅请求生命周期校验和接受事实记录；不更换参数类型 |
| 结果查询 | `BootSoundEffectModel.smali:476` setResourceBikeResult，514 行接口；`SoundEffectModel.smali:254` otaSendId | 保留原 polling 行为；不将状态码当 TTP |
| 开关 | `SoundEffectModel.smali:392` 附近 soundEffectChange，427 行接口，type 经 Integer.valueOf | KMP 原有 type 字符串编码不在本轮改变；不声称类型等价已实测 |
| 结果通知 | `BootSoundEffectModel$setResourceBikeResult$disposable$1.smali:114` onSuccess$lambda$1，向 nr5.d 分发 SoundStatusBean | 不是车辆观察证据 |
| 添加/重命名/删除 | `SoundEffectAddModel.smali:161,372`；`sound/effect/dialog/SoundEffectReNameDialog.smali:235`；`SoundEffectDeleteDialog.smali:585` | 不修改业务协议/上传下载链路 |

**遗留限制**：原 KMP pollSoundResult 在缺少 otaSendId 时返回 true，缺少 status 时第三次查询也可返回 true。本轮保留这套兼容返回规则，不把 true 解释为服务端结果码 0 或车辆已应用；requestAccepted/serverStatus 缺证据时仍 null。聚合查询新增 loadedFields：接口 success 不为 true、缺少 data 容器或分类元数据时对应字段不生效；全部失败返回 null。分类接口失败时即使车辆配置查询成功，也不把保留的旧分类选中值认证为有效。旧解析值不被失败查询的 false/空列表覆盖。

## 6. 阶段 A 验证

第一版 31 项限定纯本地测试通过。独立只读复核后，补齐成功后刷新、不可取消底层请求占用、部分查询有效性、直接取消重新查询四类边界。最终过滤执行 **58 项，0 failures/errors/skipped**：本轮 Controller 29 项、FakeTransport Gateway 11 项、第八轮 ValueState 11 项、StateIsolation 7 项。

一次扩展测试因同 VIN 车型切换夹具在锁仍持有时对 refresh() 返回 null 使用 `!!`，随后没有释放 NonCancellable 假请求而挂起。仅终止本次 Gradle Test Executor，任务记录为 FAILED（1m36s）；修正夹具为先验证刷新延后、释放旧请求，再查询新车型，重新编译并运行上述 58 项通过。没有将该中断描述为通过。

本轮不执行完整 jvmTest，不使用真实账号、网络、车辆或 BLE；iOS 未构建，两种主题和真实页面生命周期未进行运行/视觉检查。

## 7. 阶段 B：遗留映射只读取证

本节仅审计，不修改以下设置的模型、Mapper、TTP、命令、帧、UI 或旧报告。Smali 路径相对于本报告开头的根目录；KMP 路径若无前缀，位于 `shared/src/commonMain/kotlin/top/ghhccghk/yadea/`。行号以本轮源码为准。

### 7.1 ID 14：报警灵敏度

| 环节 | 原 App 具体证据 | 当前 KMP / 结论 |
| --- | --- | --- |
| 页面归属 | `vehicle/view/VehicleAlarmSensitivityActivity.smali:87` getSettingConfigId 返回字符串 14（本节 vehicle 前缀为 `com/yadea/smartmoto/vehicle/`） | `carsetting/CarSettingsGateway.kt:1735` 与 20 共用 Slider 0..7、初值未知 |
| UI 读取 | `vehicle/databinding/ActivityVehicleAlarmSensitivityBindingImpl.smali:446` 调 VM.r1，475 附近解包 Integer，664 把数值传给 IndicatorSeekBar.a0(F) | 不是凭字段名称判定，而是跟踪真实绑定 |
| 绑定数据源 | `vehicle/viewmodel/VehicleSettingViewModel.smali:30245` r1 返回 l2；22495 读取 **alarmSensitivityStatus**，22503 更新 l2；转换 0→0、1→50、2→100、其余→0 | `carsetting/CarSettings.kt:875` 实际读取 **alarmWarningMode**，0..7，两个链不一致 |
| 字段解析 | `l76.smali:9387` 写 alarmSensitivityStatus，调用 C(data,0x1C,2)；9764 写 alarmWarningMode，调用 C(data,0x33,0) | `vehicle/TtpInfo.kt:567` 只有后者；本轮搜索未找到 KMP alarmSensitivityStatus |
| 用户操作 | `VehicleAlarmSensitivityActivity$a.smali:48` 附近停止拖动时发送 ALARM_SENSITIVITY_GEAR、thumbPosition；90 附近从 wd5.e 取值。`com/warkiz/widget/IndicatorSeekBar.smali:5218-5252` 证实 e 是 tick 位置（反向时 tickCount-position-1），不是 0/50/100 进度 | `VehicleSettingCommands.kt:610` 将 Slider 数值作为 0..7 参数。不能把 UI 进度、tick 位置和编码容量混为一谈 |
| 文案/编码 | `hp6.smali:671` m(I) 为 0低/1中/2高，其他为空；`qg.smali:2264` 映射 0x5F；`BleBicycleCommandManager.smali:3674` generateBleCommand 的 0x5F 分支支持三位参数位置 | 既有 `VehicleSettingFrameEncoder.kt:185` 编码结构有对应证据，但三位编码容量不证明原 UI 开放八档 |

**结论：STATIC_FLOW_VERIFIED（已证明历史映射不一致）；迁移 PARTIAL。** 第五轮报告第 82 行的“alarmWarningMode / 0..7 / 映射一致”和第七轮清单第 62 行的 COMPLETE 不能继续当作业务语义已证实。本次完整绑定链支持 alarmSensitivityStatus，而非两字段可互换。第八轮状态测试只验证既有字段的状态行为，不证明原业务映射正确。

后续需单独授权的映射修复与回归：补充原字段解析、按真实 tick 参数设计 UI 转换、明确无效值策略、保护既有车型；本轮不更换字段或范围，也不以静态证据宣称用户车辆已复现。

### 7.2 ID 64 / 89

| 环节 | 原 App 证据 | 当前 KMP 对照 |
| --- | --- | --- |
| 页面归属 | `com/yadea/smartmoto/vehicle/view/HelpPromotePlusActivity.smali:193` getSettingConfigId 返回 64；`HelpPromotePlus2Activity.smali:201` 返回 89 | 同为拆分的总开关、自适应和档位稳定条目，不应合并变体语义 |
| 主开关 | 两页绑定调用 VM.L5→K1；`VehicleSettingViewModel.smali:22195` 附近以 helpCarState==1 回填，另有连接/电源视觉约束 | `carsetting/CarSettings.kt:740` 使用 helpCarState；不是凭名称改为 pushModeStatus |
| 自适应 UI 数据源 | `ActivityHelpPromotePlusBindingImpl.smali:1135` →VM.W2→F；`ActivityHelpPromotePlus2BindingImpl.smali:1140` →VM.X2→G（两文件位于 `com/yadea/smartmoto/vehicle/databinding/`） | 当前应按 confId 区分，而不是共用通用布尔转换 |
| 自适应转换 | `com/yadea/smartmoto/vehicle/viewmodel/VehicleSettingViewModel.smali:23662` b9 路径：F=(customDriveSwitchStatus==1)，G=(customDriveSwitchStatus==2) | `CarSettings.kt:734-738` 仍统一 flagOrNull：raw2 被判 false、raw1 判 true，因此 89 与原链相反；906 的 adaptivePushOrNull helper 无调用者 |
| 档位读取 | 同 VM:23712 将 customDriveLevelStatus 直接 int-to-float，无 +1/-1 | `CarSettings.kt:738` 仍 floatIn(1..3)；`CarSettingsGateway.kt:1297` 提供 0..2，`VehicleSettingCommands.kt:412` 写 0..2：目标0被过滤、3却可成为范围外观察 |
| 原字段解析 | `l76.smali:10990,11125` 附近及其后续档位分支：helpCarState=G(data,0x6E,6)，customDriveSwitchStatus=G(data,0x76,6)，customDriveLevelStatus=C(data,0x79,0) | `vehicle/TtpInfo.kt:538,555,556` 已同样解析，本轮不更换偏移或位域 |
| 自适应点击 | 两 Activity.smali:94 的 onCbClick 读取**点击时已有** checkbox 状态：64 false→1、true→0；89 false→2、true→0。64 setListener:209 把监听挂在 actionCustom FrameLayout；`HelpPromotePlusActivity$d.smali:61` 的 onClick 仅去抖后调用，不先翻转 checkbox；89 同类父容器监听路径 | `VehicleSettingCommands.kt:825` 为目标 on→0、off→1/2，当前“取消值”注释与原父容器点击路径的目标语义相反。不能把“点击时已勾选发送0”误解成“目标勾选发送0” |
| 命令/档位事件 | `qg.smali:1639` 附近映射自适应 D0；64/89 参数分别为 {0,1}/{0,2}；`HelpPromotePlusActivity$a.smali:56` 档位直接发送 thumbPosition，`com/warkiz/widget/IndicatorSeekBar.smali:5244` 证实其为零基 tick 位置 | 保持原 D0/D1 和编码不变；未将位域容量推导为原车型完整合法范围或物理速度 |

**结论：上述局部链路与差异 STATIC_FLOW_VERIFIED，迁移 PARTIAL，不修改。** 已核查当前源码及历史提交 `0e1f8c9`：helper 存在不代表回填已接入，不能把 VS-015 历史 COMPLETE 当作当前路径已正确，也不能未经 Git 证据就声称是后来回归。历史修复记录 `docs/agent/vehicle-settings/VEHICLE_SETTINGS_REPAIR_LOG.md:355` 的“勾选写0”及完成结论需以本次点击前状态/目标状态区分重新评估；旧文件本轮保持原样。

后续建议三项分别取证/修复：接入分变体读取；使实际档位读取与已证实 UI tick 语义一致；复核目标开关→D0 的极性并新增纯本地回归。仍缺原布局恰有三个 tick、完整固件参数允许范围和各档物理速度的独立证据；不得根据当前 0..2 模型、三位字段或报告文字补造。用户既有实测与本轮静态发现分开记录，本轮没有操作车辆复现。

### 7.3 事务语义共同边界

14/64/89 当前普通 Controller 的“新快照与目标相同”确认规则来自第八轮；本轮只核对实际输入链。若输入映射有偏差，状态隔离测试通过也不能证明业务确认正确。51 则是操作结果位，不能自动复用持续配置的等值确认。字段解析、业务转换、写入目标和事务关联应分别提供证据，不能因它们共享 Repository 就认定闭环。

### 7.4 ID 51：仪表时间同步与事务确认

| 环节 | Smali 证据 | KMP 对照与边界 |
| --- | --- | --- |
| 页面/用户事件 | `com/yadea/smartmoto/vehicle/view/BleMeterTimeSyncActivity.smali:245` confId 51；67 的 onSwClick 取反 switch，OPEN/1 与 CLOSE/0；原为关闭时延迟 2000ms 分发 ble_sync_gauge_time 事件（190 附近） | `carsetting/VehicleSettingCommands.kt:475` 保持 OPEN/CLOSE、1/0，不改业务 |
| 写入编码 | `qg.smali:3236` CLOSE 与 7007 OPEN 均到 0xC1；`com/yadea/blecontrol/data/BleBicycleCommandManager.smali:1250` generateBleCommand 0xC1：0 为既有 FC 固定串，1 为 FD 固定串 | `VehicleSettingFrameEncoder.kt:240` 对应固定串；这里只核对原分支，不新增帧或真实发送 |
| UI 回填 | `BleMeterTimeSyncActivity.smali:281` 观察 VM.k2；VM.smali:28292 返回 D2，23115-23165 将有效 syncTimeResultStatus 按 ==1 转 Boolean 写 D2；`BleMeterTimeSyncActivity$setListener$2.smali:60` 在状态更新锁外刷新 switch | 能证明原 App 展示如何更新，不能据此证明它是本次写操作的成功回执 |
| 原始字段 | `l76.smali:11530/11539`：syncTimeResult=G(data,0xAB,6)，syncTimeResultStatus=G(data,0xAC,0) | `vehicle/TtpInfo.kt:700-701` 同字段。结果位与持续设置值的含义不宜合并 |
| 更新时间锁 | `SettingBaseActivity.smali:1032` beginStatusLimit：mStatusUpdateLimit=true，取消旧计时器，按 min(p1,6)*500+300ms 启动；其 `$beginStatusLimit$1.smali:225` 附近延迟后解除 | 这是 UI 限流，不是请求身份匹配。sendVehicleSetting:1936 的时间记录和 VehicleService 调用也不证明车辆已执行 |
| 通用帧尾 | `BleBicycleCommandManager.smali:5114` 调 getIncreaseByteHexStr；8913 的实现按 increaseByteNum 循环附加 FF，负数归零；increaseControlByte(I) 设置长度 | **该方法并非递增事务序列号**。本轮查到的是填充长度，不把命名当关联证据，也不推断其他协议绝无 ACK |
| 当前状态保护 | `carsetting/CarSettings.kt:859` 保留兼容 kind 更新；882 明确排除 51 建立实际观察或事务确认；`CarSettingsGateway.kt:326` 附近结果查询请求只有 configId 等既有字段，无本次操作 ID/VIN 关联参数 | 保持第八轮保护不变；旧 TTP 结果位、接口 result、发送成功均不能晋升为本次车辆确认 |

**结论：展示转换和写入分支 STATIC_FLOW_VERIFIED；本次事务相关性 BLOCKED/PARTIAL。** 缺少可证明新结果归属本次 VIN/操作的返回字段、关联规则或抓包。原 Activity 的限时屏蔽和延迟事件不能替代关联证据；本轮不修改命令，不把结果位重新作为真实车辆值。

## 8. 修改文件与原问题对应

阶段 A 提交 **0877b4a**，只有下列六个文件；未修改普通 `CarSettingsController`、命令表、帧编码、TTP 或平台实现。

| 文件（相对于仓库根） | 修改原因和内容 | 行为/协议边界 |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CustomSoundGateway.kt` | 原独立 Controller 的六类异步操作无身份隔离；124 起增加身份、串行写、局部合并、取消传播、查询有效性和独立状态；147/177/355/360/371/380/393 为各入口，406/428 为取消/释放 | 共享业务状态调整，不改 Gateway 接口参数和远端协议 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt` | 357 起音效多段请求和 686 起轮询加入边界校验、接受事实；710 的音效专用包装不影响普通设置；部分查询失败不再制造有效默认值 | 请求路径、参数、写入布尔兼容规则保持；加载有效性收紧 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt` | 109 页面释放，974 关闭管理，995 起区分查询与本次选择，未知不假装关闭；局部弹窗和试听随车辆身份清理 | 继续两层 App* 双主题；不改上传/下载/试听实现 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt` | 318/321 对替换释放和 VIN/车型变化接入 Controller | 生命周期变化，不改导航或车辆控制 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/NinthRoundCustomSoundTest.kt` | 29 项 Fake Gateway + 可控 deferred/可变 VIN；含不响应取消请求 | 无账号、网络、BLE、车辆 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/NinthRoundCustomSoundGatewayTest.kt` | 11 项 FakeTransport，检查多段请求、轮询、原返回语义及部分读取 | 假响应经过生产解析；不是云端端到端验证 |

本报告另作 docs 提交，不混入功能提交或用户已有文档。

## 9. 实际验证与覆盖

所有命令均使用 `$env:GRADLE_USER_HOME = 'C:\Users\DozenOregano696\.gradle'` 和 `--offline`。

| 实际执行 | 结果 | 层级 |
| --- | --- | --- |
| `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm` | BUILD SUCCESSFUL | BUILD_VERIFIED；测试源码编译不等于执行 |
| `:androidApp:assembleDebug :desktopApp:build` | BUILD SUCCESSFUL；Desktop test 为 NO-SOURCE | BUILD_VERIFIED；不是 UI 运行验证 |
| 过滤 `:shared:jvmTest --tests 'top.ghhccghk.yadea.carsetting.NinthRoundCustomSoundTest' --tests 'top.ghhccghk.yadea.carsetting.NinthRoundCustomSoundGatewayTest' --tests 'top.ghhccghk.yadea.carsetting.EighthRoundValueStateTest' --tests 'top.ghhccghk.yadea.carsetting.EighthRoundStateIsolationTest'` | 最终 58 项通过，失败/错误/跳过均 0；分别 29/11/11/7 | UNIT_TESTED，仅这些纯本地过滤目标 |
| `git diff --check` 与精确 pathspec 检查 | 功能提交前通过；文档提交前再检查 | 静态/Git 检查 |

最终 XML 证据位于 `shared/build/test-results/jvmTest/TEST-*.xml`，仅为本地构建产物，不提交。

| 测试范围 | 已执行的断言 |
| --- | --- |
| 六类请求跨 VIN | refresh、enable、apply、delete、rename、add 旧响应不写新车；add 不启动旧后续请求 |
| 乱序/竞争 | 同 VIN 最新 refresh 生效；写入使旧刷新失效；旧 finally 不清新 busy；写预留同步生效 |
| 取消/生命周期 | Gateway 取消传播；启动前取消释放标记；dispose 拒绝新操作；取消记录与失败区分 |
| 不能撤回的旧请求 | NonCancellable 假请求结束前保留占用；同 VIN 车型切换仍隔离；返回原 VIN 不能绕过；重新查询失败不解锁 |
| 状态职责 | 失败不覆盖云端值；成功不制造车辆选择或 serverStatus；明确接受与原始服务端码分开；成功后受保护刷新 |
| 查询部分失败 | 拒绝/缺 data 不制造已知值；缺分类不能认证旧选中；可用字段保留，失效字段不宣称已知 |
| 局部资源变更 | rename 只更新匹配资源/分类名；add 后刷新一次，刷新失败不抹除已记录提交结果 |
| 前轮回归 | 第八轮独立实际值/草稿和异步隔离两组 18 项；不是全部受保护功能逐一真机验证 |

未执行完整 jvmTest、真实云端或车辆测试。没有标记 RUNTIME_VERIFIED / VEHICLE_VERIFIED；iOS 未构建。中间一次夹具失败及修复见第 6 节。

## 10. 未解决问题与边界

1. **PARTIAL**：Gateway 历史布尔轮询语义仍存在“缺 id/缺 status 可 true”，本轮仅防止其被误报车辆成功；服务器业务闭环须另取证。
2. **UNKNOWN/BLOCKED**：没有自定义音效的可靠车辆回读证据，云端查询记录与服务器状态码都不等于车辆物理应用。取消也不能撤回已发出的远端异步工作。
3. **PARTIAL**：取消后的重新查询只是重新建立当前会话的云端快照，不证明远端任务终止；远端可能继续异步变更。
4. **PARTIAL**：Controller 按现有 UI featureScope 的串行入口使用，没有新增跨线程锁框架；任意多线程并行调用与真实 UI 压力未运行验证。
5. **PARTIAL/BLOCKED**：14/64/89/51 只读证据差异和后续建议见第 7 节，未混入 Stage A 修复。
6. **未验证**：Miuix/Material3、深浅色、真实页面重建、iOS、云端端到端、实车状态均未验证；以前用户实测基线不等于本轮 Agent 实测。

## 11. Git 交付

- 初始 `master / 6843110`，无 staged / unstaged。
- 功能与测试：`0877b4a fix(custom-sound): isolate vehicle requests and cloud submission state`，精确六文件，范围见第 8 节。
- 审计文档单独提交本文件，其最终哈希见本轮交付消息及 Git log，避免自引用哈希。
- 不推送远端，不清理日志、APK、截图、反编译目录或原有审计文档；原有 untracked 保留。本轮报告提交后复查 tracked staged / unstaged 为空。
