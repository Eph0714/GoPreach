package com.emfitsolutions.gopreach.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.emfitsolutions.gopreach.R

private const val CHANNEL_ID = "gopreach_reminders"

/**
 * Local-only notifications (no push backend yet — see BUILD_PLAN.md's
 * "Redesign the Publisher Dashboard" phase note): Monthly Report reminders
 * ([com.emfitsolutions.gopreach.data.sync.ReminderWorker], a periodic
 * WorkManager check) and "Forward to Other Congregation" alerts (fired
 * in-process — see [com.emfitsolutions.gopreach.ui.screens.pipeline
 * .ForwardRequestsViewModel] — the moment a new pending request streams in
 * while this app is running). Every call here is a no-op if the user hasn't
 * granted POST_NOTIFICATIONS (Android 13+) — never crashes, never nags twice
 * for the same permission in one call.
 */
object NotificationHelper {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Monthly report reminders and forward request alerts"
            }
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
