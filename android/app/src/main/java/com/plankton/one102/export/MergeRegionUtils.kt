package com.plankton.one102.export

internal data class MergeRegion(
    val firstRow: Int,
    val lastRow: Int,
    val firstCol: Int,
    val lastCol: Int,
)

internal object MergeRegionUtils {
    /**
     * Apache POI requires merged regions to contain at least 2 cells.
     * Returns null when region is invalid or effectively a single cell.
     */
    fun normalizeOrNull(
        firstRow: Int,
        lastRow: Int,
        firstCol: Int,
        lastCol: Int,
    ): MergeRegion? {
        if (firstRow < 0 || lastRow < 0 || firstCol < 0 || lastCol < 0) return null
        if (lastRow < firstRow || lastCol < firstCol) return null
        if (firstRow == lastRow && firstCol == lastCol) return null
        return MergeRegion(firstRow, lastRow, firstCol, lastCol)
    }
}

