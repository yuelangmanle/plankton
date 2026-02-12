package com.plankton.one102.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SlateBlue,
    onPrimary = Color.White,
    primaryContainer = AquaBlue,
    onPrimaryContainer = Color(0xFF0B1220),
    secondary = MintGreen,
    onSecondary = Color(0xFF0B1220),
    tertiary = SoftYellow,
    onTertiary = Color(0xFF0B1220),
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
)

@Composable
fun PlanktonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}

