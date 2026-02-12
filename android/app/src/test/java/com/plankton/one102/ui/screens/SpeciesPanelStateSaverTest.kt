package com.plankton.one102.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesPanelStateSaverTest {
    @Test
    fun toSaveableList_and_restore_roundTrip_allFalse() {
        val state = SpeciesPanelState()
        val restored = speciesPanelStateFromSaveableList(state.toSaveableList())
        assertEquals(state, restored)
    }

    @Test
    fun toSaveableList_and_restore_roundTrip_allTrue() {
        val state = SpeciesPanelState(
            imageImportExpanded = true,
            autoMatchExpanded = true,
            pointExpanded = true,
            bulkExpanded = true,
        )
        val restored = speciesPanelStateFromSaveableList(state.toSaveableList())
        assertEquals(state, restored)
    }

    @Test
    fun toSaveableList_and_restore_roundTrip_mixedValues() {
        val state = SpeciesPanelState(
            imageImportExpanded = true,
            autoMatchExpanded = false,
            pointExpanded = true,
            bulkExpanded = false,
        )
        val restored = speciesPanelStateFromSaveableList(state.toSaveableList())
        assertEquals(state, restored)
    }

    @Test
    fun restore_fromEmptyList_fallsBackToDefaults() {
        val restored = speciesPanelStateFromSaveableList(emptyList())
        assertEquals(SpeciesPanelState(), restored)
    }

    @Test
    fun restore_fromShortList_usesAvailableValuesAndDefaults() {
        val restored = speciesPanelStateFromSaveableList(
            listOf(
                true,
                true,
            ),
        )
        assertEquals(
            SpeciesPanelState(
                imageImportExpanded = true,
                autoMatchExpanded = true,
                pointExpanded = false,
                bulkExpanded = false,
            ),
            restored,
        )
    }
}
