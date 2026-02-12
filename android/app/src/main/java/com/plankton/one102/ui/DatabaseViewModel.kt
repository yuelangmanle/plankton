package com.plankton.one102.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.SpeciesDbSources
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyEntry
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.normalizeLvl1Name
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class MindMapExportRequest(
    val scopeLevel: String,
    val scopeValue: String?,
)

class DatabaseViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as PlanktonApplication
    private val wetWeightRepo = application.wetWeightRepository
    private val taxonomyRepo = application.taxonomyRepository
    private val taxonomyOverrideRepo = application.taxonomyOverrideRepository

    private val builtinWetWeights = MutableStateFlow<List<WetWeightEntry>>(emptyList())
    private val builtinTaxonomies = MutableStateFlow<Map<String, TaxonomyEntry>>(emptyMap())
    private val _mindMapExportRequest = MutableStateFlow<MindMapExportRequest?>(null)
    val mindMapExportRequest: StateFlow<MindMapExportRequest?> = _mindMapExportRequest.asStateFlow()

    val customWetWeights: StateFlow<List<WetWeightEntry>> = wetWeightRepo.observeCustomEntries()
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val customTaxonomies: StateFlow<List<TaxonomyRecord>> = taxonomyOverrideRepo.observeCustomEntries()
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val items: StateFlow<List<SpeciesDbItem>> = combine(
        builtinWetWeights,
        builtinTaxonomies,
        customWetWeights,
        customTaxonomies,
    ) { builtinWet, builtinTax, customWet, customTax ->
        val builtinWetMap = builtinWet.associateBy { it.nameCn }
        val customWetMap = customWet.associateBy { it.nameCn }
        val customTaxMap = customTax.associateBy { it.nameCn }

        val names = buildSet {
            addAll(builtinTax.keys)
            addAll(builtinWetMap.keys)
            addAll(customWetMap.keys)
            addAll(customTaxMap.keys)
        }

        names.map { nameCn ->
            val builtinTaxEntry = builtinTax[nameCn]
            val builtinWetWeight = builtinWetMap[nameCn]
            val customWetWeight = customWetMap[nameCn]
            val customTaxonomy = customTaxMap[nameCn]

            val wetAsTaxonomy = builtinWetWeight?.taxonomy?.let { ww ->
                val lvl1 = normalizeLvl1Name(ww.group.orEmpty())
                val lvl4 = ww.sub?.trim().orEmpty()
                if (lvl1.isBlank() && lvl4.isBlank()) null else Taxonomy(lvl1 = lvl1, lvl4 = lvl4)
            }
            val taxonomy = customTaxonomy?.taxonomy ?: builtinTaxEntry?.taxonomy ?: wetAsTaxonomy
            val latin = customTaxonomy?.nameLatin?.takeIf { it.isNotBlank() }
                ?: customWetWeight?.nameLatin?.takeIf { !it.isNullOrBlank() }
                ?: builtinTaxEntry?.nameLatin?.takeIf { !it.isNullOrBlank() }
                ?: builtinWetWeight?.nameLatin?.takeIf { !it.isNullOrBlank() }

            val wetWeightMg = customWetWeight?.wetWeightMg ?: builtinWetWeight?.wetWeightMg

            SpeciesDbItem(
                nameCn = nameCn,
                nameLatin = latin,
                wetWeightMg = wetWeightMg,
                taxonomy = taxonomy,
                sources = SpeciesDbSources(
                    builtinTaxonomy = builtinTaxEntry != null,
                    builtinWetWeight = builtinWetWeight != null,
                    customTaxonomy = customTaxonomy != null,
                    customWetWeight = customWetWeight != null,
                ),
            )
        }.sortedWith { a, b -> collator.compare(a.nameCn, b.nameCn) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            builtinWetWeights.value = wetWeightRepo.getBuiltinEntries()
        }
        viewModelScope.launch {
            builtinTaxonomies.value = taxonomyRepo.getBuiltinEntryMap()
        }
    }

    fun requestMindMapExport(scopeLevel: String, scopeValue: String?) {
        _mindMapExportRequest.value = MindMapExportRequest(scopeLevel = scopeLevel, scopeValue = scopeValue)
    }

    fun clearMindMapExportRequest() {
        _mindMapExportRequest.value = null
    }

    fun upsertWetWeight(
        nameCn: String,
        nameLatin: String?,
        wetWeightMg: Double,
        group: String?,
        sub: String?,
    ) {
        viewModelScope.launch {
            wetWeightRepo.upsertCustom(
                WetWeightEntry(
                    nameCn = nameCn,
                    nameLatin = nameLatin,
                    wetWeightMg = wetWeightMg,
                    taxonomy = WetWeightTaxonomy(
                        group = group,
                        sub = sub,
                    ),
                ),
            )
        }
    }

    suspend fun upsertWetWeightSync(
        nameCn: String,
        nameLatin: String?,
        wetWeightMg: Double,
        group: String?,
        sub: String?,
        importBatchId: String,
    ) {
        wetWeightRepo.upsertImported(
            WetWeightEntry(
                nameCn = nameCn,
                nameLatin = nameLatin,
                wetWeightMg = wetWeightMg,
                taxonomy = WetWeightTaxonomy(group = group, sub = sub),
            ),
            importBatchId = importBatchId,
        )
    }

    fun deleteWetWeightOverride(nameCn: String) {
        viewModelScope.launch { wetWeightRepo.deleteCustom(nameCn) }
    }

    suspend fun deleteWetWeightOverrideSync(nameCn: String) {
        wetWeightRepo.deleteCustom(nameCn)
    }

    suspend fun clearImportedWetWeights(): Int = wetWeightRepo.deleteImportedAll()

    fun upsertTaxonomyOverride(nameCn: String, nameLatin: String?, taxonomy: Taxonomy) {
        viewModelScope.launch {
            taxonomyOverrideRepo.upsertCustom(TaxonomyRecord(nameCn = nameCn, nameLatin = nameLatin, taxonomy = taxonomy))
        }
    }

    suspend fun upsertTaxonomyOverrideSync(nameCn: String, nameLatin: String?, taxonomy: Taxonomy) {
        taxonomyOverrideRepo.upsertCustom(TaxonomyRecord(nameCn = nameCn, nameLatin = nameLatin, taxonomy = taxonomy))
    }

    fun deleteTaxonomyOverride(nameCn: String) {
        viewModelScope.launch { taxonomyOverrideRepo.deleteCustom(nameCn) }
    }

    suspend fun deleteTaxonomyOverrideSync(nameCn: String) {
        taxonomyOverrideRepo.deleteCustom(nameCn)
    }

    companion object {
        private val collator: Collator = Collator.getInstance(Locale.CHINA)
    }
}
