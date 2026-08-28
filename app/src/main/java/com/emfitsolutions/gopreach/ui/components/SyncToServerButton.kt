package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.emfitsolutions.gopreach.data.sync.ConnectivityObserver
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.SyncScheduler
import com.emfitsolutions.gopreach.data.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the "SYNC TO SERVER" button is doing right now — separate from the passive
 * pending-count indicator ([SyncStatusButton]), this only reflects a run the user
 * explicitly triggered by tapping the button. */
sealed class ManualSyncState {
    data object Idle : ManualSyncState()
    data object NoNetwork : ManualSyncState()
    data class Syncing(val done: Int, val total: Int) : ManualSyncState()
    data class Summary(val uploaded: Int, val failed: Int) : ManualSyncState()
    /** Couldn't run at all (e.g. the connection dropped mid-sync) — distinct from
     * [Summary] with a nonzero [Summary.failed], which means it ran but some
     * individual records were rejected. */
    data object Failed : ManualSyncState()
}

/** "There is 1 change" / "There are 5 changes" (spec §10/§11 — the wording is
 * explicitly singular/plural, not just "N change(s)"). */
fun pendingChangesPhrase(count: Int): String =
    if (count == 1) "There is 1 change made that needs to sync into the server."
    else "There are $count changes made that need to sync into the server."

@HiltViewModel
class ManualSyncViewModel @Inject constructor(
    private val connectivityObserver: ConnectivityObserver,
    private val syncScheduler: SyncScheduler,
    offlineFirestoreRepository: OfflineFirestoreRepository,
) : ViewModel() {

    val pendingCount: StateFlow<Int> = offlineFirestoreRepository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow<ManualSyncState>(ManualSyncState.Idle)
    val uiState: StateFlow<ManualSyncState> = _uiState

    fun dismissDialog() {
        _uiState.value = ManualSyncState.Idle
    }

    /** Spec §5/§8: check connectivity and reject immediately (no silent queueing
     * for later) if offline; a click while already syncing is a no-op — the
     * button itself is disabled for that same reason, but this guards the
     * ViewModel side too in case something else ever calls it directly. Then
     * kicks off [SyncScheduler.requestSyncNow] and follows *that exact request's
     * id* — not "whatever the unique work name currently shows" — until it
     * reports finished (see [SyncScheduler.requestSyncNow]'s doc comment for
     * why the id-based tracking is what actually fixes the stuck-forever bug,
     * not just switching to `REPLACE`). */
    fun syncToServer() {
        if (_uiState.value is ManualSyncState.Syncing) return
        if (!connectivityObserver.isOnline()) {
            _uiState.value = ManualSyncState.NoNetwork
            return
        }
        _uiState.value = ManualSyncState.Syncing(done = 0, total = 0)
        val requestId = syncScheduler.requestSyncNow()
        viewModelScope.launch {
            syncScheduler.observeWorkInfo(requestId).collect { info ->
                if (info == null) return@collect
                val progress = info.progress
                val finished = progress.getBoolean(SyncWorker.KEY_FINISHED, false)
                if (finished) {
                    _uiState.value = ManualSyncState.Summary(
                        uploaded = progress.getInt(SyncWorker.KEY_UPLOADED, 0),
                        failed = progress.getInt(SyncWorker.KEY_FAILED, 0),
                    )
                    return@collect
                }
                when (info.state) {
                    WorkInfo.State.RUNNING -> _uiState.value = ManualSyncState.Syncing(
                        done = progress.getInt("done", 0),
                        total = progress.getInt(SyncWorker.KEY_TOTAL, 0),
                    )
                    WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> _uiState.value = ManualSyncState.Failed
                    // A defensive fallback, not the normal path: SUCCEEDED should
                    // already have been caught by the KEY_FINISHED progress check
                    // above. If it's ever reached here anyway (progress data missing
                    // for some reason), still resolve the UI out of Syncing rather
                    // than leaving the button stuck forever on "Checking for pending
                    // changes..." — this was a real bug (a stale WorkInfo from a past
                    // run, before requestSyncNow() switched to ExistingWorkPolicy
                    // .REPLACE, could report SUCCEEDED with none of this run's data).
                    WorkInfo.State.SUCCEEDED -> _uiState.value = ManualSyncState.Summary(uploaded = 0, failed = 0)
                    else -> Unit
                }
            }
        }
    }
}

/**
 * The Main Form's primary, explicit "[ SYNC TO SERVER ]" action — kept
 * completely independent of Refresh and of app-update checking (spec §18).
 * The app also syncs automatically now whenever internet/mobile data is
 * available (see [com.emfitsolutions.gopreach.data.sync.SyncScheduler
 * .ensureAutomaticSyncStarted]); this button is the tracked, user-visible
 * path — the one that shows real, measured progress ("SYNCING 47%", spec
 * §6) computed from actual completed/total operations, and is disabled so a
 * second tap can't start an overlapping run (spec §8) — not the only thing
 * that ever uploads a pending change anymore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncToServerButton(viewModel: ManualSyncViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncing = state as? ManualSyncState.Syncing

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = viewModel::syncToServer,
            enabled = syncing == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (syncing == null) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                if (syncing != null && syncing.total > 0) {
                    "SYNCING ${(syncing.done * 100 / syncing.total)}%"
                } else if (syncing != null) {
                    "SYNCING..."
                } else {
                    "SYNC TO SERVER"
                },
            )
        }

        // Spec §7/§13 — a small status line under the button: live upload
        // progress while syncing, otherwise the dynamic pending-changes count
        // (or a plain "synced" confirmation when there's nothing pending).
        val statusText = when {
            syncing != null && syncing.total > 0 -> "Uploading changes: ${syncing.done} / ${syncing.total}"
            syncing != null -> "Checking for pending changes..."
            state is ManualSyncState.Failed -> "Sync Failed – Try Again"
            pendingCount > 0 -> pendingChangesPhrase(pendingCount)
            else -> "✓ All changes synced"
        }
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (state is ManualSyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
        if (syncing != null && syncing.total > 0) {
            LinearProgressIndicator(
                progress = { syncing.done.toFloat() / syncing.total.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    when (val s = state) {
        ManualSyncState.NoNetwork -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("No Network Connection") },
            text = {
                Text(
                    "${pendingChangesPhrase(pendingCount).removeSuffix(".")} stored on this device.\n\n" +
                        "Connect to the Internet and try again.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("OK") } },
        )
        is ManualSyncState.Syncing -> Unit // reflected in the button/status line above, no blocking dialog
        ManualSyncState.Failed -> Unit // reflected in the status line above
        is ManualSyncState.Summary -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = {
                Text(
                    when {
                        s.failed == 0 -> "Sync Complete"
                        s.uploaded == 0 -> "Sync Failed"
                        else -> "Sync Partially Complete"
                    },
                )
            },
            text = {
                Text(
                    if (s.failed == 0) {
                        "All changes have been successfully synchronized with the server."
                    } else {
                        "${s.uploaded} changes synchronized.\n${s.failed} changes still need synchronization."
                    },
                    color = if (s.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("OK") } },
            dismissButton = if (s.failed > 0) {
                { TextButton(onClick = { viewModel.dismissDialog(); viewModel.syncToServer() }) { Text("Retry") } }
            } else null,
        )
        ManualSyncState.Idle -> Unit
    }
}
