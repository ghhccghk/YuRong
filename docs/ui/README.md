# UI 重设计分析

本目录记录对 MeowUI 的源码研究、当前项目的真实 UI 审查，以及下一批 UI 改造规范。分析阶段只修改文档和审查截图，不修改业务代码。

- [MeowUI 设计语言分析](./meowui-design-analysis.md)
- [当前项目 UI 问题清单](./current-ui-audit.md)
- [双 UI 重设计规范](./redesign-spec.md)
- [页面级改造方案](./page-redesign-plan.md)
- [执行复盘](./execution-retrospective.md)
- [主题、取色与设置持久化源码分析](./theme-persistence-analysis.md)
- [MeowUI Docs 功能查漏补缺审计](./meowui-docs-gap-audit.md)

## 证据状态

MeowUI 研究基于源码快照 `60bb989`（仓库 `lingqiqi5211/MeowUI`）。当前项目 UI 已在 Android 真机上启动并截图，设备随后断开；截图保存在 [audit-screenshots](./audit-screenshots)。

已观察页面：

- Miuix：连接、投屏、设置。
- Material 3：设置；切换主题后观察到页面跳回“连接”，该状态问题记录在问题清单中。

未完成的验证：

- 未在同一设备上完成可控的浅色/深色双主题矩阵。
- 未完成 iOS、Desktop、Web 的截图级视觉验证。
- 未进行改造后的页面验证；当前阶段不修改业务代码。
