# 第十轮车辆设置映射纠错与原 App 行为回归

日期：2026-11-10。仓库：`F:\android app code\toupin`。原始证据：`F:\android app code\YadeaHook\temp_smali`。

## 1. 当前基线及安全边界

- 开始时实际分支 `master`，HEAD `c52b2f7120f4b856faae13fa917b40cb5573549f`，staged/unstaged 均为空；不假定停留在前轮功能提交。
- 第九轮功能提交确实存在：`0877b4a2b9e31ea8faa575522fcaaac7e674670d`。实际文档提交为上述 c52b2f7，文件 `docs/vehicle-settings/ninth-round-custom-sound-and-legacy-audit.md`，通过 Git log/show 核对，不从报告猜测哈希。
- 已读根 AGENTS.md、Android 逆向技能、第八/九轮报告及相关历史。重新检查原 Smali/当前调用链后才修改。
- 范围只限 14、64、89 的字段、UI 模型参数和目标命令转换；没有新增车辆命令、修改帧或普通状态框架。
- 全程没有真实登录、云端/车辆/BLE 操作、OTA、音效上传删除或远端推送。aapt2 只读取已有 APK 资源，不运行 App。

原有 untracked 全部保留，不移动、不清理、不加入提交：

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

## 2. 结论总表

| 项目 | 修复前真实问题 | 本轮处理 | 验证边界 |
| --- | --- | --- | --- |
| ID14 | 读取 alarmWarningMode；14/20 共用八档；进度直接作为 0..7 命令参数 | 解析 alarmSensitivityStatus；raw0/1/2→进度0/50/100→tick/参数0/1/2；非法保持未知 | STATIC_FLOW_VERIFIED、UNIT_TESTED、BUILD_VERIFIED；未实车 |
| ID64 自适应 | 独立 helper 未被真实回填调用；目标开关极性与父点击语义相反 | raw==1 开；目标开写1、关写0；保留主开关独立 | 同上 |
| ID89 自适应 | 统一 flagOrNull 误判 raw1/raw2；目标极性相反 | raw==2 开；目标开写2、关写0 | 同上 |
| 64/89 档位 | Mapper/写入0..2，回填1..3；写入静默钳位 | 原布局证实三档0..2，回填修正；拒绝非法/小数命令输入 | 两个页面范围已证实；完整固件范围仍 UNKNOWN |
| ID51 | 操作结果位不能充当持续配置或本次事务回执 | 只保护性复核，零代码修改 | STATIC_FLOW_VERIFIED + 既有本地测试；事务关联 BLOCKED |
| 第八/九轮隔离 | 映射变更不能绕过 VIN/操作身份边界 | 保留原实现，定向回归通过 | Fake 测试，不等于云端/车辆身份验证 |

详细证据分别在本目录 `id14-alarm-sensitivity-mapping.md`、`id64-id89-adaptive-push-mapping.md`。两份文档包含完整文件/方法/行号、原始值/UI/tick/写入/回读表、已知与未知边界，不以本总表替代原始证据。

## 3. 本轮新增的关键证据

1. **14 解析原语纠正**：`l76.smali:9387` 实际为 C(data,0x1C,2)，不是任务文字中的 G。C 为三位并以7无效，G 为两位并以3无效，不能根据名称替换。alarmWarningMode 在同文件9764来自另一个偏移0x33。
2. **原布局补证**：已有 yadea.apk 的资源表中，`0x7f0d00ed`→res/EbG.xml（报警），`0x7f0d007d`→res/uAX.xml（64），`0x7f0d007e`→res/4h4.xml（89）。三者明确 ticks_count=3；后两者另明确 min0/max2。tick 文案数组为低/中/高。与原 Smali getLayoutId/R/Binding tag 一致，不是截图或字段容量推断。
3. **当前/目标状态区分**：64/89 父容器监听、子 CheckBox clickable=false、$d不翻转、onCbClick读已有 checked，共同证明当前关→目标开时分别写1/2。旧“勾选写0”不能当 target=true 的极性证据。
4. **未伪造默认回读**：原 App 部分无效值会落到0/false。本轮遵守用户 UNKNOWN 约束，不把展示兜底移植成车辆实际值；失效快照不建立观察或新确认。

APK SHA256：`329BA5ECF012E71E70A1496F139C21822E5D64CBB96DDFAA59A9896E474AE685`。使用本地 SDK build-tools 35.0.1 的 aapt2，只读资源；没有把 APK、日志、资源树或 Smali 加入 Git。

## 4. 修改文件及提交范围

所有路径相对于仓库根。本轮共八个代码/测试文件、三个新增文档。UI 页面、平台实现、帧编码、密码、X1 和独立音效 Controller 文件均未修改。

| 完整文件路径（仓库相对） | 原因、改动及证据 | 提交 |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt` | AlarmSensitivityMapping 与14回填；64/89独立读写映射、接入helper、档位回填；VM l2/F/G/H和原布局 | 两个功能提交 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt` | 仅拆开14/20的Slider模型；原报警布局三档及VM进度 | ID14 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt` | 14进度转tick；64/89目标D0参数与D1非法拒绝；原监听及qg分支 | 两个功能提交；命令ID不变 |
| `shared/src/commonMain/kotlin/top/ghhccghk/yadea/vehicle/TtpInfo.kt` | 仅末尾新增alarmSensitivityStatus默认无效值及C解析；l76原调用 | ID14；其他位域不变 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/EighthRoundValueStateTest.kt` | 仅改14的原始数据夹具字段，保留原状态断言 | ID14 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/TenthRoundAlarmSensitivityTest.kt` | 10项纯内存解析/映射/状态/VIN测试 | ID14 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsControllerTest.kt` | 仅纠正已有64/89目标D0参数的四个断言 | 分变体 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/TenthRoundAdaptivePushTest.kt` | 13项独立变体/档位/状态/VIN/配置换代测试 | 分变体 |
| `docs/vehicle-settings/id14-alarm-sensitivity-mapping.md` | 原App完整链、布局/转换/UNKNOWN与验证记录 | 文档 |
| `docs/vehicle-settings/id64-id89-adaptive-push-mapping.md` | 原App双变体/父点击/布局证据及未覆盖边界 | 文档 |
| `docs/vehicle-settings/tenth-round-mapping-repair.md` | 基线、安全、总验证与交付 | 文档 |

这些变更涉及业务值映射和已存在命令的参数选择，不涉及新的协议、帧长、位位置或传输行为。共同状态机保持原样。

## 5. ID51 保护性复核

原链重新核对入口：`com/yadea/smartmoto/vehicle/view/BleMeterTimeSyncActivity.smali` getSettingConfigId/ onSwClick；`VehicleSettingViewModel.smali:23115-23165,28292` 的 D2/k2；`l76.smali:11530,11539`。syncTimeResultStatus 是供原页面展示的操作结果位，而非可持续配置的车辆实际值。

当前 KMP 位置相对于 commonMain 的 `top/ghhccghk/yadea`：

- `vehicle/TtpInfo.kt:704` 仍 G(data,0xAC,0)，未改字段语义。
- `carsetting/CarSettings.kt:896` 仅保留兼容 kind 回填；919 的 `item.configId != "51"` 仍阻止它进入实际观察与普通等值确认。reconcileObservation 在651附近还校验live、operationId和新快照sequence；本轮未动这些规则。
- `carsetting/VehicleSettingCommands.kt:475` 保持原C1 OPEN/CLOSE及参数；VehicleSettingFrameEncoder未修改。
- 原云端 result true 只进入结果确认层，不产生 VehicleSettingValue.Known；writer回执只记录CLOUD_ACCEPTED/LOCAL_UPDATED。51的结果位不能建立车辆实际值，也不能依靠旧值与目标相等确认本次操作。
- `SettingBaseActivity.smali:1032` beginStatusLimit 和原页面延迟事件是UI行为；`BleBicycleCommandManager.smali:8913` getIncreaseByteHexStr 是FF填充，不能当事务序列号。本轮不添加任何基于延时的事务关联。

`EighthRoundValueStateTest.timeSyncResultFlagIsNotActualSettingOrTransactionConfirmation` 本轮实际通过；其余身份/回执保护通过18项第八轮测试及静态diff复核。没有为51伪造事务ID或新增回读确认。真实新结果归属本次VIN/操作的证据仍 **BLOCKED**。

## 6. 回归保护及限制

| 保护范围 | 实际检查 |
| --- | --- |
| 14以外报警 | 原alarmWarningMode/volume解析保留；新增测试验证兄弟位和ID20八档不受影响 |
| VS-013 | 0x26/0x27能量回收解析方法定向测试通过；相关映射/命令diff无变化 |
| VS-014 | customKeyFunctionsReadBackFromCustomFunctionBits通过；1005映射diff无变化 |
| VS-015 | 本次允许的64/89映射纠错；原两项测试接入真实回填验证，不以历史helper存在代替 |
| VS-016 | SixthRoundReviewTest两项Fake密码状态/换VIN通过；密码代码和0x82编码未变 |
| X1 001/002/003 | FifthRoundX1MappingTest三项及缺配置只读测试通过；映射不变 |
| 普通开机距离 | FifthRoundDistanceRegressionTest的VIN/UNKNOWN回归通过；0..7不变 |
| 氛围灯 | Fake本地选择不能认证车辆值测试通过；0xF7和未知回读逻辑未改 |
| 普通定时关机 | scheduledShutdownTimeMatchesSeekBarSemantics通过；命令/映射未改 |
| 八轮状态隔离 | ValueState 11项、StateIsolation 7项通过；本轮只改14夹具，不改状态更新算法 |
| 九轮41/88隔离 | Controller29项、FakeTransport11项通过；CustomSoundController和请求链未改 |
| 既有编码 | GoldenFrame三项本地字节比较通过；Encoder整个文件相对基线无diff |

以上是本地回归/静态保护，不是全部功能完整套件或先前用户实车结论的重新认证。

## 7. 实际构建与测试

全部使用既有缓存 `C:\Users\DozenOregano696\.gradle` 和 `--offline`，没有临时缓存、重新下载依赖或修改构建配置。

| 实际任务 | 结果与层级 |
| --- | --- |
| `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline` | 两阶段分别通过：ID14阶段29s，64/89阶段23s；BUILD_VERIFIED + TEST_SOURCE_COMPILED |
| `:androidApp:assembleDebug :desktopApp:build --offline` | 两阶段分别通过：49s、30s；BUILD_VERIFIED。Desktop test NO-SOURCE，不计作测试通过 |
| 下表明确过滤的 `:shared:jvmTest --tests ... --offline` | 最终98项实际通过，failures/errors/skipped全部0；UNIT_TESTED，仅Fake/内存数据 |
| `git diff --check` | 每次功能提交前通过；最终提交/基线diff另复查 |

最终过滤选择如下，类名统一前缀 `top.ghhccghk.yadea.carsetting`，TtpInfoParserTest除外：

| 类 / 方法 | 实际执行数 |
| --- | --- |
| TenthRoundAlarmSensitivityTest | 10 |
| TenthRoundAdaptivePushTest | 13 |
| EighthRoundValueStateTest | 11 |
| EighthRoundStateIsolationTest | 7 |
| NinthRoundCustomSoundTest | 29 |
| NinthRoundCustomSoundGatewayTest | 11 |
| CarSettingsControllerTest：adaptivePushUsesVariantSpecificSwitchValuesAndZeroBasedLevel、adaptivePushCommandsUseVariantSpecificParameters、customKeyFunctionsReadBackFromCustomFunctionBits | 3 |
| FifthRoundX1MappingTest | 3 |
| FifthRoundDistanceRegressionTest | 1 |
| SixthRoundReviewTest | 4 |
| SixthRoundActivityBatchTest.scheduledShutdownTimeMatchesSeekBarSemantics | 1 |
| VehicleSettingGoldenFrameTest | 3 |
| top.ghhccghk.yadea.vehicle.TtpInfoParserTest：parse_energyRecoveryUsesSlideModeFields、parse_chargingDriveAndNavi_0x70_to_0x7A | 2 |
| 合计 | **98** |

执行前已检查选中目标使用Fake Gateway、FakeTransport、Fake writer/sink、CompletableDeferred或纯ByteArray；没有真实HttpTransport、账号或BLE设备。没有执行不带--tests的完整jvmTest。最终XML在 `shared/build/test-results/jvmTest/TEST-*.xml`，是未提交的构建产物。

中间验证记录：ID14第一次21项有1项新测试夹具失败，因附加项导致single()不成立；改为稳定条目ID查找后重新编译，21项通过。64/89首次综合96项通过，补充两项已有解析回归后最终98项通过；不累加重复运行作为额外测试数量。中间进度曾把手工加总误报106/108，交付前用PowerShell解析13份JUnit XML求和纠正为96/98；最终新增23项、历史回归75项。最终所有修复版本构建通过。

现有expect/actual、弃用API、unchecked cast等编译警告未顺手修复。**iOS未构建，App未启动，双主题/真机UI未运行，真实云端/BLE/车辆均未验证**，未标记RUNTIME_VERIFIED或VEHICLE_VERIFIED。

## 8. UNKNOWN / PARTIAL / BLOCKED

1. `UNKNOWN`：ID14 raw3..6和固件额外参数语义；14只开放原页面证实的三档。0..7编码容量不等于业务合法域。
2. `UNKNOWN/PARTIAL`：64/89完整固件合法范围、各档物理速度、其他未见布局/车型差异。已核实两个页面的0..2，不扩展到其他车型。
3. `PARTIAL`：原64/89完整连接/电源gating与KMP交互逐状态等价、两主题实际拖动和真实页面生命周期未运行验证；本轮不扩展协议/安全参数或改写这些限制。
4. `BLOCKED`：ID51本次事务关联证据不足，继续RESULT_ONLY保护；不修改其编码或结果状态规则。
5. `未验证`：真实车辆对0x5F/D0/D1执行与回读时序、真实云端端到端、iOS与UI。Fake回读匹配不是车辆确认测试。
6. 氛围灯UNKNOWN、自定义标定、数字车钥匙、音效协议等不属于本轮修复；均未借本次纠错开放未知功能。

## 9. Git 交付

| 完整提交哈希 | 内容 | 实际文件范围 |
| --- | --- | --- |
| `d7db57078836a15a52f385194f46b66365bc96be` | fix(car-settings): restore evidenced alarm sensitivity mapping | CarSettings.kt、CarSettingsGateway.kt、VehicleSettingCommands.kt、TtpInfo.kt、EighthRoundValueStateTest.kt、TenthRoundAlarmSensitivityTest.kt，路径见第4节 |
| `b8060c605f560cb8b5b6d524b0c51c7fd6844b40` | fix(car-settings): restore variant-specific adaptive push mapping | CarSettings.kt、VehicleSettingCommands.kt、CarSettingsControllerTest.kt、TenthRoundAdaptivePushTest.kt，路径见第4节 |
| 文档单独提交，哈希见最终交付消息/Git log | docs(car-settings): record round ten mapping evidence and regression | 仅本轮三份新文档，避免在文档里自引用自身提交哈希 |

每次用精确pathspec暂存/提交，没有全量git add，没有重置/清理/重写历史。代码提交后staged/unstaged为空，原有12条untracked状态保留；文档提交后再次复查并在交付消息报告最终HEAD与状态。不推送。
