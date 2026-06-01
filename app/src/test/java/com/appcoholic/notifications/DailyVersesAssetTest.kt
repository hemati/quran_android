package com.appcoholic.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Asset-validation guard for `notifications/daily_verses.json`.
 *
 * Every curated reference MUST be in range against the app's authoritative
 * [QuranInfo] data. This test fails the build if any reference is invalid,
 * out of range, or duplicated — which is exactly its purpose: it guarantees
 * no invented / mistyped verse reference can ship.
 */
@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class DailyVersesAssetTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val quranInfo = QuranInfo(MadaniDataSource())

  @Test
  fun every_reference_is_in_range_against_quran_info() {
    val repo = DailyVerseRepository(context)
    val verses = (0 until repo.size()).map { repo.verseForDay(it.toLong()) }

    assertThat(verses).isNotEmpty()
    // Expected curated band: at least 100, comfortably below the whole Quran.
    assertThat(verses.size).isAtLeast(100)
    assertThat(verses.size).isAtMost(130)

    for (verse in verses) {
      assertThat(verse.sura).isIn(1..114)
      assertThat(verse.ayah).isAtLeast(1)
      assertThat(verse.ayah).isAtMost(quranInfo.getNumberOfAyahs(verse.sura))
    }

    // No duplicate (sura, ayah) pairs.
    val distinct = verses.map { it.sura to it.ayah }.toSet()
    assertThat(distinct).hasSize(verses.size)
  }

  @Test
  fun references_are_spread_across_many_suras() {
    val repo = DailyVerseRepository(context)
    val suras = (0 until repo.size()).map { repo.verseForDay(it.toLong()).sura }.toSet()
    // Guard against over-concentration in a handful of suras.
    assertThat(suras.size).isAtLeast(40)
  }
}
