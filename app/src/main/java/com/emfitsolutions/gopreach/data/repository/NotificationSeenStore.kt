package com.emfitsolutions.gopreach.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "gopreach_notification_seen"

/** The four kinds of event the unified notification balloon (see
 * [com.emfitsolutions.gopreach.ui.screens.notifications.NotificationCenterViewModel])
 * surfaces. [ANNOUNCEMENT] deliberately isn't tracked by [NotificationSeenStore]
 * itself — it reuses the pre-existing [AnnouncementSeenStore] so the
 * standalone Announcement bell (see PublisherWelcomeHeader) and this unified
 * balloon never disagree about what's already been seen. */
enum class NotificationCategory { TRANSFER_REQUEST, MONTHLY_REPORT, ANNOUNCEMENT, CALENDAR_SCHEDULE }

/**
 * Unified notification balloon — per-device, per-signed-in-Person "last seen"
 * timestamp for [NotificationCategory.TRANSFER_REQUEST]/[NotificationCategory
 * .MONTHLY_REPORT]/[NotificationCategory.CALENDAR_SCHEDULE] (used only to
 * compute the bell's unseen badge count). Same local-only reasoning as
 * [AnnouncementSeenStore]'s doc comment — it never needs to sync across
 * devices or be visible to anyone else.
 */
@Singleton
class NotificationSeenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lastSeenAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastSeenAt: StateFlow<Map<String, Long>> = _lastSeenAt

    private fun key(category: NotificationCategory, personId: String) = "${category.name}:$personId"

    fun lastSeenAt(category: NotificationCategory, personId: String): Long {
        val k = key(category, personId)
        return _lastSeenAt.value[k] ?: prefs.getLong(k, 0L)
    }

    fun markSeenNow(category: NotificationCategory, personId: String) {
        val k = key(category, personId)
        val now = System.currentTimeMillis()
        prefs.edit { putLong(k, now) }
        _lastSeenAt.value = _lastSeenAt.value + (k to now)
    }
}
