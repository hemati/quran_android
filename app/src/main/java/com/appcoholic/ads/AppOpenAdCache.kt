package com.appcoholic.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import timber.log.Timber

/**
 * One preloaded app-open ad for the process, following Google's app-open rules: load ahead of
 * time (after consent and whenever the reader goes to the background), show only an ad that is
 * already loaded when the app comes back, and never one older than 4 hours. Loading on return and
 * showing it whenever it arrived could put the ad over the page seconds later, which counts as an
 * unexpected ad. Main thread only.
 */
object AppOpenAdCache {
  private var ad: AppOpenAd? = null
  private var loadedAt = 0L
  private var loading = false

  /** When an app-open ad was last shown, for the cooldown (process-wide, not per activity). */
  var lastShownAt = 0L
    private set

  fun preload(context: Context, adUnitId: String) {
    val now = System.currentTimeMillis()
    if (loading || (ad != null && AdGatePolicy.isAppOpenAdFresh(loadedAt, now))) return
    loading = true
    AppOpenAd.load(
      context.applicationContext,
      adUnitId,
      AdRequest.Builder().build(),
      object : AppOpenAd.AppOpenAdLoadCallback() {
        override fun onAdLoaded(loadedAd: AppOpenAd) {
          ad = loadedAd
          loadedAt = System.currentTimeMillis()
          loading = false
        }

        override fun onAdFailedToLoad(error: LoadAdError) {
          loading = false
          Timber.w("App-open ad failed to load: %s", error.message)
        }
      }
    )
  }

  /** Shows the preloaded ad if there is a fresh one; returns whether it was shown. */
  fun showIfReady(activity: Activity): Boolean {
    val readyAd = ad ?: return false
    ad = null
    if (!AdGatePolicy.isAppOpenAdFresh(loadedAt, System.currentTimeMillis())) return false
    readyAd.show(activity)
    lastShownAt = System.currentTimeMillis()
    return true
  }
}
