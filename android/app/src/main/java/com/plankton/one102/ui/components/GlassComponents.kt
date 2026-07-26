package com.plankton.one102.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plankton.one102.ui.theme.GlassBorder
import com.plankton.one102.ui.theme.GlassGradient1
import com.plankton.one102.ui.theme.GlassShadow
import com.plankton.one102.ui.theme.LocalDensityTokens
import com.plankton.one102.ui.theme.LocalDesignTokens

data class GlassPrefs(
    val enabled: Boolean,
    val blur: Boolean,
    val opacity: Float,
)

val LocalGlassPrefs = staticCompositionLocalOf { GlassPrefs(enabled = true, blur = true, opacity = 1f) }

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val prefs = LocalGlassPrefs.current
    val baseBrush: Brush = if (prefs.enabled) {
        GlassGradient1
    } else {
        SolidColor(MaterialTheme.colorScheme.background)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBrush)
    ) {
        // The background deliberately stays a single opaque draw pass. Older vendor GPU
        // renderers can turn full-screen transparent/blur layers into an opaque white sheet.
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glassEnabled: Boolean = LocalGlassPrefs.current.enabled,
    blurEnabled: Boolean = LocalGlassPrefs.current.blur,
    shape: Shape = RoundedCornerShape(LocalDensityTokens.current.cardCorner),
    elevation: Dp = LocalDensityTokens.current.cardElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    val designTokens = LocalDesignTokens.current
    if (glassEnabled) {
        val shadowModifier = if (elevation > 0.dp) {
            Modifier.shadow(
                elevation,
                shape,
                spotColor = designTokens.cardShadowColor,
                ambientColor = designTokens.cardShadowColor,
            )
        } else {
            Modifier
        }
        Box(
            modifier = modifier
                .then(shadowModifier)
                .clip(shape)
                // Keep cards opaque as well: this avoids a second transparent composition
                // layer while retaining the soft, light visual hierarchy.
                .background(if (blurEnabled) Color(0xFFFAFCFE) else MaterialTheme.colorScheme.surface)
                .border(1.dp, designTokens.cardBorderColor, shape)
        ) {
            Column {
                content()
            }
        }
    } else {
        // Fallback to standard Material Card
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

@Composable
fun GlassContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                scaleIn(initialScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                scaleOut(targetScale = 0.95f, animationSpec = spring(stiffness = Spring.StiffnessLow)),
        modifier = modifier
    ) {
        content()
    }
}
