package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiCacheEntity>>

    @Query("SELECT * FROM ai_cache ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AiCacheEntity>

    @Query("SELECT * FROM ai_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AiCacheEntity)

    @Query("DELETE FROM ai_cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM ai_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM ai_cache WHERE purpose = :purpose")
    suspend fun deleteByPurpose(purpose: String)
}
