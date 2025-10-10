package com.quran.labs.androidquran

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.bytedance.sdk.openadsdk.api.PAGConstant
import com.quran.labs.androidquran.core.worker.QuranWorkerFactory
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import com.quran.labs.androidquran.di.component.application.DaggerApplicationComponent
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.SharedPrefHelper
import com.quran.labs.androidquran.util.RecordingLogTree
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.labs.androidquran.widget.BookmarksWidgetSubscriber
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.ads.mediation.pangle.PangleMediationAdapter

import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import timber.log.Timber
import javax.inject.Inject

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
    applicationComponent = initializeInjector()
    applicationComponent.inject(this)
    initializeWorkManager()
    bookmarksWidgetSubscriber.subscribeBookmarksWidgetIfNecessary()
    val sharedPrefHelper = SharedPrefHelper(this)
    val storedPangleConsent = sharedPrefHelper.pangleGdprConsent
    val consentToApply =
      if (storedPangleConsent != SharedPrefHelper.CONSENT_UNSET) {
        storedPangleConsent
      } else {
        PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_DEFAULT
      }
    PangleMediationAdapter.setGDPRConsent(consentToApply)
    val requestConfiguration = RequestConfiguration.Builder()
      .setTestDeviceIds(listOf("1A433D1C8B6C98FF184A4694E09AC80F"))
      .build()
    MobileAds.setRequestConfiguration(requestConfiguration)
    MobileAds.initialize(this) { status ->
      status.adapterStatusMap.forEach { (adapter, st) ->
        Timber.d("GMA-Init: Adapter=$adapter, ${st.description}, ${st.initializationState}, ${st.latency}ms")
      }
    }


    // theme setup
    val theme = quranSettings.currentTheme()
    ThemeUtil.setTheme(theme)
  }

  open fun setupTimber() {
    Timber.plant(RecordingLogTree())
  }

  open fun initializeInjector(): ApplicationComponent {
    return DaggerApplicationComponent.factory()
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
