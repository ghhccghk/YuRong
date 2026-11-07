# 车辆设置问题清单

本轮只读审计发现以下可独立确认的问题。状态统一为 `PENDING`，因为本轮按要求不修改业务代码；`UNKNOWN` 项不进入自动修复。

| ID | 优先级 | 安全 | 类别 | 代码位置 | 原始证据 | 当前差异 | 影响 | 建议修复 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| VS-001 | P1 | L2 | VIN/状态污染 | `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt:165-205`、`ui/screens/MainScreen.kt:419-430` | 原页面按当前车辆加载；`BasePeripheralsModel$getBikeConfigs$1` 以当前 VIN 请求 | `refresh()` 只设置 `loading=true`，不清空 items/missingCloudItems；失败也保留旧车值 | 切换车辆或刷新失败时可能显示/编辑上一辆车设置 | 刷新开始按 VIN 清空或标记 stale，响应带 VIN 校验；失败显示空/错误而非旧数据 | COMPLETE |
| VS-002 | P1 | L2 | TTP/VIN 隔离 | `CarSettings.kt:152,307-481`、`MainScreen.kt:429-431` | TTP 状态是车辆快照，`TtpInfoParser` 保留 raw/hex | `latestVehicleStatus` 只保存 `TtpInfo`，没有 VIN；新 VIN 加载期间旧 TTP 可覆盖新条目 | 新车页面短暂显示旧车 TCS/ABS/亮度/容量等状态 | 在控制器状态中保存 `(vin, TtpInfo)`，只允许当前 VIN 回填 | COMPLETE |
| VS-003 | P2 | L1 | 控件/回读错误 | `CarSettingsGateway.kt:1283-1295` | `ReversingFunctionActivity` / `HelpPromoteActivity` 的 `getSettingConfigId()` 为 43/44；TTP 有 `reversingState/helpCarState` | `kindOf()` 先匹配 `"43","44" -> Info("状态由车辆回读")`，后面的 Toggle 分支永远不可达；`withToggle()` 也不会作用于 Info | 倒车/婴儿车模式无法编辑，TTP 回读无效 | 删除重复 Info 分支，保留 Toggle 并继续 TTP 回填；保留只读说明作为摘要而非控件类型 | PENDING |
| VS-004 | P2 | L2 | 权限绕过 | `CarSettings.kt:220-228`、`VehicleSettingCommands.kt:23-74` | 原 `CarSettingsItemAvailabilityHelper` 先按 `ItemAccess/BlockReason` 判定可用性 | `CarSettingsController.update()` 检查 `item.writable`，但 `MappedCarSettingWriter`/`CloudCarSettingWriter` 接收任意 item，不检查 `writable` 或当前清单权限 | 直接调用 writer 可绕过 `isAuth=false` 或伪造条目写入 | 将权限、confId、条目 ID 校验下沉到 writer/命令入口；控制器仅做 UI 便利检查 | PENDING |
| VS-005 | P1 | L3 | 写入确认缺失 | `CarSettings.kt:239-295`、`VehicleSettingCommands.kt:23-36` | 原设置页写入后由车辆状态/回调更新；`TtpInfo.extended` 提供多项反馈 | BLE 写入只看 `sink.send()` 返回值，直接保留乐观值；没有回读确认或失败回滚 | 蓝牙写入成功但车辆未生效时 UI 假装已确认 | 只读审计；修复需命令 ACK/回读策略和超时回滚，不得猜测协议 | PENDING |
| VS-006 | P2 | L2 | 参数默认值 | `CarSettingsGateway.kt:1258-1425` | `CarSettingBean` 字段值来自云端；原页面子 Activity 对每项有具体初始化 | 多处缺字段时用 `false`、`0`、`1`、`80`、`350` 等本地默认；`CloudCarSettings.boolValue()` 的 null 被 `== true` 转为 false | 云端缺字段时可能把未知状态显示为关闭/固定值 | 建立 per-confId 默认值证据表；无证据保留 UNKNOWN/只读，不写入 | PENDING |
| VS-007 | P2 | L1 | 原功能缺失 | `CarSettingsGateway.kt:1479-1562`、`BackLightActivity.smali:234-242` | 原 `BackLightActivity.getSettingConfigId()` 返回 `21` | KMP `TITLES`、`kindOf()`、命令映射均无 `21`；云端下发 21 时只得到通用 Info | 背光设置无法识别/操作 | 补齐 21 的标题、控件、命令和回读证据；证据不足时至少标记只读 UNKNOWN | PENDING |
| VS-008 | P2 | L2 | 云端写入参数 | `CarSettingsGateway.kt:67-137`、`VehicleSettingViewModel.smali:10091-11832` | `P4()` 对不同 confId 产生字段；`MixRequest.j()/k()` 区分 `updateBikeSetting` 与 `updateBikeSettingParams` | KMP 只有单一 `CloudCarSettingUpdateBuilder`，未表达 `updateBikeSettingParams`/`valueJson` 的完整参数模型 | 参数型设置可能使用错误 API 或丢失字段 | 先从 `s20.g()`、请求适配器和抓包确认每个字段；未确认项保持只读 | PENDING |
| VS-009 | P2 | L2 | 状态确认语义 | `CarSettingsGateway.kt:185-210`、`MixRequest$checkSettingStatus$3.smali:78-111` | 原回调读取 `CheckReportLossBean.result` | KMP 将 `result == 0` 视为成功，未确认 `result` 枚举、超时和失败重试语义 | 可能把失败/处理中误判为成功或超时回滚 | 以抓包/Smali 枚举确认；按 confId 记录确认字段和轮询终止条件 | PENDING |
| VS-010 | P2 | L1 | UI 生命周期 | `CarSettingsScreen.kt:63-90,446-485` | 原子页面拥有独立生命周期和刷新 | `selectedClassification`、`showMissingCloudFeatures`、detail draft 由 Composable `remember` 持有；切 VIN 后可能继续显示旧分类/草稿 | 页面重组或切车后 UI 状态与数据不一致 | 以 VIN/loadedFromCloud 为 key 重置 UI 草稿和选中分类 | PENDING |
| VS-011 | P2 | L1 | 权限/隐藏 | `CarSettingsGateway.kt:674-705` | `BikeConfig.isAuth`、`isUserLabFun`、`izLab` 是云端权限/实验室来源 | 主条目 `writable` 统一用 `isAuth != false && (BLE || cloud)`；拆分项只检查 `isAuth`，且 `95` 直接隐藏 | 拆分项、动态项和隐藏项的权限与原可用性 helper 不完全一致 | 对每个条目 ID 复用统一 `ItemAccess`，明确入口可见、可写、隐藏三种状态 | PENDING |
| VS-012 | P3 | L3 | BLE 命令一致性 | `VehicleSettingCommands.kt`、`VehicleSettingFrameEncoder.kt`、`BleBicycleCommandManager.smali` | 原 `BleBicycleCommandManager.generateBleCommand` 管理位域、反值和长度 | KMP 多处使用固定 hex/反值参数；部分 commandId 只有函数注释，缺少逐帧 Smali 对照 | 错误位域可能导致车辆参数被错误覆盖 | 只读审计；逐命令导出 Smali 帧样本，建立 golden frame 测试后再修复 | PENDING |

## 未判定问题

- `TtpInfo.extended` 中仍有许多字段未被 `CarSettingsController` 消费；在没有明确设置项映射前保持 `UNKNOWN`。
- `copyType`、`copyStatus`、`isIntelligence` 的产品语义尚无完整调用链证据，不能据字段名扩展 UI。
- `CloudCarSettingUpdateBuilder` 的 `SLIDER_FIELDS["10"]`、`SLIDER_FIELDS["52"]` 等是否属于 `updateBikeSettingParams`，需继续追踪 `s20.g()`。
- `5` 号报警音主题 encoder 允许 0..2，但 KMP UI 只有两项；需抓包确认是否存在第三种主题。
