package com.emfitsolutions.gopreach.ui.screens.pipeline

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** "FORWARD TO OTHER PUBLISHER" spec flow — the *receiving* publisher's
 * "Forwarded to Me" queue: full record details, [ACCEPT]/[DECLINE] directly
 * (no assignment step — the sender already targeted this exact publisher). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherForwardRequestsScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: PublisherForwardRequestsViewModel = hiltViewModel(),
) {
    val requestsFlow = remember(currentPersonId) { viewModel.incomingRequestsFor(currentPersonId) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<PublisherForwardRequest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forwarded to Me") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (requests.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No pending records forwarded to you.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(requests, key = { it.id }) { request ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
                            Text("From: ${request.fromPublisherNameSnapshot}", style = MaterialTheme.typography.bodySmall)
                            Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { selected = request }) { Text("REVIEW") }
                        }
                    }
                }
            }
        }
    }

    selected?.let { request ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Forwarded Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FORWARDED BY:", style = MaterialTheme.typography.labelLarge)
                    Text("Publisher Name: ${request.fromPublisherNameSnapshot}")
                    Text("—".repeat(20), style = MaterialTheme.typography.bodySmall)
                    Text("Name: ${request.personNameSnapshot}")
                    Text(
                        "Accepting adds this record to your own Bible Study/Return Visit record and removes it from ${request.fromPublisherNameSnapshot}'s.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.accept(request, currentPersonId); selected = null }) { Text("ACCEPT") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.decline(request, currentPersonId); selected = null }) { Text("DECLINE") }
                    TextButton(onClick = { selected = null }) { Text("CLOSE") }
                }
            },
        )
    }
}
