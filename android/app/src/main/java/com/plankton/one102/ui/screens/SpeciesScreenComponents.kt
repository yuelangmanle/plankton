package com.plankton.one102.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.LVL1_ORDER
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.ui.ImageImportMode
import com.plankton.one102.ui.ImageImportResult
import com.plankton.one102.ui.ImageImportSource
import com.plankton.one102.ui.components.GlassCard
import kotlinx.coroutines.delay

@Composable
internal fun SpeciesPointSelectorCard(
    activeLabel: String,
    pointExpanded: Boolean,
    speciesCount: Int,
    totalCount: Int,
    points: List<Pair<Id, String>>,
    activePointId: Id,
    onExpandedChange: (Boolean) -> Unit,
    onSelectPoint: (Id) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!pointExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("当前采样点：${activeLabel.ifBlank { "未命名" }}", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (pointExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (pointExpanded) "收起" else "展开",
                )
            }

            Text(
                "本点位：物种 $speciesCount 个 · 总计 $totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            if (!pointExpanded) {
                Text(
                    "提示：点这里展开切换点位；下方列表用于该点位计数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((id, label) in points) {
                        if (id == activePointId) {
                            Button(onClick = { onSelectPoint(id) }) { Text("✓$label") }
                        } else {
                            OutlinedButton(onClick = { onSelectPoint(id) }) { Text(label) }
                        }
                    }
                }
                Text(
                    "建议按采样点逐个录入计数：先选点位，再对每个物种点 +/-.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun SpeciesHeaderRow(
    onOpenFocus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("物种与计数", style = MaterialTheme.typography.titleLarge)
        androidx.compose.material3.TextButton(onClick = onOpenFocus) { Text("专注录入") }
    }
}

@Composable
internal fun SpeciesWetWeightLibraryRow(
    activeLibraryName: String,
    menuOpen: Boolean,
    libraries: List<Pair<String, String>>,
    onMenuOpenChange: (Boolean) -> Unit,
    onSelectLibrary: (String) -> Unit,
    onCreateLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "湿重库：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Box {
            OutlinedButton(onClick = { onMenuOpenChange(true) }) { Text(activeLibraryName) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                libraries.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onMenuOpenChange(false)
                            onSelectLibrary(id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("＋ 新建湿重库") },
                    onClick = {
                        onMenuOpenChange(false)
                        onCreateLibrary()
                    },
                )
            }
        }
    }
}

@Composable
internal fun SpeciesBulkActionsCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClearPoint: () -> Unit,
    onClearAll: () -> Unit,
    onMerge: () -> Unit,
    onCreateTemplate: () -> Unit,
    onBatchInput: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }

            if (!expanded) {
                Text(
                    "展开后可清空计数、合并同名物种，或用当前物种清单新建数据集（计数清零）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClearPoint, modifier = Modifier.weight(1f)) { Text("清空当前点计数") }
                    OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f)) { Text("清空全部计数") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onMerge, modifier = Modifier.weight(1f)) { Text("合并同名物种") }
                    OutlinedButton(onClick = onCreateTemplate, modifier = Modifier.weight(1f)) { Text("新建数据集（清零）") }
                }
                OutlinedButton(onClick = onBatchInput, modifier = Modifier.fillMaxWidth()) { Text("批量录入（Excel / 语音）") }
            }
        }
    }
}

@Composable
internal fun SpeciesAutoMatchCard(
    expanded: Boolean,
    hasLastAutoMatch: Boolean,
    autoMatchWriteToDb: Boolean,
    speciesEditWriteToDb: Boolean,
    autoMatchBusy: Boolean,
    autoMatchProgress: String,
    autoMatchError: String?,
    onExpandedChange: (Boolean) -> Unit,
    onAutoMatchWriteToDbChange: (Boolean) -> Unit,
    onSpeciesEditWriteToDbChange: (Boolean) -> Unit,
    onRunAutoMatchTaxonomy: () -> Unit,
    onRunAutoMatchWetWeight: () -> Unit,
    onClearLastAutoMatch: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("一键补齐（分类 / 湿重）", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }

            if (!expanded) {
                Text(
                    "点击展开后可分别补齐“分类”或“湿重”（先查本机库，缺失再调 API）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                if (hasLastAutoMatch) {
                    Text(
                        "提示：存在“本次匹配写入”，可展开后清空（不影响本机数据库）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            } else {
                Text(
                    "会先尝试从本机数据库（内置分类/湿重库 + 自定义覆盖）补齐；仍缺失时再调用 API。只会补齐空白项，不会覆盖你已填写的数据。完成后可一键清空本次匹配写入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("写入本机库", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.Switch(
                        checked = autoMatchWriteToDb,
                        onCheckedChange = onAutoMatchWriteToDbChange,
                    )
                }
                Text(
                    if (autoMatchWriteToDb) "已开启：AI 补齐到的分类/湿重会写入自定义库（清空本次匹配不会回滚）。" else "默认关闭：补齐结果只写入当前数据集（不会自动写入本机库）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("编辑写入本机库", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.Switch(
                        checked = speciesEditWriteToDb,
                        onCheckedChange = onSpeciesEditWriteToDbChange,
                    )
                }
                Text(
                    if (speciesEditWriteToDb) "已开启：手动编辑分类/湿重会写入自定义库。" else "默认关闭：手动编辑仅更新当前数据集。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !autoMatchBusy,
                        onClick = onRunAutoMatchTaxonomy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (autoMatchBusy) {
                            androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("匹配中…")
                        } else {
                            Text("补齐分类")
                        }
                    }
                    Button(
                        enabled = !autoMatchBusy,
                        onClick = onRunAutoMatchWetWeight,
                        modifier = Modifier.weight(1f),
                    ) { Text("补齐湿重") }
                }
                OutlinedButton(
                    enabled = !autoMatchBusy && hasLastAutoMatch,
                    onClick = onClearLastAutoMatch,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("清空本次匹配") }

                if (autoMatchProgress.isNotBlank()) {
                    Text(autoMatchProgress, style = MaterialTheme.typography.bodySmall)
                }
                if (autoMatchError != null) {
                    Text(
                        "提示：$autoMatchError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpeciesImageImportCard(
    expanded: Boolean,
    imageCount: Int,
    busy: Boolean,
    message: String?,
    error: String?,
    useApi1: Boolean,
    useApi2: Boolean,
    useImageApi: Boolean,
    api1Label: String,
    api2Label: String,
    apiImgLabel: String,
    api1Enabled: Boolean,
    api2Enabled: Boolean,
    apiImgEnabled: Boolean,
    api1Status: String?,
    api2Status: String?,
    apiImgStatus: String?,
    mode: ImageImportMode,
    overwriteExisting: Boolean,
    hasApi1Result: Boolean,
    hasApi2Result: Boolean,
    hasApiImageResult: Boolean,
    selectedSource: ImageImportSource,
    preview: ImageImportResult?,
    onExpandedChange: (Boolean) -> Unit,
    onToggleUseApi1: (Boolean) -> Unit,
    onToggleUseApi2: (Boolean) -> Unit,
    onToggleUseImageApi: (Boolean) -> Unit,
    onPickImages: () -> Unit,
    onTakePhoto: () -> Unit,
    onClearImages: () -> Unit,
    onSelectMode: (ImageImportMode) -> Unit,
    onOverwriteExistingChange: (Boolean) -> Unit,
    onRunImageImport: () -> Unit,
    onSelectSource: (ImageImportSource) -> Unit,
    onApplyPreview: (ImageImportResult) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("图片识别导入（AI）", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                )
            }

            if (!expanded) {
                Text(
                    "批量识别纸面记录（多图/拍照），识别后可手动增减。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                if (imageCount > 0) {
                    Text("已选择 $imageCount 张图片", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    "支持同一张纸多个点位；可选多张图片批量识别。识别后会对物种名做模糊校正，无法确认会提示你核对。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = useApi1 && api1Enabled,
                            onCheckedChange = onToggleUseApi1,
                            enabled = api1Enabled && !busy,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                api1Label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (api1Enabled) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                },
                            )
                            if (!api1Enabled && api1Status != null) {
                                Text(
                                    api1Status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = useApi2 && api2Enabled,
                            onCheckedChange = onToggleUseApi2,
                            enabled = api2Enabled && !busy,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                api2Label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (api2Enabled) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                },
                            )
                            if (!api2Enabled && api2Status != null) {
                                Text(
                                    api2Status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = useImageApi && apiImgEnabled,
                            onCheckedChange = onToggleUseImageApi,
                            enabled = apiImgEnabled && !busy,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                apiImgLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (apiImgEnabled) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                },
                            )
                            if (!apiImgEnabled && apiImgStatus != null) {
                                Text(
                                    apiImgStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = !busy, onClick = onPickImages) { Text("选择图片") }
                    OutlinedButton(enabled = !busy, onClick = onTakePhoto) { Text("拍照") }
                    if (imageCount > 0) {
                        OutlinedButton(enabled = !busy, onClick = onClearImages) { Text("清空") }
                    }
                }

                Text("已选图片：$imageCount 张", style = MaterialTheme.typography.bodySmall)

                Text("导入方式", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val appendSelected = mode == ImageImportMode.Append
                    OutlinedButton(onClick = { onSelectMode(ImageImportMode.Append) }) {
                        Text(if (appendSelected) "✓ 追加到当前" else "追加到当前")
                    }
                    val newSelected = mode == ImageImportMode.NewDataset
                    OutlinedButton(onClick = { onSelectMode(ImageImportMode.NewDataset) }) {
                        Text(if (newSelected) "✓ 新建数据集" else "新建数据集")
                    }
                }

                val overwriteEnabled = mode == ImageImportMode.Append
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Switch(
                        checked = overwriteExisting,
                        onCheckedChange = onOverwriteExistingChange,
                        enabled = overwriteEnabled && !busy,
                    )
                    Text(
                        if (overwriteEnabled) "重复点位/物种计数覆盖更新（批次导入）" else "覆盖更新仅对“追加到当前”生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overwriteEnabled) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        },
                    )
                }

                Text(
                    "同一张图片内同名点位/物种会累加；不同图片同名点位/物种取最大值。导入后可在物种/采样点页面手动调整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Button(
                    enabled = !busy,
                    onClick = onRunImageImport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        Text("识别中…")
                    } else {
                        Text("开始识别")
                    }
                }

                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                if (hasApi1Result || hasApi2Result || hasApiImageResult) {
                    Text("使用结果来源：", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasApi1Result) {
                            val sel = selectedSource == ImageImportSource.Api1
                            OutlinedButton(onClick = { onSelectSource(ImageImportSource.Api1) }) {
                                Text(if (sel) "✓$api1Label" else api1Label)
                            }
                        }
                        if (hasApi2Result) {
                            val sel = selectedSource == ImageImportSource.Api2
                            OutlinedButton(onClick = { onSelectSource(ImageImportSource.Api2) }) {
                                Text(if (sel) "✓$api2Label" else api2Label)
                            }
                        }
                        if (hasApiImageResult) {
                            val sel = selectedSource == ImageImportSource.ImageApi
                            OutlinedButton(onClick = { onSelectSource(ImageImportSource.ImageApi) }) {
                                Text(if (sel) "✓$apiImgLabel" else apiImgLabel)
                            }
                        }
                    }
                }

                if (preview != null) {
                    val totalRows = preview.points.sumOf { it.species.size }
                    Text("识别预览：点位 ${preview.points.size} 个，条目 $totalRows 条", style = MaterialTheme.typography.bodySmall)

                    val showPoints = preview.points.take(6)
                    for (point in showPoints) {
                        val total = point.species.sumOf { it.count }
                        Text("${point.label}：${point.species.size} 种，合计 $total", style = MaterialTheme.typography.bodySmall)
                    }
                    val more = preview.points.size - showPoints.size
                    if (more > 0) {
                        Text("…还有 $more 个点位", style = MaterialTheme.typography.bodySmall)
                    }

                    if (preview.warnings.isNotEmpty()) {
                        val shown = preview.warnings.take(6)
                        val moreWarn = preview.warnings.size - shown.size
                        val tipText = if (moreWarn > 0) shown.joinToString("；") + "；另有 $moreWarn 条" else shown.joinToString("；")
                        Text("识别提示：$tipText", style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.notes.isNotEmpty()) {
                        Text("备注：${preview.notes.joinToString("；")}", style = MaterialTheme.typography.bodySmall)
                    }

                    val applyLabel = if (mode == ImageImportMode.NewDataset) "应用并新建数据集" else "应用到当前数据集"
                    Button(
                        enabled = !busy,
                        onClick = { onApplyPreview(preview) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(applyLabel) }
                }
            }
        }
    }
}

internal fun LazyListScope.groupedSpeciesListItems(
    groupedSpecies: List<Pair<String, List<Species>>>,
    activePointId: Id,
    activeSpeciesId: Id?,
    aiUiHidden: Boolean,
    onSelectSpecies: (Id) -> Unit,
    onEditFull: (Id) -> Unit,
    onEditCount: (Species) -> Unit,
    onEditTaxonomy: (Id) -> Unit,
    onAutofillWetWeight: (Id) -> Unit,
    onAutofillTaxonomy: (Id) -> Unit,
    onQueryTaxonomy: (Id) -> Unit,
    onQueryWetWeight: (Id) -> Unit,
    onSaveWetWeight: (Species, Double) -> Unit,
    onDeleteSpecies: (Id) -> Unit,
) {
    for ((groupLabel, list) in groupedSpecies) {
        item(
            key = "group-$groupLabel",
            contentType = "group-header",
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                blurEnabled = false,
                elevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("$groupLabel (${list.size})", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        for (s in list) {
            item(
                key = "species-${s.id}",
                contentType = "species-row",
            ) {
                SpeciesCard(
                    species = s,
                    activePointId = activePointId,
                    selected = s.id == activeSpeciesId,
                    aiUiHidden = aiUiHidden,
                    onSelect = { onSelectSpecies(s.id) },
                    onEditFull = { onEditFull(s.id) },
                    onEditCount = { onEditCount(s) },
                    onEditTaxonomy = { onEditTaxonomy(s.id) },
                    onAutofillWetWeight = { onAutofillWetWeight(s.id) },
                    onAutofillTaxonomy = { onAutofillTaxonomy(s.id) },
                    onQueryTaxonomy = { onQueryTaxonomy(s.id) },
                    onQueryWetWeight = { onQueryWetWeight(s.id) },
                    onSaveWetWeight = { mg: Double -> onSaveWetWeight(s, mg) },
                    onDelete = { onDeleteSpecies(s.id) },
                )
            }
        }
    }
}

@Composable
internal fun SpeciesQuickActionsDock(
    canScrollTop: Boolean,
    onScrollTop: () -> Unit,
    onAddSpecies: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        var fabExpanded by rememberSaveable { mutableStateOf(false) }
        var fabTick by rememberSaveable { mutableStateOf(0) }

        fun expandFab() {
            fabExpanded = true
            fabTick += 1
        }

        LaunchedEffect(fabExpanded, fabTick) {
            if (fabExpanded) {
                delay(2600)
                fabExpanded = false
            }
        }

        val xOffset by animateDpAsState(targetValue = if (fabExpanded) 0.dp else 24.dp, label = "fabDockX")

        Column(
            modifier = Modifier.offset(x = xOffset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (!fabExpanded) {
                FloatingActionButton(onClick = ::expandFab) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "展开")
                }
            } else {
                if (canScrollTop) {
                    FloatingActionButton(onClick = {
                        fabExpanded = false
                        onScrollTop()
                    }) {
                        Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = "返回顶部")
                    }
                }
                FloatingActionButton(onClick = {
                    fabExpanded = false
                    onAddSpecies()
                }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "添加物种")
                }
            }
        }
    }
}

@Composable
internal fun SpeciesCard(
    species: Species,
    activePointId: Id,
    selected: Boolean,
    aiUiHidden: Boolean,
    onSelect: () -> Unit,
    onEditFull: () -> Unit,
    onEditCount: () -> Unit,
    onEditTaxonomy: () -> Unit,
    onAutofillWetWeight: () -> Unit,
    onAutofillTaxonomy: () -> Unit,
    onQueryTaxonomy: () -> Unit,
    onQueryWetWeight: () -> Unit,
    onSaveWetWeight: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val count by remember(activePointId, species.countsByPointId) {
        derivedStateOf { species.countsByPointId[activePointId] ?: 0 }
    }
    val missingWetWeight by remember(species.avgWetWeightMg, species.countsByPointId) {
        derivedStateOf { species.avgWetWeightMg == null && speciesAnyCountPositive(species) }
    }
    val taxSummary by remember(species.taxonomy) {
        derivedStateOf {
            val t = species.taxonomy
            listOf(
                t.lvl2.takeIf { it.isNotBlank() },
                t.lvl3.takeIf { it.isNotBlank() },
                t.lvl4.takeIf { it.isNotBlank() },
                t.lvl5.takeIf { it.isNotBlank() },
            ).filterNotNull().joinToString(" · ").ifBlank { "（未分类）" }
        }
    }
    val wetText by remember(species.avgWetWeightMg) {
        derivedStateOf { species.avgWetWeightMg?.let { "${speciesFormatMg(it)} mg/个" } ?: "未查到湿重" }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        blurEnabled = false,
        elevation = if (selected) 6.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        species.nameCn.ifBlank { "（未命名物种）" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (species.nameLatin.isNotBlank()) {
                        Text(
                            species.nameLatin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onSelect()
                        onEditCount()
                    },
                ) { Text("计数 $count") }
                IconButton(onClick = onEditFull) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "编辑信息")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑分类/拉丁名") },
                            onClick = {
                                menuOpen = false
                                onEditTaxonomy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("从分类库补齐分类") },
                            onClick = {
                                menuOpen = false
                                onAutofillTaxonomy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("从湿重库补齐湿重") },
                            onClick = {
                                menuOpen = false
                                onAutofillWetWeight()
                            },
                        )
                        if (!aiUiHidden) {
                            DropdownMenuItem(
                                text = { Text("双 API 查分类") },
                                onClick = {
                                    menuOpen = false
                                    onQueryTaxonomy()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("双 API 查湿重") },
                                onClick = {
                                    menuOpen = false
                                    onQueryWetWeight()
                                },
                            )
                        }
                        val wetWeightMg = species.avgWetWeightMg
                        DropdownMenuItem(
                            text = { Text("保存到湿重库") },
                            enabled = wetWeightMg != null && species.nameCn.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                wetWeightMg?.let(onSaveWetWeight)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除物种") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    taxSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    wetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (species.avgWetWeightMg == null && missingWetWeight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (missingWetWeight) {
                GlassCard(modifier = Modifier.fillMaxWidth(), blurEnabled = false) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("未查到湿重", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!aiUiHidden) {
                                OutlinedButton(onClick = onQueryWetWeight) { Text("双 API 查湿重") }
                            }
                            OutlinedButton(onClick = onEditFull) { Text("去编辑填写") }
                        }
                    }
                }
            }
        }
    }
}

