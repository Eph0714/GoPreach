package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
}

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

    /** Spec §7-§9: check connectivity first; if online, kick off the same
     * [SyncScheduler.requestSyncNow] the auto-sync path already uses (so behavior
     * stays identical — this is just an explicit, visible way to trigger and watch
     * it), then follow this run's [WorkInfo.progress] until it reports finished. */
    fun syncToServer() {
        if (!connectivityObserver.isOnline()) {
            _uiState.value = ManualSyncState.NoNetwork
            return
        }
        _uiState.value = ManualSyncState.Syncing(done = 0, total = 0)
        syncScheduler.requestSyncNow()
        viewModelScope.launch {
            syncScheduler.observeWorkInfo().collect { info ->
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
                if (info.state == WorkInfo.State.RUNNING) {
                    _uiState.value = ManualSyncState.Syncing(
                        done = progress.getInt("done", 0),
                        total = progress.getInt(SyncWorker.KEY_TOTAL, 0),
                    )
                } else if (info.state == WorkInfo.State.CANCELLED) {
                    // Genuinely couldn't run at all (rather than "some records
                    // failed", which is reported via the KEY_FINISHED progress above).
                    _uiState.value = ManualSyncState.Summary(uploaded = 0, failed = 0)
                }
            }
        }
    }
}

/**
 * The Main Form's primary, explicit "[ SYNC TO SERVER ]" action (spec §6/§17):
 * background auto-sync keeps running exactly as before (every local write already
 * queues and flushes on its own via [SyncScheduler]) — this button is a visible,
 * user-initiated way to check connectivity, trigger that same flush, watch its
 * progress, and see a clear summary afterward, rather than only trusting it
 * happened silently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncToServerButton(viewModel: ManualSyncViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Button(onClick = viewModel::syncToServer, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(if (pendingCount > 0) "SYNC TO SERVER ($pendingCount pending)" else "SYNC TO SERVER")
    }

    when (val s = state) {
        ManualSyncState.NoNetwork -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("No Network Connection") },
            text = {
                Text(
                    "GoPreach cannot connect to the server right now. Your local changes are safely stored on this device.\n\n" +
                        "Please connect to the Internet and try again.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDialog) { Text("OK") } },
        )
        is ManualSyncState.Syncing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Syncing to Server...") },
            text = {
                Column {
                    if (s.total > 0) {
                        Text("Uploading records: ${s.done} / ${s.total}")
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    } else {
                        Text("Checking for pending changes...")
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {},
        )
        is ManualSyncState.Summary -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(if (s.failed == 0) "Sync Complete" else "Sync Completed with Errors") },
            text = {
                Text(
                    buildString {
                        append("${s.uploaded} records synchronized")
                        if (s.failed > 0) append("\n${s.failed} records could not be synchronized")
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
