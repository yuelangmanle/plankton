package com.plankton.one102.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.plankton.one102.data.AppJson
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_ID
import com.plankton.one102.domain.DEFAULT_WET_WEIGHT_LIBRARY_NAME
import com.plankton.one102.domain.Dataset

@Database(
    entities = [
        DatasetEntity::class,
        WetWeightEntity::class,
        WetWeightLibraryEntity::class,
        TaxonomyEntity::class,
        AliasEntity::class,
        AiCacheEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun datasetDao(): DatasetDao
    abstract fun wetWeightDao(): WetWeightDao
    abstract fun wetWeightLibraryDao(): WetWeightLibraryDao
    abstract fun taxonomyDao(): TaxonomyDao
    abstract fun aliasDao(): AliasDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS taxonomies_custom (
                        nameCn TEXT NOT NULL PRIMARY KEY,
                        nameLatin TEXT,
                        lvl1 TEXT,
                        lvl2 TEXT,
                        lvl3 TEXT,
                        lvl4 TEXT,
                        lvl5 TEXT,
                        updatedAt TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wetweights_custom ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE wetweights_custom ADD COLUMN importBatchId TEXT")
            }
        }

        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS aliases (
                        alias TEXT NOT NULL PRIMARY KEY,
                        canonical TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ai_cache (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        purpose TEXT NOT NULL,
                        apiTag TEXT NOT NULL,
                        nameCn TEXT NOT NULL,
                        nameLatin TEXT,
                        wetWeightMg REAL,
                        lvl1 TEXT,
                        lvl2 TEXT,
                        lvl3 TEXT,
                        lvl4 TEXT,
                        lvl5 TEXT,
                        prompt TEXT NOT NULL,
                        raw TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE datasets ADD COLUMN readOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE datasets ADD COLUMN snapshotAt TEXT")
                db.execSQL("ALTER TABLE datasets ADD COLUMN snapshotSourceId TEXT")
                db.execSQL("ALTER TABLE datasets ADD COLUMN pointsCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE datasets ADD COLUMN speciesCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_datasets_updatedAt ON datasets(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_cache_updatedAt ON ai_cache(updatedAt)")

                val cursor = db.query("SELECT id, json FROM datasets")
                val stmt = db.compileStatement(
                    """
                    UPDATE datasets
                    SET readOnly = ?, snapshotAt = ?, snapshotSourceId = ?, pointsCount = ?, speciesCount = ?
                    WHERE id = ?
                    """.trimIndent(),
                )
                try {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val json = cursor.getString(1)
                        val ds = runCatching { AppJson.decodeFromString(Dataset.serializer(), json) }.getOrNull()
                        val readOnly = if (ds?.readOnly == true) 1 else 0
                        val snapshotAt = ds?.snapshotAt
                        val snapshotSourceId = ds?.snapshotSourceId
                        val pointsCount = ds?.points?.size ?: 0
                        val speciesCount = ds?.species?.size ?: 0

                        stmt.bindLong(1, readOnly.toLong())
                        if (snapshotAt == null) stmt.bindNull(2) else stmt.bindString(2, snapshotAt)
                        if (snapshotSourceId == null) stmt.bindNull(3) else stmt.bindString(3, snapshotSourceId)
                        stmt.bindLong(4, pointsCount.toLong())
                        stmt.bindLong(5, speciesCount.toLong())
                        stmt.bindString(6, id)
                        stmt.execute()
                        stmt.clearBindings()
                    }
                } finally {
                    cursor.close()
                    stmt.close()
                }
            }
        }

        private data class ColumnMeta(
            val name: String,
            val type: String,
            val notNull: Boolean,
            val primaryKeyPosition: Int,
            val defaultValue: String?,
        )

        private fun readColumns(db: SupportSQLiteDatabase, table: String): Map<String, ColumnMeta> {
            val map = mutableMapOf<String, ColumnMeta>()
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                val notNullIndex = cursor.getColumnIndex("notnull")
                val pkIndex = cursor.getColumnIndex("pk")
                val defaultIndex = cursor.getColumnIndex("dflt_value")
                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    if (name.isBlank()) continue
                    val type = if (typeIndex >= 0) cursor.getString(typeIndex) ?: "" else ""
                    val notNull = if (notNullIndex >= 0) cursor.getInt(notNullIndex) == 1 else false
                    val primaryKeyPosition = if (pkIndex >= 0) cursor.getInt(pkIndex) else 0
                    val defaultValue = if (defaultIndex >= 0) cursor.getString(defaultIndex) else null
                    map[name] = ColumnMeta(name, type, notNull, primaryKeyPosition, defaultValue)
                }
            }
            return map
        }

        private fun ensureWetWeightLibrarySchema(db: SupportSQLiteDatabase) {
            val librariesExists = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='wetweight_libraries'",
            ).use { it.moveToFirst() }

            if (!librariesExists) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wetweight_libraries (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            } else {
                val columns = readColumns(db, "wetweight_libraries")
                val expected = setOf("id", "name", "createdAt", "updatedAt")
                val missing = expected - columns.keys
                val extra = columns.keys - expected
                val notNullMismatch = listOf("id", "name", "createdAt", "updatedAt").any {
                    columns[it]?.notNull != true
                }
                val pkMismatch = columns["id"]?.primaryKeyPosition != 1
                val typeMismatch = listOf("id", "name", "createdAt", "updatedAt").any {
                    val type = columns[it]?.type?.uppercase() ?: ""
                    !type.contains("TEXT")
                }
                val needRebuild = missing.isNotEmpty() || extra.isNotEmpty() || notNullMismatch || pkMismatch || typeMismatch
                if (needRebuild) {
                    db.execSQL("DROP TABLE IF EXISTS wetweight_libraries_new")
                    db.execSQL(
                        """
                        CREATE TABLE wetweight_libraries_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    if (columns.containsKey("id") && columns.containsKey("name")) {
                        val createdAtExpr = if (columns.containsKey("createdAt")) "COALESCE(createdAt, datetime('now'))" else "datetime('now')"
                        val updatedAtExpr = if (columns.containsKey("updatedAt")) "COALESCE(updatedAt, datetime('now'))" else "datetime('now')"
                        db.execSQL(
                            """
                            INSERT INTO wetweight_libraries_new (id, name, createdAt, updatedAt)
                            SELECT id, name, $createdAtExpr, $updatedAtExpr
                            FROM wetweight_libraries
                            WHERE id IS NOT NULL AND name IS NOT NULL
                            """.trimIndent(),
                        )
                    }
                    db.execSQL("DROP TABLE wetweight_libraries")
                    db.execSQL("ALTER TABLE wetweight_libraries_new RENAME TO wetweight_libraries")
                }
            }

            val safeDefaultLibraryName = DEFAULT_WET_WEIGHT_LIBRARY_NAME.replace("'", "''")
            db.execSQL(
                """
                INSERT OR IGNORE INTO wetweight_libraries (id, name, createdAt, updatedAt)
                VALUES ('$DEFAULT_WET_WEIGHT_LIBRARY_ID', '$safeDefaultLibraryName', datetime('now'), datetime('now'))
                """.trimIndent(),
            )

            val tableExists = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='wetweights_custom'",
            ).use { it.moveToFirst() }

            if (!tableExists) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wetweights_custom (
                        nameCn TEXT NOT NULL PRIMARY KEY,
                        nameLatin TEXT,
                        wetWeightMg REAL NOT NULL,
                        groupName TEXT,
                        subName TEXT,
                        origin TEXT NOT NULL DEFAULT 'manual',
                        importBatchId TEXT,
                        updatedAt TEXT NOT NULL,
                        libraryId TEXT NOT NULL DEFAULT '$DEFAULT_WET_WEIGHT_LIBRARY_ID'
                    )
                    """.trimIndent(),
                )
            } else {
                val columns = readColumns(db, "wetweights_custom")
                val expected = setOf(
                    "nameCn",
                    "nameLatin",
                    "wetWeightMg",
                    "groupName",
                    "subName",
                    "origin",
                    "importBatchId",
                    "updatedAt",
                    "libraryId",
                )
                val requiredNotNull = setOf("nameCn", "wetWeightMg", "origin", "updatedAt", "libraryId")
                val missing = expected - columns.keys
                val extra = columns.keys - expected
                val notNullMismatch = requiredNotNull.any { columns[it]?.notNull != true }
                val pkMismatch = (columns["nameCn"]?.primaryKeyPosition != 1) ||
                    columns.any { (name, meta) -> name != "nameCn" && meta.primaryKeyPosition > 0 }
                val libraryMeta = columns["libraryId"]
                val libraryDefaultOk = libraryMeta?.defaultValue?.contains(DEFAULT_WET_WEIGHT_LIBRARY_ID) == true
                val originMeta = columns["origin"]
                val originDefaultOk = originMeta?.defaultValue?.contains("manual") == true
                val needRebuild = missing.isNotEmpty() ||
                    extra.isNotEmpty() ||
                    notNullMismatch ||
                    pkMismatch ||
                    !libraryDefaultOk ||
                    !originDefaultOk

                if (needRebuild) {
                    db.execSQL("DROP TABLE IF EXISTS wetweights_custom_new")
                    db.execSQL(
                        """
                        CREATE TABLE wetweights_custom_new (
                            nameCn TEXT NOT NULL PRIMARY KEY,
                            nameLatin TEXT,
                            wetWeightMg REAL NOT NULL,
                            groupName TEXT,
                            subName TEXT,
                            origin TEXT NOT NULL DEFAULT 'manual',
                            importBatchId TEXT,
                            updatedAt TEXT NOT NULL,
                            libraryId TEXT NOT NULL DEFAULT '$DEFAULT_WET_WEIGHT_LIBRARY_ID'
                        )
                        """.trimIndent(),
                    )

                    val hasNameCn = columns.containsKey("nameCn")
                    val hasWetWeight = columns.containsKey("wetWeightMg")
                    if (hasNameCn && hasWetWeight) {
                        val selectNameLatin = if (columns.containsKey("nameLatin")) "nameLatin" else "NULL"
                        val selectGroup = if (columns.containsKey("groupName")) "groupName" else "NULL"
                        val selectSub = if (columns.containsKey("subName")) "subName" else "NULL"
                        val selectOrigin = if (columns.containsKey("origin")) "COALESCE(origin, 'manual')" else "'manual'"
                        val selectBatch = if (columns.containsKey("importBatchId")) "importBatchId" else "NULL"
                        val selectUpdatedAt = if (columns.containsKey("updatedAt")) "COALESCE(updatedAt, datetime('now'))" else "datetime('now')"
                        val selectLibrary = if (columns.containsKey("libraryId")) {
                            "COALESCE(libraryId, '$DEFAULT_WET_WEIGHT_LIBRARY_ID')"
                        } else {
                            "'$DEFAULT_WET_WEIGHT_LIBRARY_ID'"
                        }
                        db.execSQL(
                            """
                            INSERT INTO wetweights_custom_new
                                (nameCn, nameLatin, wetWeightMg, groupName, subName, origin, importBatchId, updatedAt, libraryId)
                            SELECT
                                nameCn,
                                $selectNameLatin,
                                wetWeightMg,
                                $selectGroup,
                                $selectSub,
                                $selectOrigin,
                                $selectBatch,
                                $selectUpdatedAt,
                                $selectLibrary
                            FROM wetweights_custom
                            WHERE nameCn IS NOT NULL AND wetWeightMg IS NOT NULL
                            """.trimIndent(),
                        )
                    }
                    db.execSQL("DROP TABLE wetweights_custom")
                    db.execSQL("ALTER TABLE wetweights_custom_new RENAME TO wetweights_custom")
                }
            }

            db.execSQL("CREATE INDEX IF NOT EXISTS index_wetweights_custom_libraryId ON wetweights_custom(libraryId)")
        }

        private val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        private val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        private val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        private val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        // Repair legacy 9.x installs that may already be on version 9 but still have
        // an older wetweights_custom layout (missing/nullable columns or defaults).
        private val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        // Repair installs that have already reached schema 10 but still carry a
        // malformed wetweights_custom table from earlier partial upgrades.
        private val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        // Force-repair installs already at 11 whose tables are still malformed.
        private val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureWetWeightLibrarySchema(db)
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "plankton.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
            ).addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        ensureWetWeightLibrarySchema(db)
                    }
                },
            ).build()
        }
    }
}
