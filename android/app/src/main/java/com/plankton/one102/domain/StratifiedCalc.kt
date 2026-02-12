package com.plankton.one102.domain

import kotlin.math.ln

enum class WaterLayer(val label: String) {
    Upper("上层"),
    Middle("中层"),
    Lower("下层"),
}

data class StratifiedKey(
    val layer: WaterLayer,
    val site: String,
)

data class StratifiedSiteLayerIndex(
    val key: StratifiedKey,
    val totalCount: Int,
    val speciesCountS: Int,
    val H: Double?,
    val J: Double?,
    val D: Double?,
)

data class StratifiedSpeciesDominance(
    val key: StratifiedKey,
    val speciesId: Id,
    val count: Int,
    val p: Double?,
    val fi: Double?,
    val Y: Double?,
    val isDominant: Boolean?,
)

private fun resolveLayer(depthM: Double, cfg: StratificationConfig): WaterLayer? {
    if (!depthM.isFinite()) return null
    val u = cfg.upper
    val m = cfg.middle
    val l = cfg.lower

    // Use inclusive ranges and a stable priority order.
    // Example: upper 0–10 (含10), middle 10–30, lower >30 can be expressed as:
    // upper=[0,10], middle=[10,30], lower=[30,∞), then "10" belongs to upper and "30" belongs to middle.
    if (depthM >= u.minM && depthM <= u.maxM) return WaterLayer.Upper
    if (depthM >= m.minM && depthM <= m.maxM) return WaterLayer.Middle
    if (depthM >= l.minM && depthM <= l.maxM) return WaterLayer.Lower
    return null
}

fun buildStratifiedPointGroups(dataset: Dataset): Map<StratifiedKey, List<Point>> {
    val cfg = dataset.stratification
    if (!cfg.enabled) return emptyMap()

    val map = linkedMapOf<StratifiedKey, MutableList<Point>>()
    for (p in dataset.points) {
        val (siteRaw, depth) = resolveSiteAndDepthForPoint(label = p.label, site = p.site, depthM = p.depthM)
        val site = siteRaw?.trim()?.takeIf { it.isNotBlank() } ?: "未命名"
        val d = depth ?: continue
        val layer = resolveLayer(d, cfg) ?: continue
        map.getOrPut(StratifiedKey(layer = layer, site = site)) { mutableListOf() }.add(p)
    }
    return map
}

private fun calcHFromCounts(counts: Iterable<Int>, total: Int): Double? {
    if (total <= 0) return null
    var sum = 0.0
    for (n in counts) {
        if (n <= 0) continue
        val p = n.toDouble() / total.toDouble()
        if (p <= 0) continue
        sum += p * ln(p)
    }
    return -sum
}

fun calcStratifiedIndices(dataset: Dataset, groups: Map<StratifiedKey, List<Point>> = buildStratifiedPointGroups(dataset)): List<StratifiedSiteLayerIndex> {
    if (!dataset.stratification.enabled) return emptyList()

    val out = mutableListOf<StratifiedSiteLayerIndex>()
    for ((key, pts) in groups) {
        val countsBySpecies = dataset.species.associate { sp ->
            val sum = pts.sumOf { p -> sp.countsByPointId[p.id] ?: 0 }
            sp.id to sum
        }
        val total = countsBySpecies.values.sum()
        val s = countsBySpecies.values.count { it > 0 }
        val h = calcHFromCounts(countsBySpecies.values, total)
        out += StratifiedSiteLayerIndex(
            key = key,
            totalCount = total,
            speciesCountS = s,
            H = h,
            J = calcJ(h, s),
            D = calcD(total, s),
        )
    }
    return out
}

fun calcFiByLayerBySpeciesId(
    dataset: Dataset,
    groups: Map<StratifiedKey, List<Point>> = buildStratifiedPointGroups(dataset),
): Map<WaterLayer, Map<Id, Double>> {
    if (!dataset.stratification.enabled) return emptyMap()

    val sitesByLayer = groups.keys.groupBy({ it.layer }, { it.site }).mapValues { (_, sites) -> sites.toSet() }
    val countsByKeyBySpeciesId: Map<StratifiedKey, Map<Id, Int>> = groups.mapValues { (_, pts) ->
        dataset.species.associate { sp ->
            val sum = pts.sumOf { p -> sp.countsByPointId[p.id] ?: 0 }
            sp.id to sum
        }
    }

    val out = mutableMapOf<WaterLayer, MutableMap<Id, Double>>()
    for ((layer, sites) in sitesByLayer) {
        val totalSites = sites.size
        val bySpecies = mutableMapOf<Id, Double>()
        for (sp in dataset.species) {
            if (totalSites <= 0) {
                bySpecies[sp.id] = 0.0
                continue
            }
            var present = 0
            for (site in sites) {
                val key = StratifiedKey(layer = layer, site = site)
                val n = countsByKeyBySpeciesId[key]?.get(sp.id) ?: 0
                if (n > 0) present += 1
            }
            bySpecies[sp.id] = present.toDouble() / totalSites.toDouble()
        }
        out[layer] = bySpecies
    }
    return out
}

fun calcStratifiedDominance(
    dataset: Dataset,
    groups: Map<StratifiedKey, List<Point>> = buildStratifiedPointGroups(dataset),
): List<StratifiedSpeciesDominance> {
    if (!dataset.stratification.enabled) return emptyList()

    val indices = calcStratifiedIndices(dataset, groups).associateBy { it.key }
    val fiByLayer = calcFiByLayerBySpeciesId(dataset, groups)

    val out = mutableListOf<StratifiedSpeciesDominance>()
    for ((key, pts) in groups) {
        val idx = indices[key]
        val total = idx?.totalCount ?: pts.sumOf { p -> dataset.species.sumOf { sp -> sp.countsByPointId[p.id] ?: 0 } }
        val fiMap = fiByLayer[key.layer].orEmpty()
        for (sp in dataset.species) {
            val n = pts.sumOf { p -> sp.countsByPointId[p.id] ?: 0 }
            val p = if (total > 0 && n > 0) n.toDouble() / total.toDouble() else null
            val fi = fiMap[sp.id]
            val y = if (p != null && fi != null) p * fi else null
            out += StratifiedSpeciesDominance(
                key = key,
                speciesId = sp.id,
                count = n,
                p = p,
                fi = fi,
                Y = y,
                isDominant = y?.let { it > 0.02 },
            )
        }
    }
    return out
}
