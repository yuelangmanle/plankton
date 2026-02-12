package com.plankton.one102.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.data.db.AiCacheEntity
import com.plankton.one102.ui.AiCacheViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard

@Composable
fun AiCacheScreen(viewModel: AiCacheViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val list by viewModel.entries.collectAsStateWithLifecycle()

    var q by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<AiCacheEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<AiCacheEntity?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmClearSpeciesInfo by remember { mutableStateOf(false) }

    val filtered = remember(list, q) {
        val query = q.trim()
        if (query.isEmpty()) return@remember list
        val lower = query.lowercase()
        list.filter { e ->
            e.nameCn.contains(query) ||
                (e.nameLatin ?: "").lowercase().contains(lower) ||
                e.purpose.contains(query) ||
                e.apiTag.contains(query)
        }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI 缓存", style = MaterialTheme.typography.titleLarge)
            Text(
                "用途：缓存 AI 返回的结构化结果（含提示词与原文），避免重复调用并便于追溯。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("搜索") },
                placeholder = { Text("中文名/拉丁名/purpose/apiTag") },
            )
            Text(
                "${filtered.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { confirmClearSpeciesInfo = true }, modifier = Modifier.weight(1f)) { Text("清空物种补齐缓存") }
            OutlinedButton(onClick = { confirmClearAll = true }, modifier = Modifier.weight(1f)) { Text("清空全部缓存") }
        }

        if (filtered.isEmpty()) {
            Text("暂无缓存。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.key }) { e ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(e.nameCn, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!e.nameLatin.isNullOrBlank()) {
                                    Text(
                                        e.nameLatin.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "${e.purpose} · ${e.apiTag} · ${e.updatedAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            IconButton(onClick = { detail = e }) { Icon(imageVector = Icons.Outlined.Visibility, contentDescription = "查看") }
                            IconButton(onClick = { confirmDelete = e }) { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除") }
                        }
                    }
                }
            }
        }
    }

    if (detail != null) {
        val e = detail!!
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("缓存详情") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${e.nameCn} · ${e.purpose} · ${e.apiTag}", style = MaterialTheme.typography.titleMedium)
                    if (!e.nameLatin.isNullOrBlank()) Text("拉丁名：${e.nameLatin}", style = MaterialTheme.typography.bodySmall)
                    if (e.wetWeightMg != null) Text("湿重：${e.wetWeightMg} mg/个", style = MaterialTheme.typography.bodySmall)
                    val tax = listOf(e.lvl1, e.lvl2, e.lvl3, e.lvl4, e.lvl5).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                    if (tax.isNotBlank()) Text("分类：$tax", style = MaterialTheme.typography.bodySmall)
                    Text("更新时间：${e.updatedAt}", style = MaterialTheme.typography.bodySmall)

                    Text("提示词：", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(e.prompt, style = MaterialTheme.typography.bodySmall) }

                    Text("原文：", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer { Text(e.raw, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.copyToClipboard("AI 缓存", "PROMPT:\n${e.prompt}\n\nRAW:\n${e.raw}")
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("复制") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
        )
    }

    if (confirmDelete != null) {
        val e = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除缓存") },
            text = { Text("将删除缓存「${e.nameCn} / ${e.purpose} / ${e.apiTag}」。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteByKey(e.key)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        confirmDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }

    if (confirmClearSpeciesInfo) {
        AlertDialog(
            onDismissRequest = { confirmClearSpeciesInfo = false },
            title = { Text("清空物种补齐缓存") },
            text = { Text("将清空 purpose=species_info 的缓存。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSpeciesInfo()
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                        confirmClearSpeciesInfo = false
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClearSpeciesInfo = false }) { Text("取消") } },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空全部缓存") },
            text = { Text("将清空所有 AI 缓存。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                        confirmClearAll = false
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("取消") } },
        )
    }
}
}

private fun android.content.Context.copyToClipboard(label: String, text: String) {
    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}
