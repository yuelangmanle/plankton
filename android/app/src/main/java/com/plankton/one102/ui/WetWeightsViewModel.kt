package com.plankton.one102.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.repo.WetWeightRepository
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class WetWeightsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: WetWeightRepository = (app as PlanktonApplication).wetWeightRepository

    private val builtinEntries = MutableStateFlow<List<WetWeightEntry>>(emptyList())

    val customEntries: StateFlow<List<WetWeightEntry>> = repository.observeCustomEntries().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    data class WetWeightSources(
        val builtin: Boolean,
        val custom: Boolean,
    )

    data class WetWeightItem(
        val entry: WetWeightEntry,
        val sources: WetWeightSources,
    )

    val items: StateFlow<List<WetWeightItem>> = combine(builtinEntries, customEntries) { builtin, custom ->
        val builtinMap = builtin.associateBy { it.nameCn.trim() }
        val customMap = custom.associateBy { it.nameCn.trim() }
        val names = buildSet {
            addAll(builtinMap.keys)
            addAll(customMap.keys)
        }.filter { it.isNotBlank() }

        names.map { nameCn ->
            val b = builtinMap[nameCn]
            val c = customMap[nameCn]
            val display = when {
                c == null -> b ?: WetWeightEntry(nameCn = nameCn, wetWeightMg = 0.0)
                b == null -> c
                else -> WetWeightEntry(
                    nameCn = nameCn,
                    nameLatin = c.nameLatin ?: b.nameLatin,
                    wetWeightMg = c.wetWeightMg,
                    taxonomy = WetWeightTaxonomy(
                        group = c.taxonomy.group ?: b.taxonomy.group,
                        sub = c.taxonomy.sub ?: b.taxonomy.sub,
                    ),
                )
            }
            WetWeightItem(
                entry = display,
                sources = WetWeightSources(builtin = b != null, custom = c != null),
            )
        }.sortedWith { a, b -> collator.compare(a.entry.nameCn, b.entry.nameCn) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            builtinEntries.value = repository.getBuiltinEntries()
        }
    }

    fun upsertCustom(entry: WetWeightEntry) {
        viewModelScope.launch { repository.upsertCustom(entry) }
    }

    fun deleteCustom(nameCn: String) {
        viewModelScope.launch { repository.deleteCustom(nameCn) }
    }

    companion object {
        private val collator: Collator = Collator.getInstance(Locale.CHINA)
    }
}
