package com.appcoholic.notifications

import com.appcoholic.notifications.di.NotificationsTranslationModel
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first

/** The Arabic and active-translation text for a single verse. Both nullable. */
data class VerseText(val arabic: String?, val translation: String?)

/**
 * Headless loader for a single verse's Arabic + active-translation text.
 *
 * Built for background contexts (e.g. the daily-verse notification worker), so
 * it carries no UI/notification concerns and is engineered to degrade
 * gracefully: [load] MUST NEVER throw. Any DB failure — or a missing/empty
 * translation — collapses to a null field rather than an exception. Arabic and
 * translation are looked up independently, so one failing never blocks the
 * other.
 *
 * Reuses the existing injectable [TranslationModel] (headless DB reads on
 * `Dispatchers.IO`) and [TranslationsDBAdapter] (active translation catalog)
 * rather than touching raw SQLite.
 *
 * "Active translation" (v1): the first downloaded translation reported by
 * [TranslationsDBAdapter.getTranslations]. Honoring the user's selected
 * ordering (`QuranSettings.activeTranslations`) is deferred — that ordering is
 * not reachable here without editing upstream, and first-available is an
 * acceptable v1 fallback for a single-line notification.
 */
@Inject
class VerseTextLoader(
  @NotificationsTranslationModel private val translationModel: TranslationModel,
  private val translationsDBAdapter: TranslationsDBAdapter,
) {
  suspend fun load(ref: VerseRef): VerseText {
    val range = VerseRange(ref.sura, ref.ayah, ref.sura, ref.ayah, 1)
    val arabic = runCatching {
      translationModel.getArabicFromDatabase(range).firstOrNull()?.text
    }.getOrNull()
    val translation = runCatching {
      val db = activeTranslationFilename()
      if (db != null) {
        translationModel.getTranslationFromDatabase(range, db).firstOrNull()?.text
      } else {
        null
      }
    }.getOrNull()
    return VerseText(arabic, translation)
  }

  private suspend fun activeTranslationFilename(): String? =
    runCatching {
      translationsDBAdapter.getTranslations().first().firstOrNull()?.filename
    }.getOrNull()
}
