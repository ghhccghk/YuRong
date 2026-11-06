# 修复报告

## 当前基线（2026-11-07）

- Git HEAD：`035750d fix(settings): honor iOS defaults for missing keys`
- 分支状态：`master` 领先 `origin/master` 9 个提交
- 已暂存修改：无
- 未暂存跟踪文件修改：`git status` 显示大量文件，但 `git diff --ignore-space-at-eol --quiet` 成功，已确认均为 CRLF/LF 行尾噪声，没有真实内容修改；本次源码改动另行统计
- 未跟踪文件：存在，均按受保护工作区内容处理
- 已执行静态检查：仓库结构、Gradle 模块与目标、Source Set 文件统计、Smali 文件统计、设置存储契约与调用路径
- 已执行构建：共享 JVM/JS/WasmJS 编译任务成功；Desktop 构建成功；本轮 Android Debug 组装被本机 Windows 版 SDK build-tools 阻塞
- 已执行测试：无
- 真机验证：无；iOS 目标在 Windows 主机上不可编译/运行

## 当前问题

| ID | 优先级 | 安全 | 状态 | 摘要 |
| --- | --- | --- | --- | --- |
| IOS-PREF-001 | P3 | L2 | COMPLETED | iOS 缺失标量设置键未返回声明默认值；已修复，iOS 编译待 macOS 验证 |
| PREF-OBSERVE-002 | P4 | L2 | BLOCKED | 调用链未发现用户可见陈旧状态，缺少扩大平台观察实现的证据 |
| MIRROR-SOURCE-GATE-003 | P3 | L1 | COMPLETED | 无可用帧源平台仍允许启动投屏；已禁用入口并显示原因 |

## 验证声明

编译通过只证明对应目标能够编译，不证明车辆、云端或真机行为正确。所有未运行的项目必须继续标记为“尚未验证”。

### 本轮执行记录

- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs`：`BUILD SUCCESSFUL`；三个目标均执行了真实编译任务。
- `:desktopApp:build`：`BUILD SUCCESSFUL`；18 个 actionable task，10 executed、1 from cache、7 up-to-date。
- `:androidApp:assembleDebug`：未通过。首次因 `local.properties` 指向的 SDK 路径不可解析失败；临时指向 `F:\sdk` 后，因 build-tools 只有 Windows `.exe`、Linux AGP 找不到 `aapt` 失败。未修改 SDK 或 `local.properties`。
- 未运行 `jvmTest`、真实登录、云端请求、车辆设置写入、OTA、音效上传或设备操作。
- 当前修改目标是 `commonMain` 投屏页；JVM/JS/WasmJS 与 Desktop 已验证编译，Android 与 iOS 真机行为尚未验证。
