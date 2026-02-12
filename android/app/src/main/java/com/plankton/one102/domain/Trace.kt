package com.plankton.one102.domain

import kotlin.math.ln

private fun fmt(value: Double?, digits: Int = 6): String {
    if (value == null) return "（空）"
    if (!value.isFinite()) return "（非有限数）"
    return "%.${digits}f".format(value).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun fmtInt(value: Int): String = value.toString()

fun buildPointTrace(dataset: Dataset, pointId: Id): String {
    val point = dataset.points.firstOrNull { it.id == pointId } ?: return "找不到该采样点。"
    val calc = calcDataset(dataset)
    val pi = calc.pointIndexById[pointId]

    val sb = StringBuilder()
    sb.appendLine("点位：${point.label.ifBlank { point.id }}")
    sb.appendLine()
    sb.appendLine("输入：")
    sb.appendLine("- 浓缩体积 Vc（mL）= ${fmt(point.vConcMl, 3)}")
    sb.appendLine("- 原水体积 Vo（L）= ${fmt(point.vOrigL, 3)}")
    sb.appendLine("- 密度公式：density(ind/L) = (n / 1.3) * (Vc / Vo)")
    sb.appendLine("- 生物量公式：biomass(mg/L) = density * 湿重(mg/个)")
    sb.appendLine()

    val totalCount = pi?.totalCount ?: 0
    val s = pi?.speciesCountS ?: 0
    sb.appendLine("汇总：")
    sb.appendLine("- N（该点位总计数）= ${fmtInt(totalCount)}")
    sb.appendLine("- S（该点位物种数，n>0 的物种个数）= ${fmtInt(s)}")
    sb.appendLine()

    sb.appendLine("指数（公式顺序：3→4→5→6）：")
    sb.appendLine("- 公式3 Shannon-Wiener：H' = -Σ(p_i * ln p_i)；p_i = n_i / N")
    sb.appendLine("- 公式4 Pielou：J = H' / ln S")
    sb.appendLine("- 公式5 Margalef：D = (S - 1) / ln N")
    sb.appendLine("- 公式6 优势度：Y_i = (n_i / N) * f_i；其中 f_i=出现点数/总点数；Y>0.02 视为优势种")
    sb.appendLine()

    val pointCount = dataset.points.size.coerceAtLeast(1)
    val speciesLines = mutableListOf<String>()
    var sumPLnP = 0.0

    for (sp in dataset.species) {
        val n = sp.countsByPointId[pointId] ?: 0
        if (n <= 0) continue

        val per = calc.perSpeciesByPoint[sp.id]?.get(pointId)
        val density = per?.density
        val biomass = per?.biomass
        val p = per?.p
        val pLnP = per?.pLnP
        val fi = calc.fiBySpeciesId[sp.id] ?: 0.0
        val y = per?.Y

        if (pLnP != null && pLnP.isFinite()) sumPLnP += pLnP

        val bTxt = when (biomass) {
            null -> "（无法计算）"
            is BiomassCell.Value -> fmt(biomass.mgPerL, 6)
            BiomassCell.MissingWetWeight -> "未查到湿重"
        }

        val present = (fi * pointCount.toDouble()).toInt()

        speciesLines += buildString {
            appendLine("物种：${sp.nameCn.ifBlank { sp.id }}")
            if (sp.nameLatin.isNotBlank()) appendLine("- 拉丁名：${sp.nameLatin}")
            if (sp.taxonomy.lvl1.isNotBlank()) {
                appendLine(
                    "- 分类：${
                        listOf(sp.taxonomy.lvl1, sp.taxonomy.lvl2, sp.taxonomy.lvl3, sp.taxonomy.lvl4, sp.taxonomy.lvl5)
                            .filter { it.isNotBlank() }
                            .joinToString(" / ")
                    }",
                )
            }
            appendLine("- n = ${fmtInt(n)}")
            appendLine("- density = (n/1.3)*(Vc/Vo) = ${fmt(density, 6)} ind/L")
            appendLine("- 湿重 = ${fmt(sp.avgWetWeightMg, 6)} mg/个")
            appendLine("- biomass = density*湿重 = $bTxt mg/L")
            appendLine("- p = n/N = ${fmt(p, 8)}")
            appendLine("- p*ln(p) = ${fmt(pLnP, 8)}")
            appendLine("- f = 出现点数/总点数 = $present/$pointCount = ${fmt(fi, 6)}")
            appendLine("- Y = p*f = ${fmt(y, 8)}（${if (y != null && y > 0.02) "优势种" else "非优势种/不确定"}）")
        }.trimEnd()
    }

    if (speciesLines.isEmpty()) {
        sb.appendLine("该点位没有任何物种计数（全为 0）。")
        return sb.toString().trimEnd()
    }

    sb.appendLine("物种明细：")
    sb.appendLine(speciesLines.joinToString("\n\n"))
    sb.appendLine()

    val h = pi?.H
    val j = pi?.J
    val d = pi?.D
    val computedH = if (totalCount > 0) -sumPLnP else null

    sb.appendLine("该点位指数结果：")
    sb.appendLine("- Σ(p*ln p) = ${fmt(sumPLnP, 8)}")
    sb.appendLine("- H' = -Σ(p*ln p) = ${fmt(computedH, 8)}（程序值：${fmt(h, 8)}）")
    sb.appendLine("- J = H'/ln S = ${fmt(j, 8)}")
    sb.appendLine("- D = (S-1)/ln N = ${fmt(d, 8)}")

    // Extra sanity check echo
    if (s > 1 && totalCount > 1) {
        val checkJ = h?.let { it / ln(s.toDouble()) }
        val checkD = (s - 1).toDouble() / ln(totalCount.toDouble())
        sb.appendLine()
        sb.appendLine("校验（按公式重算）：")
        sb.appendLine("- J(重算) = ${fmt(checkJ, 8)}")
        sb.appendLine("- D(重算) = ${fmt(checkD, 8)}")
    }

    return sb.toString().trimEnd()
}

