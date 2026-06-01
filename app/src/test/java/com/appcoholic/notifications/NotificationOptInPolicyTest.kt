package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [NotificationOptInPolicy]. Pure logic — no Android, no DI.
 *
 * Engagement-gated opt-in: ask after the user has actually read (or via a high
 * launch-count fallback), with one gentle re-ask after a "Not now", then stop.
 */
class NotificationOptInPolicyTest {

  @Test
  fun first_ask_after_reading() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 1,
        launchCount = 1,
        deferrals = 0,
      )
    ).isTrue()
  }

  @Test
  fun fallback_by_launches_when_no_reading_yet() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 0,
        launchCount = 5,
        deferrals = 0,
      )
    ).isTrue()
  }

  /** The fix: do NOT prompt on the 2nd launch before the user has read anything. */
  @Test
  fun does_not_prompt_too_early() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 0,
        launchCount = 2,
        deferrals = 0,
      )
    ).isFalse()
  }

  @Test
  fun does_not_prompt_when_permission_already_granted() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = true,
        readingSessions = 99,
        launchCount = 99,
        deferrals = 0,
      )
    ).isFalse()
  }

  @Test
  fun does_not_prompt_when_already_resolved() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = true,
        permissionGranted = false,
        readingSessions = 99,
        launchCount = 99,
        deferrals = 0,
      )
    ).isFalse()
  }

  @Test
  fun after_one_deferral_not_enough_engagement_stays_quiet() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 1,
        launchCount = 3,
        deferrals = 1,
      )
    ).isFalse()
  }

  @Test
  fun after_one_deferral_re_ask_once_more_engagement() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 5,
        launchCount = 1,
        deferrals = 1,
      )
    ).isTrue()
  }

  @Test
  fun after_two_deferrals_stop_forever() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInResolved = false,
        permissionGranted = false,
        readingSessions = 99,
        launchCount = 99,
        deferrals = 2,
      )
    ).isFalse()
  }
}
