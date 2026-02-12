package com.plankton.one102.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.repo.AliasRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AliasViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as PlanktonApplication
    private val repo = application.aliasRepository

    val aliases: StateFlow<List<AliasRecord>> = repo.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun upsert(alias: String, canonicalNameCn: String) {
        viewModelScope.launch { repo.upsert(alias, canonicalNameCn) }
    }

    fun delete(alias: String) {
        viewModelScope.launch { repo.delete(alias) }
    }

    fun clearAll() {
        viewModelScope.launch { repo.deleteAll() }
    }
}

