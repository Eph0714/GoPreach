package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The app works fully offline (spec: local writes always succeed instantly,
 * queued for later, never uploaded automatically — see spec §17 "Manual Sync
 * Requirement"). This is the small header-level "you have N changes waiting to
 * go up" status indicator (spec §16) plus a quick manual trigger.
 *
 * Shares [ManualSyncViewModel] with [SyncToServerButton] (both are `hiltViewModel()`-
 * scoped to the same screen, so Compose resolves them to the same instance) rather
 * than keeping a separate ViewModel/sync path — that way tapping this icon gets
 * the exact same connectivity check (spec §11: reject immediately if offline,
 * never silently queue) and progress/summary handling `SyncToServerButton`
 * already renders, instead of a second, divergent "sync" behavior.
 */
@Composable
fun SyncStatusButton(viewModel: ManualSyncViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    Box {
        IconButton(onClick = viewModel::syncToServer) {
            Icon(
                imageVector = if (pendingCount > 0) Icons.Rounded.CloudSync else Icons.Rounded.CloudDone,
                contentDescription = if (pendingCount > 0) "Sync now ($pendingCount pending)" else "All changes synced",
                tint = Color.White,
            )
        }
        if (pendingCount > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                containerColor = MaterialTheme.colorScheme.error,
            ) {
                Text(pendingCount.coerceAtMost(99).toString())
            }
        }
    }
}
