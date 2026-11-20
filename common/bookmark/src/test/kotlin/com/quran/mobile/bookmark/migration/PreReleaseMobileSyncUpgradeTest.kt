package com.quran.mobile.bookmark.migration

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.dao.Settings
import com.quran.data.di.AppCoroutineScope
import com.quran.data.model.PageAppearance
import com.quran.data.source.MushafType
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.bookmark.di.MobileSyncDatabase
import com.quran.mobile.bookmark.importdata.MobileSyncImporterImpl
import com.quran.mobile.bookmark.model.BookmarksDaoImpl
import com.quran.mobile.bookmark.model.HighlightsDaoImpl
import com.quran.mobile.bookmark.model.ReadingBookmarkPageMapper
import com.quran.mobile.bookmark.model.RepositoryBackedTestBookmarkCollectionsState
import com.quran.mobile.bookmark.model.TestPageProvider
import com.quran.mobile.bookmark.time.FakeMobileSyncTimestampProvider
import com.quran.shared.persistence.model.AyahHighlightColor
import com.quran.shared.persistence.model.AyahReadingBookmark
import com.quran.shared.persistence.model.Mushaf
import com.quran.shared.persistence.model.PageReadingBookmark
import com.quran.shared.persistence.model.ReadingBookmarkSlot
import com.quran.shared.persistence.repository.bookmark.repository.BookmarksRepositoryImpl
import com.quran.shared.persistence.repository.collection.repository.CollectionsRepositoryImpl
import com.quran.shared.persistence.repository.collectionbookmark.repository.CollectionBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingbookmark.repository.ReadingBookmarksRepositoryImpl
import com.quran.shared.persistence.repository.readingsession.repository.ReadingSessionsRepositoryImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
  private lateinit var mobileSyncDatabase: MobileSyncDatabase
  private lateinit var upgrade: PreReleaseMobileSyncUpgrade

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication().applicationContext as Context
    listOf("quran.db", PreReleaseMobileSyncUpgrade.MOVED_ASIDE_0_1_7_DATABASE_NAME, "user_data.db")
      .forEach { context.deleteDatabase(it) }
    appCoroutineScope = AppCoroutineScope()
    mobileSyncDatabase = MobileSyncDatabase(context)
    upgrade = PreReleaseMobileSyncUpgrade(
      appContext = context,
      mobileSyncImporter = MobileSyncImporterImpl(mobileSyncDatabase, context),
      highlightsDao = HighlightsDaoImpl(
        timestampProvider = FakeMobileSyncTimestampProvider(),
        collectionBookmarksRepository = collectionBookmarksRepository(),
        appCoroutineScope = appCoroutineScope
      ),
      pageMapper = ReadingBookmarkPageMapper(
        settings = UnavailableSettings(),
        pageProviders = mapOf("madani" to TestPageProvider(MushafType.MADANI_1405, MadaniDataSource())),
        fallbackPageType = "madani"
      )
    )
  }

  @After
  fun tearDown() {
    appCoroutineScope.cancel()
  }

  @Test
  fun `no old database means nothing to import`() {
    assertThat(upgrade.hasPendingImport()).isFalse()
  }

  @Test
  fun `2_11_0 data in quran db is imported and the old file deleted`() = runTest {
    create0121Database()
    assertThat(upgrade.hasPendingImport()).isTrue()

    upgrade.importIfNeeded()

    assertThat(upgrade.hasPendingImport()).isFalse()
    assertThat(context.getDatabasePath("quran.db").exists()).isFalse()

    val bookmarksDao = bookmarksDao()
    // highlighted ayahs did not count as bookmarks in 2.11.0 and must not become ones
    val bookmarks = bookmarksDao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 255, 2 to 3)

    val tags = bookmarksDao.tags().filterNot { it.isSystem }
    assertThat(tags.map { it.name }).containsExactly("Ramadan", "Leer")
    val ayatAlKursi = bookmarks.single { it.ayah == 255 }
    assertThat(bookmarksDao.getBookmarkTagIds(ayatAlKursi.id)).contains(tags.single { it.name == "Ramadan" }.id)

    val highlights = collectionBookmarksRepository().getHighlightsFlow().first()
    assertThat(highlights.map { Triple(it.sura, it.ayah, it.color) }).containsExactly(
      Triple(18, 10, AyahHighlightColor.BLUE),
      // 0.1.21's red is pink now; of two highlight colors the later one counts
      Triple(36, 1, AyahHighlightColor.PINK)
    )

    val readingBookmarks = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database.synced)
      .getReadingBookmarks(Mushaf.MADANI_1405)
    val page = readingBookmarks.single { it.slot == ReadingBookmarkSlot.TEAL } as PageReadingBookmark
    assertThat(page.page to page.mushaf).isEqualTo(2 to Mushaf.MADANI_1405)
    val ayah = readingBookmarks.single { it.slot == ReadingBookmarkSlot.ORANGE } as AyahReadingBookmark
    assertThat(Triple(ayah.sura, ayah.ayah, ayah.name)).isEqualTo(Triple(18, 10, "Abends"))
    // the empty third slot stays empty
    assertThat(readingBookmarks.filter {
      it.slot == ReadingBookmarkSlot.RED && (it is PageReadingBookmark || it is AyahReadingBookmark)
    }).isEmpty()

    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database.synced).getReadingSessions()
    assertThat(readingSessions.map { it.sura to it.ayah }).containsExactly(36 to 1)
  }

  @Test
  fun `2_10_0 data in quran db is imported`() = runTest {
    create017Database("quran.db")

    upgrade.importIfNeeded()

    assert017DataImported()
    assertThat(context.getDatabasePath("quran.db").exists()).isFalse()
  }

  @Test
  fun `2_10_0 data that 2_11_0 moved aside is imported`() = runTest {
    create017Database(PreReleaseMobileSyncUpgrade.MOVED_ASIDE_0_1_7_DATABASE_NAME)
    assertThat(upgrade.hasPendingImport()).isTrue()

    upgrade.importIfNeeded()

    assert017DataImported()
    assertThat(upgrade.hasPendingImport()).isFalse()
  }

  @Test
  fun `a database of unknown schema is kept`() = runTest {
    context.openOrCreateDatabase("quran.db", Context.MODE_PRIVATE, null).use {
      it.execSQL("CREATE TABLE something_else(id INTEGER PRIMARY KEY)")
    }

    upgrade.importIfNeeded()

    assertThat(context.getDatabasePath("quran.db").exists()).isTrue()
    assertThat(bookmarksDao().bookmarks()).isEmpty()
  }

  private suspend fun assert017DataImported() {
    val bookmarksDao = bookmarksDao()
    val bookmarks = bookmarksDao.bookmarks()
    assertThat(bookmarks.map { it.sura to it.ayah }).containsExactly(2 to 3, 2 to 255)

    val tags = bookmarksDao.tags().filterNot { it.isSystem }
    assertThat(tags.map { it.name }).containsExactly("Migrationstest")
    val ayatAlKursi = bookmarks.single { it.ayah == 255 }
    assertThat(bookmarksDao.getBookmarkTagIds(ayatAlKursi.id)).contains(tags.single().id)

    val readingBookmarks = ReadingBookmarksRepositoryImpl(mobileSyncDatabase.database.synced)
      .getReadingBookmarks(Mushaf.MADANI_1405)
    val page = readingBookmarks.single { it.slot == ReadingBookmarkSlot.TEAL } as PageReadingBookmark
    assertThat(page.page).isEqualTo(2)
    val ayah = readingBookmarks.single { it.slot == ReadingBookmarkSlot.ORANGE } as AyahReadingBookmark
    assertThat(ayah.sura to ayah.ayah).isEqualTo(18 to 10)

    val readingSessions = ReadingSessionsRepositoryImpl(mobileSyncDatabase.database.synced).getReadingSessions()
    assertThat(readingSessions.map { it.sura to it.ayah }).containsExactly(36 to 1)
  }

  private fun collectionBookmarksRepository() =
    CollectionBookmarksRepositoryImpl(mobileSyncDatabase.database.synced)

  private fun bookmarksDao(): BookmarksDaoImpl {
    val collectionsRepository = CollectionsRepositoryImpl(mobileSyncDatabase.database.synced)
    val collectionBookmarksRepository = collectionBookmarksRepository()
    return BookmarksDaoImpl(
      quranInfoProvider = { QuranInfo(MadaniDataSource()) },
      bookmarksRepository = BookmarksRepositoryImpl(mobileSyncDatabase.database.synced),
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

  // schema (from mobile-sync 0.1.21's .sq files) and rows as Rattil 2.11.0 wrote them
  private fun create0121Database() {
    context.openOrCreateDatabase("quran.db", Context.MODE_PRIVATE, null).use {
      it.execSQL(
        """CREATE TABLE bookmark(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, remote_id TEXT,
          sura INTEGER NOT NULL, ayah INTEGER NOT NULL, mushaf_id INTEGER NOT NULL DEFAULT 1,
          deleted INTEGER NOT NULL DEFAULT 0, bookmark_pending_op TEXT,
          bookmark_pending_version INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, bookmark_modified_at INTEGER NOT NULL)"""
      )
      it.execSQL(
        """CREATE TABLE collection(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, remote_id TEXT,
          name TEXT NOT NULL, created_at INTEGER NOT NULL, modified_at INTEGER NOT NULL,
          deleted INTEGER NOT NULL DEFAULT 0, is_edited INTEGER NOT NULL DEFAULT 0,
          is_default INTEGER NOT NULL DEFAULT 0, is_system INTEGER NOT NULL DEFAULT 0,
          pending_version INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """CREATE TABLE bookmark_collection(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          bookmark_local_id INTEGER NOT NULL, collection_local_id INTEGER NOT NULL,
          is_active INTEGER NOT NULL DEFAULT 1, pending_op TEXT, pending_version INTEGER NOT NULL DEFAULT 0,
          last_synced_bookmark_remote_id TEXT, last_synced_collection_remote_id TEXT,
          created_at INTEGER NOT NULL, modified_at INTEGER NOT NULL,
          UNIQUE(bookmark_local_id, collection_local_id))"""
      )
      it.execSQL(
        """CREATE TABLE reading_bookmark(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, slot INTEGER NOT NULL, name TEXT, bookmark_type TEXT, sura INTEGER,
          ayah INTEGER, page INTEGER, mushaf_id INTEGER, pending_op TEXT,
          pending_version INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL)"""
      )
      it.execSQL(
        """CREATE TABLE reading_session(local_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          remote_id TEXT, chapter_number INTEGER NOT NULL, verse_number INTEGER NOT NULL,
          is_edited INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL,
          modified_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0,
          pending_version INTEGER NOT NULL DEFAULT 0)"""
      )
      it.execSQL(
        """INSERT INTO collection(local_id, name, created_at, modified_at, deleted, is_default, is_system)
          VALUES (1, 'Favorites', 100, 100, 0, 1, 1), (2, 'system:highlights:blue', 100, 100, 0, 0, 1),
          (3, 'system:highlights:red', 100, 100, 0, 0, 1), (4, 'Ramadan', 1500, 1500, 0, 0, 0),
          (5, 'Gelöscht', 1600, 1600, 1, 0, 0), (6, 'Leer', 1700, 1700, 0, 0, 0)"""
      )
      it.execSQL(
        """INSERT INTO bookmark(local_id, sura, ayah, deleted, created_at, modified_at, bookmark_modified_at)
          VALUES (1, 2, 255, 0, 2000, 2000, 2000), (2, 2, 3, 0, 1000, 1000, 1000),
          (3, 18, 10, 0, 3000, 3000, 3000), (4, 3, 1, 1, 3000, 3000, 3000),
          (5, 36, 1, 0, 4000, 4000, 4000), (6, 1, 1, 0, 5000, 5000, 5000)"""
      )
      it.execSQL(
        """INSERT INTO bookmark_collection(bookmark_local_id, collection_local_id, is_active,
          created_at, modified_at)
          VALUES (1, 1, 1, 2000, 2000), (2, 1, 1, 1000, 1000), (1, 4, 1, 2100, 2100),
          (3, 2, 1, 3000, 3000), (5, 2, 1, 4000, 4000), (5, 3, 1, 4500, 4500),
          (4, 1, 1, 3000, 3000), (2, 5, 1, 1600, 1600), (6, 1, 0, 5000, 5000)"""
      )
      it.execSQL(
        """INSERT INTO reading_bookmark(slot, name, bookmark_type, sura, ayah, page, mushaf_id,
          created_at, modified_at)
          VALUES (1, NULL, 'PAGE', NULL, NULL, 2, 1, 4000, 5000),
          (2, 'Abends', 'AYAH', 18, 10, NULL, 1, 3000, 4500),
          (3, NULL, NULL, NULL, NULL, NULL, NULL, 1000, 1000)"""
      )
      it.execSQL(
        """INSERT INTO reading_session(chapter_number, verse_number, created_at, modified_at, deleted)
          VALUES (36, 1, 6000, 6000, 0), (1, 1, 100, 100, 1)"""
      )
    }
  }

  // schema and rows as written by mobile-sync 0.1.7 (Rattil 2.10.0)
  private fun create017Database(name: String) {
    context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
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

  private class UnavailableSettings : Settings {
    override suspend fun setVersion(version: Int) = unused()

    override suspend fun setShouldOverlayPageInfo(shouldOverlay: Boolean) = unused()

    override suspend fun lastPage(): Int = unused()

    override suspend fun pageAppearance(): PageAppearance = unused()

    override suspend fun pageTextBrightness(): Int = unused()

    override suspend fun nightModeBackgroundBrightness(): Int = unused()

    override suspend fun dayPageBackgroundColor(): Int? = unused()

    override suspend fun shouldShowHeaderFooter(): Boolean = unused()

    override suspend fun shouldShowBookmarks(): Boolean = unused()

    override suspend fun pageType(): String = unused()

    override suspend fun setPageType(pageType: String) = unused()

    override suspend fun showSidelines(): Boolean = unused()

    override suspend fun setShowSidelines(show: Boolean) = unused()

    override suspend fun showLineDividers(): Boolean = unused()

    override suspend fun setShouldShowLineDividers(show: Boolean) = unused()

    override suspend fun setAyahTextSize(value: Int) = unused()

    override suspend fun translationTextSize(): Int = unused()

    override fun preferencesFlow(): Flow<String> = emptyFlow()

    private fun unused(): Nothing = throw UnsupportedOperationException()
  }
}
