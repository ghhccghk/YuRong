# 项目审计基线

- 审计日期：2026-11-07
- 仓库：`F:\android app code\toupin`
- 原始 Smali：`F:\android app code\YadeaHook\temp_smali`
- Git 分支：`master`
- HEAD：`a23b3d4 fix(mirror): keep camera source resolution unlocked`
- 上游：`origin/master`，当前分支领先 7 个提交
- 基线工作区：无已暂存修改，无未暂存跟踪文件修改

## 审计范围

本目录持续记录 KMP 实现与原始 Smali 的对照结果、可复现缺陷、修复方案、验证结果和剩余风险。审计首先覆盖已经移植且在共享代码或平台适配层有明确契约的功能，不把类名、字段名或经验推断当作行为证据。

问题优先级：

- P0：启动或基本运行受阻。
- P1：核心业务错误。
- P2：与原 App 有明确行为差异。
- P3：平台兼容或功能缺失。
- P4：异常处理、性能或代码质量。

安全等级：

- L1：普通 UI 与展示逻辑。
- L2：业务逻辑、API、数据解析、持久化。
- L3：车辆控制、BLE、OTA、投屏协议、帧发送；默认只读审计。

## 工程结构

- Gradle 模块：`androidApp`、`desktopApp`、`shared`、`webApp`
- Shared 目标：Android、JVM、JS Browser、WasmJS Browser、iOS Arm64、iOS Simulator Arm64
- Kotlin 文件数量（本轮只读统计）：`androidApp` 12、`desktopApp` 2、`webApp` 2、`shared` 298
- Shared Source Set 统计：`commonMain` 148、`androidMain` 24、`jvmMain` 22、`iosMain` 18、`jsMain` 17、`wasmJsMain` 17、`jvmTest` 52
- 原始 Smali 文件数：70,877

## 架构边界

- 业务状态与共享 UI 位于 `shared/src/commonMain`。
- 平台能力分别位于 Android、JVM、iOS、JS、WasmJS Source Set。
- 双主题由 `UiThemeStyle.Miuix` 与 `UiThemeStyle.Material3` 维持；业务页面通过 `App*` 语义 API 访问控件。
- 设置持久化遵循“配置模型、存储抽象、平台后端、Compose 绑定”分层。
- BLE、Wi-Fi、OTA、投屏协议和帧发送在未取得新增证据和明确授权前仅做只读审计。
- 不根据字段名猜测车辆命令；写入映射必须有 Smali 或抓包依据。

## 受保护工作区内容

基线存在大量未跟踪文件，包括 `docs/flutter/`、审计文档、JADX 输出、APK、崩溃日志、JVM/镜像日志等。它们不属于本轮修复，不得删除、移动或混入提交。

后续提交只使用明确 pathspec，并在提交前执行：

- `git diff --check`
- 检查 `git diff --cached --name-status`
- 检查 `git status --short`
- 确认没有日志、APK、构建产物或用户文档进入提交

## 当前证据边界

- 已读项目根目录 `AGENTS.md`。
- 已完成 Git 状态、模块、Source Set、Gradle 配置和 Smali 目录的只读检查。
- 尚未运行任何单元测试、真实登录、云端请求、车辆写入或真机验证。
- 编译结果只在实际执行后登记，不能由静态检查代替。
- 本任务不以“代码已存在”等同于“行为已恢复”；每项功能仍需有对应 Smali 调用链或明确跨平台契约证据。

## 跨平台契约要点

`PreferenceStore.read()` 的调用方依赖 `PreferenceKey.defaultValue` 处理键缺失。Android、JVM、JS 和 WasmJS 后端都显式返回默认值；iOS 后端也必须保持相同语义。平台 API 对缺失标量键的零值返回行为不能被直接暴露为业务默认值。

## 持续审计方法

1. 先确认原始行为或跨平台契约。
2. 定位 KMP 对应实现及全部调用链。
3. 判断问题优先级、安全等级和平台影响。
4. 采用最小修改，每次只处理一个独立问题。
5. 执行允许的编译/构建验证。
6. 更新功能矩阵、任务队列、修复历史和报告。
7. 使用明确 pathspec 独立提交。
