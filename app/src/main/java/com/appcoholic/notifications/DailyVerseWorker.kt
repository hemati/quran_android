package com.appcoholic.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import dev.zacsweers.metro.Inject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Posts the daily-verse notification, then re-enqueues itself for the next day.
 *
 * Contract:
 * - **Never crashes.** Any failure during the post is swallowed; the worker
 *   still self-reschedules and returns [Result.success] so the daily cadence
 *   survives a transient DB/notification error.
 * - **Idempotent per day.** [DailyVersePolicy] dedupes on the calendar date, so
 *   re-runs on the same day skip (recording a `skipped` analytics event) rather
 *   than double-posting.
 * - **Self-rescheduling.** [NotificationScheduler.scheduleDailyVerse] runs at
 *   the end on every path (posted, skipped, or errored), keeping the chain alive
 *   and letting the smart trigger hour adapt over time.
 *
 * All collaborators are injected into the nested [Factory]; WorkManager itself
 * only ever constructs the worker through that factory (see
 * `di/NotificationWorkerModule`).
 */
class DailyVerseWorker(
  appContext: Context,
  params: WorkerParameters,
  private val repo: DailyVerseRepository,
  private val textLoader: VerseTextLoader,
  private val notificationFactory: ReminderNotificationFactory,
  private val prefs: NotificationPrefs,
  private val analytics: NotificationAnalytics,
  private val scheduler: NotificationScheduler,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    try {
      val zone = ZoneId.systemDefault()
      // Resolve "now" once so the dedupe key and verse selection cannot
      // straddle midnight (two now() calls could land on different days).
      val date = LocalDate.now(zone)
      val today = date.toString()
      val decision = DailyVersePolicy.shouldPost(
        prefs.dailyVerseEnabled, prefs.lastPostedDate(TYPE), today
      )
      if (decision.shouldPost) {
        val ref = repo.verseForDay(date.toEpochDay())
        val text = textLoader.load(ref)
        notificationFactory.ensureChannels()
        val shown = notificationFactory.post(
          NotificationConstants.NOTIF_ID_DAILY_VERSE,
          notificationFactory.buildDailyVerse(ref, text)
        )
        if (shown) {
          prefs.setLastPostedDate(TYPE, today)   // only dedupe a verse we actually showed
          analytics.posted(TYPE, POST_DELAY_MINUTES)
        } else {
          analytics.skipped(TYPE, "not_shown")
        }
      } else {
        analytics.skipped(TYPE, decision.reason)
      }
    } catch (e: Exception) {
      // Never crash the worker; still reschedule below for the next day.
    } finally {
      // Self-reschedule on every path (posted, skipped, or errored). Guarded so
      // a reschedule failure can never break the worker's never-crash contract.
      runCatching { scheduler.scheduleDailyVerse() }
    }
    return Result.success()
  }

  class Factory @Inject constructor(
    private val repo: DailyVerseRepository,
    private val textLoader: VerseTextLoader,
    private val notificationFactory: ReminderNotificationFactory,
    private val prefs: NotificationPrefs,
    private val analytics: NotificationAnalytics,
    private val scheduler: NotificationScheduler,
  ) : WorkerTaskFactory {
    override fun makeWorker(
      appContext: Context,
      workerParameters: WorkerParameters
    ): ListenableWorker =
      DailyVerseWorker(
        appContext, workerParameters, repo, textLoader, notificationFactory,
        prefs, analytics, scheduler
      )
  }

  companion object {
    private const val TYPE = "daily_verse"
    // Worker posts on-trigger, so the post happens with ~0 delay from its
    // scheduled time. (Int, not Long: the Firebase analytics provider drops Long params.)
    private const val POST_DELAY_MINUTES = 0
  }
}
