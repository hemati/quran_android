package com.appcoholic.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Pure foreground-transition counter (no Android types) so it is unit-testable.
 *
 * Tracks started activities at the process level. A "warm foreground" is a
 * background(0)->foreground(1) transition that is NOT the very first one (the
 * cold start). This is what gates the app-open ad so it never shows on cold
 * start and only after the user truly left and returned to the app.
 */
class ForegroundCounter {
  private var started = 0
  private var sawFirstForeground = false
  var pendingWarmForeground = false
    private set

  fun onStart() {
    if (started == 0) {
      if (sawFirstForeground) pendingWarmForeground = true
      sawFirstForeground = true
    }
    started++
  }

  fun onStop() {
    if (started > 0) started--
  }

  /** Returns true once after a real warm foreground, then clears the flag. */
  fun consumeWarmForeground(): Boolean {
    val pending = pendingWarmForeground
    pendingWarmForeground = false
    return pending
  }
}

/**
 * Process-level app-foreground gate for the app-open ad. Registered once from
 * [Application.onCreate]; the reading screen consults [consumeWarmForeground] in
 * onResume to decide whether the app was genuinely re-entered from background.
 */
object AppForegroundGate : Application.ActivityLifecycleCallbacks {
  private val counter = ForegroundCounter()

  fun register(application: Application) {
    application.registerActivityLifecycleCallbacks(this)
  }

  fun consumeWarmForeground(): Boolean = counter.consumeWarmForeground()

  override fun onActivityStarted(activity: Activity) { counter.onStart() }
  override fun onActivityStopped(activity: Activity) { counter.onStop() }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
  override fun onActivityDestroyed(activity: Activity) {}
}
