package com.plankton.one102.domain

import kotlin.math.abs

data class CalcDiffItem(
    val kind: String,
    val pointLabel: String,
    val speciesName: String? = null,
    val internalValue: String,
    val apiValue: String,
)

data class CalcDiffReport(
    val totalChecked: Int,
    val mismatchCount: Int,
    val items: List<CalcDiffItem>,
)

private fun fmtNumber(v: Double?): String {
    if (v == null || !v.isFinite()) return "（空）"
    return String.format("%.8f", v).trimEnd('0').trimEnd('.')
}

private fun biomassToNumber(v: BiomassCell?): Double? {
    return when (v) {
        null -> null
        is BiomassCell.Value -> v.mgPerL
        BiomassCell.MissingWetWeight -> null
    }
}

private fun biomassToText(v: BiomassCell?): String {
    return when (v) {
        null -> "（空）"
        is BiomassCell.Value -> fmtNumber(v.mgPerL)
        BiomassCell.MissingWetWeight -> "未查到湿重"
    }
}

private fun nearlyEqual(a: Double?, b: Double?, tol: Double): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    if (!a.isFinite() || !b.isFinite()) return false
    return abs(a - b) <= tol
}

fun diffCalc(
    dataset: Dataset,
    internal: DatasetCalc,
    api: DatasetCalc,
    tol: Double = 1e-4,
    maxItems: Int = 60,
): CalcDiffReport {
    val items = mutableListOf<CalcDiffItem>()
    var totalChecked = 0

    for (p in dataset.points) {
        val piInternal = internal.pointIndexById[p.id]
        val piApi = api.pointIndexById[p.id]
        fun checkPoint(kind: String, a: Double?, b: Double?) {
            totalChecked += 1
            if (!nearlyEqual(a, b, tol)) {
                items += CalcDiffItem(
                    kind = kind,
                    pointLabel = p.label.ifBlank { p.id },
                    internalValue = fmtNumber(a),
                    apiValue = fmtNumber(b),
                )
            }
        }
        checkPoint("H'", piInternal?.H, piApi?.H)
        checkPoint("J", piInternal?.J, piApi?.J)
        checkPoint("D", piInternal?.D, piApi?.D)
    }

    for (sp in dataset.species) {
        val spName = sp.nameCn.ifBlank { sp.id }
        val mapInternal = internal.perSpeciesByPoint[sp.id].orEmpty()
        val mapApi = api.perSpeciesByPoint[sp.id].orEmpty()
        for (p in dataset.points) {
            val a = mapInternal[p.id]
            val b = mapApi[p.id]
            fun check(kind: String, va: Double?, vb: Double?) {
                totalChecked += 1
                if (!nearlyEqual(va, vb, tol)) {
                    items += CalcDiffItem(
                        kind = kind,
                        pointLabel = p.label.ifBlank { p.id },
                        speciesName = spName,
                        internalValue = fmtNumber(va),
                        apiValue = fmtNumber(vb),
                    )
                }
            }
            check("density", a?.density, b?.density)
            check("p*ln(p)", a?.pLnP, b?.pLnP)
            check("Y", a?.Y, b?.Y)

            totalChecked += 1
            val ba = a?.biomass
            val bb = b?.biomass
            val sameBio = when {
                ba is BiomassCell.MissingWetWeight && bb is BiomassCell.MissingWetWeight -> true
                else -> nearlyEqual(biomassToNumber(ba), biomassToNumber(bb), tol)
            }
            if (!sameBio) {
                items += CalcDiffItem(
                    kind = "biomass",
                    pointLabel = p.label.ifBlank { p.id },
                    speciesName = spName,
                    internalValue = biomassToText(ba),
                    apiValue = biomassToText(bb),
                )
            }
        }
    }

    val shown = if (items.size > maxItems) items.take(maxItems) else items
    return CalcDiffReport(
        totalChecked = totalChecked,
        mismatchCount = items.size,
        items = shown,
    )
}
