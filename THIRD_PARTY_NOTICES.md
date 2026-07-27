# Third-party notices

本仓库包含或引用了多个上游项目。它们不受根目录 Apache-2.0 许可自动覆盖，分发时必须同时遵守各自的许可证和 NOTICE 文件。

- `voice_assistant/third_party/whisper.cpp/`：保留上游 `LICENSE`、版权和贡献者声明。
- `voice_assistant/third_party/vulkan-hpp/`：保留上游许可证与版权声明。
- `tools/` 与 Gradle/Maven/npm 依赖：请以对应发行包中的许可证和锁定版本为准。

新增第三方依赖时，请在 Pull Request 中说明来源、版本、许可证和是否需要 NOTICE 更新。不要把本地模型、真实业务表格、API Key、签名私钥或构建产物提交到仓库。
