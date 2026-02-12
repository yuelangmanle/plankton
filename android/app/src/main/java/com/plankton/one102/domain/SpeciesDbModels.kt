package com.plankton.one102.domain

data class SpeciesDbSources(
    val builtinTaxonomy: Boolean = false,
    val builtinWetWeight: Boolean = false,
    val customTaxonomy: Boolean = false,
    val customWetWeight: Boolean = false,
)

data class SpeciesDbItem(
    val nameCn: String,
    val nameLatin: String? = null,
    val wetWeightMg: Double? = null,
    val taxonomy: Taxonomy? = null,
    val sources: SpeciesDbSources = SpeciesDbSources(),
)

