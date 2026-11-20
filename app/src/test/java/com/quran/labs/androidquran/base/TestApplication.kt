package com.quran.labs.androidquran.base

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.di.component.application.ApplicationComponent
import dev.zacsweers.metro.createGraphFactory

class TestApplication : QuranApplication() {

  override fun onCreate() {
    // Rattil: activities read Firebase Remote Config, and Robolectric doesn't run Firebase's
    // init provider, so give the tests a default app (no network: nothing is fetched here)
    if (FirebaseApp.getApps(this).isEmpty()) {
      FirebaseApp.initializeApp(
        this,
        FirebaseOptions.Builder()
          .setApplicationId("1:000000000000:android:0000000000000000")
          .setProjectId("rattil-test")
          .setApiKey("test-api-key")
          .build()
      )
    }
    super.onCreate()
  }

  override fun initializeInjector(): ApplicationComponent {
    return createGraphFactory<ApplicationComponent.Factory>()
      .generate(this)
  }

  override fun setupTimber() {
  }

  override fun initializeWorkManager() {
  }
}
