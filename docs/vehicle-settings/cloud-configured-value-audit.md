# 云端配置值 UNKNOWN 专项审计与修复

日期：2026-11-11。仓库：`F:\android app code\toupin`。

## 1. 基线与验证边界

- 分支 `master`；实际起始 HEAD：`d46686b07f147789a77afbec951f9833713e1443`。
- 修改前检查 status、branch、HEAD、cached diff、unstaged diff；原 staged/unstaged 均为空。
- 阅读 AGENTS.md、逆向技能、原有缓存恢复报告与实际调用链。先只读审计，再修复。
- 未登录、未主动访问真实账号/云端、未运行正常 App、未执行 BLE/车辆写入。
- 用户随后在本对话提供了**刷新后的接口响应**；它是用户提供的响应证据，不是 Agent 自行实时抓包。测试只重放无身份标识的字段子集和合成数据，不保存完整响应中的用户/车辆 ID、tid、资源 URL。
- 没有新增存储模型、平台依赖或持久化系统；未修改命令、帧编码、Writer 分流、密码状态机或音效 Controller。

原有以下 12 个 untracked 路径均保留，不提交、不清理；本地 Fake 测试可通过项目既有日志器追加测试日志，日志不属于交付：

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

## 2. 刷新响应证据

用户提供的响应 envelope 为 `success=true`、`code="000000"`，实际配置字段位于 `data` 根对象，不是 `bikeConfigVos` 条目内。

| 对象 | 原始字段/类型/值 | 元数据 | 修改前丢失位置 | 修改后 |
| --- | --- | --- | --- | --- |
| 电子围栏开关 | `data.distanceOn`，JSON number，`1` | `confId="10"`，`confName="电子围栏"`，parentId=1002，sort=81，izLab=1，confType=0，未下发 isAuth | **没有丢失**：Gateway→boolValue→configuredValue→displayKind 已可达；Row/Sheet 却固定强调车辆当前值 UNKNOWN | 继续直接 Switch，来源显示“云端配置：开启（非车辆回读）” |
| 电子围栏半径 | `data.distance`，JSON number，`200` | 同一 confId，拆成 `cloud:10:distance`，KMP channel=IOT | Gateway values 和 item.kind 已拿到数字；configuredValue 只支持 Toggle，返回 null，导致 displayKind=null | 严格验证后建立独立 configuredValue，显示 200 m |
| 密码开关通用展示记录 | `data.passwordUnlockEnable`，JSON number，`1` | 用户响应包含 confId=25 | VALUE_KEYS 漏键，已有 Mapper 永远拿不到字段 | 补白名单；不改独立密码业务 |
| 其他数值 | chargeWattVal=350、chargeUlVal=100、highestSpeedLimit=0、bleDistance=2、seatHeatingLevel=2 | **有字段不代表本次清单包含对应控件**；如样本没有 48/62 | 同样没有数值/Choice configuredValue 映射 | 只有动态清单确实生成对应项时，才建立展示值 |

不能将以上响应外推为所有 VIN/车型/未来响应均包含这些字段。实际修改前后页面截图、同一设备连续刷新前后的 StateFlow 没有留存，属于 `PARTIAL`；Fake 明确模拟了“初始无清单→调用 refresh→响应解析→UI 展示”的全过程。

## 3. 原 App 原始证据

Smali 根目录：`F:\android app code\YadeaHook\temp_smali`。以下位置均重新阅读原文件，而非只引用历史报告。

| 文件及位置（相对 Smali 根目录） | 事实 |
| --- | --- |
| `com/yadea/smartmoto/common/bean/CarSettingBean.smali:75,77,331,340` | distance、distanceOn 为 int 字段，getter 直接返回字段 |
| `com/yadea/smartmoto/vehicle/viewmodel/VehicleSettingViewModel$getBikeConfigs$1.smali:185` 附近 invokeSuspend | 从当前车辆取 VIN，请求 selectBikeSettingByVin |
| 同目录 `VehicleSettingViewModel$getBikeConfigs$1$a.smali:81` onSuccess | 发布 CarSettingBean；另将 bikeConfigVos 交给 ViewModel 处理 |
| `VehicleSettingViewModel.smali:11984` Q6、`:12242` | CarSettingBean.distanceOn == 1 回填 x；q3():30072 返回相应 Boolean LiveData |
| `vehicle/activity/UserSelectRangeActivity.smali:863` 附近 getSettingConfigId | 页面配置 ID 为 10 |
| 同文件 getSelectScope、isRangeChanged；`UserSelectRangeActivity$observe$2.smali` invoke | 选择值取 SeekBar.getProgress()；与云端 distance 比较；云端 distance 直接传给 curProgress |
| `vehicle/databinding/UserActivitySelectRangeBindingImpl.smali:346,501,512,896` 附近 | 开关读 q3；半径读 g6→getDistance；显示经 checkMetricSystemM；使用真实 Switch |
| `base/utils/MetricSystemUtils.smali:549` checkMetricSystemM | 公制直接拼接 m，英制走既有转换；不是根据字段名猜单位 |
| `s20.smali:721` 附近 e、`:743,788,801` | 保存 distance，构造 distanceOn、distance 字符串参数；distance 无额外倍率 |
| `UserSelectRangeActivity.smali:100` 附近构造器、initView | MINE_RADIUS=0xC8（200），MAX_RADIUS=0x7D0（2000），分别 setMin/setMax |
| `VehicleSettingViewModel.smali:12284,12382,12394,12432` Q6 | capacity、chargeWattVal、chargeUlVal、highestSpeedLimit 分别进入独立 UI 属性，不要求先有 TTP |

原 App 对未反序列化 int 的默认行为不作为 KMP 已知值证据；KMP 缺失/null/非法字段仍保留未知。原 App chargeUlVal 分支还存在自己的回退值，本轮同样不采用默认值建立 configuredValue。

**范围差异保留 PARTIAL**：原 App 围栏 200..2000，现有 KMP 控件 50..1000、steps=18。本轮按任务保留既有范围、步进和写入规范，不扩展范围或断言该范围就是固件合法范围。超出现有 KMP 范围的云端数字仍在 rawConfigJson/values 中，但不进入可编辑 configuredValue；例如 1500 不能因此宣称服务端非法。后续需要独立核对不同车型和交互限制。

## 4. 实际刷新链路与触发时机

以下源码位置相对仓库，K=`shared/src/commonMain/kotlin/top/ghhccghk/yadea`。

```text
MainScreen 当前 VIN/token + Controller/车型身份
 -> CarSettingsController.refresh() (K/carsetting/CarSettings.kt:366)
 -> YadeaCarSettingsGateway.load(vin) (CarSettingsGateway.kt:292)
 -> POST vehiclebase/bike-setting/selectBikeSettingByVin
 -> 解码 data / bikeConfigVos / VALUE_KEYS
 -> CloudCarSettings(configs, values, rawJson)
 -> CloudCarSettingMapper.toItems（动态条目、权限、分组）
 -> configuredValue(cloud,item)（严格映射实际字段）
 -> CarSettingsState.valueStates (CarSettings.kt:439)
 -> displayKind / Row / Sheet
```

| 触发 | 当前实现 |
| --- | --- |
| 首次装配 MainScreen 且 token/VIN 有效 | MainScreen.kt:432 附近 LaunchedEffect 调 refresh |
| VIN / token / Gateway / Controller 变化 | 同一 effect 重新执行；VIN 变化先 onVinChanged 清会话 |
| 同 VIN 车型身份变化 | MainScreen.kt:299 的 carSettingsModelKey 使 Controller 替换，重新加载；不是复用旧车型配置 |
| 用户主动刷新 | CarSettingsScreen.kt:238，实际调用 Controller.refresh，不是只读 StateFlow |
| 同一 Controller 页面重新进入 | 没有独立重新进入即刷新钩子；仍可手动刷新 |
| 网络恢复 | 没有独立网络恢复自动重试钩子；保留旧值/错误，可手动刷新。`PARTIAL`，本轮不增加网络监听系统 |

已在进行的同 VIN 加载由原 loading guard 串行化，第二次调用返回 false，不启动另一个请求；随后可再刷新。VIN/代次不一致的旧返回被拒绝，A→B→A 同样由代次隔离。未修改请求签名、token 来源、网络路径、写入 API 或 BLE 流程。

## 5. 状态与展示规则

**没有重建或改变状态字段定义**。复用 CarSettingValueState；没有新增 ValueSource 枚举来冒充 LIVE_TTP。

| 字段 | 本轮规则 |
| --- | --- |
| vehicleValue | 仍只由有效实时 TTP 建立；云端刷新从不赋入配置值 |
| cachedValue | 仍为 CACHED_TTP 历史观察，不作车辆确认 |
| configuredValue | 只接受白名单中的根对象标量，严格布尔或整数及现有控件范围/选项检查；缺失、空、非法不默认、不 clamp 成有效值 |
| draftValue | 同 VIN、控件形状兼容的草稿跨刷新保留；显式取消仍生效 |
| submittedValue | 保留本次选择记录，不能由旧云端记录覆盖；不是车辆已确认值 |
| writeState / confirmationState | 显式刷新仍废弃旧操作身份；未结束的 UI 等待标 CANCELLED，已取得的 CLOUD_ACCEPTED/LOCAL_UPDATED 回执不丢失；不声称撤销服务端请求 |

displayKind 原优先级保持：草稿→提交目标→车辆观察→TTP 缓存→云端配置。冲突时各来源均保留，Row 和 Sheet 的来源摘要同时展示车辆/缓存/云端事实，未凭空发明跨来源时间戳来判定谁更“新”。旧云端值不会盖掉有效 TTP 或用户目标。

展示修复：

- 有云端配置、没有车辆观察时，显示“云端配置：…（非车辆回读）”，不再固定给整个配置贴“车辆当前值：UNKNOWN”。真正没有任何配置/观察时继续 UNKNOWN。
- 存在车辆观察但已失效时，仍如实显示历史/UNKNOWN，不把云端值当成新回读。
- 数值列表和 Sheet 共用标签函数；完全未知时标签为“状态未知”，Slider 的布局占位数字不再被展示为已知值。用户实际拖动后才建立草稿。
- Switch 类型与一次 targetChecked 回调不变；权限、连接、电源门控不变；不恢复被移除的“云端车辆设置项/车联网”泛化副标题。

刷新协调：同 VIN 加载中/失败/取消均保留已有配置；成功则用新响应更新 configuredValue。成功响应缺少实际值时不能继续声称旧值是新配置；缺少配置项时不自行创造该项。控件类型、范围或选项改变时，不兼容的草稿/提交记录不跨新清单保留。

## 6. 纯云端与双通道审计

`channelOf`/IOT_IDS 位于 CarSettingsGateway.kt:1642、1880。当前动态 Mapper **没有生成 BOTH 枚举的分支**；不能把它与实际同时支持 Cloud Update Builder 和 VehicleSettingCommands 混为一谈。真正写入分流由 VehicleSettingCommands.kt:52 的 CloudCarSettingWriter 决定：已连接 BLE 可先尝试已确认映射，否则云端。两者本轮均不改。

| IOT_IDS 中的项目 | 云端展示审计结果 |
| --- | --- |
| 8 | 独立报失入口，不将入口伪造为 Boolean |
| 9、10、16、24、40、75、76、82、91 | 对应 alarmOn、distanceOn、dumpAlarm、emergencyContact、colorEggSoundEffect、tamperProofIotEnable、positioningFunc、lowBatTelWarning、sosEmergencyCall；原有 Boolean 路径保留，已测试 0/1 |
| 10:distance | 新增严格 distance→数值 configuredValue |
| 47 / 47:enable | 瓦数新增 chargeWattVal 路径，开关保留 chargeWattSwitch；两来源可并存 |
| 48 | 新增 chargeUlVal 路径；缺字段不默认 100 |
| 60 | 容量值按既有 BatteryCapacityOptions 查表；没有 capacitySwitch 字段证据，不从 capacity 推断开关 |
| 61 | 新增 highestSpeedLimit；0 仍为不限速，非零现有最小值保护不变 |
| 62 / 62:level | 保留 seatHeating；档位复用既有 seatHeatingLevel 1..3→index 0..2 映射，不拿开关代替档位 |

其他有明确既有字段的双能力/云端配置控件：7、13、15、17、25、52、53、59、77 的 Boolean 映射保留；52:distance 增加 bleDistance 的严格展示路径。样本中 52 有云端字段，但当前映射并不能据此宣称其有 BLE 命令；测试没有编造 BLE 支持。47 的云端和 BLE 双能力与冲突回读由 Fake 单独覆盖。

共性问题是 **configuredValue 只支持 Toggle + UI 固定 TTP UNKNOWN 文案**，不是所有项目都完全丢弃云端，也不是 BLE 连接后 TTP 会清空配置。applyVehicleStatus 原本就是独立 copy vehicleValue/readbackState，未发现它清除 configuredValue，予以保留并新增测试。

以下不擅自接入：modeOn/shakeOn/nfcOn 等字段不能凭名称对应 ID；4/58 的速度字段与上限/当前速度含义需要完整独立证据，未新增 configuredValue；brightnessAdjustValue 等非本轮已闭环字段仍保留原状。样本未下发的 48/60/62 等项目不硬编码显示。未获取另一车型真实响应的差异保留 `PARTIAL/BLOCKED`。

## 7. 修改文件

| 文件（相对仓库） | 必要修改 |
| --- | --- |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt | 数值/选项的独立 configuredValue；补 passwordUnlockEnable；围栏缺失/非法值不冒充默认 200 |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/network/JsonSupport.kt | 增加根对象标量读取，避免嵌套元数据冒充实际字段或正则截取数字前缀；只将本设置 load 切换到新方法，其余现有解析调用不变；无新依赖 |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt | 同 VIN 兼容草稿、目标和回执保留；原 loadGeneration/VIN/旧 finally 失效保护保留 |
| shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt | Row/Sheet 来源摘要一致；未知数值标签；保留直接 Switch、双主题语义控件和状态反馈 |
| shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CloudConfiguredValueRefreshTest.kt | 30 个本地刷新链路及来源/并发测试 |

ID 14/64/89、ID 51 RESULT_ONLY、帧编码、音效、密码状态机、持久缓存后端、平台实现无 diff。历史测试文件最终也无 diff。

## 8. 实际测试

先 compileTestKotlinJvm，再精确过滤执行。Fake Gateway 委托真实 YadeaCarSettingsGateway 解码，但 HttpTransport 始终为内存 Fake，baseUrl 为 example.invalid，token 为假常量；Fake Writer 只记录参数。没有构造实际网络适配器/车辆 sink。

新增 30 测试覆盖：刷新后围栏 Boolean/数值、缺值/非法值/空值/溢出/小数、根字段与嵌套元数据隔离、数值单位/范围/提交参数、独立多来源、无 BLE 时展示、TTP 缺失不清云端、有 TTP 时优先且来源不混、同 VIN 刷新等待/失败/成功缺字段、草稿/目标/回执保留、旧操作及 finally、串行刷新、VIN A→B→A、Controller/车型替换、取消传播、权限、列表与编辑面板文案、字段白名单遗漏、20 组既有 Boolean 映射的 0/1。

最终执行的精确类过滤如下，均 `failures=0, errors=0, skipped=0`：

| 类（top.ghhccghk.yadea.carsetting） | 实际通过数 |
| --- | --- |
| CloudConfiguredValueRefreshTest | 30 |
| SettingsCacheRecoveryTest / SettingsSummaryRecoveryTest | 11 / 6 |
| EighthRoundValueStateTest / EighthRoundStateIsolationTest | 11 / 7 |
| CarSettingsStateIsolationTest | 3 |
| EleventhRoundUiSessionTest / EleventhRoundUiGatingTest / EleventhRoundSliderIntegrationTest | 7 / 8 / 10 |
| TenthRoundAlarmSensitivityTest / TenthRoundAdaptivePushTest | 10 / 13 |
| NinthRoundCustomSoundTest / NinthRoundCustomSoundGatewayTest | 29 / 11 |
| CloudCarSettingsApiSemanticsTest / CloudCarSettingWriterTest / CloudSettingStatusPollingTest | 3 / 4 / 6 |
| Phase4CarSettingsTest | 6 |
| 合计 | **175（新增 30 + 历史 145）** |

精确运行方式：`:shared:jvmTest` 后逐一提供上述完整类名的 `--tests`，加 `--offline`；没有无过滤运行完整 jvmTest。

开发中的初次新测试有 5 个夹具断言失败：错误假定只有围栏两项（实际既有 VS-014 固定按键入口也存在），以及错误假定 52 有 BLE 映射；已按实际代码修正测试范围，未删除功能来迎合断言。另一次候选“写入时禁止刷新”破坏第十轮已有显式重载契约，已弃用该实现，保留原代次隔离；没有改写历史测试来通过。最终仅新测试文件有测试 diff。

## 9. 实际构建

全程 GRADLE_USER_HOME=`C:\Users\DozenOregano696\.gradle`，离线模式，不切换缓存或下载依赖。

| 任务 | 结果 |
| --- | --- |
| :shared:compileKotlinJvm | BUILD_VERIFIED |
| :shared:compileKotlinJs | BUILD_VERIFIED |
| :shared:compileKotlinWasmJs | BUILD_VERIFIED |
| :shared:compileTestKotlinJvm | TEST_SOURCE_COMPILED，随后精确执行测试 |
| :androidApp:assembleDebug | BUILD_VERIFIED |
| :desktopApp:build | BUILD_VERIFIED；desktopApp:test 为 NO-SOURCE，不算测试通过数 |
| iOS | 未构建；Windows 主机不提供 Apple SDK，未增加平台专属代码 |

共享四任务最终组合执行成功（15 秒）；新增测试补齐后单独测试源码编译成功（2 秒）；最终过滤测试成功（5 秒）；最终 Android/Desktop 组合成功（17 秒）。现有 deprecated/unchecked/expect-actual 警告未做无关修改。

## 10. 状态、剩余风险与提交

- `STATIC_FLOW_VERIFIED`：上述解析、映射、状态协调、Row/Sheet 共享展示链路。
- `UNIT_TESTED`：175 个内存 Fake 测试实际执行通过；不是云端或车辆验证。
- `TEST_SOURCE_COMPILED` / `BUILD_VERIFIED`：如上逐项记录。
- `RUNTIME_VERIFIED` / `VEHICLE_VERIFIED`：**未执行**。未启动 Android/Desktop 正常 App；无真实设备双主题视觉对照、车辆回读或真实写入结果。
- `PARTIAL`：原 App 200..2000 与 KMP 50..1000 范围差异；暂不扩大操作范围。无网络恢复自动刷新/同 Controller 重进自动刷新，本轮不扩展刷新策略。
- `BLOCKED`：样本外缺字段、未闭环字段/配置 ID 的含义、另一车型服务端返回、没有事务关联的车辆执行确认；不得由默认值或字段名字补造。

功能提交：`738aeefc9123da178881946bbb7baa4d32089db0`，精确范围为第 7 节的 5 个源码/测试文件。

本文单独 docs 提交，提交哈希由 `git log -1 --format=%H -- docs/vehicle-settings/cloud-configured-value-audit.md` 可追溯，并在最终交付中列出，避免在文档自身写入自引用哈希。提交前分别检查 diff --check/cached --check，未全量 add、reset、clean、改写历史或 push。文档提交完成后重新检查 HEAD/status；预期 staged/unstaged 为空，原 12 项 untracked 保留，实际终态在交付中确认。
