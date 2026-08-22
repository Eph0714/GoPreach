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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.sync.OfflineFirestoreRepository
import com.emfitsolutions.gopreach.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    offlineFirestoreRepository: OfflineFirestoreRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    // The pending-operations queue is one shared table underneath every domain
    // repository (see OfflineFirestoreRepository), so this count is global —
    // it doesn't matter that this ViewModel isn't tied to any one collection.
    val pendingCount: StateFlow<Int> = offlineFirestoreRepository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun syncNow() = syncScheduler.requestSyncNow()
}

/**
 * The app works fully offline (spec: local writes always succeed instantly,
 * queued for later); this is the visible "you have N changes waiting to go
 * up" affordance plus a manual trigger, rather than only relying on the
 * automatic background flush on reconnect (see SyncScheduler/SyncWorker) —
 * some users want to see and control that moment, not just trust it happened.
 */
@Composable
fun SyncStatusButton(viewModel: SyncStatusViewModel = hiltViewModel()) {
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    Box {
        IconButton(onClick = viewModel::syncNow) {
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
