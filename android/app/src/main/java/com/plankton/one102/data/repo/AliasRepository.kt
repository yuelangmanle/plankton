package com.plankton.one102.data.repo

import com.plankton.one102.data.db.AliasDao
import com.plankton.one102.data.db.AliasEntity
import com.plankton.one102.domain.CustomLibraryMeta
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AliasRecord(
    val alias: String,
    val canonicalNameCn: String,
    val updatedAt: String,
)

class AliasRepository(private val dao: AliasDao) {
    fun observeAll(): Flow<List<AliasRecord>> {
        return dao.observeAll().map { list ->
            list.map { AliasRecord(alias = it.alias, canonicalNameCn = it.canonical, updatedAt = it.updatedAt) }
        }
    }

    suspend fun getAll(): List<AliasRecord> = withContext(Dispatchers.IO) {
        dao.getAll().map { AliasRecord(alias = it.alias, canonicalNameCn = it.canonical, updatedAt = it.updatedAt) }
    }

    suspend fun resolve(nameCn: String): String? = withContext(Dispatchers.IO) {
        val key = nameCn.trim()
        if (key.isEmpty()) return@withContext null
        dao.getByAlias(key)?.canonical
    }

    suspend fun upsert(alias: String, canonicalNameCn: String) {
        val a = alias.trim()
        val c = canonicalNameCn.trim()
        require(a.isNotEmpty()) { "别名不能为空" }
        require(c.isNotEmpty()) { "标准名不能为空" }
        withContext(Dispatchers.IO) {
            dao.upsert(AliasEntity(alias = a, canonical = c, updatedAt = nowIso()))
        }
    }

    suspend fun delete(alias: String) = withContext(Dispatchers.IO) { dao.deleteByAlias(alias.trim()) }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }

    suspend fun getMeta(): CustomLibraryMeta = withContext(Dispatchers.IO) {
        CustomLibraryMeta(
            count = dao.countAll(),
            updatedAt = dao.latestUpdatedAt(),
        )
    }
}
