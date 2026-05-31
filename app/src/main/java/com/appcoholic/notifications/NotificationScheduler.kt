package com.appcoholic.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the fork's reminder work via [WorkManager].
 *
 * v1 implements only [scheduleDailyVerse]; re-engagement scheduling plus
 * `scheduleAll`/`cancelAll` arrive in a later task. The daily-verse job is a
 * one-time request that the worker re-enqueues after each run (self-reschedule),
 * which keeps the trigger hour adaptive (smart hour can shift) without relying
 * on periodic work's minimum-interval constraints.
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

  companion object {
    const val DEFAULT_VERSE_HOUR = 9
  }
}
