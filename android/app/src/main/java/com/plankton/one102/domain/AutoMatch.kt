package com.plankton.one102.domain

import kotlinx.serialization.Serializable

@Serializable
data class AutoMatchEntry(
    val speciesId: Id,
    val nameCn: String,
    val nameLatin: String = "",
    val wetWeightMg: Double? = null,
    val taxonomy: Taxonomy = Taxonomy(),
)

@Serializable
data class AutoMatchSession(
    val sessionId: String = "",
    val createdAt: String = "",
    val entries: List<AutoMatchEntry> = emptyList(),
)

