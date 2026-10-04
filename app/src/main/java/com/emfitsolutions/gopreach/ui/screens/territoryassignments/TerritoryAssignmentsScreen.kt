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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
 * Territory Assignment dashboard — one card per Field Service Group's whole
 * territory **within one province** (see [GroupTerritoryRow]'s own doc
 * comment), each municipality shown as its own heading with its barangays
 * nested underneath. [fixedCongregationId] scopes the list for everyone but
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
    onEdit: (congregationId: String, groupId: String, provinceId: Int) -> Unit,
    viewModel: TerritoryAssignmentsViewModel = hiltViewModel(),
) {
    val congregations by viewModel.congregations.collectAsStateWithLifecycle(initialValue = emptyList())
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    val effectiveCongregationId = fixedCongregationId ?: congregationFilter
    var searchQuery by remember { mutableStateOf("") }
    var provinceFilter by remember { mutableStateOf<Int?>(null) }
    var sortOption by remember { mutableStateOf(TerritorySortOption.GROUP_NAME) }
    val rowsFlow = remember(effectiveCongregationId, searchQuery, provinceFilter, sortOption) {
        viewModel.rowsFor(effectiveCongregationId, searchQuery, provinceFilter, sortOption)
    }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Province options for the filter dropdown — derived from whatever is
    // currently loaded (before the province filter itself narrows it), so
    // the dropdown always offers every province this scope actually has data
    // in, never an empty "nothing to pick" list once a filter is applied.
    val allRowsFlow = remember(effectiveCongregationId, searchQuery) {
        viewModel.rowsFor(effectiveCongregationId, searchQuery, provinceFilter = null)
    }
    val allRows by allRowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val provinceOptions = remember(allRows) {
        allRows.map { it.provinceId to it.provinceName }.distinct().sortedBy { it.second }
    }

    var pendingRemoveMunicipality by remember { mutableStateOf<Pair<GroupTerritoryRow, MunicipalityAssignment>?>(null) }
    var pendingRemoveGroup by remember { mutableStateOf<GroupTerritoryRow?>(null) }
    // "Make the record clickable, show the barangays inside it" — which
    // single card is currently expanded; collapsing one when another is
    // tapped open keeps the list from growing unreadably long.
    var expandedKey by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // "If a barangay is selected show the boundary map" — province +
    // municipality + barangay name for the currently-open boundary dialog,
    // null when none.
    var selectedBarangay by remember { mutableStateOf<Triple<String, String, String>?>(null) }
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

    val totalMunicipalities = rows.sumOf { it.municipalities.size }
    val totalBarangays = rows.sumOf { it.totalBarangays }

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
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                LabeledDropdown(
                    label = "Province",
                    selectedLabel = provinceOptions.firstOrNull { it.first == provinceFilter }?.second ?: "All Provinces",
                    options = listOf(null to "All Provinces") + provinceOptions.map { it.first to it.second },
                    onSelected = { provinceFilter = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                LabeledDropdown(
                    label = "Sort by",
                    selectedLabel = sortOption.label,
                    options = TerritorySortOption.entries.map { it to it.label },
                    onSelected = { sortOption = it ?: TerritorySortOption.GROUP_NAME },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "${rows.size} assignment${if (rows.size == 1) "" else "s"} · " +
                    "$totalMunicipalities municipalit${if (totalMunicipalities == 1) "y" else "ies"} · " +
                    "$totalBarangays barangay${if (totalBarangays == 1) "" else "s"} covered",
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
                        if (searchQuery.isBlank() && provinceFilter == null) {
                            "No territory assignments yet. Tap + to add one."
                        } else {
                            "No assignment matches this search/filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.groupId to it.provinceId }) { row ->
                        val key = row.groupId to row.provinceId
                        val isExpanded = expandedKey == key
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { expandedKey = if (isExpanded) null else key },
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
                                            IconButton(onClick = { onEdit(row.congregationId, row.groupId, row.provinceId) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                            }
                                            IconButton(onClick = { pendingRemoveGroup = row }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        )
                                    }
                                }
                                Text(
                                    "${row.municipalities.size} Municipalit${if (row.municipalities.size == 1) "y" else "ies"} · " +
                                        "${row.totalBarangays} Barangay${if (row.totalBarangays == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    row.provinceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    row.municipalities.forEach { municipality ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                municipality.assignment.muncityName.uppercase(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            if (!readOnly) {
                                                IconButton(
                                                    modifier = Modifier.size(28.dp),
                                                    onClick = { pendingRemoveMunicipality = row to municipality },
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = "Remove ${municipality.assignment.muncityName}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        municipality.barangays.forEach { barangay ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable {
                                                        selectedBarangay = Triple(row.provinceName, municipality.assignment.muncityName, barangay.barangayName)
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text("•  ${barangay.barangayName}", style = MaterialTheme.typography.bodyMedium)
                                                Icon(
                                                    Icons.Rounded.Map,
                                                    contentDescription = "Show boundary",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        row.municipalities.joinToString(" · ") { it.assignment.muncityName },
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

    pendingRemoveMunicipality?.let { (row, municipality) ->
        AlertDialog(
            onDismissRequest = { pendingRemoveMunicipality = null },
            title = { Text("Remove ${municipality.assignment.muncityName}?") },
            text = {
                Text(
                    "${row.group?.name ?: "This group"}'s claim on ${municipality.assignment.muncityName} will be removed. " +
                        "${municipality.barangays.size} barangay${if (municipality.barangays.size == 1) "" else "s"} will become available to assign elsewhere.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(municipality.assignment.id, municipality.assignment.congregationId, currentPersonId)
                    pendingRemoveMunicipality = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveMunicipality = null }) { Text("Cancel") } },
        )
    }

    pendingRemoveGroup?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingRemoveGroup = null },
            title = { Text("Remove this territory assignment?") },
            text = {
                Text(
                    "${row.group?.name ?: "This group"}'s whole territory in ${row.provinceName} will be removed — " +
                        "${row.municipalities.joinToString(", ") { it.assignment.muncityName }} " +
                        "(${row.totalBarangays} barangay${if (row.totalBarangays == 1) "" else "s"} total) will become available to assign elsewhere.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeGroup(row.congregationId, row.groupId, row.provinceId, currentPersonId)
                    pendingRemoveGroup = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoveGroup = null }) { Text("Cancel") } },
        )
    }

    selectedBarangay?.let { (province, municipality, barangayName) ->
        BarangayBoundaryDialog(
            province = province,
            municipality = municipality,
            barangayName = barangayName,
            onDismiss = { selectedBarangay = null },
        )
    }
}

/** Small read-only searchable-free dropdown shared by the Province/Sort
 * filter row — few enough options in both cases that a plain
 * [ExposedDropdownMenuBox] list needs no search field, unlike the Province/
 * Municipality/Barangay pickers in the wizard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(value); expanded = false })
            }
        }
    }
}
