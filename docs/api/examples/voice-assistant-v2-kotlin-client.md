# v2 Kotlin 调用示例

请先阅读 [接口规范](../voice-assistant-v2.md)。以下示例只展示协议顺序，生产调用必须处理服务断开、授权 Intent、超时和用户取消。

```kotlin
val client = VoicePartnerBrokerClient(context)
when (val begun = client.begin("generic")) {
    is VoicePartnerBrokerClient.BeginResult.Ready ->
        client.submit(begun.broker, begun.sessionId, requestId, wavFile, ::showProgress) { result ->
            // 展示 raw_text / partner_normalized_text；禁止直接写业务数据库。
        }
    is VoicePartnerBrokerClient.BeginResult.AuthorizationRequired -> showAuthorization(begun.intent)
    is VoicePartnerBrokerClient.BeginResult.Failed -> showError(begun.message)
}
```
