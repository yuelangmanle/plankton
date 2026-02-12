package com.plankton.one102.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.domain.MergeCountsMode
import com.plankton.one102.domain.mergeDuplicateSpeciesByName
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.bestCanonicalName
import com.plankton.one102.ui.DatabaseViewModel
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.dialogs.TaxonomyQueryDialog
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import kotlinx.coroutines.launch

private fun scoreSuggestion(item: SpeciesDbItem, q: String): Int {
    val query = q.trim()
    if (query.isBlank()) return 0
    val qLower = query.lowercase()

    var s = 0
    if (item.nameCn == query) s += 1000
    if (item.nameCn.contains(query)) s += 250
    if ((item.nameLatin ?: "").lowercase().contains(qLower)) s += 180

    val t = item.taxonomy
    if (t != null) {
        val fields = listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5)
        if (fields.any { it.contains(query) }) s += 80
        if (fields.any { it.lowercase().contains(qLower) }) s += 60
    }

    if (item.wetWeightMg != null) s += 5
    return s
}

private fun collectTaxonomyCounts(items: List<SpeciesDbItem>): List<Map<String, Int>> {
    val counts = List(5) { linkedMapOf<String, Int>() }
    for (item in items) {
        val t = item.taxonomy ?: continue
        val values = listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5)
        for (i in values.indices) {
            val raw = values[i].trim()
            if (raw.isBlank()) continue
            val key = if (i == 0) normalizeLvl1Name(raw) else raw
            if (key.isBlank()) continue
            val map = counts[i]
            map[key] = (map[key] ?: 0) + 1
        }
    }
    return counts
}

private fun suggestTaxonomy(values: Map<String, Int>, query: String, limit: Int = 6): List<String> {
    if (values.isEmpty()) return emptyList()
    val q = query.trim()
    val qLower = q.lowercase()
    val filtered = if (q.isBlank()) {
        values.entries
    } else {
        values.entries.filter { it.key.contains(q) || it.key.lowercase().contains(qLower) }
    }
    return filtered
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .distinct()
        .take(limit)
}

private fun hasTaxonomy(t: Taxonomy): Boolean {
    return listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).any { it.isNotBlank() }
}

@Composable
fun SpeciesEditScreen(
    viewModel: MainViewModel,
    databaseViewModel: DatabaseViewModel,
    padding: PaddingValues,
    speciesId: Id,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as PlanktonApplication
    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository

    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dbItems by databaseViewModel.items.collectAsStateWithLifecycle()

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    val sp = ds.species.firstOrNull { it.id == speciesId }
    if (sp == null) {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("未找到该物种。", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onClose) { Text("返回") }
            }
        }
        return
    }

    var nameCn by remember(speciesId) { mutableStateOf(sp.nameCn) }
    var nameLatin by remember(speciesId) { mutableStateOf(sp.nameLatin) }
    var wetText by remember(speciesId) { mutableStateOf(sp.avgWetWeightMg?.toString() ?: "") }
    var lvl1 by remember(speciesId) { mutableStateOf(sp.taxonomy.lvl1) }
    var lvl2 by remember(speciesId) { mutableStateOf(sp.taxonomy.lvl2) }
    var lvl3 by remember(speciesId) { mutableStateOf(sp.taxonomy.lvl3) }
    var lvl4 by remember(speciesId) { mutableStateOf(sp.taxonomy.lvl4) }
    var lvl5 by remember(speciesId) { mutableStateOf(sp.taxonomy.lvl5) }

    val taxonomyCounts = remember(dbItems) { collectTaxonomyCounts(dbItems) }
    fun suggestForLevel(level: Int, query: String, limit: Int = 6): List<String> {
        if (level == 0) {
            val base = listOf("原生动物", "轮虫类", "枝角类", "桡足类")
            val q = query.trim()
            val filtered = if (q.isBlank()) base else base.filter { it.contains(q) }
            return filtered.take(limit)
        }
        val values = taxonomyCounts.getOrNull(level) ?: emptyMap()
        return suggestTaxonomy(values, query, limit)
    }

    @Composable
    fun TaxonomySuggestionRow(values: List<String>, onPick: (String) -> Unit) {
        if (values.isEmpty()) return
        Text(
            "关键词联想：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (v in values) {
                OutlinedButton(onClick = { onPick(v) }) { Text(v) }
            }
        }
    }

    var taxQuery by remember { mutableStateOf(false) }
    var wetQuery by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(settings.aiUiHidden) {
        if (settings.aiUiHidden) {
            taxQuery = false
            wetQuery = false
        }
    }

    fun applyToDataset(
        nameCnFixed: String,
        latinFixed: String,
        wet: Double?,
        taxonomy: Taxonomy,
    ) {
        viewModel.updateCurrentDataset { cur ->
            val updated = cur.copy(
                species = cur.species.map { s ->
                    if (s.id != speciesId) s
                    else s.copy(
                        nameCn = nameCnFixed,
                        nameLatin = latinFixed,
                        avgWetWeightMg = wet,
                        taxonomy = taxonomy,
                    )
                },
            )
            mergeDuplicateSpeciesByName(updated, MergeCountsMode.Max).dataset
        }
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("编辑物种", style = MaterialTheme.typography.titleLarge)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameCn,
                    onValueChange = { nameCn = it },
                    label = { Text("物种中文名") },
                    singleLine = true,
                )

                val suggestQuery = nameCn.trim().ifBlank { nameLatin.trim() }
                val suggestions = remember(suggestQuery, dbItems) {
                    if (suggestQuery.isBlank()) return@remember emptyList()
                    dbItems.asSequence()
                        .mapNotNull { item ->
                            val score = scoreSuggestion(item, suggestQuery)
                            if (score > 0) score to item else null
                        }
                        .sortedWith(compareByDescending<Pair<Int, SpeciesDbItem>> { it.first }.thenBy { it.second.nameCn })
                        .take(8)
                        .map { it.second }
                        .toList()
                }

                if (suggestions.isNotEmpty()) {
                    Text(
                        "关键词联想（点一下自动补齐缺失项）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in suggestions) {
                            OutlinedButton(
                                onClick = {
                                    nameCn = s.nameCn
                                    if (nameLatin.isBlank() && !s.nameLatin.isNullOrBlank()) nameLatin = s.nameLatin.orEmpty()
                                    if (wetText.isBlank() && s.wetWeightMg != null) wetText = s.wetWeightMg.toString()
                                    val t = s.taxonomy
                                    if (t != null) {
                                        if (lvl1.isBlank()) lvl1 = t.lvl1
                                        if (lvl2.isBlank()) lvl2 = t.lvl2
                                        if (lvl3.isBlank()) lvl3 = t.lvl3
                                        if (lvl4.isBlank()) lvl4 = t.lvl4
                                        if (lvl5.isBlank()) lvl5 = t.lvl5
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.nameCn, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    val sub = buildString {
                                        val latin = s.nameLatin?.trim().orEmpty()
                                        val t = s.taxonomy
                                        val tax = if (t != null) listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).filter { it.isNotBlank() }.joinToString(" · ") else ""
                                        if (latin.isNotBlank()) append(latin)
                                        if (latin.isNotBlank() && tax.isNotBlank()) append(" · ")
                                        if (tax.isNotBlank()) append(tax)
                                        if (s.wetWeightMg != null) {
                                            if (latin.isNotBlank() || tax.isNotBlank()) append(" · ")
                                            append("湿重 ")
                                            append(s.wetWeightMg)
                                            append(" mg/个")
                                        }
                                    }
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = nameLatin,
                    onValueChange = { nameLatin = it },
                    label = { Text("拉丁名（可空）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = wetText,
                    onValueChange = { wetText = it },
                    label = { Text("平均湿重（mg/个，可空）") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("例如：0.0005") },
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("分类（内置分类库）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "lvl1 必须为四大类之一：原生动物 / 轮虫类 / 枝角类 / 桡足类（可留空）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同步保存到本机库", modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.speciesEditWriteToDb,
                        onCheckedChange = { enabled ->
                            viewModel.saveSettings(settings.copy(speciesEditWriteToDb = enabled))
                        },
                    )
                }
                Text(
                    if (settings.speciesEditWriteToDb) {
                        "已开启：保存时分类/湿重会同步写入自定义库。"
                    } else {
                        "默认关闭：仅更新当前数据集，不写入自定义库。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                OutlinedTextField(value = lvl1, onValueChange = { lvl1 = it }, singleLine = true, label = { Text("大类 lvl1") })
                val lvl1Suggestions = remember(lvl1, taxonomyCounts) { suggestForLevel(0, lvl1) }
                TaxonomySuggestionRow(lvl1Suggestions) { lvl1 = normalizeLvl1Name(it) }

                OutlinedTextField(value = lvl2, onValueChange = { lvl2 = it }, singleLine = true, label = { Text("纲 lvl2") })
                val lvl2Suggestions = remember(lvl2, taxonomyCounts) { if (lvl2.isBlank()) emptyList() else suggestForLevel(1, lvl2) }
                TaxonomySuggestionRow(lvl2Suggestions) { lvl2 = it }

                OutlinedTextField(value = lvl3, onValueChange = { lvl3 = it }, singleLine = true, label = { Text("目 lvl3") })
                val lvl3Suggestions = remember(lvl3, taxonomyCounts) { if (lvl3.isBlank()) emptyList() else suggestForLevel(2, lvl3) }
                TaxonomySuggestionRow(lvl3Suggestions) { lvl3 = it }

                OutlinedTextField(value = lvl4, onValueChange = { lvl4 = it }, singleLine = true, label = { Text("科 lvl4") })
                val lvl4Suggestions = remember(lvl4, taxonomyCounts) { if (lvl4.isBlank()) emptyList() else suggestForLevel(3, lvl4) }
                TaxonomySuggestionRow(lvl4Suggestions) { lvl4 = it }

                OutlinedTextField(value = lvl5, onValueChange = { lvl5 = it }, singleLine = true, label = { Text("属 lvl5") })
                val lvl5Suggestions = remember(lvl5, taxonomyCounts) { if (lvl5.isBlank()) emptyList() else suggestForLevel(4, lvl5) }
                TaxonomySuggestionRow(lvl5Suggestions) { lvl5 = it }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val key = nameCn.trim()
                                if (key.isBlank()) return@launch

                                val aliasMap = runCatching {
                                    aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
                                }.getOrElse { emptyMap() }
                                val canonical = aliasMap[key] ?: key
                                val candidates = buildSet {
                                    addAll(taxonomyRepo.getBuiltinEntryMap().keys)
                                    addAll(taxonomyOverrideRepo.getCustomEntries().mapNotNull { it.nameCn.trim().ifBlank { null } })
                                }
                                val best = if (!candidates.contains(canonical)) bestCanonicalName(canonical, candidates) else null
                                val resolved = when {
                                    candidates.contains(canonical) -> canonical
                                    best != null && best.score >= 0.9 -> best.canonical
                                    else -> canonical
                                }

                                val custom = taxonomyOverrideRepo.findCustomByNameCn(resolved)
                                val builtin = if (custom == null) taxonomyRepo.findEntryByNameCn(resolved) else null
                                val t = custom?.taxonomy ?: builtin?.taxonomy
                                if (t == null) {
                                    val hint = best?.takeIf { it.score >= 0.6 }?.let { "（可能是 ${it.canonical}）" }.orEmpty()
                                    Toast.makeText(context, "内置分类库未找到该物种分类$hint", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                lvl1 = lvl1.ifBlank { t.lvl1 }
                                lvl2 = lvl2.ifBlank { t.lvl2 }
                                lvl3 = lvl3.ifBlank { t.lvl3 }
                                lvl4 = lvl4.ifBlank { t.lvl4 }
                                lvl5 = lvl5.ifBlank { t.lvl5 }
                                if (nameLatin.isBlank() && !custom?.nameLatin.isNullOrBlank()) {
                                    nameLatin = custom?.nameLatin.orEmpty()
                                } else if (nameLatin.isBlank() && !builtin?.nameLatin.isNullOrBlank()) {
                                    nameLatin = builtin?.nameLatin.orEmpty()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("从分类库补齐分类") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val key = nameCn.trim()
                                if (key.isBlank()) return@launch
                                val aliasMap = try {
                                    aliasRepo.getAll().associate { it.alias.trim() to it.canonicalNameCn.trim() }
                                } catch (_: Exception) {
                                    emptyMap()
                                }
                                val canonical = aliasMap[key].orEmpty()
                                val entry = wetWeightRepo.findByNameCn(key) ?: run {
                                    if (canonical.isNotBlank() && canonical != key) wetWeightRepo.findByNameCn(canonical) else null
                                }
                                if (entry == null) {
                                    Toast.makeText(context, "湿重库未查到该物种", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                if (wetText.isBlank()) wetText = entry.wetWeightMg.toString()
                                if (nameLatin.isBlank() && !entry.nameLatin.isNullOrBlank()) nameLatin = entry.nameLatin.orEmpty()
                                val g = normalizeLvl1Name(entry.taxonomy.group.orEmpty())
                                if (lvl1.isBlank() && g.isNotBlank()) lvl1 = g
                                if (lvl4.isBlank() && entry.taxonomy.sub.orEmpty().isNotBlank()) lvl4 = entry.taxonomy.sub.orEmpty()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("从湿重库补齐湿重") }
                }
                if (!settings.aiUiHidden) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { taxQuery = true }, modifier = Modifier.weight(1f)) { Text("双 API 查分类") }
                        OutlinedButton(onClick = { wetQuery = true }, modifier = Modifier.weight(1f)) { Text("双 API 查湿重") }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val key = nameCn.trim()
                        val wet = wetText.trim().toDoubleOrNull()
                        if (key.isBlank() || wet == null || !wet.isFinite() || wet <= 0) {
                            Toast.makeText(context, "请先填写有效的中文名与湿重", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        scope.launch {
                            wetWeightRepo.upsertCustom(
                                WetWeightEntry(
                                    nameCn = key,
                                    nameLatin = nameLatin.trim().ifBlank { null },
                                    wetWeightMg = wet,
                                    taxonomy = WetWeightTaxonomy(
                                        group = normalizeLvl1Name(lvl1).takeIf { it.isNotBlank() },
                                        sub = lvl4.trim().takeIf { it.isNotBlank() },
                                    ),
                                ),
                            )
                            Toast.makeText(context, "已保存到湿重库", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存到湿重库") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("删除物种") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val key = nameCn.trim()
                    if (key.isBlank()) {
                        Toast.makeText(context, "物种中文名不能为空", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val wet = wetText.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    if (wet != null && (!wet.isFinite() || wet <= 0)) {
                        Toast.makeText(context, "湿重需为大于 0 的数值，或留空", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val t = Taxonomy(
                        lvl1 = normalizeLvl1Name(lvl1),
                        lvl2 = lvl2.trim(),
                        lvl3 = lvl3.trim(),
                        lvl4 = lvl4.trim(),
                        lvl5 = lvl5.trim(),
                    )
                    applyToDataset(key, nameLatin.trim(), wet, t)
                    if (settings.speciesEditWriteToDb) {
                        val nameLatinFixed = nameLatin.trim().ifBlank { null }
                        scope.launch {
                            if (wet != null && wet.isFinite() && wet > 0) {
                                wetWeightRepo.upsertCustom(
                                    WetWeightEntry(
                                        nameCn = key,
                                        nameLatin = nameLatinFixed,
                                        wetWeightMg = wet,
                                        taxonomy = WetWeightTaxonomy(
                                            group = normalizeLvl1Name(lvl1).takeIf { it.isNotBlank() },
                                            sub = lvl4.trim().takeIf { it.isNotBlank() },
                                        ),
                                    ),
                                )
                            }
                            if (hasTaxonomy(t)) {
                                taxonomyOverrideRepo.upsertCustom(
                                    TaxonomyRecord(nameCn = key, nameLatin = nameLatinFixed, taxonomy = t),
                                )
                            }
                        }
                    }
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存并返回") }
        }
    }
}

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除物种") },
            text = { Text("将从当前数据集中删除该物种。", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.updateCurrentDataset { cur -> cur.copy(species = cur.species.filter { it.id != speciesId }) }
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    if (taxQuery && !settings.aiUiHidden) {
        TaxonomyQueryDialog(
            settings = settings,
            nameCn = nameCn.trim(),
            nameLatin = nameLatin.trim().ifBlank { null },
            onClose = { taxQuery = false },
            onApply = { t ->
                lvl1 = t.lvl1
                lvl2 = t.lvl2
                lvl3 = t.lvl3
                lvl4 = t.lvl4
                lvl5 = t.lvl5
                taxQuery = false
            },
        )
    }

    if (wetQuery && !settings.aiUiHidden) {
        WetWeightQueryDialog(
            settings = settings,
            nameCn = nameCn.trim(),
            nameLatin = nameLatin.trim().ifBlank { null },
            onClose = { wetQuery = false },
            onApply = { mg ->
                wetText = mg.toString()
                wetQuery = false
            },
            onSaveToLibrary = { mg ->
                val key = nameCn.trim()
                if (key.isBlank()) return@WetWeightQueryDialog
                scope.launch {
                    wetWeightRepo.upsertCustom(
                        WetWeightEntry(
                            nameCn = key,
                            nameLatin = nameLatin.trim().ifBlank { null },
                            wetWeightMg = mg,
                            taxonomy = WetWeightTaxonomy(
                                group = normalizeLvl1Name(lvl1).takeIf { it.isNotBlank() },
                                sub = lvl4.trim().takeIf { it.isNotBlank() },
                            ),
                        ),
                    )
                }
            },
        )
    }
}
