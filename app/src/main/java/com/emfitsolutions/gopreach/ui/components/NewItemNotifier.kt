package com.emfitsolutions.gopreach.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.repository.NotificationCategory
import com.emfitsolutions.gopreach.notifications.NotificationHelper
import com.emfitsolutions.gopreach.ui.screens.notifications.NotificationItem
import kotlinx.coroutines.flow.Flow

/**
 * "Make a default notification sound to all the incoming notification like
 * Transfer Request, Announcement, Calendar Alarm" — this is the Announcement
 * and Calendar Event half of that: fires one local notification per new item
 * that streams into [items] (same "one notification per session per new
 * arrival, not on initial load" pattern as [com.emfitsolutions.gopreach.ui
 * .screens.home.ForwardRequestNotifier] — Transfer Request notifications stay
 * on that separate, more targeted notifier so the two don't double-fire for
 * the same event). Both share [com.emfitsolutions.gopreach.notifications
 * .REMINDERS_CHANNEL_ID], so both play whichever sound the user picked in
 * Settings.
 *
 * [onlyCategories] restricts which [NotificationItem.category] values ring a
 * system notification — the unified balloon [items] already comes from also
 * includes Transfer Request/Monthly Report rows this composable should leave
 * alone.
 */
@Composable
fun NewItemNotifier(items: Flow<List<NotificationItem>>, onlyCategories: Set<NotificationCategory>) {
    val context = LocalContext.current
    val list by items.collectAsStateWithLifecycle(initialValue = null)
    var lastMaxTimestamp by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(list) {
        val current = list ?: return@LaunchedEffect
        val relevant = current.filter { it.category in onlyCategories }
        val previousMax = lastMaxTimestamp
        if (previousMax != null) {
            relevant.filter { it.timestamp > previousMax }
                .forEach { item ->
                    NotificationHelper.notify(context, id = item.hashCode(), title = item.title, text = item.subtitle, category = item.category)
                }
        }
        val currentMax = relevant.maxOfOrNull { it.timestamp }
        if (currentMax != null && (previousMax == null || currentMax > previousMax)) {
            lastMaxTimestamp = currentMax
        }
    }
}
