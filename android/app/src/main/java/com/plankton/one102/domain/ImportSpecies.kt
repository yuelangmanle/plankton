package com.plankton.one102.domain

data class ImportedSpecies(
    val nameCn: String,
    val nameLatin: String? = null,
    val wetWeightMg: Double? = null,
    val taxonomy: Taxonomy? = null,
    val aliases: List<String> = emptyList(),
)
