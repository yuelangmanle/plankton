package com.plankton.one102.data.api

import com.plankton.one102.domain.ApiCapability
import com.plankton.one102.domain.ApiConnection
import com.plankton.one102.domain.ApiRoute
import com.plankton.one102.domain.ApiRouteMode
import com.plankton.one102.domain.ApiTaskType
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.toConfig
import com.plankton.one102.domain.migratedApiCenter
import com.plankton.one102.domain.syncedLegacyApiSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiRoutingTest {
    private val primary = ApiConnection(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        selectedModel = "deepseek-chat",
    )
    private val fallback = ApiConnection(
        id = "mimo",
        name = "MiMo",
        baseUrl = "https://api.xiaomimimo.com/v1",
        selectedModel = "mimo-v2",
        capabilities = setOf(ApiCapability.Text, ApiCapability.Vision),
    )

    @Test
    fun automaticRouteProvidesPrimaryAndFallback() {
        val settings = Settings(
            apiConnections = listOf(primary, fallback),
            apiRoutes = listOf(ApiRoute(ApiTaskType.Chat, ApiRouteMode.Automatic, "deepseek", "mimo")),
        )

        val plan = ApiRouting.resolve(settings, ApiTaskType.Chat)

        assertEquals("deepseek", plan.primary?.id)
        assertEquals("mimo", plan.fallback?.id)
        assertEquals(ApiRouteMode.Automatic, plan.mode)
    }

    @Test
    fun specificRouteNeverAddsImplicitFallback() {
        val settings = Settings(
            apiConnections = listOf(primary, fallback),
            apiRoutes = listOf(ApiRoute(ApiTaskType.ImageRecognition, ApiRouteMode.Specific, "mimo")),
        )

        val plan = ApiRouting.resolve(settings, ApiTaskType.ImageRecognition)

        assertEquals("mimo", plan.primary?.id)
        assertNull(plan.fallback)
    }

    @Test
    fun dualRouteWithOnlyOneConfiguredServiceDegradesToAutomatic() {
        val settings = Settings(
            apiConnections = listOf(primary),
            apiRoutes = listOf(ApiRoute(ApiTaskType.Enrichment, ApiRouteMode.Automatic, "deepseek")),
        )

        val plan = ApiRouting.resolve(settings, ApiTaskType.Enrichment, modeOverride = ApiRouteMode.Dual)

        assertEquals(ApiRouteMode.Automatic, plan.mode)
        assertEquals("deepseek", plan.primary?.id)
        assertNull(plan.secondary)
    }

    @Test
    fun legacySettingsRemainRoutable() {
        val settings = Settings(
            api1 = primary.toConfig(),
            api2 = fallback.toConfig(),
        )

        val plan = ApiRouting.resolve(settings, ApiTaskType.Report)

        assertEquals("legacy-api1", plan.primary?.id)
        assertEquals("legacy-api2", plan.fallback?.id)
    }

    @Test
    fun migrationDisablesLegacyDefaultDualCallsAndPreservesConfiguredServices() {
        val migrated = Settings(
            api1 = primary.toConfig(),
            api2 = fallback.toConfig(),
            aiUseDualApi = true,
        ).migratedApiCenter()

        assertEquals(false, migrated.aiUseDualApi)
        assertEquals(listOf("legacy-api1", "legacy-api2"), migrated.apiConnections.map { it.id })
        assertEquals("legacy-api1", migrated.apiRoutes.first { it.task == ApiTaskType.Chat }.primaryConnectionId)
    }

    @Test
    fun apiCenterRouteSynchronizesLegacySlotsForUnmigratedCallers() {
        val settings = Settings(
            apiConnections = listOf(primary, fallback),
            apiRoutes = listOf(
                ApiRoute(ApiTaskType.Chat, ApiRouteMode.Automatic, "mimo", "deepseek"),
                ApiRoute(ApiTaskType.ImageRecognition, ApiRouteMode.Specific, "mimo"),
            ),
        ).syncedLegacyApiSlots()

        assertEquals("MiMo", settings.api1.name)
        assertEquals("DeepSeek", settings.api2.name)
        assertEquals("MiMo", settings.imageApi.name)
    }
}
