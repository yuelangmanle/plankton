# 浮游动物一体化

面向浮游动物现场采样、物种计数、图片/AI 辅助核对、数据备份和 Excel 报告导出的多端工具集。仓库包含 Web 原型、原生 Android 主 App 和可选的独立语音助手；现场数据默认保存在设备本地，外部 API 只有在用户配置并主动调用时才会发送数据。

- 项目主页：<https://github.com/yuelangmanle/plankton>
- 当前主 App：[`v7.6 (760)`](https://github.com/yuelangmanle/plankton/releases/tag/v7.6)
- 许可证：Apache-2.0（上游依赖按其各自许可证执行，见 [第三方声明](THIRD_PARTY_NOTICES.md)）
- 最低 Android 版本：Android 14 / API 34

## 目录

| 目录 | 用途 |
| --- | --- |
| [`app/`](app/) | Vite + React + TypeScript Web 原型 |
| [`android/`](android/) | 原生 Android 主 App，负责现场录入、AI、导出和更新检查 |
| [`voice_assistant/`](voice_assistant/) | 可选的语音助手与跨 App 桥接 |
| [`docs/`](docs/) | 项目书、当前程序说明、初始化和发布清单 |
| [`tools/`](tools/) | 文档同步、资源生成和发布辅助脚本 |

## 快速开始

首次克隆后先阅读 [首次初始化资源](docs/首次初始化资源.md)。不要把真实表格、API Key、正式签名文件或本地模型上传到 GitHub。

### Web 原型

```powershell
cd app
npm ci
npm run dev
```

常用命令：`npm run lint`、`npm run build`、`npm run preview`。

### Android 主 App

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

正式签名材料只从本地 `.secrets/release-signing/` 读取，CI 不保存也不生成正式签名。完整发布步骤见 [发布检查清单](docs/release-checklist.md)。语音助手只有发生实际行为变化时才单独构建和发布。

## 使用与隐私

- 数据集、备份和 API 配置默认保存在本机；备份不包含 Keystore 中的密钥。
- 图片识别、问答和模型接口会把用户主动提交的内容发送到所选服务商，服务商的留存和计费政策由用户自行确认。
- GitHub 更新只信任公开 Release；安装覆盖升级前必须保持正式签名证书一致。
- 导出分享前请确认文件中没有不应外传的采样数据。

## Apilot API 互操作

主 App 支持与 [Apilot](https://github.com/yuelangmanle/Apilot) 的 V2 API Profile 接口互通：可从 Apilot 经授权选择一个服务，也可把本机已配置的服务发送给 Apilot。发送前可逐条多选、全选或全不选；实际传输只包含所选服务，API Key 默认不外发，双方仍会各自展示确认页。

若手机未安装 Apilot，主 App 会提示并可直接打开其 GitHub 项目下载。该集成遵循 Apilot 的公开 V2 apiProfiles 一次性 content URI 传输约定；Apilot 的安装、服务商条款和密钥安全由用户自行确认。

## 文档与协作

- [项目书](docs/项目书.md)：产品范围、数据规则和长期路线
- [当前程序情况](docs/当前程序情况.md)：当前入口、版本和代码索引
- [原生 Android 项目书](docs/原生安卓项目书.md)：Android 细则与兼容性要求
- [贡献指南](CONTRIBUTING.md)：本地验证、提交和 PR 约束
- [安全策略](SECURITY.md)：私密漏洞报告方式
- [更新记录](CHANGELOG.md)：仓库级变更入口，完整版本说明以应用日志和 Releases 为准

问题请先使用 [Discussions](https://github.com/yuelangmanle/plankton/discussions)；确认是可复现缺陷后再提交 Issue。提交前请查看 [行为准则](CODE_OF_CONDUCT.md)。

## 不纳入 Git 的内容

`.gitignore` 明确排除构建目录、APK 历史包、模型权重、表格、`api_backups/`、`.secrets/`、Keystore 和临时日志。仓库只保留无真实数据的导出模板：

- `android/app/src/main/assets/templates/table1.xlsx`
- `android/app/src/main/assets/templates/table2.xlsx`

## 作者

月亮满了 · QQ `3335196397`
