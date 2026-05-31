package com.appcoholic.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import com.quran.labs.androidquran.data.QuranDisplayData
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts the re-engagement ("continue reading") notification when the user has
 * been idle past the threshold and no suppression rule applies.
 *
 * Contract:
 * - **Periodic, NOT self-rescheduling.** Unlike [DailyVerseWorker], this worker
 *   is enqueued as a periodic WorkManager request (by the Task 11 scheduler), so
 *   WorkManager re-runs it on its interval. It must never call a scheduler.
 * - **Never crashes.** Any failure is swallowed and [Result.success] is returned
 *   so the periodic chain survives a transient DB/notification error and simply
 *   runs again next interval.
 * - **Pure decision off-loaded** to [ReEngagementPolicy]; this worker only reads
 *   prefs / clock / zone, posts on "ok", and records analytics on every path.
 *
 * On a post it bumps [NotificationPrefs.consecutiveUnanswered]; that counter is
 * reset to 0 when the user opens the app (ReadingActivityTracker, a later task),
 * which is how the policy's "paused" back-off recovers.
 *
 * All collaborators are injected into the nested [Factory]; WorkManager itself
 * only ever constructs the worker through that factory (see
 * `di/NotificationWorkerModule`).
 */
class ReEngagementWorker(
  appContext: Context,
  params: WorkerParameters,
  private val prefs: NotificationPrefs,
  private val lastReadProvider: LastReadProvider,
  private val quranDisplayData: QuranDisplayData,
  private val notificationFactory: ReminderNotificationFactory,
  private val analytics: NotificationAnalytics,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    try {
      val zone = ZoneId.systemDefault()
      val nowMs = System.currentTimeMillis()
      val offsetHours = zone.rules.getOffset(Instant.now()).totalSeconds / 3600
      val postedVerseToday = prefs.lastPostedDate("daily_verse") == LocalDate.now(zone).toString()
      val decision = ReEngagementPolicy.decide(
        enabled = prefs.reEngagementEnabled, now = nowMs,
        lastForegroundAt = prefs.lastForegroundAt, lastReEngagedAt = prefs.lastReEngagedAt,
        consecutiveUnanswered = prefs.consecutiveUnanswered, postedVerseToday = postedVerseToday,
        thresholdDays = THRESHOLD_DAYS, quietStart = QUIET_START, quietEnd = QUIET_END, zoneOffsetHours = offsetHours,
      )
      if (decision.shouldPost) {
        val page = lastReadProvider.lastReadPage()
        val suraName = page?.let { quranDisplayData.getSuraNameFromPage(applicationContext, it, true) }
        notificationFactory.ensureChannels()
        notificationFactory.post(
          NotificationConstants.NOTIF_ID_REENGAGE,
          notificationFactory.buildReEngagement(suraName, page)
        )
        prefs.lastReEngagedAt = nowMs
        prefs.consecutiveUnanswered = prefs.consecutiveUnanswered + 1
        analytics.posted(TYPE, 0)
      } else {
        analytics.skipped(TYPE, decision.reason)
      }
    } catch (e: Exception) {
      // never crash; periodic WorkManager will run again next interval
    }
    return Result.success()
  }

  class Factory @Inject constructor(
    private val prefs: NotificationPrefs,
    private val lastReadProvider: LastReadProvider,
    private val quranDisplayData: QuranDisplayData,
    private val notificationFactory: ReminderNotificationFactory,
    private val analytics: NotificationAnalytics,
  ) : WorkerTaskFactory {
    override fun makeWorker(
      appContext: Context,
      workerParameters: WorkerParameters
    ): ListenableWorker =
      ReEngagementWorker(
        appContext, workerParameters, prefs, lastReadProvider, quranDisplayData,
        notificationFactory, analytics
      )
  }

  companion object {
    private const val TYPE = "reengage"
    private const val THRESHOLD_DAYS = 3
    private const val QUIET_START = 22
    private const val QUIET_END = 7
  }
}
