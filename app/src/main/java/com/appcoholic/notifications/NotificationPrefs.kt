package com.appcoholic.notifications

import android.content.Context
import androidx.core.content.edit
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

class NotificationPrefs @Inject constructor(@ApplicationContext context: Context) {
  private val p = context.getSharedPreferences(NotificationConstants.PREFS_FILE, Context.MODE_PRIVATE)

  var dailyVerseEnabled: Boolean
    get() = p.getBoolean("daily_verse_enabled", true)
    set(v) = p.edit { putBoolean("daily_verse_enabled", v) }
  var reEngagementEnabled: Boolean
    get() = p.getBoolean("reengage_enabled", true)
    set(v) = p.edit { putBoolean("reengage_enabled", v) }

  /** User-chosen hour 0..23, or null = use smart/fallback. */
  var verseHourOverride: Int?
    get() = if (p.contains("verse_hour")) p.getInt("verse_hour", 9) else null
    set(v) = p.edit { if (v == null) remove("verse_hour") else putInt("verse_hour", v) }

  var lastForegroundAt: Long
    get() = p.getLong("last_fg_at", 0L)
    set(v) = p.edit { putLong("last_fg_at", v) }
  var lastReEngagedAt: Long
    get() = p.getLong("last_reengage_at", 0L)
    set(v) = p.edit { putLong("last_reengage_at", v) }
  var consecutiveUnanswered: Int
    get() = p.getInt("reengage_unanswered", 0)
    set(v) = p.edit { putInt("reengage_unanswered", v) }
  var launchCount: Int
    get() = p.getInt("launch_count", 0)
    set(v) = p.edit { putInt("launch_count", v) }
  /** Terminal flag: set only when the user enabled, declined twice, or permission already granted. */
  var optInResolved: Boolean
    get() = p.getBoolean("optin_resolved", false)
    set(v) = p.edit { putBoolean("optin_resolved", v) }
  var readingSessions: Int
    get() = p.getInt("reading_sessions", 0)
    set(v) = p.edit { putInt("reading_sessions", v) }
  var optInDeferrals: Int
    get() = p.getInt("optin_deferrals", 0)
    set(v) = p.edit { putInt("optin_deferrals", v) }

  fun lastPostedDate(type: String): String? = p.getString("last_posted_$type", null)
  fun setLastPostedDate(type: String, isoDate: String) = p.edit { putString("last_posted_$type", isoDate) }

  private fun readingHours(): List<Int> =
    (p.getString("reading_hours", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
  fun recordReadingHour(hour: Int) {
    val hours = (readingHours() + hour).takeLast(20)
    p.edit { putString("reading_hours", hours.joinToString(",")) }
  }
  fun readingSessionCount(): Int = readingHours().size
  /** Median reading hour once we have >=3 samples, else null. */
  fun smartVerseHour(): Int? {
    val hours = readingHours().sorted()
    return if (hours.size >= 3) hours[hours.size / 2] else null
  }
}
