package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.sync.SyncStatusCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SyncStatusIndicatorViewModel @Inject constructor(
    syncStatusCenter: SyncStatusCenter,
) : ViewModel() {
    val snapshot: StateFlow<SyncStatusCenter.Snapshot> = syncStatusCenter.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatusCenter.Snapshot(SyncStatusCenter.Status.WAITING_FOR_INTERNET, 0, 0))
}

private fun SyncStatusCenter.Status.emoji(): String = when (this) {
    SyncStatusCenter.Status.SYNCED -> "🟢"
    SyncStatusCenter.Status.SYNCING -> "🟡"
    SyncStatusCenter.Status.WAITING_FOR_INTERNET -> "🔴"
    SyncStatusCenter.Status.RETRYING -> "⚠️"
    SyncStatusCenter.Status.SYNC_ERROR -> "⚠️"
}

/** Spec §7's own example wording ("✓ All changes synchronized" / "↻ 3
 * changes waiting to sync" / "⚠ Sync temporarily unavailable — retrying
 * automatically") — a permanent [SyncStatusCenter.Status.SYNC_ERROR] is the
 * one state that actually tells the Publisher something needs attention,
 * rather than "just wait, this resolves itself." */
private fun SyncStatusCenter.Snapshot.label(): String = when (status) {
    SyncStatusCenter.Status.SYNCED ->
        if (pendingCount > 0) "↻ $pendingCount change${if (pendingCount == 1) "" else "s"} waiting to sync" else "✓ All changes synchronized"
    SyncStatusCenter.Status.SYNCING -> "Syncing…"
    SyncStatusCenter.Status.WAITING_FOR_INTERNET ->
        "Offline — $pendingCount change${if (pendingCount == 1) "" else "s"} will sync automatically"
    SyncStatusCenter.Status.RETRYING -> "Sync temporarily unavailable — retrying automatically"
    SyncStatusCenter.Status.SYNC_ERROR ->
        "Sync error — $permanentFailureCount change${if (permanentFailureCount == 1) "" else "s"} need attention"
}

/**
 * Real-time connection/sync status badge — independent of
 * [ManualSyncViewModel]/[SyncToServerButton]'s own state, since those only
 * reflect a *manually*-triggered run: this reflects the sync system as a
 * whole, including the automatic background triggers
 * ([com.emfitsolutions.gopreach.data.sync.SyncScheduler]).
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusIndicatorViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(snapshot.status.emoji(), modifier = Modifier.padding(end = 6.dp))
        Text(snapshot.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
