package com.plankton.one102.ui.screens

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.plankton.one102.PlanktonApplication
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.data.api.buildCalcOutputFromCalc
import com.plankton.one102.data.api.CalcOutput
import com.plankton.one102.domain.ApiConfig
import com.plankton.one102.domain.BiomassCell
import com.plankton.one102.domain.CalcDiffReport
import com.plankton.one102.domain.DataIssue
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetCalc
import com.plankton.one102.domain.calcDataset
import com.plankton.one102.domain.buildStratifiedLabel
import com.plankton.one102.domain.diffCalc
import com.plankton.one102.domain.IssueLevel
import com.plankton.one102.domain.LibraryMeta
import com.plankton.one102.domain.normalizeLvl1Name
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.resolveSiteAndDepthForPoint
import com.plankton.one102.domain.Species
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.validateDataset
import com.plankton.one102.export.DocxReportExporter
import com.plankton.one102.export.ExportLatinOptions
import com.plankton.one102.export.ExcelExportWorker
import com.plankton.one102.export.ExcelTemplateExporter
import com.plankton.one102.export.WorkbookPreview
import com.plankton.one102.export.buildTaxonomyLatinOverrides
import com.plankton.one102.export.buildWorkbookPreview
import com.plankton.one102.export.writeFileToSafAtomic
import com.plankton.one102.ui.CalcSource
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.PreviewCommand
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import com.plankton.one102.ui.components.AiRichText
import com.plankton.one102.ui.theme.GlassWhite
import com.plankton.one102.ui.dialogs.WorkbookPreviewDialog
import com.plankton.one102.ui.dialogs.WorkbookPreviewSummary
import com.plankton.one102.ui.dialogs.WetWeightQueryDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private fun anyCountPositive(species: Species): Boolean = species.countsByPointId.values.any { it > 0 }

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

private fun defaultAiReportName(apiLabel: String): String {
    val clean = apiLabel.trim().ifBlank { "API" }
    return "${safeFileStem(clean)}浮游动物初步评价.docx"
}

private fun safeFileStem(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "浮游动物一体化"
    return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

private fun defaultXlsxName(titlePrefix: String, tableLabel: String): String {
    val prefix = titlePrefix.trim()
    val stem = if (prefix.isNotEmpty()) "$prefix$tableLabel" else tableLabel
    return "${safeFileStem(stem)}.xlsx"
}

private data class AiReportExtra(
    val samplingSite: String,
    val geoInfo: String,
    val climateSeason: String,
    val otherInfo: String,
)

private enum class CustomMetric(val label: String) {
    Density("密度"),
    Biomass("生物量"),
    H("H'"),
    D("D"),
    J("J"),
    Y("优势度"),
}

private val POINT_METRICS = setOf(CustomMetric.H, CustomMetric.D, CustomMetric.J)
private val SPECIES_METRICS = setOf(CustomMetric.Density, CustomMetric.Biomass, CustomMetric.Y)

private data class CalcDiffDialogState(
    val label: String,
    val report: CalcDiffReport,
)

private data class CalcDiffCsvRequest(
    val label: String,
    val report: CalcDiffReport,
)

private data class CalcDiffRow(
    val kind: String,
    val where: String,
    val internalValue: String,
    val apiValue: String,
)

private data class AbnormalPointSummary(
    val label: String,
    val summary: String,
    val count: Int,
    val order: Int,
)

private data class CompletenessScore(
    val score: Int,
    val totalChecks: Int,
    val missingCount: Int,
    val missingWetWeight: Int,
    val missingTaxonomy: Int,
    val missingVc: Int,
    val invalidVo: Int,
)

private fun calculateCompleteness(dataset: Dataset): CompletenessScore {
    var total = 0
    var missing = 0
    var missingWetWeight = 0
    var missingTaxonomy = 0
    var missingVc = 0
    var invalidVo = 0

    val pointsWithCounts = dataset.points.filter { p ->
        dataset.species.any { (it.countsByPointId[p.id] ?: 0) > 0 }
    }
    for (p in pointsWithCounts) {
        total += 1
        val vc = p.vConcMl
        if (vc == null || !vc.isFinite() || vc <= 0) {
            missing += 1
            missingVc += 1
        }

        total += 1
        if (!p.vOrigL.isFinite() || p.vOrigL <= 0) {
            missing += 1
            invalidVo += 1
        }
    }

    val usedSpecies = dataset.species.filter { anyCountPositive(it) }
    for (sp in usedSpecies) {
        total += 1
        if (sp.avgWetWeightMg == null) {
            missing += 1
            missingWetWeight += 1
        }

        total += 1
        if (sp.taxonomy.lvl1.isBlank()) {
            missing += 1
            missingTaxonomy += 1
        }
    }

    val score = if (total <= 0) {
        100
    } else {
        ((total - missing).toDouble() / total * 100.0).roundToInt().coerceIn(0, 100)
    }

    return CompletenessScore(
        score = score,
        totalChecks = total,
        missingCount = missing,
        missingWetWeight = missingWetWeight,
        missingTaxonomy = missingTaxonomy,
        missingVc = missingVc,
        invalidVo = invalidVo,
    )
}

private fun formatUpdatedAt(value: String?): String = value?.trim().takeIf { !it.isNullOrBlank() } ?: "—"

private fun formatLibraryMetaLines(meta: LibraryMeta?): List<String> {
    if (meta == null) return emptyList()
    return listOf(
        "分类库：内置 v${meta.taxonomyVersion}，自定义 ${meta.taxonomyCustomCount} 条，更新时间 ${formatUpdatedAt(meta.taxonomyUpdatedAt)}",
        "湿重库：内置 v${meta.wetWeightVersion}，自定义 ${meta.wetWeightCustomCount} 条，更新时间 ${formatUpdatedAt(meta.wetWeightUpdatedAt)}",
        "别名：${meta.aliasSource}，共 ${meta.aliasCount} 条，更新时间 ${formatUpdatedAt(meta.aliasUpdatedAt)}",
    )
}

private fun nowHuman(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())

private fun summarizeWarnings(warnings: List<String>, maxItems: Int = 4): List<String> {
    if (warnings.size <= maxItems) return warnings
    val rest = warnings.size - maxItems
    return warnings.take(maxItems) + "… 另有 $rest 条"
}

private fun buildCalcDiffRows(report: CalcDiffReport): List<CalcDiffRow> {
    return report.items.map { item ->
        val where = if (item.speciesName.isNullOrBlank()) {
            "点位 ${item.pointLabel}"
        } else {
            "点位 ${item.pointLabel} / 物种 ${item.speciesName}"
        }
        CalcDiffRow(
            kind = item.kind,
            where = where,
            internalValue = item.internalValue,
            apiValue = item.apiValue,
        )
    }
}

private fun csvEscape(value: String): String {
    val needsQuote = value.contains(',') || value.contains('\n') || value.contains('"')
    if (!needsQuote) return value
    return "\"" + value.replace("\"", "\"\"") + "\""
}

private fun buildCalcDiffCsv(report: CalcDiffReport, label: String): String {
    val sb = StringBuilder()
    sb.appendLine("算法,指标,点位/物种,内置,$label")
    for (item in report.items) {
        val where = if (item.speciesName.isNullOrBlank()) {
            "点位 ${item.pointLabel}"
        } else {
            "点位 ${item.pointLabel} / 物种 ${item.speciesName}"
        }
        sb.appendLine(
            listOf(
                label,
                item.kind,
                where,
                item.internalValue,
                item.apiValue,
            ).joinToString(",") { csvEscape(it) },
        )
    }
    return sb.toString().trimEnd()
}

private fun fmtNum(value: Double?, digits: Int = 6): String {
    if (value == null || !value.isFinite()) return ""
    return "%.${digits}f".format(value).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun buildCustomMetricsCsv(
    dataset: Dataset,
    calc: DatasetCalc,
    selected: List<CustomMetric>,
): String {
    val pointMetrics = selected.filter { POINT_METRICS.contains(it) }
    val speciesMetrics = selected.filter { SPECIES_METRICS.contains(it) }
    val sb = StringBuilder()

    if (pointMetrics.isNotEmpty()) {
        sb.appendLine("【点位指标】")
        sb.append("点位")
        for (m in pointMetrics) {
            sb.append(",").append(m.label)
        }
        sb.appendLine()
        for (p in dataset.points) {
            val idx = calc.pointIndexById[p.id]
            sb.append(csvEscape(p.label.ifBlank { p.id }))
            for (m in pointMetrics) {
                val value = when (m) {
                    CustomMetric.H -> fmtNum(idx?.H, 8)
                    CustomMetric.D -> fmtNum(idx?.D, 8)
                    CustomMetric.J -> fmtNum(idx?.J, 8)
                    else -> ""
                }
                sb.append(",").append(csvEscape(value))
            }
            sb.appendLine()
        }
        sb.appendLine()
    }

    if (speciesMetrics.isNotEmpty()) {
        sb.appendLine("【物种指标】（仅导出计数>0）")
        sb.append("点位,物种,计数")
        for (m in speciesMetrics) {
            sb.append(",").append(m.label)
        }
        sb.appendLine()
        for (sp in dataset.species) {
            val perMap = calc.perSpeciesByPoint[sp.id].orEmpty()
            for (p in dataset.points) {
                val per = perMap[p.id]
                val count = per?.count ?: 0
                if (count <= 0) continue
                sb.append(csvEscape(p.label.ifBlank { p.id }))
                sb.append(",").append(csvEscape(sp.nameCn.ifBlank { sp.id }))
                sb.append(",").append(count)
                for (m in speciesMetrics) {
                    val value = when (m) {
                        CustomMetric.Density -> fmtNum(per?.density, 8)
                        CustomMetric.Biomass -> when (val b = per?.biomass) {
                            is BiomassCell.Value -> fmtNum(b.mgPerL, 8)
                            BiomassCell.MissingWetWeight -> "未查到湿重"
                            else -> ""
                        }
                        CustomMetric.Y -> fmtNum(per?.Y, 8)
                        else -> ""
                    }
                    sb.append(",").append(csvEscape(value))
                }
                sb.appendLine()
            }
        }
    }

    return sb.toString().trimEnd()
}

private fun buildChecklistText(
    dataset: Dataset,
    visibleIssues: List<DataIssue>,
    ignoredCount: Int,
    libraryMeta: LibraryMeta?,
    completeness: CompletenessScore?,
): String {
    val err = visibleIssues.filter { it.level == IssueLevel.Error }
    val warn = visibleIssues.filter { it.level == IssueLevel.Warn }
    val info = visibleIssues.filter { it.level == IssueLevel.Info }
    val sb = StringBuilder()
    sb.appendLine("浮游动物一体化｜导出校验清单")
    sb.appendLine("数据集：${dataset.titlePrefix.trim().ifBlank { "（未命名）" }}")
    sb.appendLine("生成时间：${nowHuman()}")
    sb.appendLine("ERROR ${err.size} · WARN ${warn.size} · INFO ${info.size} · 已忽略 $ignoredCount")
    if (libraryMeta != null) {
        sb.appendLine("分类库：内置 v${libraryMeta.taxonomyVersion} / 自定义 ${libraryMeta.taxonomyCustomCount} 条 / 更新 ${formatUpdatedAt(libraryMeta.taxonomyUpdatedAt)}")
        sb.appendLine("湿重库：内置 v${libraryMeta.wetWeightVersion} / 自定义 ${libraryMeta.wetWeightCustomCount} 条 / 更新 ${formatUpdatedAt(libraryMeta.wetWeightUpdatedAt)}")
        sb.appendLine("别名：${libraryMeta.aliasSource} / ${libraryMeta.aliasCount} 条 / 更新 ${formatUpdatedAt(libraryMeta.aliasUpdatedAt)}")
    }
    if (completeness != null) {
        sb.appendLine("完整度：${completeness.score}%（检查 ${completeness.totalChecks} 项，缺失 ${completeness.missingCount} 项）")
        sb.appendLine(
            "缺失明细：湿重 ${completeness.missingWetWeight} · 分类 ${completeness.missingTaxonomy} · Vc ${completeness.missingVc} · Vo异常 ${completeness.invalidVo}",
        )
    }
    sb.appendLine()
    fun appendGroup(title: String, list: List<DataIssue>) {
        sb.appendLine("【$title】")
        if (list.isEmpty()) {
            sb.appendLine("（暂无）")
        } else {
            for (i in list) {
                val line = if (i.detail.isNotBlank()) "${i.title}：${i.detail}" else i.title
                sb.appendLine("- $line")
            }
        }
        sb.appendLine()
    }
    appendGroup("必须修复", err)
    appendGroup("可忽略", warn)
    appendGroup("提示", info)
    return sb.toString().trimEnd()
}

@Composable
private fun IssueRow(
    issue: DataIssue,
    allowIgnore: Boolean,
    onIgnore: (String) -> Unit,
) {
    val (icon, tint) = when (issue.level) {
        IssueLevel.Error -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
        IssueLevel.Warn -> Icons.Outlined.WarningAmber to MaterialTheme.colorScheme.tertiary
        IssueLevel.Info -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f)) {
            Text(issue.title, style = MaterialTheme.typography.bodyMedium, color = tint)
            if (issue.detail.isNotBlank()) {
                Text(
                    issue.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        if (allowIgnore) {
            TextButton(onClick = { onIgnore(issue.key) }) { Text("忽略") }
        }
    }
}

@Composable
private fun CalcDiffTable(
    label: String,
    rows: List<CalcDiffRow>,
    maxRows: Int,
    totalCount: Int? = null,
    onMore: (() -> Unit)?,
) {
    if (rows.isEmpty()) return
    val shown = if (rows.size > maxRows) rows.take(maxRows) else rows
    val total = totalCount ?: rows.size
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$label 差异示意（仅列不一致）", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("指标", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("点位/物种", modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("内置", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(label, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    for (row in shown) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(row.kind, modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall)
                            Text(row.where, modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                            Text(row.internalValue, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                            Text(row.apiValue, modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (total > shown.size && onMore != null) {
                Text(
                    "已展示 ${shown.size}/$total 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                TextButton(onClick = onMore) { Text("查看全部") }
            }
        }
    }
}

private fun fmt(value: Double?, digits: Int = 4): String {
    if (value == null || !value.isFinite()) return "（空）"
    return "%.${digits}f".format(value).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private data class PointMetricWidths(
    val vc: Int,
    val vo: Int,
    val h: Int,
    val d: Int,
    val j: Int,
    val n: Int,
    val s: Int,
)

private fun intDigits(value: Double?): Int {
    if (value == null || !value.isFinite()) return 1
    val v = abs(value)
    val intPart = v.toLong()
    return intPart.toString().length.coerceAtLeast(1)
}

private fun fmtAligned(value: Double?, digits: Int, intWidth: Int): String {
    val width = intWidth.coerceAtLeast(1)
    if (value == null || !value.isFinite()) {
        val total = width + if (digits > 0) 1 + digits else 0
        return "—".padStart(total, ' ')
    }
    val formatted = String.format(Locale.US, "%.${digits}f", value)
    val parts = formatted.split('.', limit = 2)
    val intPart = parts.firstOrNull().orEmpty().padStart(width, ' ')
    val fracPart = if (digits > 0) parts.getOrNull(1).orEmpty().padEnd(digits, '0') else ""
    return if (digits > 0) "$intPart.$fracPart" else intPart
}

private fun biomassTotal(cells: List<BiomassCell?>): Any? {
    if (cells.any { it == BiomassCell.MissingWetWeight }) return "未查到湿重"
    val nums = cells.mapNotNull { (it as? BiomassCell.Value)?.mgPerL }.filter { it.isFinite() }
    if (nums.isEmpty()) return null
    return nums.sum()
}

private fun buildDatasetSummaryText(ds: Dataset): String {
    val calc = calcDataset(ds)
    val sb = StringBuilder()
    sb.appendLine("数据集：${ds.titlePrefix.trim().ifBlank { "（未命名）" }}")
    sb.appendLine("更新时间：${ds.updatedAt}")
    sb.appendLine("采样点：${ds.points.size} 个；物种条目：${ds.species.size} 条")

    val missingWet = ds.species.count { it.avgWetWeightMg == null && anyCountPositive(it) }
    val missingVc = ds.points.count { it.vConcMl == null }
    sb.appendLine("缺失提示：有计数但缺湿重=${missingWet} 条；缺少浓缩体积(Vc)的点位=${missingVc} 个")
    sb.appendLine()

    sb.appendLine("采样点与指标（按点位）：")
    for (p in ds.points) {
        val idx = calc.pointIndexById[p.id]
        val densities = ds.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.density }
        val biomasses = ds.species.map { sp -> calc.perSpeciesByPoint[sp.id]?.get(p.id)?.biomass }
        val totalDensity = if (p.vConcMl == null || p.vOrigL <= 0) null else densities.filterNotNull().sum()
        val totalBiomass = if (p.vConcMl == null || p.vOrigL <= 0) null else biomassTotal(biomasses)

        sb.appendLine("点位 ${p.label.ifBlank { p.id }}：Vc=${fmt(p.vConcMl, 3)} mL；Vo=${fmt(p.vOrigL, 3)} L")
        sb.appendLine(
            "指标：N=${idx?.totalCount ?: 0}；S=${idx?.speciesCountS ?: 0}；H'=${fmt(idx?.H, 4)}；D=${fmt(idx?.D, 4)}；J=${fmt(idx?.J, 4)}；总密度=${fmt(totalDensity, 4)} ind/L；总生物量=${
                when (totalBiomass) {
                    null -> "（空）"
                    is Double -> "${fmt(totalBiomass, 4)} mg/L"
                    else -> totalBiomass.toString()
                }
            }",
        )

        val topY = ds.species.asSequence().mapNotNull { sp ->
            val per = calc.perSpeciesByPoint[sp.id]?.get(p.id) ?: return@mapNotNull null
            val y = per.Y ?: return@mapNotNull null
            if (per.count <= 0) return@mapNotNull null
            Triple(sp, y, per)
        }.sortedByDescending { it.second }.take(6).toList()

        if (topY.isNotEmpty()) {
            sb.appendLine("优势度 Top（按 Y 从高到低，最多 6 条）：")
            for ((i, entry) in topY.withIndex()) {
                val (sp, y, per) = entry
                val tag = if (per.isDominant == true) "优势种" else "—"
                val lvl1 = normalizeLvl1Name(sp.taxonomy.lvl1).ifBlank { "（未分类）" }
                sb.appendLine("${i + 1}) ${sp.nameCn.ifBlank { sp.id }}（$lvl1），n=${per.count}，Y=${fmt(y, 6)}，$tag")
            }
        }
        sb.appendLine()
    }

    sb.appendLine("全局 Top 物种（按总计数）：")
    val topSpecies = ds.species.map { sp -> sp to sp.countsByPointId.values.sum() }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(15)

    if (topSpecies.isEmpty()) {
        sb.appendLine("（暂无计数>0的物种）")
    } else {
        for ((i, pair) in topSpecies.withIndex()) {
            val (sp, sum) = pair
            val lvl1 = normalizeLvl1Name(sp.taxonomy.lvl1).ifBlank { "（未分类）" }
            sb.appendLine("${i + 1}) ${sp.nameCn.ifBlank { sp.id }}（$lvl1）：总计数=$sum")
        }
    }

    sb.appendLine()
    sb.appendLine("计算公式（纯文本）：")
    sb.appendLine("密度：density(ind/L) = (n / 1.3) * (Vc / Vo)")
    sb.appendLine("生物量：biomass(mg/L) = density * wetWeightMg")
    sb.appendLine("香农指数：H' = -sum(p_i * ln(p_i)), p_i = n_i / N")
    sb.appendLine("均匀度：J = H' / ln(S)")
    sb.appendLine("丰富度：D = (S - 1) / ln(N)")
    sb.appendLine("优势度：Y_i = (n_i / N) * f_i, f_i = appearPoints / totalPoints；若 Y > 0.02 视为优势种")

    return sb.toString().trimEnd()
}

private fun buildAiReportPrompt(ds: Dataset, extra: AiReportExtra): String {
    val sb = StringBuilder()
    sb.appendLine("你是生态学与浮游动物学助手。请基于我提供的数据做“浮游动物初步评价”分析报告。")
    sb.appendLine("要求：")
    sb.appendLine("1) 只基于我提供的数据与公式判断；不确定必须直说；不得编造引用。")
    sb.appendLine("2) 输出必须是【纯文本中文报告】，不要输出 Markdown/表格/代码/JSON；不要出现奇怪符号（例如 ###、```、*、|、> 等）。")
    sb.appendLine("3) 用“一、二、三 …”分节；语言简洁、通顺；数字保留 3-4 位即可。")
    sb.appendLine("4) 在评价前先结合我提供的补充资料（若为空则说明缺失）。")
    sb.appendLine("5) 结尾给出：总体结论、主要依据（对应指标）、风险与局限、建议下一步需要补充的现场信息。")
    sb.appendLine()

    sb.appendLine("补充资料（可为空）：")
    sb.appendLine("- 采样地点：${extra.samplingSite.trim().ifBlank { "（未提供）" }}")
    sb.appendLine("- 地理信息：${extra.geoInfo.trim().ifBlank { "（未提供）" }}")
    sb.appendLine("- 气候/时节：${extra.climateSeason.trim().ifBlank { "（未提供）" }}")
    sb.appendLine("- 其他信息：${extra.otherInfo.trim().ifBlank { "（未提供）" }}")
    sb.appendLine()

    sb.appendLine("数据摘要如下：")
    sb.appendLine(buildDatasetSummaryText(ds))
    sb.appendLine()
    sb.appendLine("现在开始输出报告正文。")

    return sb.toString().trimEnd()
}

private suspend fun writeBytes(context: android.content.Context, uri: Uri, bytes: ByteArray) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val tempFile = File.createTempFile("saf-", ".tmp", dir)
    try {
        withContext(Dispatchers.IO) {
            tempFile.outputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
        }
        writeFileToSafAtomic(context, uri, tempFile)
        val app = context.applicationContext as? PlanktonApplication
        app?.preferences?.setLastExport(uri.toString(), nowIso())
    } finally {
        runCatching { tempFile.delete() }
    }
}

private suspend fun writeToCache(context: android.content.Context, fileName: String, bytes: ByteArray): Uri {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName)
    withContext(Dispatchers.IO) {
        file.outputStream().use { out ->
            out.write(bytes)
            out.flush()
        }
    }
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}

private fun treeToDocumentUri(treeUri: Uri): Uri {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
}

private fun withSuffixBeforeExt(fileName: String, suffix: String): String {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || dot == fileName.length - 1) return "$fileName$suffix"
    val stem = fileName.substring(0, dot)
    val ext = fileName.substring(dot)
    return "$stem$suffix$ext"
}

private suspend fun createUniqueDocument(
    contentResolver: ContentResolver,
    parentDocumentUri: Uri,
    mime: String,
    displayName: String,
): Uri = withContext(Dispatchers.IO) {
    var lastErr: Throwable? = null
    for (i in 0..30) {
        val name = if (i == 0) displayName else withSuffixBeforeExt(displayName, "(${i + 1})")
        val uri = runCatching { DocumentsContract.createDocument(contentResolver, parentDocumentUri, mime, name) }.getOrElse {
            lastErr = it
            null
        }
        if (uri != null) return@withContext uri
    }
    throw (lastErr ?: IllegalStateException("无法创建导出文件：$displayName"))
}

private fun Settings.exportLatinOptions(): ExportLatinOptions {
    return ExportLatinOptions(
        lvl1 = exportLatinLvl1,
        lvl2 = exportLatinLvl2,
        lvl3 = exportLatinLvl3,
        lvl4 = exportLatinLvl4,
        lvl5 = exportLatinLvl5,
        species = exportLatinSpecies,
    )
}

@Composable
fun PreviewScreen(
    viewModel: MainViewModel,
    padding: PaddingValues,
    onOpenCharts: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as PlanktonApplication
    val wetWeightRepo = app.wetWeightRepository
    val taxonomyRepo = app.taxonomyRepository
    val taxonomyOverrideRepo = app.taxonomyOverrideRepository
    val aliasRepo = app.aliasRepository
    val contentResolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val exporter = remember { ExcelTemplateExporter(context.applicationContext) }

    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val previewCommand by viewModel.previewCommand.collectAsStateWithLifecycle()
    val latestSettings by rememberUpdatedState(settings)
    val lastExportUri by app.preferences.lastExportUri.collectAsStateWithLifecycle(initialValue = null)
    val lastExportAt by app.preferences.lastExportAt.collectAsStateWithLifecycle(initialValue = null)

    val dialogColor = if (settings.glassEffectEnabled) GlassWhite else MaterialTheme.colorScheme.surface
    val dialogShape = RoundedCornerShape(24.dp)

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }
    val latestDs by rememberUpdatedState(ds)

    LaunchedEffect(ds.id, previewState.datasetId) {
        if (previewState.datasetId != ds.id) {
            viewModel.resetPreviewState(ds.id)
        }
    }

    val internalCalc = remember(ds) { calcDataset(ds) }
    val allIssues = remember(ds) { validateDataset(ds) }
    val ignoredKeys = remember(ds.ignoredIssueKeys) { ds.ignoredIssueKeys.toSet() }
    val issues = remember(allIssues, ignoredKeys) { allIssues.filter { it.key !in ignoredKeys } }
    val errCount = remember(issues) { issues.count { it.level == IssueLevel.Error } }
    val warnCount = remember(issues) { issues.count { it.level == IssueLevel.Warn } }
    val infoCount = remember(issues) { issues.count { it.level == IssueLevel.Info } }
    val ignoredCount = remember(allIssues, issues) { (allIssues.size - issues.size).coerceAtLeast(0) }

    val missing = remember(ds) { ds.species.filter { it.avgWetWeightMg == null && anyCountPositive(it) } }
    val completeness = remember(ds) { calculateCompleteness(ds) }
    val numericStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val calcState = previewState.calcCheck
    val reportState = previewState.report
    val reportBusy = reportState.busy
    val reportProgress = reportState.progress
    val reportError = reportState.error
    val reportText1 = reportState.text1
    val reportText2 = reportState.text2
    val calcCheckBusy = calcState.busy
    val calcCheckMessage = calcState.message
    val calcCheckError = calcState.error
    val apiCalc1 = calcState.apiCalc1
    val apiCalc2 = calcState.apiCalc2
    val apiCalcWarn1 = calcState.apiWarn1
    val apiCalcWarn2 = calcState.apiWarn2
    val diffReport1 = calcState.diffReport1
    val diffReport2 = calcState.diffReport2
    var queryTargetId by remember { mutableStateOf<String?>(null) }
    val queryTarget = ds.species.firstOrNull { it.id == queryTargetId }

    var libraryMeta by remember { mutableStateOf<LibraryMeta?>(null) }
    var libraryMetaError by remember { mutableStateOf<String?>(null) }

    val pointSummaries = remember(ds, internalCalc) {
        ds.points.map { p ->
            val idx = internalCalc.pointIndexById[p.id]
            Triple(p.label.ifBlank { p.id }, idx?.totalCount ?: 0, idx?.speciesCountS ?: 0)
        }
    }
    val pointMetricWidths = remember(ds, internalCalc) {
        fun maxInt(values: List<Double?>): Int {
            val max = values.maxOfOrNull { intDigits(it) } ?: 1
            return max.coerceAtLeast(1)
        }
        val vcVals = ds.points.map { it.vConcMl }
        val voVals = ds.points.map { it.vOrigL }
        val hVals = ds.points.map { internalCalc.pointIndexById[it.id]?.H }
        val dVals = ds.points.map { internalCalc.pointIndexById[it.id]?.D }
        val jVals = ds.points.map { internalCalc.pointIndexById[it.id]?.J }
        val nVals = ds.points.map { internalCalc.pointIndexById[it.id]?.totalCount?.toDouble() }
        val sVals = ds.points.map { internalCalc.pointIndexById[it.id]?.speciesCountS?.toDouble() }
        PointMetricWidths(
            vc = maxInt(vcVals),
            vo = maxInt(voVals),
            h = maxInt(hVals),
            d = maxInt(dVals),
            j = maxInt(jVals),
            n = maxInt(nVals),
            s = maxInt(sVals),
        )
    }
    val dominantSummary = remember(ds, internalCalc) {
        val result = mutableListOf<Pair<String, Int>>()
        for (sp in ds.species) {
            val per = internalCalc.perSpeciesByPoint[sp.id].orEmpty()
            val count = per.values.count { it.isDominant == true }
            if (count > 0) result += (sp.nameCn.ifBlank { sp.id } to count)
        }
        result.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(12)
    }
    val abnormalPointSummary = remember(issues, ds.points) {
        val order = ds.points.withIndex().associate { it.value.id to it.index }
        issues.asSequence()
            .filter { it.pointId != null && it.title != "未发现明显问题" }
            .groupBy { it.pointId.orEmpty() }
            .map { (pid, list) ->
                val label = ds.points.firstOrNull { it.id == pid }?.label?.ifBlank { pid } ?: pid
                val titles = list.map { it.title }.distinct().joinToString("；")
                AbnormalPointSummary(label = label, summary = titles, count = list.size, order = order[pid] ?: Int.MAX_VALUE)
            }
            .sortedWith(compareBy<AbnormalPointSummary> { it.order }.thenBy { it.label })
            .take(12)
            .toList()
    }
    val previewSummaryLines = remember(completeness, dominantSummary, abnormalPointSummary, missing) {
        buildList {
            add("完整度 ${completeness.score}% · 缺失湿重 ${completeness.missingWetWeight} · 缺失分类 ${completeness.missingTaxonomy}")
            if (dominantSummary.isNotEmpty()) {
                val top = dominantSummary.take(3).joinToString("、") { "${it.first}(${it.second})" }
                add("优势种 Top：$top")
            }
            if (abnormalPointSummary.isNotEmpty()) {
                val points = abnormalPointSummary.take(3).joinToString("、") { it.label }
                add("异常点位：$points")
            }
            if (missing.isNotEmpty()) {
                add("缺失湿重条目：${missing.size}")
            }
        }
    }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var previewKind by remember { mutableStateOf<Int?>(null) } // 1=表1,2=表2,3=简表1,4=简表2
    var previewLoading by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewData by remember { mutableStateOf<WorkbookPreview?>(null) }

    val workManager = remember { WorkManager.getInstance(context) }
    var exportWorkIdTable1 by remember { mutableStateOf<UUID?>(null) }
    var exportWorkIdTable2 by remember { mutableStateOf<UUID?>(null) }
    var exportWorkIdTable3 by remember { mutableStateOf<UUID?>(null) }
    var exportWorkIdTable4 by remember { mutableStateOf<UUID?>(null) }
    val exportWorkInfo1Flow = remember(exportWorkIdTable1) {
        exportWorkIdTable1?.let { workManager.getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val exportWorkInfo2Flow = remember(exportWorkIdTable2) {
        exportWorkIdTable2?.let { workManager.getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val exportWorkInfo3Flow = remember(exportWorkIdTable3) {
        exportWorkIdTable3?.let { workManager.getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val exportWorkInfo4Flow = remember(exportWorkIdTable4) {
        exportWorkIdTable4?.let { workManager.getWorkInfoByIdFlow(it) } ?: flowOf(null)
    }
    val exportWorkInfo1 by exportWorkInfo1Flow.collectAsStateWithLifecycle(initialValue = null)
    val exportWorkInfo2 by exportWorkInfo2Flow.collectAsStateWithLifecycle(initialValue = null)
        val exportWorkInfo3 by exportWorkInfo3Flow.collectAsStateWithLifecycle(initialValue = null)
        val exportWorkInfo4 by exportWorkInfo4Flow.collectAsStateWithLifecycle(initialValue = null)

    // --- AI report ---
    var report1Expanded by remember { mutableStateOf(false) }
    var report2Expanded by remember { mutableStateOf(false) }

    var pendingDocxApiTag by remember { mutableStateOf<String?>(null) }
    var pendingDocxText by remember { mutableStateOf<String?>(null) }
    var pendingBatchApi1Text by remember { mutableStateOf<String?>(null) }
    var pendingBatchApi2Text by remember { mutableStateOf<String?>(null) }

    // --- AI calc verification ---
    var diffExportBusy by remember { mutableStateOf(false) }
    var pendingCalcDiffCsv by remember { mutableStateOf<CalcDiffCsvRequest?>(null) }
    var diffDialog by remember { mutableStateOf<CalcDiffDialogState?>(null) }

    val allCustomMetrics = remember { CustomMetric.values().toList() }
    var customMetrics by remember { mutableStateOf(allCustomMetrics) }
    var customExportBusy by remember { mutableStateOf(false) }
    var pendingCustomExportMetrics by remember { mutableStateOf<List<CustomMetric>?>(null) }
    var checklistUri by remember { mutableStateOf<Uri?>(null) }
    var checklistBusy by remember { mutableStateOf(false) }

    suspend fun generateChecklist(reason: String): Uri {
        val text = buildChecklistText(latestDs, issues, ignoredCount, libraryMeta, completeness)
        val filename = safeFileStem("${latestDs.titlePrefix.trim().ifBlank { "数据集" }}-校验清单-$reason-${nowHuman()}") + ".txt"
        return writeToCache(context, filename, text.toByteArray(Charsets.UTF_8))
    }

    LaunchedEffect(ds.id) {
        diffDialog = null
        checklistUri = null
        report1Expanded = false
        report2Expanded = false
    }

    LaunchedEffect(ds.id) {
        libraryMetaError = null
        val res = runCatching {
            val taxonomyVersion = taxonomyRepo.getBuiltinVersion()
            val taxonomyCustom = taxonomyOverrideRepo.getCustomMeta()
            val wetWeightVersion = wetWeightRepo.getBuiltinVersion()
            val wetWeightCustom = wetWeightRepo.getCustomMeta()
            val aliasMeta = aliasRepo.getMeta()
            LibraryMeta(
                taxonomyVersion = taxonomyVersion,
                taxonomyCustomCount = taxonomyCustom.count,
                taxonomyUpdatedAt = taxonomyCustom.updatedAt,
                wetWeightVersion = wetWeightVersion,
                wetWeightCustomCount = wetWeightCustom.count,
                wetWeightUpdatedAt = wetWeightCustom.updatedAt,
                aliasCount = aliasMeta.count,
                aliasUpdatedAt = aliasMeta.updatedAt,
            )
        }
        libraryMeta = res.getOrNull()
        libraryMetaError = res.exceptionOrNull()?.message
    }

    var handledWorkIdTable1 by remember { mutableStateOf<UUID?>(null) }
    var handledWorkIdTable2 by remember { mutableStateOf<UUID?>(null) }
    var handledWorkIdTable3 by remember { mutableStateOf<UUID?>(null) }
    var handledWorkIdTable4 by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(exportWorkInfo1?.id, exportWorkInfo1?.state) {
        val info = exportWorkInfo1 ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        if (handledWorkIdTable1 == info.id) return@LaunchedEffect
        handledWorkIdTable1 = info.id
        val error = info.outputData.getString(ExcelExportWorker.KEY_ERROR)
        if (info.state == WorkInfo.State.SUCCEEDED) {
            message = "已导出表1.xlsx"
            checklistUri = try {
                generateChecklist("导出表1")
            } catch (e: Exception) {
                AppLogger.logError(context, "Checklist", "生成校验清单失败（表1）", e)
                null
            }
        } else if (!error.isNullOrBlank()) {
            message = "导出表1失败：$error"
        } else {
            message = "导出表1失败：未知错误"
        }
    }

    LaunchedEffect(exportWorkInfo2?.id, exportWorkInfo2?.state) {
        val info = exportWorkInfo2 ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        if (handledWorkIdTable2 == info.id) return@LaunchedEffect
        handledWorkIdTable2 = info.id
        val error = info.outputData.getString(ExcelExportWorker.KEY_ERROR)
        if (info.state == WorkInfo.State.SUCCEEDED) {
            message = "已导出表2.xlsx"
            checklistUri = try {
                generateChecklist("导出表2")
            } catch (e: Exception) {
                AppLogger.logError(context, "Checklist", "生成校验清单失败（表2）", e)
                null
            }
        } else if (!error.isNullOrBlank()) {
            message = "导出表2失败：$error"
        } else {
            message = "导出表2失败：未知错误"
        }
    }

    LaunchedEffect(exportWorkInfo3?.id, exportWorkInfo3?.state) {
        val info = exportWorkInfo3 ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        if (handledWorkIdTable3 == info.id) return@LaunchedEffect
        handledWorkIdTable3 = info.id
        val error = info.outputData.getString(ExcelExportWorker.KEY_ERROR)
        if (info.state == WorkInfo.State.SUCCEEDED) {
            message = "已导出表1简表.xlsx"
            checklistUri = try {
                generateChecklist("导出表1简表")
            } catch (e: Exception) {
                AppLogger.logError(context, "Checklist", "生成校验清单失败（表1简表）", e)
                null
            }
        } else if (!error.isNullOrBlank()) {
            message = "导出表1简表失败：$error"
        } else {
            message = "导出表1简表失败：未知错误"
        }
    }

    LaunchedEffect(exportWorkInfo4?.id, exportWorkInfo4?.state) {
        val info = exportWorkInfo4 ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        if (handledWorkIdTable4 == info.id) return@LaunchedEffect
        handledWorkIdTable4 = info.id
        val error = info.outputData.getString(ExcelExportWorker.KEY_ERROR)
        if (info.state == WorkInfo.State.SUCCEEDED) {
            message = "已导出表2简表.xlsx"
            checklistUri = try {
                generateChecklist("导出表2简表")
            } catch (e: Exception) {
                AppLogger.logError(context, "Checklist", "生成校验清单失败（表2简表）", e)
                null
            }
        } else if (!error.isNullOrBlank()) {
            message = "导出表2简表失败：$error"
        } else {
            message = "导出表2简表失败：未知错误"
        }
    }

    fun selectedCalcOverride(): DatasetCalc? {
        return when (calcState.calcSource) {
            CalcSource.Internal -> null
            CalcSource.Api1 -> apiCalc1
            CalcSource.Api2 -> apiCalc2
        }
    }

    fun buildCalcOverrideJson(calc: DatasetCalc?): String? {
        val raw = calc ?: return null
        val output = buildCalcOutputFromCalc(latestDs, raw)
        return AppJson.encodeToString(CalcOutput.serializer(), output)
    }

    fun apiLabel(api: ApiConfig, fallback: String): String {
        return api.name.trim().ifBlank { fallback }
    }

    fun ignoreIssues(keys: List<String>) {
        if (keys.isEmpty()) return
        viewModel.updateCurrentDataset { cur ->
            val next = (cur.ignoredIssueKeys + keys).distinct()
            cur.copy(ignoredIssueKeys = next)
        }
    }

    fun clearIgnoredIssues() {
        viewModel.updateCurrentDataset { cur ->
            if (cur.ignoredIssueKeys.isEmpty()) cur else cur.copy(ignoredIssueKeys = emptyList())
        }
    }

    fun enqueueExcelExport(table: Int, uri: Uri) {
        val calcJson = buildCalcOverrideJson(selectedCalcOverride())
        val data = Data.Builder()
            .putString(ExcelExportWorker.KEY_DATASET_ID, latestDs.id)
            .putString(ExcelExportWorker.KEY_OUTPUT_URI, uri.toString())
            .putInt(ExcelExportWorker.KEY_TABLE, table)
            .putBoolean(ExcelExportWorker.KEY_LATIN_LVL1, latestSettings.exportLatinLvl1)
            .putBoolean(ExcelExportWorker.KEY_LATIN_LVL2, latestSettings.exportLatinLvl2)
            .putBoolean(ExcelExportWorker.KEY_LATIN_LVL3, latestSettings.exportLatinLvl3)
            .putBoolean(ExcelExportWorker.KEY_LATIN_LVL4, latestSettings.exportLatinLvl4)
            .putBoolean(ExcelExportWorker.KEY_LATIN_LVL5, latestSettings.exportLatinLvl5)
            .putBoolean(ExcelExportWorker.KEY_LATIN_SPECIES, latestSettings.exportLatinSpecies)
        if (!calcJson.isNullOrBlank()) {
            data.putString(ExcelExportWorker.KEY_CALC_OUTPUT_JSON, calcJson)
        }
        val request = OneTimeWorkRequestBuilder<ExcelExportWorker>()
            .setInputData(data.build())
            .build()
        workManager.enqueue(request)
        when (table) {
            1 -> exportWorkIdTable1 = request.id
            2 -> exportWorkIdTable2 = request.id
            3 -> exportWorkIdTable3 = request.id
            4 -> exportWorkIdTable4 = request.id
        }
    }

    fun startPreview(kind: Int) {
        previewKind = kind
        previewLoading = true
        previewError = null
        previewData = null
        scope.launch {
            val res = runCatching {
                val opts = latestSettings.exportLatinOptions()
                val override = selectedCalcOverride()
                val latinNameMap = buildTaxonomyLatinOverrides(taxonomyOverrideRepo.getCustomEntries())
                val out = when (kind) {
                    1 -> exporter.exportTable1(latestDs, opts, override, libraryMeta, latinNameMap)
                    2 -> exporter.exportTable2(latestDs, opts, override, libraryMeta, latinNameMap)
                    3 -> exporter.exportSimpleCountTable(latestDs, libraryMeta)
                    4 -> exporter.exportSimpleTable2(latestDs, libraryMeta)
                    else -> error("未知预览类型：$kind")
                }
                buildWorkbookPreview(out.bytes)
            }
            res.fold(
                onSuccess = { previewData = it },
                onFailure = {
                    previewError = it.message ?: it.toString()
                    AppLogger.logError(context, "WorkbookPreview", "预览表${kind}失败", it)
                },
            )
            previewLoading = false
        }
    }

    val exportTable1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_XLSX),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModel.createSnapshotNow(latestDs, "导出表1")
        enqueueExcelExport(1, uri)
        message = "已开始后台导出（表1）"
    }

    val exportTable2Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_XLSX),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModel.createSnapshotNow(latestDs, "导出表2")
        enqueueExcelExport(2, uri)
        message = "已开始后台导出（表2）"
    }

    val exportSimpleTable1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_XLSX),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModel.createSnapshotNow(latestDs, "导出表1简表")
        enqueueExcelExport(3, uri)
        message = "已开始后台导出（表1简表）"
    }

    val exportSimpleTable2Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_XLSX),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModel.createSnapshotNow(latestDs, "导出表2简表")
        enqueueExcelExport(4, uri)
        message = "已开始后台导出（表2简表）"
    }

    LaunchedEffect(previewCommand) {
        val command = previewCommand ?: return@LaunchedEffect
        when (command) {
            PreviewCommand.ExportTable1 -> {
                val name = defaultXlsxName(latestDs.titlePrefix, "表1")
                exportTable1Launcher.launch(name)
            }
            PreviewCommand.ExportTable2 -> {
                val name = defaultXlsxName(latestDs.titlePrefix, "表2")
                exportTable2Launcher.launch(name)
            }
            PreviewCommand.PreviewTable1 -> {
                startPreview(1)
            }
            PreviewCommand.PreviewTable2 -> {
                startPreview(2)
            }
        }
        viewModel.clearPreviewCommand()
    }

    val exportCalcDiffCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val req = pendingCalcDiffCsv
        pendingCalcDiffCsv = null
        if (uri == null || req == null) return@rememberLauncherForActivityResult
        diffExportBusy = true
        scope.launch {
            val res = runCatching {
                val csv = buildCalcDiffCsv(req.report, req.label)
                writeBytes(context, uri, csv.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(
                context,
                res.fold(onSuccess = { "已导出差异明细" }, onFailure = { "导出失败：${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
            diffExportBusy = false
        }
    }

    val exportCustomMetricsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val metrics = pendingCustomExportMetrics
        pendingCustomExportMetrics = null
        if (uri == null || metrics.isNullOrEmpty()) return@rememberLauncherForActivityResult
        customExportBusy = true
        scope.launch {
            val res = runCatching {
                viewModel.createSnapshotNow(latestDs, "自定义指标导出")
                val csv = buildCustomMetricsCsv(latestDs, internalCalc, metrics)
                writeBytes(context, uri, csv.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(
                context,
                res.fold(onSuccess = { "已导出自定义指标" }, onFailure = { "导出失败：${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
            if (res.isSuccess) {
                checklistUri = generateChecklist("自定义指标导出")
            }
            customExportBusy = false
        }
    }

    fun requestCustomExport() {
        if (customMetrics.isEmpty()) {
            Toast.makeText(context, "请至少选择一个指标", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCustomExportMetrics = customMetrics
        val base = "${latestDs.titlePrefix.trim().ifBlank { "数据集" }}-自定义指标"
        exportCustomMetricsLauncher.launch("${safeFileStem(base)}.csv")
    }

    suspend fun buildReportDocxBytes(apiTag: String, aiText: String): ByteArray = withContext(Dispatchers.Default) {
        val extra = AiReportExtra(
            samplingSite = reportState.samplingSite,
            geoInfo = reportState.geoInfo,
            climateSeason = reportState.climateSeason,
            otherInfo = reportState.otherInfo,
        )
        val extraText = buildString {
            appendLine("采样地点：${extra.samplingSite.trim().ifBlank { "（未提供）" }}")
            appendLine("地理信息：${extra.geoInfo.trim().ifBlank { "（未提供）" }}")
            appendLine("气候/时节：${extra.climateSeason.trim().ifBlank { "（未提供）" }}")
            appendLine("其他信息：${extra.otherInfo.trim().ifBlank { "（未提供）" }}")
        }.trimEnd()

        DocxReportExporter.export(
            title = "$apiTag 浮游动物初步评价",
            subtitleLines = listOf(
                "生成时间：${nowHuman()}",
                "数据集：${latestDs.titlePrefix.trim().ifBlank { "（未命名）" }}",
            ),
            sections = listOf(
                DocxReportExporter.Section(title = "补充资料", body = extraText),
                DocxReportExporter.Section(title = "数据摘要", body = buildDatasetSummaryText(latestDs)),
                DocxReportExporter.Section(title = "AI 初步评价", body = aiText),
            ),
        )
    }

    fun runCalcCheck() {
        viewModel.updatePreviewCalcState { it.copy(error = null, message = null) }
        viewModel.startPreviewCalcCheck(latestDs, settings)
    }

    fun requestCalcDiffExport(label: String, apiCalc: DatasetCalc) {
        val report = diffCalc(latestDs, internalCalc, apiCalc, maxItems = Int.MAX_VALUE)
        if (report.mismatchCount <= 0) {
            Toast.makeText(context, "未发现差异，无需导出", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCalcDiffCsv = CalcDiffCsvRequest(label = label, report = report)
        val base = "${latestDs.titlePrefix.trim().ifBlank { "数据集" }}-计算核对差异-$label"
        exportCalcDiffCsvLauncher.launch("${safeFileStem(base)}.csv")
    }

    fun startAiReport() {
        report1Expanded = false
        report2Expanded = false
        viewModel.updatePreviewReportState { it.copy(error = null) }

        val extra = AiReportExtra(
            samplingSite = reportState.samplingSite,
            geoInfo = reportState.geoInfo,
            climateSeason = reportState.climateSeason,
            otherInfo = reportState.otherInfo,
        )
        val prompt = buildAiReportPrompt(latestDs, extra)
        viewModel.startPreviewReport(prompt, settings)
    }

    val exportReportDocxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_DOCX),
    ) { uri ->
        val apiTag = pendingDocxApiTag
        val aiText = pendingDocxText
        pendingDocxApiTag = null
        pendingDocxText = null
        if (uri == null || apiTag == null || aiText.isNullOrBlank()) return@rememberLauncherForActivityResult

        viewModel.updatePreviewReportState { it.copy(busy = true, progress = "导出中…") }
        scope.launch {
            val res = runCatching {
                val bytes = buildReportDocxBytes(apiTag, aiText)
                writeBytes(context, uri, bytes)
            }
            message = res.fold(
                onSuccess = { "已导出：${defaultAiReportName(apiTag)}" },
                onFailure = { "导出失败：${it.message}" },
            )
            viewModel.updatePreviewReportState { it.copy(busy = false, progress = null) }
        }
    }

    val exportReportBatchDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val t1 = pendingBatchApi1Text
        val t2 = pendingBatchApi2Text
        pendingBatchApi1Text = null
        pendingBatchApi2Text = null
        if (uri == null || (t1.isNullOrBlank() && t2.isNullOrBlank())) return@rememberLauncherForActivityResult

        viewModel.updatePreviewReportState { it.copy(busy = true, progress = "导出中…") }
        scope.launch {
            val res = runCatching {
                val parentDoc = treeToDocumentUri(uri)
                var exported = 0
                if (!t1.isNullOrBlank()) {
                    val outUri = createUniqueDocument(contentResolver, parentDoc, MIME_DOCX, defaultAiReportName("API1"))
                    val bytes = buildReportDocxBytes("API1", t1)
                    writeBytes(context, outUri, bytes)
                    exported += 1
                }
                if (!t2.isNullOrBlank()) {
                    val outUri = createUniqueDocument(contentResolver, parentDoc, MIME_DOCX, defaultAiReportName("API2"))
                    val bytes = buildReportDocxBytes("API2", t2)
                    writeBytes(context, outUri, bytes)
                    exported += 1
                }
                exported
            }
            message = res.fold(
                onSuccess = { "已导出 $it 份报告（docx）" },
                onFailure = { "导出失败：${it.message}" },
            )
            viewModel.updatePreviewReportState { it.copy(busy = false, progress = null) }
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
            Text("预览与导出", style = MaterialTheme.typography.titleLarge)
            Text(
                "建议先确保点位、体积、计数与湿重数据完整，再导出表1/表2。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("结果可视化", style = MaterialTheme.typography.titleMedium)
                Text(
                    "查看点位指标与 Top 物种图表（用于快速核对；Excel 导出不受影响）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Button(onClick = onOpenCharts, modifier = Modifier.fillMaxWidth()) { Text("打开图表") }
            }
        }

        if (message != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(message.orEmpty(), modifier = Modifier.padding(12.dp))
            }
        }

        if (!settings.aiUiHidden) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("结果分析报告（AI）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "可补充采样背景信息，然后选择 API1/2 生成“浮游动物初步评价”报告，并导出为 docx 文档。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = reportState.useApi1,
                            onCheckedChange = { v -> viewModel.updatePreviewReportState { it.copy(useApi1 = v) } },
                            enabled = !reportBusy,
                        )
                        Text("使用 API1", modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = reportState.useApi2,
                            onCheckedChange = { v -> viewModel.updatePreviewReportState { it.copy(useApi2 = v) } },
                            enabled = !reportBusy,
                        )
                        Text("使用 API2")
                    }

                    OutlinedTextField(
                        value = reportState.samplingSite,
                        onValueChange = { v -> viewModel.updatePreviewReportState { it.copy(samplingSite = v) } },
                        enabled = !reportBusy,
                        label = { Text("采样地点（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reportState.geoInfo,
                        onValueChange = { v -> viewModel.updatePreviewReportState { it.copy(geoInfo = v) } },
                        enabled = !reportBusy,
                        label = { Text("地理信息（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reportState.climateSeason,
                        onValueChange = { v -> viewModel.updatePreviewReportState { it.copy(climateSeason = v) } },
                        enabled = !reportBusy,
                        label = { Text("气候/时节（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = reportState.otherInfo,
                        onValueChange = { v -> viewModel.updatePreviewReportState { it.copy(otherInfo = v) } },
                        enabled = !reportBusy,
                        label = { Text("其他补充资料（可选，多行）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )

                    Button(
                        onClick = ::startAiReport,
                        enabled = !reportBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("生成报告") }

                    if (reportBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (reportProgress != null) {
                        Text(reportProgress.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (reportError != null) {
                        Text(
                            reportError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    OutlinedButton(
                        enabled = reportBusy || !reportText1.isNullOrBlank() || !reportText2.isNullOrBlank() || reportError != null,
                        onClick = {
                            report1Expanded = false
                            report2Expanded = false
                            viewModel.clearPreviewReportResults()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除回复") }

                    val canBatch = !reportText1.isNullOrBlank() && !reportText2.isNullOrBlank()
                    if (canBatch) {
                        OutlinedButton(
                            enabled = !reportBusy,
                            onClick = {
                                pendingBatchApi1Text = reportText1
                                pendingBatchApi2Text = reportText2
                                exportReportBatchDirLauncher.launch(null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("一键导出两个报告（选择目录）") }
                    }
                }
            }

            if (!reportText1.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("API1 报告", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(
                                reportText1.orEmpty(),
                                maxLines = if (report1Expanded) Int.MAX_VALUE else 16,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { report1Expanded = !report1Expanded },
                                enabled = !reportBusy,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (report1Expanded) "收起" else "展开") }
                            Button(
                                onClick = {
                                    pendingDocxApiTag = "API1"
                                    pendingDocxText = reportText1
                                    exportReportDocxLauncher.launch(defaultAiReportName("API1"))
                                },
                                enabled = !reportBusy,
                                modifier = Modifier.weight(1f),
                            ) { Text("导出 docx") }
                        }
                    }
                }
            }

            if (!reportText2.isNullOrBlank()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("API2 报告", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(
                                reportText2.orEmpty(),
                                maxLines = if (report2Expanded) Int.MAX_VALUE else 16,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { report2Expanded = !report2Expanded },
                                enabled = !reportBusy,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (report2Expanded) "收起" else "展开") }
                            Button(
                                onClick = {
                                    pendingDocxApiTag = "API2"
                                    pendingDocxText = reportText2
                                    exportReportDocxLauncher.launch(defaultAiReportName("API2"))
                                },
                                enabled = !reportBusy,
                                modifier = Modifier.weight(1f),
                            ) { Text("导出 docx") }
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("采样点汇总", style = MaterialTheme.typography.titleMedium)
                for (p in ds.points) {
                    val idx = internalCalc.pointIndexById[p.id]
                    val nText = fmtAligned(idx?.totalCount?.toDouble(), 0, pointMetricWidths.n)
                    val sText = fmtAligned(idx?.speciesCountS?.toDouble(), 0, pointMetricWidths.s)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.label.ifBlank { "未命名" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("N=$nText S=$sText", style = numericStyle)
                    }
                    Text(
                        "Vc=${fmtAligned(p.vConcMl, 3, pointMetricWidths.vc)} mL · Vo=${fmtAligned(p.vOrigL, 3, pointMetricWidths.vo)} L · H'=${fmtAligned(idx?.H, 4, pointMetricWidths.h)} · D=${fmtAligned(idx?.D, 4, pointMetricWidths.d)} · J=${fmtAligned(idx?.J, 4, pointMetricWidths.j)}",
                        style = numericStyle,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("统计摘要", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点位总计、物种数、优势种与缺失项概览（用于快速核对）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Text("数据版本", style = MaterialTheme.typography.titleSmall)
                if (libraryMetaError != null) {
                    Text("版本信息读取失败：$libraryMetaError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    val metaLines = formatLibraryMetaLines(libraryMeta)
                    if (metaLines.isEmpty()) {
                        Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (line in metaLines) {
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Text("数据完整度评分", style = MaterialTheme.typography.titleSmall)
                Text(
                    "完整度 ${completeness.score}%（检查 ${completeness.totalChecks} 项，缺失 ${completeness.missingCount} 项）",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "缺失明细：湿重 ${completeness.missingWetWeight} · 分类 ${completeness.missingTaxonomy} · Vc ${completeness.missingVc} · Vo异常 ${completeness.invalidVo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Text("点位总计 / 物种数", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for ((label, total, speciesCount) in pointSummaries) {
                        val nText = fmtAligned(total.toDouble(), 0, pointMetricWidths.n)
                        val sText = fmtAligned(speciesCount.toDouble(), 0, pointMetricWidths.s)
                        Text("$label：N=$nText · S=$sText", style = numericStyle)
                    }
                }

                Text("优势种（出现点数）", style = MaterialTheme.typography.titleSmall)
                if (dominantSummary.isEmpty()) {
                    Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((name, count) in dominantSummary) {
                            Text("$name：$count", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text("异常点位清单", style = MaterialTheme.typography.titleSmall)
                if (abnormalPointSummary.isEmpty()) {
                    Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (item in abnormalPointSummary) {
                            Text("${item.label}：${item.summary}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text("缺失湿重：${missing.size} 条", style = MaterialTheme.typography.bodySmall)
                if (missing.isNotEmpty()) {
                    val previewMissing = missing.take(8)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in previewMissing) {
                            Text("• ${s.nameCn.ifBlank { "（未命名物种）" }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("缺失湿重", style = MaterialTheme.typography.titleMedium)
                Text(
                    "当某物种有计数但没有平均湿重时，生物量会显示“未查到湿重”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                if (missing.isEmpty()) {
                    Text("暂无。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(missing, key = { it.id }) { s ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(s.nameCn.ifBlank { "（未命名物种）" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (s.nameLatin.isNotBlank()) {
                                            Text(
                                                s.nameLatin,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                    TextButton(onClick = { queryTargetId = s.id }) { Text("查湿重") }
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val errIssues = issues.filter { it.level == IssueLevel.Error }
            val warnIssues = issues.filter { it.level == IssueLevel.Warn }
            val infoIssues = issues.filter { it.level == IssueLevel.Info }
            val ignoreCandidates = warnIssues + infoIssues
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("预导出体检", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导出前建议先处理必须修复项（不处理也可导出，但结果可能不完整）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Text(
                    "ERROR $errCount · WARN $warnCount · INFO $infoCount · 已忽略 $ignoredCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = ignoreCandidates.isNotEmpty(),
                        onClick = { ignoreIssues(ignoreCandidates.map { it.key }) },
                        modifier = Modifier.weight(1f),
                    ) { Text("一键忽略可忽略项") }
                    OutlinedButton(
                        enabled = ignoredCount > 0,
                        onClick = ::clearIgnoredIssues,
                        modifier = Modifier.weight(1f),
                    ) { Text("恢复忽略") }
                }

                Button(
                    onClick = {
                        viewModel.updateCurrentDataset { cur ->
                            val existingLabels = cur.points
                                .map { it.label.trim() }
                                .filter { it.isNotBlank() }
                                .toMutableSet()

                            fun nextNumericLabel(): String {
                                var n = 1
                                while (existingLabels.contains(n.toString())) n += 1
                                val s = n.toString()
                                existingLabels.add(s)
                                return s
                            }

                            val nextPoints = cur.points.mapIndexed { idx, p ->
                                val defaultVOrig = settings.defaultVOrigL
                                val fixedVOrig = if (!p.vOrigL.isFinite() || p.vOrigL <= 0) defaultVOrig else p.vOrigL

                                if (cur.stratification.enabled) {
                                    val rawLabel = p.label.trim()
                                    val (s0, d0) = resolveSiteAndDepthForPoint(label = rawLabel, site = p.site, depthM = p.depthM)
                                    val site = s0?.trim()?.takeIf { it.isNotBlank() }
                                        ?: rawLabel.substringBefore("-", missingDelimiterValue = rawLabel).trim().ifBlank { (idx + 1).toString() }
                                    val depth = d0 ?: 0.0
                                    val label = buildStratifiedLabel(site, depth)
                                    p.copy(label = label, site = site, depthM = depth, vOrigL = fixedVOrig)
                                } else {
                                    val label = p.label.trim().ifBlank { nextNumericLabel() }
                                    val (s0, d0) = resolveSiteAndDepthForPoint(label = label, site = p.site, depthM = p.depthM)
                                    p.copy(label = label, site = s0, depthM = d0, vOrigL = fixedVOrig)
                                }
                            }

                            val pointIds = nextPoints.map { it.id }
                            val nextSpecies = cur.species.map { sp ->
                                val nextCounts = buildMap {
                                    for (pid in pointIds) {
                                        val v = sp.countsByPointId[pid] ?: 0
                                        put(pid, v.coerceAtLeast(0))
                                    }
                                }
                                sp.copy(
                                    taxonomy = sp.taxonomy.copy(lvl1 = normalizeLvl1Name(sp.taxonomy.lvl1)),
                                    countsByPointId = nextCounts,
                                )
                            }

                            cur.copy(points = nextPoints, species = nextSpecies)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("一键修复常见问题") }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text("必须修复", style = MaterialTheme.typography.titleSmall) }
                    if (errIssues.isEmpty()) {
                        item {
                            Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    } else {
                        items(errIssues, key = { it.key }) { issue ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IssueRow(issue = issue, allowIgnore = false, onIgnore = {})
                                }
                            }
                        }
                    }

                    item { Text("可忽略", style = MaterialTheme.typography.titleSmall) }
                    if (warnIssues.isEmpty()) {
                        item {
                            Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    } else {
                        items(warnIssues, key = { it.key }) { issue ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IssueRow(issue = issue, allowIgnore = true, onIgnore = { key -> ignoreIssues(listOf(key)) })
                                }
                            }
                        }
                    }

                    item { Text("提示", style = MaterialTheme.typography.titleSmall) }
                    if (infoIssues.isEmpty()) {
                        item {
                            Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                    } else {
                        items(infoIssues, key = { it.key }) { issue ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IssueRow(issue = issue, allowIgnore = true, onIgnore = { key -> ignoreIssues(listOf(key)) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!settings.aiUiHidden) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                val mismatch = (diffReport1?.mismatchCount ?: 0) > 0 || (diffReport2?.mismatchCount ?: 0) > 0
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("计算结果核对（AI）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "默认使用内置算法；当与 API 结果不一致时由你选择导出结果来源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = calcState.useApi1,
                                onCheckedChange = { v -> viewModel.updatePreviewCalcState { it.copy(useApi1 = v) } },
                            )
                            Text(apiLabel(settings.api1, "API1"), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = calcState.useApi2,
                                onCheckedChange = { v -> viewModel.updatePreviewCalcState { it.copy(useApi2 = v) } },
                            )
                            Text(apiLabel(settings.api2, "API2"), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        enabled = !calcCheckBusy,
                        onClick = ::runCalcCheck,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (calcCheckBusy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("核对中…")
                        } else {
                            Text("开始核对")
                        }
                    }

                    if (calcCheckBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (calcCheckMessage != null) {
                        Text(calcCheckMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (calcCheckError != null) {
                        Text(calcCheckError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    OutlinedButton(
                        enabled = calcCheckBusy || calcCheckMessage != null || calcCheckError != null || apiCalc1 != null || apiCalc2 != null,
                        onClick = { viewModel.clearPreviewCalcResults() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除核对结果") }

                    if (apiCalcWarn1.isNotEmpty()) {
                        Text("API1 提示：", style = MaterialTheme.typography.bodySmall)
                        for (line in summarizeWarnings(apiCalcWarn1)) {
                            Text("• $line", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (apiCalcWarn2.isNotEmpty()) {
                        Text("API2 提示：", style = MaterialTheme.typography.bodySmall)
                        for (line in summarizeWarnings(apiCalcWarn2)) {
                            Text("• $line", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    diffReport1?.takeIf { it.mismatchCount > 0 }?.let { report ->
                        val label = apiLabel(settings.api1, "API1")
                        CalcDiffTable(
                            label = label,
                            rows = buildCalcDiffRows(report),
                            maxRows = 10,
                            totalCount = report.mismatchCount,
                            onMore = { diffDialog = CalcDiffDialogState(label, report) },
                        )
                        if (apiCalc1 != null) {
                            val calc = apiCalc1
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(
                                    enabled = !diffExportBusy,
                                    onClick = { requestCalcDiffExport(label, calc) },
                                ) { Text("导出差异 CSV") }
                            }
                        }
                    }
                    diffReport2?.takeIf { it.mismatchCount > 0 }?.let { report ->
                        val label = apiLabel(settings.api2, "API2")
                        CalcDiffTable(
                            label = label,
                            rows = buildCalcDiffRows(report),
                            maxRows = 10,
                            totalCount = report.mismatchCount,
                            onMore = { diffDialog = CalcDiffDialogState(label, report) },
                        )
                        if (apiCalc2 != null) {
                            val calc = apiCalc2
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(
                                    enabled = !diffExportBusy,
                                    onClick = { requestCalcDiffExport(label, calc) },
                                ) { Text("导出差异 CSV") }
                            }
                        }
                    }

                    if (mismatch) {
                        Text("检测到不一致，请选择导出结果来源：", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val internalSelected = calcState.calcSource == CalcSource.Internal
                            OutlinedButton(
                                onClick = { viewModel.updatePreviewCalcState { it.copy(calcSource = CalcSource.Internal) } },
                            ) { Text(if (internalSelected) "✓ 内置算法" else "内置算法") }
                            if (apiCalc1 != null) {
                                val s1 = calcState.calcSource == CalcSource.Api1
                                OutlinedButton(
                                    onClick = { viewModel.updatePreviewCalcState { it.copy(calcSource = CalcSource.Api1) } },
                                ) { Text(if (s1) "✓ ${apiLabel(settings.api1, "API1")}" else apiLabel(settings.api1, "API1")) }
                            }
                            if (apiCalc2 != null) {
                                val s2 = calcState.calcSource == CalcSource.Api2
                                OutlinedButton(
                                    onClick = { viewModel.updatePreviewCalcState { it.copy(calcSource = CalcSource.Api2) } },
                                ) { Text(if (s2) "✓ ${apiLabel(settings.api2, "API2")}" else apiLabel(settings.api2, "API2")) }
                            }
                        }
                    } else if (diffReport1 != null || diffReport2 != null) {
                        Text("结果一致，仍使用内置算法。", style = MaterialTheme.typography.bodySmall)
                    }

                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("导出 Excel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "模板驱动导出（严格复刻表格样式），并提供表1/表2与简表导出与分享。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                if (!lastExportUri.isNullOrBlank()) {
                    Text(
                        "最近导出：${lastExportAt ?: "—"} · $lastExportUri",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("可选：导出拉丁名", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "默认不加入拉丁名；勾选后会在对应列追加“（Latin）”。\n若纲/目/科未内置拉丁名，可在「数据库」新增条目（中文名=纲/目/科名，填写拉丁名），导出会自动追加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )

                        fun set(next: Settings) = viewModel.saveSettings(next)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("类", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinLvl1,
                                onCheckedChange = { set(settings.copy(exportLatinLvl1 = it)) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("纲", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinLvl2,
                                onCheckedChange = { set(settings.copy(exportLatinLvl2 = it)) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("目", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinLvl3,
                                onCheckedChange = { set(settings.copy(exportLatinLvl3 = it)) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("科", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinLvl4,
                                onCheckedChange = { set(settings.copy(exportLatinLvl4 = it)) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("属", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinLvl5,
                                onCheckedChange = { set(settings.copy(exportLatinLvl5 = it)) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("种", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Switch(
                                checked = settings.exportLatinSpecies,
                                onCheckedChange = { set(settings.copy(exportLatinSpecies = it)) },
                            )
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val export1State = exportWorkInfo1?.state
                        val export1Running = export1State == WorkInfo.State.RUNNING || export1State == WorkInfo.State.ENQUEUED
                        val export1Progress = exportWorkInfo1?.progress?.getString(ExcelExportWorker.KEY_PROGRESS)
                        val export1Error = exportWorkInfo1?.outputData?.getString(ExcelExportWorker.KEY_ERROR)

                        Text("表1.xlsx", style = MaterialTheme.typography.titleSmall)
                        Button(
                            enabled = !busy && !export1Running,
                            onClick = {
                                val name = defaultXlsxName(ds.titlePrefix, "表1")
                                exportTable1Launcher.launch(name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (export1Running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(export1Progress ?: "后台导出中…")
                            } else if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("处理中…")
                            } else {
                                Text("导出表1.xlsx")
                            }
                        }
                        if (export1State == WorkInfo.State.FAILED && !export1Error.isNullOrBlank()) {
                            Text("导出失败：$export1Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = { startPreview(1) },
                                modifier = Modifier.weight(1f),
                            ) { Text("预览表1") }
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val res = runCatching {
                                            viewModel.createSnapshotNow(latestDs, "分享表1")
                                            val latinNameMap = buildTaxonomyLatinOverrides(taxonomyOverrideRepo.getCustomEntries())
                                            val out = exporter.exportTable1(
                                                latestDs,
                                                latestSettings.exportLatinOptions(),
                                                selectedCalcOverride(),
                                                libraryMeta,
                                                latinNameMap,
                                            )
                                            val filename = defaultXlsxName(ds.titlePrefix, "表1")
                                            val uri = writeToCache(context, filename, out.bytes)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = out.mime
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享表1.xlsx"))
                                        }
                                        message = res.fold(
                                            onSuccess = { "已生成分享文件（表1.xlsx）" },
                                            onFailure = { "分享失败：${it.message}" },
                                        )
                                        if (res.isSuccess) {
                                            checklistUri = generateChecklist("分享表1")
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("分享表1") }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val export2State = exportWorkInfo2?.state
                        val export2Running = export2State == WorkInfo.State.RUNNING || export2State == WorkInfo.State.ENQUEUED
                        val export2Progress = exportWorkInfo2?.progress?.getString(ExcelExportWorker.KEY_PROGRESS)
                        val export2Error = exportWorkInfo2?.outputData?.getString(ExcelExportWorker.KEY_ERROR)

                        Text("表2.xlsx", style = MaterialTheme.typography.titleSmall)
                        Button(
                            enabled = !busy && !export2Running,
                            onClick = {
                                val name = defaultXlsxName(ds.titlePrefix, "表2")
                                exportTable2Launcher.launch(name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (export2Running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(export2Progress ?: "后台导出中…")
                            } else if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("处理中…")
                            } else {
                                Text("导出表2.xlsx")
                            }
                        }
                        if (export2State == WorkInfo.State.FAILED && !export2Error.isNullOrBlank()) {
                            Text("导出失败：$export2Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = { startPreview(2) },
                                modifier = Modifier.weight(1f),
                            ) { Text("预览表2") }
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val res = runCatching {
                                            viewModel.createSnapshotNow(latestDs, "分享表2")
                                            val latinNameMap = buildTaxonomyLatinOverrides(taxonomyOverrideRepo.getCustomEntries())
                                            val out = exporter.exportTable2(
                                                latestDs,
                                                latestSettings.exportLatinOptions(),
                                                selectedCalcOverride(),
                                                libraryMeta,
                                                latinNameMap,
                                            )
                                            val filename = defaultXlsxName(ds.titlePrefix, "表2")
                                            val uri = writeToCache(context, filename, out.bytes)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = out.mime
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享表2.xlsx"))
                                        }
                                        message = res.fold(
                                            onSuccess = { "已生成分享文件（表2.xlsx）" },
                                            onFailure = { "分享失败：${it.message}" },
                                        )
                                        if (res.isSuccess) {
                                            checklistUri = generateChecklist("分享表2")
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("分享表2") }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val export3State = exportWorkInfo3?.state
                        val export3Running = export3State == WorkInfo.State.RUNNING || export3State == WorkInfo.State.ENQUEUED
                        val export3Progress = exportWorkInfo3?.progress?.getString(ExcelExportWorker.KEY_PROGRESS)
                        val export3Error = exportWorkInfo3?.outputData?.getString(ExcelExportWorker.KEY_ERROR)

                        Text("表1简表.xlsx（四大类+物种）", style = MaterialTheme.typography.titleSmall)
                        Button(
                            enabled = !busy && !export3Running,
                            onClick = {
                                val name = defaultXlsxName(ds.titlePrefix, "表1简表")
                                exportSimpleTable1Launcher.launch(name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (export3Running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(export3Progress ?: "后台导出中…")
                            } else if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("处理中…")
                            } else {
                                Text("导出表1简表.xlsx")
                            }
                        }
                        if (export3State == WorkInfo.State.FAILED && !export3Error.isNullOrBlank()) {
                            Text("导出失败：$export3Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = { startPreview(3) },
                                modifier = Modifier.weight(1f),
                            ) { Text("预览表1简表") }
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val res = runCatching {
                                            viewModel.createSnapshotNow(latestDs, "分享表1简表")
                                            val out = exporter.exportSimpleCountTable(latestDs, libraryMeta)
                                            val filename = defaultXlsxName(ds.titlePrefix, "表1简表")
                                            val uri = writeToCache(context, filename, out.bytes)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = out.mime
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享表1简表.xlsx"))
                                        }
                                        message = res.fold(
                                            onSuccess = { "已生成分享文件（表1简表.xlsx）" },
                                            onFailure = { "分享失败：${it.message}" },
                                        )
                                        if (res.isSuccess) {
                                            checklistUri = generateChecklist("分享表1简表")
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("分享表1简表") }
                        }
                    }
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val export4State = exportWorkInfo4?.state
                        val export4Running = export4State == WorkInfo.State.RUNNING || export4State == WorkInfo.State.ENQUEUED
                        val export4Progress = exportWorkInfo4?.progress?.getString(ExcelExportWorker.KEY_PROGRESS)
                        val export4Error = exportWorkInfo4?.outputData?.getString(ExcelExportWorker.KEY_ERROR)

                        Text("表2简表.xlsx（四大类+物种）", style = MaterialTheme.typography.titleSmall)
                        Button(
                            enabled = !busy && !export4Running,
                            onClick = {
                                val name = defaultXlsxName(ds.titlePrefix, "表2简表")
                                exportSimpleTable2Launcher.launch(name)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (export4Running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(export4Progress ?: "后台导出中…")
                            } else if (busy) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                                Text("处理中…")
                            } else {
                                Text("导出表2简表.xlsx")
                            }
                        }
                        if (export4State == WorkInfo.State.FAILED && !export4Error.isNullOrBlank()) {
                            Text("导出失败：$export4Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = { startPreview(4) },
                                modifier = Modifier.weight(1f),
                            ) { Text("预览表2简表") }
                            Button(
                                enabled = !busy && !previewLoading,
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val res = runCatching {
                                            viewModel.createSnapshotNow(latestDs, "分享表2简表")
                                            val out = exporter.exportSimpleTable2(latestDs, libraryMeta)
                                            val filename = defaultXlsxName(ds.titlePrefix, "表2简表")
                                            val uri = writeToCache(context, filename, out.bytes)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = out.mime
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "分享表2简表.xlsx"))
                                        }
                                        message = res.fold(
                                            onSuccess = { "已生成分享文件（表2简表.xlsx）" },
                                            onFailure = { "分享失败：${it.message}" },
                                        )
                                        if (res.isSuccess) {
                                            checklistUri = generateChecklist("分享表2简表")
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("分享表2简表") }
                        }
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val selectedSet = remember(customMetrics) { customMetrics.toSet() }
            val available = remember(allCustomMetrics, selectedSet) { allCustomMetrics.filterNot { selectedSet.contains(it) } }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自定义指标导出（CSV）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "可选择并排序导出密度/生物量/H/D/J/Y，并随导出生成校验清单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )

                Text("已选指标（可排序）", style = MaterialTheme.typography.titleSmall)
                if (customMetrics.isEmpty()) {
                    Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((idx, m) in customMetrics.withIndex()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = { checked ->
                                        if (!checked) customMetrics = customMetrics.filterNot { it == m }
                                    },
                                )
                                Text(m.label, modifier = Modifier.weight(1f))
                                IconButton(
                                    enabled = idx > 0,
                                    onClick = {
                                        val next = customMetrics.toMutableList()
                                        val item = next.removeAt(idx)
                                        next.add(idx - 1, item)
                                        customMetrics = next
                                    },
                                ) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移") }
                                IconButton(
                                    enabled = idx < customMetrics.size - 1,
                                    onClick = {
                                        val next = customMetrics.toMutableList()
                                        val item = next.removeAt(idx)
                                        next.add(idx + 1, item)
                                        customMetrics = next
                                    },
                                ) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移") }
                            }
                        }
                    }
                }

                Text("可选指标", style = MaterialTheme.typography.titleSmall)
                if (available.isEmpty()) {
                    Text("暂无。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (m in available) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = { checked ->
                                        if (checked) customMetrics = customMetrics + m
                                    },
                                )
                                Text(m.label)
                            }
                        }
                    }
                }

                Button(
                    enabled = !customExportBusy,
                    onClick = { requestCustomExport() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (customExportBusy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        Text("导出中…")
                    } else {
                        Text("导出自定义指标（CSV）")
                    }
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("校验清单", style = MaterialTheme.typography.titleMedium)
                Text(
                    "汇总缺失湿重/异常体积/异常计数等提示，便于导出前复核。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !checklistBusy,
                        onClick = {
                            checklistBusy = true
                            scope.launch {
                                val res = try {
                                    Result.success(generateChecklist("手动生成"))
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                                res.onSuccess { checklistUri = it }
                                Toast.makeText(
                                    context,
                                    res.fold(onSuccess = { "已生成校验清单" }, onFailure = { "生成失败：${it.message}" }),
                                    Toast.LENGTH_LONG,
                                ).show()
                                checklistBusy = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (checklistBusy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                            Text("生成中…")
                        } else {
                            Text("生成校验清单")
                        }
                    }
                    OutlinedButton(
                        enabled = checklistUri != null,
                        onClick = {
                            val uri = checklistUri ?: return@OutlinedButton
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享校验清单"))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("分享清单") }
                }
                if (checklistUri != null) {
                    Text("已生成（可直接分享）", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

    if (queryTarget != null) {
        WetWeightQueryDialog(
            settings = settings,
            nameCn = queryTarget.nameCn,
            nameLatin = queryTarget.nameLatin,
            onClose = { queryTargetId = null },
            onApply = { mg ->
                viewModel.updateCurrentDataset { cur ->
                    cur.copy(
                        species = cur.species.map { sp ->
                            if (sp.id == queryTarget.id) sp.copy(avgWetWeightMg = mg) else sp
                        },
                    )
                }
                queryTargetId = null
            },
            onSaveToLibrary = { mg ->
                val nameCn = queryTarget.nameCn.trim()
                if (nameCn.isEmpty()) return@WetWeightQueryDialog
                scope.launch {
                    wetWeightRepo.upsertCustom(
                        WetWeightEntry(
                            nameCn = nameCn,
                            nameLatin = queryTarget.nameLatin.trim().ifBlank { null },
                            wetWeightMg = mg,
                            taxonomy = WetWeightTaxonomy(
                                group = queryTarget.taxonomy.lvl1.trim().ifBlank { null },
                                sub = queryTarget.taxonomy.lvl4.trim().ifBlank { null },
                            ),
                        ),
                    )
                }
            },
        )
    }

    if (previewKind != null) {
        val title = when (previewKind) {
            1 -> "预览：表1.xlsx"
            2 -> "预览：表2.xlsx"
            3 -> "预览：表1简表.xlsx"
            4 -> "预览：表2简表.xlsx"
            else -> "预览：Excel"
        }
        WorkbookPreviewDialog(
            title = title,
            loading = previewLoading,
            error = previewError,
            preview = previewData,
            summary = WorkbookPreviewSummary(
                entryCount = ds.species.size,
                pointCount = ds.points.size,
                lines = previewSummaryLines,
            ),
            onClose = {
                previewKind = null
                previewError = null
                previewData = null
                previewLoading = false
            },
            onRetry = { previewKind?.let { startPreview(it) } },
        )
    }

    diffDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { diffDialog = null },
            confirmButton = { TextButton(onClick = { diffDialog = null }) { Text("关闭") } },
            title = { Text("差异明细（${state.label}）") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val report = state.report
                    val shown = report.items.size
                    Text("共检查 ${report.totalChecked} 项，差异 ${report.mismatchCount} 项（展示 $shown 项）", style = MaterialTheme.typography.bodySmall)
                    CalcDiffTable(
                        label = state.label,
                        rows = buildCalcDiffRows(report),
                        maxRows = shown,
                        totalCount = report.mismatchCount,
                        onMore = null,
                    )
                }
            },
            containerColor = dialogColor,
            shape = dialogShape,
        )
    }
}
