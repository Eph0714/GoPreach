package com.emfitsolutions.gopreach.ui.screens.sharelocation

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spec §6.1 — Share Location. [canShareOwnLocation] gates the "share my
 * location" toggle to publishers only, per spec ("Publisher: can share own
 * location while preaching"); every role can view whoever's currently sharing
 * within their own scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationScreen(
    currentPersonId: String,
    visibleCongregationId: String?,
    visibleGroupId: String?,
    canShareOwnLocation: Boolean,
    onBack: () -> Unit,
    viewModel: ShareLocationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isSharing by viewModel.isSharing.collectAsStateWithLifecycle()
    val rowsFlow = remember(visibleCongregationId, visibleGroupId) {
        viewModel.rowsFor(visibleCongregationId, visibleGroupId, currentPersonId)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.toggleSharing(true, currentPersonId, visibleCongregationId, visibleGroupId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (canShareOwnLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Share my location while preaching", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isSharing,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                viewModel.toggleSharing(false, currentPersonId, visibleCongregationId, visibleGroupId)
                            }
                        },
                    )
                }
            }

            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No one is currently sharing their location.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.person.id }) { row ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Updated ${dateFormat.format(Date(row.location.updatedAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = {
                                    val uri = Uri.parse("geo:${row.location.lat},${row.location.lng}?q=${row.location.lat},${row.location.lng}(${row.person.fullName})")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }) { Text("Open in Maps") }
                            }
                        }
                    }
                }
            }
        }
    }
}
