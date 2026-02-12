package com.voiceassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voiceassistant.ui.theme.GlassBorder
import com.voiceassistant.ui.theme.GlassGradient1
import com.voiceassistant.ui.theme.GlassShadow
import com.voiceassistant.ui.theme.GlassWhite
import com.voiceassistant.ui.theme.GlassWhiteWeak

data class GlassPrefs(
    val enabled: Boolean,
    val blur: Boolean,
    val opacity: Float,
)

val LocalGlassPrefs = staticCompositionLocalOf { GlassPrefs(enabled = true, blur = true, opacity = 1f) }

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val prefs = LocalGlassPrefs.current
    val brush: Brush = if (prefs.enabled) {
        GlassGradient1
    } else {
        SolidColor(MaterialTheme.colorScheme.background)
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush)
                .then(
                    if (prefs.enabled && prefs.blur) {
                        Modifier.blur(24.dp)
                    } else {
                        Modifier
                    },
                ),
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glassEnabled: Boolean = LocalGlassPrefs.current.enabled,
    blurEnabled: Boolean = LocalGlassPrefs.current.blur,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (glassEnabled) {
        val opacityFactor = LocalGlassPrefs.current.opacity.coerceIn(0.5f, 1.5f)
        val baseAlpha = if (blurEnabled) 0.4f else 0.8f
        val cardAlpha = (baseAlpha * opacityFactor).coerceIn(0.2f, 0.95f)
        val shadowModifier = if (elevation > 0.dp) {
            Modifier.shadow(elevation, shape, spotColor = GlassShadow, ambientColor = GlassShadow)
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .then(shadowModifier)
                .clip(shape)
                .background(GlassWhite.copy(alpha = cardAlpha))
                .border(1.dp, GlassBorder, shape),
        ) {
            Column { content() }
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        ) {
            content()
        }
    }
}
