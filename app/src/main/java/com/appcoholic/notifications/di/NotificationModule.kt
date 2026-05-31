package com.appcoholic.notifications.di

import android.content.Context
import com.quran.data.di.AppScope
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.model.translation.TranslationModelImpl
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Fork-owned AppScope bindings for the notifications feature.
 *
 * Provides a [NotificationsTranslationModel]-qualified [TranslationModel] that
 * resolves at [AppScope]. Upstream's `TranslationModelImpl` is bound only at
 * `@ActivityScope`, but the daily-verse worker's factory is created in the
 * AppScope graph, so [com.appcoholic.notifications.VerseTextLoader] needs an
 * app-scoped instance. We build one directly from AppScope-resolvable deps:
 * the application [Context], [QuranFileUtils], and the flavor-bound [AyahMapper]
 * (the madani flavor binds `IdentityAyahMapper` via `QuranDataModule`).
 *
 * `TranslationModelImpl`'s constructor is `internal`, which is accessible here
 * because fork code lives in the same `:app` module. The qualifier keeps this
 * from colliding with the existing unqualified `@ActivityScope` binding.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NotificationModule {

  @Provides
  @SingleIn(AppScope::class)
  @NotificationsTranslationModel
  fun provideNotificationsTranslationModel(
    @ApplicationContext context: Context,
    quranFileUtils: QuranFileUtils,
    ayahMapper: AyahMapper,
  ): TranslationModel = TranslationModelImpl(context, quranFileUtils, ayahMapper)
}
