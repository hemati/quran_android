package com.quran.mobile.bookmark.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.quran.data.dao.HighlightsDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightColor
import com.quran.mobile.bookmark.importdata.MobileSyncImportBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollection
import com.quran.mobile.bookmark.importdata.MobileSyncImportCollectionBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportData
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingBookmark
import com.quran.mobile.bookmark.importdata.MobileSyncImportReadingSession
import com.quran.mobile.bookmark.importdata.MobileSyncImporter
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Rattil-only upgrade of the mobile-sync data.
 *
 * Rattil released two mobile-sync versions that upstream never shipped, so upstream has no upgrade
 * path for either:
 * - 2.10.0 shipped mobile-sync 0.1.7, whose quran.db schema later releases replaced without a
 *   migration. 2.11.0 renamed such a database to [MOVED_ASIDE_0_1_7_DATABASE_NAME] at startup.
 * - 2.11.0 shipped mobile-sync 0.1.21, which keeps its data in quran.db. Newer mobile-sync keeps
 *   its data in user_data.db, with a new schema, and never opens quran.db.
 *
 * [importIfNeeded] reads every old database that is left, imports its data into the current
 * database and deletes the old file once the import succeeded.
 */
@SingleIn(AppScope::class)
class PreReleaseMobileSyncUpgrade @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val mobileSyncImporter: MobileSyncImporter,
  private val highlightsDao: HighlightsDao,
  private val pageMapper: ReadingBookmarkPageMapper
) {

  fun hasPendingImport(): Boolean = OLD_DATABASE_NAMES.any { name -> appContext.getDatabasePath(name).exists() }

  suspend fun importIfNeeded() {
    OLD_DATABASE_NAMES.forEach { name ->
      val file = appContext.getDatabasePath(name)
      if (!file.exists()) return@forEach

      // an unknown schema is left in place, so no data is deleted that was not imported
      val oldData = readOldDatabase(file) ?: return@forEach
      if (!oldData.data.isEmpty()) {
        mobileSyncImporter.importData(oldData.data, deleteExisting = false)
      }
      oldData.highlights.forEach { (suraAyah, color) -> highlightsDao.setHighlight(suraAyah, color) }
      SQLiteDatabase.deleteDatabase(file)
    }
  }

  private fun readOldDatabase(file: File): OldData? {
    return openReadOnly(file).use { db ->
      when {
        db.hasTable("ayah_bookmark") -> OldData(readSchema017(db), emptyMap())
        db.hasTable("bookmark") && db.hasColumn("bookmark_collection", "is_active") -> readSchema0121(db)
        else -> null
      }
    }
  }

  // mobile-sync 0.1.7 (Rattil 2.10.0)
  private fun readSchema017(db: SQLiteDatabase): MobileSyncImportData {
    val bookmarks = db.queryList(
      "SELECT sura, ayah, created_at FROM ayah_bookmark WHERE deleted = 0"
    ) { cursor ->
      MobileSyncImportBookmark(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2))
    }

    val collections = db.queryList(
      "SELECT name, created_at FROM collection WHERE deleted = 0"
    ) { cursor ->
      MobileSyncImportCollection(name = cursor.getString(0), timestampMillis = cursor.getLong(1))
    }

    // mobile-sync 0.1.7 stored the ayah bookmark's local_id as text in bookmark_local_id
    val collectionBookmarks = db.queryList(
      """
      SELECT c.name, ab.sura, ab.ayah, bc.created_at
      FROM bookmark_collection bc
      JOIN collection c ON c.local_id = bc.collection_local_id AND c.deleted = 0
      JOIN ayah_bookmark ab ON ab.local_id = CAST(bc.bookmark_local_id AS INTEGER) AND ab.deleted = 0
      WHERE bc.deleted = 0 AND bc.bookmark_type = 'AYAH'
      """.trimIndent()
    ) { cursor ->
      MobileSyncImportCollectionBookmark(
        collectionName = cursor.getString(0),
        sura = cursor.getInt(1),
        ayah = cursor.getInt(2),
        timestampMillis = cursor.getLong(3)
      )
    }

    // 0.1.7 had no slots: the most recent reading bookmarks take the slots in order
    val readingBookmarks = db.queryList(
      """
      SELECT bookmark_type, sura, ayah, page, modified_at FROM reading_bookmark
      WHERE deleted = 0 ORDER BY modified_at DESC LIMIT ${ReadingBookmarkSlot.entries.size}
      """.trimIndent()
    ) { cursor -> cursor.toReadingBookmarkRow(typeColumn = 0, suraColumn = 1, pageColumn = 3, timestampColumn = 4) }
      .zip(ReadingBookmarkSlot.entries) { row, slot -> row.toImport(slot, name = null) }

    return MobileSyncImportData(
      bookmarks = bookmarks,
      collections = collections,
      collectionBookmarks = collectionBookmarks,
      readingSessions = db.readingSessions(),
      readingBookmarks = readingBookmarks
    )
  }

  // mobile-sync 0.1.21 (Rattil 2.11.0): bookmarks, tags and highlights are all memberships of an
  // ayah in a collection; the default collection holds the bookmarks, the system highlight
  // collections the highlights and the other non-system collections are the tags.
  private fun readSchema0121(db: SQLiteDatabase): OldData {
    val memberships = db.queryList(
      // the conditions 2.11.0 listed its bookmarks, tags and highlights with
      """
      SELECT c.name, c.is_default, c.is_system, b.sura, b.ayah, b.bookmark_modified_at, bc.modified_at
      FROM bookmark_collection bc
      JOIN collection c ON c.local_id = bc.collection_local_id
      JOIN bookmark b ON b.local_id = bc.bookmark_local_id
      WHERE bc.is_active = 1 AND b.deleted = 0 AND c.deleted = 0
      """.trimIndent()
    ) { cursor ->
      Membership(
        collectionName = cursor.getString(0),
        isDefault = cursor.getInt(1) == 1,
        isSystem = cursor.getInt(2) == 1,
        suraAyah = SuraAyah(cursor.getInt(3), cursor.getInt(4)),
        bookmarkTimestamp = cursor.getLong(5),
        membershipTimestamp = cursor.getLong(6)
      )
    }

    val bookmarks = memberships
      .filter { it.isDefault }
      .groupBy { it.suraAyah }
      .map { (suraAyah, rows) ->
        MobileSyncImportBookmark(suraAyah.sura, suraAyah.ayah, rows.maxOf { it.bookmarkTimestamp })
      }

    val collections = db.queryList(
      "SELECT name, created_at FROM collection WHERE deleted = 0 AND is_system = 0"
    ) { cursor ->
      MobileSyncImportCollection(name = cursor.getString(0), timestampMillis = cursor.getLong(1))
    }

    val collectionBookmarks = memberships
      .filter { !it.isSystem }
      .map { membership ->
        MobileSyncImportCollectionBookmark(
          collectionName = membership.collectionName,
          sura = membership.suraAyah.sura,
          ayah = membership.suraAyah.ayah,
          timestampMillis = membership.membershipTimestamp
        )
      }

    // like 2.11.0, an ayah in several highlight collections shows its latest color
    val highlights = memberships
      .mapNotNull { membership ->
        val color = membership.takeIf { it.isSystem }?.collectionName?.toHighlightColor()
        color?.let { membership to it }
      }
      .groupBy { (membership, _) -> membership.suraAyah }
      .mapValues { (_, rows) -> rows.maxBy { (membership, _) -> membership.membershipTimestamp }.second }

    val readingBookmarks = db.queryList(
      """
      SELECT slot, bookmark_type, sura, ayah, page, name, modified_at FROM reading_bookmark
      WHERE bookmark_type IS NOT NULL
      """.trimIndent()
    ) { cursor ->
      // slots are stored as 1, 2, 3 in every mobile-sync version; only their color names changed
      val slot = ReadingBookmarkSlot.entries.getOrNull(cursor.getInt(0) - 1)
      val row = cursor.toReadingBookmarkRow(typeColumn = 1, suraColumn = 2, pageColumn = 4, timestampColumn = 6)
      slot?.let { row.toImport(it, name = cursor.getStringOrNull(5)) }
    }.filterNotNull()

    return OldData(
      data = MobileSyncImportData(
        bookmarks = bookmarks,
        collections = collections,
        collectionBookmarks = collectionBookmarks,
        readingSessions = db.readingSessions(),
        readingBookmarks = readingBookmarks
      ),
      highlights = highlights
    )
  }

  private fun SQLiteDatabase.readingSessions(): List<MobileSyncImportReadingSession> {
    if (!hasTable("reading_session")) return emptyList()
    return queryList(
      "SELECT chapter_number, verse_number, modified_at FROM reading_session WHERE deleted = 0"
    ) { cursor ->
      MobileSyncImportReadingSession(cursor.getInt(0), cursor.getInt(1), cursor.getLong(2))
    }
  }

  private fun Cursor.toReadingBookmarkRow(
    typeColumn: Int,
    suraColumn: Int,
    pageColumn: Int,
    timestampColumn: Int
  ): ReadingBookmarkRow {
    return if (getString(typeColumn) == "PAGE") {
      PageRow(getInt(pageColumn), getLong(timestampColumn))
    } else {
      AyahRow(getInt(suraColumn), getInt(suraColumn + 1), getLong(timestampColumn))
    }
  }

  private fun ReadingBookmarkRow.toImport(
    slot: ReadingBookmarkSlot,
    name: String?
  ): MobileSyncImportReadingBookmark {
    return when (this) {
      // old versions stored no page type; their pages are those of the reference page type
      is PageRow -> MobileSyncImportReadingBookmark.Page(
        slot, page, pageMapper.sourceMushafFor(null), timestamp, name
      )
      is AyahRow -> MobileSyncImportReadingBookmark.Ayah(slot, sura, ayah, timestamp, name)
    }
  }

  private class OldData(
    val data: MobileSyncImportData,
    val highlights: Map<SuraAyah, HighlightColor>
  )

  private data class Membership(
    val collectionName: String,
    val isDefault: Boolean,
    val isSystem: Boolean,
    val suraAyah: SuraAyah,
    val bookmarkTimestamp: Long,
    val membershipTimestamp: Long
  )

  private sealed interface ReadingBookmarkRow
  private data class PageRow(val page: Int, val timestamp: Long) : ReadingBookmarkRow
  private data class AyahRow(val sura: Int, val ayah: Int, val timestamp: Long) : ReadingBookmarkRow

  companion object {
    private const val DATABASE_NAME = "quran.db"
    internal const val MOVED_ASIDE_0_1_7_DATABASE_NAME = "quran-mobile-sync-0.1.7.db"
    private val OLD_DATABASE_NAMES = listOf(MOVED_ASIDE_0_1_7_DATABASE_NAME, DATABASE_NAME)

    // 0.1.21 named its highlight collections by color; "red" is called pink since
    private val HIGHLIGHT_COLORS = mapOf(
      "system:highlights:blue" to HighlightColor.BLUE,
      "system:highlights:red" to HighlightColor.PINK,
      "system:highlights:green" to HighlightColor.GREEN,
      "system:highlights:yellow" to HighlightColor.YELLOW,
      "system:highlights:purple" to HighlightColor.PURPLE
    )

    private fun String.toHighlightColor(): HighlightColor? = HIGHLIGHT_COLORS[trim().lowercase()]

    private fun openReadOnly(file: File): SQLiteDatabase {
      return SQLiteDatabase.openDatabase(
        file.path,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
      )
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean {
      return rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(name)
      ).use { cursor -> cursor.moveToFirst() }
    }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean {
      return rawQuery("PRAGMA table_info($table)", null).use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        generateSequence { cursor.takeIf { it.moveToNext() } }.any { it.getString(nameIndex) == column }
      }
    }

    private fun Cursor.getStringOrNull(column: Int): String? = if (isNull(column)) null else getString(column)

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
