package com.emfitsolutions.gopreach.ui.screens.contactrecord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

/** "Make a filter like (All, Congregation, By Congregation, By Status, By
 * Name)" — four modes; [ALL] shows everything with no extra control,
 * the other three each swap in the one control that matches their name. */
private enum class ContactFilterMode(val label: String) {
    ALL("All"),
    CONGREGATION("By Congregation"),
    STATUS("By Status"),
    NAME("By Name"),
}

/**
 * "Contact Record" module (visible to Super-Admin, Coordinator Elder, and
 * Regular Elder — see GoPreachNavGraph's CONTACT_RECORD composable) — one
 * consolidated, read-only directory of every Publisher, Interested Person
 * (Searching/Return Visit/Bible Study), Coordinator Elder, Service Overseer,
 * and Ministerial Servant's contact details, so finding someone's number/
 * address no longer means checking five separate Manage screens one at a
 * time. Congregation-scoped the same way every other Manage screen here is
 * (`visibleCongregationId == null` for Super-Admin's "all congregations").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactRecordScreen(
    visibleCongregationId: String?,
    onBack: () -> Unit,
    viewModel: ContactRecordViewModel = hiltViewModel(),
) {
    val rowsFlow = remember(visibleCongregationId) { viewModel.rowsFor(visibleCongregationId) }
    val rows by rowsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val congregations by viewModel.congregations.collectAsStateWithLifecycle()

    var filterMode by remember { mutableStateOf(ContactFilterMode.ALL) }
    var congregationFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var nameQuery by remember { mutableStateOf("") }

    val filteredRows = remember(rows, filterMode, congregationFilter, statusFilter, nameQuery) {
        when (filterMode) {
            ContactFilterMode.ALL -> rows
            ContactFilterMode.CONGREGATION -> {
                val target = congregationFilter
                if (target == null) rows else rows.filter { it.congregationId == target }
            }
            ContactFilterMode.STATUS -> {
                val target = statusFilter
                if (target == null) rows else rows.filter { target in it.sourceLabels }
            }
            ContactFilterMode.NAME -> {
                val needle = nameQuery.trim()
                if (needle.isBlank()) rows else rows.filter { it.name.contains(needle, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContactFilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = filterMode == mode,
                        onClick = {
                            filterMode = mode
                            // Switching modes clears whatever the *other*
                            // filter controls held, so re-picking "All" (or
                            // any other mode) always starts unfiltered
                            // instead of silently carrying a stale pick.
                            congregationFilter = null
                            statusFilter = null
                            nameQuery = ""
                        },
                        label = { Text(mode.label) },
                    )
                }
            }

            when (filterMode) {
                ContactFilterMode.CONGREGATION -> {
                    ContactCongregationDropdown(
                        congregations = congregations,
                        selectedId = congregationFilter,
                        onSelected = { congregationFilter = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                ContactFilterMode.STATUS -> {
                    ContactStatusDropdown(
                        selected = statusFilter,
                        onSelected = { statusFilter = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                ContactFilterMode.NAME -> {
                    OutlinedTextField(
                        value = nameQuery,
                        onValueChange = { nameQuery = it },
                        label = { Text("Search by name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        visualTransformation = VisualTransformation.None,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                ContactFilterMode.ALL -> Unit
            }

            Text(
                "${filteredRows.size} of ${rows.size} contact${if (rows.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (filteredRows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (rows.isEmpty()) "No contacts recorded yet." else "No contacts match this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredRows, key = { "${it.name}|${it.congregationName}|${it.sourceLabels}" }) { row ->
                        ContactRowCard(row)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactCongregationDropdown(
    congregations: List<com.emfitsolutions.gopreach.data.model.Congregation>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = congregations.firstOrNull { it.id == selectedId }?.name ?: "All Congregations"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Congregation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All Congregations") }, onClick = { onSelected(null); expanded = false })
            congregations.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelected(c.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactStatusDropdown(
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected ?: "All Statuses",
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            visualTransformation = VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All Statuses") }, onClick = { onSelected(null); expanded = false })
            CONTACT_SOURCE_LABELS.forEach { label ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(label); expanded = false })
            }
        }
    }
}

@Composable
private fun ContactRowCard(row: ContactRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Text(
                row.sourceLabels.sorted().joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (row.contact.isNotBlank()) {
                Text("Contact: ${row.contact}", style = MaterialTheme.typography.bodySmall)
            }
            if (row.address.isNotBlank()) {
                Text("Address: ${row.address}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                row.congregationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
