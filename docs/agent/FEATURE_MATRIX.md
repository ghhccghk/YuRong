# 功能对照矩阵

状态定义：

- `已实现`：当前代码存在完整入口，仍需按具体功能完成行为验证。
- `部分实现`：主路径存在，但已发现明确缺口。
- `只读审计`：安全等级 L3，本轮不修改。
- `待核验`：尚未完成 Smali 调用链与 KMP 行为对照。

| 功能域 | KMP 入口 | 当前状态 | 证据 / 已知问题 | 验证边界 |
| --- | --- | --- | --- | --- |
| 双主题 UI | `shared/.../ui/theme/UiTheme.kt`、`ui/components/` | 已实现，待视觉核验 | 语义 API 与两套主题分支存在 | 未做两主题、明暗、宽窄屏截图检查 |
| 外观设置持久化 | `shared/.../settings/AppPreferences.kt` | 已实现，编译验证 | `PreferenceStore` 契约清楚；iOS 缺失标量键默认值已由 `IOS-PREF-001` 修复 | iOS 真机行为未验证 |
| 平台设置后端 | `shared/src/*Main/.../settings/` | 已实现，编译验证 | Android/JVM/JS/WasmJS 缺失键行为一致；iOS 已补齐声明默认值 | iOS 真机行为未验证 |
| 设置观察 | `AppPreferences.observe()` 与各后端 `observe()` | 未发现可证实缺陷 | `PREF-OBSERVE-002` 调用链未发现用户可见陈旧状态；不再扩大实现改动 | 静态调用链审计；未运行测试 |
| 登录态与设备标识 | `AppPreferences`、认证相关代码 | 待核验 | 存储分离和清理入口存在 | 禁止真实登录 |
| 车辆设置云端映射 | `carsetting/CarSettingsGateway.kt`、`VehicleSettingCommands.kt` | 只读审计 | 命令映射属于 L3，不能基于字段名修改 | 禁止真实车辆写入 |
| 自定义音效 | `carsetting/CustomSoundGateway.kt` 等 | 只读审计 | 涉及云端资源和音效应用 | 禁止上传、应用和真实请求 |
| 云端主题 | `vehicle/VehicleCloudTheme.kt` | 待核验 | 已有 VIN 缓存与背景映射约束 | 禁止真实云端请求 |
| BLE / OTA / 投屏协议 | 各平台适配与协议层 | 只读审计 | 属于 L3，本轮默认不修改 | 禁止设备操作 |
| 投屏能力门控 | `ui/screens/MirrorScreen.kt`、`ui/state/AppViewModel.kt` | 已实现，编译验证 | `MIRROR-SOURCE-GATE-003`：无可用帧源时禁用“一键投屏”和“开始投屏”并显示原因；未改变投屏协议 | 共享 JVM/JS/WasmJS 与 Desktop 编译通过；Android 构建受 SDK 阻塞；真机未验证 |
| 桌面小组件 | `androidApp/.../widget/` | 待核验 | 已有多项近期提交，需继续对照原行为 | 未做 Android 真机小组件检查 |
| HTTP 平台能力 | 各平台网络适配 | 已审计，未发现假成功 | 平台不可用时返回 `null`，调用方映射为 `null`、`Failure`、`false` 或空列表；未发现把不可用当成功 | 静态调用链审计；禁止真实请求 |

## 下一步审计顺序

1. 已完成 `IOS-PREF-001`，共享目标与 Desktop 构建通过；iOS 真机待验证。
2. `PREF-OBSERVE-002` 因缺少用户可见陈旧状态证据而阻塞，不重写非 Android `observe()`。
3. 已审计非 Android HTTP 能力的显式降级，未发现假成功路径。
4. 继续选择有 Smali 调用链或明确跨平台契约支撑的独立问题，优先处理 P3/P4 平台兼容缺陷。
