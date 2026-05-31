package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [ReEngagementPolicy]. Pure logic — no Android, no DB, no DI.
 *
 * The base scenario (#1) fires: enabled, idle 4 days past a 3-day threshold,
 * at 10:00 UTC (outside the 22..7 quiet window), no verse posted today, never
 * re-engaged, and zero unanswered. Each later case mutates exactly one input so
 * the suppression branch it names is the one actually hit.
 */
class ReEngagementPolicyTest {

  private val DAY = 86_400_000L
  private val HOUR = 3_600_000L

  @Test
  fun fires_after_3_days_idle() {
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = 100 * DAY + 10 * HOUR,
      lastForegroundAt = 96 * DAY,
      lastReEngagedAt = 0L,
      consecutiveUnanswered = 0,
      postedVerseToday = false,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isTrue()
    assertThat(decision.reason).isEqualTo("ok")
  }

  @Test
  fun suppressed_during_quiet_hours() {
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = 100 * DAY + 23 * HOUR,
      lastForegroundAt = 96 * DAY,
      lastReEngagedAt = 0L,
      consecutiveUnanswered = 0,
      postedVerseToday = false,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("quiet_hours")
  }

  @Test
  fun suppressed_when_verse_posted_today() {
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = 100 * DAY + 10 * HOUR,
      lastForegroundAt = 96 * DAY,
      lastReEngagedAt = 0L,
      consecutiveUnanswered = 0,
      postedVerseToday = true,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("verse_posted_today")
  }

  @Test
  fun capped_to_once_per_7_days() {
    val now = 100 * DAY + 10 * HOUR
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = now,
      lastForegroundAt = 96 * DAY,
      lastReEngagedAt = now - 2 * DAY,
      consecutiveUnanswered = 0,
      postedVerseToday = false,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("cap_7d")
  }

  @Test
  fun pauses_after_3_unanswered() {
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = 100 * DAY + 10 * HOUR,
      lastForegroundAt = 96 * DAY,
      lastReEngagedAt = 0L,
      consecutiveUnanswered = 3,
      postedVerseToday = false,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("paused")
  }

  @Test
  fun not_idle_long_enough() {
    val now = 100 * DAY + 10 * HOUR
    val decision = ReEngagementPolicy.decide(
      enabled = true,
      now = now,
      lastForegroundAt = now - 1 * DAY,
      lastReEngagedAt = 0L,
      consecutiveUnanswered = 0,
      postedVerseToday = false,
      thresholdDays = 3,
      quietStart = 22,
      quietEnd = 7,
      zoneOffsetHours = 0,
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("active_recently")
  }
}
