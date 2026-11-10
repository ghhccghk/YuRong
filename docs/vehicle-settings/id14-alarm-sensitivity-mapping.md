# ID 14 报警灵敏度映射纠错

日期：2026-11-10。结论：`STATIC_FLOW_VERIFIED`、`UNIT_TESTED`、`TEST_SOURCE_COMPILED`、`BUILD_VERIFIED`；未进行运行、云端或实车验证。

## 1. 证据定位约定

- Smali 根：`F:\android app code\YadeaHook\temp_smali`。下表 Smali 路径相对于此目录，行号是文件行号，不是 `.line` 调试序号。
- KMP 根：`F:\android app code\toupin\shared\src\commonMain\kotlin\top\ghhccghk\yadea`。
- 本轮重新读取页面、Binding、ViewModel、解析器、控件及命令分支，不以历史报告作为协议证据。
- 补充布局证据来自用户已有、只读检查的 `F:\android app code\toupin\yadea.apk`，SHA256：`329BA5ECF012E71E70A1496F139C21822E5D64CBB96DDFAA59A9896E474AE685`。使用 Android SDK `build-tools\35.0.1\aapt2.exe dump resources / dump xmltree`，没有安装或运行 APK。
- APK 资源表、Smali R 资源 ID、页面 getLayoutId 和 Binding tag 相互匹配。这是布局交叉核对，不声称已对整个 APK dex 与 Smali 做逐字节同源认证。APK、资源和反编译文件不提交。

## 2. 原 App 完整输入/输出链

| 环节 | 原始证据 | 结论 |
| --- | --- | --- |
| 入口 | `com/yadea/smartmoto/vehicle/view/VehicleAlarmSensitivityActivity.smali:87`，getSettingConfigId | 返回字符串 `14` |
| 布局 | 同 Activity 的 getLayoutId；R layout `0x7f0d00ed`；APK `res/EbG.xml` 第 84 源行 | `activity_vehicle_alarm_sensitivity` 的 IndicatorSeekBar，tag `binding_4` |
| 初值 | `com/yadea/smartmoto/vehicle/viewmodel/VehicleSettingViewModel.smali:4295` 构造 l2 | MutableLiveData 无初始 Integer；不能认定默认低档为车辆值 |
| Binding 输入 | `com/yadea/smartmoto/vehicle/databinding/ActivityVehicleAlarmSensitivityBindingImpl.smali:446`，executeBindings；475 附近解包，664 调 a0(F) | 取 VM.r1 的 Integer 作为进度，不是直接取写入参数 |
| 属性来源 | `VehicleSettingViewModel.smali:30245` r1 返回 l2；22495 起 b9 读取 alarmSensitivityStatus 并更新 l2 | raw 0/1/2 转进度 0/50/100 |
| 原始解析 | `l76.smali:9387` 及此前 const，z 的字段解析分支 | **C(data,0x1C,2)**，不是 G；三位掩码，7 为无效 |
| 解析原语 | `l76.smali:123` C；336 的 G | C 掩码 7，G 掩码 3；不可互换。调用处 v5=2 在 9123 附近建立 |
| 旧错误字段 | `l76.smali:9764` alarmWarningMode | C(data,0x33,0)，与页面实际绑定字段独立 |
| 三档依据 | APK `res/EbG.xml` 第 84 源行控件属性 | `isb_ticks_count=3`，`isb_tick_texts_array=@0x7f030034`，`isb_seek_smoothly=true`；无 min/max 覆盖 |
| 默认范围 | `ev.smali:105` 构造 b=100、c=0、d=0；`com/warkiz/widget/IndicatorSeekBar.smali:958` H(Context,AttributeSet) | 属性未覆盖时进度范围 0..100；未发现本 Activity/Binding 动态改 tick 数 |
| tick 定义 | `IndicatorSeekBar.smali:616` getThumbPosOnTick；5162 起 p(Z)，5218-5252 对事件 e 赋值 | e 为零基 tick 位置；进度与位置是不同量。默认非反向；反向逻辑不能当本页面已启用 |
| 标签 | APK array `0x7f030034` volume_array，三项低/中/高；`hp6.smali:671` m(I) | 0/1/2 对应低/中/高；其他值返回空文案 |
| 监听 | `VehicleAlarmSensitivityActivity.smali:97` setListener；其 `$a.smali:90` b | 读取 wd5.e 保存 thumbPosition |
| 写入 | `VehicleAlarmSensitivityActivity$a.smali:48` 停止拖动回调 | ALARM_SENSITIVITY_GEAR 参数直接取 thumbPosition，不是进度 50/100 |
| 命令 | `qg.smali:2264`、2280 附近分支 | ALARM_SENSITIVITY_GEAR 到 `0x5F` |
| 编码 | `com/yadea/blecontrol/data/BleBicycleCommandManager.smali:3674` generateBleCommand | 0x5F 参数字节为 `(parameter << 5) + 0x1F`，前 3 字节 FF、后 21 字节 FF；本轮不修改既有编码 |

任务描述曾写 G(data,0x1C,2)，本轮以实际 `invoke-static ... ->C([BII)I` 为准。三位编码容量也不证明页面开放 0..7 八档。

## 3. 数值映射表

| TTP 原值 | 原页面进度 / KMP Slider 值 | tick 位置 | 写入参数 | 文案 |
| --- | --- | --- | --- | --- |
| 0 | 0 | 0 | 0 | 低 |
| 1 | 50 | 1 | 1 | 中 |
| 2 | 100 | 2 | 2 | 高 |
| 3..6 | 不接受为已知业务档位 | UNKNOWN | 不生成命令 | UNKNOWN |
| 原字节字段 7、缺失字段 | 解析为 -1 / UNKNOWN | UNKNOWN | 不生成命令 | UNKNOWN |
| 非 0/50/100 的 UI 输入、NaN、无穷 | 非合法提交进度 | 无位置 | 不生成命令 | 不静默取整或钳位 |

写入参数 n 与后续有效 raw n 采用上表一致的展示映射；这只是静态映射及本地往返验证，不证明任意固件已执行请求。必须是当前 VIN 的新快照，才可进入原有回读比较。

**有意保留的安全差异**：原 VM 对其他 raw 值回退到进度 0，Binding 在部分连接条件下也会显示 0。本轮不把这类 UI 兜底认证成车辆低档。初次无效时保持 UNKNOWN；已有有效观察后收到无效/缺失字段，保留上次观察并标记 `MISSING_OR_INVALID`，不建立新观察或确认。

## 4. KMP 修改

| 文件 / 位置 | 修改前问题 | 修改后 |
| --- | --- | --- |
| `vehicle/TtpInfo.kt:359,570` | 未解析页面实际使用的字段 | 在模型末尾追加带 INVALID 默认值的 alarmSensitivityStatus，并调用 C(0x1C,2)；不移动旧字段，不改 alarmWarningMode |
| `carsetting/CarSettings.kt:66,912` | ID 14 用 alarmWarningMode 0..7 回填 | AlarmSensitivityMapping 显式 raw→进度；只用 alarmSensitivityStatus |
| `carsetting/CarSettingsGateway.kt:1735,1742` | 14 与 20 共用八档模型 | 14 为 0..100、steps=1、UNKNOWN；20 仍 0..7、steps=6 |
| `carsetting/VehicleSettingCommands.kt:609` | 将进度直接取整钳位 0..7 | 只接受三种已证实进度，映射到参数 0/1/2；命令仍 0x5F |
| `shared/src/jvmTest/.../EighthRoundValueStateTest.kt` | 七项状态测试夹具使用错误业务字段 | 仅将 ID 14 夹具改成 alarmSensitivityStatus=1，不改状态规则 |
| `shared/src/jvmTest/.../TenthRoundAlarmSensitivityTest.kt` | 无原字段/进度/tick 隔离回归 | 新增 10 项纯本地测试 |

测试路径省略部分为 `kotlin/top/ghhccghk/yadea/carsetting`，相对于仓库根。UI 继续使用既有 AppSliderPreference，传入新的 range/steps，不改两套主题组件。文案仍沿用通用数值展示，本轮不是原页面像素级复制。

## 5. 验证与边界

- 10 项新增测试实际通过：解析三位与兄弟位隔离、缺失、raw/进度/tick 全映射、非法输入拒绝、ID20 独立、云端/旧字段不能冒充回读、无效不覆盖有效、草稿/发送回执/新回读区分、跨 VIN 隔离。
- 第一轮 21 项定向执行曾有 1 项测试夹具失败：Mapper 生成附加项，不能用 items.single()；改用稳定 `cloud:14` 条目查找后重编译、21 项通过。最终综合过滤运行含本组 10 项，共 98 项通过，0 失败/错误/跳过；不是完整 jvmTest。
- JVM、JS、Wasm 主源码及 JVM 测试源码离线编译通过；Android assembleDebug、Desktop build 离线通过。具体命令、次数和总表见总报告。
- 未执行 iOS 构建、实际 UI、真机、真实云端、BLE 或车辆验证。固件对 raw 3..6 的含义仍 `UNKNOWN`；不开放、不猜测。
- 第五轮报告与第七轮清单中的 alarmWarningMode/0..7 完成结论不能继续作为 ID14 映射依据；旧文档保持原样，由本证据表更正。

## 6. Git

独立功能提交：`d7db57078836a15a52f385194f46b66365bc96be`，`fix(car-settings): restore evidenced alarm sensitivity mapping`。

实际范围为上表六文件。没有修改 VehicleSettingFrameEncoder、BLE 传输、其他报警设置或状态隔离算法。本文独立随文档提交，最终文档哈希见 Git 和交付消息，避免自引用。
