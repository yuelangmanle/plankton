package com.plankton.one102.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class ApiTaskType {
    Chat,
    BulkCommand,
    ImageRecognition,
    Enrichment,
    CalculationCheck,
    Report,
}

fun ApiTaskType.label(): String = when (this) {
    ApiTaskType.Chat -> "普通问答"
    ApiTaskType.BulkCommand -> "批量指令解析"
    ApiTaskType.ImageRecognition -> "图片识别"
    ApiTaskType.Enrichment -> "分类/湿重补齐"
    ApiTaskType.CalculationCheck -> "计算核对"
    ApiTaskType.Report -> "报告生成"
}

@Serializable
enum class ApiRouteMode {
    Specific,
    Automatic,
    Dual,
}

fun ApiRouteMode.label(): String = when (this) {
    ApiRouteMode.Specific -> "指定服务"
    ApiRouteMode.Automatic -> "自动切换"
    ApiRouteMode.Dual -> "双 API 核对"
}

@Serializable
enum class ApiCapability {
    Text,
    Vision,
    StructuredJson,
    LongContext,
}

@Serializable
data class ApiConnection(
    val id: String = newId(),
    val name: String = "",
    val providerId: String = "custom",
    val baseUrl: String = "",
    val protocol: String = "openai-compatible",
    val selectedModel: String = "",
    val modelIds: List<String> = emptyList(),
    val apiKeyRef: String = "",
    val lastModelsAt: String? = null,
    val capabilities: Set<ApiCapability> = emptySet(),
    val capabilityVerifiedAt: String? = null,
    val lastHealthOk: Boolean? = null,
    val lastHealthMessage: String? = null,
    val lastLatencyMs: Long? = null,
    val lastCheckedAt: String? = null,
    val riskAcknowledged: Boolean = false,
    @Transient val apiKey: String = "",
)

fun ApiConnection.toConfig(): ApiConfig = ApiConfig(
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = selectedModel,
    apiKeyRef = apiKeyRef,
)

@Serializable
data class ApiRoute(
    val task: ApiTaskType = ApiTaskType.Chat,
    val mode: ApiRouteMode = ApiRouteMode.Automatic,
    val primaryConnectionId: String? = null,
    val fallbackConnectionId: String? = null,
    val secondaryConnectionId: String? = null,
    val primaryModel: String? = null,
    val fallbackModel: String? = null,
    val secondaryModel: String? = null,
)

@Serializable
data class ApiInvocationRecord(
    val id: String = newId(),
    val task: ApiTaskType = ApiTaskType.Chat,
    val connectionName: String = "",
    val model: String = "",
    val ok: Boolean = false,
    val statusCode: Int? = null,
    val message: String = "",
    val latencyMs: Long? = null,
    val fallbackUsed: Boolean = false,
    val createdAt: String = nowIso(),
)

fun Settings.apiConnectionsCompat(): List<ApiConnection> {
    if (apiConnections.isNotEmpty()) return apiConnections
    return listOfNotNull(
        api1.takeIf { it.baseUrl.isNotBlank() || it.apiKey.isNotBlank() || it.model.isNotBlank() }?.let {
            ApiConnection(id = "legacy-api1", name = it.name.ifBlank { "主服务" }, providerId = "legacy", baseUrl = it.baseUrl, selectedModel = it.model, apiKey = it.apiKey, apiKeyRef = it.apiKeyRef)
        },
        api2.takeIf { it.baseUrl.isNotBlank() || it.apiKey.isNotBlank() || it.model.isNotBlank() }?.let {
            ApiConnection(id = "legacy-api2", name = it.name.ifBlank { "备用服务" }, providerId = "legacy", baseUrl = it.baseUrl, selectedModel = it.model, apiKey = it.apiKey, apiKeyRef = it.apiKeyRef)
        },
        imageApi.takeIf { it.baseUrl.isNotBlank() || it.apiKey.isNotBlank() || it.model.isNotBlank() }?.let {
            ApiConnection(id = "legacy-image", name = it.name.ifBlank { "图片识别服务" }, providerId = "legacy", baseUrl = it.baseUrl, selectedModel = it.model, apiKey = it.apiKey, apiKeyRef = it.apiKeyRef)
        },
    )
}

fun Settings.apiRoutesCompat(): List<ApiRoute> {
    if (apiRoutes.isNotEmpty()) return apiRoutes
    val connections = apiConnectionsCompat()
    val primary = connections.firstOrNull { it.id == "legacy-api1" }?.id ?: connections.firstOrNull()?.id
    val fallback = connections.firstOrNull { it.id == "legacy-api2" }?.id
    val image = connections.firstOrNull { it.id == "legacy-image" }?.id ?: primary
    return listOf(
        ApiRoute(ApiTaskType.Chat, ApiRouteMode.Automatic, primary, fallback),
        ApiRoute(ApiTaskType.BulkCommand, ApiRouteMode.Automatic, primary, fallback),
        ApiRoute(ApiTaskType.ImageRecognition, ApiRouteMode.Specific, image),
        ApiRoute(ApiTaskType.Enrichment, ApiRouteMode.Automatic, primary, fallback),
        ApiRoute(ApiTaskType.CalculationCheck, if (aiUseDualApi) ApiRouteMode.Dual else ApiRouteMode.Automatic, primary, fallback, fallback),
        ApiRoute(ApiTaskType.Report, ApiRouteMode.Automatic, primary, fallback),
    )
}

fun Settings.migratedApiCenter(): Settings {
    if (apiConnections.isNotEmpty() && apiRoutes.isNotEmpty()) return this
    return copy(
        apiConnections = apiConnectionsCompat(),
        apiRoutes = apiRoutesCompat(),
        aiUseDualApi = false,
    )
}

/** Keeps legacy call sites functional while the UI and routing layer use API center records. */
fun Settings.syncedLegacyApiSlots(): Settings {
    val canonical = migratedApiCenter()
    val connections = canonical.apiConnectionsCompat()
    fun config(id: String?): ApiConfig? = connections.firstOrNull { it.id == id }?.toConfig()
    val chat = canonical.apiRoutesCompat().firstOrNull { it.task == ApiTaskType.Chat }
    val image = canonical.apiRoutesCompat().firstOrNull { it.task == ApiTaskType.ImageRecognition }
    return canonical.copy(
        api1 = config(chat?.primaryConnectionId) ?: canonical.api1,
        api2 = config(chat?.secondaryConnectionId ?: chat?.fallbackConnectionId) ?: canonical.api2,
        imageApi = config(image?.primaryConnectionId) ?: canonical.imageApi,
    )
}
