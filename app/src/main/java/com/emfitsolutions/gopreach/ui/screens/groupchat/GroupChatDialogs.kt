package com.emfitsolutions.gopreach.ui.screens.groupchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.model.Congregation
import com.emfitsolutions.gopreach.data.model.GroupChat
import com.emfitsolutions.gopreach.ui.components.FormDialog
import com.emfitsolutions.gopreach.ui.components.requiredFieldsMessage

/**
 * "Select Congregation" dropdown — Super-Admin only (spec §5: "a real
 * dropdown only for Super-Admin"). Everyone else's congregation is fixed
 * and shown read-only instead (spec §4: "the assigned congregation should
 * be displayed as read-only... no congregation selector that allows access
 * to other congregations").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatCongregationPicker(congregations: List<Congregation>, selectedId: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: ""
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Congregation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            congregations.forEach { congregation ->
                DropdownMenuItem(text = { Text(congregation.name) }, onClick = { onSelected(congregation.id); expanded = false })
            }
        }
    }
}

/**
 * "Include a search box to easily search participants by Full Name,
 * Publisher Name, Role" + "Select Multiple Participants" — the reusable
 * congregation-scoped picker both [CreateGroupChatDialog] and
 * [ManageParticipantsDialog] build on.
 */
@Composable
private fun ParticipantPicker(
    candidates: List<ParticipantCandidate>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(candidates, query) {
        if (query.isBlank()) candidates
        else candidates.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.publisherName.contains(query, ignoreCase = true) ||
                it.roleLabel.contains(query, ignoreCase = true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search by name, publisher name, or role") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filtered.isEmpty()) {
            Text(
                "No participants found in this congregation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                items(filtered, key = { it.personId }) { candidate ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = candidate.personId in selectedIds, onCheckedChange = { onToggle(candidate.personId) })
                        Column {
                            Text(candidate.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(candidate.roleLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** "The Coordinator Elder must be able to create and manage a group chat" —
 * name/description + congregation (fixed, unless Super-Admin) + participant
 * picker, all in one create step. */
@Composable
fun CreateGroupChatDialog(
    fixedCongregationId: String?,
    congregations: List<Congregation>,
    currentPersonId: String,
    viewModel: GroupChatViewModel,
    onDismiss: () -> Unit,
    onCreated: (GroupChat) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pickedCongregationId by remember { mutableStateOf(fixedCongregationId) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val congregationId = fixedCongregationId ?: pickedCongregationId

    val candidatesFlow = remember(congregationId) { viewModel.candidatesFor(congregationId) }
    val candidates by candidatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    fun submit() {
        val message = requiredFieldsMessage(
            "Group Chat Name" to groupName.isNotBlank(),
            "Congregation" to (congregationId != null),
        )
        if (message != null) {
            errorMessage = message
            return
        }
        viewModel.createGroupChat(
            congregationId = congregationId!!,
            groupName = groupName.trim(),
            description = description.trim(),
            participantIds = selectedIds.toList(),
            createdByPersonId = currentPersonId,
            onCreated = onCreated,
        )
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "New Group Chat",
        onConfirm = ::submit,
        confirmLabel = "Create",
        errorMessage = errorMessage,
        maxContentHeight = 560.dp,
    ) {
        if (fixedCongregationId == null) {
            GroupChatCongregationPicker(congregations = congregations, selectedId = pickedCongregationId, onSelected = { pickedCongregationId = it; selectedIds = emptySet() })
        }
        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Group Chat Name") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        if (congregationId != null) {
            Text("Participants", style = MaterialTheme.typography.labelLarge)
            ParticipantPicker(
                candidates = candidates,
                selectedIds = selectedIds,
                onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
            )
        }
    }
}

/** Group Chat's own "Settings" — rename/describe + add/remove participants,
 * still congregation-locked to the chat's own [GroupChat.congregationId]
 * (spec §3: a Coordinator Elder/Admin may never move a chat to, or add
 * participants from, another congregation). */
@Composable
fun ManageParticipantsDialog(
    chat: GroupChat,
    currentPersonId: String,
    viewModel: GroupChatViewModel,
    onDismiss: () -> Unit,
) {
    var groupName by remember(chat.id) { mutableStateOf(chat.groupName) }
    var description by remember(chat.id) { mutableStateOf(chat.description) }
    var selectedIds by remember(chat.id) { mutableStateOf(chat.participantIds.toSet()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val candidatesFlow = remember(chat.congregationId) { viewModel.candidatesFor(chat.congregationId) }
    val candidates by candidatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    fun submit() {
        val message = requiredFieldsMessage("Group Chat Name" to groupName.isNotBlank())
        if (message != null) {
            errorMessage = message
            return
        }
        viewModel.updateGroupChat(chat.id, groupName.trim(), description.trim())
        viewModel.updateParticipants(chat.id, chat.congregationId, selectedIds.toList(), currentPersonId)
        onDismiss()
    }

    FormDialog(
        onDismissRequest = onDismiss,
        title = "Group Chat Settings",
        onConfirm = ::submit,
        confirmLabel = "Save",
        errorMessage = errorMessage,
        maxContentHeight = 560.dp,
    ) {
        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Group Chat Name") },
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Participants", style = MaterialTheme.typography.labelLarge)
        ParticipantPicker(
            candidates = candidates,
            selectedIds = selectedIds,
            onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
        )
    }
}
