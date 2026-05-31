package com.appcoholic.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class DailyVerseRepositoryTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun selection_is_deterministic_and_in_range() {
    val repo = DailyVerseRepository(context)
    assertThat(repo.size()).isGreaterThan(0)
    assertThat(repo.verseForDay(10)).isEqualTo(repo.verseForDay(10 + repo.size().toLong()))
  }

  @Test
  fun verse_for_day_cycles_through_the_whole_list() {
    val repo = DailyVerseRepository(context)
    val cycle = (0 until repo.size()).map { repo.verseForDay(it.toLong()) }
    assertThat(cycle.toSet()).hasSize(repo.size())
  }
}
