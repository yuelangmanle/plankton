# 主 App 发布检查清单

- [ ] `versionName/versionCode`、设置页更新日志、项目书、当前程序说明和新窗口提示词一致。
- [ ] 运行 `tools/verify_docs_sync.ps1`，再运行文档加密同步脚本。
- [ ] 运行完整主 App 单元测试与 Release 构建；人工真机验收由用户执行，不做 ADB 模拟点击。
- [ ] 使用 `.secrets/release-signing/` 中既有正式 JKS 签名；核对包名、版本、v3 签名和证书 SHA-256。
- [ ] 计算 APK SHA-256，写入 `docs/releases/v<version>.md`。
- [ ] Git 提交并推送主分支，创建同名 GitHub Release，上传唯一当前 APK；旧包移入归档或保留在历史 Release。
- [ ] 通过 Release 页面和应用内“检查 GitHub 更新”核对版本、说明与下载链接。
- [ ] 确认仓库中没有 API Key、真实数据、签名私钥、`api_backups/`、APK 或构建缓存；Release APK 仅作为 GitHub Release 附件上传。
