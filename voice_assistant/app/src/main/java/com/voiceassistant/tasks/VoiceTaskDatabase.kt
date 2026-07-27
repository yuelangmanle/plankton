package com.voiceassistant.tasks

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

internal enum class VoiceTaskStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, NEEDS_REVIEW }

@Entity(tableName = "voice_tasks")
internal data class VoiceTaskEntity(
    @PrimaryKey val id: String,
    val status: VoiceTaskStatus,
    val audioPath: String?,
    val returnAction: String?,
    val returnPackage: String?,
    val partnerSessionId: String?,
    val overridesPayload: String?,
    val transcript: String?,
    val errorMessage: String?,
    val createdAtMs: Long,
    val finishedAtMs: Long?,
)

@Dao
internal interface VoiceTaskDao {
    @Query("SELECT * FROM voice_tasks ORDER BY createdAtMs DESC") fun observeAll(): Flow<List<VoiceTaskEntity>>
    @Query("SELECT * FROM voice_tasks WHERE id = :id") suspend fun get(id: String): VoiceTaskEntity?
    @Query("SELECT * FROM voice_tasks WHERE status = 'QUEUED' ORDER BY createdAtMs ASC LIMIT 1") suspend fun nextQueued(): VoiceTaskEntity?
    @Query("SELECT * FROM voice_tasks WHERE status = 'QUEUED' ORDER BY createdAtMs ASC") suspend fun queued(): List<VoiceTaskEntity>
    @androidx.room.Upsert suspend fun upsert(task: VoiceTaskEntity)
    @Query("UPDATE voice_tasks SET status = :status, transcript = :transcript, errorMessage = :errorMessage, finishedAtMs = :finishedAtMs WHERE id = :id") suspend fun finish(id: String, status: VoiceTaskStatus, transcript: String?, errorMessage: String?, finishedAtMs: Long?)
    @Query("UPDATE voice_tasks SET status = 'RUNNING' WHERE id = :id AND status = 'QUEUED'") suspend fun markRunning(id: String): Int
    @Query("UPDATE voice_tasks SET status = 'QUEUED' WHERE status = 'RUNNING'") suspend fun recoverRunning(): Int
    @Query("UPDATE voice_tasks SET audioPath = NULL WHERE finishedAtMs IS NOT NULL AND finishedAtMs < :cutoff") suspend fun clearExpiredAudio(cutoff: Long): Int
}

@Database(entities = [VoiceTaskEntity::class], version = 2, exportSchema = true)
internal abstract class VoiceTaskDatabase : RoomDatabase() {
    abstract fun tasks(): VoiceTaskDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE voice_tasks ADD COLUMN overridesPayload TEXT")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, VoiceTaskDatabase::class.java, "voice_tasks.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
