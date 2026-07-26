package com.plankton.one102.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.DEFAULT_SETTINGS
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.ApiProfile
import com.plankton.one102.domain.migratedApiCenter
import com.plankton.one102.domain.syncedLegacyApiSlots
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "plankton"

private val Context.dataStore by preferencesDataStore(name = STORE_NAME)

private object Keys {
    val currentDatasetId = stringPreferencesKey("currentDatasetId")
    val settingsJson = stringPreferencesKey("settingsJson")
    val lastExportUri = stringPreferencesKey("lastExportUri")
    val lastExportAt = stringPreferencesKey("lastExportAt")
}

class AppPreferences(private val context: Context) {
    private val secretStore = ApiSecretStore(context)
    val currentDatasetId: Flow<String?> = context.dataStore.data.map { it[Keys.currentDatasetId] }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.settingsJson]
        if (raw.isNullOrBlank()) return@map DEFAULT_SETTINGS
        runCatching {
            val decoded = AppJson.decodeFromString(Settings.serializer(), raw)
            val migrated = if (raw.contains("\"apiConnections\"") || raw.contains("\"apiRoutes\"")) decoded else decoded.copy(aiUseDualApi = false)
            hydrateSecrets(migrated)
        }.getOrElse { DEFAULT_SETTINGS }
    }

    val lastExportUri: Flow<String?> = context.dataStore.data.map { it[Keys.lastExportUri] }
    val lastExportAt: Flow<String?> = context.dataStore.data.map { it[Keys.lastExportAt] }

    suspend fun setCurrentDatasetId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.currentDatasetId) else prefs[Keys.currentDatasetId] = id
        }
    }

    suspend fun saveSettings(next: Settings) {
        val stored = storeSecrets(next.migratedApiCenter().syncedLegacyApiSlots())
        val raw = AppJson.encodeToString(Settings.serializer(), stored)
        context.dataStore.edit { prefs ->
            prefs[Keys.settingsJson] = raw
        }
    }

    suspend fun setLastExport(uri: String?, exportedAt: String = nowIso()) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(Keys.lastExportUri)
                prefs.remove(Keys.lastExportAt)
            } else {
                prefs[Keys.lastExportUri] = uri
                prefs[Keys.lastExportAt] = exportedAt
            }
        }
    }

    private fun hydrateSecrets(settings: Settings): Settings {
        fun hydrate(config: ApiConfig): ApiConfig = if (config.apiKeyRef.isBlank()) config else config.copy(apiKey = secretStore.get(config.apiKeyRef))
        fun hydrate(profile: ApiProfile): ApiProfile = if (profile.apiKeyRef.isBlank()) profile else profile.copy(apiKey = secretStore.get(profile.apiKeyRef))
        return settings.copy(
            api1 = hydrate(settings.api1),
            api2 = hydrate(settings.api2),
            imageApi = hydrate(settings.imageApi),
            apiProfiles = settings.apiProfiles.map(::hydrate),
            apiConnections = settings.apiConnections.map { connection ->
                if (connection.apiKeyRef.isBlank()) connection else connection.copy(apiKey = secretStore.get(connection.apiKeyRef))
            },
        )
    }

    private fun storeSecrets(settings: Settings): Settings {
        fun store(config: ApiConfig): ApiConfig {
            if (config.apiKey.isBlank()) return config.copy(apiKey = "")
            val ref = config.apiKeyRef.ifBlank { secretStore.newRef() }
            secretStore.put(ref, config.apiKey)
            return config.copy(apiKey = "", apiKeyRef = ref)
        }
        fun store(profile: ApiProfile): ApiProfile {
            if (profile.apiKey.isBlank()) return profile.copy(apiKey = "")
            val ref = profile.apiKeyRef.ifBlank { secretStore.newRef() }
            secretStore.put(ref, profile.apiKey)
            return profile.copy(apiKey = "", apiKeyRef = ref)
        }
        return settings.copy(
            api1 = store(settings.api1),
            api2 = store(settings.api2),
            imageApi = store(settings.imageApi),
            apiProfiles = settings.apiProfiles.map(::store),
            apiConnections = settings.apiConnections.map { connection ->
                if (connection.apiKey.isBlank()) connection else {
                    val ref = connection.apiKeyRef.ifBlank { secretStore.newRef() }
                    secretStore.put(ref, connection.apiKey)
                    connection.copy(apiKey = "", apiKeyRef = ref)
                }
            },
        )
    }
}
