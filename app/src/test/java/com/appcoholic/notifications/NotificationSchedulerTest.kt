package com.appcoholic.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class NotificationSchedulerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private lateinit var workManager: WorkManager
  private lateinit var scheduler: NotificationScheduler

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder().setExecutor(SynchronousExecutor()).build()
    )
    workManager = WorkManager.getInstance(context)
    scheduler = NotificationScheduler(context, NotificationPrefs(context))
  }

  @Test
  fun `app start keeps the pending daily verse`() {
    // WorkManager cold-starts the process to run the verse; app start must not replace that run
    scheduler.scheduleDailyVerse()
    val pending = pendingDailyVerse().single()

    scheduler.ensureScheduled()

    assertThat(pendingDailyVerse().single().id).isEqualTo(pending.id)
  }

  @Test
  fun `app start schedules the daily verse when none is pending`() {
    scheduler.ensureScheduled()

    assertThat(pendingDailyVerse()).hasSize(1)
  }

  @Test
  fun `settings changes still reschedule the pending daily verse`() {
    scheduler.scheduleDailyVerse()
    val pending = pendingDailyVerse().single()

    scheduler.scheduleAll()

    assertThat(pendingDailyVerse().single().id).isNotEqualTo(pending.id)
  }

  @Test
  fun `app start cancels the daily verse when it is turned off`() {
    scheduler.scheduleDailyVerse()
    NotificationPrefs(context).dailyVerseEnabled = false

    scheduler.ensureScheduled()

    assertThat(pendingDailyVerse()).isEmpty()
  }

  private fun pendingDailyVerse(): List<WorkInfo> =
    workManager.getWorkInfosForUniqueWork(NotificationConstants.WORK_DAILY_VERSE).get()
      .filter { !it.state.isFinished }
}
