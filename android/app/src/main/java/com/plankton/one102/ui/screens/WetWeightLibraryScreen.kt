package com.plankton.one102.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_NAME
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.WetWeightsViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.ui.dialogs.TaxonomyQueryDialog
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import com.plankton.one102.ui.theme.GlassWhite
import kotlinx.coroutines.launch

@Composable
fun WetWeightLibraryScreen(mainViewModel: MainViewModel, viewModel: WetWeightsViewModel, padding: PaddingValues) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val wetWeightRepo = (context.applicationContext as PlanktonApplication).wetWeightRepository
    val libraries by wetWeightRepo.observeLibraries().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    var q by remember { mutableStateOf("") }
    var onlyCustom by remember { mutableStateOf(false) }
    var isNew by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<WetWeightsViewModel.WetWeightItem?>(null) }
    var draftNameCn by remember { mutableStateOf("") }
    var draftLatin by remember { mutableStateOf("") }
    var draftWetWeight by remember { mutableStateOf("") }
    var draftGroup by remember { mutableStateOf("") }
    var draftSub by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var libraryMenuOpen by remember { mutableStateOf(false) }
    var libraryCreateOpen by remember { mutableStateOf(false) }
    var libraryNameDraft by remember { mutableStateOf("") }
    var libraryError by remember { mutableStateOf<String?>(null) }

    val glassPrefs = LocalGlassPrefs.current
    val dialogColor = if (glassPrefs.enabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    val activeLibraryName = libraries.firstOrNull {
        it.id == settings.activeWetWeightLibraryId.trim().ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID }
    }?.name ?: DEFAULT_WET_WEIGHT_LIBRARY_NAME

    val filtered = remember(items, q, onlyCustom) {
        val query = q.trim()
        val base = if (onlyCustom) items.filter { it.sources.custom } else items
        if (query.isEmpty()) return@remember base
        val qLower = query.lowercase()
        base.filter { item ->
            val it = item.entry
            it.nameCn.contains(query) ||
                (it.nameLatin ?: "").lowercase().contains(qLower) ||
                (it.taxonomy.group ?: "").contains(query) ||
                (it.taxonomy.sub ?: "").contains(query)
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
            Text("平均湿重库", style = MaterialTheme.typography.titleLarge)
            Text(
                "展示内置湿重库 + 你的自定义覆盖；在「数据库」页或这里修改都会同步生效。编辑会写入“自定义覆盖”，不会直接修改内置数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前湿重库：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Box {
                    OutlinedButton(onClick = { libraryMenuOpen = true }) { Text(activeLibraryName) }
                    DropdownMenu(expanded = libraryMenuOpen, onDismissRequest = { libraryMenuOpen = false }) {
                        libraries.forEach { lib ->
                            DropdownMenuItem(
                                text = { Text(lib.name) },
                                onClick = {
                                    libraryMenuOpen = false
                                    mainViewModel.saveSettings(settings.copy(activeWetWeightLibraryId = lib.id))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("＋ 新建湿重库") },
                            onClick = {
                                libraryMenuOpen = false
                                libraryCreateOpen = true
                                libraryNameDraft = ""
                                libraryError = null
                            },
                        )
                    }
                }
            }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("搜索") },
                placeholder = { Text("中文名/拉丁名/分类…") },
            )
            Button(
                onClick = {
                    error = null
                    isNew = true
                    editor = null
                    draftNameCn = ""
                    draftLatin = ""
                    draftWetWeight = ""
                    draftGroup = ""
                    draftSub = ""
                },
            ) { Text("新增") }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (onlyCustom) "仅看自定义覆盖" else "显示全部（内置 + 自定义覆盖）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Switch(checked = onlyCustom, onCheckedChange = { onlyCustom = it })
        }

        if (filtered.isEmpty()) {
            Text("暂无条目。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.entry.nameCn }) { item ->
                    val e = item.entry
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${e.nameCn}${if (item.sources.custom) "（自定义）" else ""} · ${e.wetWeightMg} mg/个",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!e.nameLatin.isNullOrBlank()) {
                                Text(
                                    e.nameLatin.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!e.taxonomy.group.isNullOrBlank() || !e.taxonomy.sub.isNullOrBlank()) {
                                Text(
                                    listOfNotNull(
                                        e.taxonomy.group?.takeIf { it.isNotBlank() },
                                        e.taxonomy.sub?.takeIf { it.isNotBlank() }?.let { " / $it" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        error = null
                                        isNew = false
                                        editor = item
                                        draftNameCn = e.nameCn
                                        draftLatin = e.nameLatin ?: ""
                                        draftWetWeight = e.wetWeightMg.toString()
                                        draftGroup = e.taxonomy.group ?: ""
                                        draftSub = e.taxonomy.sub ?: ""
                                    },
                                ) { Text("编辑") }
                                if (item.sources.custom) {
                                    OutlinedButton(
                                        onClick = { viewModel.deleteCustom(e.nameCn) },
                                    ) { Text(if (item.sources.builtin) "删除覆盖" else "删除") }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (isNew || editor != null) {
        var taxQuery by remember(editor?.entry?.nameCn, isNew) { mutableStateOf(false) }
        var wetQuery by remember(editor?.entry?.nameCn, isNew) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                editor = null
                isNew = false
            },
            title = { Text(if (isNew) "新增条目" else "编辑条目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = draftNameCn,
                        onValueChange = { draftNameCn = it },
                        label = { Text("中文名（唯一）") },
                        singleLine = true,
                        enabled = isNew,
                    )
                    OutlinedTextField(
                        value = draftLatin,
                        onValueChange = { draftLatin = it },
                        label = { Text("拉丁名（可空）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draftWetWeight,
                        onValueChange = { draftWetWeight = it },
                        label = { Text("平均湿重（mg/个）") },
                        singleLine = true,
                        placeholder = { Text("例如：0.0005") },
                    )
                    if (!settings.aiUiHidden) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { taxQuery = true }, modifier = Modifier.weight(1f)) { Text("双 API 查分类") }
                            OutlinedButton(onClick = { wetQuery = true }, modifier = Modifier.weight(1f)) { Text("双 API 查湿重") }
                        }
                    }
                    OutlinedTextField(
                        value = draftGroup,
                        onValueChange = { draftGroup = it },
                        label = { Text("分类（大类，可空）") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draftSub,
                        onValueChange = { draftSub = it },
                        label = { Text("分类（亚类，可空）") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        error = null
                        val nameCn = draftNameCn.trim()
                        if (nameCn.isEmpty()) {
                            error = "中文名不能为空。"
                            return@TextButton
                        }
                        if (isNew && items.any { it.entry.nameCn == nameCn }) {
                            error = "已存在同名条目：$nameCn"
                            return@TextButton
                        }
                        val wetWeight = draftWetWeight.trim().toDoubleOrNull()
                        if (wetWeight == null || !wetWeight.isFinite() || wetWeight <= 0) {
                            error = "平均湿重需为大于 0 的数值（单位 mg/个）。"
                            return@TextButton
                        }
                        viewModel.upsertCustom(
                            WetWeightEntry(
                                nameCn = nameCn,
                                nameLatin = draftLatin.trim().ifBlank { null },
                                wetWeightMg = wetWeight,
                                taxonomy = WetWeightTaxonomy(
                                    group = draftGroup.trim().ifBlank { null },
                                    sub = draftSub.trim().ifBlank { null },
                                ),
                            ),
                        )
                        editor = null
                        isNew = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editor = null
                        isNew = false
                    },
                ) { Text("取消") }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )

        if (taxQuery && !settings.aiUiHidden) {
            TaxonomyQueryDialog(
                settings = settings,
                nameCn = draftNameCn.trim(),
                nameLatin = draftLatin.trim().ifBlank { null },
                onClose = { taxQuery = false },
                onApply = { taxonomy ->
                    draftGroup = normalizeLvl1Name(taxonomy.lvl1)
                    draftSub = taxonomy.lvl4.trim()
                    taxQuery = false
                },
            )
        }

        if (wetQuery && !settings.aiUiHidden) {
            WetWeightQueryDialog(
                settings = settings,
                nameCn = draftNameCn.trim(),
                nameLatin = draftLatin.trim().ifBlank { null },
                onClose = { wetQuery = false },
                onApply = { mg ->
                    draftWetWeight = mg.toString()
                    wetQuery = false
                },
                onSaveToLibrary = { mg ->
                    val key = draftNameCn.trim()
                    if (key.isBlank()) return@WetWeightQueryDialog
                    viewModel.upsertCustom(
                        WetWeightEntry(
                            nameCn = key,
                            nameLatin = draftLatin.trim().ifBlank { null },
                            wetWeightMg = mg,
                            taxonomy = WetWeightTaxonomy(
                                group = normalizeLvl1Name(draftGroup).takeIf { it.isNotBlank() },
                                sub = draftSub.trim().takeIf { it.isNotBlank() },
                            ),
                        ),
                    )
                },
            )
        }
    }

    if (libraryCreateOpen) {
        AlertDialog(
            onDismissRequest = { libraryCreateOpen = false },
            title = { Text("新建湿重库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (libraryError != null) {
                        Text(libraryError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = libraryNameDraft,
                        onValueChange = { libraryNameDraft = it },
                        singleLine = true,
                        label = { Text("库名称") },
                        placeholder = { Text("例如：推荐湿重库") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = libraryNameDraft.trim()
                        if (name.isBlank()) {
                            libraryError = "库名称不能为空。"
                            return@TextButton
                        }
                        scope.launch {
                            val created = runCatching { wetWeightRepo.createLibrary(name) }
                            created.onSuccess { lib ->
                                mainViewModel.saveSettings(settings.copy(activeWetWeightLibraryId = lib.id))
                                libraryCreateOpen = false
                            }.onFailure {
                                libraryError = it.message ?: "新建失败"
                            }
                        }
                    },
                ) { Text("创建并切换") }
            },
            dismissButton = { TextButton(onClick = { libraryCreateOpen = false }) { Text("取消") } },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}
}
