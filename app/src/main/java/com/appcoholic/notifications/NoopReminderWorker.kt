package com.appcoholic.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import dev.zacsweers.metro.Inject

class NoopReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result = Result.success()

  class Factory @Inject constructor() : WorkerTaskFactory {
    override fun makeWorker(appContext: Context, workerParameters: WorkerParameters): ListenableWorker =
      NoopReminderWorker(appContext, workerParameters)
  }
}
