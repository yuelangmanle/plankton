package com.plankton.one102.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.db.AiCacheEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiCacheViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as PlanktonApplication
    private val repo = application.aiCacheRepository

    val entries: StateFlow<List<AiCacheEntity>> = repo.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun clearAll() {
        viewModelScope.launch { repo.deleteAll() }
    }

    fun clearSpeciesInfo() {
        viewModelScope.launch { repo.deleteSpeciesInfo() }
    }

    fun deleteByKey(key: String) {
        viewModelScope.launch { repo.deleteByKey(key) }
    }
}

