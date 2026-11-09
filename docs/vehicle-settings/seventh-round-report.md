# 车辆设置第七轮报告

## 范围

本轮以当前 KMP 车辆设置 UI 为入口完成未实现选项审计，并修复证据充分、低风险的未知初值问题。未修改数字车钥匙、BLE/Wi-Fi/投屏/OTA、自定义距离标定和既有实车保护项。

## 审计结果

完整清单见 `docs/vehicle-settings/unimplemented-settings-audit.md`。

- 当前数字 confId：81。
- 静态完整链路：45。
- 部分实现：17。
- 只有写入/无可靠车辆回读：12。
- 只有读取：2。
- 条件性/隐藏：5。
- 原 App 存在但 KMP 无独立入口：5。
- 云端未知 confId 会生成只读 `Info`，不按名称猜测业务。

## 本轮修改

1. `55cdd5b`：confId 14/20 的 Slider 在 TTP 到达前保持 UNKNOWN。
2. `1b2c248`：confId 21/81 的延时灯时间 Choice 在 TTP 到达前保持 UNKNOWN。
3. `719103f`：confId 27/31/54 的 Choice 在 TTP 到达前保持 UNKNOWN。

修改仅涉及 `CarSettingsGateway.kt` 的初始 `valueKnown` 和测试源码，不涉及命令、协议、TTP 解析或用户保护项。

## 未修改问题

- confId 50 仪表风格缺少可靠回读；当前 UI 会把“未知值”直接禁用，需先分离用户草稿和车辆回读状态。
- 自定义距离标定缺少完整帧/状态机/结果回读证据。
- 低功耗属于真实车辆云控制，不自动迁移。
- 多功能键、启动灯缺少保存通道和命令证据。
- 温度单位缺少持久化和车辆行为证据。
- VS-015 64/89 当前读取范围与既有 Smali 证据存在回归风险；本轮按保护范围不修改，留给用户确认后单独处理。

## 验证

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs`：通过。
- `:shared:compileTestKotlinJvm`：通过。
- `:androidApp:assembleDebug :desktopApp:build`：通过。
- 测试源码已编写并编译。
- `:shared:jvmTest`：未执行。
- 真实云端、BLE、车辆、真机：未验证。

## Git

基线 HEAD：`7a94294`。

- `7128df3`：第七轮审计文档。
- `55cdd5b`、`1b2c248`、`719103f`：三项 UNKNOWN 初值修复。
- 本轮文档提交见后续 commit。
