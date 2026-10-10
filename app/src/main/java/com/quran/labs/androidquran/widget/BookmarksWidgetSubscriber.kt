package com.quran.labs.androidquran.widget

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton that monitors for changes to bookmarks and triggers [BookmarksWidget] updates.
 * Bookmark changes are only monitored if at least one [BookmarksWidget] exists.
 */
@SingleIn(AppScope::class)
class BookmarksWidgetSubscriber @Inject constructor(
  private val bookmarksDao: BookmarksDao,
  private val bookmarksWidgetUpdater: BookmarksWidgetUpdater
) {
  private var scope: CoroutineScope = MainScope()
  private var isSubscribed = false

  fun subscribeBookmarksWidgetIfNecessary() {
    // getAppWidgetIds is a binder call, so keep it off the main thread during app start (ANRs)
    scope.launch {
      val hasWidgets = withContext(Dispatchers.IO) {
        bookmarksWidgetUpdater.checkForAnyBookmarksWidgets()
      }
      if (hasWidgets) {
        subscribeBookmarksWidget()
      }
    }
  }

  private fun subscribeBookmarksWidget() {
    if (isSubscribed) {
      return
    }

    isSubscribed = true
    scope = MainScope()
    bookmarksDao.changes
      .onEach { bookmarksWidgetUpdater.updateBookmarksWidget() }
      .launchIn(scope)
  }

  fun onEnabledBookmarksWidget() {
    subscribeBookmarksWidget()
  }

  fun onDisabledBookmarksWidget() {
    scope.cancel()
    isSubscribed = false
  }
}
