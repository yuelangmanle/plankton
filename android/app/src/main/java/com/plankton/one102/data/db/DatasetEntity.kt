package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "datasets",
    indices = [
        Index(value = ["updatedAt"]),
    ],
)
data class DatasetEntity(
    @PrimaryKey val id: String,
    val titlePrefix: String,
    val createdAt: String,
    val updatedAt: String,
    val readOnly: Boolean,
    val snapshotAt: String?,
    val snapshotSourceId: String?,
    val pointsCount: Int,
    val speciesCount: Int,
    val json: String,
)
