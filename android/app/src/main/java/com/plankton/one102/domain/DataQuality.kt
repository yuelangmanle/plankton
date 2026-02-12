package com.plankton.one102.domain

enum class IssueLevel { Info, Warn, Error }

data class DataIssue(
    val level: IssueLevel,
    val title: String,
    val detail: String = "",
    val speciesId: Id? = null,
    val pointId: Id? = null,
    val key: String = buildIssueKey(level, title, detail, speciesId, pointId),
)

private fun anyCountPositive(species: Species, pointId: Id? = null): Boolean {
    return if (pointId == null) {
        species.countsByPointId.values.any { it > 0 }
    } else {
        (species.countsByPointId[pointId] ?: 0) > 0
    }
}

private fun normalizeKeyPart(value: String): String {
    val cleaned = value.trim().replace(Regex("\\s+"), " ")
    return cleaned.take(80)
}

private fun buildIssueKey(
    level: IssueLevel,
    title: String,
    detail: String,
    speciesId: Id?,
    pointId: Id?,
): String {
    val parts = mutableListOf<String>()
    parts += title.trim().ifBlank { level.name }
    if (!pointId.isNullOrBlank()) parts += "P:$pointId"
    if (!speciesId.isNullOrBlank()) parts += "S:$speciesId"
    if (parts.size <= 1) {
        val d = normalizeKeyPart(detail)
        if (d.isNotBlank()) parts += d
    }
    return parts.joinToString("|")
}

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
}

fun validateDataset(dataset: Dataset): List<DataIssue> {
    val issues = mutableListOf<DataIssue>()

    if (dataset.points.isEmpty()) {
        issues += DataIssue(
            IssueLevel.Warn,
            "没有采样点",
            "当前数据集没有采样点：无法录入计数/计算/导出。你可以在「采样点」页面新增或导入。",
        )
        return issues
    }

    val pointIds = dataset.points.map { it.id }.toSet()

    // Points
    for ((idx, p) in dataset.points.withIndex()) {
        if (p.label.isBlank()) {
            issues += DataIssue(
                IssueLevel.Warn,
                "采样点名称为空",
                "第 ${idx + 1} 条采样点名称为空，建议命名（如 1-0、1-2、G1…）。",
                pointId = p.id,
            )
        }
        if (!p.vOrigL.isFinite() || p.vOrigL <= 0) {
            issues += DataIssue(
                IssueLevel.Error,
                "原水体积不合法",
                "采样点「${p.label.ifBlank { idx + 1 }}」原水体积为 ${p.vOrigL}，需为 >0 的数值（单位 L）。",
                pointId = p.id,
            )
        }
        val used = dataset.species.any { anyCountPositive(it, p.id) }
        val conc = p.vConcMl
        if (used && (conc == null || !conc.isFinite() || conc <= 0)) {
            issues += DataIssue(
                IssueLevel.Warn,
                "浓缩体积缺失/不合法",
                "采样点「${p.label.ifBlank { idx + 1 }}」有计数，但浓缩体积为空或 ≤0；密度/生物量会计算失败。",
                pointId = p.id,
            )
        }

        if (dataset.stratification.enabled) {
            val (_, depth) = resolveSiteAndDepthForPoint(label = p.label, site = p.site, depthM = p.depthM)
            if (depth == null) {
                issues += DataIssue(
                    IssueLevel.Warn,
                    "分层：水深缺失/不合法",
                    "采样点「${p.label.ifBlank { idx + 1 }}」在分层模式下建议使用“点位-水深(m)”格式（例如 1-0.3）。否则分层汇总表会跳过该点位。",
                    pointId = p.id,
                )
            } else {
                val cfg = dataset.stratification
                val inAny = (depth >= cfg.upper.minM && depth <= cfg.upper.maxM) ||
                    (depth >= cfg.middle.minM && depth <= cfg.middle.maxM) ||
                    (depth >= cfg.lower.minM && depth <= cfg.lower.maxM)
                if (!inAny) {
                    issues += DataIssue(
                        IssueLevel.Warn,
                        "分层：水深不在范围内",
                        "采样点「${p.label.ifBlank { idx + 1 }}」水深=$depth m 不在上/中/下层范围内；分层汇总表会跳过该点位。",
                        pointId = p.id,
                    )
                }
            }
        }
    }

    if (dataset.stratification.enabled) {
        val cfg = dataset.stratification
        fun checkRange(name: String, min: Double, max: Double) {
            if (!min.isFinite() || !max.isFinite() || max <= min) {
                issues += DataIssue(IssueLevel.Warn, "分层范围不合法", "$name：起=$min 止=$max（需为有限数且止>起）。")
            }
        }
        checkRange("上层", cfg.upper.minM, cfg.upper.maxM)
        checkRange("中层", cfg.middle.minM, cfg.middle.maxM)
        checkRange("下层", cfg.lower.minM, cfg.lower.maxM)
    }

    // Consistency checks for stratified points (same site, different depths).
    data class SitePoint(val point: Point, val site: String, val depth: Double, val totalCount: Int)
    val totalsByPointId = dataset.points.associate { p -> p.id to calcPointTotals(dataset, p).first }
    val sitePoints = dataset.points.mapNotNull { p ->
        val (site, depth) = resolveSiteAndDepthForPoint(label = p.label, site = p.site, depthM = p.depthM)
        if (site.isNullOrBlank() || depth == null || !depth.isFinite()) return@mapNotNull null
        SitePoint(point = p, site = site, depth = depth, totalCount = totalsByPointId[p.id] ?: 0)
    }
    val grouped = sitePoints.groupBy { it.site }
    val vcLow = 0.55
    val vcHigh = 1.8
    val countLow = 0.3
    val countHigh = 3.0
    for ((site, points) in grouped) {
        if (points.size < 2) continue
        val vcValues = points.mapNotNull { it.point.vConcMl?.takeIf { v -> v.isFinite() && v > 0 } }
        val vcMedian = median(vcValues)
        if (vcMedian != null && vcMedian > 0) {
            for (sp in points) {
                val vc = sp.point.vConcMl
                if (vc == null || !vc.isFinite() || vc <= 0) continue
                val ratio = vc / vcMedian
                if (ratio < vcLow || ratio > vcHigh) {
                    issues += DataIssue(
                        IssueLevel.Info,
                        "分层一致性：浓缩体积偏离",
                        "站位「$site」点位「${sp.point.label.ifBlank { sp.point.id }}」Vc=$vc mL，较该站位中位数 ${"%.3f".format(vcMedian).trimEnd('0').trimEnd('.')} mL 偏离较大。",
                        pointId = sp.point.id,
                    )
                }
            }
        }

        val countValues = points.map { it.totalCount }.filter { it >= 0 }
        val countMedian = median(countValues.map { it.toDouble() })
        if (countMedian != null && countMedian > 0) {
            for (sp in points) {
                val total = sp.totalCount.toDouble()
                val ratio = total / countMedian
                if (ratio < countLow || ratio > countHigh) {
                    issues += DataIssue(
                        IssueLevel.Info,
                        "分层一致性：计数偏离",
                        "站位「$site」点位「${sp.point.label.ifBlank { sp.point.id }}」总计数=$total，较该站位中位数 ${"%.0f".format(countMedian)} 偏离较大。",
                        pointId = sp.point.id,
                    )
                }
            }
        }
    }

    // Species basic checks
    val nameCounts = mutableMapOf<String, Int>()
    for (sp in dataset.species) {
        for ((pid, c) in sp.countsByPointId) {
            if (!pointIds.contains(pid)) {
                issues += DataIssue(
                    IssueLevel.Error,
                    "数据结构异常",
                    "物种「${sp.nameCn.ifBlank { sp.id }}」包含不存在的点位计数。",
                    speciesId = sp.id,
                )
                continue
            }
            if (c < 0) {
                issues += DataIssue(
                    IssueLevel.Error,
                    "计数为负数",
                    "物种「${sp.nameCn.ifBlank { sp.id }}」在某采样点计数为 $c。",
                    speciesId = sp.id,
                )
            }
        }

        val used = anyCountPositive(sp)
        if (used && sp.nameCn.isBlank()) {
            issues += DataIssue(IssueLevel.Warn, "物种名称为空", "存在计数但物种中文名为空；建议补齐物种名称。", speciesId = sp.id)
        }
        if (used && sp.avgWetWeightMg == null) {
            issues += DataIssue(
                IssueLevel.Warn,
                "缺少平均湿重",
                "物种「${sp.nameCn.ifBlank { sp.id }}」有计数但平均湿重为空；生物量会显示“未查到湿重”。",
                speciesId = sp.id,
            )
        }
        if (used && sp.taxonomy.lvl1.isBlank()) {
            issues += DataIssue(
                IssueLevel.Warn,
                "缺少大类分类",
                "物种「${sp.nameCn.ifBlank { sp.id }}」有计数但未填大类（原生动物/轮虫类/枝角类/桡足类）。",
                speciesId = sp.id,
            )
        }

        val key = sp.nameCn.trim()
        if (key.isNotEmpty()) nameCounts[key] = (nameCounts[key] ?: 0) + 1
    }
    for ((name, n) in nameCounts) {
        if (n > 1) issues += DataIssue(IssueLevel.Warn, "物种名称重复", "中文名「$name」在当前数据集中出现 $n 次，可能影响统计与导出阅读。")
    }

    if (issues.isEmpty()) {
        issues += DataIssue(IssueLevel.Info, "未发现明显问题", "当前数据满足密度/生物量/多样性计算的基础条件。")
    }
    return issues
}
