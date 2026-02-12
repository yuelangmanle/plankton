package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class TaxonomyEntry(
    val nameCn: String,
    val nameLatin: String? = null,
    val taxonomy: Taxonomy = Taxonomy(),
)

@Serializable
data class TaxonomiesJson(
    val version: Int = 1,
    val entries: List<TaxonomyEntry> = emptyList(),
)

