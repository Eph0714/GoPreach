package com.emfitsolutions.gopreach.ui.screens.territoryassignments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emfitsolutions.gopreach.data.repository.TerritoryAssignmentResult
import com.emfitsolutions.gopreach.ui.components.CongregationFilterDropdown
import com.emfitsolutions.gopreach.ui.components.GroupColorPalette
import com.emfitsolutions.gopreach.ui.components.rememberActionToast

/**
 * Territory Assignment dashboard — search/filter/counts + a card per
 * assignment (FS Group, Municipality, its barangays), Edit/Remove for
 * authorized users. [fixedCongregationId] scopes the list for everyone but
 * Super-Admin, same pattern as [com.emfitsolutions.gopreach.ui.screens.groups
 * .ManageGroupsScreen]. [readOnly] hides Add/Edit/Remove — no view-only role
 * is spec'd for this module today, kept for parity with the rest of the app's
 * admin screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryAssignmentsScreen(
    fixedCongregationId: String?,
    currentPersonId: String,
    readOnly: Boolean = false,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (assignmentId: String) -> Unit,
    viewModel: TerritoryAssignmentsViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationId = fixedCongregationId ?: congregationFilter
    var searchQuery by remember { mutableStateOf("") }
    val rowsFlow = remember(effectiveCongregationId, searchQuery) { viewModel.rowsFor(effectiveCongregationId, searchQuery) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingRemove by remember { mutableStateOf<TerritoryAssignmentRow?>(null) }
    // "Make the record clickable, show the barangays inside it" — which
    // single card is currently expanded to list its individual barangays;
    // collapsing one assignment when another is tapped open keeps the list
    // from growing unreadably long with every record expanded at once.
    var expandedAssignmentId by remember { mutableStateOf<String?>(null) }
    // "If a barangay is selected show the boundary map" — municipality name
    // + barangay name for the currently-open boundary dialog, null when none.
    var selectedBarangay by remember { mutableStateOf<Pair<String, String>?>(null) }
    val removeResult by viewModel.removeResult.collectAsStateWithLifecycle()
    val showToast = rememberActionToast()

    LaunchedEffect(removeResult) {
        when (val result = removeResult) {
            is TerritoryAssignmentResult.Success -> {
                showToast("Territory assignment removed.")
                viewModel.consumeRemoveResult()
            }
            is TerritoryAssignmentResult.Offline -> {
                showToast(result.message)
                viewModel.consumeRemoveResult()
            }
            is TerritoryAssignmentResult.Error -> {
                showToast(result.message)
                viewModel.consumeRemoveResult()
            }
            is TerritoryAssignmentResult.Conflict, null -> Unit
        }
    }

    val totalBarangays = rows.sumOf { it.barangays.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Territory Assignment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Territory Assignment")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (fixedCongregationId == null) {
                CongregationFilterDropdown(
                    congregations = congregations,
                    selectedCongregationId = congregationFilter,
                    onSelected = { congregationFilter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search: FS Group, Municipality, Barangay") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                "${rows.size} assignment${if (rows.size == 1) "" else "s"} · $totalBarangays barangay${if (totalBarangays == 1) "" else "s"} covered",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No territory assignments yet. Tap + to add one." else "No assignment matches this search.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.assignment.id }) { row ->
                        val isExpanded = expandedAssignmentId == row.assignment.id
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { expandedAssignmentId = if (isExpanded) null else row.assignment.id },
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // "Same Group = Same Color" — the same
                                        // swatch every other Group-colored
                                        // screen in this app reads.
                                        Box(
                                            modifier = Modifier.size(14.dp)
                                                .clip(CircleShape)
                                                .background(GroupColorPalette.parseHex(row.group?.color ?: GroupColorPalette.UNASSIGNED_COLOR)),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(row.group?.name ?: "Unknown Group", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!readOnly) {
                                            IconButton(onClick = { onEdit(row.assignment.id) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                            }
                                            IconButton(onClick = { pendingRemove = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        )
                                    }
                                }
                                Text(row.assignment.muncityName, style = MaterialTheme.typography.bodyMedium)
                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Text(
                                        "${row.barangays.size} barangay${if (row.barangays.size == 1) "" else "s"} — tap one to see its boundary",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    row.barangays.forEach { barangay ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { selectedBarangay = row.assignment.muncityName to barangay.barangayName }
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(barangay.barangayName, style = MaterialTheme.typography.bodyMedium)
                                            Icon(
                                                Icons.Rounded.Map,
                                                contentDescription = "Show boundary",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "${row.barangays.size} barangay${if (row.barangays.size == 1) "" else "s"}: " +
                                            row.barangays.joinToString(", ") { it.barangayName },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRemove?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove this territory assignment?") },
            text = {
                Text(
                    "${row.group?.name ?: "This group"}'s claim on ${row.assignment.muncityName} will be removed. " +
                        "${row.barangays.size} barangay${if (row.barangays.size == 1) "" else "s"} will become available to assign elsewhere.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(row.assignment.id, row.assignment.congregationId, currentPersonId)
                    pendingRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } },
        )
    }

    selectedBarangay?.let { (municipality, barangayName) ->
        BarangayBoundaryDialog(
            municipality = municipality,
            barangayName = barangayName,
            onDismiss = { selectedBarangay = null },
        )
    }
}
