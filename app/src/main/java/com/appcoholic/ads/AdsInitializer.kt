package com.appcoholic.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes the Mobile Ads SDK (and its mediation adapters) once, after UMP consent.
 *
 * This used to run in Application.onCreate, so every background start (widget, workers,
 * media browser service) spun up all adapters and a WebView, which showed up as ANRs.
 */
object AdsInitializer {
  private val started = AtomicBoolean(false)

  /** Callers must have checked `ConsentInformation.canRequestAds()`. */
  fun initializeOnce(context: Context) {
    if (!started.compareAndSet(false, true)) return
    val appContext = context.applicationContext
    Thread {
      MobileAds.initialize(appContext) { status ->
        status.adapterStatusMap.forEach { (adapter, st) ->
          Timber.d("GMA-Init: Adapter=$adapter, ${st.description}, ${st.initializationState}, ${st.latency}ms")
        }
      }
    }.start()
  }
}
