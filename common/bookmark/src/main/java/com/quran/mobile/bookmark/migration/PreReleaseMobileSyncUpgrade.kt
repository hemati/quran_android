package com.quran.mobile.bookmark.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.quran.data.di.AppScope
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Rattil-only upgrade of the mobile-sync database.
 *
 * Rattil 2.10.0 shipped mobile-sync 0.1.7. Later mobile-sync releases replaced the quran.db
 * schema without a migration (both report schema version 1), so opening an old database
 * crashes. Upstream never released the old schema, so it has no upgrade path for it.
 *
 * [moveAsideIfOutdated] runs in Application.onCreate, before mobile-sync opens quran.db, and
 * renames an old-schema database, letting mobile-sync create a fresh one. [importIfNeeded] then copies the old
 * data into the new database and deletes the old file once the import succeeded.
 */
@SingleIn(AppScope::class)
class PreReleaseMobileSyncUpgrade @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val mobileSyncImporter: MobileSyncImporter
) {

  fun hasPendingImport(): Boolean = appContext.getDatabasePath(OUTDATED_DATABASE_NAME).exists()

  suspend fun importIfNeeded() {
    val outdatedDatabase = appContext.getDatabasePath(OUTDATED_DATABASE_NAME)
    if (!outdatedDatabase.exists()) return

    val data = readOutdatedDatabase(outdatedDatabase)
    if (!data.isEmpty()) {
      mobileSyncImporter.importData(data, deleteExisting = false)
    }
    SQLiteDatabase.deleteDatabase(outdatedDatabase)
  }

  private fun readOutdatedDatabase(file: File): MobileSyncImportData {
    return openDatabase(file, SQLiteDatabase.OPEN_READONLY).use { db ->
      val bookmarkImportIds = mutableMapOf<Long, String>()
      val bookmarks = db.queryList(
        "SELECT local_id, sura, ayah, created_at FROM ayah_bookmark WHERE deleted = 0"
      ) { cursor ->
        val sura = cursor.getInt(1)
        val ayah = cursor.getInt(2)
        val importId = "bookmark-$sura-$ayah"
        bookmarkImportIds[cursor.getLong(0)] = importId
        MobileSyncImportBookmark(importId, sura, ayah, cursor.getLong(3))
      }

      val collections = db.queryList(
        "SELECT local_id, name, created_at FROM collection WHERE deleted = 0"
      ) { cursor ->
        MobileSyncImportCollection(
          importId = collectionImportId(cursor.getLong(0)),
          name = cursor.getString(1),
          timestampMillis = cursor.getLong(2)
        )
      }

      // mobile-sync 0.1.7 stored the ayah bookmark's local_id as text in bookmark_local_id
      val collectionBookmarks = db.queryList(
        """
        SELECT bc.collection_local_id, ab.local_id, bc.created_at
        FROM bookmark_collection bc
        JOIN collection c ON c.local_id = bc.collection_local_id AND c.deleted = 0
        JOIN ayah_bookmark ab ON ab.local_id = CAST(bc.bookmark_local_id AS INTEGER) AND ab.deleted = 0
        WHERE bc.deleted = 0 AND bc.bookmark_type = 'AYAH'
        """.trimIndent()
      ) { cursor ->
        MobileSyncImportCollectionBookmark(
          collectionImportId = collectionImportId(cursor.getLong(0)),
          bookmarkImportId = bookmarkImportIds.getValue(cursor.getLong(1)),
          timestampMillis = cursor.getLong(2)
        )
      }

      val readingSessions = db.queryList(
        "SELECT chapter_number, verse_number, modified_at FROM reading_session WHERE deleted = 0"
      ) { cursor ->
        MobileSyncImportReadingSession(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2))
      }

      val readingBookmarks = db.readingBookmarks(ReadingBookmarkSlot.entries)

      MobileSyncImportData(
        bookmarks = bookmarks,
        collections = collections,
        collectionBookmarks = collectionBookmarks,
        readingSessions = readingSessions,
        readingBookmarks = readingBookmarks
      )
    }
  }

  private fun SQLiteDatabase.readingBookmarks(
    slots: List<ReadingBookmarkSlot>
  ): List<MobileSyncImportReadingBookmark> {
    val rows = queryList(
      """
      SELECT bookmark_type, sura, ayah, page, modified_at FROM reading_bookmark
      WHERE deleted = 0 ORDER BY modified_at DESC LIMIT ${slots.size}
      """.trimIndent()
    ) { cursor ->
      if (cursor.getString(0) == "PAGE") {
        PageRow(cursor.getInt(3), cursor.getLong(4))
      } else {
        AyahRow(cursor.getInt(1), cursor.getInt(2), cursor.getLong(4))
      }
    }
    return rows.zip(slots) { row, slot ->
      when (row) {
        is PageRow -> MobileSyncImportReadingBookmark.Page(slot, row.page, row.timestamp)
        is AyahRow -> MobileSyncImportReadingBookmark.Ayah(slot, row.sura, row.ayah, row.timestamp)
      }
    }
  }

  private sealed interface ReadingBookmarkRow
  private data class PageRow(val page: Int, val timestamp: Long) : ReadingBookmarkRow
  private data class AyahRow(val sura: Int, val ayah: Int, val timestamp: Long) : ReadingBookmarkRow

  companion object {
    private const val DATABASE_NAME = "quran.db"
    internal const val OUTDATED_DATABASE_NAME = "quran-mobile-sync-0.1.7.db"

    /**
     * Renames quran.db if it still has the mobile-sync 0.1.7 schema. Must run before
     * mobile-sync opens the database.
     */
    fun moveAsideIfOutdated(context: Context) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      if (prefs.getBoolean(PREF_SCHEMA_CHECKED, false)) return

      val database = context.getDatabasePath(DATABASE_NAME)
      val isOutdated = database.exists() && try {
        openDatabase(database, SQLiteDatabase.OPEN_READWRITE).use { db -> db.hasTable("ayah_bookmark") }
      } catch (_: SQLiteException) {
        false
      }

      if (isOutdated) {
        val target = context.getDatabasePath(OUTDATED_DATABASE_NAME)
        DATABASE_FILE_SUFFIXES.forEach { suffix ->
          val file = File(database.path + suffix)
          if (file.exists()) {
            file.renameTo(File(target.path + suffix))
          }
        }
      }
      prefs.edit().putBoolean(PREF_SCHEMA_CHECKED, true).apply()
    }

    private const val PREFS_NAME = "rattil_mobile_sync_upgrade"
    private const val PREF_SCHEMA_CHECKED = "schemaChecked"

    private val DATABASE_FILE_SUFFIXES = listOf("", "-journal", "-wal", "-shm")

    private fun collectionImportId(localId: Long) = "collection-$localId"

    private fun openDatabase(file: File, flags: Int): SQLiteDatabase {
      return SQLiteDatabase.openDatabase(
        file.path,
        null,
        flags or SQLiteDatabase.NO_LOCALIZED_COLLATORS
      )
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean {
      return rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(name)
      ).use { cursor -> cursor.moveToFirst() }
    }

    private fun <T> SQLiteDatabase.queryList(sql: String, map: (Cursor) -> T): List<T> {
      return rawQuery(sql, null).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(map(cursor))
          }
        }
      }
    }
  }
}
