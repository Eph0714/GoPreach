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
import androidx.compose.runtime.LaunchedEffect
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
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.ui.components.CongregationFilterDropdown
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast
import androidx.compose.ui.window.DialogProperties

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
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    // "Add a filter for Congregation" (Super-Admin only).
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationIds = congregationFilter?.let { setOf(it) } ?: congregationIds
    val requestsFlow = remember(effectiveCongregationIds) { viewModel.pendingRequestsFor(effectiveCongregationIds) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val publisherRequestsFlow = remember(effectiveCongregationIds) { publisherForwardViewModel.requestsFor(effectiveCongregationIds) }
    val publisherRequests by publisherRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<ForwardRequest?>(null) }
    val showToast = rememberActionToast()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forward Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (congregationIds == null) {
            CongregationFilterDropdown(
                congregations = congregations,
                selectedCongregationId = congregationFilter,
                onSelected = { congregationFilter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (requests.isEmpty() && publisherRequests.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No forward requests.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (requests.isNotEmpty()) {
                    item { Text("To Other Congregation/Group", style = MaterialTheme.typography.titleSmall) }
                    items(requests, key = { it.id }) { request ->
                        val personFlow = remember(request.interestedPersonId) { viewModel.personFor(request.interestedPersonId) }
                        val person by personFlow.collectAsStateWithLifecycle(initialValue = null)
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
                                // "Include the basic details of the forwarded
                                // record, not just the name" — stage + address,
                                // live off the record itself (see
                                // ForwardRequestsViewModel.personFor's own doc
                                // comment).
                                person?.let { p ->
                                    Text(stageLabel(p.pipelineStage), style = MaterialTheme.typography.bodySmall)
                                    addressLine(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                Text("From: ${request.fromPublisherNameSnapshot} · ${request.fromCongregationNameSnapshot}", style = MaterialTheme.typography.bodySmall)
                                Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { selected = request }) { Text(if (readOnly) "View" else "Review") }
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
    }

    AutoCloseOnNoLongerPending(selected?.id, requests.map { it.id }) { selected = null }

    selected?.let { request ->
        ReviewForwardRequestDialog(
            request = request,
            currentPersonId = currentPersonId,
            readOnly = readOnly,
            onDismiss = { selected = null },
            onDecline = {
                viewModel.decline(request, currentPersonId)
                showToast("Forward request declined.")
                selected = null
            },
            onAccepted = { assignedTo -> showToast("Accepted — assigned to ${assignedTo.fullName}.") },
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
        ForwardRequestStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
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
    onAccepted: (Person) -> Unit,
    viewModel: ForwardRequestsViewModel,
) {
    val publishersFlow = remember(request.toCongregationId) { viewModel.assignablePublishers(request.toCongregationId) }
    val publishers by publishersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val personFlow = remember(request.interestedPersonId) { viewModel.personFor(request.interestedPersonId) }
    val person by personFlow.collectAsStateWithLifecycle(initialValue = null)
    var assigning by remember { mutableStateOf(false) }
    var selectedPublisher by remember { mutableStateOf<Person?>(null) }
    // "Prevent Double Submission" — this dialog predates FormDialog's own
    // built-in guard and isn't built on it (a custom two-step Accept/Assign
    // flow), so it needs its own; a fresh instance of this composable is
    // created each time a request is opened, so this always starts unarmed.
    var hasActed by remember { mutableStateOf(false) }

    if (readOnly) {
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
            onDismissRequest = onDismiss,
            title = { Text("Forward Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FORWARD REQUEST FROM:", style = MaterialTheme.typography.labelLarge)
                    Text("Publisher Name: ${request.fromPublisherNameSnapshot}")
                    Text("Congregation/Group: ${request.fromCongregationNameSnapshot}")
                    Text("—".repeat(20), style = MaterialTheme.typography.bodySmall)
                    Text("Name: ${request.personNameSnapshot}")
                    person?.let { p ->
                        Text("Record status: ${stageLabel(p.pipelineStage)}")
                        addressLine(p)?.let { Text("Address: $it") }
                    }
                    Text("Status: Pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
        return
    }

    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        onDismissRequest = onDismiss,
        title = { Text("Forward Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FORWARD REQUEST FROM:", style = MaterialTheme.typography.labelLarge)
                Text("Publisher Name: ${request.fromPublisherNameSnapshot}")
                Text("Congregation/Group: ${request.fromCongregationNameSnapshot}")
                Text("—".repeat(20), style = MaterialTheme.typography.bodySmall)
                Text("Name: ${request.personNameSnapshot}")
                person?.let { p ->
                    Text("Record status: ${stageLabel(p.pipelineStage)}")
                    addressLine(p)?.let { Text("Address: $it") }
                }
                if (!assigning) {
                    Text("To assign this record to a publisher in your congregation, tap Accept.", style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = { assigning = true }, enabled = !hasActed) { Text("Accept") }
            } else {
                TextButton(
                    onClick = {
                        val publisher = selectedPublisher
                        if (publisher != null && !hasActed) {
                            hasActed = true
                            viewModel.accept(request, publisher, currentPersonId)
                            onAccepted(publisher)
                            onDismiss()
                        }
                    },
                    enabled = selectedPublisher != null && !hasActed,
                ) { Text("Confirm") }
            }
        },
        dismissButton = {
            if (!assigning) {
                TextButton(
                    onClick = { if (!hasActed) { hasActed = true; onDecline() } },
                    enabled = !hasActed,
                ) { Text("Decline") }
            } else {
                TextButton(onClick = { assigning = false; selectedPublisher = null }, enabled = !hasActed) { Text("Back") }
            }
        },
    )
}

/** "If a forward request is cancelled, the accept/decline dialog open on the
 * receiving side must close automatically" — [selected] holds a static
 * snapshot of the request from when the dialog opened, so it never sees the
 * sender's later cancel on its own; this watches the *live*, reactively
 * filtered [requests] list instead and clears [selected] the moment the open
 * request's id drops out of it (cancelled, accepted, or declined by this
 * same screen, or the record's own PENDING status otherwise changing
 * server-side) — a real, no-manual-refresh close, not just "next open will be
 * stale-free". */
@Composable
private fun AutoCloseOnNoLongerPending(selectedId: String?, pendingIds: List<String>, onAutoClose: () -> Unit) {
    LaunchedEffect(selectedId, pendingIds) {
        if (selectedId != null && selectedId !in pendingIds) onAutoClose()
    }
}

/** "Include the basic details of the forwarded record, not just the name" —
 * a plain, human-readable stage name (matching the label this same stage
 * shows as everywhere else in the pipeline UI — see PipelineScreen's own,
 * screen-private equivalent). */
private fun stageLabel(stage: PipelineStage): String = when (stage) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

/** Barangay/City-Municipality/Province, comma-joined, skipping whichever of
 * the three weren't filled in — `null` (not an empty string) when none of
 * them were, so callers can cleanly skip the line entirely instead of
 * showing an empty one. */
private fun addressLine(person: InterestedPerson): String? {
    val parts = listOfNotNull(person.barangay, person.cityMunicipality, person.province).filter { it.isNotBlank() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
