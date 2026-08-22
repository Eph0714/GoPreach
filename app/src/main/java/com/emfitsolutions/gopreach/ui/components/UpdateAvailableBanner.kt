package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.sync.RemoteUpdateTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateAvailableViewModel @Inject constructor(
    private val tracker: RemoteUpdateTracker,
) : ViewModel() {
    val hasUpdates = tracker.hasUpdates
    fun acknowledge() = tracker.acknowledge()
}

/**
 * The app is always current while online (every collection has a live
 * Firestore listener — see RemoteSyncCoordinator), so this isn't gating
 * anything the way a "pull to load new data" banner would in a polling app.
 * It's a visible confirmation that something changed elsewhere since this
 * screen was last opened, for users who want that reassurance rather than
 * trusting a silent background sync. Tapping Refresh just acknowledges it —
 * the data shown is already the latest.
 */
@Composable
fun UpdateAvailableBanner(viewModel: UpdateAvailableViewModel = hiltViewModel()) {
    val hasUpdates by viewModel.hasUpdates.collectAsStateWithLifecycle()
    if (!hasUpdates) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "New updates are available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = viewModel::acknowledge) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Refresh")
            }
        }
    }
}
