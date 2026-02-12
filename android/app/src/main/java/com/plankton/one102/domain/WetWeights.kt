package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class WetWeightTaxonomy(
    val group: String? = null,
    val sub: String? = null,
)

@Serializable
data class WetWeightEntry(
    val nameCn: String,
    val nameLatin: String? = null,
    val wetWeightMg: Double,
    val taxonomy: WetWeightTaxonomy = WetWeightTaxonomy(),
)

@Serializable
data class WetWeightsJson(
    val version: Int = 1,
    val entries: List<WetWeightEntry> = emptyList(),
)

