package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "taxonomies_custom")
data class TaxonomyEntity(
    @PrimaryKey val nameCn: String,
    val nameLatin: String?,
    val lvl1: String?,
    val lvl2: String?,
    val lvl3: String?,
    val lvl4: String?,
    val lvl5: String?,
    val updatedAt: String,
)

