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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.plankton.one102.data.repo.AliasRecord
import com.plankton.one102.ui.AliasViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard

@Composable
fun AliasScreen(viewModel: AliasViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val aliases by viewModel.aliases.collectAsStateWithLifecycle()

    var q by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<AliasRecord?>(null) }
    var confirmDelete by remember { mutableStateOf<AliasRecord?>(null) }

    val filtered = remember(aliases, q) {
        val query = q.trim()
        if (query.isEmpty()) return@remember aliases
        val lower = query.lowercase()
        aliases.filter { it.alias.contains(query) || it.canonicalNameCn.lowercase().contains(lower) }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("别名管理", style = MaterialTheme.typography.titleLarge)
            Text(
                "用途：把“你常用的别名/旧名/简写”映射到标准中文名；自动补齐与本机检索会先尝试按别名转换。",
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
                placeholder = { Text("别名/标准名") },
            )
            Button(onClick = { editor = AliasRecord(alias = "", canonicalNameCn = "", updatedAt = "") }) { Text("新增") }
        }

        if (filtered.isEmpty()) {
            Text("暂无别名。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
                items(filtered, key = { it.alias }) { a ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(a.alias, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "→ ${a.canonicalNameCn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            TextButton(onClick = { editor = a }) { Text("编辑") }
                            IconButton(onClick = { confirmDelete = a }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editor != null) {
        val editing = editor!!
        val isNew = editing.alias.isBlank()
        var alias by remember(editing.alias) { mutableStateOf(editing.alias) }
        var canonical by remember(editing.alias) { mutableStateOf(editing.canonicalNameCn) }
        var error by remember(editing.alias) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editor = null },
            title = { Text(if (isNew) "新增别名" else "编辑别名") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        enabled = isNew,
                        singleLine = true,
                        label = { Text("别名（唯一）") },
                    )
                    OutlinedTextField(
                        value = canonical,
                        onValueChange = { canonical = it },
                        singleLine = true,
                        label = { Text("标准中文名") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val a = alias.trim()
                        val c = canonical.trim()
                        error = null
                        if (a.isBlank() || c.isBlank()) {
                            error = "别名与标准名都不能为空。"
                            return@TextButton
                        }
                        viewModel.upsert(a, c)
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        editor = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editor = null }) { Text("取消") } },
        )
    }

    if (confirmDelete != null) {
        val a = confirmDelete!!
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除别名") },
            text = { Text("将删除别名「${a.alias}」。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(a.alias)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        confirmDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}
}
