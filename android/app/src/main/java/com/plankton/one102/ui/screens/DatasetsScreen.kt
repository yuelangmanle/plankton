package com.plankton.one102.ui.screens

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.plankton.one102.data.repo.BackupExportOptions
import com.plankton.one102.data.repo.BackupImportOptions
import com.plankton.one102.data.repo.BackupSummary
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetSummary
import com.plankton.one102.domain.Point
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.calcDataset
import com.plankton.one102.importer.importDatasetFromTable1Excel
import com.plankton.one102.importer.importDatasetFromSimpleTableExcel
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.theme.GlassWhite
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import kotlin.math.abs

private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private enum class CompareTarget { A, B }

@Composable
fun DatasetsScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    val datasetSummaries by viewModel.datasetSummaries.collectAsStateWithLifecycle()
    val datasetTotal by viewModel.datasetTotalCount.collectAsStateWithLifecycle()
    val current by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    val dialogColor = if (settings.glassEffectEnabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    var renameTarget by remember { mutableStateOf<DatasetSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var shareBusy by remember { mutableStateOf(false) }
    var excelImportBusy by remember { mutableStateOf(false) }

    var exportDialogOpen by remember { mutableStateOf(false) }
    var exportEncrypt by remember { mutableStateOf(false) }
    var exportPwd1 by remember { mutableStateOf("") }
    var exportPwd2 by remember { mutableStateOf("") }
    var exportOptError by remember { mutableStateOf<String?>(null) }
    var pendingExportOptions by remember { mutableStateOf(BackupExportOptions()) }

    var importDialogOpen by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var importLoading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importPwd by remember { mutableStateOf("") }
    var importOptions by remember { mutableStateOf(BackupImportOptions()) }
    var importing by remember { mutableStateOf(false) }

    var compareAId by remember { mutableStateOf<String?>(current?.id) }
    var compareBId by remember { mutableStateOf<String?>(null) }
    var comparePicker by remember { mutableStateOf<CompareTarget?>(null) }
    var snapshotsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(datasetSummaries, current?.id) {
        if (compareAId == null || datasetSummaries.none { it.id == compareAId }) {
            compareAId = current?.id ?: datasetSummaries.firstOrNull()?.id
        }
        if (compareBId != null && datasetSummaries.none { it.id == compareBId }) {
            compareBId = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup(contentResolver, uri, options = pendingExportOptions) { res ->
            message = if (res.isSuccess) "已导出备份" else "导出失败：${res.exceptionOrNull()?.message}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importUri = uri
        importDialogOpen = true
        importLoading = true
        importError = null
        importSummary = null
        importPwd = ""
        importOptions = BackupImportOptions()

        viewModel.readBackupSummary(contentResolver, uri, password = null) { res ->
            importSummary = res.getOrNull()
            importError = res.exceptionOrNull()?.message
            importLoading = false
        }
    }

    val importTable1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (excelImportBusy) return@rememberLauncherForActivityResult
        excelImportBusy = true
        message = null
        scope.launch {
            val res = runCatching {
                val imported = importDatasetFromTable1Excel(contentResolver, uri, defaultVOrigL = settings.defaultVOrigL)
                viewModel.importNewDataset(imported)
                imported
            }
            message = res.fold(
                onSuccess = { "已从表1导入数据集：点位 ${it.points.size} · 物种 ${it.species.size}" },
                onFailure = { "表1导入失败：${it.message ?: it.toString()}" },
            )
            excelImportBusy = false
        }
    }

    val importSimpleTableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (excelImportBusy) return@rememberLauncherForActivityResult
        excelImportBusy = true
        message = null
        scope.launch {
            val res = runCatching {
                val imported = importDatasetFromSimpleTableExcel(contentResolver, uri, defaultVOrigL = settings.defaultVOrigL)
                viewModel.importNewDataset(imported)
                imported
            }
            message = res.fold(
                onSuccess = { "已从简表导入数据集：点位 ${it.points.size} · 物种 ${it.species.size}" },
                onFailure = { "简表导入失败：${it.message ?: it.toString()}" },
            )
            excelImportBusy = false
        }
    }

    var compareA by remember { mutableStateOf<Dataset?>(current) }
    var compareB by remember { mutableStateOf<Dataset?>(null) }

    LaunchedEffect(compareAId, current?.id) {
        compareA = when {
            compareAId == null -> null
            current?.id == compareAId -> current
            else -> viewModel.getDatasetById(compareAId!!)
        }
    }

    LaunchedEffect(compareBId, current?.id) {
        compareB = when {
            compareBId == null -> null
            current?.id == compareBId -> current
            else -> viewModel.getDatasetById(compareBId!!)
        }
    }

    val compareResult = remember(compareA, compareB) {
        if (compareA != null && compareB != null) compareDatasets(compareA!!, compareB!!) else null
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
            Text("历史数据集", style = MaterialTheme.typography.titleLarge)

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("数据集操作", style = MaterialTheme.typography.titleMedium)
                Text(
                    "新建/导入/备份集中在此处，列表与结果在下方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Text("主操作", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { viewModel.createNewDataset() }, modifier = Modifier.fillMaxWidth()) { Text("新建数据集") }
                OutlinedButton(
                    enabled = !excelImportBusy,
                    onClick = { importTable1Launcher.launch(arrayOf(MIME_XLSX)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (excelImportBusy) "表1导入中…" else "从表1（计数表）导入新数据集") }
                OutlinedButton(
                    enabled = !excelImportBusy,
                    onClick = { importSimpleTableLauncher.launch(arrayOf(MIME_XLSX)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (excelImportBusy) "简表导入中…" else "从简表（四大类+物种）导入新数据集") }

                Text("备份与分享", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportDialogOpen = true
                            exportEncrypt = false
                            exportPwd1 = ""
                            exportPwd2 = ""
                            exportOptError = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("导出备份") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("导入备份") }
                }
                OutlinedButton(
                    enabled = !shareBusy,
                    onClick = {
                        shareBusy = true
                        message = null
                        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
                        val filename = "plankton-backup-${System.currentTimeMillis()}.json"
                        val file = File(dir, filename)
                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, file)

                        viewModel.exportBackup(contentResolver, uri) { res ->
                            shareBusy = false
                            message = res.fold(
                                onSuccess = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享备份文件"))
                                    "已生成备份并打开分享"
                                },
                                onFailure = { "生成备份失败：${it.message}" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (shareBusy) "生成备份中…" else "一键分享备份") }
            }
        }

        Text("结果与列表", style = MaterialTheme.typography.titleMedium)

        if (message != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    message!!,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("结果对比", style = MaterialTheme.typography.titleMedium)
                Text(
                    "选择两个数据集，查看差异点位、差异物种与指数变化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { comparePicker = CompareTarget.A },
                        modifier = Modifier.weight(1f),
                    ) { Text(compareA?.let { datasetTitle(it) } ?: "选择数据集A") }
                    OutlinedButton(
                        onClick = { comparePicker = CompareTarget.B },
                        modifier = Modifier.weight(1f),
                    ) { Text(compareB?.let { datasetTitle(it) } ?: "选择数据集B") }
                }

                val compareAValue = compareA
                val compareBValue = compareB
                if (compareAValue == null || compareBValue == null) {
                    Text("请先选择两个数据集。", style = MaterialTheme.typography.bodySmall)
                } else if (compareAValue.id == compareBValue.id) {
                    Text("当前选择为同一数据集。", style = MaterialTheme.typography.bodySmall)
                } else if (compareResult != null) {
                    val diff = compareResult
                    Text("差异点位（A 独有）：${formatListPreview(diff.pointsOnlyInA)}", style = MaterialTheme.typography.bodySmall)
                    Text("差异点位（B 独有）：${formatListPreview(diff.pointsOnlyInB)}", style = MaterialTheme.typography.bodySmall)
                    Text("差异物种（A 独有）：${formatListPreview(diff.speciesOnlyInA)}", style = MaterialTheme.typography.bodySmall)
                    Text("差异物种（B 独有）：${formatListPreview(diff.speciesOnlyInB)}", style = MaterialTheme.typography.bodySmall)

                    Text("指数变化（仅列不一致）", style = MaterialTheme.typography.titleSmall)
                    if (diff.indexDiffs.isEmpty()) {
                        Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else {
                        val sorted = diff.indexDiffs.sortedByDescending { abs(it.deltaValue ?: 0.0) }
                        val shown = sorted.take(12)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (row in shown) {
                                Text(
                                    "• ${row.pointLabel} / ${row.metric}：${row.aValue} → ${row.bValue}（Δ ${row.delta}）",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (sorted.size > shown.size) {
                            Text("… 另有 ${sorted.size - shown.size} 条变化", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val normalDatasets = datasetSummaries.filter { !it.readOnly }
        val snapshotDatasets = datasetSummaries.filter { it.readOnly }

        if (datasetSummaries.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("暂无数据集", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "请先新建数据集或从表1导入；导入后可在下方列表管理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (ds in normalDatasets) {
                    DatasetRow(
                        ds = ds,
                        selected = current?.id == ds.id,
                        onSelect = { viewModel.selectDataset(ds.id) },
                        onRename = {
                            renameTarget = ds
                            renameText = ds.titlePrefix
                        },
                        onDuplicate = { viewModel.duplicateDataset(ds.id) },
                        onDelete = { viewModel.deleteDataset(ds.id) },
                    )
                }

                if (snapshotDatasets.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "只读快照（${snapshotDatasets.size}）",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { snapshotsExpanded = !snapshotsExpanded }) {
                                    Text(if (snapshotsExpanded) "收起" else "展开")
                                }
                            }
                            Text(
                                "统一管理导出前的只读快照。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            if (snapshotsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (ds in snapshotDatasets) {
                                        DatasetRow(
                                            ds = ds,
                                            selected = current?.id == ds.id,
                                            onSelect = { viewModel.selectDataset(ds.id) },
                                            onRename = {
                                                renameTarget = ds
                                                renameText = ds.titlePrefix
                                            },
                                            onDuplicate = { viewModel.duplicateDataset(ds.id) },
                                            onDelete = { viewModel.deleteDataset(ds.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (datasetSummaries.size < datasetTotal) {
                    OutlinedButton(
                        onClick = { viewModel.loadMoreDatasets() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加载更多数据集") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ds = renameTarget ?: return@TextButton
                        viewModel.renameDataset(ds.id, renameText)
                        renameTarget = null
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
            title = { Text("重命名（标题前缀）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("标题前缀（可空）") },
                        singleLine = true,
                    )
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (exportDialogOpen) {
        AlertDialog(
            onDismissRequest = { exportDialogOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportOptError = null
                        if (exportEncrypt) {
                            if (exportPwd1.trim().isBlank()) {
                                exportOptError = "请输入加密密码"
                                return@TextButton
                            }
                            if (exportPwd1 != exportPwd2) {
                                exportOptError = "两次密码不一致"
                                return@TextButton
                            }
                            pendingExportOptions = BackupExportOptions(encrypt = true, password = exportPwd1)
                            exportLauncher.launch("plankton-backup.enc.json")
                        } else {
                            pendingExportOptions = BackupExportOptions(encrypt = false, password = null)
                            exportLauncher.launch("plankton-backup.json")
                        }
                        exportDialogOpen = false
                    },
                ) { Text("选择位置并导出") }
            },
            dismissButton = { TextButton(onClick = { exportDialogOpen = false }) { Text("取消") } },
            title = { Text("导出备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = exportEncrypt, onCheckedChange = { exportEncrypt = it })
                        Text("加密备份（需要密码）")
                    }
                    if (exportEncrypt) {
                        OutlinedTextField(
                            value = exportPwd1,
                            onValueChange = { exportPwd1 = it },
                            label = { Text("密码") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = exportPwd2,
                            onValueChange = { exportPwd2 = it },
                            label = { Text("确认密码") },
                            singleLine = true,
                        )
                    }
                    if (exportOptError != null) {
                        Text(exportOptError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (importDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!importing) importDialogOpen = false
            },
            confirmButton = {
                TextButton(
                    enabled = !importing && !importLoading,
                    onClick = {
                        val uri = importUri ?: return@TextButton
                        val sum = importSummary
                        val needPwd = sum?.encrypted == true
                        val pwd = importPwd.trim().takeIf { it.isNotBlank() }
                        if (needPwd && pwd == null) {
                            importError = "备份已加密：请输入密码"
                            return@TextButton
                        }
                        importing = true
                        importError = null
                        val finalOptions = importOptions.copy(password = pwd)
                        viewModel.importBackup(contentResolver, uri, options = finalOptions) { res ->
                            message = res.fold(
                                onSuccess = {
                                    if (finalOptions.importDatasets) "已导入 $it 个数据集" else "已导入（未导入数据集）"
                                },
                                onFailure = { "导入失败：${it.message ?: it.toString()}" },
                            )
                            importing = false
                            importDialogOpen = false
                        }
                    },
                ) { Text(if (importing) "导入中…" else "开始导入") }
            },
            dismissButton = { TextButton(enabled = !importing, onClick = { importDialogOpen = false }) { Text("取消") } },
            title = { Text("导入备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (importLoading) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                            Text("解析中…")
                        }
                    } else if (importSummary != null) {
                        val s = importSummary!!
                        Text("加密：${if (s.encrypted) "是" else "否"}")
                        if (s.exportedAt != null) Text("导出时间：${s.exportedAt}")
                        if (s.decrypted) {
                            Text("数据集：${s.datasets ?: "?"} · 点位：${s.points ?: "?"} · 物种：${s.species ?: "?"}")
                            Text("自定义库：湿重 ${s.wetWeights ?: "?"} · 分类 ${s.taxonomies ?: "?"} · 别名 ${s.aliases ?: "?"} · AI缓存 ${s.aiCache ?: "?"}")
                        } else {
                            Text("需要密码才能预览详细信息。")
                        }
                    }

                    if (importSummary?.encrypted == true && importSummary?.decrypted != true) {
                        OutlinedTextField(
                            value = importPwd,
                            onValueChange = { importPwd = it },
                            label = { Text("密码") },
                            singleLine = true,
                        )
                        Button(
                            enabled = !importing && importPwd.trim().isNotBlank(),
                            onClick = {
                                val uri = importUri ?: return@Button
                                importLoading = true
                                importError = null
                                viewModel.readBackupSummary(contentResolver, uri, password = importPwd.trim()) { res ->
                                    importSummary = res.getOrNull()
                                    importError = res.exceptionOrNull()?.message
                                    importLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("解锁预览") }
                    }

                    Text("选择导入内容：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importDatasets, onCheckedChange = { importOptions = importOptions.copy(importDatasets = it) })
                        Text("数据集")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importSettings, onCheckedChange = { importOptions = importOptions.copy(importSettings = it) })
                        Text("设置")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importWetWeights, onCheckedChange = { importOptions = importOptions.copy(importWetWeights = it) })
                        Text("自定义湿重")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importTaxonomies, onCheckedChange = { importOptions = importOptions.copy(importTaxonomies = it) })
                        Text("自定义分类")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importAliases, onCheckedChange = { importOptions = importOptions.copy(importAliases = it) })
                        Text("别名")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Checkbox(checked = importOptions.importAiCache, onCheckedChange = { importOptions = importOptions.copy(importAiCache = it) })
                        Text("AI缓存")
                    }

                    if (importError != null) {
                        Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }

    if (comparePicker != null) {
        val target = comparePicker
        AlertDialog(
            onDismissRequest = { comparePicker = null },
            confirmButton = {
                TextButton(onClick = { comparePicker = null }) { Text("关闭") }
            },
            title = { Text(if (target == CompareTarget.A) "选择数据集A" else "选择数据集B") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(datasetSummaries, key = { it.id }) { ds ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (target == CompareTarget.A) compareAId = ds.id else compareBId = ds.id
                                    comparePicker = null
                                },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(datasetTitle(ds), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "更新时间：${formatIso(ds.updatedAt)} · 点位 ${ds.pointsCount} · 物种 ${ds.speciesCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}

@Composable
private fun DatasetRow(
    ds: DatasetSummary,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = datasetTitle(ds)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected) "✓ $title" else title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ds.readOnly) {
                    Text(
                        "只读快照",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            Text(
                "更新时间：${formatIso(ds.updatedAt)} · 点位 ${ds.pointsCount} · 物种 ${ds.speciesCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRename) { Text("重命名") }
                OutlinedButton(onClick = onDuplicate) { Text("复制") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

private fun formatIso(iso: String): String {
    return runCatching {
        val instant = Instant.parse(iso)
        formatter.format(instant)
    }.getOrElse { iso }
}

private fun datasetTitle(ds: DatasetSummary): String {
    return ds.titlePrefix.ifBlank { "未命名（${formatIso(ds.createdAt)}）" }
}

private fun datasetTitle(ds: Dataset): String {
    return ds.titlePrefix.ifBlank { "未命名（${formatIso(ds.createdAt)}）" }
}

private fun formatListPreview(items: List<String>, limit: Int = 8): String {
    if (items.isEmpty()) return "暂无"
    val shown = items.take(limit)
    val base = shown.joinToString("、")
    val rest = items.size - shown.size
    return if (rest > 0) "$base 等 ${items.size} 个" else base
}

private data class CompareResult(
    val pointsOnlyInA: List<String>,
    val pointsOnlyInB: List<String>,
    val speciesOnlyInA: List<String>,
    val speciesOnlyInB: List<String>,
    val indexDiffs: List<IndexDiffRow>,
)

private data class IndexDiffRow(
    val pointLabel: String,
    val metric: String,
    val aValue: String,
    val bValue: String,
    val delta: String,
    val deltaValue: Double?,
)

private fun compareDatasets(a: Dataset, b: Dataset): CompareResult {
    fun pointKey(p: Point): String = p.label.trim().ifBlank { p.id }
    fun speciesKey(s: Species): String = s.nameCn.trim().ifBlank { s.id }
    fun fmtNum(value: Double?, digits: Int = 6): String {
        if (value == null || !value.isFinite()) return "—"
        return "%.${digits}f".format(value).trimEnd('0').trimEnd('.').ifBlank { "0" }
    }

    val pointsA = a.points.associateBy { pointKey(it) }
    val pointsB = b.points.associateBy { pointKey(it) }
    val labelsA = pointsA.keys
    val labelsB = pointsB.keys
    val onlyA = (labelsA - labelsB).sorted()
    val onlyB = (labelsB - labelsA).sorted()

    val speciesA = a.species.filter { anyCountPositive(it) }.map { speciesKey(it) }.filter { it.isNotBlank() }.toSet()
    val speciesB = b.species.filter { anyCountPositive(it) }.map { speciesKey(it) }.filter { it.isNotBlank() }.toSet()
    val speciesOnlyA = (speciesA - speciesB).sorted()
    val speciesOnlyB = (speciesB - speciesA).sorted()

    val calcA = calcDataset(a)
    val calcB = calcDataset(b)
    val commonLabels = labelsA.intersect(labelsB)
    val diffs = mutableListOf<IndexDiffRow>()

    fun addDiff(label: String, metric: String, aVal: Double?, bVal: Double?, tol: Double = 1e-6) {
        if (aVal == null && bVal == null) return
        val deltaValue = if (aVal != null && bVal != null) bVal - aVal else null
        val changed = if (aVal == null || bVal == null) true else abs(bVal - aVal) > tol
        if (!changed) return
        diffs += IndexDiffRow(
            pointLabel = label,
            metric = metric,
            aValue = fmtNum(aVal),
            bValue = fmtNum(bVal),
            delta = fmtNum(deltaValue, 6),
            deltaValue = deltaValue,
        )
    }

    for (label in commonLabels) {
        val pa = pointsA[label] ?: continue
        val pb = pointsB[label] ?: continue
        val ia = calcA.pointIndexById[pa.id]
        val ib = calcB.pointIndexById[pb.id]
        if (ia == null || ib == null) continue
        addDiff(label, "N", ia.totalCount.toDouble(), ib.totalCount.toDouble(), tol = 0.0)
        addDiff(label, "S", ia.speciesCountS.toDouble(), ib.speciesCountS.toDouble(), tol = 0.0)
        addDiff(label, "H'", ia.H, ib.H, tol = 1e-6)
        addDiff(label, "D", ia.D, ib.D, tol = 1e-6)
        addDiff(label, "J", ia.J, ib.J, tol = 1e-6)
    }

    return CompareResult(
        pointsOnlyInA = onlyA,
        pointsOnlyInB = onlyB,
        speciesOnlyInA = speciesOnlyA,
        speciesOnlyInB = speciesOnlyB,
        indexDiffs = diffs,
    )
}

private fun anyCountPositive(species: Species): Boolean = species.countsByPointId.values.any { it > 0 }
