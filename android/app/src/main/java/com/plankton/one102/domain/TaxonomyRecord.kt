package com.plankton.one102.domain

data class TaxonomyRecord(
    val nameCn: String,
    val nameLatin: String? = null,
    val taxonomy: Taxonomy = Taxonomy(),
)

