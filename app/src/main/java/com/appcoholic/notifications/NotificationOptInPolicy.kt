package com.appcoholic.notifications

object NotificationOptInPolicy {
  /** Prompt only once, only when the perm isn't already granted, and not before the 2nd launch. */
  fun shouldPrompt(optInPrompted: Boolean, permissionGranted: Boolean, launchCount: Int): Boolean =
    !optInPrompted && !permissionGranted && launchCount >= 2
}
