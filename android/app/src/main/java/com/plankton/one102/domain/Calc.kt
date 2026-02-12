package com.plankton.one102.domain

import kotlin.math.ln

sealed interface BiomassCell {
    data class Value(val mgPerL: Double) : BiomassCell

    data object MissingWetWeight : BiomassCell
}

data class PointIndex(
    val pointId: Id,
    val label: String,
    val vConcMl: Double?,
    val vOrigL: Double,
    val totalCount: Int,
    val speciesCountS: Int,
    val H: Double?,
    val J: Double?,
    val D: Double?,
)

data class PerPointSpeciesCalc(
    val count: Int,
    val density: Double?,
    val biomass: BiomassCell?,
    val p: Double?,
    val pLnP: Double?,
    val Y: Double?,
    val isDominant: Boolean?,
)

data class DatasetCalc(
    val pointIndexById: Map<Id, PointIndex>,
    val fiBySpeciesId: Map<Id, Double>,
    val perSpeciesByPoint: Map<Id, Map<Id, PerPointSpeciesCalc>>,
)

private fun safeFinite(value: Double?): Double? {
    if (value == null) return null
    if (!value.isFinite()) return null
    return value
}

fun calcDensityIndPerL(count: Int, vConcMl: Double?, vOrigL: Double): Double? {
    if (count <= 0) return 0.0
    val conc = safeFinite(vConcMl) ?: return null
    if (vOrigL <= 0) return null
    return (count / 1.3) * (conc / vOrigL)
}

fun calcBiomassMgPerL(count: Int, density: Double?, wetWeightMg: Double?): BiomassCell? {
    if (count <= 0) return BiomassCell.Value(0.0)
    if (density == null) return null
    if (wetWeightMg == null) return BiomassCell.MissingWetWeight
    return BiomassCell.Value(density * wetWeightMg)
}

fun calcFiBySpeciesId(dataset: Dataset): Map<Id, Double> {
    val pointCount = dataset.points.size
    val result = mutableMapOf<Id, Double>()
    for (sp in dataset.species) {
        if (pointCount <= 0) {
            result[sp.id] = 0.0
            continue
        }
        var present = 0
        for (p in dataset.points) {
            val count = sp.countsByPointId[p.id] ?: 0
            if (count > 0) present += 1
        }
        result[sp.id] = present.toDouble() / pointCount.toDouble()
    }
    return result
}

fun calcPointTotals(dataset: Dataset, point: Point): Pair<Int, Int> {
    var totalCount = 0
    var s = 0
    for (sp in dataset.species) {
        val count = sp.countsByPointId[point.id] ?: 0
        totalCount += count
        if (count > 0) s += 1
    }
    return totalCount to s
}

fun calcH(dataset: Dataset, point: Point, totalCount: Int): Double? {
    if (totalCount <= 0) return null
    var sum = 0.0
    for (sp in dataset.species) {
        val n = sp.countsByPointId[point.id] ?: 0
        if (n <= 0) continue
        val p = n.toDouble() / totalCount.toDouble()
        if (p <= 0) continue
        sum += p * ln(p)
    }
    return -sum
}

fun calcJ(H: Double?, S: Int): Double? {
    if (H == null) return null
    if (S <= 1) return null
    return H / ln(S.toDouble())
}

fun calcD(totalCount: Int, S: Int): Double? {
    if (S <= 1) return null
    if (totalCount <= 1) return null
    return (S - 1).toDouble() / ln(totalCount.toDouble())
}

fun calcDataset(dataset: Dataset): DatasetCalc {
    val pointIndexById = mutableMapOf<Id, PointIndex>()
    val fiBySpeciesId = calcFiBySpeciesId(dataset)

    val perSpeciesByPoint = mutableMapOf<Id, MutableMap<Id, PerPointSpeciesCalc>>()
    for (sp in dataset.species) perSpeciesByPoint[sp.id] = mutableMapOf()

    val totalsByPointId = mutableMapOf<Id, Pair<Int, Int>>()
    for (p in dataset.points) totalsByPointId[p.id] = calcPointTotals(dataset, p)

    for (p in dataset.points) {
        val (totalCount, s) = totalsByPointId[p.id] ?: (0 to 0)
        val h = calcH(dataset, p, totalCount)
        val j = calcJ(h, s)
        val d = calcD(totalCount, s)

        pointIndexById[p.id] = PointIndex(
            pointId = p.id,
            label = p.label,
            vConcMl = p.vConcMl,
            vOrigL = p.vOrigL,
            totalCount = totalCount,
            speciesCountS = s,
            H = h,
            J = j,
            D = d,
        )
    }

    for (sp in dataset.species) {
        val fi = fiBySpeciesId[sp.id] ?: 0.0
        for (p in dataset.points) {
            val pointTotals = totalsByPointId[p.id] ?: (0 to 0)
            val totalCount = pointTotals.first
            val count = sp.countsByPointId[p.id] ?: 0
            val density = calcDensityIndPerL(count, p.vConcMl, p.vOrigL)
            val biomass = calcBiomassMgPerL(count, density, sp.avgWetWeightMg)

            val prob = if (totalCount > 0) count.toDouble() / totalCount.toDouble() else null
            val pLnP = when {
                prob == null -> null
                prob > 0 -> prob * ln(prob)
                prob == 0.0 -> 0.0
                else -> null
            }

            val y = if (totalCount > 0 && count > 0) (count.toDouble() / totalCount.toDouble()) * fi else null
            val isDominant = y?.let { it > 0.02 }

            perSpeciesByPoint[sp.id]?.set(
                p.id,
                PerPointSpeciesCalc(
                    count = count,
                    density = density,
                    biomass = biomass,
                    p = prob,
                    pLnP = pLnP,
                    Y = y,
                    isDominant = isDominant,
                ),
            )
        }
    }

    return DatasetCalc(
        pointIndexById = pointIndexById,
        fiBySpeciesId = fiBySpeciesId,
        perSpeciesByPoint = perSpeciesByPoint,
    )
}

