package com.appcoholic.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the fork's reminder work via [WorkManager].
 *
 * Two reminders are scheduled, each gated on its own enabled flag:
 * - Daily verse ([scheduleDailyVerse]) is a one-time request that the worker
 *   re-enqueues after each run (self-reschedule), which keeps the trigger hour
 *   adaptive (smart hour can shift) without relying on periodic work's
 *   minimum-interval constraints.
 * - Re-engagement ([scheduleReEngagement]) is a daily periodic check; the
 *   worker's policy decides whether to actually post.
 *
 * Use [scheduleAll] to (re)schedule both idempotently and [cancelAll] to tear
 * everything down.
 */
@Inject
class NotificationScheduler(
  @ApplicationContext private val context: Context,
  private val prefs: NotificationPrefs,
) {
  fun scheduleDailyVerse() {
    if (!prefs.dailyVerseEnabled) {
      WorkManager.getInstance(context).cancelUniqueWork(NotificationConstants.WORK_DAILY_VERSE)
      return
    }
    val hour = prefs.verseHourOverride ?: prefs.smartVerseHour() ?: DEFAULT_VERSE_HOUR
    val zone = ZoneId.systemDefault()
    val delay = VerseTimeCalculator.delayMillis(Instant.now(), zone, hour)
    val req = OneTimeWorkRequestBuilder<DailyVerseWorker>()
      .setInitialDelay(delay, TimeUnit.MILLISECONDS)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      NotificationConstants.WORK_DAILY_VERSE, ExistingWorkPolicy.REPLACE, req
    )
  }

  /** Daily periodic check; the worker's policy decides whether to actually post. */
  fun scheduleReEngagement() {
    if (!prefs.reEngagementEnabled) {
      WorkManager.getInstance(context).cancelUniqueWork(NotificationConstants.WORK_REENGAGE)
      return
    }
    val req = PeriodicWorkRequestBuilder<ReEngagementWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      NotificationConstants.WORK_REENGAGE, ExistingPeriodicWorkPolicy.UPDATE, req
    )
  }

  /** Idempotent: (re)schedules both reminders, each respecting its enabled flag. */
  fun scheduleAll() {
    scheduleDailyVerse()
    scheduleReEngagement()
  }

  /** Cancels all reminder work (e.g. when the user disables everything). */
  fun cancelAll() {
    WorkManager.getInstance(context).cancelUniqueWork(NotificationConstants.WORK_DAILY_VERSE)
    WorkManager.getInstance(context).cancelUniqueWork(NotificationConstants.WORK_REENGAGE)
  }

  companion object {
    const val DEFAULT_VERSE_HOUR = 9
  }
}
