package com.quran.labs.androidquran

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.SharedPrefHelper
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.quran.mobile.bookmark.migration.PreReleaseMobileSyncUpgrade
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import timber.log.Timber

@HasMemberInjections
open class QuranApplication : Application(), QuranApplicationComponentProvider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber
  @Inject lateinit var quranSettings: QuranSettings

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    setupTimber()
    // Rattil: must run before anything opens the mobile-sync database
    PreReleaseMobileSyncUpgrade.moveAsideIfOutdated(this)
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    initializeWorkManager()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    val sharedPrefHelper = SharedPrefHelper(this)

    val requestConfiguration = RequestConfiguration.Builder()
      .setTestDeviceIds(listOf("1A433D1C8B6C98FF184A4694E09AC80F"))
      // no mature ads (sexual content etc.) in a Quran app; matches the AdMob account setting
      .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
      .build()
    MobileAds.setRequestConfiguration(requestConfiguration)
    // MobileAds.initialize runs after consent, see AdsInitializer


    // theme setup
    val theme = quranSettings.currentTheme()
    ThemeUtil.setTheme(theme)

    com.appcoholic.ads.AppForegroundGate.register(this)
    com.appcoholic.notifications.NotificationBootstrap(this).start()
  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return createGraphFactory<ApplicationComponent.Factory>()
      .generate(this)
  }

  open fun initializeWorkManager() {
    WorkManager.initialize(
      this,
      Configuration.Builder()
        .setWorkerFactory(quranWorkerFactory)
        .build()
    )
  }
}
