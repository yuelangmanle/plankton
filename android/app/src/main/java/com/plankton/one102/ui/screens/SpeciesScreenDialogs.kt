package com.plankton.one102.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.SpeciesDbItem
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.dialogs.BatchCountDialog
import com.plankton.one102.ui.dialogs.TaxonomyDialog
import com.plankton.one102.ui.dialogs.TaxonomyQueryDialog
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import com.plankton.one102.voiceassistant.VoiceAssistantResult

@Composable
internal fun SpeciesAddFromDatabaseDialog(
    visible: Boolean,
    query: String,
    dbItems: List<SpeciesDbItem>,
    onQueryChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
    onSelectEntry: (SpeciesDbItem) -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
        title = { Text("添加物种（数据库搜索）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("搜索") },
                    singleLine = true,
                    placeholder = { Text("搜索中文名/拉丁名/分类…") },
                )

                val normalized = query.trim()
                val results = if (normalized.isBlank()) {
                    emptyList()
                } else {
                    val qLower = normalized.lowercase()
                    dbItems.asSequence().filter { item ->
                        item.nameCn.contains(normalized) ||
                            (item.nameLatin ?: "").lowercase().contains(qLower) ||
                            (item.taxonomy?.lvl1 ?: "").contains(normalized) ||
                            (item.taxonomy?.lvl2 ?: "").contains(normalized) ||
                            (item.taxonomy?.lvl3 ?: "").contains(normalized) ||
                            (item.taxonomy?.lvl4 ?: "").contains(normalized) ||
                            (item.taxonomy?.lvl5 ?: "").contains(normalized)
                    }.take(20).toList()
                }

                if (results.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (entry in results) {
                            OutlinedButton(
                                onClick = { onSelectEntry(entry) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(entry.nameCn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!entry.nameLatin.isNullOrBlank()) {
                                        Text(
                                            entry.nameLatin.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "提示：找不到时可去“数据库”页导入/新增自定义条目，或用“新增空白”手动录入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        },
    )
}

@Composable
internal fun SpeciesCountEditDialog(
    species: Species,
    countText: String,
    onCountTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动输入计数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(species.nameCn.ifBlank { "（未命名物种）" }, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = countText,
                    onValueChange = onCountTextChange,
                    singleLine = true,
                    label = { Text("计数") },
                    placeholder = { Text("例如：12") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SpeciesSimpleConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SpeciesCreateLibraryDialog(
    visible: Boolean,
    libraryNameDraft: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onCreateAndSwitch: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建湿重库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = libraryNameDraft,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text("库名称") },
                    placeholder = { Text("例如：推荐湿重库") },
                )
            }
        },
        confirmButton = { TextButton(onClick = onCreateAndSwitch) { Text("创建并切换") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SpeciesTaxonomyWetBatchDialogsHost(
    settings: Settings,
    taxonomyTarget: Species?,
    taxonomyQueryTarget: Species?,
    wetWeightTarget: Species?,
    batchDialogOpen: Boolean,
    speciesEditWriteToDb: Boolean,
    viewModel: MainViewModel,
    activePointId: Id,
    incomingPayload: VoiceAssistantResult?,
    onCloseTaxonomyEdit: () -> Unit,
    onApplyTaxonomyEdit: (String, Taxonomy, Boolean) -> Unit,
    onCloseTaxonomyQuery: () -> Unit,
    onApplyTaxonomyQuery: (Taxonomy) -> Unit,
    onCloseWetWeightQuery: () -> Unit,
    onApplyWetWeightQuery: (Double) -> Unit,
    onSaveWetWeightToLibrary: (Double) -> Unit,
    onCloseBatchDialog: () -> Unit,
) {
    if (taxonomyTarget != null) {
        TaxonomyDialog(
            initialLatin = taxonomyTarget.nameLatin,
            initialTaxonomy = taxonomyTarget.taxonomy,
            initialWriteToDb = speciesEditWriteToDb,
            onClose = onCloseTaxonomyEdit,
            onApply = onApplyTaxonomyEdit,
        )
    }

    if (taxonomyQueryTarget != null) {
        TaxonomyQueryDialog(
            settings = settings,
            nameCn = taxonomyQueryTarget.nameCn,
            nameLatin = taxonomyQueryTarget.nameLatin.trim().ifBlank { null },
            onClose = onCloseTaxonomyQuery,
            onApply = onApplyTaxonomyQuery,
        )
    }

    if (wetWeightTarget != null) {
        WetWeightQueryDialog(
            settings = settings,
            nameCn = wetWeightTarget.nameCn,
            nameLatin = wetWeightTarget.nameLatin,
            onClose = onCloseWetWeightQuery,
            onApply = onApplyWetWeightQuery,
            onSaveToLibrary = onSaveWetWeightToLibrary,
        )
    }

    if (batchDialogOpen) {
        BatchCountDialog(
            viewModel = viewModel,
            activePointId = activePointId,
            incomingPayload = incomingPayload,
            onClose = onCloseBatchDialog,
        )
    }
}
