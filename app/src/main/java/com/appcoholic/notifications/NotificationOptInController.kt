package com.appcoholic.notifications

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R

class NotificationOptInController(
  private val activity: Activity,
  private val prefs: NotificationPrefs = NotificationPrefs(activity),
) {
  fun onAppLaunched() {
    // Pre-13 needs no runtime permission; if already granted, nothing to ask — terminal.
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
      if (!prefs.optInResolved) prefs.optInResolved = true
      return
    }
    if (prefs.optInResolved) return
    if (!NotificationOptInPolicy.shouldPrompt(
        prefs.optInResolved, false, prefs.readingSessions, prefs.launchCount, prefs.optInDeferrals)
    ) return
    showRationaleThenRequest()
  }

  private fun showRationaleThenRequest() {
    if (activity.isFinishing) return
    var handled = false
    AlertDialog.Builder(activity)
      .setTitle(R.string.notif_optin_title)
      .setMessage(R.string.notif_optin_rationale)
      .setPositiveButton(R.string.notif_optin_enable) { _, _ ->
        handled = true
        prefs.optInResolved = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        }
      }
      .setNegativeButton(R.string.notif_optin_not_now) { _, _ -> handled = true; defer() }
      .setOnDismissListener { if (!handled) defer() }   // back / outside-tap / navigate-away
      .show()
  }

  private fun defer() {
    prefs.optInDeferrals = prefs.optInDeferrals + 1
    if (prefs.optInDeferrals >= 2) prefs.optInResolved = true
  }

  companion object { private const val REQ_POST_NOTIFICATIONS = 7301 }
}
