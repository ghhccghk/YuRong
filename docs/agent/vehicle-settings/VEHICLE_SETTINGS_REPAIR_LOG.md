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
