package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WetWeightLibraryDao {
    @Query("SELECT * FROM wetweight_libraries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WetWeightLibraryEntity>>

    @Query("SELECT * FROM wetweight_libraries ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WetWeightLibraryEntity>

    @Query("SELECT * FROM wetweight_libraries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WetWeightLibraryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WetWeightLibraryEntity)

    @Query("DELETE FROM wetweight_libraries WHERE id = :id")
    suspend fun deleteById(id: String)
}
