package com.emfitsolutions.gopreach

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.emfitsolutions.gopreach.data.sync.ReminderScheduler
import com.emfitsolutions.gopreach.data.sync.RemoteSyncCoordinator
import com.emfitsolutions.gopreach.notifications.CalendarAlarmRescheduler
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt root for DI across the app.
 *
 * Also configures WorkManager with [HiltWorkerFactory] so the offline sync queue
 * (Phase 1 — SyncWorker) can have its dependencies injected, and starts every
 * repository's Firestore listener via [RemoteSyncCoordinator] so the offline
 * cache reflects data created anywhere, not just on this device.
 */
@HiltAndroidApp
class GoPreachApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var remoteSyncCoordinator: RemoteSyncCoordinator

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var calendarAlarmRescheduler: CalendarAlarmRescheduler

    override fun onCreate() {
        super.onCreate()
        remoteSyncCoordinator.startAll()
        NotificationHelper.ensureChannel(this)
        reminderScheduler.ensureScheduled()
        calendarAlarmRescheduler.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
