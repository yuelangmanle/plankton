package com.plankton.one102.data.repo

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.data.db.DatasetDao
import com.plankton.one102.data.db.DatasetEntity
import com.plankton.one102.export.writeFileToSafAtomic
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DatasetSummary
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.createDefaultDataset
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.nowIso
import com.plankton.one102.domain.touchDataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DatasetRepository(private val dao: DatasetDao) {
    fun observeAll(): Flow<List<Dataset>> {
        return dao.observeAll().map { list -> list.mapNotNull { decode(it) } }
    }

    suspend fun getAll(): List<Dataset> = dao.getAll().mapNotNull { decode(it) }

    fun observeSummaries(limit: Int): Flow<List<DatasetSummary>> = dao.observeSummaries(limit)

    suspend fun getSummaries(limit: Int): List<DatasetSummary> = dao.getSummaries(limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun countAll(): Int = dao.countAll()

    suspend fun getLatestId(): String? = dao.getLatestId()

    suspend fun exists(id: String): Boolean = dao.exists(id) > 0

    suspend fun getById(id: String): Dataset? = dao.getById(id)?.let { decode(it) }

    fun observeById(id: String): Flow<Dataset?> {
        return dao.observeById(id).map { it?.let { entity -> decode(entity) } }
    }

    suspend fun createNew(settings: Settings): Dataset {
        val ds = createDefaultDataset(settings)
        save(ds)
        return ds
    }

    suspend fun save(dataset: Dataset) {
        val touched = touchDataset(dataset)
        val json = AppJson.encodeToString(Dataset.serializer(), touched)
        val entity = DatasetEntity(
            id = touched.id,
            titlePrefix = touched.titlePrefix,
            createdAt = touched.createdAt,
            updatedAt = touched.updatedAt,
            readOnly = touched.readOnly,
            snapshotAt = touched.snapshotAt,
            snapshotSourceId = touched.snapshotSourceId,
            pointsCount = touched.points.size,
            speciesCount = touched.species.size,
            json = json,
        )
        dao.upsert(entity)
    }

    suspend fun deleteById(id: String) {
        dao.deleteById(id)
    }

    suspend fun duplicate(sourceId: String): Dataset? {
        val src = getById(sourceId) ?: return null
        val now = nowIso()
        val copied = src.copy(
            id = newId(),
            createdAt = now,
            updatedAt = now,
            readOnly = false,
            snapshotAt = null,
            snapshotSourceId = null,
        )
        save(copied)
        return copied
    }

    suspend fun createSnapshot(source: Dataset, reason: String): Dataset {
        val now = nowIso()
        val title = buildSnapshotTitle(source.titlePrefix, reason, now)
        val snapshot = source.copy(
            id = newId(),
            titlePrefix = title,
            createdAt = now,
            updatedAt = now,
            readOnly = true,
            snapshotAt = now,
            snapshotSourceId = source.id,
        )
        save(snapshot)
        return snapshot
    }

    @Serializable
    data class BackupFileV1(
        val version: Int = 1,
        val datasets: List<Dataset>,
    )

    @Suppress("UNUSED_PARAMETER")
    suspend fun exportBackup(context: Context, contentResolver: ContentResolver, uri: Uri) {
        val datasets = getAll()
        val backup = BackupFileV1(datasets = datasets)
        val raw = AppJson.encodeToString(BackupFileV1.serializer(), backup)

        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val tempFile = File.createTempFile("backup-datasets-", ".json", dir)
            withContext(Dispatchers.IO) {
                tempFile.outputStream().use { out ->
                    out.write(raw.toByteArray(Charsets.UTF_8))
                }
            }
            writeFileToSafAtomic(context, uri, tempFile)
            runCatching { tempFile.delete() }
        } catch (e: Exception) {
            AppLogger.logError(context, "DatasetBackup", "导出数据集备份失败", e)
            throw e
        }
    }

    suspend fun importBackup(contentResolver: ContentResolver, uri: Uri): Int {
        val raw = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法打开输入流")
        }.toString(Charsets.UTF_8)

        val backup = AppJson.decodeFromString(BackupFileV1.serializer(), raw)
        var imported = 0
        for (ds in backup.datasets) {
            val exists = dao.getById(ds.id) != null
            val final = if (exists) {
                val now = nowIso()
                ds.copy(id = newId(), createdAt = now, updatedAt = now)
            } else {
                ds
            }
            save(final)
            imported += 1
        }
        return imported
    }

    private fun decode(entity: DatasetEntity): Dataset? {
        return runCatching { AppJson.decodeFromString(Dataset.serializer(), entity.json) }.getOrNull()
    }
}

private val snapshotFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun buildSnapshotTitle(prefix: String, reason: String, nowIso: String): String {
    val reasonText = reason.trim().ifBlank { "导出" }
    val stamp = runCatching { snapshotFormatter.format(Instant.parse(nowIso)) }.getOrElse { nowIso.take(16).replace("T", " ") }
    val base = prefix.trim().ifBlank { "未命名" }
    return "快照 $stamp $reasonText - $base"
}
