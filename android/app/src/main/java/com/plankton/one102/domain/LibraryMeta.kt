package com.plankton.one102.domain

data class CustomLibraryMeta(
    val count: Int,
    val updatedAt: String?,
)

data class LibraryMeta(
    val taxonomyVersion: Int,
    val taxonomyCustomCount: Int,
    val taxonomyUpdatedAt: String?,
    val wetWeightVersion: Int,
    val wetWeightCustomCount: Int,
    val wetWeightUpdatedAt: String?,
    val aliasCount: Int,
    val aliasUpdatedAt: String?,
    val aliasSource: String = "本地别名库",
)
