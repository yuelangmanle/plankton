package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wetweight_libraries")
data class WetWeightLibraryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
)
