package com.plankton.one102.domain

data class MergeResult(
    val dataset: Dataset,
    val mergedCount: Int,
)

enum class MergeCountsMode {
    Sum,
    Max,
}

fun mergeDuplicateSpeciesByName(dataset: Dataset, mode: MergeCountsMode = MergeCountsMode.Sum): MergeResult {
    val groups = LinkedHashMap<String, MutableList<Species>>()
    for (sp in dataset.species) {
        val key = sp.nameCn.trim()
        if (key.isEmpty()) continue
        groups.getOrPut(key) { mutableListOf() }.add(sp)
    }

    val duplicates = groups.filterValues { it.size > 1 }
    if (duplicates.isEmpty()) return MergeResult(dataset = dataset, mergedCount = 0)

    val handled = HashSet<Id>()
    val nextSpecies = mutableListOf<Species>()
    var merged = 0

    fun mergeTaxonomy(list: List<Taxonomy>): Taxonomy {
        var lvl1 = ""
        var lvl2 = ""
        var lvl3 = ""
        var lvl4 = ""
        var lvl5 = ""
        for (t in list) {
            if (lvl1.isBlank() && t.lvl1.isNotBlank()) lvl1 = t.lvl1
            if (lvl2.isBlank() && t.lvl2.isNotBlank()) lvl2 = t.lvl2
            if (lvl3.isBlank() && t.lvl3.isNotBlank()) lvl3 = t.lvl3
            if (lvl4.isBlank() && t.lvl4.isNotBlank()) lvl4 = t.lvl4
            if (lvl5.isBlank() && t.lvl5.isNotBlank()) lvl5 = t.lvl5
        }
        return Taxonomy(lvl1 = lvl1, lvl2 = lvl2, lvl3 = lvl3, lvl4 = lvl4, lvl5 = lvl5)
    }

    for (sp in dataset.species) {
        if (!handled.add(sp.id)) continue
        val key = sp.nameCn.trim()
        val group = duplicates[key]
        if (group == null) {
            nextSpecies += sp
            continue
        }
        group.forEach { handled.add(it.id) }

        val base = group.first()
        val latin = group.firstOrNull { it.nameLatin.isNotBlank() }?.nameLatin.orEmpty()
        val wet = group.firstOrNull { it.avgWetWeightMg != null }?.avgWetWeightMg
        val tax = mergeTaxonomy(group.map { it.taxonomy })

        val counts = buildMap {
            for (p in dataset.points) {
                val values = group.map { it.countsByPointId[p.id] ?: 0 }
                val mergedValue = when (mode) {
                    MergeCountsMode.Sum -> values.sum()
                    MergeCountsMode.Max -> values.maxOrNull() ?: 0
                }
                put(p.id, mergedValue)
            }
        }

        nextSpecies += base.copy(
            nameLatin = latin,
            taxonomy = tax,
            avgWetWeightMg = wet,
            countsByPointId = counts,
        )

        merged += (group.size - 1)
    }

    return MergeResult(
        dataset = dataset.copy(species = nextSpecies),
        mergedCount = merged,
    )
}
