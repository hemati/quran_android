package com.appcoholic.notifications

object NotificationOptInPolicy {
  /**
   * Engagement-gated opt-in. Ask after the user has actually read (not merely
   * opened the app), with a high launch-count fallback so returning non-readers
   * still eventually get asked. A "Not now" defers rather than kills: we re-ask
   * once after more engagement, then stop.
   */
  fun shouldPrompt(
    optInResolved: Boolean,
    permissionGranted: Boolean,
    readingSessions: Int,
    launchCount: Int,
    deferrals: Int,
  ): Boolean {
    if (optInResolved || permissionGranted) return false
    return when (deferrals) {
      0 -> readingSessions >= 1 || launchCount >= 5      // first ask: after first read (or eventual fallback)
      1 -> readingSessions >= 5 || launchCount >= 12     // one gentle re-ask after more engagement
      else -> false                                      // after two declines, stop
    }
  }
}
