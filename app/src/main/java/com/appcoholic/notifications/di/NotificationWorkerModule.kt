package com.appcoholic.notifications.di

import com.appcoholic.notifications.NoopReminderWorker
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.core.worker.WorkerKey
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap

@BindingContainer
@ContributesTo(AppScope::class)
interface NotificationWorkerModule {
  @Binds
  @IntoMap
  @WorkerKey(NoopReminderWorker::class)
  fun bindNoopReminderWorkerFactory(factory: NoopReminderWorker.Factory): WorkerTaskFactory
}
