# 语音助手发布检查清单

- [ ] `versionName/versionCode`、应用内日志、项目书、公开接口文档和 Release 说明一致。
- [ ] 单元测试、debug 编译和 lint 已执行；真机验收由用户执行，不用 ADB 模拟点击代替。
- [ ] 正式构建仅从 `.secrets/release-signing/` 读取既有 JKS；缺失材料时构建必须失败，不能回退 debug 签名。
- [ ] 使用 `apksigner verify --verbose --print-certs` 核对 v2/v3 和证书 SHA-256，记录 APK SHA-256。
- [ ] 只上传 `voice-v<version>` Release 的语音助手 APK；主 App APK 不随语音改动重新打包。
- [ ] 旧 APK 只留在本地归档或历史 Release，仓库工作目录和 Git 不提交 APK、模型、缓存、真实音频、`api_backups/` 或 `.secrets/`。
- [ ] 检查应用内 GitHub 更新：最新时有明确提示，发现新 `voice-v*` Release 时能显示说明并交给系统下载器。
