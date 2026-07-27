# 语音识别助手 v3.7 (37)

发布日期：2026-07-27

## 重点更新

- 后台转写使用可恢复的持久队列，支持精确取消、进程恢复和 24 小时音频引用清理。
- 新增 v2 Binder 合作方协议：按 UID、包名、证书摘要和授权范围校验；`generic` 与 `plankton-v1` Profile 隔离。
- 本地规范化数字和受限词表，结果只提供不确定片段与动作建议，主 App 始终先预览再写入。
- 主页面与接入页面共享串行转写调度；新增合成语料评分、脱敏诊断和 Android 16 目标适配。
- 设置页的 GitHub 更新检查只识别正式 `voice-v*` Release。

## 安装与验证

- 包名：`com.voiceassistant`
- 版本：`3.7 (37)`
- 最低系统：Android 14（API 34）；目标系统：Android 16（API 36）
- APK：`voice-assistant-v3.7(37).apk`
- 签名：与主 App 相同的正式证书，证书 SHA-256 `77DCE854A447A8A970723C33E65B569EEB1D47C29E99A592C59B5B21D73CE03B`
- APK SHA-256：`CE43EEEA57F1FBEDE1B16E873465417FF3FC4A5763582D244E78FD4FAC447947`

真机验收由用户执行：首次录音/通知/悬浮窗权限、锁屏与后台转写、取消、主 App 跨 App 回传、正式包覆盖安装以及 GitHub 更新检查。
