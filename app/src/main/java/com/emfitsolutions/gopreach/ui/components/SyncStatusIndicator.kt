package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.local.PendingSyncOperationEntity
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.SyncScheduler
import com.emfitsolutions.gopreach.data.sync.SyncStatusCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncStatusIndicatorViewModel @Inject constructor(
    syncStatusCenter: SyncStatusCenter,
    private val offlineFirestoreRepository: OfflineFirestoreRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    val snapshot: StateFlow<SyncStatusCenter.Snapshot> = syncStatusCenter.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatusCenter.Snapshot(SyncStatusCenter.Status.WAITING_FOR_INTERNET, 0, 0))

    /** Bug fix — "Sync Error" used to be a dead end with nothing to actually
     * look at; this is the list the recovery dialog below shows. */
    val permanentFailures: StateFlow<List<PendingSyncOperationEntity>> = offlineFirestoreRepository.observePermanentSyncFailures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retry(operation: PendingSyncOperationEntity) {
        viewModelScope.launch {
            offlineFirestoreRepository.retryPermanentSyncFailure(operation)
            // Same trigger a fresh write already gets — no reason to make the
            // Publisher separately open "Sync to Server" after choosing Retry.
            syncScheduler.triggerSyncIfOnline()
        }
    }

    fun discard(operation: PendingSyncOperationEntity) {
        viewModelScope.launch { offlineFirestoreRepository.discardPermanentSyncFailure(operation) }
    }
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
        "Sync error — $permanentFailureCount change${if (permanentFailureCount == 1) "" else "s"} need attention (tap for details)"
}

/**
 * Real-time connection/sync status badge — independent of
 * [ManualSyncViewModel]/[SyncToServerButton]'s own state, since those only
 * reflect a *manually*-triggered run: this reflects the sync system as a
 * whole, including the automatic background triggers
 * ([com.emfitsolutions.gopreach.data.sync.SyncScheduler]).
 *
 * Bug fix ("Sync Error" was a dead end) — tapping the badge while it's
 * showing [SyncStatusCenter.Status.SYNC_ERROR] now opens a dialog listing
 * exactly which record(s) failed permanently and why, with a **Retry**
 * (puts it back in the normal, automatic retry queue — e.g. the underlying
 * permission/data problem has since been fixed) or **Discard** (gives up on
 * this one specific change for good) per item, instead of leaving the
 * Publisher looking at a badge with nothing they can actually do about it.
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusIndicatorViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var showDetails by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.clickable(enabled = snapshot.status == SyncStatusCenter.Status.SYNC_ERROR) { showDetails = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(snapshot.status.emoji(), modifier = Modifier.padding(end = 6.dp))
        Text(snapshot.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showDetails) {
        SyncErrorDetailsDialog(viewModel = viewModel, onDismiss = { showDetails = false })
    }
}

@Composable
private fun SyncErrorDetailsDialog(viewModel: SyncStatusIndicatorViewModel, onDismiss: () -> Unit) {
    val failures by viewModel.permanentFailures.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        title = { Text("Sync Error") },
        text = {
            if (failures.isEmpty()) {
                Text("Nothing needs attention anymore.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "These changes were saved on this device but the server rejected them for a reason that won't fix itself by simply retrying (for example, a permission or data problem). Retry if you believe the underlying issue has been fixed; Discard if this change should be given up on for good.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(failures, key = { it.id }) { failure ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${failure.collectionPath} — ${failure.operationType}", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        failure.lastError ?: "Unknown error",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { viewModel.discard(failure) }) { Text("Discard") }
                                        TextButton(onClick = { viewModel.retry(failure) }) { Text("Retry") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
