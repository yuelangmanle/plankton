# 语音助手合作方接口 v2

版本 `2` 是语音助手唯一公开的后台接入接口。它用 Binder 会话、调用 UID 与签名摘要识别调用方；合作方不得依赖旧版广播回调写入数据。

## 能力发现与会话

1. 绑定 action `com.voiceassistant.action.BIND_PARTNER_BROKER`。
2. 调用 `getCapabilities()`，读取协议版本、支持格式、最大音频字节数、Profile 与范围。
3. 用 `beginSession(hello)` 发送 `protocol_version`、16 至 128 位 `client_nonce`、`profile_id` 与 `requested_scopes`。
4. 只有用户授权的 `packageName + certificateSha256 + scopes + expiresAtMs` 才能获得会话。返回 `AUTHORIZATION_REQUIRED` 时，展示返回的系统授权 Intent，授权后重新建会话。

服务端每次 Binder 调用都按 `Binder.getCallingUid()` 校验包名和证书，忽略调用方自报身份。会话默认十分钟有效；过期、换签名、跨包复用和越权取消均失败。

## 音频与回调

`submitAudio(sessionId, requestId, ParcelFileDescriptor, options, callback)` 只接受 WAV，单文件上限以 `getCapabilities().max_audio_bytes` 为准，目前为 50 MiB。`requestId` 在同一会话内幂等：正在执行的重复提交会被拒绝，`cancel(sessionId, requestId)` 可重复调用且只取消调用方自己的任务。

进度通过 `IPartnerCallback.onProgress` 返回；终态通过 `onCompleted` 返回。错误码固定为：`AUTHORIZATION_REQUIRED`、`TOKEN_EXPIRED`、`AUDIO_UNREADABLE`、`MODEL_UNAVAILABLE`、`RATE_LIMITED`、`CANCELLED`、`INTERNAL_ERROR`。调用方应展示 `error_message`，只对标记为可重试的本地条件重新发起任务。

结果的最低字段为 `status`、`request_id`、`raw_text`。还可能包含：

- `partner_normalized_text`：本地数字、标点及别名规范化后的文本。
- `partner_uncertain_spans`：低置信或词表未命中的片段。
- `partner_proposed_actions`：仅为建议的 `type|point|species|value` 列表，不能直接写库。

回调、诊断和持久任务历史不保存原始音频、全文转写、调用方签名或 API Key。完成或失败后音频引用最多保留 24 小时，文本历史由用户手动清除。

## Profile 与范围

`generic` 仅接收通用转写和质量信息。`plankton-v1` 只有同时获得 `domain_profile` 范围时，才可在 `options` 传入受限的 `partner_point_id` 与最多 1000 个 `partner_species` 词项，用于生成动作建议。任何 Profile 都不能绕过接收 App 的动作预览和确认。

未来第三方 Profile 只能定义显示名、字段映射和受限词表；不得携带脚本、动态代码或远程下载地址。

## Kotlin 示例

```kotlin
val capabilities = broker.getCapabilities()
require(capabilities.getInt("protocol_version") == 2)
val session = broker.beginSession(hello)
broker.submitAudio(sessionId, requestId, wavDescriptor, Bundle(), callback)
// onCompleted: show transcript/review; let the receiving App confirm changes.
```

## v1 迁移

旧 `ACTION_TRANSCRIBE_AUDIO` 仅供已签名的一体化 App 过渡使用，受 signature permission 保护。新接入必须使用 v2；v1 不提供领域 Profile、可验证会话或防伪造回调，计划在 v4.0 移除。
