# 参与贡献

感谢你改进浮游动物一体化。这个仓库同时包含 Web 原型、原生 Android 主 App 和可选的语音助手，因此每个改动都需要说明影响范围。

## 开始之前

1. 阅读 [首次初始化资源](docs/首次初始化资源.md)、[项目书](docs/项目书.md) 和 [当前程序情况](docs/当前程序情况.md)。
2. 在 Issue 中确认较大的产品或架构变更，再开始实现。
3. 不要提交 `.secrets/`、API Key、正式签名材料、真实数据、模型权重、APK 或 `api_backups/`。

## 开发与验证

主 App：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Web：

```powershell
cd app
npm ci
npm run lint
npm run build
```

语音助手只有在行为发生变化时才构建和发布。不要使用 ADB 模拟点击代替真实设备验收；需要设备验证的 Pull Request 请列出人工验收步骤。

## 提交规范

- 一个提交只解决一个清晰问题，提交信息使用 `feat:`、`fix:`、`refactor:`、`docs:`、`test:`、`chore:` 等前缀。
- UI、数据格式、导出、API、备份或权限变化必须补充测试和文档。
- 主 App 版本变化时，按 `docs/release-checklist.md` 同步项目书、更新日志和加密离线文档。
- 新增依赖必须说明用途、许可证和升级风险。

## Pull Request

PR 应填写影响范围、验证命令、数据迁移/兼容性风险和人工验收步骤。合并前必须通过 CI；主分支只接受 Squash merge。
