# 车辆设置修复记录

## VS-001 — VIN 与加载代次隔离

- 状态：COMPLETE
- 问题：旧 VIN 的云端设置可能在切换车辆后继续显示，异步旧响应也可能覆盖新 VIN。
- Smali 证据：
  - `com/yadea/smartmoto/vehicle/peripherals/common/BasePeripheralsModel$getBikeConfigs$1.smali:190-241` 每次从 `VehicleDataManager.G().getVin()` 取当前 VIN，再调用 `selectBikeSettingByVin`。
  - `com/yadea/smartmoto/vehicle/carsetting/YdCarSettingActivity$initNetObserver$2.smali:64-94` 收到 `CarSettingBean` 后刷新当前设置页面。
  - 因此请求和渲染必须属于同一当前 VIN；旧请求不能覆盖新 VIN 页面。
- 当前实现：`CarSettingsController.refresh()` 在加载期间保留旧 `items/missingCloudItems`，失败也保留旧值；`runCatching` 还会吞掉协程取消。
- 修复：
  - `CarSettingsState` 增加 `vin`、`loadGeneration`。
  - `refresh()` 开始时清空旧项目并记录 VIN/代次。
  - 允许不同 VIN 发起新加载；同 VIN 加载期间拒绝重复刷新。
  - 响应只在代次和当前 VIN 都匹配时应用。
  - 取消异常继续抛出，其他异常映射为加载失败。
  - 失败清空旧项目并显示错误。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsStateIsolationTest.kt`
  - `docs/agent/vehicle-settings/VEHICLE_SETTINGS_ISSUES.md`
- 测试源码：
  - 新 VIN 刷新前清空旧项目。
  - 旧 VIN 响应不能覆盖新 VIN。
  - 刷新失败不能保留旧 VIN 项目。
- 编译结果：`:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm` 成功；`:desktopApp:build :androidApp:assembleDebug` 成功。测试源码已编译，测试未执行。
- Git commit ID：`b243a59`。
- 未完成事项：测试源码已编写但按指令未执行 `jvmTest`；真实车辆/云端行为未验证。

## VS-002 — TTP 状态与 VIN 绑定

- 状态：COMPLETE
- 问题：`latestVehicleStatus` 只保存 `TtpInfo`，旧 VIN 的 TTP 状态可能覆盖新 VIN 设置。
- Smali 证据：
  - `com/yadea/smartmoto/vehicle/peripherals/common/BasePeripheralsModel$getBikeConfigs$1.smali:196-212` 明确以当前 `VehicleDataManager.G().getVin()` 获取请求 VIN。
  - `TtpInfo` 是车辆状态快照，原页面只对当前车辆渲染；`TtpInfoParser` 本身不携带 VIN，因此 KMP 调用方必须绑定当前 VIN。
  - `YdCarSettingActivity$initNetObserver$2.smali:64-94` 只刷新当前设置页面，不把旧车辆状态跨车辆复用。
- 当前实现：`CarSettingsController.latestVehicleStatus: TtpInfo?` 与 VIN 无关；`MainScreen` 的 TTP effect 只按 `vehicleStatus.hex` 触发。
- 修复：
  - 引入 `VehicleStatusSnapshot(vin, status)`。
  - 新增显式 `updateVehicleStatus(vin, status)`，旧 VIN 状态不回填当前 VIN。
  - VIN 变化时清空缓存状态。
  - `MainScreen` 将 `vehicleProfile.vin` 加入 effect key，并传入显式 VIN。
  - 云端刷新成功后只应用同 VIN 的缓存状态。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/MainScreen.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsControllerTest.kt`
- 测试源码：旧 VIN 状态不能写入当前 VIN；VIN 切换后缓存状态被清理。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`60d5d12`。
- 未完成事项：真实车辆 TTP 切换和真机行为未验证。

## VS-010 — CarSettingsScreen 临时状态随 VIN/加载会话重置

- 状态：COMPLETE
- 问题：分类、详情、滑杆草稿、确认弹窗、自定义音效面板和联系人草稿由无 key 的 `remember` 长期持有，切 VIN 或重新加载后可能展示旧页面状态。
- Smali 证据：
  - `YdCarSettingActivity` 是独立 Activity 生命周期，进入不同车辆页面时 UI 实例和临时输入不会跨车辆复用。
  - `YdCarSettingActivity$initNetObserver$2.smali:64-94` 只在收到当前 `CarSettingBean` 后刷新当前页面。
  - 因此 KMP 的单实例 Compose 页面必须以 VIN/加载会话作为临时状态边界。
- 当前实现：`CarSettingsScreen.kt:60-91` 的临时状态均使用无 key `remember`，只有滑杆草稿依赖条目 ID/值。
- 修复：
  - 引入 `CarSettingsUiResetKey(vin, loadGeneration, loadedFromCloud)`。
  - 分类、详情、弹窗、音效面板、联系人表单、复制状态和滑杆草稿全部绑定 reset key。
  - 联系人表单只接受与当前设置 VIN 匹配的联系人数据。
  - 保留声音试听播放器资源生命周期，不随 UI 草稿重置销毁。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsUiResetKeyTest.kt`
- 测试源码：验证 VIN、加载代次、`loadedFromCloud` 任一变化都会改变 UI reset key。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`35b93f8`。
- 未完成事项：Miuix/M3、明暗、窄屏视觉检查和真机输入行为未验证。

## VS-003 — confId 43/44 恢复为可编辑 Toggle

- 状态：COMPLETE
- 问题：`CloudCarSettingMapper.kindOf()` 先返回 `Info("状态由车辆回读")`，导致后面的 `"43", "44" -> Toggle(false)` 永远不可达，TTP 回填也无法应用到 Info 控件。
- Smali 证据：
  - `ReversingFunctionActivity.getSettingConfigId()` 返回 `43`。
  - `HelpPromoteActivity.getSettingConfigId()` 返回 `44`。
  - `CarSettingsInlineOperationHelper.s()` 分别生成 `REVERSING_OPEN/CLOSE` 与 `STROLLER_OPEN/CLOSE`。
  - `TtpInfo.extended` 提供 `reversingState`、`helpCarState` 回读字段。
- 当前实现：`CarSettingsGateway.kt` 重复匹配 `"43", "44"`，首个 Info 分支遮蔽 Toggle 分支。
- 修复：删除不可达 Info 分支，保留 Toggle 初值与 `CarSettingsController.applyVehicleStatus()` 的 TTP 回填；未修改命令 ID 或帧编码。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsConfIdMappingTest.kt`
- 测试源码：验证 43/44 映射为 Toggle 且存在对应 BLE 命令映射。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`1a68941`。
- 未完成事项：BLE 帧参数仍属于 VS-012 范围；真实车辆倒车/婴儿车行为未验证。

## VS-007 — confId 21 伴我回家灯

- 状态：COMPLETE
- 问题：KMP 标题、控件、命令和 TTP 回填均未识别 confId `21`，云端下发后只得到通用 Info。
- Smali/资源证据：
  - `BackLightActivity.getSettingConfigId()` 返回 `21`。
  - `values/strings.xml` 的 `user_car_back_light` 是“伴我回家灯”。
  - `activity_back_home_light.xml` 同时包含 `YedeaOldSwitch` 与 `IndicatorSeekBar`，`light_time_array` 为 15s/30s/45s/60s。
  - `BackLightActivity.onSwClick()` 使用 `COMMAND_WITH_GO_HOME_OPEN/CLOSE`；`BackLightActivity$a.a()` 使用 `COMMAND_WITH_GO_HOME_TIME_SETTING`。
  - `ch.smali:3321-3352,4193-4214,4690-4711` 确认 commandId 0x85/0x83/0x84。
  - `TtpInfo.extended.withGoHomeStatus/withGoHomeStatus` 提供回读。
- 当前实现：`TITLES`、`kindOf()`、拆分条目和 `VehicleSettingCommands` 均无 `21`。
- 修复：
  - 增加标题“伴我回家灯”和 `LIGHT_IDS` 分类。
  - 基础条目映射为 Toggle，新增稳定 `cloud:21:time` Choice，选项 15/30/45/60 秒。
  - `VehicleSettingCommands` 将 `21` 与 `81` 共用已确认的 0x83/0x84/0x85 命令。
  - TTP 回填同时覆盖 `21` 与 `81`。
  - 未修改 `VehicleSettingFrameEncoder`，避免扩大 BLE 帧风险。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsConfIdMappingTest.kt`
- 测试源码：验证 21 的 Toggle、稳定时间条目、时间选项和命令映射。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`8d73500`。
- 未完成事项：完整 golden frame 属于 VS-012；真实车辆伴我回家灯行为未验证。

## VS-004 — Writer 层阻止只读设置写入

- 状态：COMPLETE
- 问题：控制器虽检查 `item.writable`，但 `MappedCarSettingWriter` 和 `CloudCarSettingWriter` 可被直接调用并绕过 `isAuth=false`。
- Smali 证据：
  - `CarSettingsInlineOperationHelper.smali:2045-2071` 在执行前读取 `BikeConfig.isAuth`，只有 `isAuth == true` 才继续特定分支。
  - `CarSettingsItemAvailabilityHelper.smali:1453-1559` 先按 `ItemAccess` 计算 `BlockReason`，再决定入口/开关是否可用。
  - 因此写入通道自身必须拒绝只读项，不能只依赖 UI disabled。
- 当前实现：两个 concrete writer 直接解析命令/云端字段，没有 `item.writable` 前置检查。
- 修复：在 BLE writer 和 Cloud/BLE fallback writer 的 `writeWithResult()` 入口拒绝 `writable=false`，确保不会调用 sink、fallback 或云端 gateway。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingWriterAccessTest.kt`
- 测试源码：只读项不会触发 BLE sink、BLE fallback 或云端 update。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`59d0175`。
- 未完成事项：伪造合法 `writable=true` 条目的清单级校验由 VS-011 继续处理；真实车辆权限行为未验证。

## VS-011 — 拆分条目稳定 ID 与写入支持校验

- 状态：COMPLETE
- 问题：拆分条目依赖显示名称/隐式条目，writer 没有统一校验稳定 ID、权限和命令支持；伪造拆分 ID 可能进入写入器。
- Smali 证据：
  - `CarSettingsItemAvailabilityHelper.smali:1453-1559` 先解析设置项 `configId`，再通过 `ItemAccess/BlockReason` 判断入口和开关是否可用。
  - `CarSettingsInlineOperationHelper.smali:2045-2071` 在执行前读取 `BikeConfig.isAuth`。
  - 原设置项由 Registry 的固定 configId/条目类型组合产生，不接受任意显示名称或未知拆分 ID。
- 当前实现：split ID 由 mapper 生成，但 writer 只检查 `writable`，没有集中验证拆分后缀、命令映射和云端字段支持。
- 修复：
  - 新增 `CarSettingItemAccess`，维护确认过的拆分后缀白名单。
  - `canWrite()` 同时要求 `writable`、稳定条目 ID、BLE 命令可编码或云端字段可写。
  - `VehicleSettingCommands.supportsItem()` 以 `forItem()` 和完整帧编码结果判断命令支持。
  - BLE/Cloud writer 统一调用该访问校验；未知 `cloud:<confId>:<suffix>` 和无命令/无云端字段条目拒绝写入。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingWriterAccessTest.kt`
- 测试源码：只读项、未知拆分 ID、无写入支持项被拒绝；确认的 `cloud:47:enable` 可进入写入能力判断。
- 编译结果：首次测试编译因缺少 `assertTrue` import 失败，补齐后共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`fc134c8`。
- 未完成事项：`CloudCarSettingMapper` 的 UI `writable` 展示仍以 `isAuth` 为主；真实云端权限和车辆行为未验证。

## VS-006 — 缺失默认值改为 UNKNOWN

- 状态：PARTIAL
- 问题：`CloudCarSettings.boolValue()` 返回 null 时，KMP 多数 Toggle 通过 `== true` 变成 false；固定 Choice/Slider 初值也可能伪装为已知状态。
- Smali 证据：
  - `CarSettingBean$BikeConfig.smali:20-50` 保留云端原始字段，缺失字段不应等同于关闭值。
  - `CarSettingsItemAvailabilityHelper.smali` 以 `ItemAccess/BlockReason` 区分可用、不可用和未知前置条件，不把缺失状态直接当作关闭。
  - TTP 字段使用 `-1` 表示未上报，`TtpInfo.kt` 已明确区分未上报和 0。
- 当前实现：`Toggle/Choice/Slider` 没有值是否已知的维度；缺失布尔值被渲染为 false。
- 修复：
  - `Toggle/Choice/Slider` 增加 `valueKnown`。
  - 云端布尔值缺失时映射为 `valueKnown=false`，非空值才视为已知。
  - TTP 可回填的开关初值改为 UNKNOWN，回填后由 `withToggle/withIndex/withSlider` 标记为已知。
  - UI 在值未知时显示“状态未知”并禁用控件；用户确认提交后标记为已知。
  - 保留 `0/1` 语义和已确认范围不变。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/ui/screens/CarSettingsScreen.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsConfIdMappingTest.kt`
- 测试源码：缺失布尔值为 UNKNOWN，云端非空值为已知。
- 编译结果：首次编译暴露 Slider 构造器缺少 `CarSettingKind` 继承声明，补齐后共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。测试未执行。
- Git commit ID：`e5aea6c`。
- 未完成事项：Choice/Slider 的所有固定默认值仍需逐项取得 Smali/抓包证据；`Customize` 四字段整体 UNKNOWN 状态和真实车辆回读尚未完成。
