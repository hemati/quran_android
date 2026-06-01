package com.appcoholic.notifications

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ReadingActivityTrackerTest {
  private val prefs = NotificationPrefs(ApplicationProvider.getApplicationContext())
  private val tracker = ReadingActivityTracker(prefs)
  private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

  @Test fun first_start_stamps_foreground_resets_backoff_and_bumps_launch() {
    prefs.consecutiveUnanswered = 3 // seed the back-off counter

    tracker.onActivityStarted(activity)

    assertThat(prefs.lastForegroundAt).isGreaterThan(0L)
    assertThat(prefs.launchCount).isEqualTo(1)
    assertThat(prefs.consecutiveUnanswered).isEqualTo(0) // reset on foreground
  }

  @Test fun second_start_without_stop_does_not_double_stamp() {
    tracker.onActivityStarted(activity) // 0 -> 1: foreground enter, launchCount = 1
    tracker.onActivityStarted(activity) // 1 -> 2: still foreground, no re-stamp

    assertThat(prefs.launchCount).isEqualTo(1)
  }

  @Test fun re_entering_foreground_after_full_stop_stamps_again() {
    tracker.onActivityStarted(activity) // 0 -> 1: launchCount = 1
    tracker.onActivityStarted(activity) // 1 -> 2
    tracker.onActivityStopped(activity) // 2 -> 1: still foreground
    assertThat(prefs.launchCount).isEqualTo(1)

    tracker.onActivityStopped(activity) // 1 -> 0: app backgrounded
    tracker.onActivityStarted(activity) // 0 -> 1: foreground re-enter, launchCount = 2
    assertThat(prefs.launchCount).isEqualTo(2)
  }

  // The `is PagerActivity` glue in onActivityStarted is integration-verified on device;
  // here we unit-test the extracted increment directly.
  @Test fun on_reading_session_increments_reading_sessions() {
    assertThat(prefs.readingSessions).isEqualTo(0)

    tracker.onReadingSession()
    tracker.onReadingSession()

    assertThat(prefs.readingSessions).isEqualTo(2)
  }
}
