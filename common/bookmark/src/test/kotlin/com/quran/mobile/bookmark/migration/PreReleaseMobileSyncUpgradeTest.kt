package com.quran.mobile.bookmark.migration

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppCoroutineScope
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import com.quran.mobile.bookmark.model.RepositoryBackedTestBookmarkCollectionsState
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahReadingBookmark
import com.quran.shared.persistence.model.PageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PreReleaseMobileSyncUpgradeTest {

  private lateinit var context: Context
  private lateinit var appCoroutineScope: AppCoroutineScope

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication().applicationContext as Context
    context.deleteDatabase("quran.db")
    context.deleteDatabase(PreReleaseMobileSyncUpgrade.OUTDATED_DATABASE_NAME)
    appCoroutineScope = AppCoroutineScope()
  }

  @After
  fun tearDown() {
    appCoroutineScope.cancel()
  }

  @Test
  fun `old schema data is imported into the new database`() = runTest {
    createOldSchemaDatabase()

    PreReleaseMobileSyncUpgrade.moveAsideIfOutdated(context)
    val mobileSyncDatabase = MobileSyncDatabase(context)
    val upgrade = PreReleaseMobileSyncUpgrade(context, MobileSyncImporterImpl(mobileSyncDatabase, context))
    assertThat(upgrade.hasPendingImport()).isTrue()

    upgrade.importIfNeeded()

    assertThat(upgrade.hasPendingImport()).isFalse()
    val bookmarksDao = bookmarksDao(mobileSyncDatabase)
    val bookmarks = bookmarksDao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 3, 2 to 255)

    val tags = bookmarksDao.tags().filterNot { it.isSystem }
    assertThat(tags.map { it.name }).containsExactly("Migrationstest")
    val ayatAlKursi = bookmarks.single { it.ayah == 255 }
    assertThat(bookmarksDao.getBookmarkTagIds(ayatAlKursi.id)).contains(tags.single().id)

    val readingBookmarks = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database).getReadingBookmarks()
    val page = readingBookmarks.single { it.slot == ReadingBookmarkSlot.GREEN } as PageReadingBookmark
    assertThat(page.page).isEqualTo(2)
    val ayah = readingBookmarks.single { it.slot == ReadingBookmarkSlot.PURPLE } as AyahReadingBookmark
    assertThat(ayah.sura to ayah.ayah).isEqualTo(18 to 10)

    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database).getReadingSessions()
    assertThat(readingSessions.map { it.sura to it.ayah }).containsExactly(36 to 1)
  }

  @Test
  fun `current schema database is left alone`() = runTest {
    val mobileSyncDatabase = MobileSyncDatabase(context)
    ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database)
      .setPageReadingBookmark(ReadingBookmarkSlot.GREEN, 12)

    PreReleaseMobileSyncUpgrade.moveAsideIfOutdated(context)

    assertThat(context.getDatabasePath("quran.db").exists()).isTrue()
    assertThat(PreReleaseMobileSyncUpgrade(context, MobileSyncImporterImpl(mobileSyncDatabase, context))
      .hasPendingImport()).isFalse()
  }

  private fun bookmarksDao(mobileSyncDatabase: MobileSyncDatabase): BookmarksDaoImpl {
    val collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database)
    val collectionBookmarksRepository = CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database)
    return BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database),
      collectionsRepository = collectionsRepository,
      collectionBookmarksRepository = collectionBookmarksRepository,
      bookmarkCollectionsState = RepositoryBackedTestBookmarkCollectionsState(
        collectionsRepository,
        collectionBookmarksRepository,
        appCoroutineScope
      ),
      timestampProvider = FakeMobileSyncTimestampProvider(),
      appCoroutineScope = appCoroutineScope
    )
  }

  // schema and rows as written by mobile-sync 0.1.7 (Rattil 2.10.0)
  private fun createOldSchemaDatabase() {
    val db = context.openOrCreateDatabase("quran.db", Context.MODE_PRIVATE, null)
    db.use {
      it.execSQL(
        """CREATE TABLE ayah_bookmark(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, ayah_id INTEGER NOT NULL, sura INTEGER NOT NULL, ayah INTEGER NOT NULL,
          is_edited INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE collection(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, name TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0,
          is_edited INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE bookmark_collection(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, bookmark_local_id TEXT NOT NULL, bookmark_type TEXT NOT NULL,
          collection_local_id INTEGER NOT NULL, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE reading_bookmark(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, bookmark_type TEXT NOT NULL, sura INTEGER, ayah INTEGER, page INTEGER,
          is_edited INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE reading_session(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, chapter_number INTEGER NOT NULL, verse_number INTEGER NOT NULL,
          is_edited INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE note(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, remote_id TEXT,
          note TEXT NOT NULL, start_ayah_id INTEGER NOT NULL, end_ayah_id INTEGER NOT NULL,
          created_at INTEGER NOT NULL, modified_at INTEGER NOT NULL,
          deleted INTEGER NOT NULL DEFAULT 0, is_edited INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """INSERT INTO ayah_bookmark(local_id, ayah_id, sura, ayah, created_at, modified_at, deleted)
          VALUES (1, 10, 2, 3, 1000, 1000, 0), (2, 262, 2, 255, 2000, 2000, 0),
          (3, 300, 3, 1, 3000, 3000, 1)"""
      )
      it.execSQL(
        """INSERT INTO collection(local_id, name, created_at, modified_at, deleted)
          VALUES (1, 'Migrationstest', 1500, 1500, 0), (2, 'Deleted', 1600, 1600, 1)"""
      )
      it.execSQL(
        """INSERT INTO bookmark_collection(bookmark_local_id, bookmark_type, collection_local_id,
          created_at, modified_at, deleted)
          VALUES ('2', 'AYAH', 1, 2500, 2500, 0), ('1', 'AYAH', 2, 2600, 2600, 0)"""
      )
      it.execSQL(
        """INSERT INTO reading_bookmark(bookmark_type, sura, ayah, page, created_at, modified_at, deleted)
          VALUES ('PAGE', NULL, NULL, 2, 4000, 5000, 0), ('AYAH', 18, 10, NULL, 3000, 4500, 0),
          ('PAGE', NULL, NULL, 50, 1000, 9000, 1)"""
      )
      it.execSQL(
        """INSERT INTO reading_session(chapter_number, verse_number, created_at, modified_at, deleted)
          VALUES (36, 1, 6000, 6000, 0), (1, 1, 100, 100, 1)"""
      )
    }
  }
}
