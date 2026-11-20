package com.quran.labs.androidquran

import android.app.Application
import androidx.core.os.UserManagerCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.appcoholic.ui.RattilAccent
import com.quran.labs.androidquran.common.ui.core.QuranAccent
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.SharedPrefHelper
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.sync.QuranSyncManager
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import timber.log.Timber

@HasMemberInjections
open class QuranApplication : Application(), QuranApplicationComponentProvider, Configuration.Provider {
  lateinit var applicationComponent: ApplicationComponent

  @Inject lateinit var quranWorkerFactory: QuranWorkerFactory
  @Inject lateinit var bookmarksWidgetSubscriber: BookmarksWidgetSubscriber
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var quranSyncManager: QuranSyncManager

  override fun provideQuranApplicationComponent(): QuranApplicationComponent {
    return applicationComponent
  }

  override fun onCreate() {
    super.onCreate()
    // Rattil: green accent for the Compose screens too (upstream defaults to navy/gold)
    QuranAccent.current = RattilAccent
    setupTimber()
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    quranSyncManager.startForegroundSync()
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

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(quranWorkerFactory)
      .build()

  open fun initializeWorkManager() {
    if (UserManagerCompat.isUserUnlocked(this)) {
      WorkManager.initialize(this, workManagerConfiguration)
    }
  }
}
