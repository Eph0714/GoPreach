package com.emfitsolutions.gopreach.ui.screens.elders

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.data.model.Person
import com.emfitsolutions.gopreach.ui.components.TempCredentialLookupDialog

/** Shared list UI for [ManageCoordinatorEldersScreen] and [ManageRegularEldersScreen] —
 * same card layout as Manage Admins (name/scope/contact/active toggle/temp-credential
 * lookup), just parameterized by title and what "scope" means for that role. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderListScreen(
    title: String,
    scopeLabel: String,
    rows: List<ElderRow>,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onSetActive: (ElderRow, Boolean) -> Unit,
) {
    var lookupTarget by remember { mutableStateOf<Person?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Rounded.Add, contentDescription = "Enroll $title")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("None enrolled yet. Tap + to enroll one.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.person.id }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = row.person.isTemporaryCredential) { lookupTarget = row.person },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("$scopeLabel: ${row.scopeName}", style = MaterialTheme.typography.bodySmall)
                                Text("Contact: ${row.person.contact}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (row.isActive) "Active" else "Inactive",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (row.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                if (row.person.isTemporaryCredential) {
                                    Text(
                                        "Tap to view temporary sign-in",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            Switch(checked = row.isActive, onCheckedChange = { checked -> onSetActive(row, checked) })
                        }
                    }
                }
            }
        }
    }

    lookupTarget?.let { person ->
        TempCredentialLookupDialog(person = person, onDismiss = { lookupTarget = null })
    }
}
