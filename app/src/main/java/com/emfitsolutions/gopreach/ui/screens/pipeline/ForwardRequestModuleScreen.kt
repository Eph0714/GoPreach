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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import com.emfitsolutions.gopreach.data.model.ForwardRequest
import com.emfitsolutions.gopreach.data.model.ForwardRequestStatus
import com.emfitsolutions.gopreach.data.model.InterestedPerson
import com.emfitsolutions.gopreach.data.model.PipelineStage
import com.emfitsolutions.gopreach.data.model.PublisherForwardRequest
import com.emfitsolutions.gopreach.ui.components.CongregationFilterDropdown
import com.emfitsolutions.gopreach.ui.components.formatRecordTimestamp
import com.emfitsolutions.gopreach.ui.components.rememberActionToast

/**
 * "Forward Request Module" — Super-Admin-only management view over every
 * forward request in the system, both kinds ("Forward to Other
 * Congregation/Group" and "Forward to Other Publisher"), filterable by
 * congregation (All Congregations, or one specific congregation — the same
 * shared [CongregationFilterDropdown] every other Super-Admin module already
 * uses). Unlike [ForwardRequestsScreen] (the Service Overseer's own
 * Accept/Decline queue, PENDING-only, scoped to their own congregation) this
 * shows every request regardless of status, and adds the two things only a
 * Super-Admin gets here: directly editing a request's own fields (correcting
 * a stale snapshot, or force-changing its status) and permanently deleting
 * one (cleaning up stale/test/mistaken request records) — neither of which
 * touches the underlying InterestedPerson record itself, only the request
 * document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardRequestModuleScreen(
    currentPersonId: String,
    onBack: () -> Unit,
    viewModel: ForwardRequestsViewModel = hiltViewModel(),
    publisherViewModel: PublisherForwardRequestsViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationIds = congregationFilter?.let { setOf(it) }

    val requestsFlow = remember(effectiveCongregationIds) { viewModel.allRequestsFor(effectiveCongregationIds) }
    val requests by requestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val publisherRequestsFlow = remember(effectiveCongregationIds) { publisherViewModel.requestsFor(effectiveCongregationIds) }
    val publisherRequests by publisherRequestsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editingCongregationRequest by remember { mutableStateOf<ForwardRequest?>(null) }
    var editingPublisherRequest by remember { mutableStateOf<PublisherForwardRequest?>(null) }
    var deletingCongregationRequest by remember { mutableStateOf<ForwardRequest?>(null) }
    var deletingPublisherRequest by remember { mutableStateOf<PublisherForwardRequest?>(null) }
    val showToast = rememberActionToast()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forward Request Module") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CongregationFilterDropdown(
                congregations = congregations,
                selectedCongregationId = congregationFilter,
                onSelected = { congregationFilter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (requests.isEmpty() && publisherRequests.isEmpty()) {
                Text(
                    "No forward requests.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
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
                            CongregationRequestRow(
                                request = request,
                                person = person,
                                onEdit = { editingCongregationRequest = request },
                                onDelete = { deletingCongregationRequest = request },
                            )
                        }
                    }
                    if (publisherRequests.isNotEmpty()) {
                        item { Text("To Other Publisher", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                        items(publisherRequests, key = { it.id }) { request ->
                            val personFlow = remember(request.interestedPersonId) { publisherViewModel.personFor(request.interestedPersonId) }
                            val person by personFlow.collectAsStateWithLifecycle(initialValue = null)
                            PublisherRequestRow(
                                request = request,
                                person = person,
                                onEdit = { editingPublisherRequest = request },
                                onDelete = { deletingPublisherRequest = request },
                            )
                        }
                    }
                }
            }
        }
    }

    editingCongregationRequest?.let { request ->
        EditCongregationRequestDialog(
            request = request,
            onDismiss = { editingCongregationRequest = null },
            onSave = { updated ->
                viewModel.updateRequest(updated, currentPersonId)
                showToast("Forward request updated.")
                editingCongregationRequest = null
            },
        )
    }
    editingPublisherRequest?.let { request ->
        EditPublisherRequestDialog(
            request = request,
            onDismiss = { editingPublisherRequest = null },
            onSave = { updated ->
                publisherViewModel.updateRequest(updated, currentPersonId)
                showToast("Forward request updated.")
                editingPublisherRequest = null
            },
        )
    }
    deletingCongregationRequest?.let { request ->
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
            onDismissRequest = { deletingCongregationRequest = null },
            title = { Text("Delete Forward Request?") },
            text = { Text("This permanently deletes the forward request for \"${request.personNameSnapshot}\" to ${request.toCongregationNameSnapshot}. The interested person record itself is not affected. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRequest(request, currentPersonId)
                    showToast("Forward request deleted.")
                    deletingCongregationRequest = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingCongregationRequest = null }) { Text("Cancel") } },
        )
    }
    deletingPublisherRequest?.let { request ->
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
            onDismissRequest = { deletingPublisherRequest = null },
            title = { Text("Delete Forward Request?") },
            text = { Text("This permanently deletes the forward request for \"${request.personNameSnapshot}\" to ${request.toPublisherNameSnapshot}. The interested person record itself is not affected. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    publisherViewModel.deleteRequest(request, currentPersonId)
                    showToast("Forward request deleted.")
                    deletingPublisherRequest = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingPublisherRequest = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CongregationRequestRow(
    request: ForwardRequest,
    person: InterestedPerson?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (statusText, statusColor) = statusTextAndColor(request.status)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
            person?.let { p ->
                Text(stageLabelForModule(p.pipelineStage), style = MaterialTheme.typography.bodySmall)
                addressLineForModule(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text("From: ${request.fromPublisherNameSnapshot} · ${request.fromCongregationNameSnapshot}", style = MaterialTheme.typography.bodySmall)
            Text("To: ${request.toCongregationNameSnapshot}", style = MaterialTheme.typography.bodySmall)
            Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor)
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun PublisherRequestRow(
    request: PublisherForwardRequest,
    person: InterestedPerson?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (statusText, statusColor) = statusTextAndColor(request.status)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(request.personNameSnapshot, style = MaterialTheme.typography.titleMedium)
            person?.let { p ->
                Text(stageLabelForModule(p.pipelineStage), style = MaterialTheme.typography.bodySmall)
                addressLineForModule(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Text("From: ${request.fromPublisherNameSnapshot} → ${request.toPublisherNameSnapshot}", style = MaterialTheme.typography.bodySmall)
            Text("Requested: ${formatRecordTimestamp(request.requestedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Status: $statusText", style = MaterialTheme.typography.bodySmall, color = statusColor)
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
        }
    }
}

@Composable
private fun statusTextAndColor(status: ForwardRequestStatus) = when (status) {
    ForwardRequestStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
    ForwardRequestStatus.ACCEPTED -> "Accepted" to MaterialTheme.colorScheme.primary
    ForwardRequestStatus.DECLINED -> "Declined" to MaterialTheme.colorScheme.error
    ForwardRequestStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCongregationRequestDialog(
    request: ForwardRequest,
    onDismiss: () -> Unit,
    onSave: (ForwardRequest) -> Unit,
) {
    var name by remember { mutableStateOf(request.personNameSnapshot) }
    var status by remember { mutableStateOf(request.status) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        onDismissRequest = onDismiss,
        title = { Text("Edit Forward Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = status.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ForwardRequestStatus.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { status = s; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(request.copy(personNameSnapshot = name, status = status)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPublisherRequestDialog(
    request: PublisherForwardRequest,
    onDismiss: () -> Unit,
    onSave: (PublisherForwardRequest) -> Unit,
) {
    var name by remember { mutableStateOf(request.personNameSnapshot) }
    var status by remember { mutableStateOf(request.status) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = true),
        onDismissRequest = onDismiss,
        title = { Text("Edit Forward Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = status.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ForwardRequestStatus.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { status = s; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(request.copy(personNameSnapshot = name, status = status)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun stageLabelForModule(stage: PipelineStage): String = when (stage) {
    PipelineStage.SEARCHING -> "Searching"
    PipelineStage.RETURN_VISIT -> "Return Visit"
    PipelineStage.BIBLE_STUDY -> "Bible Study"
}

private fun addressLineForModule(person: InterestedPerson): String? {
    val parts = listOfNotNull(person.barangay, person.cityMunicipality, person.province).filter { it.isNotBlank() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
