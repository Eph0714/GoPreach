package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The dashboard's hero panel — a greeting, live sync/connectivity status, and
 * a row of one-tap shortcuts into the screen's most-used destinations, all on
 * a rounded-bottom gradient panel. Inspired by a mobile finance app's
 * "Welcome, name! / balance / quick actions" dashboard pattern, adapted to
 * what GoPreach actually shows: no single numeric balance makes sense across
 * every role, so the equivalent "at a glance" facts here are sync status and
 * connectivity — the two things this offline-first app most wants a user to
 * trust at open, everywhere else content already flows from [AppBanner].
 */
@Composable
fun DashboardHero(
    greetingName: String,
    roleLabel: String?,
    isOnline: Boolean,
    pendingSyncCount: Int,
    quickActions: List<QuickAction>,
    logoContent: @Composable () -> Unit,
    topEndAction: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary),
                )
            )
            .padding(bottom = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.size(36.dp)) { logoContent() }
                topEndAction()
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Welcome, $greetingName!",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (roleLabel != null) {
                    Text(roleLabel, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusPill(
                    icon = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    label = if (isOnline) "Online" else "Offline",
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    icon = if (pendingSyncCount > 0) Icons.Rounded.CloudQueue else Icons.Rounded.CloudDone,
                    label = if (pendingSyncCount > 0) "$pendingSyncCount Pending" else "All Synced",
                    modifier = Modifier.weight(1f),
                )
            }

            if (quickActions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    quickActions.forEach { action ->
                        QuickActionButton(label = action.label, icon = action.icon, onClick = action.onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
