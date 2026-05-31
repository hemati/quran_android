package com.appcoholic.notifications.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.appcoholic.notifications.NotificationPrefs
import com.appcoholic.notifications.NotificationScheduler
import com.quran.labs.androidquran.R

/**
 * Minimal "Reminders" settings screen for the notifications fork.
 *
 * Every preference is bound manually to [NotificationPrefs] and marked
 * [androidx.preference.Preference.isPersistent] = false: androidx.preference would
 * otherwise auto-persist to the DEFAULT SharedPreferences file (and ListPreference
 * stores Strings), whereas [NotificationPrefs] uses its OWN file with `verse_hour`
 * stored as an Int. Persisting would cause a file/type mismatch. Initial state is
 * read from prefs here; each change writes back and re-runs
 * [NotificationScheduler.scheduleAll] (idempotent; cancels disabled reminders).
 */
class ReminderSettingsFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.reminder_preferences, rootKey)
    val ctx = requireContext().applicationContext
    val prefs = NotificationPrefs(ctx)
    val scheduler = NotificationScheduler(ctx, prefs)

    findPreference<SwitchPreferenceCompat>("daily_verse_enabled")!!.apply {
      isPersistent = false
      isChecked = prefs.dailyVerseEnabled
      setOnPreferenceChangeListener { _, v ->
        prefs.dailyVerseEnabled = v as Boolean
        scheduler.scheduleAll()
        true
      }
    }
    findPreference<ListPreference>("verse_hour")!!.apply {
      isPersistent = false
      value = (prefs.verseHourOverride ?: NotificationScheduler.DEFAULT_VERSE_HOUR).toString()
      setOnPreferenceChangeListener { _, v ->
        prefs.verseHourOverride = (v as String).toInt()
        scheduler.scheduleAll()
        true
      }
    }
    findPreference<SwitchPreferenceCompat>("reengage_enabled")!!.apply {
      isPersistent = false
      isChecked = prefs.reEngagementEnabled
      setOnPreferenceChangeListener { _, v ->
        prefs.reEngagementEnabled = v as Boolean
        scheduler.scheduleAll()
        true
      }
    }
  }
}
