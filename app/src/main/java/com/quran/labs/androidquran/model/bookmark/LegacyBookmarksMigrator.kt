package com.quran.labs.androidquran.model.bookmark

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.migration.LegacyBookmarkMigrationNormalizer
import com.quran.mobile.bookmark.migration.LegacyBookmarksDataSource
import com.quran.mobile.bookmark.migration.PreReleaseMobileSyncUpgrade
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@SingleIn(AppScope::class)
class LegacyBookmarksMigrator @Inject constructor(
  private val quranSettings: QuranSettings,
  private val legacyBookmarksDataSource: LegacyBookmarksDataSource,
  private val normalizer: LegacyBookmarkMigrationNormalizer,
  private val mobileSyncImporter: MobileSyncImporter,
  private val preReleaseMobileSyncUpgrade: PreReleaseMobileSyncUpgrade
) {
  private val migrationMutex = Mutex()

  suspend fun migrateIfNeeded(): Boolean {
    if (quranSettings.haveMigratedLegacyBookmarksToMobileSync() &&
      !preReleaseMobileSyncUpgrade.hasPendingImport()
    ) {
      return true
    }

    return migrationMutex.withLock {
      importPreReleaseMobileSyncData()
      if (quranSettings.haveMigratedLegacyBookmarksToMobileSync()) {
        true
      } else {
        migrate()
      }
    }
  }

  // Rattil: bring over bookmarks stored by 2.10.0's older mobile-sync schema
  private suspend fun importPreReleaseMobileSyncData() {
    withContext(Dispatchers.IO) {
      try {
        preReleaseMobileSyncUpgrade.importIfNeeded()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.e(exception, "Unable to import pre-release mobile-sync bookmarks.")
      }
    }
  }

  private suspend fun migrate(): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        val snapshot = legacyBookmarksDataSource.snapshot()
        if (!snapshot.isEmpty()) {
          val migrationData = normalizer.normalize(snapshot)
          if (!migrationData.isEmpty()) {
            mobileSyncImporter.importData(migrationData, deleteExisting = false)
          }
        }

        quranSettings.setMigratedLegacyBookmarksToMobileSync()
        true
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        Timber.e(exception, "Unable to migrate legacy bookmarks to mobile-sync.")
        false
      }
    }
  }
}
