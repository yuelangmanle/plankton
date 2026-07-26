package com.plankton.one102.domain

import kotlin.math.abs

/**
 * Platform-neutral display-mode information used to choose a requested refresh rate.
 *
 * Some devices expose 90 Hz and 120 Hz modes at a resolution other than the active
 * mode. The refresh-rate target must win over a same-resolution but lower-rate mode;
 * otherwise selecting 90 Hz can silently result in 60 Hz.
 */
internal data class DisplayModeCandidate(
    val modeId: Int,
    val refreshRate: Float,
    val physicalWidth: Int,
    val physicalHeight: Int,
)

internal fun selectDisplayMode(
    current: DisplayModeCandidate,
    supported: List<DisplayModeCandidate>,
    targetHz: Float,
): DisplayModeCandidate? {
    if (supported.isEmpty()) return null

    return supported.minWithOrNull(
        compareBy<DisplayModeCandidate> {
            if (abs(it.refreshRate - targetHz) <= REFRESH_RATE_TOLERANCE_HZ) 0 else 1
        }.thenBy {
            abs(it.refreshRate - targetHz)
        }.thenBy {
            if (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight) 0 else 1
        }.thenByDescending {
            it.refreshRate
        }.thenBy {
            it.modeId
        },
    )
}

private const val REFRESH_RATE_TOLERANCE_HZ = 0.5f
