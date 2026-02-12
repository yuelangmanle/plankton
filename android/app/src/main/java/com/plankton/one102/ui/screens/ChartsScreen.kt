package com.plankton.one102.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.BiomassCell
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.Id
import com.plankton.one102.domain.calcDataset
import com.plankton.one102.ui.MainViewModel
import com.plankton.one102.ui.components.GlassBackground
import com.plankton.one102.ui.components.GlassCard
import kotlin.math.roundToInt

private enum class Metric(val label: String) {
    TotalCount("总计数 N"),
    SpeciesCount("物种数 S"),
    H("Shannon H'"),
    J("Pielou J"),
    D("Margalef D"),
    TotalBiomass("总生物量（已知 mg/L）"),
}

private fun formatNumber(v: Double?): String {
    if (v == null || !v.isFinite()) return "—"
    val abs = kotlin.math.abs(v)
    return if (abs in 0.001..1000.0) {
        String.format("%.6f", v).trimEnd('0').trimEnd('.')
    } else {
        String.format("%.3g", v)
    }
}

private data class PointMetrics(
    val pointId: Id,
    val label: String,
    val N: Int,
    val S: Int,
    val H: Double?,
    val J: Double?,
    val D: Double?,
    val totalBiomassKnown: Double?,
    val biomassMissingWet: Int,
    val biomassUnknown: Int,
)

private fun buildPointMetrics(dataset: Dataset): List<PointMetrics> {
    val calc = calcDataset(dataset)
    val list = mutableListOf<PointMetrics>()

    for (p in dataset.points) {
        val idx = calc.pointIndexById[p.id] ?: continue

        var sum = 0.0
        var missingWet = 0
        var unknown = 0
        for (sp in dataset.species) {
            val per = calc.perSpeciesByPoint[sp.id]?.get(p.id) ?: continue
            val b = per.biomass
            when (b) {
                is BiomassCell.Value -> sum += b.mgPerL
                is BiomassCell.MissingWetWeight -> if (per.count > 0) missingWet += 1
                null -> if (per.count > 0) unknown += 1
            }
        }

        list += PointMetrics(
            pointId = p.id,
            label = p.label.ifBlank { "未命名" },
            N = idx.totalCount,
            S = idx.speciesCountS,
            H = idx.H,
            J = idx.J,
            D = idx.D,
            totalBiomassKnown = if (sum.isFinite()) sum else null,
            biomassMissingWet = missingWet,
            biomassUnknown = unknown,
        )
    }
    return list
}

@Composable
fun ChartsScreen(viewModel: MainViewModel, padding: PaddingValues) {
    val dataset by viewModel.currentDataset.collectAsStateWithLifecycle()
    val globalPointId by viewModel.activePointId.collectAsStateWithLifecycle()

    val ds = dataset ?: run {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text("加载中…") }
        }
        return
    }

    if (ds.points.isEmpty()) {
        GlassBackground {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("结果图表", style = MaterialTheme.typography.titleLarge)
                Text(
                    "当前数据集没有采样点。请先在底部「采样点」页面新增至少 1 条采样点后再查看图表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        return
    }

    var metric by remember { mutableStateOf(Metric.TotalCount) }
    val points = remember(ds) { buildPointMetrics(ds) }

    var activePointId by remember(ds.id) { mutableStateOf(globalPointId ?: ds.points.firstOrNull()?.id ?: "") }
    if (ds.points.none { it.id == activePointId }) activePointId = ds.points.firstOrNull()?.id ?: ""
    LaunchedEffect(activePointId) {
        if (activePointId.isNotBlank()) viewModel.setActivePointId(activePointId)
    }

    val calc = remember(ds) { calcDataset(ds) }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("结果图表", style = MaterialTheme.typography.titleLarge)
            Text(
                "用于快速查看各点位指标与优势物种（图表仅做预览；导出仍以 Excel 为准）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("指标选择", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in Metric.entries) {
                        val selected = metric == m
                        if (selected) {
                            OutlinedButton(onClick = { metric = m }) { Text("✓ ${m.label}") }
                        } else {
                            OutlinedButton(onClick = { metric = m }) { Text(m.label) }
                        }
                    }
                }

                val chartItems = points.map {
                    val v = when (metric) {
                        Metric.TotalCount -> it.N.toDouble()
                        Metric.SpeciesCount -> it.S.toDouble()
                        Metric.H -> it.H
                        Metric.J -> it.J
                        Metric.D -> it.D
                        Metric.TotalBiomass -> it.totalBiomassKnown
                    }
                    BarItem(label = it.label, value = v, note = when (metric) {
                        Metric.TotalBiomass -> {
                            if (it.biomassMissingWet > 0 || it.biomassUnknown > 0) {
                                "缺湿重 ${it.biomassMissingWet} / 计算缺失 ${it.biomassUnknown}"
                            } else null
                        }
                        else -> null
                    })
                }
                BarChartRow(items = chartItems, height = 140.dp)
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("点位指标表", style = MaterialTheme.typography.titleMedium)
                for (p in points) {
                    val line = buildString {
                        append(p.label)
                        append("  N=")
                        append(p.N)
                        append("  S=")
                        append(p.S)
                        append("  H'=")
                        append(formatNumber(p.H))
                        append("  J=")
                        append(formatNumber(p.J))
                        append("  D=")
                        append(formatNumber(p.D))
                        append("  生物量=")
                        append(formatNumber(p.totalBiomassKnown))
                        append(" mg/L")
                        if (p.biomassMissingWet > 0 || p.biomassUnknown > 0) {
                            append("（缺湿重 ")
                            append(p.biomassMissingWet)
                            append(" / 计算缺失 ")
                            append(p.biomassUnknown)
                            append("）")
                        }
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Top 物种（按生物量 mg/L）", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (p in ds.points) {
                        val selected = p.id == activePointId
                        if (selected) {
                            OutlinedButton(onClick = { activePointId = p.id }) { Text("✓ ${p.label.ifBlank { "未命名" }}") }
                        } else {
                            OutlinedButton(onClick = { activePointId = p.id }) { Text(p.label.ifBlank { "未命名" }) }
                        }
                    }
                }

                val top = remember(ds, activePointId) {
                    ds.species.mapNotNull { sp ->
                        val per = calc.perSpeciesByPoint[sp.id]?.get(activePointId) ?: return@mapNotNull null
                        val mg = (per.biomass as? BiomassCell.Value)?.mgPerL ?: return@mapNotNull null
                        Triple(sp.nameCn.ifBlank { "（未命名）" }, sp.nameLatin, mg)
                    }.sortedByDescending { it.third }.take(10)
                }

                if (top.isEmpty()) {
                    Text(
                        "暂无可计算的生物量（可能缺少浓缩体积或湿重）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                } else {
                    for ((nameCn, latin, mg) in top) {
                        val line = buildString {
                            append(nameCn)
                            if (!latin.isNullOrBlank()) {
                                append("（")
                                append(latin)
                                append("）")
                            }
                            append("：")
                            append(formatNumber(mg))
                            append(" mg/L")
                        }
                        Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
}

private data class BarItem(
    val label: String,
    val value: Double?,
    val note: String? = null,
)

@Composable
private fun BarChartRow(items: List<BarItem>, height: Dp) {
    val max = items.mapNotNull { it.value?.takeIf { v -> v.isFinite() } }.maxOrNull()?.takeIf { it > 0 } ?: 0.0
    val scroll = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (it in items) {
            val v = it.value
            val ratio = if (v != null && v.isFinite() && max > 0) (v / max).coerceIn(0.0, 1.0) else 0.0
            val barH = (height.value * ratio.toFloat()).dp
            val isMissing = v == null
            val barColor = if (isMissing) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            }

            Column(
                modifier = Modifier.width(72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(it.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(
                    modifier = Modifier
                        .height(height)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Spacer(
                        modifier = Modifier
                            .height(barH)
                            .fillMaxWidth()
                            .background(barColor),
                    )
                }
                Text(
                    if (v == null) "—" else formatNumber(v),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (it.note != null) {
                    Text(
                        it.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
