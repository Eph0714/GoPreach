package com.emfitsolutions.gopreach.ui.screens.userlogs

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spec §3 — "user logs": Super-Admin sees everything and can delete entries;
 * Admin/Coordinator Elder see only their own congregation's, view/export only.
 * "Export" here means the same JSON-file pattern as Backup & Restore rather than
 * a separate mechanism — left for a follow-up pass since it's a straightforward
 * repeat of [com.emfitsolutions.gopreach.ui.screens.backup.BackupRestoreScreen]'s
 * file-picker code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserLogsScreen(
    visibleCongregationId: String?,
    canDelete: Boolean,
    onBack: () -> Unit,
    viewModel: UserLogsViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(visibleCongregationId) { viewModel.rowsFor(visibleCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No activity recorded yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.entry.id }) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(row.entry.action.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                                Text(row.actorName, style = MaterialTheme.typography.bodySmall)
                                Text(dateFormat.format(Date(row.entry.timestamp)), style = MaterialTheme.typography.bodySmall)
                            }
                            if (canDelete) {
                                IconButton(onClick = { viewModel.delete(row.entry.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete entry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
