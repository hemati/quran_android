package com.appcoholic.notifications

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.quran.labs.androidquran.ui.PagerActivity
import java.time.LocalTime

/**
 * Tracks app foreground transitions via [Application.ActivityLifecycleCallbacks]
 * (no lifecycle-process dependency). On each 0->1 started-activity transition
 * (app entering foreground) it stamps last-foreground, resets the re-engagement
 * back-off counter, bumps the launch count, and records the hour-of-day (used to
 * learn the user's smart verse time).
 */
class ReadingActivityTracker(private val prefs: NotificationPrefs) : Application.ActivityLifecycleCallbacks {
  private var startedActivities = 0

  fun onEnterForeground() {
    prefs.lastForegroundAt = System.currentTimeMillis()
    prefs.consecutiveUnanswered = 0
    prefs.launchCount = prefs.launchCount + 1
    prefs.recordReadingHour(LocalTime.now().hour)
  }

  /** A genuine reading session — the user opened the reading view (PagerActivity). */
  fun onReadingSession() { prefs.readingSessions = prefs.readingSessions + 1 }

  override fun onActivityStarted(activity: Activity) {
    if (startedActivities == 0) onEnterForeground()
    startedActivities++
    // `is PagerActivity` (with the import) so a future upstream rename fails to COMPILE
    // rather than silently breaking reading detection.
    if (activity is PagerActivity) onReadingSession()
  }
  override fun onActivityStopped(activity: Activity) {
    if (startedActivities > 0) startedActivities--
  }
  // Unused lifecycle callbacks (implement explicitly; do not rely on default methods)
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
  override fun onActivityDestroyed(activity: Activity) {}
}
