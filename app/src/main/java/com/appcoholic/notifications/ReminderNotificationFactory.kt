package com.appcoholic.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.labs.androidquran.util.NotificationChannelUtil
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject

/**
 * Builds and posts the fork's reminder notifications (daily verse + reading
 * re-engagement). All notification concerns live here so the worker tasks stay
 * thin and so the upstream-merge surface is confined to com.appcoholic.
 *
 * Deep links go through [QuranDataActivity.openPageIntent] (the data-ensuring
 * launcher) rather than PagerActivity directly, so a tap can't crash on a
 * partially-provisioned app. All PendingIntents are immutable.
 */
@Inject
class ReminderNotificationFactory(
  @ApplicationContext private val context: Context,
  private val quranInfo: QuranInfo,
) {

  /**
   * Idempotently creates both notification channels. Safe to call before every
   * post; [NotificationChannelUtil.setupNotificationChannel] no-ops if the
   * channel already exists and is itself guarded for API < 26.
   */
  fun ensureChannels() {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    NotificationChannelUtil.setupNotificationChannel(
      nm,
      NotificationConstants.CHANNEL_DAILY_VERSE,
      context.getString(R.string.notif_channel_daily_verse)
    )
    NotificationChannelUtil.setupNotificationChannel(
      nm,
      NotificationConstants.CHANNEL_READING_REMINDERS,
      context.getString(R.string.notif_channel_reading_reminders)
    )
  }

  /**
   * Daily-verse notification. Tapping it deep-links to the page containing
   * [ref], highlighting the exact sura+ayah and jumping to the translation
   * view. The body shows Arabic + translation (whichever are available),
   * falling back to generic copy if both are blank.
   */
  fun buildDailyVerse(ref: VerseRef, text: VerseText): Notification {
    val page = quranInfo.getPageFromSuraAyah(ref.sura, ref.ayah)
    val pending = openPagePendingIntent(
      requestCode = NotificationConstants.REQ_OPEN_VERSE,
      page = page,
      sura = ref.sura,
      ayah = ref.ayah,
      jumpToTranslation = true,
      type = TYPE_DAILY_VERSE
    )
    val body = listOfNotNull(text.arabic, text.translation)
      .joinToString("\n\n")
      .ifBlank { context.getString(R.string.notif_daily_verse_fallback) }
    val contentText = text.translation ?: text.arabic
      ?: context.getString(R.string.notif_daily_verse_fallback)
    return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_DAILY_VERSE)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.notif_daily_verse_title))
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setAutoCancel(true)
      .setContentIntent(pending)
      .build()
  }

  /**
   * Re-engagement ("continue reading") notification.
   *
   * - [suraName]: precomputed display name of the last-read sura (worker's job).
   *   Null => generic copy.
   * - [page]: the user's last-read page (worker's job). When provided, the tap
   *   deep-links straight to that page; when null, it deep-links to the app's
   *   "jump to latest" entry via [QuranDataActivity], which resolves the latest
   *   position itself — so this never crashes even with no reading history.
   *
   * Marked [NotificationCompat.VISIBILITY_PRIVATE] so the reading position
   * isn't leaked on the lock screen.
   */
  fun buildReEngagement(suraName: String?, page: Int? = null): Notification {
    val pending = if (page != null) {
      openPagePendingIntent(
        requestCode = NotificationConstants.REQ_OPEN_REENGAGE,
        page = page,
        type = TYPE_REENGAGE
      )
    } else {
      jumpToLatestPendingIntent(NotificationConstants.REQ_OPEN_REENGAGE)
    }
    val contentText = if (suraName != null) {
      context.getString(R.string.notif_reengage_personalized, suraName)
    } else {
      context.getString(R.string.notif_reengage_generic)
    }
    return NotificationCompat.Builder(context, NotificationConstants.CHANNEL_READING_REMINDERS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.notif_reengage_title))
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(pending)
      .build()
  }

  /**
   * Posts [notification] under [id], but only when the user has notifications
   * enabled. Swallows the [SecurityException] that can race in if the POST
   * permission is revoked between the check and the [NotificationManagerCompat.notify].
   */
  fun post(id: Int, notification: Notification) {
    val mgr = NotificationManagerCompat.from(context)
    if (mgr.areNotificationsEnabled()) {
      try {
        mgr.notify(id, notification)
      } catch (e: SecurityException) {
        // POST_NOTIFICATIONS revoked between the guard and notify(); ignore.
      }
    }
  }

  private fun openPagePendingIntent(
    requestCode: Int,
    page: Int,
    sura: Int? = null,
    ayah: Int? = null,
    jumpToTranslation: Boolean = false,
    type: String,
  ): PendingIntent {
    val intent = QuranDataActivity.openPageIntent(context, page, sura, ayah, jumpToTranslation)
      .putExtra(EXTRA_NOTIF_TYPE, type)
    return pendingActivity(requestCode, intent)
  }

  private fun jumpToLatestPendingIntent(requestCode: Int): PendingIntent {
    val intent = Intent(context, QuranDataActivity::class.java).apply {
      action = ShortcutsActivity.ACTION_JUMP_TO_LATEST
      putExtra(EXTRA_NOTIF_TYPE, TYPE_REENGAGE)
    }
    return pendingActivity(requestCode, intent)
  }

  private fun pendingActivity(requestCode: Int, intent: Intent): PendingIntent =
    PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

  companion object {
    /** Read by analytics in a later task; harmless to upstream. */
    const val EXTRA_NOTIF_TYPE = "appcoholic_notif_type"
    const val TYPE_DAILY_VERSE = "daily_verse"
    const val TYPE_REENGAGE = "reengage"
  }
}
