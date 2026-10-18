# UI 目录结构

```text
ui/
├── components/   # 双风格语义控件、Shell、Dialog、Snackbar、菜单、模糊等
├── screens/      # 业务页面与页面入口（MainScreen / Connection / Vehicle / Settings）
├── theme/        # 主题枚举、AppDesign token、颜色方案、缩放与颜色过渡
├── state/        # AppViewModel 等页面状态
└── platform/     # 权限、返回手势、日志等平台边界
```

约定：

- 业务页面只使用 `components` 中的 `App*` 语义 API。
- `screens` 不直接实现 Material 3 / Miuix 两套视觉分支。
- `theme` 只放 token 与主题能力，不放业务页面。
- 新增组件优先进入 `components`，新增页面进入 `screens`。
