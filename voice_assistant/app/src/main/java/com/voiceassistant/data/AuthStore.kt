package com.voiceassistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

private val AllowedAppsKey = stringSetPreferencesKey("allowed_apps")
private val SelectedModelKey = stringPreferencesKey("selected_model")
private val EngineKey = stringPreferencesKey("engine")
private val ProcessingModeKey = stringPreferencesKey("processing_mode")
private val UseGpuKey = booleanPreferencesKey("use_gpu")
private val DecodeModeKey = stringPreferencesKey("decode_mode")
private val AutoStrategyKey = booleanPreferencesKey("auto_strategy")
private val MultiThreadKey = booleanPreferencesKey("multi_thread")
private val ThreadCountKey = intPreferencesKey("thread_count")
private val RecordFormatKey = stringPreferencesKey("record_format")
private val SherpaProviderKey = stringPreferencesKey("sherpa_provider")
private val SherpaStreamingModelKey = stringPreferencesKey("sherpa_streaming_model")
private val SherpaOfflineModelKey = stringPreferencesKey("sherpa_offline_model")
private val GlassEnabledKey = booleanPreferencesKey("glass_enabled")
private val BlurEnabledKey = booleanPreferencesKey("blur_enabled")
private val GlassOpacityKey = intPreferencesKey("glass_opacity")
private val CompactUiModeKey = booleanPreferencesKey("compact_ui_mode")
private val ScenePresetKey = stringPreferencesKey("scene_preset")

internal data class AuthorizedApp(
    val packageName: String,
    val signatureSha256: String,
)

internal class AuthStore(private val context: Context) {
    val allowedAppsFlow: Flow<List<AuthorizedApp>> = context.dataStore.data.map { prefs ->
        val entries = prefs[AllowedAppsKey].orEmpty()
        entries.mapNotNull { parseEntry(it) }.sortedBy { it.packageName }
    }

    val selectedModelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SelectedModelKey]?.trim().takeUnless { it.isNullOrBlank() } ?: "small-q8_0"
    }

    val engineFlow: Flow<TranscriptionEngine> = context.dataStore.data.map { prefs ->
        TranscriptionEngine.fromId(prefs[EngineKey])
    }

    val processingModeFlow: Flow<ProcessingMode> = context.dataStore.data.map { prefs ->
        when (prefs[ProcessingModeKey]?.trim()?.lowercase()) {
            "parallel" -> ProcessingMode.PARALLEL
            else -> ProcessingMode.QUEUE
        }
    }

    val useGpuFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[UseGpuKey] ?: true
    }

    val decodeModeFlow: Flow<DecodeMode> = context.dataStore.data.map { prefs ->
        when (prefs[DecodeModeKey]?.trim()?.lowercase()) {
            "accurate" -> DecodeMode.ACCURATE
            else -> DecodeMode.FAST
        }
    }

    val autoStrategyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AutoStrategyKey] ?: true
    }

    val multiThreadFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MultiThreadKey] ?: true
    }

    val threadCountFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[ThreadCountKey] ?: 0).coerceAtLeast(0)
    }

    val recordFormatFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[RecordFormatKey]?.trim().takeUnless { it.isNullOrBlank() } ?: "m4a"
    }

    val sherpaProviderFlow: Flow<SherpaProvider> = context.dataStore.data.map { prefs ->
        SherpaProvider.fromId(prefs[SherpaProviderKey])
    }

    val sherpaStreamingModelFlow: Flow<SherpaStreamingModel> = context.dataStore.data.map { prefs ->
        SherpaStreamingModel.fromId(prefs[SherpaStreamingModelKey])
    }

    val sherpaOfflineModelFlow: Flow<SherpaOfflineModel> = context.dataStore.data.map { prefs ->
        SherpaOfflineModel.fromId(prefs[SherpaOfflineModelKey])
    }

    val glassEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[GlassEnabledKey] ?: true
    }

    val blurEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BlurEnabledKey] ?: true
    }

    val glassOpacityFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        val stored = prefs[GlassOpacityKey] ?: 100
        (stored.coerceIn(50, 150) / 100f)
    }

    val compactUiModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[CompactUiModeKey] ?: true
    }

    val scenePresetFlow: Flow<ScenePreset> = context.dataStore.data.map { prefs ->
        ScenePreset.fromId(prefs[ScenePresetKey])
    }

    fun isAllowed(packageName: String?, signatureSha256: String?, allowed: List<AuthorizedApp>): Boolean {
        if (packageName.isNullOrBlank() || signatureSha256.isNullOrBlank()) return false
        return allowed.any { it.packageName == packageName && it.signatureSha256 == signatureSha256 }
    }

    suspend fun allow(packageName: String, signatureSha256: String) {
        val entry = buildEntry(packageName, signatureSha256)
        context.dataStore.edit { prefs ->
            val next = prefs[AllowedAppsKey].orEmpty().toMutableSet()
            next.add(entry)
            prefs[AllowedAppsKey] = next
        }
    }

    suspend fun revoke(packageName: String, signatureSha256: String) {
        val entry = buildEntry(packageName, signatureSha256)
        context.dataStore.edit { prefs ->
            val next = prefs[AllowedAppsKey].orEmpty().toMutableSet()
            next.remove(entry)
            prefs[AllowedAppsKey] = next
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(AllowedAppsKey)
        }
    }

    suspend fun setSelectedModel(model: String) {
        val safe = model.trim().ifBlank { "small-q8_0" }
        context.dataStore.edit { prefs ->
            prefs[SelectedModelKey] = safe
        }
    }

    suspend fun setEngine(engine: TranscriptionEngine) {
        context.dataStore.edit { prefs ->
            prefs[EngineKey] = engine.id
        }
    }

    suspend fun setProcessingMode(mode: ProcessingMode) {
        context.dataStore.edit { prefs ->
            prefs[ProcessingModeKey] = mode.name.lowercase()
        }
    }

    suspend fun setUseGpu(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[UseGpuKey] = enabled
        }
    }

    suspend fun setDecodeMode(mode: DecodeMode) {
        context.dataStore.edit { prefs ->
            prefs[DecodeModeKey] = mode.name.lowercase()
        }
    }

    suspend fun setAutoStrategy(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AutoStrategyKey] = enabled
        }
    }

    suspend fun setMultiThread(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[MultiThreadKey] = enabled
        }
    }

    suspend fun setThreadCount(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[ThreadCountKey] = value.coerceAtLeast(0)
        }
    }

    suspend fun setRecordFormat(formatId: String) {
        val safe = formatId.trim().ifBlank { "m4a" }
        context.dataStore.edit { prefs ->
            prefs[RecordFormatKey] = safe
        }
    }

    suspend fun setSherpaProvider(provider: SherpaProvider) {
        context.dataStore.edit { prefs ->
            prefs[SherpaProviderKey] = provider.id
        }
    }

    suspend fun setSherpaStreamingModel(model: SherpaStreamingModel) {
        context.dataStore.edit { prefs ->
            prefs[SherpaStreamingModelKey] = model.id
        }
    }

    suspend fun setSherpaOfflineModel(model: SherpaOfflineModel) {
        context.dataStore.edit { prefs ->
            prefs[SherpaOfflineModelKey] = model.id
        }
    }

    suspend fun setGlassEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GlassEnabledKey] = enabled
        }
    }

    suspend fun setBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BlurEnabledKey] = enabled
        }
    }

    suspend fun setGlassOpacity(value: Float) {
        val pct = (value.coerceIn(0.5f, 1.5f) * 100f).toInt()
        context.dataStore.edit { prefs ->
            prefs[GlassOpacityKey] = pct
        }
    }

    suspend fun setCompactUiMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CompactUiModeKey] = enabled
        }
    }

    suspend fun setScenePreset(preset: ScenePreset) {
        context.dataStore.edit { prefs ->
            prefs[ScenePresetKey] = preset.id
        }
    }

    private fun buildEntry(packageName: String, signatureSha256: String): String {
        return "$packageName|$signatureSha256"
    }

    private fun parseEntry(raw: String): AuthorizedApp? {
        val parts = raw.split('|')
        if (parts.size != 2) return null
        val pkg = parts[0].trim()
        val sig = parts[1].trim()
        if (pkg.isBlank() || sig.isBlank()) return null
        return AuthorizedApp(pkg, sig)
    }
}
