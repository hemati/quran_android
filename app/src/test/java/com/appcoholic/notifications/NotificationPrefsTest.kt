package com.appcoholic.notifications

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class NotificationPrefsTest {
  private val prefs = NotificationPrefs(ApplicationProvider.getApplicationContext())

  @Test fun defaults_are_enabled_with_null_verse_hour() {
    assertThat(prefs.dailyVerseEnabled).isTrue()       // default-on
    assertThat(prefs.reEngagementEnabled).isTrue()
    assertThat(prefs.verseHourOverride).isNull()       // smart-time not yet seeded
  }

  @Test fun roundtrips_state() {
    prefs.verseHourOverride = 18
    prefs.recordReadingHour(18)
    prefs.recordReadingHour(18)
    prefs.recordReadingHour(20)
    prefs.lastForegroundAt = 123L
    assertThat(prefs.verseHourOverride).isEqualTo(18)
    assertThat(prefs.smartVerseHour()).isEqualTo(18)   // median of [18,18,20]
    assertThat(prefs.lastForegroundAt).isEqualTo(123L)
  }
}
