package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_cache",
    indices = [
        Index(value = ["updatedAt"]),
    ],
)
data class AiCacheEntity(
    @PrimaryKey val key: String,
    val purpose: String,
    val apiTag: String,
    val nameCn: String,
    val nameLatin: String?,
    val wetWeightMg: Double?,
    val lvl1: String?,
    val lvl2: String?,
    val lvl3: String?,
    val lvl4: String?,
    val lvl5: String?,
    val prompt: String,
    val raw: String,
    val updatedAt: String,
)
