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

## VS-008 — 云端写入路径与 valueJson 参数语义

- 状态：PARTIAL
- 问题：KMP 将所有设置写入统一发往 `vehiclebase/bike-setting/updateBikeSetting`，参数型设置没有表达 `low-freq/common-cmd/param-setting`、`oper/id/value/valueJson`，且请求签名 JSON 把所有值统一当字符串。
- Smali 证据：
  - `y00.smali:580-585`：`y00.a()` 为 `@FormUrlEncoded @POST("vehiclebase/bike-setting/updateBikeSetting")`，返回 `YApiResult<Boolean>`。
  - `y00.smali:336-341`：`y00.H()` 为 `@FormUrlEncoded @POST("low-freq/common-cmd/param-setting")`，返回 `YApiResult<String>`。`MixRequest.updateBikeSettingParams` 是调用方法名，不是 HTTP 路径。
  - `MixRequest.smali:357-500`：`g()/h()` 用 `s20.g()` 生成参数、注入 VIN，再调用 `y00.a()`。
  - `MixRequest.smali:504-649`：`j()/k()` 同样使用 `s20.g()`，最终调用 `y00.H()`。
  - `jp6.smali:977-1038,1041-1110`：`jp6.o()` 与 `jp6.p()` 均通过 `f84.b()` 生成参数 JSON，再分别提交到上述两个路径。
  - `f84.smali:20-52`：`_gateway` 由 `f84.a()` 注入；`f84.b()` 使用 Gson 保留 Map 值对象类型。
  - `s20.smali:864-1925`：确认 47/48/60/61/62/75/76/82/91 等分支字段。
  - `op6.smali:39-120,279-363` 与 `VehicleParamBean.smali:7-24,69-90`：确认 single/capacity `valueJson` 的元素、字段顺序、byteCount/dataType/sort/value。
- 当前实现：`CloudCarSettingUpdate` 只有字段 Map；KMP 所有写入都走 `UPDATE_PATH`，47/48/60/61/62/76 被误写为普通云端字段或缺少 `valueJson`。
- 修复：
  - 增加 `CloudCarSettingUpdatePath.BIKE_SETTING/PARAM_SETTING`，按确认路径提交。
  - 为 47、48、60、61、62、76 生成 Smali 已确认的 `oper/id/value/valueJson`。
  - 按 `op6` 和 `VehicleParamBean` 生成 single/capacity 参数数组。
  - `CloudCarSettingUpdate.fields` 改为 `Map<String, Any?>`，网关签名 JSON 保留 Number/String 类型。
  - 普通设置继续走 `vehiclebase/bike-setting/updateBikeSetting`。
  - 增加 fake transport 离线请求测试，解密网关签名后检查 URL、字段和 `valueJson`。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CloudCarSettingsApiSemanticsTest.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/Phase4CarSettingsTest.kt`
- 测试源码：
  - 普通设置使用 `updateBikeSetting`。
  - 47/48/60/61/62/76 使用 `low-freq/common-cmd/param-setting`。
  - 60 capacity `valueJson` 为四元素数组。
  - 62 switch `valueJson` 为单元素数组。
  - 请求业务 JSON 保留数值类型。
- 编译结果：
  - 首次编译失败：交接时未完成的 `update()` 调用缺少 `fields/token/)`，同时 `CHECK_STATUS_PATH` 被错误覆盖，报 `CarSettingsGateway.kt:291` 语法错误和 `:760` getter/setter 错误；已恢复。
  - 最终执行 `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm :desktopApp:build :androidApp:assembleDebug` 成功。
  - 测试源码已编译，`jvmTest` 未执行；没有真实云端或车辆请求。
- Git commit ID：`a4fc97e`。
- 未完成事项：
  - `VehicleSettingViewModel.P4()` 某些主字段会同时提交当前状态伴随字段；KMP 当前只提交用户修改的已确认字段，尚不能证明缺少伴随字段在所有接口版本中等价。
  - 普通设置部分字段的 String/Integer 类型仍按 `P4()` 逐项核对。
  - `YApiResult<String>` 业务返回、超时和重试语义属于 VS-009；HTTP 成功不代表车辆已确认，车辆确认属于 VS-005。
  - 真实云端写入和车辆生效均尚未验证。

## VS-009 — CheckReportLossBean.result 与轮询终止语义

- 状态：COMPLETE
- 问题：KMP 将 `result == 0` 视为成功，但 HTTP/请求错误返回 `null` 后继续轮询；同时把响应顶层 `result` 作为回执，未证明它等同于 `CheckReportLossBean.data.result`。
- Smali 证据：
  - `y00.smali:179-218`：状态查询是 `@GET("common-cmd/low-freq/info/param")`，返回 `YApiResult<CheckReportLossBean>`。
  - `CheckReportLossBean.smali:7-65`：`result` 是 `String`，没有枚举常量。
  - `MixRequest$checkSettingStatus$1.smali:207-239`：请求 Map 经 `f84.a()` 增加 `_gateway=True`，最终查询字段为 `_gateway/id`。
  - `MixRequest$checkSettingStatus$3.smali:72-142`：只读取 `data.result`；`"0"` 回调 true，非零或不可解析回调 false，data/result 缺失不回调并继续等待。
  - `MixRequest$checkSettingStatus$2.smali:72-112`：网络/请求错误回调 false。
  - `CarSettingsIotPollingHelper.smali:h()` 与 `startPoll$1$1/startPoll$2`：每秒查询，最多 30 次；true/false 立即终止并分别走 success/failure，倒计时耗尽走 timeout。
- 当前实现：`checkStatus()` 缺少 `_gateway`，HTTP/transport 失败返回 null，且从顶层回退读取 `result`；控制器把异常当 pending。
- 修复：
  - 状态查询签名字段使用 `_gateway=True` 与 `id`。
  - 只读取 `data.result`。
  - `"0"` → true；非零、空/不可解析 → false；data/result 缺失 → null。
  - transport 无响应、HTTP 非 2xx、业务失败或查询异常立即 false 并终止轮询。
  - 保留 30 次、1 秒间隔；持续 null 才进入超时回滚。
  - 协程取消继续抛出，不被误当查询失败。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsGateway.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CloudSettingStatusPollingTest.kt`
- 测试源码：
  - fake transport 校验 GET 路径、`_gateway/id` 签名明文和 result 映射。
  - fake gateway 离线轮询验证 true/false 立即终止、pending 超时、异常终止。
- 编译结果：
  - 首次测试编译失败：测试误用不存在的 `CarSettingChannel.CLOUD`，改为已确认的 `CarSettingChannel.IOT`。
  - 最终共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。
  - `jvmTest` 未执行；没有真实云端或车辆请求。
- Git commit ID：`2cfdabb`。
- 未完成事项：真实接口响应和网络错误样本未验证；HTTP 成功仅代表查询结果，不代表车辆已生效，后者仍属 VS-005。

## VS-005 — BLE 写入接受与车辆确认分离

- 状态：COMPLETE
- 问题：`VehicleSettingCommandSink.send()` 返回 true 只表示本地写入/发送成功，但控制器把它当作设置已保存且没有车辆确认状态；云端接受也被直接当最终成功。
- Smali 证据：
  - `CarSettingBaseItemView.smali:977-1070`：`i()` 创建 `CommandTempBean` 并调用返回 `void` 的 `VehiclePlugin.h()`，没有 ACK 字段或回调。
  - `CarSettingsInlineOperationHelper.smali:1048-1125`：BLE 分支提交命令后没有 success/failure/timeout 车辆回调。
  - `CarSettingsInlineOperationHelper$performSwitch$1.smali:64-92`：`onCommitUi` 只提交 UI 目标值，不代表车辆生效。
  - `CarSettingsInlineOperationHelper.smali:1128-1211`：只有 IoT 分支注册 `CarSettingsIotPollingHelper$a`。
  - `CarSettingsIotPollingHelper$startPoll$1$1.smali:64-235`：`CheckReportLossBean.result=0` 后才执行 success；false 立即失败。
  - `TtpInfo.extended`：支持回读的设置项可由后续车辆状态匹配确认。
- 当前实现：`CarSettingWriteResult` 只有 success/channel；BLE sink true 后直接清空提示，没有“已发送未确认”状态。
- 修复：
  - 新增 `CarSettingConfirmationStatus`：`LOCAL_UPDATED`、`CLOUD_ACCEPTED`、`VEHICLE_CONFIRMED`、`FAILED`、`UNKNOWN`。
  - `CarSettingWriteResult` 携带确认状态；BLE sink true 只标记 `LOCAL_UPDATED`，sink false 标记 `FAILED`。
  - 云端请求成功只标记 `CLOUD_ACCEPTED`，不根据 HTTP 成功声称车辆已生效。
  - `CarSettingsState.confirmationStatuses` 按稳定条目 ID 保存状态。
  - BLE/云端未确认时显示“已发送/已提交，等待确认”。
  - 后续 TTP 必须来自写入后的新状态代次且匹配目标值，才升级为 `VEHICLE_CONFIRMED`。
  - VS-009 已确认的 `result=0` 查询成功也升级为 `VEHICLE_CONFIRMED`；失败/超时回滚并标记 `FAILED`。
  - 无通道或自定义 writer 无法判断确认时使用 `UNKNOWN`。
  - 未修改命令 ID、参数、帧编码或 BLE 协议。
- 修改文件：
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/CarSettings.kt`
  - `shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingCommands.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingsControllerTest.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CarSettingWriterAccessTest.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CloudCarSettingWriterTest.kt`
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/CloudSettingStatusPollingTest.kt`
- 测试源码：
  - fake BLE sink true/false 分别得到 `LOCAL_UPDATED/FAILED`，不会得到 `VEHICLE_CONFIRMED`。
  - BLE 本地更新在旧/不匹配 TTP 下保持未确认，新 TTP 匹配后才确认。
  - 云端接受、查询确认、查询失败、未知 writer 状态分别验证。
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功；Desktop/Android 构建成功。`jvmTest` 未执行；没有真实 BLE 或车辆请求。
- Git commit ID：`8e36146`。
- 未完成事项：真实车辆 ACK/回读时序和各设置项确认延迟未验证；没有 TTP 回读的条目会保持 `LOCAL_UPDATED/CLOUD_ACCEPTED`，不会伪造 `VEHICLE_CONFIRMED`。

## VS-012 — BLE Golden Frame 核对

- 状态：PARTIAL
- 问题：`VehicleSettingFrameEncoder` 有多条固定 suffix/位域实现，但此前测试只验证“能编码”，没有用原始 `BleBicycleCommandManager.generateBleCommand` 的完整十六进制帧做回归。
- Smali 证据：
  - `BleBicycleCommandManager.smali:125-189`：`getCheckResult()` 对 `ORDER_ID + LEN + PAYLOAD` 做 SUM8。
  - `BleBicycleCommandManager.smali:5147-5208`：外层帧为 `5944 + ORDER_ID + payloadLen + payload + check + 4B4A`。
  - `generateBleCommand():674-721`：使用运行时版本字段拼接 payload 前缀。
  - `0x0F/0x10/0x11/0x12/0x13/0x14`：`pswitch_data_f62` 与 `:4974-4985` 的固定 suffix。
  - `0x36`：`:4630-4660`，参数按 `%02x` 写入第 5 个 payload 字节。
  - `0x53/0x54`：`:3884-3895`。
  - `0x62/0x63`：`sparse-switch:5473-5475` → `:3655-3665`。
  - `0xD2/0xD3`：`:2456-2484` 调用 `uj.c(0x35, value, 4..5/6..7)`；`uj.c` 位于 `uj.smali:152-264`。
  - `0x141` 不在 `generateBleCommand` 分支，而在 `generateControlCommand():6574-6750`，使用 `uj.o/uj.g`。
- 当前实现：Golden 测试缺失；部分命令仅有编码器注释，无法逐帧证明。
- 修复：
  - 新增 `VehicleSettingGoldenFrameTest.kt`，固定 0x0F/10/11/12/13/14、0x36(80)、0x53/54、0x62/63、0xD2(0/1)、0xD3(2) 的完整帧。
  - 测试同时校验帧头、动态长度、版本/绑定前缀、payload、SUM8 和帧尾。
  - 未修改 `VehicleSettingCommands`、`VehicleSettingFrameEncoder` 或任何 BLE/OTA/投屏协议。
  - 其余命令保持 UNKNOWN，不生成猜测帧。
- 修改文件：
  - `shared/src/jvmTest/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingGoldenFrameTest.kt`
- 编译结果：共享 JVM/JS/WasmJS 与测试源码编译成功。测试源码已编写但 `jvmTest` 未执行；无真实 BLE 写入。
- Git commit ID：`34da8b5`。
- 未完成事项：
  - 其余 `VehicleSettingFrameEncoder` 命令尚未逐条建立 Golden Frame。
  - `0x141` 需要单独追踪 `generateControlCommand` 的 `uj.o/uj.g` 编码，不能继续声称来自 `generateBleCommand`。
  - 任何命令在完整 hex、位域、参数范围闭环前继续只读/UNKNOWN。

# 第二轮修复记录

## VS-013 — 能量回收使用滑行回馈字段

- 状态：COMPLETE
- 根因：普通 `EnergyRecoveryActivity` 的开关/档位由 `slideModePowerRecoverStatus/Current` 回填；KMP 错用了 `brakeModePowerRecoverFunctionStatus/GearStatus`。同时 `l76.z()` 的制动回馈档位是 `0x27 bit0-2`，KMP 原解析为 bit3-5。
- Smali 证据：`EnergyRecoveryActivity.smali:88,480-642`、`VehicleSettingViewModel.smali:22282-22485`、`l76.smali:9535-9567`。
- 修改：`TtpInfo` 新增 `slideModePowerRecoverStatus/Current`，按 `G(0x26,0)`、`C(0x26,2)` 解析；17/18 回填改用新字段；修正制动回馈档位偏移。
- 测试：`TtpInfoParserTest.parse_energyRecoveryUsesSlideModeFields`。测试源码已编译，未执行。
- Commit：`27afae7`。
- 未完成：真实车辆回读由用户后续验证。

## VS-014 — 自定义按键 TTP 回填

- 状态：PARTIAL
- 根因：`customFunctionShort/Long` 已解析，但 `CarSettingsController.applyVehicleStatus()` 没有 config `1005` 分支，短按/长按不回填。
- Smali 证据：`CustomizeKeyActivity.getFunctionId/getFunctionName:107-370`、`VehicleSettingViewModel.smali:24730-24828`、`l76.smali:12284-12300`。
- 修改：1005 短按读 `customFunctionShort`、长按读 `customFunctionLong`；函数 ID 0..8 原序映射，14/其他映射“无定义”，-1 保持 UNKNOWN。
- 测试：`CarSettingsControllerTest.customKeyFunctionsReadBackFromCustomFunctionBits`。
- Commit：`72c3b1a`。
- 证据缺口：当前 Smali 选项中没有“辅助推行”字符串，无法把该名称绑定到特定函数 ID；该项名称映射继续 UNKNOWN，不猜测 ID。

## VS-015 — 自适应推行变体语义

- 状态：COMPLETE
- 根因：config 64 与 89 不是同一组 0/1 语义。
- Smali 证据：
  - `HelpPromotePlusActivity.onCbClick():94-121`：64 勾选写 0、取消写 1。
  - `HelpPromotePlus2Activity.onCbClick():94-129`：89 勾选写 0、取消写 2。
  - 两者的 SeekBar listener 直接写 `thumbPosition` 0..2。
  - Binding：64 CheckBox 读 `customDriveSwitchStatus == 1`；89 CheckBox 读 `== 2`。
- 修改：64/89 分别按 1/2 映射勾选状态；89 取消写 2；推行速度改为 0..2，初值 UNKNOWN。
- 测试：变体回读、64/89 开关参数、0 档写入测试。
- Commit：`0e1f8c9`。
- 未完成：真实车辆回读由用户后续验证。

## 第二轮 VS-006 补充

- 状态：PARTIAL
- 已将 18、1005、64/89 等无即时证据初值标记 UNKNOWN；Customize 四字段确认来自 TTP `customizeThrottleSensitivity/customizeEnergyLevel/customizeEnergyState/customizeTcs`。
- 其余固定 Choice/Slider 默认值仍需逐项 Smali 证据。
- Commit：`48c1afa`。

## 第二轮 VS-008 补充

- 状态：PARTIAL
- 按 `P4()` 修正 75/82 Integer 类型；77 同时写 `sleepMode` 并以反值写 `SLEEP_MODE_SWITCH`；40 开启写 `colorEggSoundEffect`、关闭走 `CLEAR_SOUND_EFFECT`。
- `Map<String, Any?>` 已保留 Number/String 类型。
- 9/10/82 等伴随字段仍需要当前 `CarSettingBean` 状态，当前 item/writer 接口无法完整携带。
- Commit：`e57caeb`。

## VS-007 与 VS-011 复核

- VS-007：COMPLETE。已为 0x83/0x84/0x85 建立完整 Golden Frame，覆盖开、关和 15/30/45/60 秒参数。
- VS-012：PARTIAL。新增样本 commit `20b74d7`；其他命令继续 UNKNOWN。
- VS-011：BLOCKED。`supportsItem()` 仍只能证明 encoder 可编码；全量命令的 Smali/Golden 证据表未建立。直接收紧会禁用用户已实测正常项，直接放宽会违反“无证据不开放写入”，因此本轮不改变写入白名单。
