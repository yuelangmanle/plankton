package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class ImportedPoint(
    val label: String,
    val vConcMl: Double? = null,
    val vOrigL: Double? = null,
)

fun applyImportedPoints(
    dataset: Dataset,
    imported: List<ImportedPoint>,
    defaultVOrigL: Double,
): Dataset {
    if (imported.isEmpty()) return dataset

    val existingByLabel = dataset.points.associateBy { it.label.trim() }
    val nextPoints = imported.map { row ->
        val rawLabel = row.label.trim()
        val (parsedSite, parsedDepth) = resolveSiteAndDepthForPoint(label = rawLabel, site = null, depthM = null)

        val (label, site, depthM) = if (dataset.stratification.enabled) {
            val s = parsedSite ?: rawLabel.substringBefore("-", missingDelimiterValue = rawLabel).trim().ifBlank { rawLabel }.ifBlank { "未命名" }
            val d = parsedDepth ?: 0.0
            Triple(buildStratifiedLabel(s, d), s, d)
        } else {
            Triple(rawLabel, parsedSite, parsedDepth)
        }

        val existing = existingByLabel[rawLabel] ?: existingByLabel[label]
        val id = existing?.id ?: newId()
        Point(
            id = id,
            label = label,
            vConcMl = row.vConcMl,
            vOrigL = row.vOrigL ?: defaultVOrigL,
            site = site,
            depthM = depthM,
        )
    }

    val nextSpecies = dataset.species.map { sp ->
        val nextCounts = nextPoints.associate { p -> p.id to (sp.countsByPointId[p.id] ?: 0) }
        sp.copy(countsByPointId = nextCounts)
    }

    return dataset.copy(points = nextPoints, species = nextSpecies)
}
