package com.appcoholic.notifications.di

import dev.zacsweers.metro.Qualifier

/**
 * Fork-owned qualifier for an [com.quran.labs.androidquran.model.translation.TranslationModel]
 * that resolves at [com.quran.data.di.AppScope].
 *
 * Upstream binds the unqualified `TranslationModel` only at `@ActivityScope`
 * (via `TranslationModelImpl`), which is not resolvable where the daily-verse
 * worker's factory lives (AppScope). This qualifier lets the fork provide a
 * second, app-scoped instance without colliding with the activity binding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NotificationsTranslationModel
