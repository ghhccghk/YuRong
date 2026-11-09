# 车辆设置第二轮修复报告

## A. 原 App 设置映射表

完整截图清单和逐项映射见 `VEHICLE_SETTINGS_SCREEN_MAPPING.md`。本轮以 18 张截图确认分组和控件，以 Smali 确认字段、转换和命令，未根据截图名称推断协议。

三个重点项摘要：

| 设置项 | 原 Activity | 原字段/转换 | KMP ID | 写入 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 能量回收 | `EnergyRecoveryActivity` | `slideModePowerRecoverStatus/Current`；0x26 bit0-1/bit2-4；1/2/3 | `cloud:17`, `cloud:18` | BLE 0x13/0x14、0x3D | COMPLETE |
| 自定义按键辅助推行 | `CustomizeKeyActivity` | `customFunctionShort/Long`；0x6C 高/低 4 位 | `cloud:1005:short/long` | BLE 0x131/0x130 | PARTIAL：通用回读已修，名称到 ID 缺证据 |
| 自适应推行 | `HelpPromotePlusActivity/2` | 64 状态==1/取消1；89 状态==2/取消2；档位0..2 | `cloud:64:*`, `cloud:89:*` | BLE 0xD0/0xD1 | COMPLETE |

## B. 三个回读异常根因

1. VS-013：KMP 使用了制动回馈字段而非普通能量回收的滑行回馈字段，并且制动回馈档位偏移错误。
2. VS-014：KMP 已解析自定义按键字段，但控制器没有 1005 回填分支；“辅助推行”名称到函数 ID 缺少 Smali 证据。
3. VS-015：64/89 使用不同取消值和不同回读值，KMP 统一按 0/1 与 1..3 处理。

## C. 正常设置保护

本轮只修改 17/18、1005、64/89 的字段读取和值转换，以及 VS-008 已确认的 40/75/77/82 请求类型。没有修改其他用户已实测正常的 TTP 映射、双主题 UI、BLE 连接、Wi-Fi、投屏、OTA 或帧协议。

## D. 修复状态

| 项目 | 状态 |
| --- | --- |
| VS-013 能量回收 | COMPLETE |
| VS-014 自定义按键辅助推行 | PARTIAL |
| VS-015 自适应推行 | COMPLETE |
| VS-006 默认值/UNKNOWN | PARTIAL |
| VS-008 云端参数语义 | PARTIAL |
| VS-007 伴我回家灯帧证据 | COMPLETE |
| VS-011 写入证据分层 | BLOCKED |
| VS-012 Golden Frame | PARTIAL |

## E. 验证情况

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm`：通过。
- `:androidApp:assembleDebug :desktopApp:build`：通过。
- 新增测试源码已编译。
- `jvmTest` 未执行。
- 没有真实云端写入、BLE 发送、车辆设置写入、OTA 或音效上传。
- 三个异常项的真实车辆回读尚未由 Agent 独立复现，待用户实车复测。

## F. Git 提交

| 提交 | 内容 |
| --- | --- |
| `4c4d9f5` | 截图映射与第二轮问题定位 |
| `27afae7` | VS-013 能量回收字段修复 |
| `72c3b1a` | VS-014 自定义按键回读 |
| `0e1f8c9` | VS-015 自适应推行变体修复 |
| `48c1afa` | VS-006 UNKNOWN 补充 |
| `e57caeb` | VS-008 P4 类型/参数补充 |
| `20b74d7` | VS-007/012 Golden Frame 补充 |
