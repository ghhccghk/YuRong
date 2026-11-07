# 车辆设置修复计划

## 0. 执行约束

本轮仅形成计划。后续修复必须逐项独立提交，先完成 Smali 证据闭环，再修改代码。不得修改 BLE、Wi-Fi、投屏、OTA 或车辆帧协议；涉及 L3 的 VS-005、VS-012 默认只读，只有明确授权和 golden frame 证据后才能实施。

## 1. 推荐修复顺序

### 第一阶段：状态隔离与可见性

1. `VS-001`：给 `CarSettingsController` 增加当前 VIN/加载代次；`refresh()` 开始时清理或标记旧 items，响应只接受当前 VIN。
2. `VS-002`：`updateVehicleStatus()` 保存 `(vin, TtpInfo)`；切 VIN 立即清除旧 TTP，回填前核对 VIN。
3. `VS-010`：`CarSettingsScreen` 以 VIN 和 `loadedFromCloud` 重置分类、展开项、滑杆草稿和确认弹窗。

验证：构造两个 VIN 的 fake gateway/status，断言切换后不显示旧 items/旧 TTP；编译共享目标和 Android/Desktop。禁止真实请求。

### 第二阶段：映射和权限

4. `VS-003`：移除 `kindOf()` 中提前的 `"43","44" -> Info`，让 Toggle 与 TTP 回填生效；为 43/44 增加回归测试。
5. `VS-007`：补齐 confId `21` 的标题、控件、命令证据；若帧证据仍不足，保留 UNKNOWN 只读入口，不发送猜测命令。
6. `VS-004`、`VS-011`：统一 `ItemAccess`/`writable` 判定到 writer 层，拆分条目按稳定条目 ID 校验权限和命令支持。
7. `VS-006`：逐项建立缺失字段默认值证据；没有原默认值的项目显示 UNKNOWN，不伪造关闭状态。

验证：纯 mapper/writer 测试、权限矩阵测试、双主题静态检查；不运行真实登录或车辆写入。

### 第三阶段：云端写入和确认

8. `VS-008`：追踪 `s20.g()`、`MixRequest.j()/k()` 和请求服务接口，区分 `updateBikeSetting`、`updateBikeSettingParams`、`valueJson`；只有字段和 HTTP 语义确认后才扩展 builder。
9. `VS-009`：确认 `CheckReportLossBean.result` 枚举、超时、失败回调和轮询终止条件；再决定 `checkStatus()` 是否继续使用 `result == 0`。
10. `VS-005`：BLE 写入增加车辆确认/回读或明确的“已发送未确认”状态；不得把 sink 返回 true 当作车辆已生效。

验证：离线 fake transport、fake BLE sink、状态轮询测试；真实云端/车辆操作仍禁止。

### 第四阶段：BLE golden frame

11. `VS-012`：逐条从 `BleBicycleCommandManager.generateBleCommand` 导出命令 ID、参数范围、位域和完整帧；建立 golden frame 测试后才允许修 `VehicleSettingCommands`/`VehicleSettingFrameEncoder`。

验证：纯十六进制帧测试；不得写入真实 BLE。

## 2. 每项修复的必备检查

- 记录 Smali 文件、类、方法、行号和关键指令。
- 明确 confId、稳定条目 ID、云端字段、参数类型、范围、0/1 方向。
- 证明 UI 控件、提交转换、权限判断和回读字段是一致的。
- 检查空值、未知值、超范围、快速连续修改、切 VIN、断连、请求失败和页面退出。
- 检查 Miuix 与 Material 3 的业务回调、启用状态和提交行为一致。
- 运行允许的编译验证；没有运行的测试/真机验证必须写成“尚未验证”。

## 3. 测试矩阵（后续修复使用）

| 层级 | 测试内容 | 是否允许本轮执行 |
| --- | --- | --- |
| mapper | bikeConfigVos、分组、排序、isAuth/izLab、拆分 ID | 可写测试源码，不执行 jvmTest |
| value | Boolean/Int/Choice/Slider 转换、范围、0/1、多字段覆盖 | 可写测试源码，不执行 jvmTest |
| state | VIN 切换、旧状态隔离、失败回滚、pending 并发 | 可写测试源码，不执行 jvmTest |
| API | 请求路径、HTTP 方法、签名字段、响应解析 | 只用 fake transport；禁止真实网络 |
| BLE | commandType/commandId/参数/帧 hex | 只用 golden frame；禁止真实写入 |
| UI | 双主题、明暗、窄屏、长文本、禁用态 | 仅静态检查；截图需用户明确授权后进行 |

## 4. 停止条件

- 需要真实车辆、云端写入、OTA 或用户账号操作时暂停。
- Smali/抓包不能确认命令位域、参数范围或返回语义时标记 UNKNOWN，不自行补全。
- 修复涉及 BLE/OTA/投屏协议时等待明确授权。
- 工作区中用户已有未提交文件、日志、APK、审计文档不得纳入提交。