package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WetWeightDao {
    @Query("SELECT * FROM wetweights_custom WHERE libraryId = :libraryId ORDER BY nameCn COLLATE NOCASE ASC")
    fun observeByLibrary(libraryId: String): Flow<List<WetWeightEntity>>

    @Query("SELECT * FROM wetweights_custom ORDER BY nameCn COLLATE NOCASE ASC")
    suspend fun getAll(): List<WetWeightEntity>

    @Query("SELECT * FROM wetweights_custom WHERE libraryId = :libraryId ORDER BY nameCn COLLATE NOCASE ASC")
    suspend fun getAllByLibrary(libraryId: String): List<WetWeightEntity>

    @Query("SELECT * FROM wetweights_custom WHERE nameCn = :nameCn AND libraryId = :libraryId LIMIT 1")
    suspend fun getByNameCn(nameCn: String, libraryId: String): WetWeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WetWeightEntity)

    @Query("DELETE FROM wetweights_custom WHERE nameCn = :nameCn AND libraryId = :libraryId")
    suspend fun deleteByNameCn(nameCn: String, libraryId: String)

    @Query("DELETE FROM wetweights_custom WHERE origin = :origin AND libraryId = :libraryId")
    suspend fun deleteByOrigin(origin: String, libraryId: String)

    @Query("DELETE FROM wetweights_custom WHERE origin = :origin AND importBatchId = :batchId AND libraryId = :libraryId")
    suspend fun deleteImportedBatch(origin: String, batchId: String, libraryId: String)

    @Query("SELECT COUNT(1) FROM wetweights_custom WHERE origin = :origin AND libraryId = :libraryId")
    suspend fun countByOrigin(origin: String, libraryId: String): Int

    @Query("SELECT COUNT(1) FROM wetweights_custom WHERE libraryId = :libraryId")
    suspend fun countByLibrary(libraryId: String): Int

    @Query("SELECT COUNT(1) FROM wetweights_custom")
    suspend fun countAll(): Int

    @Query("SELECT MAX(updatedAt) FROM wetweights_custom WHERE libraryId = :libraryId")
    suspend fun latestUpdatedAt(libraryId: String): String?
}
