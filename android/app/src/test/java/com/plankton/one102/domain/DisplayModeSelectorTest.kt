package com.plankton.one102.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeSelectorTest {
    private val current = mode(id = 1, hz = 60f, width = 1080, height = 2400)

    @Test
    fun target90_prefersExactRateAtCurrentResolution() {
        val selected = selectDisplayMode(
            current = current,
            supported = listOf(
                current,
                mode(id = 2, hz = 90f, width = 1080, height = 2400),
                mode(id = 3, hz = 90f, width = 1440, height = 3200),
            ),
            targetHz = 90f,
        )

        assertEquals(2, selected?.modeId)
    }

    @Test
    fun target90_usesExactRateEvenWhenItIsAtAnotherResolution() {
        val selected = selectDisplayMode(
            current = current,
            supported = listOf(
                current,
                mode(id = 2, hz = 90f, width = 1440, height = 3200),
            ),
            targetHz = 90f,
        )

        assertEquals(2, selected?.modeId)
    }

    @Test
    fun target120_fallsBackTo90BeforeSameResolution60() {
        val selected = selectDisplayMode(
            current = current,
            supported = listOf(
                current,
                mode(id = 2, hz = 90f, width = 1440, height = 3200),
            ),
            targetHz = 120f,
        )

        assertEquals(2, selected?.modeId)
    }

    @Test
    fun target90_acceptsPanelReportedRateWithinTolerance() {
        val selected = selectDisplayMode(
            current = current,
            supported = listOf(
                current,
                mode(id = 2, hz = 89.94f, width = 1080, height = 2400),
            ),
            targetHz = 90f,
        )

        assertEquals(2, selected?.modeId)
    }

    @Test
    fun equidistantFallbackPrefersHigherRefreshRate() {
        val selected = selectDisplayMode(
            current = current,
            supported = listOf(
                mode(id = 2, hz = 60f, width = 1080, height = 2400),
                mode(id = 3, hz = 120f, width = 1080, height = 2400),
            ),
            targetHz = 90f,
        )

        assertEquals(3, selected?.modeId)
    }

    @Test
    fun emptySupportedModes_returnsNull() {
        assertNull(selectDisplayMode(current, emptyList(), targetHz = 90f))
    }

    private fun mode(id: Int, hz: Float, width: Int, height: Int) = DisplayModeCandidate(
        modeId = id,
        refreshRate = hz,
        physicalWidth = width,
        physicalHeight = height,
    )
}
