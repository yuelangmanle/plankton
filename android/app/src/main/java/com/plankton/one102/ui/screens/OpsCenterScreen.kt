package com.plankton.one102.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.DataIssue
import com.plankton.one102.domain.ImportTemplatePreset
import com.plankton.one102.domain.ImportTemplateType
import com.plankton.one102.domain.IssueLevel
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.VoiceCommandMacro
import com.plankton.one102.domain.validateDataset
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.PreviewCommand
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.theme.LocalDensityTokens
import java.util.UUID

private data class QualityTrendItem(
    val title: String,
    val score: Int,
    val errors: Int,
    val warns: Int,
    val infos: Int,
)

private fun calcQualityScore(issues: List<DataIssue>): Int {
    val errors = issues.count { it.level == IssueLevel.Error }
    val warns = issues.count { it.level == IssueLevel.Warn }
    val infos = issues.count { it.level == IssueLevel.Info }
    val raw = 100 - errors * 25 - warns * 8 - infos * 2
    return raw.coerceIn(0, 100)
}

private fun ImportTemplatePreset.mappingSummaryText(): String {
    val aliases = aliasColumns.trim().ifBlank { "-" }
    return "Sheet${sheetIndex + 1} · ${if (hasHeader) "有表头" else "无表头"} · 起始行$startRow · " +
        "中文名=$nameCnColumn 拉丁=$latinColumn 湿重=$wetWeightColumn 别名=$aliases"
}

@Composable
fun OpsCenterScreen(
    viewModel: MainViewModel,
    padding: PaddingValues,
    onOpenFocus: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenDatabase: () -> Unit,
    onOpenDatasets: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalDensityTokens.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val summaries by viewModel.datasetSummaries.collectAsStateWithLifecycle()
    val lastExportAt by viewModel.lastExportAt.collectAsStateWithLifecycle()
    val lastExportUri by viewModel.lastExportUri.collectAsStateWithLifecycle()
    val activePointId by viewModel.activePointId.collectAsStateWithLifecycle()

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    val issues = remember(ds) { validateDataset(ds) }
    val errCount = remember(issues) { issues.count { it.level == IssueLevel.Error } }
    val warnCount = remember(issues) { issues.count { it.level == IssueLevel.Warn } }
    val infoCount = remember(issues) { issues.count { it.level == IssueLevel.Info } }
    val score = remember(issues) { calcQualityScore(issues) }
    val snapshots = remember(summaries) { summaries.filter { it.readOnly }.take(20) }

    val trend by produceState(initialValue = emptyList<QualityTrendItem>(), summaries, ds.id) {
        val rows = mutableListOf<QualityTrendItem>()
        val latest = summaries.take(8)
        for (summary in latest) {
            val rowDs = viewModel.getDatasetById(summary.id) ?: continue
            val rowIssues = validateDataset(rowDs)
            rows += QualityTrendItem(
                title = summary.titlePrefix.ifBlank { "未命名" },
                score = calcQualityScore(rowIssues),
                errors = rowIssues.count { it.level == IssueLevel.Error },
                warns = rowIssues.count { it.level == IssueLevel.Warn },
                infos = rowIssues.count { it.level == IssueLevel.Info },
            )
        }
        value = rows
    }

    var editingTemplate by remember { mutableStateOf<ImportTemplatePreset?>(null) }
    var showCreateTemplate by remember { mutableStateOf(false) }
    var deletingTemplate by remember { mutableStateOf<ImportTemplatePreset?>(null) }

    var editingMacro by remember { mutableStateOf<VoiceCommandMacro?>(null) }
    var showCreateMacro by remember { mutableStateOf(false) }
    var deletingMacro by remember { mutableStateOf<VoiceCommandMacro?>(null) }

    var notice by remember { mutableStateOf<String?>(null) }

    fun updateSettings(update: (Settings) -> Settings) {
        viewModel.saveSettings(update(settings))
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.sectionGap),
        ) {
            Text("运营中心", style = MaterialTheme.typography.titleLarge)
            Text(
                "把导入模板、异常修复、现场录入、语音宏、导出任务与质量趋势集中管理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("导入模板中心", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "模板会直接驱动数据库 Excel 导入。说明=用途；字段映射=真实导入列号（如 A/B/C 或数字列号）。保存后可设为当前生效模板。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    settings.importTemplates.forEach { item ->
                        val isActiveTemplate = when {
                            settings.activeImportTemplateId.isNotBlank() -> settings.activeImportTemplateId == item.id
                            else -> settings.importTemplates.firstOrNull()?.id == item.id
                        }
                        GlassCard(modifier = Modifier.fillMaxWidth(), elevation = 0.dp) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name.ifBlank { "未命名模板" }, style = MaterialTheme.typography.titleSmall)
                                if (isActiveTemplate) {
                                    Text(
                                        "当前生效模板",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (item.description.isNotBlank()) {
                                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "映射：${item.mappingSummaryText()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        updateSettings { cur -> cur.copy(activeImportTemplateId = item.id) }
                                        notice = "已设为当前导入映射：${item.name}"
                                    },
                                    enabled = !isActiveTemplate,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (isActiveTemplate) "已生效" else "设为当前导入映射")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { editingTemplate = item }, modifier = Modifier.weight(1f)) { Text("编辑") }
                                    TextButton(onClick = { deletingTemplate = item }, modifier = Modifier.weight(1f)) { Text("删除") }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showCreateTemplate = true }, modifier = Modifier.fillMaxWidth()) { Text("新增模板") }
                    OutlinedButton(onClick = onOpenDatabase, modifier = Modifier.fillMaxWidth()) { Text("打开数据库导入页") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("异常数据工作台", style = MaterialTheme.typography.titleMedium)
                    Text("当前数据评分：$score（错误 $errCount · 警告 $warnCount · 提示 $infoCount）")
                    if (issues.isEmpty()) {
                        Text("当前未发现异常。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        issues.take(12).forEach { issue ->
                            val prefix = when (issue.level) {
                                IssueLevel.Error -> "错误"
                                IssueLevel.Warn -> "警告"
                                IssueLevel.Info -> "提示"
                            }
                            Text("[$prefix] ${issue.title}：${issue.detail}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateCurrentDataset { cur ->
                                    val usedPointIds = cur.species
                                        .flatMap { sp -> sp.countsByPointId.filterValues { it > 0 }.keys }
                                        .toSet()
                                    cur.copy(
                                        points = cur.points.map { p ->
                                            if (p.id in usedPointIds && (p.vConcMl == null || p.vConcMl <= 0.0)) {
                                                p.copy(vConcMl = 1.0)
                                            } else {
                                                p
                                            }
                                        },
                                    )
                                }
                                notice = "已补齐缺失浓缩体积（默认 1.0 mL）。"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("补齐缺失体积") }
                        OutlinedButton(
                            onClick = {
                                viewModel.updateCurrentDataset { cur ->
                                    cur.copy(
                                        species = cur.species.map { sp ->
                                            if (sp.taxonomy.lvl1.isBlank() && sp.countsByPointId.values.any { it > 0 }) {
                                                sp.copy(taxonomy = sp.taxonomy.copy(lvl1 = "（未分类）"))
                                            } else {
                                                sp
                                            }
                                        },
                                    )
                                }
                                notice = "已补齐缺失大类为“（未分类）”。"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("补齐缺失分类") }
                    }
                    Button(
                        onClick = {
                            viewModel.updateAssistantQuestion(
                                "请对当前数据集做深度修复：先列出缺失湿重、缺失分类、异常点位，再按风险优先级给出逐步修复清单。",
                            )
                            onOpenAssistant()
                            notice = if (settings.aiAssistantEnabled) {
                                "已打开助手并注入深度修复任务。"
                            } else {
                                "已打开助手并注入任务（当前 AI 开关关闭，需在设置中开启 AI 后再发送）。"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("打开助手页做深度修复（执行）") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("现场录入模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "大按钮/单手区/防误触/自动续录，适配户外快速录入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("自动续录（保留上次节奏）", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.fieldModeAutoContinue,
                            onCheckedChange = { v -> updateSettings { it.copy(fieldModeAutoContinue = v) } },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("防误触锁屏（默认开启）", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.fieldModeAccidentalTouchGuard,
                            onCheckedChange = { v -> updateSettings { it.copy(fieldModeAccidentalTouchGuard = v) } },
                        )
                    }
                    Button(onClick = onOpenFocus, modifier = Modifier.fillMaxWidth()) { Text("进入现场录入") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("语音指令宏", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "保存常用语音/文本指令为快捷宏，一键执行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    settings.voiceCommandMacros.forEach { item ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), elevation = 0.dp) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name.ifBlank { "未命名宏" }, style = MaterialTheme.typography.titleSmall)
                                Text(item.command, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (item.runAsAsk) "执行方式：询问 AI" else "执行方式：功能指令",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            val cmd = item.command.trim()
                                            val compactCmd = cmd
                                                .replace("，", "")
                                                .replace("。", "")
                                                .replace(" ", "")
                                            var handled = false
                                            val activePid = activePointId ?: ds.points.firstOrNull()?.id

                                            val wantsClearCurrent = listOf(
                                                "清零当前点位",
                                                "清空当前点位",
                                                "清零本点位",
                                                "清空本点位",
                                            ).any { compactCmd.contains(it) }
                                            if (!item.runAsAsk && activePid != null && wantsClearCurrent) {
                                                viewModel.updateCurrentDataset { cur ->
                                                    cur.copy(
                                                        species = cur.species.map { sp ->
                                                            sp.copy(countsByPointId = sp.countsByPointId + (activePid to 0))
                                                        },
                                                    )
                                                }
                                                notice = "已清空当前点位计数。"
                                                handled = true
                                            }
                                            if (!item.runAsAsk && activePid == null && wantsClearCurrent) {
                                                notice = "未找到可用点位，无法执行“清空当前点位”。"
                                                handled = true
                                            }

                                            val wantsExport1 = listOf("导出表1", "导出table1", "导出1表").any { compactCmd.contains(it) }
                                            val wantsExport2 = listOf("导出表2", "导出table2", "导出2表").any { compactCmd.contains(it) }
                                            if (!item.runAsAsk && wantsExport1) {
                                                viewModel.requestPreviewCommand(PreviewCommand.ExportTable1)
                                                onOpenPreview()
                                                notice = if (handled) "已清空当前点位，并开始导出表1。" else "已开始导出表1。"
                                                handled = true
                                            } else if (!item.runAsAsk && wantsExport2) {
                                                viewModel.requestPreviewCommand(PreviewCommand.ExportTable2)
                                                onOpenPreview()
                                                notice = if (handled) "已清空当前点位，并开始导出表2。" else "已开始导出表2。"
                                                handled = true
                                            }

                                            if (!item.runAsAsk &&
                                                listOf("检查缺失项", "缺失湿重", "缺失分类", "深度修复", "异常修复").any { compactCmd.contains(it) }
                                            ) {
                                                viewModel.updateAssistantQuestion("检查当前数据集缺失湿重、缺失分类和异常点位，并按优先级给出可执行修复步骤。")
                                                onOpenAssistant()
                                                notice = "已打开助手并注入缺失项深度修复任务。"
                                                handled = true
                                            }

                                            if (!handled) {
                                                viewModel.updateAssistantQuestion(item.command)
                                                if (item.runAsAsk) {
                                                    onOpenAssistant()
                                                    notice = "已打开助手并注入宏内容，请直接发送询问。"
                                                } else {
                                                    copyText(context, "语音宏", item.command)
                                                    onOpenAssistant()
                                                    notice = "未匹配到内置动作，已注入助手并复制到剪贴板。"
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("执行") }
                                    TextButton(onClick = { editingMacro = item }, modifier = Modifier.weight(1f)) { Text("编辑") }
                                    TextButton(onClick = { deletingMacro = item }, modifier = Modifier.weight(1f)) { Text("删除") }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showCreateMacro = true }, modifier = Modifier.fillMaxWidth()) { Text("新增宏") }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("导出任务中心", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "最近导出记录、快照与重试入口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Text("最近导出时间：${lastExportAt ?: "暂无"}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "最近导出路径：${lastExportUri ?: "暂无"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                    if (snapshots.isEmpty()) {
                        Text("暂无快照记录。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        snapshots.take(8).forEach { item ->
                            Text(
                                "• ${item.titlePrefix} · ${item.updatedAt} · 点位${item.pointsCount}/物种${item.speciesCount}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onOpenPreview, modifier = Modifier.weight(1f)) { Text("打开导出页") }
                        OutlinedButton(onClick = onOpenDatasets, modifier = Modifier.weight(1f)) { Text("打开历史数据集") }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(tokens.blockPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("数据质量趋势", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "按时间展示完整度评分与异常数量变化。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    if (trend.isEmpty()) {
                        Text("暂无可用于趋势分析的数据。", style = MaterialTheme.typography.bodySmall)
                    } else {
                        trend.forEach { item ->
                            Text(
                                "• ${item.title}：评分 ${item.score}（错${item.errors}/警${item.warns}/提${item.infos}）",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onOpenCharts, modifier = Modifier.weight(1f)) { Text("打开图表") }
                        OutlinedButton(onClick = onOpenAssistant, modifier = Modifier.weight(1f)) { Text("打开助手诊断") }
                    }
                }
            }

            notice?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showCreateTemplate) {
        TemplateEditorDialog(
            title = "新增导入模板",
            initial = null,
            onDismiss = { showCreateTemplate = false },
            onConfirm = { template ->
                updateSettings { cur ->
                    cur.copy(
                        importTemplates = cur.importTemplates + template.copy(
                            id = UUID.randomUUID().toString(),
                        ),
                    )
                }
                showCreateTemplate = false
            },
        )
    }
    editingTemplate?.let { target ->
        TemplateEditorDialog(
            title = "编辑导入模板",
            initial = target,
            onDismiss = { editingTemplate = null },
            onConfirm = { updated ->
                updateSettings { cur ->
                    cur.copy(importTemplates = cur.importTemplates.map { if (it.id == target.id) updated.copy(id = target.id) else it })
                }
                editingTemplate = null
            },
        )
    }
    deletingTemplate?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateSettings { cur -> cur.copy(importTemplates = cur.importTemplates.filterNot { it.id == target.id }) }
                        deletingTemplate = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingTemplate = null }) { Text("取消") } },
            title = { Text("删除模板") },
            text = { Text("确认删除“${target.name}”吗？") },
        )
    }

    if (showCreateMacro) {
        MacroEditorDialog(
            title = "新增语音宏",
            initial = null,
            onDismiss = { showCreateMacro = false },
            onConfirm = { macro ->
                updateSettings { cur ->
                    cur.copy(
                        voiceCommandMacros = cur.voiceCommandMacros + macro.copy(
                            id = UUID.randomUUID().toString(),
                        ),
                    )
                }
                showCreateMacro = false
            },
        )
    }
    editingMacro?.let { target ->
        MacroEditorDialog(
            title = "编辑语音宏",
            initial = target,
            onDismiss = { editingMacro = null },
            onConfirm = { updated ->
                updateSettings { cur ->
                    cur.copy(voiceCommandMacros = cur.voiceCommandMacros.map { if (it.id == target.id) updated.copy(id = target.id) else it })
                }
                editingMacro = null
            },
        )
    }
    deletingMacro?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingMacro = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateSettings { cur -> cur.copy(voiceCommandMacros = cur.voiceCommandMacros.filterNot { it.id == target.id }) }
                        deletingMacro = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingMacro = null }) { Text("取消") } },
            title = { Text("删除语音宏") },
            text = { Text("确认删除“${target.name}”吗？") },
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    title: String,
    initial: ImportTemplatePreset?,
    onDismiss: () -> Unit,
    onConfirm: (ImportTemplatePreset) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var desc by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var sheetIndexText by remember(initial?.id) { mutableStateOf(((initial?.sheetIndex ?: 0) + 1).toString()) }
    var hasHeader by remember(initial?.id) { mutableStateOf(initial?.hasHeader ?: true) }
    var headerRowText by remember(initial?.id) { mutableStateOf((initial?.headerRow ?: 1).toString()) }
    var startRowText by remember(initial?.id) { mutableStateOf((initial?.startRow ?: 2).toString()) }
    var nameCnColumn by remember(initial?.id) { mutableStateOf(initial?.nameCnColumn ?: "A") }
    var latinColumn by remember(initial?.id) { mutableStateOf(initial?.latinColumn ?: "B") }
    var wetWeightColumn by remember(initial?.id) { mutableStateOf(initial?.wetWeightColumn ?: "C") }
    var lvl1Column by remember(initial?.id) { mutableStateOf(initial?.lvl1Column ?: "D") }
    var lvl2Column by remember(initial?.id) { mutableStateOf(initial?.lvl2Column ?: "E") }
    var lvl3Column by remember(initial?.id) { mutableStateOf(initial?.lvl3Column ?: "F") }
    var lvl4Column by remember(initial?.id) { mutableStateOf(initial?.lvl4Column ?: "G") }
    var lvl5Column by remember(initial?.id) { mutableStateOf(initial?.lvl5Column ?: "H") }
    var aliasColumns by remember(initial?.id) { mutableStateOf(initial?.aliasColumns ?: "I") }
    var formError by remember(initial?.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = name.trim()
                    if (finalName.isEmpty()) {
                        formError = "模板名称不能为空"
                        return@TextButton
                    }
                    val sheetIndex = (sheetIndexText.trim().toIntOrNull() ?: 1) - 1
                    val headerRow = headerRowText.trim().toIntOrNull() ?: 1
                    val startRow = startRowText.trim().toIntOrNull() ?: 2
                    if (sheetIndex < 0) {
                        formError = "工作表序号需大于等于 1"
                        return@TextButton
                    }
                    if (headerRow < 1 || startRow < 1) {
                        formError = "行号需大于等于 1"
                        return@TextButton
                    }
                    if (nameCnColumn.trim().isEmpty()) {
                        formError = "中文名列不能为空"
                        return@TextButton
                    }
                    val mappingSummary = buildString {
                        append("Sheet")
                        append(sheetIndex + 1)
                        append(", 中文名=")
                        append(nameCnColumn.trim())
                        if (latinColumn.isNotBlank()) append(", 拉丁=" + latinColumn.trim())
                        if (wetWeightColumn.isNotBlank()) append(", 湿重=" + wetWeightColumn.trim())
                        if (aliasColumns.isNotBlank()) append(", 别名=" + aliasColumns.trim())
                    }
                    onConfirm(
                        ImportTemplatePreset(
                            id = initial?.id.orEmpty(),
                            name = finalName,
                            description = desc.trim(),
                            mapping = mappingSummary,
                            type = ImportTemplateType.SpeciesLibrary,
                            sheetIndex = sheetIndex,
                            hasHeader = hasHeader,
                            headerRow = headerRow,
                            startRow = startRow,
                            nameCnColumn = nameCnColumn.trim(),
                            latinColumn = latinColumn.trim(),
                            wetWeightColumn = wetWeightColumn.trim(),
                            lvl1Column = lvl1Column.trim(),
                            lvl2Column = lvl2Column.trim(),
                            lvl3Column = lvl3Column.trim(),
                            lvl4Column = lvl4Column.trim(),
                            lvl5Column = lvl5Column.trim(),
                            aliasColumns = aliasColumns.trim(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模板名称") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("说明") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sheetIndexText,
                        onValueChange = { sheetIndexText = it.filter { c -> c.isDigit() } },
                        label = { Text("工作表序号(1起)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = headerRowText,
                        onValueChange = { headerRowText = it.filter { c -> c.isDigit() } },
                        label = { Text("表头行") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = startRowText,
                        onValueChange = { startRowText = it.filter { c -> c.isDigit() } },
                        label = { Text("起始行") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("包含表头")
                    Switch(checked = hasHeader, onCheckedChange = { hasHeader = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = nameCnColumn, onValueChange = { nameCnColumn = it }, label = { Text("中文名列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = latinColumn, onValueChange = { latinColumn = it }, label = { Text("拉丁名列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = wetWeightColumn, onValueChange = { wetWeightColumn = it }, label = { Text("湿重列") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = lvl1Column, onValueChange = { lvl1Column = it }, label = { Text("大类列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lvl2Column, onValueChange = { lvl2Column = it }, label = { Text("纲列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lvl3Column, onValueChange = { lvl3Column = it }, label = { Text("目列") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = lvl4Column, onValueChange = { lvl4Column = it }, label = { Text("科列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lvl5Column, onValueChange = { lvl5Column = it }, label = { Text("属列") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = aliasColumns, onValueChange = { aliasColumns = it }, label = { Text("别名列(可多列)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (!formError.isNullOrBlank()) {
                    Text(formError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun MacroEditorDialog(
    title: String,
    initial: VoiceCommandMacro?,
    onDismiss: () -> Unit,
    onConfirm: (VoiceCommandMacro) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var command by remember(initial?.id) { mutableStateOf(initial?.command.orEmpty()) }
    var runAsAsk by remember(initial?.id) { mutableStateOf(initial?.runAsAsk ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isEmpty() || command.trim().isEmpty()) return@TextButton
                    onConfirm(
                        VoiceCommandMacro(
                            id = initial?.id.orEmpty(),
                            name = name.trim(),
                            command = command.trim(),
                            runAsAsk = runAsAsk,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("宏名称") }, singleLine = true)
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("指令内容") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("以“询问 AI”方式执行")
                    Switch(checked = runAsAsk, onCheckedChange = { runAsAsk = it })
                }
            }
        },
    )
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
