package com.plankton.one102.data.repo

import com.plankton.one102.data.db.AiCacheDao
import com.plankton.one102.data.db.AiCacheEntity
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiCacheRepository(private val dao: AiCacheDao) {
    fun observeAll(): Flow<List<AiCacheEntity>> = dao.observeAll()

    suspend fun getByKey(key: String): AiCacheEntity? = withContext(Dispatchers.IO) { dao.getByKey(key) }

    suspend fun getSpeciesInfo(apiTag: String, nameCn: String): AiCacheEntity? {
        return getByKey(keyForPurpose(PURPOSE_SPECIES_INFO, apiTag, nameCn))
    }

    suspend fun upsertSpeciesInfo(
        apiTag: String,
        nameCn: String,
        nameLatin: String?,
        wetWeightMg: Double?,
        taxonomy: Taxonomy?,
        prompt: String,
        raw: String,
    ) {
        val key = keyForPurpose(PURPOSE_SPECIES_INFO, apiTag, nameCn)
        val now = nowIso()
        val t = taxonomy ?: Taxonomy()
        val entity = AiCacheEntity(
            key = key,
            purpose = PURPOSE_SPECIES_INFO,
            apiTag = apiTag,
            nameCn = nameCn.trim(),
            nameLatin = nameLatin?.trim().takeIf { !it.isNullOrBlank() },
            wetWeightMg = wetWeightMg,
            lvl1 = t.lvl1.takeIf { it.isNotBlank() },
            lvl2 = t.lvl2.takeIf { it.isNotBlank() },
            lvl3 = t.lvl3.takeIf { it.isNotBlank() },
            lvl4 = t.lvl4.takeIf { it.isNotBlank() },
            lvl5 = t.lvl5.takeIf { it.isNotBlank() },
            prompt = prompt,
            raw = raw,
            updatedAt = now,
        )
        withContext(Dispatchers.IO) { dao.upsert(entity) }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun deleteSpeciesInfo() = withContext(Dispatchers.IO) { dao.deleteByPurpose(PURPOSE_SPECIES_INFO) }

    suspend fun deleteByKey(key: String) = withContext(Dispatchers.IO) { dao.deleteByKey(key) }

    companion object {
        const val PURPOSE_SPECIES_INFO: String = "species_info"

        fun keyForPurpose(purpose: String, apiTag: String, nameCn: String): String {
            return "${purpose.trim()}::${apiTag.trim()}::${nameCn.trim()}"
        }
    }
}
