# 车辆设置问题清单

本轮只读审计发现以下可独立确认的问题。初始状态为 `PENDING`；后续修复按证据、编译和提交结果更新。`UNKNOWN` 项不进入自动修复。

| ID | 优先级 | 安全 | 类别 | 代码位置 | 原始证据 | 当前差异 | 影响 | 建议修复 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| VS-001 | P1 | L2 | VIN/状态污染 | `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt:165-205`、`ui/screens/MainScreen.kt:419-430` | 原页面按当前车辆加载；`BasePeripheralsModel$getBikeConfigs$1` 以当前 VIN 请求 | `refresh()` 只设置 `loading=true`，不清空 items/missingCloudItems；失败也保留旧车值 | 切换车辆或刷新失败时可能显示/编辑上一辆车设置 | 刷新开始按 VIN 清空或标记 stale，响应带 VIN 校验；失败显示空/错误而非旧数据 | COMPLETE |
| VS-002 | P1 | L2 | TTP/VIN 隔离 | `CarSettings.kt:152,307-481`、`MainScreen.kt:429-431` | TTP 状态是车辆快照，`TtpInfoParser` 保留 raw/hex | `latestVehicleStatus` 只保存 `TtpInfo`，没有 VIN；新 VIN 加载期间旧 TTP 可覆盖新条目 | 新车页面短暂显示旧车 TCS/ABS/亮度/容量等状态 | 在控制器状态中保存 `(vin, TtpInfo)`，只允许当前 VIN 回填 | COMPLETE |
| VS-003 | P2 | L1 | 控件/回读错误 | `CarSettingsGateway.kt:1283-1295` | `ReversingFunctionActivity` / `HelpPromoteActivity` 的 `getSettingConfigId()` 为 43/44；TTP 有 `reversingState/helpCarState` | `kindOf()` 先匹配 `"43","44" -> Info("状态由车辆回读")`，后面的 Toggle 分支永远不可达；`withToggle()` 也不会作用于 Info | 倒车/婴儿车模式无法编辑，TTP 回读无效 | 删除重复 Info 分支，保留 Toggle 并继续 TTP 回填；保留只读说明作为摘要而非控件类型 | COMPLETE |
| VS-004 | P2 | L2 | 权限绕过 | `CarSettings.kt:220-228`、`VehicleSettingCommands.kt:23-74` | 原 `CarSettingsItemAvailabilityHelper` 先按 `ItemAccess/BlockReason` 判定可用性 | `CarSettingsController.update()` 检查 `item.writable`，但 `MappedCarSettingWriter`/`CloudCarSettingWriter` 接收任意 item，不检查 `writable` 或当前清单权限 | 直接调用 writer 可绕过 `isAuth=false` 或伪造条目写入 | 将权限、confId、条目 ID 校验下沉到 writer/命令入口；控制器仅做 UI 便利检查 | COMPLETE |
| VS-005 | P1 | L3 | 写入确认缺失 | `CarSettings.kt:108-144,319-437,452-668`、`VehicleSettingCommands.kt:20-82` | `CarSettingBaseItemView.i()` 将 `CommandTempBean` 交给返回 void 的 `VehiclePlugin.h()`，BLE 分支没有车辆 ACK；IoT 只有 `CheckReportLossBean.result=0` 后才走 success，TTP 可提供后续车辆状态回读 | BLE sink 返回 true 被当成保存成功，状态模型没有区分本地已发送、云端已接受和车辆已确认 | 发送失败或未生效时 UI 可能显示已确认 | 引入五态确认模型；BLE true 仅为 LOCAL_UPDATED，云端接受为 CLOUD_ACCEPTED，新 TTP 匹配或状态查询成功才 VEHICLE_CONFIRMED；失败/未知显式表达 | COMPLETE |
| VS-006 | P2 | L2 | 参数默认值 | `CarSettingsGateway.kt:1396-1559`、`CarSettings.kt:63-92` | TTP 缺失用 -1；Customize 四字段来自 `customizeThrottleSensitivity/customizeEnergyLevel/customizeEnergyState/customizeTcs` | 已为布尔缺失、1005、18、64/89 增加 UNKNOWN；其余固定 Choice/Slider 初值仍缺逐项证据 | 无证据初值可能伪装已知 | 保留 UNKNOWN；继续建立 per-confId 默认值证据表 | PARTIAL |
| VS-007 | P2 | L1 | 原功能缺失/帧证据 | `BackLightActivity`、`BleBicycleCommandManager.smali:3167-3229` | config 21、Switch/SeekBar、15/30/45/60s；0x83/84 固定 suffix，0x85 参数 0..3 左移 5 位加 0x1F | KMP 已有 UI/命令；完整帧此前未固化 | 写入安全性无法仅凭 commandId 证明 | 为 0x83/84/85 建立完整 Golden Frame；不扩大其他命令权限 | COMPLETE |
| VS-008 | P2 | L2 | 云端写入参数 | `CarSettingsGateway.kt:56-278`、`VehicleSettingViewModel.smali:10091-11832` | P4 明确 40/75/77/82 等字段类型和 40/77 参数分支 | 已修正 Map 类型、77 倒置参数、40 CLEAR_SOUND_EFFECT；9/10/82 伴随字段仍依赖当前状态 | 伴随字段缺失可能改变完整请求 | 保持 PARTIAL；需要携带当前 bean 状态后再补齐 | PARTIAL |
| VS-009 | P2 | L2 | 状态确认语义 | `CarSettingsGateway.kt:296-324`、`CarSettings.kt:324-354` | `CheckReportLossBean.result` 是 String；`checkSettingStatus$3` 只有 `"0"` 回调 true，非零/不可解析回调 false，缺失 data/result 不回调；错误回调 false；IoT Polling 以 30×1s 轮询并区分 success/failure/timeout | KMP 此前把 HTTP/异常当 pending、读取顶层 result，可能继续轮询或误判 | 失败可能被当处理中，错误结果可能污染确认状态 | 只读取 `data.result`，请求错误/非零/不可解析立即失败，缺失字段继续轮询；30 次后超时 | COMPLETE |
| VS-010 | P2 | L1 | UI 生命周期 | `CarSettingsScreen.kt:63-90,446-485` | 原子页面拥有独立生命周期和刷新 | `selectedClassification`、`showMissingCloudFeatures`、detail draft 由 Composable `remember` 持有；切 VIN 后可能继续显示旧分类/草稿 | 页面重组或切车后 UI 状态与数据不一致 | 以 VIN/loadedFromCloud 为 key 重置 UI 草稿和选中分类 | COMPLETE |
| VS-011 | P2 | L3 | 写入证据分层 | `CarSettingItemAccess.canWrite()`、`VehicleSettingCommands.supportsItem()` | 稳定 ID、云路径、编码器、Smali 命令、Golden Frame 是五个不同条件 | `supportsItem()` 当前仅证明 encoder 可生成；全量证据表尚不存在 | 直接收紧会禁用用户已实测正常项，直接放宽会开放无证据命令 | BLOCKED：需逐命令证据/授权清单；本轮不改变写入白名单 | BLOCKED |
| VS-012 | P3 | L3 | BLE 命令一致性 | `VehicleSettingGoldenFrameTest.kt`、`BleBicycleCommandManager.smali` | 外层帧和 SUM8 已确认 | 已补 0x83/84/85，连同既有样本覆盖部分命令；其余仍 UNKNOWN，0x141 属 `generateControlCommand` | 未核对命令可能覆盖错误参数 | 只补有完整证据的帧，不改协议实现 | PARTIAL |

## 未判定问题

- `TtpInfo.extended` 中仍有许多字段未被 `CarSettingsController` 消费；在没有明确设置项映射前保持 `UNKNOWN`。
- `copyType`、`copyStatus`、`isIntelligence` 的产品语义尚无完整调用链证据，不能据字段名扩展 UI。
- `CloudCarSettingUpdateBuilder` 的 `SLIDER_FIELDS["10"]`、`SLIDER_FIELDS["52"]` 等是否属于 `updateBikeSettingParams`，需继续追踪 `s20.g()`。
- `5` 号报警音主题 encoder 允许 0..2，但 KMP UI 只有两项；需抓包确认是否存在第三种主题。
