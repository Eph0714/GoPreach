package com.emfitsolutions.gopreach.ui.screens.contactrecord

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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
    var query by remember { mutableStateOf("") }
    val filteredRows = remember(rows, query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            rows
        } else {
            rows.filter {
                it.name.contains(needle, ignoreCase = true) ||
                    it.contact.contains(needle, ignoreCase = true) ||
                    it.address.contains(needle, ignoreCase = true) ||
                    it.sourceLabels.any { label -> label.contains(needle, ignoreCase = true) }
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name, contact, address, or source") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
                        if (rows.isEmpty()) "No contacts recorded yet." else "No contacts match \"$query\".",
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
