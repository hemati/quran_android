package com.appcoholic.ads

/**
 * Pure, testable ad-gating decisions for the fork's AdMob surfaces.
 *
 * The compliance-critical rule (consent must be obtained before any ad is
 * requested/shown) is enforced by the caller passing `adsAllowed` =
 * `UserMessagingPlatform.getConsentInformation(ctx).canRequestAds()`. These
 * functions additionally encode the product policy:
 *   - app-open: only on a real warm foreground (never cold start), with a cooldown
 *   - interstitial: every N sura changes, min 5 min apart, NEVER the first change
 */
object AdGatePolicy {
  const val DEFAULT_APP_OPEN_COOLDOWN_SEC = 14_400L      // 4 hours
  const val DEFAULT_INTERSTITIAL_FREQ = 4
  const val INTERSTITIAL_MIN_GAP_MS = 300_000L           // 5 minutes

  /** App-open cooldown in ms. A non-positive RC value falls back to the 4h default (NOT 24h). */
  fun appOpenCooldownMs(rcCooldownSec: Long): Long =
    (if (rcCooldownSec > 0) rcCooldownSec else DEFAULT_APP_OPEN_COOLDOWN_SEC) * 1000L

  /**
   * Whether to show the app-open ad now. Requires: not pro, consent allows ads,
   * a real warm foreground transition just happened (caller's signal — guarantees
   * "never on cold start"), and the cooldown has elapsed.
   */
  fun shouldShowAppOpen(
    isPro: Boolean,
    adsAllowed: Boolean,
    warmForeground: Boolean,
    now: Long,
    lastShown: Long,
    rcCooldownSec: Long,
  ): Boolean {
    if (isPro || !adsAllowed || !warmForeground) return false
    return now - lastShown >= appOpenCooldownMs(rcCooldownSec)
  }

  /**
   * Whether to show the sura-change interstitial now. `suraChangeCount` is the
   * post-increment count of real sura changes this session. Never fires on the
   * first change (uses max(freq, 2) as the floor), then every `freq`-th change,
   * with a 5-minute minimum gap.
   */
  fun shouldShowInterstitial(
    isPro: Boolean,
    adsAllowed: Boolean,
    freq: Int,
    suraChangeCount: Int,
    now: Long,
    lastShown: Long,
  ): Boolean {
    if (isPro || !adsAllowed || freq <= 0) return false
    if (suraChangeCount < maxOf(freq, 2)) return false   // never the first sura change of the session
    if (suraChangeCount % freq != 0) return false
    return now - lastShown >= INTERSTITIAL_MIN_GAP_MS
  }
}
