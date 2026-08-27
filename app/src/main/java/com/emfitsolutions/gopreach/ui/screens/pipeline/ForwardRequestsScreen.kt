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
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp

/** "Forward to Other Congregation" spec flow — the receiving Service
 * Overseer's (also Coordinator Elder/Admin/Super-Admin) incoming review
 * queue: full record details, [ACCEPT] (then Assign to Publisher) / [DECLINE].
 * Also lists same-congregation "FORWARD TO OTHER PUBLISHER" requests
 * read-only below it — every role "can also see this," per spec, but only
 * the target publisher ever acts on that one.
 *
 * [readOnly] widens *visibility* of this whole screen to Regular Elder/
 * Ministerial Servant (the notification balloon's "Incoming approval request
 * for transfer [All]" item) without widening *approval authority* — they see
 * the same request details Service Overseer/Coordinator Elder/Admin/
 * Super-Admin do, just with [ACCEPT]/[DECLINE] replaced by a plain [CLOSE]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardRequestsScreen(
    congregationIds: Set<String>?,
    currentPersonId: String,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    viewModel: ForwardRequestsViewModel = hiltViewModel(),
    publisherForwardViewModel: PublisherForwardRequestsViewModel = hiltViewModel(),
) {
    val requestsFlow = remember(congregationIds) { viewModel.pendingRequestsFor(congregationIds) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val publisherRequestsFlow = remember(congregationIds) { publisherForwardViewModel.requestsFor(congregationIds) }
    val publisherRequests by publisherRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<ForwardRequest?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forward Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (requests.isEmpty() && publisherRequests.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No forward requests.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (requests.isNotEmpty()) {
                    item { Text("To Other Congregation", style = MaterialTheme.typography.titleSmall) }
                    items(requests, key = { it.id }) { request ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
                                Text("From: ${request.fromPublisherNameSnapshot} · ${request.fromCongregationNameSnapshot}", style = MaterialTheme.typography.bodySmall)
                                Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { selected = request }) { Text(if (readOnly) "VIEW" else "REVIEW") }
                            }
                        }
                    }
                }
                if (publisherRequests.isNotEmpty()) {
                    item { Text("To Other Publisher (view only)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(publisherRequests, key = { it.id }) { request ->
                        PublisherForwardRequestRow(request)
                    }
                }
            }
        }
    }

    selected?.let { request ->
        ReviewForwardRequestDialog(
            request = request,
            currentPersonId = currentPersonId,
            readOnly = readOnly,
            onDismiss = { selected = null },
            onDecline = {
                viewModel.decline(request, currentPersonId)
                selected = null
            },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun PublisherForwardRequestRow(request: PublisherForwardRequest) {
    val (statusText, statusColor) = when (request.status) {
        ForwardRequestStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
        ForwardRequestStatus.ACCEPTED -> "Accepted" to MaterialTheme.colorScheme.primary
        ForwardRequestStatus.DECLINED -> "Declined" to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
            Text("From: ${request.fromPublisherNameSnapshot} → ${request.toPublisherNameSnapshot}", style = MaterialTheme.typography.bodySmall)
            Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewForwardRequestDialog(
    request: ForwardRequest,
    currentPersonId: String,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onDecline: () -> Unit,
    viewModel: ForwardRequestsViewModel,
) {
    val publishersFlow = remember(request.toCongregationId) { viewModel.assignablePublishers(request.toCongregationId) }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var assigning by remember { mutableStateOf(false) }
    var selectedPublisher by remember { mutableStateOf<Person?>(null) }

    if (readOnly) {
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
                    Text("Status: Pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        )
        return
    }

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
