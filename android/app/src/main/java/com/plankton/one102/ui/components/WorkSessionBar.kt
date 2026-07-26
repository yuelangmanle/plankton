package com.plankton.one102.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plankton.one102.domain.DatasetSavePhase
import com.plankton.one102.domain.WorkSession

@Composable
fun WorkSessionBar(
    session: WorkSession,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveText = when (session.savePhase) {
        DatasetSavePhase.Saved -> "已保存"
        DatasetSavePhase.Saving -> "保存中…"
        DatasetSavePhase.Unsaved -> "待保存"
    }
    GlassCard(modifier = modifier.fillMaxWidth(), blurEnabled = false, elevation = 0.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${session.datasetLabel} · ${session.pointLabel}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(saveText)
                        session.lastSavedAt?.let { append(" · 最近 $it") }
                        if (session.activeTaskCount > 0) append(" · 后台任务 ${session.activeTaskCount}")
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(enabled = session.undoCount > 0, onClick = onUndo) {
                    Text("撤销 ${session.undoCount}")
                }
                OutlinedButton(enabled = session.redoCount > 0, onClick = onRedo) {
                    Text("重做 ${session.redoCount}")
                }
            }
        }
    }
}
