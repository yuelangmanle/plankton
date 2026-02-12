package com.plankton.one102.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.plankton.one102.domain.DatasetSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetDao {
    @Query("SELECT * FROM datasets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DatasetEntity>>

    @Query("SELECT * FROM datasets ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DatasetEntity>

    @Query(
        """
        SELECT id, titlePrefix, createdAt, updatedAt, readOnly, snapshotAt, snapshotSourceId, pointsCount, speciesCount
        FROM datasets
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeSummaries(limit: Int): Flow<List<DatasetSummary>>

    @Query(
        """
        SELECT id, titlePrefix, createdAt, updatedAt, readOnly, snapshotAt, snapshotSourceId, pointsCount, speciesCount
        FROM datasets
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getSummaries(limit: Int): List<DatasetSummary>

    @Query("SELECT COUNT(1) FROM datasets")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(1) FROM datasets")
    suspend fun countAll(): Int

    @Query("SELECT id FROM datasets ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestId(): String?

    @Query("SELECT COUNT(1) FROM datasets WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("SELECT * FROM datasets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DatasetEntity?

    @Query("SELECT * FROM datasets WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DatasetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DatasetEntity)

    @Query("DELETE FROM datasets WHERE id = :id")
    suspend fun deleteById(id: String)
}
