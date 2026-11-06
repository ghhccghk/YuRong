# 修复报告

## 当前基线（2026-11-07）

- Git HEAD（修复前基线）：`c7d2748 fix(settings): block writes for future schema`
- 分支状态：`master` 领先 `origin/master` 11 个提交；本轮修复提交后为 12 个
- 已暂存修改：无
- 未暂存跟踪文件修改：`git status` 显示大量文件，但 `git diff --ignore-space-at-eol --quiet` 成功，已确认均为 CRLF/LF 行尾噪声，没有真实内容修改；本次源码改动另行统计
- 未跟踪文件：存在，均按受保护工作区内容处理
- 已执行静态检查：仓库结构、Gradle 模块与目标、Source Set 文件统计、Smali 文件统计、设置存储契约与调用路径
- 已执行构建：共享 JVM/JS/WasmJS 编译任务成功；Desktop 构建成功；`:shared:compileTestKotlinJvm` 测试源码编译成功；Android Debug 组装成功
- 已执行测试：无（测试源码编译不等于执行测试）
- 真机验证：无；iOS 目标在 Windows 主机上不可编译/运行

## 当前问题

| ID | 优先级 | 安全 | 状态 | 摘要 |
| --- | --- | --- | --- | --- |
| IOS-PREF-001 | P3 | L2 | COMPLETED | iOS 缺失标量设置键未返回声明默认值；已修复，iOS 编译待 macOS 验证 |
| PREF-OBSERVE-002 | P4 | L2 | BLOCKED | 调用链未发现用户可见陈旧状态，缺少扩大平台观察实现的证据 |
| MIRROR-SOURCE-GATE-003 | P3 | L1 | COMPLETED | 无可用帧源平台仍允许启动投屏；已禁用入口并显示原因 |
| NETWORK-PLATFORM-004 | P3 | L3 | BLOCKED | 非 Android TCP/UDP 未实现且异常位于投屏启动协程异常处理范围之外；协议边界待授权 |
| SETTINGS-FUTURE-SCHEMA-005 | P3 | L2 | COMPLETED | 未来 schema 下 typed 缓存与设置写入未统一阻止；已修复，测试源码编译通过、测试未执行 |
| AUTH-CONTACT-CLEAR-006 | P2 | L2 | COMPLETED | 退出登录与换绑忽略本地清理失败并误报成功；已修复，测试源码编译通过、测试未执行 |

## 验证声明

编译通过只证明对应目标能够编译，不证明车辆、云端或真机行为正确。所有未运行的项目必须继续标记为“尚未验证”。

### 本轮执行记录（AUTH-CONTACT-CLEAR-006）

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs '-Pkotlin.incremental=false'`：`BUILD SUCCESSFUL`；15 个 task，5 executed、10 up-to-date。JVM 首次因旧 WSL 增量缓存路径失败，禁用增量后成功；未删除 Gradle 缓存。
- `:shared:compileTestKotlinJvm '-Pkotlin.incremental=false'`：`BUILD SUCCESSFUL`；14 个 task，6 executed、8 up-to-date。仅编译测试源码，不执行测试。
- `:desktopApp:build '-Pkotlin.incremental=false'`：`BUILD SUCCESSFUL`；18 个 task，5 executed、13 up-to-date；`:desktopApp:test` 为 `NO-SOURCE`。
- `:androidApp:assembleDebug '-Pkotlin.incremental=false'`：`BUILD SUCCESSFUL`；59 个 task，7 executed、52 up-to-date。
- 未运行 `jvmTest`、真实登录、云端请求、车辆设置写入、OTA、音效上传或设备操作。
- 当前修改目标是认证会话的本地清理失败反馈；JVM/JS/WasmJS、Desktop 与 Android Debug 已验证编译，iOS 与真机行为尚未验证。
- 当前工作区仍有大量受保护 CRLF/LF 状态噪声和未跟踪用户文件；本项只暂存 7 个明确 pathspec。
