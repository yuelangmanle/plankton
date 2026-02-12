package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aliases")
data class AliasEntity(
    @PrimaryKey val alias: String,
    val canonical: String,
    val updatedAt: String,
)
