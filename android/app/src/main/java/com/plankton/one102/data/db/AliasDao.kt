package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases ORDER BY alias COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AliasEntity>>

    @Query("SELECT * FROM aliases ORDER BY alias COLLATE NOCASE ASC")
    suspend fun getAll(): List<AliasEntity>

    @Query("SELECT * FROM aliases WHERE alias = :alias LIMIT 1")
    suspend fun getByAlias(alias: String): AliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AliasEntity)

    @Query("DELETE FROM aliases WHERE alias = :alias")
    suspend fun deleteByAlias(alias: String)

    @Query("DELETE FROM aliases")
    suspend fun deleteAll()

    @Query("SELECT COUNT(1) FROM aliases")
    suspend fun countAll(): Int

    @Query("SELECT MAX(updatedAt) FROM aliases")
    suspend fun latestUpdatedAt(): String?
}
