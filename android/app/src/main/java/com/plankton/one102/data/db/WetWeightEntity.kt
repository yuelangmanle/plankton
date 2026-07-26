package com.plankton.one102.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(
    tableName = "wetweights_custom",
    primaryKeys = ["libraryId", "nameCn"],
    indices = [Index(value = ["libraryId"])],
)
data class WetWeightEntity(
    val nameCn: String,
    val nameLatin: String?,
    val wetWeightMg: Double,
    val groupName: String?,
    val subName: String?,
    @ColumnInfo(defaultValue = "'manual'") val origin: String,
    val importBatchId: String?,
    val updatedAt: String,
    @ColumnInfo(defaultValue = "'custom_default'") val libraryId: String,
)
