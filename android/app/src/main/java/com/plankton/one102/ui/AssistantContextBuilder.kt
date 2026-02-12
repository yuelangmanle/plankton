package com.plankton.one102.ui

import android.content.Context
import com.plankton.one102.data.repo.AliasRepository
import com.plankton.one102.data.repo.TaxonomyOverrideRepository
import com.plankton.one102.data.repo.TaxonomyRepository
import com.plankton.one102.data.repo.WetWeightRepository
import com.plankton.one102.domain.DataIssue
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.IssueLevel
import com.plankton.one102.domain.TaxonomyEntry
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.WetWeightEntry

private fun clipTextBlock(text: String, maxChars: Int): String {
    val clean = text.trimEnd()
    if (clean.length <= maxChars) return clean
    val clipped = clean.take(maxChars).trimEnd()
    val omitted = clean.length - clipped.length
    return "$clipped\n（已省略约 $omitted 字，未列出的 counts 视为 0）"
}

private fun fmtWet(value: Double?): String {
    if (value == null || !value.isFinite()) return "（缺失）"
    return "%.${6}f".format(value).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun pickTaxonomyRecord(names: List<String>, custom: Map<String, TaxonomyRecord>, builtin: Map<String, TaxonomyEntry>): Pair<String, String> {
    for (name in names) {
        custom[name]?.let { return "自定义" to "${it.taxonomy.lvl1}/${it.taxonomy.lvl2}/${it.taxonomy.lvl3}/${it.taxonomy.lvl4}/${it.taxonomy.lvl5}".trimEnd('/') }
    }
    for (name in names) {
        builtin[name]?.let {
            val t = it.taxonomy
            return "内置" to "${t.lvl1}/${t.lvl2}/${t.lvl3}/${t.lvl4}/${t.lvl5}".trimEnd('/')
        }
    }
    return "" to "（未匹配）"
}

private fun pickWetWeightEntry(names: List<String>, custom: Map<String, WetWeightEntry>, builtin: Map<String, WetWeightEntry>): Pair<String, String> {
    for (name in names) {
        custom[name]?.let { return "自定义" to fmtWet(it.wetWeightMg) }
    }
    for (name in names) {
        builtin[name]?.let { return "内置" to fmtWet(it.wetWeightMg) }
    }
    return "" to "（未匹配）"
}

private fun formatPointLabel(label: String, id: String): String = label.ifBlank { id }

private fun formatSiteDepth(site: String?, depthM: Double?): String {
    val siteText = site?.trim().orEmpty()
    val depthText = depthM?.let { if (it.isFinite()) "${it}m" else "" }.orEmpty()
    if (siteText.isBlank() && depthText.isBlank()) return ""
    val parts = listOf(siteText, depthText).filter { it.isNotBlank() }
    return "（${parts.joinToString(" / ")}）"
}

suspend fun buildAssistantContextFull(
    dataset: Dataset,
    issues: List<DataIssue>,
    taxonomyRepo: TaxonomyRepository,
    taxonomyOverrideRepo: TaxonomyOverrideRepository,
    wetWeightRepo: WetWeightRepository,
    aliasRepo: AliasRepository,
    maxPoints: Int = 200,
    maxSpecies: Int = 200,
    maxIssues: Int = 40,
    maxChars: Int = 24000,
): String {
    val points = dataset.points
    val species = dataset.species
    val aliases = aliasRepo.getAll()
    val aliasByCanonical = aliases.groupBy { it.canonicalNameCn }
    val aliasByAlias = aliases.associate { it.alias to it.canonicalNameCn }

    val customTax = taxonomyOverrideRepo.getCustomEntries().associateBy { it.nameCn.trim() }
    val builtinTax = taxonomyRepo.getBuiltinEntryMap()
    val customWet = wetWeightRepo.getCustomEntries().associateBy { it.nameCn.trim() }
    val builtinWet = wetWeightRepo.getBuiltinEntries().associateBy { it.nameCn.trim() }

    val pointLabelById = points.associate { it.id to formatPointLabel(it.label, it.id) }

    val sb = StringBuilder()
    sb.appendLine("你是“浮游动物一体化”应用内的全局数据流程监测助手。")
    sb.appendLine("任务：基于以下数据解释计算与导出过程，并指出潜在问题。")
    sb.appendLine("要求：仅基于提供的数据；不确定必须直说；不得编造引用。")
    sb.appendLine()
    sb.appendLine("当前数据集标题前缀：${dataset.titlePrefix.ifBlank { "（空）" }}")
    sb.appendLine("采样点：${points.size} 个")
    val shownPoints = points.take(maxPoints)
    for (p in shownPoints) {
        sb.appendLine(
            "- ${formatPointLabel(p.label, p.id)}${formatSiteDepth(p.site, p.depthM)}：Vc=${p.vConcMl ?: "（空）"} mL，Vo=${p.vOrigL} L",
        )
    }
    if (points.size > shownPoints.size) {
        sb.appendLine("（已省略 ${points.size - shownPoints.size} 个采样点）")
    }
    sb.appendLine()

    sb.appendLine("计数矩阵（点位 -> 物种:计数；未列出的 counts 视为 0）：")
    val shownSpecies = species.take(maxSpecies)
    for (p in shownPoints) {
        val counts = shownSpecies.mapNotNull { sp ->
            val n = sp.countsByPointId[p.id] ?: 0
            if (n == 0) null else "${sp.nameCn.ifBlank { sp.id }}=$n"
        }
        sb.appendLine("- ${pointLabelById[p.id]}：${if (counts.isEmpty()) "（全为 0）" else counts.joinToString(", ")}")
    }
    if (points.size > shownPoints.size || species.size > shownSpecies.size) {
        sb.appendLine("（已截取 ${shownPoints.size} 个点位 × ${shownSpecies.size} 个物种）")
    }
    sb.appendLine()

    sb.appendLine("计数矩阵（物种 -> 点位:计数；未列出的 counts 视为 0）：")
    for (sp in shownSpecies) {
        val name = sp.nameCn.ifBlank { sp.id }
        val total = sp.countsByPointId.values.sum()
        val counts = points.mapNotNull { p ->
            val n = sp.countsByPointId[p.id] ?: 0
            if (n == 0) null else "${pointLabelById[p.id]}=$n"
        }
        sb.appendLine("- $name：总计=$total；${if (counts.isEmpty()) "（全为 0）" else counts.joinToString(", ")}")
    }
    if (species.size > shownSpecies.size) {
        sb.appendLine("（已省略 ${species.size - shownSpecies.size} 个物种）")
    }
    sb.appendLine()

    sb.appendLine("物种信息（数据集录入）：")
    for (sp in shownSpecies) {
        sb.appendLine("- ${sp.nameCn.ifBlank { sp.id }} / ${sp.nameLatin.ifBlank { "（无拉丁名）" }}")
        sb.appendLine("  湿重：${fmtWet(sp.avgWetWeightMg)} mg/个")
        val t = sp.taxonomy
        val taxonomyLine = listOf(t.lvl1, t.lvl2, t.lvl3, t.lvl4, t.lvl5).filter { it.isNotBlank() }.joinToString("/")
        sb.appendLine("  分类：${taxonomyLine.ifBlank { "（缺失）" }}")
    }
    sb.appendLine()

    sb.appendLine("数据库匹配（分类库/湿重库/别名）：")
    sb.appendLine("数据库摘要：分类库内置 ${builtinTax.size} 条 / 自定义 ${customTax.size} 条；湿重库内置 ${builtinWet.size} 条 / 自定义 ${customWet.size} 条；别名 ${aliases.size} 条。")
    for (sp in shownSpecies) {
        val name = sp.nameCn.ifBlank { sp.id }.trim()
        val canonical = aliasByAlias[name]
        val lookupNames = listOfNotNull(name, canonical).filter { it.isNotBlank() }.distinct()
        val aliasList = aliasByCanonical[name].orEmpty().map { it.alias } +
            aliasByCanonical[canonical].orEmpty().map { it.alias }
        val (taxOrigin, taxLine) = pickTaxonomyRecord(lookupNames, customTax, builtinTax)
        val (wetOrigin, wetLine) = pickWetWeightEntry(lookupNames, customWet, builtinWet)
        sb.appendLine("- $name${if (!canonical.isNullOrBlank()) "（别名→$canonical）" else ""}")
        sb.appendLine("  分类库：${taxOrigin.ifBlank { "—" }} $taxLine")
        sb.appendLine("  湿重库：${wetOrigin.ifBlank { "—" }} $wetLine")
        if (aliasList.isNotEmpty()) {
            sb.appendLine("  别名：${aliasList.distinct().joinToString("、")}")
        }
    }
    sb.appendLine()

    sb.appendLine("已发现的问题（本机规则检查）：")
    val shownIssues = issues.take(maxIssues)
    for (i in shownIssues) {
        val tag = when (i.level) {
            IssueLevel.Info -> "INFO"
            IssueLevel.Warn -> "WARN"
            IssueLevel.Error -> "ERROR"
        }
        sb.appendLine("- [$tag] ${i.title}${if (i.detail.isNotBlank()) "：${i.detail}" else ""}")
    }
    if (issues.size > shownIssues.size) {
        sb.appendLine("（已省略 ${issues.size - shownIssues.size} 条问题）")
    }
    sb.appendLine()
    sb.appendLine("关键公式：")
    sb.appendLine("- 密度：density(ind/L) = (n / 1.3) * (Vc / Vo)")
    sb.appendLine("- 生物量：biomass(mg/L) = density * 湿重(mg/个)")
    sb.appendLine("- 公式3 Shannon-Wiener：H' = -Σ(p_i * ln p_i)，p_i = n_i / N（N=该点位总计数）")
    sb.appendLine("- 公式4 Pielou：J = H' / ln S（S=该点位物种数）")
    sb.appendLine("- 公式5 Margalef：D = (S - 1) / ln N")
    sb.appendLine("- 公式6 优势度：Y_i = (n_i / N) * f_i；f_i=出现点数/总点数；Y>0.02 视为优势种")

    return clipTextBlock(sb.toString(), maxChars)
}

suspend fun buildAssistantContextWithDocs(
    context: Context,
    dataset: Dataset,
    issues: List<DataIssue>,
    taxonomyRepo: TaxonomyRepository,
    taxonomyOverrideRepo: TaxonomyOverrideRepository,
    wetWeightRepo: WetWeightRepository,
    aliasRepo: AliasRepository,
    maxChars: Int = 28000,
    docMaxChars: Int = 8000,
): String {
    val baseMax = (maxChars - docMaxChars - 1200).coerceAtLeast(12000)
    val base = buildAssistantContextFull(
        dataset = dataset,
        issues = issues,
        taxonomyRepo = taxonomyRepo,
        taxonomyOverrideRepo = taxonomyOverrideRepo,
        wetWeightRepo = wetWeightRepo,
        aliasRepo = aliasRepo,
        maxChars = baseMax,
    )
    val docs = buildProjectDocContext(context, maxChars = docMaxChars)
    val changelog = buildChangelogContext()
    val sb = StringBuilder()
    sb.appendLine(base)
    if (docs.isNotBlank()) {
        sb.appendLine()
        sb.appendLine("【项目资料】")
        sb.appendLine(docs)
    }
    if (changelog.isNotBlank()) {
        sb.appendLine()
        sb.appendLine("【更新日志】")
        sb.appendLine(changelog)
    }
    return clipTextBlock(sb.toString(), maxChars)
}
