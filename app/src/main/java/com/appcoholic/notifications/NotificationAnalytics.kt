package com.appcoholic.notifications

import com.quran.analytics.AnalyticsProvider
import dev.zacsweers.metro.Inject

/**
 * Thin wrapper over the app's [AnalyticsProvider] for the fork's notification
 * events. In release builds [AnalyticsProvider] is backed by FirebaseProvider
 * (→ GA4); in debug it's a no-op, so injecting the interface is correct in both.
 *
 * IMPORTANT — param value types: FirebaseProvider.logEvent only forwards
 * `Float`/`Int`/`String` values correctly; `Long` values are silently dropped
 * (upstream bug — it calls `bundle.getLong` instead of `putLong`). So every
 * param here is `Int` or `String` only — never `Long`. This is why
 * [posted]'s delay is an `Int` count of minutes rather than a millis `Long`.
 */
@Inject
class NotificationAnalytics(private val analytics: AnalyticsProvider) {
  fun posted(type: String, delayMinutes: Int) =
    analytics.logEvent(
      "notification_posted",
      mapOf("notification_type" to type, "delay_minutes" to delayMinutes)
    )

  fun skipped(type: String, reason: String) =
    analytics.logEvent(
      "notification_skipped",
      mapOf("notification_type" to type, "reason" to reason)
    )

  fun opened(type: String) =
    analytics.logEvent(
      "notification_opened",
      mapOf("notification_type" to type, "source" to "notification")
    )
}
