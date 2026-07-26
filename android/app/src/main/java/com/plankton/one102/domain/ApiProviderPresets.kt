package com.plankton.one102.domain

/**
 * Connection presets intentionally contain no model identifiers. Providers retire and add
 * models independently, so the settings UI always obtains the model list from the endpoint.
 */
data class ApiProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String,
    val group: ApiProviderGroup,
    val caution: String? = null,
)

enum class ApiProviderGroup {
    Common,
    China,
    International,
    Custom,
}

fun ApiProviderGroup.label(): String = when (this) {
    ApiProviderGroup.Common -> "常用"
    ApiProviderGroup.China -> "国内服务"
    ApiProviderGroup.International -> "国际服务"
    ApiProviderGroup.Custom -> "其他 / 自定义"
}

object ApiProviderPresets {
    val entries: List<ApiProviderPreset> = listOf(
        ApiProviderPreset(
            id = "mimo-payg",
            name = "小米 MiMo（直售按量）",
            baseUrl = "https://api.xiaomimimo.com/v1",
            description = "小米 MiMo 开放平台的按量计费 OpenAI 兼容接口。填入自己的 Key 后获取可用模型。",
            group = ApiProviderGroup.Common,
        ),
        ApiProviderPreset(
            id = "mimo-token-plan-cn",
            name = "小米 MiMo（订阅 Token Plan，中国区）",
            baseUrl = "https://token-plan-cn.xiaomimimo.com/v1",
            description = "订阅套餐专属 OpenAI 兼容接口。新加坡区和欧洲区地址可在编辑页替换。",
            group = ApiProviderGroup.Common,
            caution = "MiMo 当前条款说明 Token Plan 主要面向编程工具，非编程自定义应用可能存在停服或封禁 Key 风险。继续使用即表示你已自行确认适用范围与风险。",
        ),
        ApiProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            description = "DeepSeek OpenAI 兼容接口。填入自己的 Key 后获取当前账号可用模型。",
            group = ApiProviderGroup.Common,
        ),
        ApiProviderPreset(
            id = "qwen",
            name = "阿里云百炼（通义千问）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            description = "阿里云百炼的 OpenAI 兼容模式。模型由当前百炼账号实时返回。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "zhipu",
            name = "智谱 AI（GLM）",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            description = "智谱大模型 OpenAI 兼容接口。支持的模型以当前账号返回为准。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "moonshot",
            name = "月之暗面（Kimi）",
            baseUrl = "https://api.moonshot.cn/v1",
            description = "Kimi OpenAI 兼容接口；请通过“获取并智能推荐”使用当前可用模型。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "minimax",
            name = "MiniMax",
            baseUrl = "https://api.minimax.chat/v1",
            description = "MiniMax OpenAI 兼容接口。模型名称不在应用内固化。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "baidu-qianfan",
            name = "百度智能云千帆",
            baseUrl = "https://qianfan.baidubce.com/v2",
            description = "千帆 OpenAI 兼容接口；请使用具有模型服务权限的 API Key。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "volcengine-ark",
            name = "火山方舟（豆包 / Ark）",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            description = "火山方舟 OpenAI 兼容接口。模型、端点和权限均以火山控制台为准。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "siliconflow",
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            description = "硅基流动 OpenAI 兼容接口，可从当前账号拉取多家开源模型。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "modelscope",
            name = "魔搭 ModelScope",
            baseUrl = "https://api-inference.modelscope.cn/v1",
            description = "魔搭推理服务的 OpenAI 兼容接口。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "iflytek-spark",
            name = "讯飞星火",
            baseUrl = "https://spark-api-open.xf-yun.com/v1",
            description = "讯飞星火 OpenAI 兼容接口；套餐和可用模型以控制台为准。",
            group = ApiProviderGroup.China,
        ),
        ApiProviderPreset(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            description = "OpenAI 官方接口。应用不预置具体模型，请从当前账号拉取。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "google-gemini",
            name = "Google Gemini（OpenAI 兼容）",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            description = "Gemini 的 OpenAI 兼容端点。模型和区域可用性以 Google 当前账号为准。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "groq",
            name = "GroqCloud",
            baseUrl = "https://api.groq.com/openai/v1",
            description = "Groq OpenAI 兼容接口，适合低延迟文本任务。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            description = "OpenRouter OpenAI 兼容聚合接口；模型列表按账号权限动态返回。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "mistral",
            name = "Mistral AI",
            baseUrl = "https://api.mistral.ai/v1",
            description = "Mistral 官方 OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "xai",
            name = "xAI（Grok）",
            baseUrl = "https://api.x.ai/v1",
            description = "xAI OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "together-ai",
            name = "Together AI",
            baseUrl = "https://api.together.xyz/v1",
            description = "Together AI OpenAI 兼容接口，模型按账户实时获取。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "fireworks-ai",
            name = "Fireworks AI",
            baseUrl = "https://api.fireworks.ai/inference/v1",
            description = "Fireworks AI OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "perplexity",
            name = "Perplexity",
            baseUrl = "https://api.perplexity.ai",
            description = "Perplexity OpenAI 兼容接口；联网能力及模型按其服务条款使用。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "nvidia-nim",
            name = "NVIDIA NIM",
            baseUrl = "https://integrate.api.nvidia.com/v1",
            description = "NVIDIA NIM OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "deepinfra",
            name = "DeepInfra",
            baseUrl = "https://api.deepinfra.com/v1/openai",
            description = "DeepInfra OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "cerebras",
            name = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            description = "Cerebras OpenAI 兼容接口。",
            group = ApiProviderGroup.International,
        ),
        ApiProviderPreset(
            id = "custom",
            name = "用户自定义（OpenAI 兼容）",
            baseUrl = "",
            description = "适用于任意 OpenAI 兼容服务：自行填写名称、Base URL、Key，并从接口获取模型；不支持列举时可手动填写模型。",
            group = ApiProviderGroup.Custom,
        ),
    )
}

fun isMimoTokenPlanUrl(baseUrl: String): Boolean {
    val host = baseUrl.trim().lowercase()
    return host.contains("token-plan-") && host.contains("xiaomimimo.com")
}
