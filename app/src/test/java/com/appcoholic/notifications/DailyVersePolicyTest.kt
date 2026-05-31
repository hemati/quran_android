package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [DailyVersePolicy]. Pure logic — no Android, no DB, no DI.
 */
class DailyVersePolicyTest {

  @Test
  fun disabled_does_not_post() {
    val decision = DailyVersePolicy.shouldPost(
      enabled = false,
      lastPostedDate = null,
      today = "2026-05-31",
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("disabled")
  }

  @Test
  fun already_posted_today_does_not_post() {
    val decision = DailyVersePolicy.shouldPost(
      enabled = true,
      lastPostedDate = "2026-05-31",
      today = "2026-05-31",
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("already_posted")
  }

  @Test
  fun fresh_day_posts() {
    val decision = DailyVersePolicy.shouldPost(
      enabled = true,
      lastPostedDate = "2026-05-30",
      today = "2026-05-31",
    )
    assertThat(decision.shouldPost).isTrue()
    assertThat(decision.reason).isEqualTo("ok")
  }

  @Test
  fun never_posted_posts() {
    val decision = DailyVersePolicy.shouldPost(
      enabled = true,
      lastPostedDate = null,
      today = "2026-05-31",
    )
    assertThat(decision.shouldPost).isTrue()
    assertThat(decision.reason).isEqualTo("ok")
  }

  @Test
  fun disabled_takes_precedence_over_same_day() {
    val decision = DailyVersePolicy.shouldPost(
      enabled = false,
      lastPostedDate = "2026-05-31",
      today = "2026-05-31",
    )
    assertThat(decision.shouldPost).isFalse()
    assertThat(decision.reason).isEqualTo("disabled")
  }
}
