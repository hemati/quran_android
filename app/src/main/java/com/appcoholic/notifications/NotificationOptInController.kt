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
    if (prefs.optInPrompted) return
    // Pre-13 needs no runtime permission; if already granted, nothing to ask — mark done.
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) { prefs.optInPrompted = true; return }
    if (!NotificationOptInPolicy.shouldPrompt(prefs.optInPrompted, false, prefs.launchCount)) return
    prefs.optInPrompted = true
    showRationaleThenRequest()
  }

  private fun showRationaleThenRequest() {
    if (activity.isFinishing) return
    AlertDialog.Builder(activity)
      .setTitle(R.string.notif_optin_title)
      .setMessage(R.string.notif_optin_rationale)
      .setPositiveButton(R.string.notif_optin_enable) { _, _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFICATIONS)
        }
      }
      .setNegativeButton(R.string.notif_optin_not_now, null)
      .show()
  }

  companion object { private const val REQ_POST_NOTIFICATIONS = 7301 }
}
