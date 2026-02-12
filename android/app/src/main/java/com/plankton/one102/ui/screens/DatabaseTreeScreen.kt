package com.plankton.one102.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard

private val LEVEL_LABELS: List<String> = listOf("大类", "纲", "目", "科", "属")

private fun segmentsFor(item: SpeciesDbItem): List<String> {
    val t = item.taxonomy
    val lvl1 = normalizeLvl1Name(t?.lvl1.orEmpty()).ifBlank { "（未分类）" }
    val lvl2 = t?.lvl2?.takeIf { it.isNotBlank() }
    val lvl3 = t?.lvl3?.takeIf { it.isNotBlank() }
    val lvl4 = t?.lvl4?.takeIf { it.isNotBlank() }
    val lvl5 = t?.lvl5?.takeIf { it.isNotBlank() }
    return listOfNotNull(lvl1, lvl2, lvl3, lvl4, lvl5)
}

private fun matchesPrefix(segs: List<String>, prefix: List<String>): Boolean {
    if (prefix.isEmpty()) return true
    if (segs.size < prefix.size) return false
    for (i in prefix.indices) {
        if (segs[i] != prefix[i]) return false
    }
    return true
}

@Composable
fun DatabaseTreeScreen(
    dbViewModel: DatabaseViewModel,
    padding: PaddingValues,
) {
    val allItems by dbViewModel.items.collectAsStateWithLifecycle()

    var q by remember { mutableStateOf("") }
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var leafDialog by remember { mutableStateOf<Pair<String, List<SpeciesDbItem>>?>(null) }

    val filtered = remember(allItems, q) {
        val query = q.trim()
        if (query.isBlank()) return@remember allItems
        val qLower = query.lowercase()
        allItems.filter { item ->
            item.nameCn.contains(query) ||
                (item.nameLatin ?: "").lowercase().contains(qLower) ||
                (item.taxonomy?.lvl1 ?: "").contains(query) ||
                (item.taxonomy?.lvl4 ?: "").contains(query)
        }
    }

    val currentItems = remember(filtered, path) {
        filtered.filter { matchesPrefix(segmentsFor(it), path) }
    }

    val depth = path.size
    val levelLabel = LEVEL_LABELS.getOrNull(depth) ?: "物种"

    val childCounts = remember(currentItems, depth) {
        val map = LinkedHashMap<String, Int>()
        for (it in currentItems) {
            val segs = segmentsFor(it)
            val next = segs.getOrNull(depth) ?: continue
            map[next] = (map[next] ?: 0) + 1
        }
        map
    }

    val orderedChildren = remember(childCounts, depth) {
        val keys = childCounts.keys.toList()
        if (depth == 0) {
            val order = LVL1_ORDER + listOf("（未分类）")
            val ordered = order.filter { it in childCounts.keys }
            val rest = keys.filterNot { it in order }.sorted()
            ordered + rest
        } else {
            keys.sorted()
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
            Text("数据库 · 分类路线图", style = MaterialTheme.typography.titleLarge)
            Text(
                "像路线图一样逐级下钻（大类→纲→目→科→属）。可随时查看“当前节点下全部物种”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

        OutlinedTextField(
            value = q,
            onValueChange = {
                q = it
                if (path.isNotEmpty()) path = emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索") },
            placeholder = { Text("中文名/拉丁名/大类/科…（搜索会回到根节点）") },
        )

        if (path.isNotEmpty()) {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { path = emptyList() }) { Text("根") }
                for (i in path.indices) {
                    Text("→", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    OutlinedButton(onClick = { path = path.take(i + 1) }) {
                        Text(path[i], maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Text("当前层级：$levelLabel", style = MaterialTheme.typography.titleMedium)

        if (orderedChildren.isEmpty()) {
            Text("没有更多下级分类。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(orderedChildren, key = { it }) { label ->
                    val count = childCounts[label] ?: 0
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("物种数：$count", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            OutlinedButton(
                                onClick = { path = path + label },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("进入") }
                        }
                    }
                }
            }
        }

        val showNodeSpeciesButton = currentItems.isNotEmpty() && (path.isNotEmpty() || q.isNotBlank())
        if (showNodeSpeciesButton) {
            OutlinedButton(
                onClick = {
                    val title = if (path.isEmpty()) "根节点" else path.joinToString(" / ")
                    leafDialog = title to currentItems
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("查看当前节点下全部物种（${currentItems.size}）") }
        }
        }
    }

    if (leafDialog != null) {
        val (titlePath, list) = leafDialog!!
        AlertDialog(
            onDismissRequest = { leafDialog = null },
            confirmButton = { TextButton(onClick = { leafDialog = null }) { Text("关闭") } },
            title = { Text(titlePath) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.sortedBy { it.nameCn }, key = { it.nameCn }) { sp ->
                        val wet = sp.wetWeightMg?.let { "${it} mg/个" } ?: "未查到湿重"
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(sp.nameCn, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!sp.nameLatin.isNullOrBlank()) {
                                    Text(sp.nameLatin.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                }
                                Text(wet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            },
        )
    }
}
