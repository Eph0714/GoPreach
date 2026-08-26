package com.emfitsolutions.gopreach.ui.screens.pipeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** "Forward to Other Congregation" spec flow — the receiving Service
 * Overseer's (also Coordinator Elder/Admin/Super-Admin) incoming review
 * queue: full record details, [ACCEPT] (then Assign to Publisher) / [DECLINE]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardRequestsScreen(
    congregationIds: Set<String>?,
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: ForwardRequestsViewModel = hiltViewModel(),
) {
    val requestsFlow = remember(congregationIds) { viewModel.pendingRequestsFor(congregationIds) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<ForwardRequest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forward Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (requests.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No pending forward requests.", style = MaterialTheme.typography.bodyMedium)
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
                            Text("From: ${request.fromPublisherNameSnapshot} · ${request.fromCongregationNameSnapshot}", style = MaterialTheme.typography.bodySmall)
                            Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { selected = request }) { Text("REVIEW") }
                        }
                    }
                }
            }
        }
    }

    selected?.let { request ->
        ReviewForwardRequestDialog(
            request = request,
            currentPersonId = currentPersonId,
            onDismiss = { selected = null },
            onDecline = {
                viewModel.decline(request, currentPersonId)
                selected = null
            },
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewForwardRequestDialog(
    request: ForwardRequest,
    currentPersonId: String,
    onDismiss: () -> Unit,
    onDecline: () -> Unit,
    viewModel: ForwardRequestsViewModel,
) {
    val publishersFlow = remember(request.toCongregationId) { viewModel.assignablePublishers(request.toCongregationId) }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var assigning by remember { mutableStateOf(false) }
    var selectedPublisher by remember { mutableStateOf<Person?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FORWARD REQUEST FROM:", style = MaterialTheme.typography.labelLarge)
                Text("Publisher Name: ${request.fromPublisherNameSnapshot}")
                Text("Congregation: ${request.fromCongregationNameSnapshot}")
                Text("—".repeat(20), style = MaterialTheme.typography.bodySmall)
                Text("Name: ${request.personNameSnapshot}")
                if (!assigning) {
                    Text("To assign this record to a publisher in your congregation, tap ACCEPT.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Assign to:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedPublisher?.fullName ?: "Select a publisher",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            visualTransformation = VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (publishers.isEmpty()) {
                                DropdownMenuItem(text = { Text("No publishers available") }, onClick = {}, enabled = false)
                            }
                            publishers.forEach { p ->
                                DropdownMenuItem(text = { Text(p.fullName) }, onClick = { selectedPublisher = p; expanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!assigning) {
                TextButton(onClick = { assigning = true }) { Text("ACCEPT") }
            } else {
                TextButton(
                    onClick = {
                        val publisher = selectedPublisher
                        if (publisher != null) {
                            viewModel.accept(request, publisher, currentPersonId)
                            onDismiss()
                        }
                    },
                ) { Text("SAVE") }
            }
        },
        dismissButton = {
            if (!assigning) {
                TextButton(onClick = onDecline) { Text("DECLINE") }
            } else {
                TextButton(onClick = { assigning = false; selectedPublisher = null }) { Text("BACK") }
            }
        },
    )
}
