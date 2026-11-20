package com.appcoholic.notifications

import android.app.Application
import timber.log.Timber
import kotlin.concurrent.thread

/** Single fork-owned entry point called once from QuranApplication.onCreate(). */
class NotificationBootstrap(private val application: Application) {
  fun start() {
    runCatching {
      val prefs = NotificationPrefs(application)
      application.registerActivityLifecycleCallbacks(ReadingActivityTracker(prefs))
      // off the main thread: WorkManager and time zone setup slowed cold starts into ANRs
      thread(name = "rattil-reminders") {
        runCatching { NotificationScheduler(application, prefs).ensureScheduled() }
          .onFailure { Timber.w(it, "NotificationBootstrap failed to schedule reminders") }
      }
    }.onFailure {
      // Never crash startup; log so a silently-broken reminder feature is diagnosable.
      Timber.w(it, "NotificationBootstrap failed to start reminders")
    }
  }
}
