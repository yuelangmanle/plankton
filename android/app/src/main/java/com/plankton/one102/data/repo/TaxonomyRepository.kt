package com.plankton.one102.data.repo

import android.content.Context
import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.TaxonomiesJson
import com.plankton.one102.domain.Taxonomy
import com.plankton.one102.domain.TaxonomyEntry
import com.plankton.one102.domain.normalizeLvl1Name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAXONOMIES_ASSET = "taxonomies.json"

class TaxonomyRepository(private val context: Context) {
    @Volatile
    private var builtinCache: List<TaxonomyEntry>? = null

    @Volatile
    private var builtinMapCache: Map<String, TaxonomyEntry>? = null

    @Volatile
    private var builtinOrderCache: Map<String, Int>? = null

    @Volatile
    private var builtinVersionCache: Int? = null

    suspend fun findByNameCn(nameCn: String): Taxonomy? {
        val key = nameCn.trim()
        if (key.isEmpty()) return null
        specialCaseEntry(key)?.let { return it.taxonomy }

        val map = getBuiltinEntryMap()
        return map[key]?.taxonomy
    }

    suspend fun findEntryByNameCn(nameCn: String): TaxonomyEntry? {
        val key = nameCn.trim()
        if (key.isEmpty()) return null
        specialCaseEntry(key)?.let { return it }
        return getBuiltinEntryMap()[key]
    }

    suspend fun getBuiltinEntries(): Map<String, Taxonomy> {
        return getBuiltinEntryMap().mapValues { (_, entry) -> entry.taxonomy }
    }

    suspend fun getBuiltinEntryMap(): Map<String, TaxonomyEntry> {
        val cached = builtinMapCache
        if (cached != null) return cached

        val entries = getBuiltinList()
        val map = LinkedHashMap<String, TaxonomyEntry>(entries.size)
        for (e in entries) {
            val key = e.nameCn.trim()
            if (key.isEmpty()) continue
            map.putIfAbsent(key, e)
        }

        builtinMapCache = map
        return map
    }

    suspend fun getBuiltinOrderIndex(): Map<String, Int> {
        val cached = builtinOrderCache
        if (cached != null) return cached

        val list = getBuiltinList()
        val map = LinkedHashMap<String, Int>(list.size)
        for ((i, e) in list.withIndex()) {
            val key = e.nameCn.trim()
            if (key.isNotEmpty() && !map.containsKey(key)) map[key] = i
        }
        builtinOrderCache = map
        return map
    }

    suspend fun getBuiltinVersion(): Int {
        val cached = builtinVersionCache
        if (cached != null) return cached
        getBuiltinList()
        return builtinVersionCache ?: 1
    }

    private suspend fun getBuiltinList(): List<TaxonomyEntry> {
        val cached = builtinCache
        if (cached != null) return cached

        val loaded = withContext(Dispatchers.IO) {
            context.assets.open(TAXONOMIES_ASSET).use { input ->
                val raw = input.readBytes().toString(Charsets.UTF_8)
                val json = AppJson.decodeFromString(TaxonomiesJson.serializer(), raw)
                builtinVersionCache = json.version
                json.entries
            }
        }.mapNotNull { e ->
            val nameCn = e.nameCn.trim()
            if (nameCn.isEmpty()) return@mapNotNull null
            val latin = e.nameLatin?.trim().takeIf { !it.isNullOrBlank() }
            e.copy(
                nameCn = nameCn,
                nameLatin = latin,
                taxonomy = e.taxonomy.copy(
                    lvl1 = normalizeLvl1Name(e.taxonomy.lvl1),
                    lvl2 = e.taxonomy.lvl2.trim(),
                    lvl3 = e.taxonomy.lvl3.trim(),
                    lvl4 = e.taxonomy.lvl4.trim(),
                    lvl5 = e.taxonomy.lvl5.trim(),
                ),
            )
        }.toMutableList()

        for (name in listOf("无节幼体", "无节幼虫")) {
            val sc = specialCaseEntry(name)
            if (sc != null && loaded.none { it.nameCn == name }) loaded.add(sc)
        }

        builtinCache = loaded
        return loaded
    }

    private fun specialCaseEntry(nameCn: String): TaxonomyEntry? {
        return when (nameCn.trim()) {
            "无节幼体", "无节幼虫" -> TaxonomyEntry(
                nameCn = nameCn.trim(),
                nameLatin = null,
                taxonomy = Taxonomy(
                    lvl1 = "桡足类",
                    lvl2 = "",
                    lvl3 = "栉足目",
                    lvl4 = "仙达溞科",
                    lvl5 = "秀体溞属",
                ),
            )

            else -> null
        }
    }
}
