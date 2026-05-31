package com.appcoholic.notifications

/**
 * Pure decision policy for the re-engagement (inactivity nudge) notification.
 *
 * No Android, no DB, no DI — every input is passed in so the worker stays a
 * thin adapter and the suppression rules are exhaustively unit-testable.
 *
 * Suppression order (first match wins):
 *  1. disabled            — user turned the feature off.
 *  2. paused              — [MAX_UNANSWERED] nudges ignored in a row; back off
 *                           until the user re-opens (which resets the counter).
 *  3. no_data             — never seen the app in foreground; nothing to nudge.
 *  4. active_recently     — used the app within [thresholdDays]; not idle yet.
 *  5. quiet_hours         — local time inside the do-not-disturb window.
 *  6. verse_posted_today  — already nudged today via the daily verse; don't
 *                           double up.
 *  7. cap_7d              — re-engaged within the last [CAP_DAYS]; one per week.
 * Otherwise → post ("ok").
 */
object ReEngagementPolicy {
  data class Decision(val shouldPost: Boolean, val reason: String)

  private const val DAY_MS = 86_400_000L
  private const val CAP_DAYS = 7
  private const val MAX_UNANSWERED = 3

  fun decide(
    enabled: Boolean,
    now: Long,                  // epoch millis
    lastForegroundAt: Long,
    lastReEngagedAt: Long,
    consecutiveUnanswered: Int,
    postedVerseToday: Boolean,
    thresholdDays: Int,
    quietStart: Int,            // hour [0..24)
    quietEnd: Int,
    zoneOffsetHours: Int,
  ): Decision {
    if (!enabled) return Decision(false, "disabled")
    if (consecutiveUnanswered >= MAX_UNANSWERED) return Decision(false, "paused")
    if (lastForegroundAt <= 0L) return Decision(false, "no_data")
    if (now - lastForegroundAt < thresholdDays * DAY_MS) return Decision(false, "active_recently")
    val localHour = Math.floorMod((now / 3_600_000L + zoneOffsetHours).toInt(), 24)
    val inQuiet = if (quietStart <= quietEnd) localHour in quietStart until quietEnd
                  else localHour >= quietStart || localHour < quietEnd
    if (inQuiet) return Decision(false, "quiet_hours")
    if (postedVerseToday) return Decision(false, "verse_posted_today")
    if (lastReEngagedAt > 0L && now - lastReEngagedAt < CAP_DAYS * DAY_MS) return Decision(false, "cap_7d")
    return Decision(true, "ok")
  }
}
