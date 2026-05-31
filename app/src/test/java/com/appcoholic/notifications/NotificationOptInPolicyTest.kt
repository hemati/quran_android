package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [NotificationOptInPolicy]. Pure logic — no Android, no DI.
 */
class NotificationOptInPolicyTest {

  @Test
  fun prompts_on_second_launch_when_not_prompted_and_not_granted() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInPrompted = false,
        permissionGranted = false,
        launchCount = 2,
      )
    ).isTrue()
  }

  @Test
  fun does_not_prompt_when_already_prompted() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInPrompted = true,
        permissionGranted = false,
        launchCount = 2,
      )
    ).isFalse()
  }

  @Test
  fun does_not_prompt_when_permission_already_granted() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInPrompted = false,
        permissionGranted = true,
        launchCount = 2,
      )
    ).isFalse()
  }

  @Test
  fun does_not_prompt_before_second_launch() {
    assertThat(
      NotificationOptInPolicy.shouldPrompt(
        optInPrompted = false,
        permissionGranted = false,
        launchCount = 1,
      )
    ).isFalse()
  }
}
