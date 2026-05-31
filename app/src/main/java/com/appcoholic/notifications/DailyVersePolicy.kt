package com.appcoholic.notifications

/**
 * Pure decision logic for whether the daily-verse notification should be posted
 * on a given day. Kept free of Android/DI/IO so it's trivially unit-testable and
 * reusable by the worker (and any future caller).
 *
 * Dedupe is date-based (`today` vs the last-posted ISO date), so re-runs of the
 * worker on the same calendar day are idempotent.
 */
object DailyVersePolicy {
  data class Decision(val shouldPost: Boolean, val reason: String)

  fun shouldPost(enabled: Boolean, lastPostedDate: String?, today: String): Decision = when {
    !enabled -> Decision(false, "disabled")
    lastPostedDate == today -> Decision(false, "already_posted")
    else -> Decision(true, "ok")
  }
}
