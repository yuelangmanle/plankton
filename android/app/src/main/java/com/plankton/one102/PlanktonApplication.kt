package com.plankton.one102

import android.app.Application
import com.plankton.one102.data.log.AppLogger
import com.plankton.one102.data.db.AppDatabase
import com.plankton.one102.data.prefs.AppPreferences
import com.plankton.one102.data.repo.DatasetRepository
import com.plankton.one102.data.repo.AiCacheRepository
import com.plankton.one102.data.repo.AliasRepository
import com.plankton.one102.data.repo.BackupRepository
import com.plankton.one102.data.repo.TaxonomyRepository
import com.plankton.one102.data.repo.TaxonomyOverrideRepository
import com.plankton.one102.data.repo.WetWeightRepository
import com.plankton.one102.voiceassistant.VoiceAssistantHub

class PlanktonApplication : Application() {
  val database: AppDatabase by lazy { AppDatabase.create(this) }
  val preferences: AppPreferences by lazy { AppPreferences(this) }
  val voiceAssistantHub: VoiceAssistantHub by lazy { VoiceAssistantHub() }

  val datasetRepository: DatasetRepository by lazy { DatasetRepository(database.datasetDao()) }
  val wetWeightRepository: WetWeightRepository by lazy { WetWeightRepository(this, database.wetWeightDao(), database.wetWeightLibraryDao()) }
  val taxonomyRepository: TaxonomyRepository by lazy { TaxonomyRepository(this) }
  val taxonomyOverrideRepository: TaxonomyOverrideRepository by lazy { TaxonomyOverrideRepository(database.taxonomyDao()) }
  val aliasRepository: AliasRepository by lazy { AliasRepository(database.aliasDao()) }
  val aiCacheRepository: AiCacheRepository by lazy { AiCacheRepository(database.aiCacheDao()) }
  val backupRepository: BackupRepository by lazy {
    BackupRepository(
      context = this,
      prefs = preferences,
      datasetRepo = datasetRepository,
      wetWeightDao = database.wetWeightDao(),
      wetWeightLibraryDao = database.wetWeightLibraryDao(),
      taxonomyDao = database.taxonomyDao(),
      aliasDao = database.aliasDao(),
      aiCacheDao = database.aiCacheDao(),
    )
  }

  override fun onCreate() {
    super.onCreate()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      AppLogger.logError(this, "crash", "Uncaught exception on ${thread.name}", throwable)
      previous?.uncaughtException(thread, throwable)
    }
  }
}
