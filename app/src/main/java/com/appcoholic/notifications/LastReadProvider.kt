package com.appcoholic.notifications

import com.quran.data.dao.RecentPagesDao
import dev.zacsweers.metro.Inject

class LastReadProvider @Inject constructor(private val recentPagesDao: RecentPagesDao) {
  /**
   * The most recently read page, or null if there is no reading history.
   *
   * Relies on [RecentPagesDao.recentPages] returning entries newest-first
   * (verified: `RecentPagesDaoImpl` sorts descending by `lastUpdated`). If a
   * future DAO implementation changes that ordering, this must be revisited.
   */
  suspend fun lastReadPage(): Int? = recentPagesDao.recentPages().firstOrNull()?.page
}
