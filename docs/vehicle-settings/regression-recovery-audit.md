# 车辆设置紧急回归修复

日期：2026-11-10。以用户最后的补充要求为准：删除指定 Summary 展示，而非恢复这些来源标签；实际功能必须保留。

## 1. 基线及安全

- 实际分支 `master`，修改前 HEAD `5254ed0ea8f7e4083e897289c263745c4bc60f38`。
- 开始和恢复上下文后检查 `git status --short`、分支、HEAD、cached/unstaged diff；原 staged、unstaged 均为空。
- 已阅读根 AGENTS.md、Android 逆向技能、第八至十一轮报告、第十轮映射文档及实际调用链。以 Git 和源码复核历史归因，不将用户反馈直接当成提交归因证据。
- 未 reset、clean、checkout、移动文件、全量 add、修改历史或 push。未启动正常 App，避免读取现有账号后自动刷新。
- 原有 12 项 untracked 均保留，不加入提交：

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

## 2. 先审计后修改：历史归因

比较基准：第十轮完成提交 `6aa719a177cbdad92881d6379428038ddd9b8a04`、第十一轮逐项提交与修改前 HEAD。

| 问题 | 实际证据和归因 | 处理 |
| --- | --- | --- |
| 持久 TTP 缓存未进入设置展示 | `af8d28f1bfd4a9859ab6c01910acf556b31477c7` 引入来源隔离；Controller 对非 LIVE_TTP 仅 invalidate 并返回，未建立历史展示值。`9117e939cc73228b2a84b5652b7375569f6fe2ab` 的 Row 只取 live+VALID 观察，云端配置值也被排除。均早于十一轮 | 恢复历史展示，不恢复缓存冒充实时观察 |
| Switch 变成开启/关闭菜单 | `git log -S 'if (observed == null) AppActionPreference'` 指向上述 `9117e939...`；未知 Toggle 经 ActionPreference→option Sheet。不是十一轮首次改变 | 所有普通 Toggle 无条件保留 Switch 类型，移除 Toggle 的选项 Sheet |
| 刷新期间清单消失，失败后为空 | `git blame` 修改前 CarSettings.kt:379、396 指向 `b243a59e1f8327f69593eec749aa6010776649ed`：refresh 先创建空 CarSettingsState，失败 clearForVin。早于十一轮。更早 f2299e4 的 refresh 保留旧清单 | 当前 VIN、当前 Controller 已加载的清单保留到刷新返回；失败仍显示并报错 |
| 十一轮车型 key | `0c1fa69c4884fe01df806ce1906ef3a226e26503` 确实会在车型身份变化时新建 Controller，从空清单重新加载；没有持久清单可复用时存在加载空窗，但这是隔离边界，不能无条件复用旧车型状态 | 保留 key 和重加载；不跨车型借用清单 |
| Summary 的云端占位、车联网标签 | 修改前 Gateway.summaryOf 与 Row 的 item.channel.label 仍存在；`git blame` 指向 `f2299e4b` 等早期提交，没有十一轮删除 Summary 的证据 | 按最新要求仅移除指定展示，不删除数据或功能 |

第十一轮各提交均存在：

- `c8aad6e35aa4d539056384aa8846be5a992b4795`：64/89 连接、电源、实时主开关门控；保留。
- `0c1fa69c4884fe01df806ce1906ef3a226e26503`：Controller/车型 UI key、会话 scope；保留。
- `d673ee38690e65807a9d33cf4064682463de8185`：三位置 Slider 草稿规范化和显式保存；保留。
- `9a0233b8e5ac34a474266ac981159cadb3e6d3c8`：旧响应隔离测试断言；保留。

结论：不是一个十一轮过滤条件造成四项问题。未发现十一轮按 UNKNOWN 删除云端配置项，不能强行归因。未经明确要求的 Toggle 类型变化已恢复，没有整轮回滚。

## 3. 原缓存链路及恢复边界

以下源码路径相对仓库 `F:\android app code\toupin`；K 表示 `shared/src/commonMain/kotlin/top/ghhccghk/yadea`。

```text
AppPreferences.vehicle_status_vin / vehicle_status_packet
 -> AppPreferences.readVehicleStatus(): CachedVehicleStatus(VIN,完整包)
 -> BleVehicleStatusSource.cacheRestoreJob: 匹配VIN、解码
 -> observation(origin=CACHED_TTP, VIN, sequence)
 -> MainScreen.updateVehicleObservation
 -> Controller: 原来丢弃；现在用原有字段转换建立 cachedValue
 -> StateFlow -> CarSettingRow / Sheet 的 displayKind
```

证据位置：

| 层 | 文件及位置 | 职责 |
| --- | --- | --- |
| 存储 key | K/settings/AppPreferences.kt:78、394、401 | 两个既有 key；只保存最近一次 VIN 和完整包，没有车型身份，也不是多车辆字典 |
| 平台后端 | androidMain/settings/SharedPreferencesPreferenceStore.kt；jvmMain/settings/JavaPreferencesStore.kt；iosMain/NSUserDefaultsPreferenceStore；jsMain/wasmJsMain LocalStoragePreferenceStore | PreferenceStore 抽象不变；未新建持久系统或改变 schema |
| 实际装配 | androidApp/src/main/kotlin/top/ghhccghk/yadea/MainActivity.kt:198 | cache=appPreferences、VIN Flow；生产装配未修改 |
| 缓存恢复 | K/vehicle/BleVehicleStatusSource.kt:67、84、88 | 原来已正常读取；不是 SharedPreferences 文件未读，而是下游展示丢弃 |
| 云端前保存 | K/carsetting/CarSettings.kt:697、741、961 | CACHED_TTP 保留快照，清单到达后回放；仅更新 cachedValue |
| 展示/编辑 | 同文件:194、219；K/ui/screens/CarSettingsScreen.kt:1460、1495 | 草稿/提交目标、车辆观察、缓存、明确云端开关配置依次取展示值；来源提示分开 |

具体规则：

1. `vehicleValue` 仍只来自有效实时观察。`cachedValue` 带 VIN、序号、CACHED_TTP、live=false；不会触发 reconcileObservation，也不会解除实时 UNKNOWN。
2. `configuredValue` 仅恢复已有 Mapper 明确读取、且云端真的返回的布尔配置；复用现有键及 boolValue，不把缺失字段当 false。不建立车辆观察或操作确认。
3. 同 Controller、同 VIN 已加载清单时，刷新不清空条目；失败/取消保留清单及历史展示，失败报错。成功以新 bikeConfigVos 替换条目、权限、分组和描述，删除云端不再下发的条目。刷新期间暂禁编辑/提交；新清单只保留与新控件类型/范围兼容的旧展示值。
4. 刷新仍使旧写身份失效并清除对应草稿/提交过程，不恢复旧操作成功。VIN 切换仍 clearForVin，旧 VIN 响应不获回填权限。
5. 缓存和云端记录仅为展示/编辑来源，车辆回读不一致时各自仍单独保存；草稿不会因 TTP 到达而被覆盖。
6. 没有发现独立的持久化 bikeConfigVos/普通设置清单缓存。首次启动没有清单时，先保留缓存包，等合法云端清单到达再映射；不能在完全离线首次启动时凭包创造设置项。已有同会话清单时，缓存展示不等待刷新完成。

## 4. 直接 Switch 恢复清单

原 UI 指第八轮替换之前的普通 Row Toggle 形状，不把它当成原 APK 所有独立页面均使用 Switch 的证据。修改前：有有效实时值时为 Switch，否则 Action+菜单。修改后：以下普通 Toggle 全部为 AppSwitchPreference，Miuix/Material3 原生分支共用同一个目标回调。

表中 `target` 是原生 `onCheckedChange(target)` 的**目标值**，只传一次，不再反转。完全未知、无缓存/配置/用户目标时，Switch 仍保留但禁用并标“状态未知，等待配置或回读”；底部 unchecked 只是禁用控件占位，未记录为已知 false。已有权限、安全确认、连接及电源限制仍生效。

| 设置项 ID/条目 | 原类型 | 修改前无实时值 | 当前类型 | 直接 Switch | 点击目标 |
| --- | --- | --- | --- | --- | --- |
| 1:enable | Switch | Action | Switch | 是 | target |
| 002:enable | Switch | Action | Switch | 是 | target |
| 003:enable | Switch | Action | Switch | 是 | target |
| 2 | Switch | Action | Switch | 是 | target |
| 3 | Switch | Action | Switch | 是 | target |
| 7 | Switch | Action | Switch | 是 | target |
| 9 | Switch | Action | Switch | 是 | target |
| 10 | Switch | Action | Switch | 是 | target |
| 11 | Switch | Action | Switch | 是 | target |
| 13 | Switch | Action | Switch | 是 | target |
| 15 | Switch | Action | Switch | 是 | target |
| 16 | Switch | Action | Switch | 是 | target |
| 17 | Switch | Action | Switch | 是 | target |
| 19 | Switch | Action | Switch | 是 | target |
| 21 | Switch | Action | Switch | 是 | target |
| 24 | Switch | Action | Switch | 是 | target；既有独立入口不变 |
| 25 | Switch | Action | Switch | 是 | target；密码管理独立入口保留 |
| 26 | Switch | Action | Switch | 是 | target |
| 32 | Switch | Action | Switch | 是 | target |
| 33 | Switch | Action | Switch | 是 | target |
| 36 | Switch | Action | Switch | 是 | target |
| 37 | Switch | Action | Switch | 是 | target；关闭确认保留 |
| 38 | Switch | Action | Switch | 是 | target；关闭确认保留 |
| 39 | Switch | Action | Switch | 是 | target；依赖提示保留 |
| 40 | Switch | Action | Switch | 是 | target |
| 42 | Switch | Action | Switch | 是 | target |
| 43 | Switch | Action | Switch | 是 | target |
| 44 | Switch | Action | Switch | 是 | target |
| 45 | Switch | Action | Switch | 是 | target |
| 46 | Switch | Action | Switch | 是 | target |
| 47:enable | Switch | Action | Switch | 是 | target |
| 49 | Switch | Action | Switch | 是 | target |
| 51 | Switch | Action | Switch | 是 | 完全未知仍限制；RESULT_ONLY 不变 |
| 52 | Switch | Action | Switch | 是 | target；不开放标定 |
| 53 | Switch | Action | Switch | 是 | target |
| 59 | Switch | Action | Switch | 是 | target |
| 60:enable | Switch | Action | Switch | 是 | target |
| 62 | Switch | Action | Switch | 是 | target |
| 63 | Switch | Action | Switch | 是 | target |
| 64 | Switch | Action | Switch | 是 | target；原门控保留 |
| 64:adaptive | Switch | Action | Switch | 是 | target=true→1；false→0 |
| 68 | Switch | Action | Switch | 是 | target |
| 69:fcw | Switch | Action | Switch | 是 | target |
| 69:rcw | Switch | Action | Switch | 是 | target |
| 69:bsd | Switch | Action | Switch | 是 | target |
| 72 | Switch | Action | Switch | 是 | target |
| 73 | Switch | Action | Switch | 是 | target；配置面板 UNKNOWN 不变 |
| 75 | Switch | Action | Switch | 是 | target |
| 76 | Switch | Action | Switch | 是 | target |
| 77 | Switch | Action | Switch | 是 | target |
| 79 | Switch | Action | Switch | 是 | target |
| 80 | Switch | Action | Switch | 是 | target |
| 81 | Switch | Action | Switch | 是 | target |
| 82 | Switch | Action | Switch | 是 | target |
| 84 | Switch | Action | Switch | 是 | target |
| 86 | Switch | Action | Switch | 是 | target |
| 89 | Switch | Action | Switch | 是 | target；原门控保留 |
| 89:adaptive | Switch | Action | Switch | 是 | target=true→2；false→0 |
| 90 | Switch | Action | Switch | 是 | target |
| 91 | Switch | Action | Switch | 是 | target |
| 93 | Switch | Action | Switch | 是 | target |
| 94 | Switch | Action | Switch | 是 | target |

这些是 Mapper 能生成的 Toggle 形状，不是硬编码车型清单；实际是否出现始终由 bikeConfigVos 决定。95 继续按既有规则隐藏。41/88 是独立管理入口，未修改其 Controller、开关或状态流程。缺少本身写支持/权限的条目不因此变成可写。

## 5. 云端动态配置及 Summary

- `YadeaCarSettingsGateway.load(vin)`、`selectBikeSettingByVin`、`bikeConfigVos` 解析、原始 rawJson、配置权限与写请求均未删除或改协议。
- MainScreen.kt:432 起仍按 VIN/token/Controller 加载；Mapper.toItems 从 configs 生成所有现有分组和语义子项；界面没有增加依赖 TTP/UNKNOWN 的条目过滤。
- `isAuth=false` 仍只读，缺失 isAuth 不当成 false；未登录、读取失败、权限拒绝不是同一个状态。
- 同 VIN 刷新保留旧清单直至新响应；换 VIN/车型不复用旧清单。原隐藏95、组合60、按键二级入口等既有显示规则不变。

Summary 定位：没有找到独立名为 Summary 的页面/模型。实际为 `CarSettingItem.summary` → `CarSettingRow` → `App*Preference.summary`/`AppListRow.summary`，由两套原生组件显示副标题。向用户异步确认了该区域；未收到额外位置说明时按最小范围执行。

| 展示内容 | 原始来源 | 本轮处理 |
| --- | --- | --- |
| 字面占位“云端车辆设置项” | Gateway.summaryOf，在没有有效 briefCopy/newCopy/copy 时生成 | 只在 UI settingDescriptionSummary 中隐藏；不删除源字段或 Mapper 数据 |
| “车联网”通道标签 | item.channel=IOT/BOTH → channel.label | IOT 不追加标签；BOTH 仅保留“蓝牙”；枚举、路由和业务使用不变 |
| 诊断帮助入口的车联网 Summary 片段 | CarSettingsScreen 原本“本机＝… · 蓝牙＝… · 车联网＝云端下发” | 只删最后一个 Summary 片段，帮助入口和业务仍保留 |
| 云端实际功能说明 | briefCopy/newCopy/copy → readableCloudCopy | 保留，不按含“云端/车联网”关键词删除任意说明 |
| 只读、禁用原因、草稿、实际值、提交、确认、错误 | 原状态模型和 UI | 保留，并补历史缓存/配置来源标识；不把来源标签替换成假确认 |
| 其他页面车联网服务/控车信息 | VehicleInfoScreen/VehicleControlScreen/DeviceScreen 等 | 完全不改 |

UI Info/Customize/Entrance 行、普通 Row、Slider 副标题及详情说明统一只屏蔽上述字面占位。没有删除 Repository、Gateway、Controller、网络请求、云端项目或车联网服务。

## 6. 修改及提交范围

| 完整提交 | 精确文件范围 | 必要性 |
| --- | --- | --- |
| `a1bacd6d646f188c93ed7ab97ea7d1bb1e71bceb` | K/carsetting/CarSettings.kt；K/carsetting/CarSettingsGateway.kt；K/ui/screens/CarSettingsScreen.kt；shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/SettingsCacheRecoveryTest.kt | 恢复已有缓存/明确云端布尔配置的展示；保留当前会话清单；10项Fake测试 |
| `d86dbbb0d4631b6f5a6d4768a49fa8c97fc2d2d8` | K/ui/screens/CarSettingsScreen.kt；同测试根 SettingsSwitchRecoveryTest.kt | 所有普通 Toggle 恢复 Switch，目标仅传一次，删除 Toggle 菜单；7项Fake测试 |
| `739c7f40bdc359c1915bebb6009713ae6c6ca984` | K/ui/screens/CarSettingsScreen.kt；同测试根 SettingsSummaryRecoveryTest.kt | 只移除指定 Summary 展示；6项展示函数/数据不变测试 |
| `78211d3bbc5b4def4b98f2b56ae47377e12c9c50` | K/carsetting/CarSettings.kt；K/ui/screens/CarSettingsScreen.kt；同测试根 SettingsCacheRecoveryTest.kt | 保留列表后的刷新期间禁止新写/草稿，保留观察须兼容新控件形状；缓存测试增加1项至11项 |

本报告单独 docs 提交，完整哈希通过 `git log -1 --format=%H -- docs/vehicle-settings/regression-recovery-audit.md` 可精确获取，最终答复列出；不在自身内容中伪造自引用哈希。

以上源码只涉及3个 commonMain 既有文件；没有新增持久化系统、状态框架或平台依赖。CarSettingsGateway 改动仅为现有云端值的展示投影，未改 load/update、命令或 HTTP 语义。

## 7. 实际测试与构建

使用既有 `GRADLE_USER_HOME=C:\Users\DozenOregano696\.gradle`，全部 Gradle 命令带 `--offline`，未换缓存或下载依赖。

| 验证 | 实际结果 |
| --- | --- |
| :shared:compileKotlinJvm / compileKotlinJs / compileKotlinWasmJs | BUILD_VERIFIED，修改后实际通过 |
| :shared:compileTestKotlinJvm | TEST_SOURCE_COMPILED，修改后实际通过 |
| :androidApp:assembleDebug / :desktopApp:build | BUILD_VERIFIED，实际通过；Desktop test 为 NO-SOURCE，不算测试通过 |
| 过滤的 :shared:jvmTest | UNIT_TESTED，23个XML suite、160项执行通过，失败/错误/跳过均0 |
| Miuix/Material3、浅深色、触摸与真实页面 | 仅静态检查 AppSwitchPreference 两个原生分支共用目标回调；没有实际视觉/触摸验证，不标 RUNTIME_VERIFIED |
| iOS | 当前Windows未构建，不宣称通过 |
| 真实账号/云端/车辆 | 未执行，不标 VEHICLE_VERIFIED |

实际过滤的类/方法及 XML 数量：

| Suite | 执行数 |
| --- | --- |
| SettingsCacheRecoveryTest | 11 |
| SettingsSwitchRecoveryTest | 7 |
| SettingsSummaryRecoveryTest | 6 |
| EighthRoundObservationTest | 3 |
| EighthRoundStateIsolationTest | 7 |
| EighthRoundValueStateTest | 11 |
| EleventhRoundSliderIntegrationTest | 10 |
| EleventhRoundUiGatingTest | 8 |
| EleventhRoundUiSessionTest | 7 |
| TenthRoundAlarmSensitivityTest | 10 |
| TenthRoundAdaptivePushTest | 13 |
| NinthRoundCustomSoundTest | 29 |
| NinthRoundCustomSoundGatewayTest（FakeTransport） | 11 |
| FifthRoundX1MappingTest | 3 |
| FifthRoundDistanceRegressionTest | 1 |
| SixthRoundReviewTest | 4 |
| VehicleSettingGoldenFrameTest | 3 |
| CarSettingsUiResetKeyTest | 3 |
| ResolvedSliderStateTest | 4 |
| CarSettingsStateIsolationTest | 3 |
| CarSettingsControllerTest：仅 adaptivePushUsesVariantSpecificSwitchValuesAndZeroBasedLevel、adaptivePushCommandsUseVariantSpecificParameters、customKeyFunctionsReadBackFromCustomFunctionBits | 3 |
| SixthRoundActivityBatchTest：仅 scheduledShutdownTimeMatchesSeekBarSemantics | 1 |
| TtpInfoParserTest：仅 parse_energyRecoveryUsesSlideModeFields、parse_chargingDriveAndNavi_0x70_to_0x7A | 2 |

执行命令为 `:shared:jvmTest --tests '*类名'`，后三行使用各完整方法过滤；没有无过滤运行 jvmTest。所有新测试只构造内存 Fake Gateway/Writer、CompletableDeferred、合成TTP；未构造生产传输或读取真实账号。XML 位于 shared/build/test-results/jvmTest，统计总数160，不将不同批次重复累加。

新增覆盖：缓存先到、云端后到、同VIN刷新悬挂/失败、动态清单替换、只读及UNKNOWN条目不消失、异VIN缓存拒绝、Controller替换清空内存、实时值优先、草稿隔离、缓存不能确认、明确云端布尔值、一次目标/一次Writer、失败保留新观察、64/89极性、Summary原数据不变和其他提示保留。

## 8. 保护与未完成事项

- `STATIC_FLOW_VERIFIED`：本轮未修改 TtpInfo、VehicleSettingCommands、VehicleSettingFrameEncoder、BLE源/连接/发送、Wi-Fi/投屏/OTA、密码/联系人/音效Controller。
- ID14三位置、ID20八档、64/89独立raw与D0/D1参数、ID51 RESULT_ONLY仍在原链路；缓存分支沿用原转换且51不建立观察。相关过滤回归实际通过。
- 第八轮 VIN/序号/写操作代次及确认保护、第九轮41/88隔离均保留；没有将服务端/Writer返回true解释为车辆实际值。
- `PARTIAL`：缓存记录没有车型元数据，只能证明VIN匹配。Controller替换不继承旧内存，但同VIN历史持久包的车型归属无法由现有记录严格证明；未伪造车型身份，也未宣称通过跨进程车型验证。
- `PARTIAL`：未新增云端清单持久缓存。首次离线启动、账号变化导致无合法清单时，不能仅凭TTP缓存显示完整动态项目；已有清单的同会话刷新问题已修复。
- `PARTIAL`：完全未知Toggle保留Switch但限制操作；只有缓存/明确云端记录/实时观察/显式用户目标时才能正常直接切换，不为恢复交互假定当前false。
- 未运行正常App，不承诺运行时视觉或设备触摸验收；没有真实用户缓存文件读取、账号请求或实车验证。静态链路和Fake测试不能替代这些验证。

## 9. 最终工作区

每个功能使用精确pathspec，提交前 `git diff --check` 通过。四个代码提交后 staged、unstaged 为空，除本报告外untracked仍为第1节原12项。文档单独提交后再次检查同样状态，最终HEAD见文档提交的实际Git记录及最终答复。

验收回答：已有缓存读取→历史展示链路已恢复；原Toggle类型及直接目标回调已恢复；实际页面云端动态配置保留且刷新不再先清空旧清单；指定Summary来源标签已移除，其他说明/业务保留；八至十轮映射与隔离继续保留。首次无清单离线恢复、缓存车型归属及实际UI/实车效果仍明确PARTIAL，整体不标COMPLETE。
