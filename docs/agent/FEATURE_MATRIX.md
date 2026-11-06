# 功能对照矩阵

状态定义：

- `已实现`：当前代码存在完整入口，仍需按具体功能完成行为验证。
- `部分实现`：主路径存在，但已发现明确缺口。
- `只读审计`：安全等级 L3，本轮不修改。
- `待核验`：尚未完成 Smali 调用链与 KMP 行为对照。

| 功能域 | KMP 入口 | 当前状态 | 证据 / 已知问题 | 验证边界 |
| --- | --- | --- | --- | --- |
| 双主题 UI | `shared/.../ui/theme/UiTheme.kt`、`ui/components/` | 已实现，待视觉核验 | 语义 API 与两套主题分支存在 | 未做两主题、明暗、宽窄屏截图检查 |
| 外观设置持久化 | `shared/.../settings/AppPreferences.kt` | 部分实现 | `PreferenceStore` 契约清楚；iOS 缺失标量键默认值已发现 `IOS-PREF-001` | 修复后执行允许的共享编译 |
| 平台设置后端 | `shared/src/*Main/.../settings/` | 部分实现 | Android/JVM/JS/WasmJS 缺失键行为一致；iOS 例外 | 真机 iOS 行为未验证 |
| 设置观察 | `AppPreferences.observe()` 与各后端 `observe()` | 待核验 | 非 Android 后端当前为一次性快照流，是否存在实际陈旧状态需调用链证据 | 未运行测试 |
| 登录态与设备标识 | `AppPreferences`、认证相关代码 | 待核验 | 存储分离和清理入口存在 | 禁止真实登录 |
| 车辆设置云端映射 | `carsetting/CarSettingsGateway.kt`、`VehicleSettingCommands.kt` | 只读审计 | 命令映射属于 L3，不能基于字段名修改 | 禁止真实车辆写入 |
| 自定义音效 | `carsetting/CustomSoundGateway.kt` 等 | 只读审计 | 涉及云端资源和音效应用 | 禁止上传、应用和真实请求 |
| 云端主题 | `vehicle/VehicleCloudTheme.kt` | 待核验 | 已有 VIN 缓存与背景映射约束 | 禁止真实云端请求 |
| BLE / OTA / 投屏协议 | 各平台适配与协议层 | 只读审计 | 属于 L3，本轮默认不修改 | 禁止设备操作 |
| 桌面小组件 | `androidApp/.../widget/` | 待核验 | 已有多项近期提交，需继续对照原行为 | 未做 Android 真机小组件检查 |
| HTTP 平台能力 | 各平台网络适配 | 待核验 | iOS/JS/WasmJS 是否存在显式不可用实现需单独审计 | 禁止真实请求 |

## 下一步审计顺序

1. 完成 `IOS-PREF-001` 并验证共享编译。
2. 审计 `PREF-OBSERVE-002`，只有能证明会产生用户可见陈旧状态时才修复。
3. 审计非 Android HTTP 能力的显式降级是否在公共 UI 正确表达。
4. 继续选择有 Smali 调用链或明确跨平台契约支撑的独立问题。
