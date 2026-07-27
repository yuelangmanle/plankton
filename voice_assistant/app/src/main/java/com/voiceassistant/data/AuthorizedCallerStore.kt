package com.voiceassistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voiceassistant.bridge.PartnerProtocol
import com.voiceassistant.bridge.PartnerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.partnerAuthorizationStore: DataStore<Preferences> by preferencesDataStore(name = "partner_authorizations")
private val AuthorizedCallerRecordsKey = stringSetPreferencesKey("authorized_callers_v2")

internal class AuthorizedCallerStore(private val context: Context) {
    val callersFlow: Flow<Set<AuthorizedCaller>> = context.partnerAuthorizationStore.data.map { preferences ->
        preferences[AuthorizedCallerRecordsKey].orEmpty().mapNotNull(AuthorizedCallerRecordCodec::decode).toSet()
    }

    suspend fun replace(callers: Set<AuthorizedCaller>) {
        context.partnerAuthorizationStore.edit { preferences ->
            preferences[AuthorizedCallerRecordsKey] = callers.mapTo(linkedSetOf(), AuthorizedCallerRecordCodec::encode)
        }
    }
}

internal object AuthorizedCallerRecordCodec {
    private const val FIELD_SEPARATOR = "|"
    private const val SCOPE_SEPARATOR = ","

    fun encode(caller: AuthorizedCaller): String {
        val scopeIds = caller.scopes.map(PartnerScope::wireValue).sorted().joinToString(SCOPE_SEPARATOR)
        return listOf(
            caller.packageName.trim(),
            caller.certificateSha256.trim(),
            caller.expiresAtMs.toString(),
            scopeIds,
        ).joinToString(FIELD_SEPARATOR)
    }

    fun decode(raw: String): AuthorizedCaller? {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 4) return null
        val packageName = parts[0].trim()
        val certificate = parts[1].trim()
        val expiresAtMs = parts[2].toLongOrNull()
        val scopes = PartnerProtocol.parseScopes(parts[3].split(SCOPE_SEPARATOR).filter { it.isNotBlank() })
        if (packageName.isBlank() || certificate.isBlank() || expiresAtMs == null || scopes == null) return null
        return AuthorizedCaller(packageName, certificate, scopes, expiresAtMs)
    }
}
