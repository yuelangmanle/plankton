package com.voiceassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentGreen,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    onTertiary = Color(0xFF2B1B00),
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = Color(0xFFE7EDF1),
    onSurfaceVariant = AppTextSoft,
    error = AppError,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VoiceAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, shapes = AppShapes, content = content)
}
