# ID 64 / 89 自适应推行分变体映射

日期：2026-11-10。状态：本次修复的字段/目标参数转换为 `STATIC_FLOW_VERIFIED`、`UNIT_TESTED`、`TEST_SOURCE_COMPILED`、`BUILD_VERIFIED`；整个功能仍 `PARTIAL`，未进行真实车辆验证。

## 1. 范围与证据来源

Smali 根为 `F:\android app code\YadeaHook\temp_smali`。以下文件行号相对于此目录；不是 `.line` 序号。KMP 根为 `F:\android app code\toupin\shared\src\commonMain\kotlin\top\ghhccghk\yadea`。

独立布局证据只读来自既有 `F:\android app code\toupin\yadea.apk`，SHA256：`329BA5ECF012E71E70A1496F139C21822E5D64CBB96DDFAA59A9896E474AE685`。SDK build-tools 35.0.1 的 aapt2 dump resources / dump xmltree，验证资源 ID、getLayoutId、Binding tag/控件 ID 的对应。没有运行、解包覆盖或提交 APK，也没有声称整个 dex 已与 Smali 做哈希同源认证。

## 2. 读取链路

| 环节 | 原 App 证据 | 对应结论 |
| --- | --- | --- |
| 64 入口 | `com/yadea/smartmoto/vehicle/view/HelpPromotePlusActivity.smali:188` getSettingConfigId，193 返回 | confId 为字符串 64 |
| 89 入口 | `com/yadea/smartmoto/vehicle/view/HelpPromotePlus2Activity.smali:196` getSettingConfigId，201 返回 | confId 为字符串 89 |
| 主开关 | `com/yadea/smartmoto/vehicle/databinding/ActivityHelpPromotePlusBindingImpl.smali:884` 等 executeBindings 调 VM.L5→K1；Plus2 同链；VM b9:22195 附近 | 主开关由 helpCarState==1 回填，与自适应字段不是同一状态 |
| 64 自适应 | `ActivityHelpPromotePlusBindingImpl.smali:1135`→VM.W2→F | F 的实际生成链需要单独检查 |
| 89 自适应 | `ActivityHelpPromotePlus2BindingImpl.smali:1140`→VM.X2→G | G 不可套通用 Boolean 转换 |
| 回填转换 | `com/yadea/smartmoto/vehicle/viewmodel/VehicleSettingViewModel.smali:23662,23698`，b9 | F=(customDriveSwitchStatus==1)；G=(customDriveSwitchStatus==2) |
| 档位回填 | 同 VM:23712 | H 从 customDriveLevelStatus 直接 int-to-float，无 +1/-1 |
| 原始字段 | `l76.smali:10990,11125` 及随后档位分支 | helpCarState=G(0x6E,6)，自适应=G(0x76,6)，档位=C(0x79,0) |
| KMP 解析 | `vehicle/TtpInfo.kt:540,557,558` | 已与上述字段一致，64/89 本轮不改解析偏移/位宽 |

Binding 表中的短路径均位于 `com/yadea/smartmoto/vehicle/databinding`。原 App 对空/无效数据存在 false/0 展示兜底；KMP 不将兜底提升为真实观察，沿用第八轮 UNKNOWN/无效观察保护。

## 3. 独立状态与点击语义

### 3.1 回读表

| customDriveSwitchStatus | 64 自适应 UI | 89 自适应 UI |
| --- | --- | --- |
| 0 | 关闭 | 关闭 |
| 1 | 开启 | 关闭 |
| 2 | 关闭 | 开启 |
| 字段 3（G 解析为 -1）、缺失、域外值 | UNKNOWN，不产生新观察 | UNKNOWN，不产生新观察 |

主开关仍来自 helpCarState；不能用自适应 raw 推断主开关，也不能把两个车型配置 ID 合并。

### 3.2 为什么参数必须按目标状态计算

| 证据 | 实际行为 |
| --- | --- |
| 两 Activity 的 setListener，64:209 / 89:217 附近 | 监听在 actionCustom FrameLayout，不是直接监听 CheckBox 翻转 |
| 两 Activity `$d.smali:61` onClick | 去抖后调用 access$onCbClick，无事先更改 checked |
| 原布局 `res/uAX.xml` / `res/4h4.xml` 第 94、105 源行 | 父 FrameLayout 为 binding_4；子 CheckBox binding_5 明确 `android:clickable=false` |
| `HelpPromotePlusActivity.smali:94` onCbClick | 读当前 checked，xor 1：当前 false 发 1，当前 true 发 0 |
| `HelpPromotePlus2Activity.smali:94` onCbClick | 当前 false 发 2，当前 true 发 0 |

所以父容器点击时读到的是**原状态**，不是系统自动翻转后的目标。KMP 回调提交的是目标 checked，两者必须显式区分：

| 变体 | currentChecked | targetChecked | D0 参数 |
| --- | --- | --- | --- |
| 64 | false | true | 1 |
| 64 | true | false | 0 |
| 89 | false | true | 2 |
| 89 | true | false | 0 |

不能从历史“勾选写 0”注释推出 target=true 写 0。发出命令后的原页面展示由后续 Binding 数据更新，不以本地取反作为车辆成功证据。

## 4. 三档范围和命令

| 环节 | 原始证据 | 已证实范围 |
| --- | --- | --- |
| 64 布局 | R layout `0x7f0d007d`→`res/uAX.xml`，第 140 源行 IndicatorSeekBar | min=0、max=2、ticks_count=3 |
| 89 布局 | R layout `0x7f0d007e`→`res/4h4.xml`，同源行控件 | min=0、max=2、ticks_count=3 |
| 标签 | 两布局 tick_texts_array=@0x7f030034；APK volume_array | 低/中/高，三项；seek_smoothly=true |
| tick 位置 | `com/warkiz/widget/IndicatorSeekBar.smali:616,5162` | 事件 e 为零基位置，不由字段位宽推导；未发现两 Activity/Binding 动态改 tick 数 |
| 拖动事件 | 两 Activity `$a.smali:40-100` | 从 wd5.e 保存 thumbPosition，停止拖动直接发送 CUSTOM_DRIVE_LEVEL_SETTING |
| 命令路由 | `qg.smali:1625-1641`，5280-5296 | 开关 D0，档位 D1；存在 BLE 连接条件 |
| 帧分支 | `com/yadea/blecontrol/data/BleBicycleCommandManager.smali:2486,2501` | D1 调 uj.c(0x38,param,0,2)；D0 调 uj.c(0x37,param,2,3)，本轮不改帧编码 |
| 既有 KMP 编码 | `carsetting/VehicleSettingFrameEncoder.kt:152` | 保持原 D0/D1 帧字段，不改 BLE 传输 |

| TTP 档位 raw | UI 进度 / tick | D1 参数 | 显示语义 |
| --- | --- | --- | --- |
| 0 | 0 / 0 | 0 | 低 |
| 1 | 1 / 1 | 1 | 中 |
| 2 | 2 / 2 | 2 | 高 |
| 3..6 | UNKNOWN | 不开放 | 超出已证实页面范围 |
| 7、缺失 | 解析无效 / UNKNOWN | 不生成 | 不钳位成正常档位 |
| 小数、NaN、无穷、UI 域外值 | 非合法 tick | 不生成 | 不静默取整 |

上述 0..2 是**这两个原页面的可写选择范围**，不是“所有车型所有固件完整允许范围”的证明。物理速度、固件其他保留值、其他布局变体仍 UNKNOWN，不扩展档位。

## 5. 限制与状态隔离

主开关 Binding 通过连接/电源相关表达式设置 enabled/cover；`ActivityHelpPromotePlusBindingImpl.smali:1278` actionCustom clickable 取主开关，1283/1291 分别设置主开关/自适应 checked，1310/1315 coverEnable/主开关 enabled，1380 seekbar enabled。VM.G6/R4 都返回 p2；VM:23587 起从 VehicleDataManager.G().isPowerStateOpen() 写 p2。禁止由这些显示限制推测新的协议参数。

本轮只修复映射，不迁移或改写整套连接/电源交互规则。原页面全部 gating 与 KMP 完全一致的证明仍 `PARTIAL`，真实车辆状态限制未运行验证。云端 bikeConfigVos 仍决定是否提供 64/89、权限和分组，不新增硬编码车型入口。

第八轮身份、loadGeneration、operationId、草稿/实际值分离、发送回执与新回读比较保持不变。新增测试覆盖 VIN-A/64→VIN-B/89、同 VIN 配置 64→89 重新加载后旧写入返回；旧状态、旧响应和 pending 不得覆盖新条目。它们是 Fake 调用链测试，不证明底层真实车辆身份认证。

## 6. 实际修改范围

| 文件 | 改动 / 原问题 | 对应证据 |
| --- | --- | --- |
| `carsetting/CarSettings.kt:82,92,770,943` | 两个独立 Mapping；把已有但未调用的 adaptivePushOrNull 接到真实回填分支；level 读取 1..3→0..2 | 两 Binding→VM F/G/H、原布局 |
| `carsetting/VehicleSettingCommands.kt:415,817` | D0 按目标状态分别映射 1/2/0；D1 拒绝非法 tick，不再钳位 | 父容器点击、onCbClick、$a 档位监听 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsControllerTest.kt:195` | 只纠正历史 D0 断言四处目标极性 | 同上，不改变其他测试 |
| `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/TenthRoundAdaptivePushTest.kt` | 新增 13 项纯本地回归 | 下节 |

历史 `0e1f8c97582d89a9ccb15ea36fcd82c4dd54f1ef` 已新增 helper 和部分零基范围，但当前基线真实回填仍走 flagOrNull/1..3，D0 仍反向。不能仅凭 helper 存在或历史完成记录宣称接入成功，也不能没有 Git 证据就说这是后续提交引入的回归。旧报告/日志不覆盖修改。

## 7. 验证

- 新增 13 项实际通过：64 raw0/1/2、89 raw0/1/2、同快照不混变体、两种父点击目标参数、非法/缺失 UNKNOWN、主开关独立、两变体三档读写、非法小数拒绝、位域/兄弟位、跨 VIN 旧写入、同 VIN 配置换代、草稿取消/回执不冒充回读。
- 历史 adaptivePushUsesVariantSpecificSwitchValuesAndZeroBasedLevel、adaptivePushCommandsUseVariantSpecificParameters 两项实际通过。
- 最终综合过滤共 98 项通过，0 failures/errors/skipped，具体分组/命令见总报告；未执行完整套件。
- 该补丁后 JVM/JS/Wasm 与 JVM 测试源码编译通过（23s）；Android/Desktop 离线构建通过（30s）。Desktop test 为 NO-SOURCE，不计测试通过。
- iOS、Miuix/Material3 实际 UI、真实云端、BLE 和车辆均未验证。完整固件范围/物理速度未知；连接电源交互全覆盖仍 PARTIAL。

## 8. Git

独立分变体功能提交：`b8060c605f560cb8b5b6d524b0c51c7fd6844b40`，`fix(car-settings): restore variant-specific adaptive push mapping`，精确四文件如上。未修改 64/89 主开关命令、TTP 偏移、帧编码、其他功能状态机；不推送远端。本文随单独文档提交，最终哈希见 Git 和交付消息。
