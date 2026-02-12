package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val id: Id,
    val label: String,
    val vConcMl: Double? = null,
    val vOrigL: Double,
    val site: String? = null,
    val depthM: Double? = null,
)

@Serializable
data class Species(
    val id: Id,
    val nameCn: String = "",
    val nameLatin: String = "",
    val taxonomy: Taxonomy = DEFAULT_TAXONOMY,
    val avgWetWeightMg: Double? = null,
    val countsByPointId: Map<Id, Int> = emptyMap(),
)

@Serializable
data class Dataset(
    val id: Id,
    val titlePrefix: String = "",
    val createdAt: String,
    val updatedAt: String,
    val points: List<Point>,
    val species: List<Species>,
    val stratification: StratificationConfig = StratificationConfig(),
    val lastAutoMatch: AutoMatchSession? = null,
    val readOnly: Boolean = false,
    val snapshotAt: String? = null,
    val snapshotSourceId: Id? = null,
    val ignoredIssueKeys: List<String> = emptyList(),
)

data class DatasetSummary(
    val id: Id,
    val titlePrefix: String,
    val createdAt: String,
    val updatedAt: String,
    val readOnly: Boolean,
    val snapshotAt: String?,
    val snapshotSourceId: Id?,
    val pointsCount: Int,
    val speciesCount: Int,
)

fun createDefaultDataset(settings: Settings = DEFAULT_SETTINGS): Dataset {
    val createdAt = nowIso()
    val point1 = Point(
        id = newId(),
        label = "1",
        vConcMl = null,
        vOrigL = settings.defaultVOrigL,
        site = "1",
        depthM = null,
    )
    return Dataset(
        id = newId(),
        titlePrefix = "",
        createdAt = createdAt,
        updatedAt = createdAt,
        points = listOf(point1),
        species = emptyList(),
    )
}

fun touchDataset(dataset: Dataset): Dataset = dataset.copy(updatedAt = nowIso())

fun createBlankPoint(settings: Settings, nextLabel: String, id: Id = newId()): Point {
    val (site, depthM) = resolveSiteAndDepthForPoint(label = nextLabel, site = null, depthM = null)
    return Point(
        id = id,
        label = nextLabel,
        vConcMl = null,
        vOrigL = settings.defaultVOrigL,
        site = site,
        depthM = depthM,
    )
}

fun createBlankSpecies(pointIds: List<Id>): Species = Species(
    id = newId(),
    nameCn = "",
    nameLatin = "",
    taxonomy = DEFAULT_TAXONOMY,
    avgWetWeightMg = null,
    countsByPointId = pointIds.associateWith { 0 },
)
