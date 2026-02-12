package com.plankton.one102.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plankton.one102.domain.UiDensityMode

@Immutable
data class DensityTokens(
    val mode: UiDensityMode,
    val scale: Float,
    val cardCorner: Dp,
    val cardElevation: Dp,
    val sectionGap: Dp,
    val blockPadding: Dp,
)

@Immutable
data class AppDesignTokens(
    val cardBorderColor: Color = GlassBorder,
    val cardShadowColor: Color = GlassShadow,
    val navGlassAlphaBlur: Float = 0.68f,
    val navGlassAlphaNoBlur: Float = 0.92f,
)

fun densityTokens(mode: UiDensityMode): DensityTokens {
    return when (mode) {
        UiDensityMode.Standard -> DensityTokens(
            mode = mode,
            scale = 1f,
            cardCorner = 18.dp,
            cardElevation = 4.dp,
            sectionGap = 12.dp,
            blockPadding = 12.dp,
        )
        UiDensityMode.Compact -> DensityTokens(
            mode = mode,
            scale = 0.9f,
            cardCorner = 14.dp,
            cardElevation = 2.dp,
            sectionGap = 8.dp,
            blockPadding = 10.dp,
        )
    }
}

val LocalDensityTokens = staticCompositionLocalOf { densityTokens(UiDensityMode.Standard) }
val LocalDesignTokens = staticCompositionLocalOf { AppDesignTokens() }
