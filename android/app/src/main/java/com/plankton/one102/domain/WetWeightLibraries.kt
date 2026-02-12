package com.plankton.one102.domain

const val DEFAULT_WET_WEIGHT_LIBRARY_ID: String = "custom_default"
const val DEFAULT_WET_WEIGHT_LIBRARY_NAME: String = "自定义库"

data class WetWeightLibrary(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
)
