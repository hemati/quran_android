package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import com.quran.data.dao.RecentPagesDao
import com.quran.data.model.bookmark.RecentPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Instant

class LastReadProviderTest {
  private fun dao(pages: List<Int>) = object : RecentPagesDao {
    override fun recentPagesFlow(): Flow<List<RecentPage>> = flowOf(emptyList())
    override suspend fun recentPages() = pages.map { RecentPage(it, Instant.fromEpochMilliseconds(0)) }
    override suspend fun addRecentPage(page: Int) {}
    override suspend fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {}
    override suspend fun removeRecentPages() {}
    override suspend fun replaceRecentPages(pages: List<RecentPage>) {}
    override suspend fun removeRecentsForPage(page: Int) {}
  }

  @Test fun returns_null_when_no_history() = runTest {
    assertThat(LastReadProvider(dao(emptyList())).lastReadPage()).isNull()
  }
  @Test fun returns_most_recent_page() = runTest {
    assertThat(LastReadProvider(dao(listOf(42, 10))).lastReadPage()).isEqualTo(42)
  }
}
