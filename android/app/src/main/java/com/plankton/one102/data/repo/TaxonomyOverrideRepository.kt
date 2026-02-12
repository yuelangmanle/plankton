package com.plankton.one102.data.repo

import com.plankton.one102.data.db.TaxonomyDao
import com.plankton.one102.data.db.TaxonomyEntity
import com.plankton.one102.domain.CustomLibraryMeta
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyRecord
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class TaxonomyOverrideRepository(
    private val dao: TaxonomyDao,
) {
    fun observeCustomEntries(): Flow<List<TaxonomyRecord>> {
        return dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getCustomEntries(): List<TaxonomyRecord> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }.sortedWith { a, b -> collator.compare(a.nameCn, b.nameCn) }
    }

    suspend fun findCustomByNameCn(nameCn: String): TaxonomyRecord? = withContext(Dispatchers.IO) {
        val key = nameCn.trim()
        if (key.isEmpty()) return@withContext null
        dao.getByNameCn(key)?.toDomain()
    }

    suspend fun upsertCustom(record: TaxonomyRecord) {
        val now = nowIso()
        val e = TaxonomyEntity(
            nameCn = record.nameCn.trim(),
            nameLatin = record.nameLatin?.trim().takeIf { !it.isNullOrBlank() },
            lvl1 = record.taxonomy.lvl1.trim().takeIf { it.isNotBlank() },
            lvl2 = record.taxonomy.lvl2.trim().takeIf { it.isNotBlank() },
            lvl3 = record.taxonomy.lvl3.trim().takeIf { it.isNotBlank() },
            lvl4 = record.taxonomy.lvl4.trim().takeIf { it.isNotBlank() },
            lvl5 = record.taxonomy.lvl5.trim().takeIf { it.isNotBlank() },
            updatedAt = now,
        )
        dao.upsert(e)
    }

    suspend fun deleteCustom(nameCn: String) {
        dao.deleteByNameCn(nameCn.trim())
    }

    suspend fun getCustomMeta(): CustomLibraryMeta = withContext(Dispatchers.IO) {
        CustomLibraryMeta(
            count = dao.countAll(),
            updatedAt = dao.latestUpdatedAt(),
        )
    }

    private fun TaxonomyEntity.toDomain(): TaxonomyRecord {
        return TaxonomyRecord(
            nameCn = nameCn,
            nameLatin = nameLatin,
            taxonomy = Taxonomy(
                lvl1 = lvl1.orEmpty(),
                lvl2 = lvl2.orEmpty(),
                lvl3 = lvl3.orEmpty(),
                lvl4 = lvl4.orEmpty(),
                lvl5 = lvl5.orEmpty(),
            ),
        )
    }

    companion object {
        private val collator: Collator = Collator.getInstance(Locale.CHINA)
    }
}
