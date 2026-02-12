package com.plankton.one102.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.ui.HapticKind
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.performHaptic
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard

private fun clampNonNegativeInt(v: Int): Int = if (v < 0) 0 else v

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusCountScreen(
    viewModel: MainViewModel,
    padding: PaddingValues,
    onClose: () -> Unit = {},
    onEditSpecies: (Id) -> Unit = {},
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val globalPointId by viewModel.activePointId.collectAsStateWithLifecycle()

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    if (ds.points.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("专注录入", style = MaterialTheme.typography.titleLarge)
            Text(
                "当前数据集没有采样点。请先在底部「采样点」页面新增至少 1 条采样点后再录入计数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

    var activePointId by remember(ds.id) { mutableStateOf(globalPointId ?: ds.points.firstOrNull()?.id ?: "") }
    if (ds.points.none { it.id == activePointId }) activePointId = ds.points.firstOrNull()?.id ?: ""

    LaunchedEffect(globalPointId, ds.points.size) {
        val pid = globalPointId
        if (pid != null && ds.points.any { it.id == pid } && pid != activePointId) {
            activePointId = pid
        }
    }
    LaunchedEffect(activePointId) {
        if (activePointId.isNotBlank()) viewModel.setActivePointId(activePointId)
    }

    var filter by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var editLocked by rememberSaveable(ds.id) { mutableStateOf(settings.fieldModeAccidentalTouchGuard) }

    val collapsedSaver = remember {
        listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        )
    }
    var collapsedGroups by rememberSaveable(stateSaver = collapsedSaver) { mutableStateOf(emptySet()) }
    fun isCollapsed(group: String): Boolean = group in collapsedGroups
    fun toggle(group: String) {
        collapsedGroups = if (group in collapsedGroups) collapsedGroups - group else collapsedGroups + group
    }

    val filteredSpecies = remember(ds.species, filter) {
        val q = filter.trim()
        if (q.isEmpty()) return@remember ds.species
        val qLower = q.lowercase()
        ds.species.filter { sp ->
            sp.nameCn.contains(q) || sp.nameLatin.lowercase().contains(qLower)
        }
    }

    val grouped = remember(filteredSpecies) {
        val buckets = LinkedHashMap<String, MutableList<Species>>()
        for (g in LVL1_ORDER) buckets[g] = mutableListOf()
        buckets["（未分类）"] = mutableListOf()
        for (s in filteredSpecies) {
            val lvl1 = normalizeLvl1Name(s.taxonomy.lvl1)
            val key = if (lvl1 in LVL1_ORDER) lvl1 else "（未分类）"
            buckets.getValue(key).add(s)
        }
        buckets
    }

    fun updateCount(speciesId: Id, pointId: Id, next: Int) {
        viewModel.updateCurrentDataset { cur ->
            cur.copy(
                species = cur.species.map { sp ->
                    if (sp.id != speciesId) sp
                    else sp.copy(countsByPointId = sp.countsByPointId + (pointId to clampNonNegativeInt(next)))
                },
            )
        }
    }

    var editId by remember { mutableStateOf<Id?>(null) }
    var editText by remember { mutableStateOf("") }
    val editTarget = ds.species.firstOrNull { it.id == editId }
    val pointStatsMap by remember(ds.species, ds.points) {
        derivedStateOf {
            val map = mutableMapOf<Id, Pair<Int, Int>>()
            for (p in ds.points) {
                map[p.id] = 0 to 0
            }
            for (sp in ds.species) {
                for ((pointId, count) in sp.countsByPointId) {
                    if (count <= 0) continue
                    val current = map[pointId] ?: (0 to 0)
                    map[pointId] = (current.first + 1) to (current.second + count)
                }
            }
            map
        }
    }
    val pointStats by remember(pointStatsMap, activePointId) {
        derivedStateOf { pointStatsMap[activePointId] ?: (0 to 0) }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
            Column(modifier = Modifier.weight(1f)) {
                Text("专注录入", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val label = ds.points.firstOrNull { it.id == activePointId }?.label.orEmpty().ifBlank { "未命名" }
                Text("当前采样点：$label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
            IconButton(onClick = { showSearch = true }) { Icon(imageVector = Icons.Outlined.Search, contentDescription = "搜索") }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), elevation = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (editLocked) "防误触：已锁定（点击解锁后才可修改计数）" else "防误触：已解锁",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { editLocked = !editLocked }) {
                    Text(if (editLocked) "解锁编辑" else "锁定编辑")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (p in ds.points) {
                val selected = p.id == activePointId
                val label = p.label.ifBlank { "未命名" }
                val stats = pointStatsMap[p.id] ?: (0 to 0)
                val statText = "物种 ${stats.first} · 总数 ${stats.second}"
                if (selected) {
                    Button(onClick = { activePointId = p.id }) {
                        val subColor = LocalContentColor.current.copy(alpha = 0.7f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                statText,
                                style = MaterialTheme.typography.labelSmall,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    TextButton(onClick = { activePointId = p.id }) {
                        val subColor = LocalContentColor.current.copy(alpha = 0.7f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                statText,
                                style = MaterialTheme.typography.labelSmall,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), elevation = 0.dp) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "当前点位统计：物种 ${pointStats.first} · 计数总和 ${pointStats.second}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "提示：仅统计当前点位计数 > 0 的物种。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            for ((groupLabel, list) in grouped) {
                if (list.isEmpty()) continue
                stickyHeader(key = "h-$groupLabel") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle(groupLabel) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$groupLabel（${list.size}）", style = MaterialTheme.typography.titleMedium)
                            Icon(
                                imageVector = if (isCollapsed(groupLabel)) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                contentDescription = if (isCollapsed(groupLabel)) "展开" else "收起",
                            )
                        }
                    }
                }

                if (isCollapsed(groupLabel)) continue

                items(list, key = { it.id }, contentType = { "row" }) { sp ->
                    val count = sp.countsByPointId[activePointId] ?: 0
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    sp.nameCn.ifBlank { "（未命名物种）" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (sp.nameLatin.isNotBlank()) {
                                    Text(
                                        sp.nameLatin,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
 
                            val size = 48.dp
                            FilledTonalIconButton(
                                onClick = { onEditSpecies(sp.id) },
                                modifier = Modifier.width(size).height(size),
                                enabled = !editLocked,
                            ) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = "编辑")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    performHaptic(context, settings, HapticKind.Click)
                                    updateCount(sp.id, activePointId, count - 1)
                                },
                                modifier = Modifier.width(size).height(size),
                                enabled = !editLocked,
                            ) {
                                Icon(imageVector = Icons.Filled.Remove, contentDescription = "减")
                            }

                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(size)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !editLocked) {
                                        editId = sp.id
                                        editText = count.toString()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    performHaptic(context, settings, HapticKind.Click)
                                    updateCount(sp.id, activePointId, count + 1)
                                },
                                modifier = Modifier.width(size).height(size),
                                enabled = !editLocked,
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "加")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("搜索/筛选") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("关键词") },
                        placeholder = { Text("中文名/拉丁名") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Text(
                        "提示：点数字可手动输入计数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("关闭") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        filter = ""
                        showSearch = false
                    },
                ) { Text("清空") }
            },
        )
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editId = null },
            title = { Text("手动输入计数") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(editTarget.nameCn.ifBlank { "（未命名物种）" }, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        singleLine = true,
                        label = { Text("计数") },
                        placeholder = { Text("例如：12") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = clampNonNegativeInt(editText.trim().toIntOrNull() ?: 0)
                        updateCount(editTarget.id, activePointId, n)
                        performHaptic(context, settings, HapticKind.Success)
                        editId = null
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editId = null }) { Text("取消") } },
        )
    }
}
}
