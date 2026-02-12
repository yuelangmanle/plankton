package com.plankton.one102.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeRegionUtilsTest {
    @Test
    fun singleCellRegion_returnsNull() {
        val region = MergeRegionUtils.normalizeOrNull(
            firstRow = 5,
            lastRow = 5,
            firstCol = 0,
            lastCol = 0,
        )
        assertNull(region)
    }

    @Test
    fun rowMerge_isValid() {
        val region = MergeRegionUtils.normalizeOrNull(
            firstRow = 5,
            lastRow = 5,
            firstCol = 0,
            lastCol = 1,
        )
        assertEquals(MergeRegion(5, 5, 0, 1), region)
    }

    @Test
    fun colMerge_isValid() {
        val region = MergeRegionUtils.normalizeOrNull(
            firstRow = 5,
            lastRow = 6,
            firstCol = 0,
            lastCol = 0,
        )
        assertEquals(MergeRegion(5, 6, 0, 0), region)
    }

    @Test
    fun reversedBounds_returnsNull() {
        val region = MergeRegionUtils.normalizeOrNull(
            firstRow = 7,
            lastRow = 6,
            firstCol = 0,
            lastCol = 1,
        )
        assertNull(region)
    }

    @Test
    fun negativeBounds_returnsNull() {
        val region = MergeRegionUtils.normalizeOrNull(
            firstRow = -1,
            lastRow = 2,
            firstCol = 0,
            lastCol = 1,
        )
        assertNull(region)
    }
}

