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
    val status: StateFlow<SyncStatusCenter.Status> = syncStatusCenter.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatusCenter.Status.OFFLINE)
}

private fun SyncStatusCenter.Status.emoji(): String = when (this) {
    SyncStatusCenter.Status.ONLINE -> "🟢" // 🟢
    SyncStatusCenter.Status.OFFLINE -> "🔴" // 🔴
    SyncStatusCenter.Status.SYNCING -> "🟡" // 🟡
    SyncStatusCenter.Status.SYNC_FAILED -> "⚠️" // ⚠️
}

private fun SyncStatusCenter.Status.label(): String = when (this) {
    SyncStatusCenter.Status.ONLINE -> "Online"
    SyncStatusCenter.Status.OFFLINE -> "Offline"
    SyncStatusCenter.Status.SYNCING -> "Syncing"
    SyncStatusCenter.Status.SYNC_FAILED -> "Sync Failed"
}

/**
 * Real-time connection/sync status badge — 🟢 Online / 🔴 Offline / 🟡 Syncing /
 * ⚠️ Sync Failed — independent of [ManualSyncViewModel]/[SyncToServerButton]'s
 * own state, since those only reflect a *manually*-triggered run: this reflects
 * the sync system as a whole, including the automatic background triggers
 * ([com.emfitsolutions.gopreach.data.sync.SyncScheduler]).
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    viewModel: SyncStatusIndicatorViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(status.emoji(), modifier = Modifier.padding(end = 6.dp))
        Text(status.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
