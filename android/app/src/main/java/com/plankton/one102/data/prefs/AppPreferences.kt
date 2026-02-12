package com.plankton.one102.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.DEFAULT_SETTINGS
import com.plankton.one102.domain.Settings
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
    val currentDatasetId: Flow<String?> = context.dataStore.data.map { it[Keys.currentDatasetId] }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.settingsJson]
        if (raw.isNullOrBlank()) return@map DEFAULT_SETTINGS
        runCatching { AppJson.decodeFromString(Settings.serializer(), raw) }.getOrElse { DEFAULT_SETTINGS }
    }

    val lastExportUri: Flow<String?> = context.dataStore.data.map { it[Keys.lastExportUri] }
    val lastExportAt: Flow<String?> = context.dataStore.data.map { it[Keys.lastExportAt] }

    suspend fun setCurrentDatasetId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.currentDatasetId) else prefs[Keys.currentDatasetId] = id
        }
    }

    suspend fun saveSettings(next: Settings) {
        val raw = AppJson.encodeToString(Settings.serializer(), next)
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
}
