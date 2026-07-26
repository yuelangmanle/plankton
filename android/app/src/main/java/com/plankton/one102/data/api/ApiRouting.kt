package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.ApiCapability
import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.ApiRouteMode
import com.plankton.one102.domain.ApiRoute
import com.plankton.one102.domain.ApiTaskType
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.apiConnectionsCompat
import com.plankton.one102.domain.apiRoutesCompat
import com.plankton.one102.domain.toConfig

data class ApiRoutePlan(
    val task: ApiTaskType,
    val mode: ApiRouteMode,
    val primary: ApiConnection?,
    val fallback: ApiConnection?,
    val secondary: ApiConnection?,
) {
    val hasPrimary: Boolean get() = primary?.baseUrl.orEmpty().isNotBlank() && primary?.selectedModel.orEmpty().isNotBlank()
}

object ApiRouting {
    fun resolve(
        settings: Settings,
        task: ApiTaskType,
        modeOverride: ApiRouteMode? = null,
        primaryOverride: String? = null,
    ): ApiRoutePlan {
        val connections = settings.apiConnectionsCompat()
        val route = settings.apiRoutesCompat().firstOrNull { it.task == task } ?: ApiRoute(task)
        val mode = modeOverride ?: route.mode
        fun connection(id: String?): ApiConnection? = id?.let { value -> connections.firstOrNull { it.id == value } }

        val primary = connection(primaryOverride ?: route.primaryConnectionId) ?: defaultPrimary(connections, task)
        val fallback = if (mode == ApiRouteMode.Automatic || mode == ApiRouteMode.Dual) {
            connection(route.fallbackConnectionId) ?: defaultFallback(connections, primary)
        } else {
            null
        }
        val secondary = if (mode == ApiRouteMode.Dual) connection(route.secondaryConnectionId) ?: fallback else null
        return ApiRoutePlan(task = task, mode = mode, primary = primary, fallback = fallback, secondary = secondary)
    }

    fun config(connection: ApiConnection?, modelOverride: String? = null): ApiConfig? {
        if (connection == null) return null
        return connection.toConfig().let { config ->
            if (modelOverride.isNullOrBlank()) config else config.copy(model = modelOverride)
        }
    }

    private fun defaultPrimary(connections: List<ApiConnection>, task: ApiTaskType): ApiConnection? {
        if (task == ApiTaskType.ImageRecognition) {
            return connections.firstOrNull { it.id == "legacy-image" }
                ?: connections.firstOrNull { ApiCapability.Vision in it.capabilities }
                ?: connections.firstOrNull()
        }
        return connections.firstOrNull { it.id == "legacy-api1" } ?: connections.firstOrNull()
    }

    private fun defaultFallback(connections: List<ApiConnection>, primary: ApiConnection?): ApiConnection? {
        return connections.firstOrNull { it.id == "legacy-api2" && it.id != primary?.id }
            ?: connections.firstOrNull { it.id != primary?.id }
    }
}
