package com.plankton.one102.data.interop

import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.newId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

const val APILOT_PACKAGE = "com.example.api_manager"
const val APILOT_GITHUB_URL = "https://github.com/yuelangmanle/Apilot"
const val APILOT_IMPORT_ACTION = "com.apilot.intent.action.IMPORT_API_CONFIGS"
const val APILOT_PICK_ACTION = "com.apilot.intent.action.PICK_API_CONFIG"
const val APILOT_IMPORT_MIME = "application/vnd.apilot.api-configs+json"
const val APILOT_RESULT_MIME = "application/vnd.apilot.api-profile+json"
const val APILOT_EXTRA_CONFIGS_JSON = "com.apilot.extra.API_CONFIGS_JSON"
const val APILOT_EXTRA_SOURCE_NAME = "com.apilot.extra.SOURCE_NAME"
const val APILOT_EXTRA_REQUEST_ID = "com.apilot.extra.REQUEST_ID"
const val APILOT_EXTRA_SCHEMA_VERSION = "com.apilot.extra.SCHEMA_VERSION"
const val APILOT_EXTRA_REQUESTED_SCOPES = "com.apilot.extra.REQUESTED_SCOPES"
const val APILOT_EXTRA_RETURN_TRANSPORT = "com.apilot.extra.RETURN_TRANSPORT"

data class ApilotPickedProfile(
    val connection: ApiConnection,
    val protocolId: String,
    val grantedScopes: Set<String>,
)

/**
 * Apilot V2 import payload. Secrets are opt-in because an Intent target can receive the shared
 * URI before its own confirmation UI is displayed.
 */
fun buildApilotImportPayload(
    connections: List<ApiConnection>,
    includeApiKeys: Boolean,
    sourceSignatureSha256: String? = null,
): String {
    val payload = buildJsonObject {
        put("schemaVersion", JsonPrimitive(2))
        put(
            "source",
            buildJsonObject {
                put("appName", JsonPrimitive("浮游动物一体化"))
                put("packageName", JsonPrimitive("com.plankton.one102"))
                sourceSignatureSha256?.trim()?.takeIf { it.isNotBlank() }?.let { put("signatureSha256", JsonPrimitive(it)) }
            },
        )
        put(
            "apiProfiles",
            buildJsonArray {
                connections.forEach { connection ->
                    add(
                        buildJsonObject {
                            put(
                                "connection",
                                buildJsonObject {
                                    put("name", JsonPrimitive(connection.name.trim().ifBlank { "未命名服务" }))
                                    put("baseUrl", JsonPrimitive(connection.baseUrl.trim()))
                                    put("environment", JsonPrimitive("production"))
                                    put("tags", buildJsonArray { add(JsonPrimitive(connection.providerId.trim().ifBlank { "custom" })) })
                                },
                            )
                            put(
                                "provider",
                                buildJsonObject { put("id", JsonPrimitive(connection.providerId.trim().ifBlank { "custom" })) },
                            )
                            put("protocol", buildJsonObject { put("id", JsonPrimitive("openai_compatible")) })
                            put(
                                "models",
                                buildJsonObject {
                                    connection.selectedModel.trim().takeIf { it.isNotBlank() }?.let { put("selectedModel", JsonPrimitive(it)) }
                                    if (connection.modelIds.isNotEmpty()) {
                                        put("availableModels", buildJsonArray { connection.modelIds.forEach { add(JsonPrimitive(it)) } })
                                        put("catalogMode", JsonPrimitive("saved"))
                                    } else {
                                        put("catalogMode", JsonPrimitive("none"))
                                    }
                                    put("source", JsonPrimitive("third_party"))
                                },
                            )
                            if (includeApiKeys && connection.apiKey.isNotBlank()) {
                                put("secrets", buildJsonObject { put("apiKey", JsonPrimitive(connection.apiKey)) })
                            }
                            put("origin", buildJsonObject { put("appName", JsonPrimitive("浮游动物一体化")) })
                        },
                    )
                }
            },
        )
    }
    return AppJson.encodeToString(JsonObject.serializer(), payload)
}

/** Parses only the fields that the main app can safely use after Apilot has shown its approval UI. */
fun parseApilotPickedProfile(raw: String): ApilotPickedProfile? {
    val root = runCatching { AppJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
    val schema = (root["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
    if (schema != 2) return null

    val scopes = (root["grantedScopes"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotBlank) }
        ?.toSet()
        ?: emptySet()
    val profile = root["apiProfile"] as? JsonObject ?: return null
    val connection = profile["connection"] as? JsonObject ?: return null
    val name = connection.string("name") ?: "Apilot 导入服务"
    val baseUrl = connection.string("baseUrl") ?: return null
    if (baseUrl.isBlank()) return null
    val providerId = (profile["provider"] as? JsonObject)?.string("id") ?: "custom"
    val protocolId = (profile["protocol"] as? JsonObject)?.string("id") ?: "openai_compatible"
    val models = profile["models"] as? JsonObject
    val selectedModel = models?.string("selectedModel").orEmpty()
    val modelIds = (models?.get("availableModels") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotBlank) }
        ?.distinct()
        ?: emptyList()
    val apiKey = if ("secret.api_key" in scopes) {
        (profile["secrets"] as? JsonObject)?.string("apiKey").orEmpty()
    } else {
        ""
    }

    return ApilotPickedProfile(
        connection = ApiConnection(
            id = newId(),
            name = name,
            providerId = providerId,
            baseUrl = baseUrl,
            protocol = protocolId,
            selectedModel = selectedModel,
            modelIds = modelIds,
            apiKey = apiKey,
        ),
        protocolId = protocolId,
        grantedScopes = scopes,
    )
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
