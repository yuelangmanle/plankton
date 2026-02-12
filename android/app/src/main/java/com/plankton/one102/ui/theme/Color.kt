package com.plankton.one102.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Original Palette
val AquaBlue = Color(0xFFD3E5F7)
val MintGreen = Color(0xFFDCEBDD)
val SoftYellow = Color(0xFFF3E8C4)
val PaleOrange = Color(0xFFECCFB3)
val SlateBlue = Color(0xFF49698A)

// Glassmorphism Palette
val GlassWhite = Color(0xCCFFFFFF) // 80% White
val GlassWhiteWeak = Color(0x66FFFFFF) // 40% White
val GlassWhiteVeryWeak = Color(0x1AFFFFFF) // 10% White
val GlassText = Color(0xFF0B1220)
val GlassTextSecondary = Color(0x990B1220)

// Gradients
val GlassGradient1 = Brush.linearGradient(
    colors = listOf(
        Color(0xFFE8F3FF),
        Color(0xFFE4F6F2),
        Color(0xFFF6F1E6),
    )
)

val GlassGradient2 = Brush.linearGradient(
    colors = listOf(
        Color(0xFFEFF4FA),
        Color(0xFFF3F8F2),
        Color(0xFFF7F5EE),
    ),
)

val GlassBorder = Color(0x33283E57)
val GlassShadow = Color(0x16000000)
