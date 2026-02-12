package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxonomyDao {
    @Query("SELECT * FROM taxonomies_custom ORDER BY nameCn COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TaxonomyEntity>>

    @Query("SELECT * FROM taxonomies_custom ORDER BY nameCn COLLATE NOCASE ASC")
    suspend fun getAll(): List<TaxonomyEntity>

    @Query("SELECT * FROM taxonomies_custom WHERE nameCn = :nameCn LIMIT 1")
    suspend fun getByNameCn(nameCn: String): TaxonomyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaxonomyEntity)

    @Query("DELETE FROM taxonomies_custom WHERE nameCn = :nameCn")
    suspend fun deleteByNameCn(nameCn: String)

    @Query("SELECT COUNT(1) FROM taxonomies_custom")
    suspend fun countAll(): Int

    @Query("SELECT MAX(updatedAt) FROM taxonomies_custom")
    suspend fun latestUpdatedAt(): String?
}
