package com.voiceassistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun TaskTimeline(
    status: String,
    durationMs: Long? = null,
    qualityLabel: String? = null,
    qualityScore: Int? = null,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs?.takeIf { it >= 0 }?.let { " · ${"%.1f".format(it / 1000.0)} 秒" }.orEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        Text("任务时间线：排队 → $status$duration", style = MaterialTheme.typography.bodySmall)
        if (qualityLabel != null && qualityScore != null) {
            Text("识别质量：$qualityLabel（$qualityScore 分）", style = MaterialTheme.typography.bodySmall)
        }
    }
}
