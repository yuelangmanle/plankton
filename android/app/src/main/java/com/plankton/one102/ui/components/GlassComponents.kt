package com.plankton.one102.ui.components

import android.os.Build
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import com.plankton.one102.ui.theme.GlassGradient2
import com.plankton.one102.ui.theme.GlassShadow
import com.plankton.one102.ui.theme.GlassWhite
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
        if (prefs.enabled && prefs.blur) {
            val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(16.dp)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GlassGradient2)
                    .then(blurModifier)
                    .alpha(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.38f else 0.2f),
            )
        }
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
        val opacityFactor = LocalGlassPrefs.current.opacity.coerceIn(0.5f, 1.5f)
        val baseAlpha = if (blurEnabled) designTokens.navGlassAlphaBlur else designTokens.navGlassAlphaNoBlur
        val cardAlpha = (baseAlpha * opacityFactor).coerceIn(0.2f, 0.95f)
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
                .background(GlassWhite.copy(alpha = cardAlpha))
                .border(1.dp, designTokens.cardBorderColor, shape)
        ) {
            if (blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.08f),
                                ),
                            ),
                        )
                        .blur(10.dp),
                )
            }
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
