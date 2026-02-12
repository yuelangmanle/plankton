package com.plankton.one102.ui.screens

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.plankton.one102.domain.Id

internal data class SpeciesPanelState(
    val imageImportExpanded: Boolean = false,
    val autoMatchExpanded: Boolean = false,
    val pointExpanded: Boolean = false,
    val bulkExpanded: Boolean = false,
)

internal fun SpeciesPanelState.toSaveableList(): List<Boolean> =
    listOf(
        imageImportExpanded,
        autoMatchExpanded,
        pointExpanded,
        bulkExpanded,
    )

internal fun speciesPanelStateFromSaveableList(saved: List<Boolean>): SpeciesPanelState =
    SpeciesPanelState(
        imageImportExpanded = saved.getOrNull(0) ?: false,
        autoMatchExpanded = saved.getOrNull(1) ?: false,
        pointExpanded = saved.getOrNull(2) ?: false,
        bulkExpanded = saved.getOrNull(3) ?: false,
    )

internal val SpeciesPanelStateSaver: Saver<SpeciesPanelState, Any> =
    listSaver(
        save = { state -> state.toSaveableList() },
        restore = { saved -> speciesPanelStateFromSaveableList(saved) },
    )

internal data class SpeciesAddDialogState(
    val query: String = "",
    val visible: Boolean = false,
)

internal data class SpeciesLibraryUiState(
    val menuOpen: Boolean = false,
    val createOpen: Boolean = false,
    val nameDraft: String = "",
    val error: String? = null,
)

internal data class SpeciesConfirmState(
    val clearPoint: Boolean = false,
    val clearAll: Boolean = false,
    val merge: Boolean = false,
)

internal data class SpeciesInlineEditState(
    val taxonomyEditId: Id? = null,
    val taxonomyQueryId: Id? = null,
    val wetWeightQueryId: Id? = null,
    val countEditId: Id? = null,
    val countEditPointId: Id? = null,
    val countEditText: String = "",
    val batchDialogOpen: Boolean = false,
)
