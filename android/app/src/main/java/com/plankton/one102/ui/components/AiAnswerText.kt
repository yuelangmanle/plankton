package com.plankton.one102.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.plankton.one102.domain.buildAiDisplayAnswer

@Composable
fun AiAnswerText(
    rawText: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    compact: Boolean = false,
    maxLines: Int? = null,
    previewChars: Int? = null,
) {
    val display = remember(rawText, previewChars) { buildAiDisplayAnswer(rawText, maxPreviewChars = previewChars) }
    var showReasoning by remember(rawText) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AiRichText(
            text = display.visibleText,
            style = style,
            compact = compact,
            maxLines = maxLines,
        )
        if (display.hasReasoning) {
            TextButton(onClick = { showReasoning = !showReasoning }) {
                Text(if (showReasoning) "收起思考过程" else "查看思考过程")
            }
            if (showReasoning) {
                GlassCard {
                    AiRichText(
                        text = display.reasoningText,
                        modifier = Modifier.padding(10.dp),
                        style = style,
                        compact = compact,
                    )
                }
            }
        }
    }
}
