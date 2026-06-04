package com.appcoholic.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdGatePolicyTest {
  private val FOUR_H_MS = 14_400L * 1000L
  private val FIVE_MIN = 300_000L

  // ---- app-open cooldown ----
  @Test fun cooldown_uses_rc_value_when_positive() {
    assertThat(AdGatePolicy.appOpenCooldownMs(7200)).isEqualTo(7200L * 1000L)
  }

  @Test fun cooldown_falls_back_to_4h_when_unset_or_zero() {
    assertThat(AdGatePolicy.appOpenCooldownMs(0)).isEqualTo(FOUR_H_MS)
    assertThat(AdGatePolicy.appOpenCooldownMs(-1)).isEqualTo(FOUR_H_MS)
  }

  // ---- app-open ----
  @Test fun appOpen_blocked_on_cold_start_no_warm_foreground() {
    // warmForeground=false models a cold start -> never show
    assertThat(
      AdGatePolicy.shouldShowAppOpen(
        isPro = false, adsAllowed = true, warmForeground = false,
        now = 10 * FOUR_H_MS, lastShown = 0, rcCooldownSec = 0
      )
    ).isFalse()
  }

  @Test fun appOpen_blocked_when_consent_not_allowed() {
    assertThat(
      AdGatePolicy.shouldShowAppOpen(false, adsAllowed = false, warmForeground = true, now = 10 * FOUR_H_MS, lastShown = 0, rcCooldownSec = 0)
    ).isFalse()
  }

  @Test fun appOpen_blocked_for_pro() {
    assertThat(
      AdGatePolicy.shouldShowAppOpen(isPro = true, adsAllowed = true, warmForeground = true, now = 10 * FOUR_H_MS, lastShown = 0, rcCooldownSec = 0)
    ).isFalse()
  }

  @Test fun appOpen_shows_on_warm_foreground_after_cooldown() {
    assertThat(
      AdGatePolicy.shouldShowAppOpen(false, true, warmForeground = true, now = FOUR_H_MS, lastShown = 0, rcCooldownSec = 0)
    ).isTrue()
  }

  @Test fun appOpen_blocked_within_cooldown() {
    assertThat(
      AdGatePolicy.shouldShowAppOpen(false, true, warmForeground = true, now = FOUR_H_MS - 1, lastShown = 0, rcCooldownSec = 0)
    ).isFalse()
  }

  // ---- interstitial ----
  @Test fun interstitial_never_on_first_sura_changes() {
    for (count in 1..3) {
      assertThat(
        AdGatePolicy.shouldShowInterstitial(false, true, freq = 4, suraChangeCount = count, now = 10 * FIVE_MIN, lastShown = 0)
      ).isFalse()
    }
  }

  @Test fun interstitial_fires_every_freq_after_gap() {
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, 4, suraChangeCount = 4, now = 10 * FIVE_MIN, lastShown = 0)).isTrue()
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, 4, suraChangeCount = 8, now = 10 * FIVE_MIN, lastShown = 0)).isTrue()
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, 4, suraChangeCount = 5, now = 10 * FIVE_MIN, lastShown = 0)).isFalse()
  }

  @Test fun interstitial_respects_5min_gap() {
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, 4, suraChangeCount = 4, now = FIVE_MIN - 1, lastShown = 0)).isFalse()
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, 4, suraChangeCount = 4, now = FIVE_MIN, lastShown = 0)).isTrue()
  }

  @Test fun interstitial_blocked_when_consent_not_allowed_or_disabled_or_pro() {
    assertThat(AdGatePolicy.shouldShowInterstitial(false, adsAllowed = false, freq = 4, suraChangeCount = 4, now = 10 * FIVE_MIN, lastShown = 0)).isFalse()
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, freq = 0, suraChangeCount = 4, now = 10 * FIVE_MIN, lastShown = 0)).isFalse()
    assertThat(AdGatePolicy.shouldShowInterstitial(isPro = true, adsAllowed = true, freq = 4, suraChangeCount = 4, now = 10 * FIVE_MIN, lastShown = 0)).isFalse()
  }

  @Test fun interstitial_freq1_still_skips_the_very_first_change() {
    // even an aggressive RC freq=1 must not fire on the first sura change of the session
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, freq = 1, suraChangeCount = 1, now = 10 * FIVE_MIN, lastShown = 0)).isFalse()
    assertThat(AdGatePolicy.shouldShowInterstitial(false, true, freq = 1, suraChangeCount = 2, now = 10 * FIVE_MIN, lastShown = 0)).isTrue()
  }
}
