package com.appcoholic.notifications

import android.app.Application
import timber.log.Timber

/** Single fork-owned entry point called once from QuranApplication.onCreate(). */
class NotificationBootstrap(private val application: Application) {
  fun start() {
    runCatching {
      val prefs = NotificationPrefs(application)
      application.registerActivityLifecycleCallbacks(ReadingActivityTracker(prefs))
      NotificationScheduler(application, prefs).scheduleAll()
    }.onFailure {
      // Never crash startup; log so a silently-broken reminder feature is diagnosable.
      Timber.w(it, "NotificationBootstrap failed to start reminders")
    }
  }
}
