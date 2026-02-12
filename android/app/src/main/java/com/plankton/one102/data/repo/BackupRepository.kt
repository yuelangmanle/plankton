package com.plankton.one102.data.repo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.plankton.one102.data.AppJson
import com.plankton.one102.data.db.AiCacheDao
import com.plankton.one102.data.db.AliasDao
import com.plankton.one102.data.db.TaxonomyDao
import com.plankton.one102.data.db.WetWeightDao
import com.plankton.one102.data.db.WetWeightLibraryDao
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.data.prefs.AppPreferences
import com.plankton.one102.export.writeFileToSafAtomic
import com.plankton.one102.domain.Dataset
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.Settings
import com.plankton.one102.domain.newId
import com.plankton.one102.domain.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class BackupImportOptions(
    val importSettings: Boolean = true,
    val importDatasets: Boolean = true,
    val importWetWeights: Boolean = true,
    val importTaxonomies: Boolean = true,
    val importAliases: Boolean = true,
    val importAiCache: Boolean = true,
    val password: String? = null,
)

data class BackupExportOptions(
    val encrypt: Boolean = false,
    val password: String? = null,
)

data class BackupSummary(
    val encrypted: Boolean,
    val decrypted: Boolean,
    val backupVersion: Int?,
    val exportedAt: String?,
    val datasets: Int?,
    val points: Int?,
    val species: Int?,
    val wetWeights: Int?,
    val taxonomies: Int?,
    val aliases: Int?,
    val aiCache: Int?,
)

class BackupRepository(
    private val context: Context,
    private val prefs: AppPreferences,
    private val datasetRepo: DatasetRepository,
    private val wetWeightDao: WetWeightDao,
    private val wetWeightLibraryDao: WetWeightLibraryDao,
    private val taxonomyDao: TaxonomyDao,
    private val aliasDao: AliasDao,
    private val aiCacheDao: AiCacheDao,
) {
    @Serializable
    data class BackupWetWeight(
        val nameCn: String,
        val nameLatin: String? = null,
        val wetWeightMg: Double,
        val groupName: String? = null,
        val subName: String? = null,
        val origin: String = WetWeightRepository.ORIGIN_MANUAL,
        val importBatchId: String? = null,
        val updatedAt: String,
        val libraryId: String = DEFAULT_WET_WEIGHT_LIBRARY_ID,
    )

    @Serializable
    data class BackupWetWeightLibrary(
        val id: String,
        val name: String,
        val createdAt: String,
        val updatedAt: String,
    )

    @Serializable
    data class BackupTaxonomy(
        val nameCn: String,
        val nameLatin: String? = null,
        val lvl1: String? = null,
        val lvl2: String? = null,
        val lvl3: String? = null,
        val lvl4: String? = null,
        val lvl5: String? = null,
        val updatedAt: String,
    )

    @Serializable
    data class BackupAlias(
        val alias: String,
        val canonical: String,
        val updatedAt: String,
    )

    @Serializable
    data class BackupAiCache(
        val key: String,
        val purpose: String,
        val apiTag: String,
        val nameCn: String,
        val nameLatin: String? = null,
        val wetWeightMg: Double? = null,
        val lvl1: String? = null,
        val lvl2: String? = null,
        val lvl3: String? = null,
        val lvl4: String? = null,
        val lvl5: String? = null,
        val prompt: String,
        val raw: String,
        val updatedAt: String,
    )

    @Serializable
    data class BackupFileV1(
        val version: Int = 1,
        val datasets: List<Dataset> = emptyList(),
    )

    @Serializable
    data class BackupFileV2(
        val version: Int = 2,
        val exportedAt: String = "",
        val settings: Settings = com.plankton.one102.domain.DEFAULT_SETTINGS,
        val currentDatasetId: String? = null,
        val datasets: List<Dataset> = emptyList(),
        val wetWeights: List<BackupWetWeight> = emptyList(),
        val wetWeightLibraries: List<BackupWetWeightLibrary> = emptyList(),
        val taxonomies: List<BackupTaxonomy> = emptyList(),
        val aliases: List<BackupAlias> = emptyList(),
        val aiCache: List<BackupAiCache> = emptyList(),
    )

    @Serializable
    data class EncryptedBackupEnvelope(
        val format: String = "plankton-backup",
        val encrypted: Boolean = true,
        val envelopeVersion: Int = 1,
        val exportedAt: String = "",
        val algorithm: String = "AES-256-GCM",
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int = 120_000,
        val saltBase64: String,
        val ivBase64: String,
        val ciphertextBase64: String,
    )

    @Suppress("UNUSED_PARAMETER")
    suspend fun exportBackup(contentResolver: ContentResolver, uri: Uri, options: BackupExportOptions = BackupExportOptions()) {
        val settings = prefs.settings.first()
        val currentId = prefs.currentDatasetId.first()
        val datasets = datasetRepo.getAll()

        val wetWeights = wetWeightDao.getAll().map {
            BackupWetWeight(
                nameCn = it.nameCn,
                nameLatin = it.nameLatin,
                wetWeightMg = it.wetWeightMg,
                groupName = it.groupName,
                subName = it.subName,
                origin = it.origin,
                importBatchId = it.importBatchId,
                updatedAt = it.updatedAt,
                libraryId = it.libraryId,
            )
        }
        val wetWeightLibraries = wetWeightLibraryDao.getAll().map {
            BackupWetWeightLibrary(
                id = it.id,
                name = it.name,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
            )
        }
        val taxonomies = taxonomyDao.getAll().map {
            BackupTaxonomy(
                nameCn = it.nameCn,
                nameLatin = it.nameLatin,
                lvl1 = it.lvl1,
                lvl2 = it.lvl2,
                lvl3 = it.lvl3,
                lvl4 = it.lvl4,
                lvl5 = it.lvl5,
                updatedAt = it.updatedAt,
            )
        }
        val aliases = aliasDao.getAll().map { BackupAlias(alias = it.alias, canonical = it.canonical, updatedAt = it.updatedAt) }
        val aiCache = aiCacheDao.getAll().map {
            BackupAiCache(
                key = it.key,
                purpose = it.purpose,
                apiTag = it.apiTag,
                nameCn = it.nameCn,
                nameLatin = it.nameLatin,
                wetWeightMg = it.wetWeightMg,
                lvl1 = it.lvl1,
                lvl2 = it.lvl2,
                lvl3 = it.lvl3,
                lvl4 = it.lvl4,
                lvl5 = it.lvl5,
                prompt = it.prompt,
                raw = it.raw,
                updatedAt = it.updatedAt,
            )
        }

        val backup = BackupFileV2(
            exportedAt = nowIso(),
            settings = settings,
            currentDatasetId = currentId,
            datasets = datasets,
            wetWeights = wetWeights,
            wetWeightLibraries = wetWeightLibraries,
            taxonomies = taxonomies,
            aliases = aliases,
            aiCache = aiCache,
        )

        val raw = AppJson.encodeToString(BackupFileV2.serializer(), backup)
        val outRaw = if (options.encrypt) {
            val password = options.password?.trim().orEmpty()
            if (password.isBlank()) throw IllegalArgumentException("加密备份需要密码")
            val env = encryptEnvelope(plainJson = raw, exportedAt = backup.exportedAt, password = password)
            AppJson.encodeToString(EncryptedBackupEnvelope.serializer(), env)
        } else {
            raw
        }
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val tempFile = File.createTempFile("backup-", ".json", dir)
            withContext(Dispatchers.IO) {
                tempFile.outputStream().use { out ->
                    out.write(outRaw.toByteArray(Charsets.UTF_8))
                }
            }
            writeFileToSafAtomic(context, uri, tempFile)
            prefs.setLastExport(uri.toString(), backup.exportedAt)
            runCatching { tempFile.delete() }
        } catch (e: Exception) {
            AppLogger.logError(context, "BackupExport", "导出备份失败", e)
            throw e
        }
    }

    suspend fun readBackupSummary(contentResolver: ContentResolver, uri: Uri, password: String? = null): BackupSummary {
        val raw = readRaw(contentResolver, uri)
        val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrElse {
            throw IllegalArgumentException("备份文件不是有效的 JSON：${it.message ?: it.toString()}")
        }
        val obj = runCatching { element.jsonObject }.getOrElse {
            throw IllegalArgumentException("备份文件格式不正确：根节点不是 JSON 对象")
        }

        val env = tryDecodeEncryptedEnvelope(obj)
        if (env != null) {
            if (password.isNullOrBlank()) {
                return BackupSummary(
                    encrypted = true,
                    decrypted = false,
                    backupVersion = null,
                    exportedAt = env.exportedAt.ifBlank { null },
                    datasets = null,
                    points = null,
                    species = null,
                    wetWeights = null,
                    taxonomies = null,
                    aliases = null,
                    aiCache = null,
                )
            }
            val decrypted = decryptEnvelope(env, password.trim())
            return readBackupSummaryFromRaw(decrypted, encrypted = true)
        }

        return readBackupSummaryFromRaw(raw, encrypted = false)
    }

    suspend fun importBackup(contentResolver: ContentResolver, uri: Uri, options: BackupImportOptions = BackupImportOptions()): Int {
        val raw = readRaw(contentResolver, uri)
        val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrElse {
            throw IllegalArgumentException("备份文件不是有效的 JSON：${it.message ?: it.toString()}")
        }
        val obj = runCatching { element.jsonObject }.getOrElse {
            throw IllegalArgumentException("备份文件格式不正确：根节点不是 JSON 对象")
        }

        val env = tryDecodeEncryptedEnvelope(obj)
        val decryptedRaw = if (env != null) {
            val password = options.password?.trim().orEmpty()
            if (password.isBlank()) throw IllegalArgumentException("备份文件已加密：请输入密码")
            decryptEnvelope(env, password)
        } else {
            raw
        }

        val element2 = runCatching { AppJson.parseToJsonElement(decryptedRaw) }.getOrElse {
            throw IllegalArgumentException("备份文件不是有效的 JSON：${it.message ?: it.toString()}")
        }
        val obj2 = runCatching { element2.jsonObject }.getOrElse {
            throw IllegalArgumentException("备份文件格式不正确：根节点不是 JSON 对象")
        }
        if (!obj2.containsKey("datasets")) {
            val sampleKeys = obj2.keys.take(6).joinToString(", ")
            throw IllegalArgumentException("不是本应用备份文件：缺少 datasets 字段（文件包含：$sampleKeys）")
        }

        val versionPrim = obj2["version"]?.jsonPrimitive
        val version = parseBackupVersion(versionPrim)
        val normalizedRaw = normalizeVersionRawIfNeeded(element2, versionPrim, version) ?: decryptedRaw

        return when (version) {
            1 -> if (options.importDatasets) importV1(normalizedRaw) else 0
            2 -> importV2(normalizedRaw, options)
            null -> {
                runCatching { importV2(normalizedRaw, options) }
                    .recoverCatching { if (options.importDatasets) importV1(normalizedRaw) else 0 }
                    .getOrElse {
                        throw IllegalArgumentException("无法识别备份文件格式：请确认选择的是本应用导出的 plankton-backup.json")
                    }
            }

            else -> {
                // Future versions: try the newest importer first, then give a clear error.
                runCatching { importV2(normalizedRaw, options) }.getOrElse {
                    throw IllegalArgumentException("备份文件版本不受支持：version=$version")
                }
            }
        }
    }

    private suspend fun readRaw(contentResolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法打开输入流")
        }.toString(Charsets.UTF_8)
    }

    private fun tryDecodeEncryptedEnvelope(obj: JsonObject): EncryptedBackupEnvelope? {
        val encrypted = obj["encrypted"]?.jsonPrimitive?.booleanOrNull
        if (encrypted != true) return null
        if (!obj.containsKey("ciphertextBase64")) return null
        return runCatching { AppJson.decodeFromJsonElement(EncryptedBackupEnvelope.serializer(), obj) }.getOrNull()
    }

    private fun encryptEnvelope(plainJson: String, exportedAt: String, password: String): EncryptedBackupEnvelope {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val iterations = 120_000

        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256))
            .encoded
        val key = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        return EncryptedBackupEnvelope(
            exportedAt = exportedAt,
            iterations = iterations,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    private fun decryptEnvelope(env: EncryptedBackupEnvelope, password: String): String {
        val salt = runCatching { Base64.getDecoder().decode(env.saltBase64) }
            .getOrElse { throw IllegalArgumentException("加密备份格式错误：salt 无法解析") }
        val iv = runCatching { Base64.getDecoder().decode(env.ivBase64) }
            .getOrElse { throw IllegalArgumentException("加密备份格式错误：iv 无法解析") }
        val ciphertext = runCatching { Base64.getDecoder().decode(env.ciphertextBase64) }
            .getOrElse { throw IllegalArgumentException("加密备份格式错误：ciphertext 无法解析") }

        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, env.iterations, 256))
            .encoded
        val key = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return runCatching {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ciphertext)
            plain.toString(Charsets.UTF_8)
        }.getOrElse {
            throw IllegalArgumentException("解密失败：密码不正确或备份文件已损坏")
        }
    }

    private fun readBackupSummaryFromRaw(raw: String, encrypted: Boolean): BackupSummary {
        val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrElse {
            throw IllegalArgumentException("备份文件不是有效的 JSON：${it.message ?: it.toString()}")
        }
        val obj = runCatching { element.jsonObject }.getOrElse {
            throw IllegalArgumentException("备份文件格式不正确：根节点不是 JSON 对象")
        }

        val versionPrim = obj["version"]?.jsonPrimitive
        val version = parseBackupVersion(versionPrim)
        return when (version) {
            1 -> {
                val v1 = runCatching { AppJson.decodeFromString(BackupFileV1.serializer(), raw) }.getOrNull()
                val datasets = v1?.datasets ?: emptyList()
                BackupSummary(
                    encrypted = encrypted,
                    decrypted = true,
                    backupVersion = 1,
                    exportedAt = null,
                    datasets = datasets.size,
                    points = datasets.sumOf { it.points.size },
                    species = datasets.sumOf { it.species.size },
                    wetWeights = 0,
                    taxonomies = 0,
                    aliases = 0,
                    aiCache = 0,
                )
            }

            else -> {
                val v2 = runCatching { AppJson.decodeFromString(BackupFileV2.serializer(), raw) }.getOrNull()
                if (v2 == null) {
                    BackupSummary(
                        encrypted = encrypted,
                        decrypted = true,
                        backupVersion = version,
                        exportedAt = obj["exportedAt"]?.jsonPrimitive?.content,
                        datasets = null,
                        points = null,
                        species = null,
                        wetWeights = null,
                        taxonomies = null,
                        aliases = null,
                        aiCache = null,
                    )
                } else {
                    BackupSummary(
                        encrypted = encrypted,
                        decrypted = true,
                        backupVersion = 2,
                        exportedAt = v2.exportedAt.ifBlank { null },
                        datasets = v2.datasets.size,
                        points = v2.datasets.sumOf { it.points.size },
                        species = v2.datasets.sumOf { it.species.size },
                        wetWeights = v2.wetWeights.size,
                        taxonomies = v2.taxonomies.size,
                        aliases = v2.aliases.size,
                        aiCache = v2.aiCache.size,
                    )
                }
            }
        }
    }

    private fun parseBackupVersion(prim: JsonPrimitive?): Int? {
        if (prim == null) return null
        prim.intOrNull?.let { return it }

        val content = prim.content.trim()
        content.toIntOrNull()?.let { return it }
        content.toDoubleOrNull()?.let { d ->
            if (!d.isFinite()) return null
            val i = d.toInt()
            if (abs(d - i.toDouble()) < 1e-9) return i
        }
        return null
    }

    private fun normalizeVersionRawIfNeeded(element: JsonElement, prim: JsonPrimitive?, version: Int?): String? {
        if (version == null) return null
        if (prim == null) return null

        val content = prim.content.trim()
        val alreadyIntLiteral = content == version.toString() && !prim.isString
        if (alreadyIntLiteral) return null

        val obj = element.jsonObject
        val patched: JsonObject = JsonObject(obj.toMutableMap().apply { put("version", JsonPrimitive(version)) })
        return AppJson.encodeToString(JsonElement.serializer(), patched)
    }

    private suspend fun importV1(raw: String): Int {
        val backup = AppJson.decodeFromString(BackupFileV1.serializer(), raw)
        var imported = 0
        for (ds in backup.datasets) {
            val exists = datasetRepo.getById(ds.id) != null
            val final = if (exists) {
                val now = nowIso()
                ds.copy(id = newId(), createdAt = now, updatedAt = now)
            } else {
                ds
            }
            datasetRepo.save(final)
            imported += 1
        }
        return imported
    }

    private suspend fun importV2(raw: String, options: BackupImportOptions): Int {
        val backup = AppJson.decodeFromString(BackupFileV2.serializer(), raw)
        if (options.importSettings) {
            prefs.saveSettings(backup.settings)
        }

        // Import custom tables (upsert).
        if (options.importWetWeights) {
            for (lib in backup.wetWeightLibraries) {
                wetWeightLibraryDao.upsert(
                    com.plankton.one102.data.db.WetWeightLibraryEntity(
                        id = lib.id,
                        name = lib.name,
                        createdAt = lib.createdAt,
                        updatedAt = lib.updatedAt,
                    ),
                )
            }
            for (w in backup.wetWeights) {
                wetWeightDao.upsert(
                    com.plankton.one102.data.db.WetWeightEntity(
                        nameCn = w.nameCn,
                        nameLatin = w.nameLatin,
                        wetWeightMg = w.wetWeightMg,
                        groupName = w.groupName,
                        subName = w.subName,
                        origin = w.origin,
                        importBatchId = w.importBatchId,
                        updatedAt = w.updatedAt,
                        libraryId = w.libraryId.ifBlank { DEFAULT_WET_WEIGHT_LIBRARY_ID },
                    ),
                )
            }
        }
        if (options.importTaxonomies) {
            for (t in backup.taxonomies) {
                taxonomyDao.upsert(
                    com.plankton.one102.data.db.TaxonomyEntity(
                        nameCn = t.nameCn,
                        nameLatin = t.nameLatin,
                        lvl1 = t.lvl1,
                        lvl2 = t.lvl2,
                        lvl3 = t.lvl3,
                        lvl4 = t.lvl4,
                        lvl5 = t.lvl5,
                        updatedAt = t.updatedAt,
                    ),
                )
            }
        }
        if (options.importAliases) {
            for (a in backup.aliases) {
                aliasDao.upsert(com.plankton.one102.data.db.AliasEntity(alias = a.alias, canonical = a.canonical, updatedAt = a.updatedAt))
            }
        }
        if (options.importAiCache) {
            for (c in backup.aiCache) {
                aiCacheDao.upsert(
                    com.plankton.one102.data.db.AiCacheEntity(
                        key = c.key,
                        purpose = c.purpose,
                        apiTag = c.apiTag,
                        nameCn = c.nameCn,
                        nameLatin = c.nameLatin,
                        wetWeightMg = c.wetWeightMg,
                        lvl1 = c.lvl1,
                        lvl2 = c.lvl2,
                        lvl3 = c.lvl3,
                        lvl4 = c.lvl4,
                        lvl5 = c.lvl5,
                        prompt = c.prompt,
                        raw = c.raw,
                        updatedAt = c.updatedAt,
                    ),
                )
            }
        }

        // Datasets (handle id conflicts).
        val idMap = mutableMapOf<String, String>()
        var imported = 0
        if (options.importDatasets) {
            for (ds in backup.datasets) {
                val exists = datasetRepo.getById(ds.id) != null
                val final = if (exists) {
                    val now = nowIso()
                    ds.copy(id = newId(), createdAt = now, updatedAt = now)
                } else {
                    ds
                }
                datasetRepo.save(final)
                idMap[ds.id] = final.id
                imported += 1
            }

            val prefer = backup.currentDatasetId?.let { idMap[it] ?: it }
            if (!prefer.isNullOrBlank()) {
                prefs.setCurrentDatasetId(prefer)
            } else {
                prefs.setCurrentDatasetId(idMap.values.firstOrNull())
            }
        }

        return imported
    }
}
