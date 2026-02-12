package com.plankton.one102.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plankton.one102.export.WorkbookPreview
import com.plankton.one102.ui.components.GlassCard

data class WorkbookPreviewSummary(
    val entryCount: Int,
    val pointCount: Int,
    val lines: List<String> = emptyList(),
)

@Composable
fun WorkbookPreviewDialog(
    title: String,
    loading: Boolean,
    error: String?,
    preview: WorkbookPreview?,
    summary: WorkbookPreviewSummary? = null,
    onClose: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("仅用于快速核对数值，最终以导出的 .xlsx 为准。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = onClose) { Text("关闭") }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text("生成预览中…")
                        }
                    }
                    return@Column
                }

                if (error != null) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("预览失败：$error", color = MaterialTheme.colorScheme.error)
                        if (onRetry != null) {
                            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                    return@Column
                }

                val wb = preview
                if (wb == null || wb.sheets.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("（无可预览内容）", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        if (onRetry != null) OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                    }
                    return@Column
                }

                var tabIndex by remember { mutableIntStateOf(0) }
                tabIndex = tabIndex.coerceIn(0, wb.sheets.size - 1)

                TabRow(selectedTabIndex = tabIndex) {
                    wb.sheets.forEachIndexed { i, s ->
                        Tab(
                            selected = i == tabIndex,
                            onClick = { tabIndex = i },
                            text = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }

                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val isPhone = screenWidthDp < 600
                var zoom by remember { mutableFloatStateOf(if (isPhone) 1.35f else 1.0f) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary?.let { info ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("预览摘要", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "条目数 ${info.entryCount} · 点位数 ${info.pointCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (info.lines.isNotEmpty()) {
                                    for (line in info.lines) {
                                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("缩放：${(zoom * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(10.dp))
                        IconButton(onClick = { zoom = (zoom - 0.1f).coerceIn(0.75f, 2.0f) }) {
                            Icon(Icons.Outlined.Remove, contentDescription = "缩小")
                        }
                        IconButton(onClick = { zoom = (zoom + 0.1f).coerceIn(0.75f, 2.0f) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "放大")
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { zoom = 1.0f }) { Text("重置") }
                    }
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 0.75f..2.0f,
                    )
                }

                val sheet = wb.sheets[tabIndex]
                val h = rememberScrollState()
                val v = rememberScrollState()

                val cellBorder = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                val cellWidth = 120.dp * zoom
                val cellPadding = 6.dp * zoom
                val fontSize = (MaterialTheme.typography.bodySmall.fontSize.value * zoom).sp

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val header = sheet.rows.firstOrNull().orEmpty()
                    val bodyRows = if (sheet.rows.size > 1) sheet.rows.drop(1) else emptyList()

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(h)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (cell in header) {
                                Surface(
                                    modifier = Modifier.width(cellWidth),
                                    border = cellBorder,
                                    tonalElevation = 1.dp,
                                ) {
                                    Text(
                                        cell,
                                        modifier = Modifier.padding(cellPadding),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(h)
                                .verticalScroll(v)
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (row in bodyRows) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (cell in row) {
                                        Surface(
                                            modifier = Modifier.width(cellWidth),
                                            border = cellBorder,
                                            tonalElevation = 0.dp,
                                        ) {
                                            Text(
                                                cell,
                                                modifier = Modifier.padding(cellPadding),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
