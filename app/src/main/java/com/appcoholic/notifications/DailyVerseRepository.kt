package com.appcoholic.notifications

import android.content.Context
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import org.json.JSONObject

/**
 * Provides the curated list of daily-verse references from the bundled asset
 * `notifications/daily_verses.json` and a deterministic per-day selection.
 *
 * The asset holds verse *references* only (sura + ayah integers). The Arabic
 * text and translations are rendered at runtime from the app's own databases.
 */
class DailyVerseRepository @Inject constructor(@ApplicationContext private val context: Context) {
  private val verses: List<VerseRef> by lazy { load() }

  private fun load(): List<VerseRef> {
    val json = context.assets.open("notifications/daily_verses.json")
      .bufferedReader().use { it.readText() }
    val arr = JSONObject(json).getJSONArray("verses")
    return (0 until arr.length()).map {
      val o = arr.getJSONObject(it)
      VerseRef(o.getInt("sura"), o.getInt("ayah"))
    }
  }

  /** Deterministic: the same verse for everyone on a given epoch-day. */
  fun verseForDay(epochDay: Long): VerseRef = verses[Math.floorMod(epochDay, verses.size)]

  fun size(): Int = verses.size
}
