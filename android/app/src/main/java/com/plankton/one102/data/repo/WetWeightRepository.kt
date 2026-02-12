package com.plankton.one102.data.repo

import android.content.Context
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.db.WetWeightDao
import com.plankton.one102.data.db.WetWeightEntity
import com.plankton.one102.data.db.WetWeightLibraryDao
import com.plankton.one102.data.db.WetWeightLibraryEntity
import com.plankton.one102.domain.CustomLibraryMeta
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_NAME
import com.plankton.one102.domain.WetWeightEntry
import com.plankton.one102.domain.WetWeightLibrary
import com.plankton.one102.domain.WetWeightTaxonomy
import com.plankton.one102.domain.WetWeightsJson
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class WetWeightRepository(
    private val context: Context,
    private val dao: WetWeightDao,
    private val libraryDao: WetWeightLibraryDao,
) {
    private val activeLibraryId = MutableStateFlow(DEFAULT_WET_WEIGHT_LIBRARY_ID)

    @Volatile
    private var builtinCache: List<WetWeightEntry>? = null

    @Volatile
    private var builtinVersionCache: Int? = null

    fun observeActiveLibraryId(): StateFlow<String> = activeLibraryId.asStateFlow()

    fun setActiveLibraryId(id: String) {
        val fixed = id.trim().ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID }
        activeLibraryId.value = fixed
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCustomEntries(): Flow<List<WetWeightEntry>> {
        return activeLibraryId.flatMapLatest { libraryId ->
            dao.observeByLibrary(libraryId).map { list ->
                list.map {
                    WetWeightEntry(
                        nameCn = it.nameCn,
                        nameLatin = it.nameLatin,
                        wetWeightMg = it.wetWeightMg,
                        taxonomy = WetWeightTaxonomy(
                            group = it.groupName,
                            sub = it.subName,
                        ),
                    )
                }
            }
        }
    }

    fun observeLibraries(): Flow<List<WetWeightLibrary>> {
        return libraryDao.observeAll().map { list ->
            list.map { it.toDomain() }.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }

    suspend fun getLibraries(): List<WetWeightLibrary> = withContext(Dispatchers.IO) {
        libraryDao.getAll().map { it.toDomain() }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    suspend fun ensureDefaultLibrary(): WetWeightLibrary = withContext(Dispatchers.IO) {
        val existing = libraryDao.getById(DEFAULT_WET_WEIGHT_LIBRARY_ID)
        if (existing != null) return@withContext existing.toDomain()
        val now = nowIso()
        val entity = WetWeightLibraryEntity(
            id = DEFAULT_WET_WEIGHT_LIBRARY_ID,
            name = DEFAULT_WET_WEIGHT_LIBRARY_NAME,
            createdAt = now,
            updatedAt = now,
        )
        libraryDao.upsert(entity)
        entity.toDomain()
    }

    suspend fun createLibrary(name: String): WetWeightLibrary = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "库名称不能为空" }
        val now = nowIso()
        val entity = WetWeightLibraryEntity(
            id = newId(),
            name = trimmed,
            createdAt = now,
            updatedAt = now,
        )
        libraryDao.upsert(entity)
        entity.toDomain()
    }

    suspend fun renameLibrary(id: String, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "库名称不能为空" }
        val existing = libraryDao.getById(id) ?: return@withContext
        libraryDao.upsert(
            existing.copy(
                name = trimmed,
                updatedAt = nowIso(),
            ),
        )
    }

    suspend fun getBuiltinEntries(): List<WetWeightEntry> {
        val cached = builtinCache
        if (cached != null) return cached

        val loaded = withContext(Dispatchers.IO) {
            context.assets.open("wetweights.json").use { input ->
                val raw = input.readBytes().toString(Charsets.UTF_8)
                val json = AppJson.decodeFromString(WetWeightsJson.serializer(), raw)
                builtinVersionCache = json.version
                json.entries
            }
        }
        builtinCache = loaded
        return loaded
    }

    suspend fun getBuiltinVersion(): Int {
        val cached = builtinVersionCache
        if (cached != null) return cached
        getBuiltinEntries()
        return builtinVersionCache ?: 1
    }

    suspend fun getCustomMeta(libraryId: String = activeLibraryId.value): CustomLibraryMeta = withContext(Dispatchers.IO) {
        CustomLibraryMeta(
            count = dao.countByLibrary(libraryId),
            updatedAt = dao.latestUpdatedAt(libraryId),
        )
    }

    suspend fun getCustomEntries(libraryId: String = activeLibraryId.value): List<WetWeightEntry> {
        return withContext(Dispatchers.IO) {
            dao.getAllByLibrary(libraryId).map {
                WetWeightEntry(
                    nameCn = it.nameCn,
                    nameLatin = it.nameLatin,
                    wetWeightMg = it.wetWeightMg,
                    taxonomy = WetWeightTaxonomy(group = it.groupName, sub = it.subName),
                )
            }
        }
    }

    suspend fun findByNameCn(
        nameCn: String,
        libraryId: String = activeLibraryId.value,
        preferBuiltin: Boolean = true,
    ): WetWeightEntry? {
        val key = nameCn.trim()
        if (key.isEmpty()) return null

        if (preferBuiltin) {
            val builtin = getBuiltinEntries().firstOrNull { it.nameCn == key }
            if (builtin != null) return builtin
        }

        val custom = dao.getByNameCn(key, libraryId)
        if (custom != null) {
            return WetWeightEntry(
                nameCn = custom.nameCn,
                nameLatin = custom.nameLatin,
                wetWeightMg = custom.wetWeightMg,
                taxonomy = WetWeightTaxonomy(group = custom.groupName, sub = custom.subName),
            )
        }

        if (!preferBuiltin) {
            val builtin = getBuiltinEntries()
            return builtin.firstOrNull { it.nameCn == key }
        }
        return null
    }

    suspend fun upsertCustom(entry: WetWeightEntry, libraryId: String = activeLibraryId.value) {
        upsertCustom(entry, libraryId, origin = ORIGIN_MANUAL, importBatchId = null)
    }

    suspend fun upsertAutoMatched(entry: WetWeightEntry, libraryId: String = activeLibraryId.value) {
        upsertCustom(entry, libraryId, origin = ORIGIN_AUTOMATCH, importBatchId = null)
    }

    suspend fun upsertImported(entry: WetWeightEntry, importBatchId: String, libraryId: String = activeLibraryId.value) {
        upsertCustom(entry, libraryId, origin = ORIGIN_IMPORT, importBatchId = importBatchId)
    }

    private suspend fun upsertCustom(entry: WetWeightEntry, libraryId: String, origin: String, importBatchId: String?) {
        val now = nowIso()
        val e = WetWeightEntity(
            nameCn = entry.nameCn.trim(),
            nameLatin = entry.nameLatin?.trim().takeIf { !it.isNullOrBlank() },
            wetWeightMg = entry.wetWeightMg,
            groupName = entry.taxonomy.group?.trim().takeIf { !it.isNullOrBlank() },
            subName = entry.taxonomy.sub?.trim().takeIf { !it.isNullOrBlank() },
            origin = origin,
            importBatchId = importBatchId,
            updatedAt = now,
            libraryId = libraryId,
        )
        dao.upsert(e)
    }

    suspend fun deleteCustom(nameCn: String, libraryId: String = activeLibraryId.value) {
        dao.deleteByNameCn(nameCn.trim(), libraryId)
    }

    suspend fun deleteImportedAll(libraryId: String = activeLibraryId.value): Int {
        val n = dao.countByOrigin(ORIGIN_IMPORT, libraryId)
        dao.deleteByOrigin(ORIGIN_IMPORT, libraryId)
        return n
    }

    private fun combine(custom: List<WetWeightEntry>, builtin: List<WetWeightEntry>): List<WetWeightEntry> {
        val customByName = custom.associateBy { it.nameCn }
        val customSorted = customByName.values.sortedWith { a, b -> collator.compare(a.nameCn, b.nameCn) }
        val builtinFiltered = builtin.filter { !customByName.containsKey(it.nameCn) }
        return customSorted + builtinFiltered
    }

    private fun score(entry: WetWeightEntry, q: String, qLower: String): Int {
        val cn = entry.nameCn
        val latin = (entry.nameLatin ?: "").lowercase()
        var s = 0
        if (cn == q) s += 100
        if (cn.contains(q)) s += 50
        if (latin.contains(qLower)) s += 20
        if ((entry.taxonomy.group ?: "").contains(q)) s += 10
        if ((entry.taxonomy.sub ?: "").contains(q)) s += 8
        return s
    }

    suspend fun searchCombined(query: String, limit: Int = 50, libraryId: String = activeLibraryId.value): List<WetWeightEntry> {
        val q = query.trim()
        val builtin = getBuiltinEntries()
        val custom = getCustomEntries(libraryId)
        val entries = combine(custom, builtin)
        if (q.isEmpty()) return entries.take(limit.coerceAtMost(entries.size))

        val qLower = q.lowercase()
        val hits = entries.mapNotNull { e ->
            val s = score(e, q, qLower)
            if (s > 0) s to e else null
        }.sortedWith { a, b ->
            val byScore = b.first.compareTo(a.first)
            if (byScore != 0) byScore else collator.compare(a.second.nameCn, b.second.nameCn)
        }

        return hits.take(limit).map { it.second }
    }

    companion object {
        const val ORIGIN_MANUAL: String = "manual"
        const val ORIGIN_IMPORT: String = "import"
        const val ORIGIN_AUTOMATCH: String = "auto_match"
        private val collator: Collator = Collator.getInstance(Locale.CHINA)
    }
}

private fun WetWeightLibraryEntity.toDomain(): WetWeightLibrary {
    return WetWeightLibrary(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
