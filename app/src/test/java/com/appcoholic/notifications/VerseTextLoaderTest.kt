package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for [VerseTextLoader].
 *
 * Plain `runTest` with hand-rolled fakes — no Robolectric, no real DB. The
 * loader's contract is: it MUST NEVER throw and degrades gracefully to nulls.
 */
class VerseTextLoaderTest {

  /** Configurable fake of [TranslationModel] backed by in-memory verse lists. */
  private class FakeTranslationModel(
    private val arabic: List<QuranText> = emptyList(),
    private val translation: List<QuranText> = emptyList(),
    private val arabicThrows: Boolean = false,
    private val translationThrows: Boolean = false,
  ) : TranslationModel {
    override suspend fun getArabicFromDatabase(verses: VerseRange): List<QuranText> {
      if (arabicThrows) throw RuntimeException("arabic db boom")
      return arabic
    }

    override suspend fun getTranslationFromDatabase(verses: VerseRange, db: String): List<QuranText> {
      if (translationThrows) throw RuntimeException("translation db boom")
      return translation
    }
  }

  private fun dbAdapter(filename: String?) = FakeTranslationsDBAdapter().apply {
    if (filename != null) {
      setTranslations(listOf(LocalTranslation(id = 1, filename = filename, name = "Test")))
    }
  }

  private val ref = VerseRef(sura = 2, ayah = 255)

  @Test
  fun returns_arabic_and_translation_when_both_present() = runTest {
    val loader = VerseTextLoader(
      translationModel = FakeTranslationModel(
        arabic = listOf(QuranText(2, 255, "نص", null)),
        translation = listOf(QuranText(2, 255, "the translation", null)),
      ),
      translationsDBAdapter = dbAdapter("en.test.db"),
    )

    val result = loader.load(ref)

    assertThat(result.arabic).isEqualTo("نص")
    assertThat(result.translation).isEqualTo("the translation")
  }

  @Test
  fun translation_null_when_no_downloaded_translation() = runTest {
    // No translation in the DB adapter -> no active filename -> translation null,
    // but Arabic is still returned (graceful fallback).
    val loader = VerseTextLoader(
      translationModel = FakeTranslationModel(
        arabic = listOf(QuranText(2, 255, "نص", null)),
        translation = listOf(QuranText(2, 255, "should not be used", null)),
      ),
      translationsDBAdapter = dbAdapter(null),
    )

    val result = loader.load(ref)

    assertThat(result.arabic).isEqualTo("نص")
    assertThat(result.translation).isNull()
  }

  @Test
  fun translation_null_when_translation_db_returns_empty() = runTest {
    // A translation is "downloaded" (filename present) but the DB has no row
    // for this verse -> translation null, Arabic still returned.
    val loader = VerseTextLoader(
      translationModel = FakeTranslationModel(
        arabic = listOf(QuranText(2, 255, "نص", null)),
        translation = emptyList(),
      ),
      translationsDBAdapter = dbAdapter("en.test.db"),
    )

    val result = loader.load(ref)

    assertThat(result.arabic).isEqualTo("نص")
    assertThat(result.translation).isNull()
  }

  @Test
  fun does_not_throw_when_arabic_lookup_throws() = runTest {
    // Never-throws guarantee: a DB failure on the Arabic side degrades to null
    // rather than propagating. Translation can still be returned independently.
    val loader = VerseTextLoader(
      translationModel = FakeTranslationModel(
        arabicThrows = true,
        translation = listOf(QuranText(2, 255, "the translation", null)),
      ),
      translationsDBAdapter = dbAdapter("en.test.db"),
    )

    val result = loader.load(ref)

    assertThat(result.arabic).isNull()
    assertThat(result.translation).isEqualTo("the translation")
  }

  @Test
  fun does_not_throw_when_translation_lookup_throws() = runTest {
    val loader = VerseTextLoader(
      translationModel = FakeTranslationModel(
        arabic = listOf(QuranText(2, 255, "نص", null)),
        translationThrows = true,
      ),
      translationsDBAdapter = dbAdapter("en.test.db"),
    )

    val result = loader.load(ref)

    assertThat(result.arabic).isEqualTo("نص")
    assertThat(result.translation).isNull()
  }
}
