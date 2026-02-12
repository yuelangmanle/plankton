package com.voiceassistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun HoldToRecordButton(
    recording: Boolean,
    enabled: Boolean,
    onStart: () -> Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    ).value
    val buttonColor = animateColorAsState(
        targetValue = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "recordColor",
    ).value

    val pressModifier = modifier.pointerInput(recording, enabled) {
        detectTapGestures(
            onPress = {
                if (!enabled) return@detectTapGestures
                val started = onStart()
                if (!started) return@detectTapGestures
                try {
                    tryAwaitRelease()
                } finally {
                    onStop()
                }
            },
        )
    }

    Button(
        onClick = {},
        enabled = enabled,
        modifier = pressModifier.graphicsLayer(
            scaleX = if (recording) pulseScale else 1f,
            scaleY = if (recording) pulseScale else 1f,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = if (recording) {
            ButtonDefaults.buttonColors(containerColor = buttonColor)
        } else {
            ButtonDefaults.buttonColors(containerColor = buttonColor)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recording) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.onError, CircleShape),
                )
            }
            Text(if (recording) "录音中…松开结束" else "按住说话")
        }
    }
}
